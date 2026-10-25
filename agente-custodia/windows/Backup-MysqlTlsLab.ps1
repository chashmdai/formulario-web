[CmdletBinding()]
param([string]$Destination='C:\agente\config\mysql-tls-respaldo-20261025')
$ErrorActionPreference='Stop'
if(-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'Elevación normal requerida'}
if(Test-Path -LiteralPath $Destination){throw 'No sobrescribir respaldo anterior'}
$ini='C:\ProgramData\MySQL\MySQL Server 8.0\my.ini';$data='C:\ProgramData\MySQL\MySQL Server 8.0\Data'
$acl=[Security.AccessControl.DirectorySecurity]::new();$acl.SetAccessRuleProtection($true,$false)
foreach($sid in @([Security.Principal.WindowsIdentity]::GetCurrent().User,[Security.Principal.SecurityIdentifier]::new('S-1-5-18'),[Security.Principal.SecurityIdentifier]::new('S-1-5-32-544'))){$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))}
[IO.FileSystemAclExtensions]::CreateDirectory($acl,$Destination)|Out-Null
$files=@((Get-Item -LiteralPath $ini)) + @(Get-ChildItem -LiteralPath $data -Filter '*.pem')
if(Test-Path -LiteralPath (Join-Path $data 'mysqld-auto.cnf')){$files+=Get-Item -LiteralPath (Join-Path $data 'mysqld-auto.cnf')}
$manifest=@()
foreach($f in $files){$dest=Join-Path $Destination $f.Name;Copy-Item -LiteralPath $f.FullName -Destination $dest;$sha=(Get-FileHash -LiteralPath $f.FullName -Algorithm SHA256).Hash;if($sha -ne (Get-FileHash -LiteralPath $dest -Algorithm SHA256).Hash){throw 'Respaldo discrepante'};$manifest+=@{original=$f.FullName;archivo=$f.Name;sha256=$sha;acl=(Get-Acl -LiteralPath $f.FullName).Sddl}}
$manifest|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $Destination 'manifest.json') -Encoding utf8
@{estado='RESPALDO_VERIFICADO';archivos=$manifest.Count;servicio=(Get-Service MySQL80).Status.ToString()}|ConvertTo-Json -Compress|Set-Content -LiteralPath (Join-Path $Destination 'resultado.json')
