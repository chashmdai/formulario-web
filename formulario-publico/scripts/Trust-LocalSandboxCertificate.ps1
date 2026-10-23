[CmdletBinding()]
param([switch]$Remove)
$ErrorActionPreference = 'Stop'
$tlsDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../.local/https'))
$rootPath = Join-Path $tlsDirectory 'ca/rootCA.pem'
$root = [Security.Cryptography.X509Certificates.X509Certificate2]::CreateFromPem([IO.File]::ReadAllText($rootPath))
$certificatePath = 'Cert:/CurrentUser/Root/' + $root.Thumbprint
if ($Remove) {
    if (Test-Path -LiteralPath $certificatePath) { Remove-Item -LiteralPath $certificatePath }
    Write-Output 'Retirada la confianza de la CA propia de SIGER del usuario actual.'
} else {
    [IO.File]::WriteAllBytes((Join-Path $tlsDirectory 'rootCA.cer'), $root.RawData)
    $alreadyTrusted = Test-Path -LiteralPath $certificatePath
    if (!$alreadyTrusted) { Import-Certificate -FilePath (Join-Path $tlsDirectory 'rootCA.cer') -CertStoreLocation Cert:/CurrentUser/Root | Out-Null }
    [pscustomobject]@{ Thumbprint=$root.Thumbprint; Store='CurrentUser/Root'; AlreadyTrusted=$alreadyTrusted; AddedAt=(Get-Date).ToString('o') } |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $tlsDirectory 'trust.json')
    Write-Output ('CA de SIGER confiada solamente por el usuario actual; huella ' + $root.Thumbprint)
}
