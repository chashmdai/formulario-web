[CmdletBinding()]
param([switch]$EnableLogin)
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$tlsDirectory = Join-Path $workspace '../.local/https'
$caddy = Join-Path $workspace '../.local/tools/caddy/caddy.exe'
$jar = Join-Path $workspace 'backend/target/formulario-publico-0.1.0-SNAPSHOT.jar'
$domain = 'claveunica.formularioadulto.defensorianinez.gob.cl'
$origin = 'https://' + $domain
$configuration = Join-Path $workspace 'config/local-sandbox/application-local-sandbox-https.yml'
if (!(Test-Path -LiteralPath $jar) -or !(Test-Path -LiteralPath $caddy)) { throw 'Preparar el JAR y las herramientas locales primero.' }
if ($EnableLogin) {
    # Recheck the running, blocked HTTPS stack immediately before enabling; no cached approval alone.
    & (Join-Path $PSScriptRoot 'Test-LocalSandbox.ps1') -ForActivation
}
& (Join-Path $PSScriptRoot 'Stop-LocalSandbox.ps1')
if (Get-NetTCPConnection -LocalPort 443,8080 -State Listen -ErrorAction SilentlyContinue) { throw '443 u 8080 está ocupado. No se detendrá un servicio ajeno automáticamente.' }
$previous = @{}
$names = @('CU_ENVIRONMENT','CU_CLIENT_ID','CU_CLIENT_SECRET','CU_REDIRECT_URI','CU_LOGOUT_URI','CU_CALLBACK_APPROVED',
    'CU_ID_TOKEN_ALGORITHM','SIGER_PUBLIC_ORIGIN','SIGER_COOKIE_SECURE','SIGER_BIND_ADDRESS','SIGER_PORT',
    'SPRING_CONFIG_ADDITIONAL_LOCATION','SPRING_CONFIG_LOCATION','SPRING_APPLICATION_JSON','SPRING_PROFILES_ACTIVE','SIGER_TLS_CERT','SIGER_TLS_KEY',
    'PUBLIC_INTEGRATION_TOKEN','SIGER_INTEGRATION_TOKEN','PUBLIC_DB_URL','PUBLIC_DB_USER','PUBLIC_DB_PASSWORD','PUBLIC_OPERATIONS_TOKEN','SIGER_DB_PASSWORD','SIGER_OPERATIONS_TOKEN')
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$backend = $null; $proxy = $null
try {
    $credentials = Get-Content -LiteralPath (Join-Path $workspace '../../CLAVEUNICATEST/DefensoriadelaNinez.txt') -Raw
    $id = [regex]::Match($credentials, '(?im)^\s*Client ID SANDBOX\s*:\s*([^\r\n]+)')
    $secret = [regex]::Match($credentials, '(?im)^\s*Client Secret SANDBOX\s*:\s*([^\r\n]+)')
    if (!$id.Success -or !$secret.Success) { throw 'Par Sandbox no encontrado.' }
    $env:CU_ENVIRONMENT='SANDBOX'; $env:CU_CLIENT_ID=$id.Groups[1].Value.Trim(); $env:CU_CLIENT_SECRET=$secret.Groups[1].Value.Trim()
    $env:CU_REDIRECT_URI=$origin + '/redirect'; $env:CU_LOGOUT_URI=$origin + '/logout'; $env:SIGER_PUBLIC_ORIGIN=$origin
    $env:CU_CALLBACK_APPROVED=$EnableLogin.IsPresent.ToString().ToLowerInvariant(); $env:CU_ID_TOKEN_ALGORITHM='RS256'
    $env:SIGER_COOKIE_SECURE='true'; $env:SIGER_BIND_ADDRESS='127.0.0.1'; $env:SIGER_PORT='8080'
    $env:SPRING_PROFILES_ACTIVE='local-sandbox-https'
    if ($env:PUBLIC_DEMO_ENABLED -eq 'true') { $env:SPRING_PROFILES_ACTIVE += ',local-integration-demo' }
    if ($env:PUBLIC_DURABLE_ENABLED -eq 'true') {
        if($env:PUBLIC_DEMO_ENABLED -eq 'true'){throw 'Los perfiles durable y archivos de demostración son incompatibles.'}
        $env:SPRING_PROFILES_ACTIVE += ',jdbc-reception'
    }
    $env:SPRING_CONFIG_ADDITIONAL_LOCATION=([URI]::new($configuration)).AbsoluteUri
    $env:SPRING_CONFIG_LOCATION=$null; $env:SPRING_APPLICATION_JSON=$null
    if ($env:PUBLIC_DEMO_ENABLED -ne 'true' -and $env:PUBLIC_DURABLE_ENABLED -ne 'true') { $env:PUBLIC_INTEGRATION_TOKEN=$null }
    $env:SIGER_INTEGRATION_TOKEN=$null
    $backend = Start-Process -FilePath (Get-Command java).Source -ArgumentList @('-jar', ('"' + $jar + '"')) -WorkingDirectory $workspace -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $tlsDirectory 'backend.log') -RedirectStandardError (Join-Path $tlsDirectory 'backend-error.log')
    # Caddy has no reason to inherit CU credentials or Spring environment.
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    $env:CU_CLIENT_ID=$null; $env:CU_CLIENT_SECRET=$null
    $env:PUBLIC_INTEGRATION_TOKEN=$null; $env:SIGER_INTEGRATION_TOKEN=$null
    $env:PUBLIC_DB_PASSWORD=$null; $env:SIGER_DB_PASSWORD=$null; $env:PUBLIC_OPERATIONS_TOKEN=$null; $env:SIGER_OPERATIONS_TOKEN=$null
    $credentials=$null; $id=$null; $secret=$null
    $env:SIGER_TLS_CERT=(Join-Path $tlsDirectory 'site.pem').Replace('\','/')
    $env:SIGER_TLS_KEY=(Join-Path $tlsDirectory 'site-key.pem').Replace('\','/')
    & $caddy validate --config (Join-Path $workspace 'config/local-sandbox/Caddyfile') --adapter caddyfile
    if ($LASTEXITCODE -ne 0) { throw 'Configuración Caddy inválida.' }
    $proxy = Start-Process -FilePath $caddy -ArgumentList @('run','--config', ('"' + (Join-Path $workspace 'config/local-sandbox/Caddyfile') + '"'),'--adapter','caddyfile') `
        -WorkingDirectory $workspace -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $tlsDirectory 'proxy.log') -RedirectStandardError (Join-Path $tlsDirectory 'proxy-error.log')
    [pscustomobject]@{
        Backend=@{Id=$backend.Id;StartedUtc=$backend.StartTime.ToUniversalTime().ToString('o');ExpectedArgument=$jar}
        Proxy=@{Id=$proxy.Id;StartedUtc=$proxy.StartTime.ToUniversalTime().ToString('o');ExpectedArgument=(Join-Path $workspace 'config/local-sandbox/Caddyfile')}
        Environment='SANDBOX';Origin=$origin;LoginEnabled=$EnableLogin.IsPresent
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $tlsDirectory 'processes.json')
    $deadline=(Get-Date).AddSeconds(25)
    do {
        if ($backend.HasExited -or $proxy.HasExited) { throw 'Backend o proxy terminó durante el arranque; revisar logs locales protegidos.' }
        $up = @(Get-NetTCPConnection -LocalPort 443,8080 -State Listen -ErrorAction SilentlyContinue)
        if ($up.Count -ge 2) { break }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $deadline)
    if ($up.Count -lt 2) { throw 'No se abrieron los puertos locales a tiempo.' }
    Write-Output ('SIGER Sandbox local: ' + $origin + '; inicio habilitado=' + $EnableLogin.IsPresent)
} catch {
    foreach ($ownedProcess in @($backend,$proxy)) { if ($ownedProcess -and !$ownedProcess.HasExited) { Stop-Process -Id $ownedProcess.Id } }
    throw
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    $credentials=$null; $id=$null; $secret=$null
}
