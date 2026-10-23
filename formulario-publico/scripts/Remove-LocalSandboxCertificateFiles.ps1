$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$tlsDirectory = [IO.Path]::GetFullPath((Join-Path $workspace '../.local/https'))
if (!$tlsDirectory.StartsWith([IO.Path]::GetFullPath((Join-Path $workspace '..')) + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Destino fuera del workspace.' }
if (!(Test-Path -LiteralPath $tlsDirectory)) { return }
if ((Get-Item -LiteralPath $tlsDirectory).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'No se eliminará un enlace de directorio.' }
& (Join-Path $PSScriptRoot 'Stop-LocalSandbox.ps1')
$rootPath = Join-Path $tlsDirectory 'ca/rootCA.pem'
if (Test-Path -LiteralPath $rootPath) { & (Join-Path $PSScriptRoot 'Trust-LocalSandboxCertificate.ps1') -Remove }
Remove-Item -LiteralPath $tlsDirectory -Recurse -Force
Write-Output 'Eliminados certificados, claves, diagnósticos y respaldos locales de esta prueba. Herramientas portables conservadas.'
