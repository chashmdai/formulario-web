[CmdletBinding()]
param([switch]$Revertir)
$ErrorActionPreference='Stop'
try{
 if(-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){throw 'Elevación normal requerida'}
 $ini='C:\ProgramData\MySQL\MySQL Server 8.0\my.ini';$backup='C:\agente\config\mysql-tls-respaldo-20261025'
 $m=@(Get-Content -LiteralPath (Join-Path $backup 'manifest.json') -Raw|ConvertFrom-Json)
 foreach($f in $m){if((Get-FileHash -LiteralPath (Join-Path $backup $f.archivo)).Hash -ne $f.sha256){throw 'Respaldo no válido'}}
 $before=($m|Where-Object archivo -eq 'my.ini').sha256
 $marker='C:\agente\config\mysql-tls-lab-20261025\config-aplicada.json'
 if($Revertir){
  $applied=Get-Content -LiteralPath $marker -Raw|ConvertFrom-Json
  if((Get-FileHash -LiteralPath $ini).Hash -ne $applied.sha256){throw 'Configuración cambió desde esta intervención'}
  [IO.File]::WriteAllBytes($ini,[IO.File]::ReadAllBytes((Join-Path $backup 'my.ini')))
 }else{
  if((Get-FileHash -LiteralPath $ini).Hash -ne $before){throw 'Configuración distinta del respaldo'}
  $bytes=[IO.File]::ReadAllBytes($ini);$suffix=[Text.UTF8Encoding]::new($false).GetBytes((@('','# TLS EXCLUSIVO laboratorio agente 2026-10-25; respaldo C:/agente/config/mysql-tls-respaldo-20261025','[mysqld]','ssl-ca=C:/agente/config/mysql-tls-lab-20261025/ca.pem','ssl-cert=C:/agente/config/mysql-tls-lab-20261025/server-cert.pem','ssl-key=C:/agente/config/mysql-tls-lab-20261025/server-key.pem','') -join [Environment]::NewLine))
  [IO.File]::WriteAllBytes($ini,($bytes+$suffix))
  & 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe' "--defaults-file=$ini" --validate-config 2> (Join-Path $backup 'validacion-config.txt')
  if($LASTEXITCODE -ne 0){[IO.File]::WriteAllBytes($ini,$bytes);throw 'Config rechazada y restaurada'}
  @{sha256=(Get-FileHash -LiteralPath $ini).Hash;anterior=$before;reinicio=$false}|ConvertTo-Json -Compress|Set-Content -LiteralPath $marker
 }
 @{estado='CONFIGURACION_VERIFICADA';rollback=[bool]$Revertir;servicio=(Get-Service MySQL80).Status.ToString()}|ConvertTo-Json -Compress|Set-Content -LiteralPath (Join-Path $backup 'resultado-config.json')
}catch{($_.ToString()+' '+$_.ScriptStackTrace)|Set-Content -LiteralPath 'C:\agente\logs\mysql-config-error.txt';exit 1}
