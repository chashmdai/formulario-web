#Requires -Version 7.4
[CmdletBinding()]
param([switch]$Interactiva, [switch]$Reconstruir, [switch]$NoAbrirNavegador)
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (!$Interactiva) {
    # Toda identidad/proveedor simulado vive solo en src/test; no se habilita un login ficticio.
    foreach ($entry in @(@('formulario-publico','ContratoPublicoHttpTest,RecepcionPublicaTest,ContratoDigestTest'),
                         @('siger-institucional','RecuperacionHttpTest,ContratoDigestTest'))) {
        Push-Location (Join-Path $repoRoot ($entry[0]+'/backend'))
        try {
            & mvn -B -ntp ('-Dtest='+$entry[1]) test
            if ($LASTEXITCODE -ne 0) { throw 'Falló la demostración automatizada del contrato.' }
        } finally { Pop-Location }
    }
    Write-Host 'Demostración automatizada completada: custodia, HTTP simulado, SIGER temporalmente caído, confirmación perdida, reinicio e idempotencia.'
    return
}
if ($Reconstruir) {
    # Windows mantiene abiertos los JAR en ejecución: detener solo instancias propias antes de reconstruir.
    & (Join-Path $repoRoot 'formulario-publico/scripts/Stop-LocalSandbox.ps1')
    & (Join-Path $repoRoot 'siger-institucional/scripts/SIGER.ps1') detener
    & (Join-Path $PSScriptRoot 'Build-Todo.ps1')
}
$names=@('PUBLIC_INTEGRATION_TOKEN','PUBLIC_DEMO_DIRECTORY','PUBLIC_DEMO_ENABLED','SIGER_INTEGRATION_TOKEN','SIGER_DEMO_DIRECTORY','SIGER_PUBLIC_SOURCE')
$previous=@{}
foreach ($name in $names) { $previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process') }
try {
    $token=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    $env:PUBLIC_INTEGRATION_TOKEN=$token; $env:SIGER_INTEGRATION_TOKEN=$token
    $env:PUBLIC_DEMO_DIRECTORY=Join-Path $repoRoot '.local/demo/publico'
    $env:SIGER_DEMO_DIRECTORY=Join-Path $repoRoot '.local/demo/institucional'
    $env:PUBLIC_DEMO_ENABLED='true'
    # Conexión exclusiva entre procesos loopback de la demo. En producción será HTTPS público.
    $env:SIGER_PUBLIC_SOURCE='http://127.0.0.1:8080'
    # Invocar el script directamente: cmd.exe puede esperar procesos descendientes en una redirección.
    & (Join-Path $repoRoot 'formulario-publico/scripts/Formulario.ps1') iniciar -NoAbrirNavegador
    & (Join-Path $repoRoot 'siger-institucional/scripts/SIGER.ps1') iniciar -DemoIntegracion
    Write-Host 'DEMO LOCAL: entra con el RUN oficial Sandbox; revisa el mensaje y usa Enviar Comunicación de prueba.'
    Write-Host 'Consulta luego la recepción en SIGER. Para probar indisponibilidad: SIGER.cmd detener; el formulario conserva los pendientes.'
    Write-Host 'Para recuperar tras detenerlo vuelve a ejecutar este script -Interactiva (rota la credencial efímera de ambos procesos).'
    Write-Host 'Para detener: FORMULARIO.cmd detener y SIGER.cmd detener. Los datos locales se conservan en .local/demo.'
    if (!$NoAbrirNavegador) { Start-Process 'https://claveunica.formularioadulto.defensorianinez.gob.cl' }
} catch {
    & (Join-Path $repoRoot 'siger-institucional/scripts/SIGER.ps1') detener
    & (Join-Path $repoRoot 'formulario-publico/scripts/Formulario.ps1') detener
    throw
} finally {
    $token=$null
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
}
