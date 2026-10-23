#Requires -Version 7.4
[CmdletBinding()]
param(
    [ValidateSet('iniciar','detener','estado')][string]$Accion = 'iniciar',
    [switch]$Reconstruir,
    [switch]$NoAbrirNavegador
)
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$origin = 'https://claveunica.formularioadulto.defensorianinez.gob.cl'
$jar = Join-Path $workspace 'backend/target/formulario-publico-0.1.0-SNAPSHOT.jar'
$ownsStartup = $false

function Stop-SigerDevelopmentProcesses {
    # Only processes listening on SIGER's development ports and proven to belong to this checkout.
    foreach ($listener in @(Get-NetTCPConnection -LocalPort 443,8080,5173 -State Listen -ErrorAction SilentlyContinue)) {
        $processInfo = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $listener.OwningProcess)
        if (!$processInfo -or !$processInfo.CommandLine) { continue }
        $command = $processInfo.CommandLine.Replace('/', '\')
        $isJar = $processInfo.Name -eq 'java.exe' -and $command.Contains($jar.Replace('/', '\'), [StringComparison]::OrdinalIgnoreCase)
        $isClasses = $processInfo.Name -eq 'java.exe' -and $command.Contains((Join-Path $workspace 'backend\target\classes'), [StringComparison]::OrdinalIgnoreCase) -and $command.Contains('cl.nexbyte.siger.FormularioPublicoApplication')
        $isVite = $processInfo.Name -eq 'node.exe' -and $command.Contains((Join-Path $workspace 'front\node_modules\'), [StringComparison]::OrdinalIgnoreCase) -and $command -match '\\vite\\'
        if ($isJar -or $isClasses -or $isVite) {
            $ownedProcess = Get-Process -Id $processInfo.ProcessId -ErrorAction SilentlyContinue
            if ($ownedProcess) {
                Write-Host ('Cerrando la instancia anterior de este SIGER (PID ' + $ownedProcess.Id + ').')
                Stop-Process -Id $ownedProcess.Id
                $ownedProcess.WaitForExit(10000) | Out-Null
            }
        } elseif ($listener.LocalPort -in @(443,8080)) {
            throw ('El puerto ' + $listener.LocalPort + ' está ocupado por otro proceso (PID ' + $listener.OwningProcess + '). Ciérralo desde su terminal antes de iniciar SIGER; no se detendrá automáticamente.')
        }
    }
}

function Get-LocalSigerStatus {
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.UseProxy = $false; $handler.AllowAutoRedirect = $false
    $client = [Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(4)
    try {
        $response = $client.GetAsync($origin + '/api/session').GetAwaiter().GetResult()
        if ([int]$response.StatusCode -ne 200) { throw 'HTTPS no disponible.' }
        $session = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
        return [pscustomobject]@{ Disponible=$true; Ambiente=$session.environment; IngresoHabilitado=$session.loginAvailable; Direccion=$origin }
    } catch {
        return [pscustomobject]@{ Disponible=$false; Ambiente=''; IngresoHabilitado=$false; Direccion=$origin }
    } finally { $client.Dispose(); $handler.Dispose() }
}

try {
    if ($Accion -eq 'estado') { Get-LocalSigerStatus | Format-List; return }
    if ($Accion -eq 'detener') {
        & (Join-Path $PSScriptRoot 'Stop-LocalSandbox.ps1')
        Stop-SigerDevelopmentProcesses
        Write-Host 'Formulario Público detenido. Se conservan hosts y certificado para el próximo arranque.'
        return
    }

    Write-Host 'Formulario Público | ClaveÚnica Sandbox HTTPS local'
    Write-Host 'Este asistente sirve React desde el JAR. No necesitas ejecutar npm run dev.'
    if (!(Get-Command java -ErrorAction SilentlyContinue)) { throw 'Java 25 no está disponible en PATH.' }
    $credentialsPath = Join-Path $workspace '../../CLAVEUNICATEST/DefensoriadelaNinez.txt'
    if (!(Test-Path -LiteralPath $credentialsPath)) { throw ('Falta el TXT institucional Sandbox en ' + $credentialsPath) }

    Write-Host '[1/6] Cerrando instancias anteriores de este workspace y limpiando registros de otros arranques...'
    & (Join-Path $PSScriptRoot 'Stop-LocalSandbox.ps1')
    Stop-SigerDevelopmentProcesses

    if ($Reconstruir -or !(Test-Path -LiteralPath $jar)) {
        foreach ($tool in @('mvn','npm')) { if (!(Get-Command $tool -ErrorAction SilentlyContinue)) { throw ('Para construir SIGER se necesita ' + $tool + ' en PATH.') } }
        Write-Host 'Construyendo backend y frontend; esta operación puede tardar unos minutos...'
        & (Join-Path $PSScriptRoot 'Build-Formulario.ps1')
    }
    Write-Host '[2/6] Preparando herramientas portables y certificado local...'
    & (Join-Path $PSScriptRoot 'Prepare-LocalSandboxTools.ps1')
    & (Join-Path $PSScriptRoot 'Trust-LocalSandboxCertificate.ps1')

    Write-Host '[3/6] Comprobando resolución local mediante hosts...'
    $localAddresses = @()
    try { $localAddresses = @([Net.Dns]::GetHostAddresses(([URI]$origin).Host) | ForEach-Object { $_.IPAddressToString }) } catch { }
    if ($localAddresses.Count -eq 0 -or ($localAddresses | Where-Object { $_ -ne '127.0.0.1' })) {
        Write-Host 'Se necesita administrador solo para agregar el bloque SIGER a hosts y vaciar caché DNS. Autoriza UAC si Windows lo solicita.'
        $hostsScript = Join-Path $PSScriptRoot 'Set-LocalSandboxHosts.ps1'
        $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
        if (([Security.Principal.WindowsPrincipal]::new($identity)).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
            & $hostsScript
        } elseif (Get-Command sudo.exe -ErrorAction SilentlyContinue) {
            & sudo.exe --inline (Join-Path $PSHOME 'pwsh.exe') -NoProfile -File $hostsScript
            if ($LASTEXITCODE -ne 0) { throw 'No se autorizó o falló la modificación de hosts.' }
        } else {
            $elevated = Start-Process -FilePath (Join-Path $PSHOME 'pwsh.exe') -ArgumentList @('-NoProfile','-File',('"' + $hostsScript + '"')) -Verb RunAs -WindowStyle Hidden -Wait -PassThru
            if ($elevated.ExitCode -ne 0) { throw 'Falló la modificación autorizada de hosts.' }
        }
    }

    Write-Host '[4/6] Levantando HTTPS con el ingreso inicialmente bloqueado...'
    $ownsStartup = $true
    & (Join-Path $PSScriptRoot 'Start-LocalSandbox.ps1')
    Write-Host '[5/6] Validando certificado, cookies, origen y callback antes de habilitar...'
    & (Join-Path $PSScriptRoot 'Test-LocalSandbox.ps1') -RequireLoginBlocked | Out-Null
    Write-Host '[6/6] Habilitando exclusivamente Sandbox y comprobando su redirección a ClaveÚnica...'
    & (Join-Path $PSScriptRoot 'Start-LocalSandbox.ps1') -EnableLogin | Out-Null
    & (Join-Path $PSScriptRoot 'Test-LocalSandbox.ps1') -RequireLoginEnabled | Out-Null

    Write-Host ''
    Write-Host 'FORMULARIO PÚBLICO LISTO. Abre esta dirección para iniciar sesión:' -ForegroundColor Green
    Write-Host $origin -ForegroundColor Green
    Write-Host 'RUN oficial de prueba: 44.444.444-4 | clave de prueba: testing (solo en Gobierno Digital).'
    Write-Host 'Los procesos quedan ejecutándose en segundo plano. Para detenerlos: FORMULARIO.cmd detener'
    if (!$NoAbrirNavegador) { Start-Process -FilePath $origin }
    return
} catch {
    if ($ownsStartup) {
        try { & (Join-Path $PSScriptRoot 'Stop-LocalSandbox.ps1') | Out-Null }
        catch { Write-Host 'No se pudo completar la limpieza de los procesos registrados; revisar FORMULARIO.cmd estado.' }
    }
    Write-Host ''
    Write-Host ('El Formulario Público no quedó listo: ' + $_.Exception.Message) -ForegroundColor Red
    Write-Host 'No cambies CU_CALLBACK_APPROVED manualmente. Revisa .local\https\backend.log y backend-error.log si falló el arranque.'
    throw
}
