#Requires -Version 7.4
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$private=Join-Path $repo '.local/robustez/mysql'
$path=Join-Path $private 'credentials.clixml';$credentials=Import-Clixml -LiteralPath $path
$defaults=Join-Path $private 'provision-private.cnf';$sqlPath=Join-Path $private 'provision-private.sql'
try{
 $rootPassword=[Net.NetworkCredential]::new('',$credentials.root).Password
 [IO.File]::WriteAllText($defaults,"[client]`nuser=root`npassword=$rootPassword`nhost=127.0.0.1`nport=13306`n")
 $sql=''
 foreach($pair in @(@('robust_public_local','publicLocalApp','publicLocalMigration'),@('robust_inst_local','institutionLocalApp','institutionLocalMigration'))){
  $schema=$pair[0];$sql+="CREATE DATABASE IF NOT EXISTS $schema CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;`n"
  foreach($user in @($pair[1],$pair[2])){
   if(!$credentials.PSObject.Properties[$user]){$credentials|Add-Member -NotePropertyName $user -NotePropertyValue (ConvertTo-SecureString ([Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))) -AsPlainText -Force)}
   $password=[Net.NetworkCredential]::new('',$credentials.$user).Password
   $rights=if($user -like '*Migration'){'SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES,DROP'}else{'SELECT,INSERT,UPDATE,DELETE'}
   $sql+="CREATE USER IF NOT EXISTS '$user'@'127.0.0.1' IDENTIFIED BY '$password';`nGRANT $rights ON $schema.* TO '$user'@'127.0.0.1';`n"
  }
 }
 # DPAPI se guarda antes de ejecutar para permitir recuperar una ejecución interrumpida.
 $credentials|Export-Clixml -LiteralPath $path
 [IO.File]::WriteAllText($sqlPath,$sql)
 $mysql=Join-Path $repo '.local/tools/mysql/mysql-8.4.11-winx64/bin/mysql.exe'
 $process=Start-Process -FilePath $mysql -ArgumentList @(('--defaults-extra-file="'+$defaults+'"'),'--batch') -WindowStyle Hidden -PassThru -Wait -RedirectStandardInput $sqlPath -RedirectStandardOutput (Join-Path $private 'provision-out-private.log') -RedirectStandardError (Join-Path $private 'provision-error-private.log')
 if($process.ExitCode -ne 0){throw 'Falló la creación de esquemas locales; revisar únicamente el log privado.'}
 Write-Host 'Esquemas locales de recepción preparados. Son distintos de los esquemas que limpian las pruebas.'
}finally{
 foreach($file in @($defaults,$sqlPath)){if(Test-Path -LiteralPath $file){Remove-Item -LiteralPath $file}}
 $credentials=$null;$rootPassword=$null;$password=$null;$sql=$null
}
