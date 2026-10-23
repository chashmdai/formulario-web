$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$privateDirectory = Join-Path $workspace '../.local'
$toolsDirectory = Join-Path $privateDirectory 'tools'
$tlsDirectory = Join-Path $privateDirectory 'https'
New-Item -ItemType Directory -Force (Join-Path $toolsDirectory 'caddy'),(Join-Path $toolsDirectory 'mkcert'),(Join-Path $tlsDirectory 'ca') | Out-Null
# Set only the DACL. Set-Acl on some installed PowerShell builds also tries to
# write audit metadata and unnecessarily requires SeSecurityPrivilege.
$taskUserSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& (Join-Path $env:windir 'System32/icacls.exe') $privateDirectory /inheritance:r /grant:r `
    ('*' + $taskUserSid + ':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' /Q | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'No se pudieron proteger los archivos locales del usuario.' }
$caddyArchive = Join-Path $toolsDirectory 'caddy.zip'
$mkcert = Join-Path $toolsDirectory 'mkcert/mkcert.exe'
$caddy = Join-Path $toolsDirectory 'caddy/caddy.exe'
if (!(Test-Path -LiteralPath $caddy)) {
    Invoke-WebRequest 'https://github.com/caddyserver/caddy/releases/download/v2.11.7/caddy_2.11.7_windows_amd64.zip' -OutFile $caddyArchive
    if ((Get-FileHash -LiteralPath $caddyArchive -Algorithm SHA256).Hash -ne '0A1EDC0B799512051C57071CE0E798D3F2CF67DC98171366F1D2B326072E3B06') { throw 'Hash Caddy no coincide.' }
    Expand-Archive -LiteralPath $caddyArchive -DestinationPath (Join-Path $toolsDirectory 'caddy') -Force
}
if (!(Test-Path -LiteralPath $mkcert)) {
    Invoke-WebRequest 'https://github.com/FiloSottile/mkcert/releases/download/v1.4.4/mkcert-v1.4.4-windows-amd64.exe' -OutFile $mkcert
}
if ((Get-FileHash -LiteralPath $mkcert -Algorithm SHA256).Hash -ne 'D2660B50A9ED59EADA480750561C96ABC2ED4C9A38C6A24D93E30E0977631398') { throw 'Hash mkcert no coincide con el artefacto fijado para esta prueba.' }
$previousCaRoot = $env:CAROOT
try {
    $env:CAROOT = Join-Path $tlsDirectory 'ca'
    if (!(Test-Path -LiteralPath (Join-Path $tlsDirectory 'site.pem')) -or !(Test-Path -LiteralPath (Join-Path $tlsDirectory 'site-key.pem'))) {
        & $mkcert -cert-file (Join-Path $tlsDirectory 'site.pem') -key-file (Join-Path $tlsDirectory 'site-key.pem') 'claveunica.formularioadulto.defensorianinez.gob.cl'
        if ($LASTEXITCODE -ne 0) { throw 'mkcert no pudo generar el certificado.' }
    }
} finally { $env:CAROOT = $previousCaRoot }
& $caddy version
& $mkcert -version
Write-Output 'Herramientas y certificado preparados dentro de .local; todavía no modifica hosts ni instala confianza.'
