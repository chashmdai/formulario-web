[CmdletBinding()]
param(
    [string]$CredentialsPath = (Join-Path $PSScriptRoot '../../../CLAVEUNICATEST/DefensoriadelaNinez.txt'),
    [string]$RedirectUri = 'https://claveunica.formularioadulto.ddn.gob.cl/redirect',
    [string]$LogoutUri = 'https://claveunica.formularioadulto.ddn.gob.cl/logout',
    [string]$PublicOrigin = 'http://localhost:5173',
    [ValidateSet('RS256', 'HS256')][string]$IdTokenAlgorithm = 'RS256',
    [switch]$CallbackApproved,
    [switch]$SecureCookie,
    [switch]$Packaged
)
$ErrorActionPreference = 'Stop'
$names = @('CU_ENVIRONMENT','CU_CLIENT_ID','CU_CLIENT_SECRET','CU_REDIRECT_URI','CU_LOGOUT_URI','CU_CALLBACK_APPROVED','CU_ID_TOKEN_ALGORITHM','SIGER_PUBLIC_ORIGIN','SIGER_COOKIE_SECURE')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    if (!$CallbackApproved) {
        Write-Host 'Modo de desarrollo: ingreso ClaveÚnica deshabilitado. Para repetir la autenticación HTTPS real, usa SIGER.cmd en la raíz.'
    }
    # Deliberately selects only SANDBOX. No QA/production auto-selection or fallback.
    $content = Get-Content -LiteralPath $CredentialsPath -Raw
    $idMatch = [regex]::Match($content, '(?im)^\s*Client ID SANDBOX\s*:\s*([^\r\n]+)')
    $secretMatch = [regex]::Match($content, '(?im)^\s*Client Secret SANDBOX\s*:\s*([^\r\n]+)')
    if (!$idMatch.Success -or !$secretMatch.Success) { throw 'No se encontró el par Sandbox en el archivo institucional.' }
    $env:CU_ENVIRONMENT = 'SANDBOX'
    $env:CU_CLIENT_ID = $idMatch.Groups[1].Value.Trim()
    $env:CU_CLIENT_SECRET = $secretMatch.Groups[1].Value.Trim()
    $env:CU_REDIRECT_URI = $RedirectUri
    $env:CU_LOGOUT_URI = $LogoutUri
    $env:CU_CALLBACK_APPROVED = $CallbackApproved.IsPresent.ToString().ToLowerInvariant()
    $env:CU_ID_TOKEN_ALGORITHM = $IdTokenAlgorithm
    $env:SIGER_PUBLIC_ORIGIN = $PublicOrigin.TrimEnd('/')
    $env:SIGER_COOKIE_SECURE = $SecureCookie.IsPresent.ToString().ToLowerInvariant()
    $content = $null; $idMatch = $null; $secretMatch = $null
    Push-Location (Join-Path $PSScriptRoot '../backend')
    try {
        if ($Packaged) { & java -jar target/formulario-publico-0.1.0-SNAPSHOT.jar }
        else { & mvn spring-boot:run }
        if ($LASTEXITCODE -ne 0) { throw 'El backend terminó con error. Revisar el diagnóstico sin imprimir credenciales.' }
    } finally { Pop-Location }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    $content = $null; $idMatch = $null; $secretMatch = $null
}
