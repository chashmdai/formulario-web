#Requires -Version 7.4
[CmdletBinding()]
param([ValidateSet('preparar','iniciar','detener','estado')][string]$Accion='estado')
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$privateRoot=Join-Path $repoRoot '.local/robustez/mysql'
$toolRoot=Join-Path $repoRoot '.local/tools/mysql'
$base=Join-Path $toolRoot 'mysql-8.4.11-winx64'
$mysqld=Join-Path $base 'bin/mysqld.exe'
$statePath=Join-Path $privateRoot 'process.json'
$credentialsPath=Join-Path $privateRoot 'credentials.clixml'
if($Accion -eq 'detener'){
    if(Test-Path -LiteralPath $statePath){
        $record=Get-Content -LiteralPath $statePath -Raw|ConvertFrom-Json
        $child=Get-Process -Id $record.Id -ErrorAction SilentlyContinue
        if($child -and $child.StartTime.ToUniversalTime().Ticks -eq ([DateTimeOffset]$record.StartedUtc).UtcDateTime.Ticks){
            $command=(Get-CimInstance Win32_Process -Filter ('ProcessId='+$record.Id)).CommandLine
            if(!$command.Contains($privateRoot,[StringComparison]::OrdinalIgnoreCase)){throw 'El proceso no pertenece a esta base local.'}
            $clientConfig=Join-Path $privateRoot 'shutdown-private.cnf'
            try {
                $credential=Import-Clixml -LiteralPath $credentialsPath
                $password=[Net.NetworkCredential]::new('',$credential.root).Password
                [IO.File]::WriteAllText($clientConfig,"[client]`nuser=root`npassword=$password`nhost=127.0.0.1`nport=13306`n")
                & (Join-Path $base 'bin/mysqladmin.exe') ('--defaults-extra-file='+$clientConfig) shutdown
                if($LASTEXITCODE -ne 0){throw 'No fue posible detener MySQL limpiamente.'}
                $child.WaitForExit(10000)|Out-Null
            } finally {
                if(Test-Path -LiteralPath $clientConfig){Remove-Item -LiteralPath $clientConfig}
                $password=$null;$credential=$null
            }
        }
        Remove-Item -LiteralPath $statePath
    }
    Write-Host 'MySQL local de pruebas detenido. Los datos se conservan.';return
}
if($Accion -eq 'estado'){
    Write-Output ([pscustomobject]@{Puerto=13306;Escuchando=[bool](Get-NetTCPConnection -LocalPort 13306 -State Listen -ErrorAction SilentlyContinue);CredencialesProtegidas=Test-Path -LiteralPath $credentialsPath});return
}
New-Item -ItemType Directory -Path $privateRoot,$toolRoot -Force|Out-Null
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& (Join-Path $env:windir 'System32/icacls.exe') $privateRoot /inheritance:r /grant:r ('*'+$sid+':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' /Q|Out-Null
if($LASTEXITCODE -ne 0){throw 'No se pudo proteger el directorio local.'}
if(!(Test-Path -LiteralPath $mysqld)){
    $archive=Join-Path $toolRoot 'mysql-8.4.11-winx64.zip'
    if(!(Test-Path -LiteralPath $archive)){Invoke-WebRequest 'https://cdn.mysql.com/Downloads/MySQL-8.4/mysql-8.4.11-winx64.zip' -OutFile $archive -TimeoutSec 600}
    # Publicado por Oracle y contrastado con SHA-256 de la descarga oficial usada en esta iteración.
    if((Get-FileHash $archive -Algorithm MD5).Hash -ne '2E833921898A9A030EA6BFE81BD811BC' -or
       (Get-FileHash $archive -Algorithm SHA256).Hash -ne 'A492371D687D2BAB088B0062581144A0044B8964BAEFDF4FAA579292B423D25C'){throw 'Integridad del paquete MySQL no coincide.'}
    Expand-Archive -LiteralPath $archive -DestinationPath $toolRoot
}
$data=Join-Path $privateRoot 'data'
if(!(Test-Path -LiteralPath (Join-Path $data 'mysql'))){
    & $mysqld --no-defaults --initialize-insecure ('--basedir='+$base) ('--datadir='+$data) ('--log-error='+(Join-Path $privateRoot 'initialize.log'))
    if($LASTEXITCODE -ne 0){throw 'No se pudo inicializar el datadir aislado.'}
}
if($Accion -eq 'preparar'){Write-Host 'MySQL portable preparado. Sin servicio Windows, WSL ni Docker.';return}
if(Get-NetTCPConnection -LocalPort 13306 -State Listen -ErrorAction SilentlyContinue){
    if(!(Test-Path -LiteralPath $statePath)){throw '13306 está ocupado por otro servicio.'}
    $saved=Get-Content -LiteralPath $statePath -Raw|ConvertFrom-Json
    $listenerId=(Get-NetTCPConnection -LocalPort 13306 -State Listen|Select-Object -First 1).OwningProcess
    if($listenerId -ne $saved.Id){
        # MySQL en Windows crea un proceso supervisor y un hijo que escucha.
        $info=Get-CimInstance Win32_Process -Filter ('ProcessId='+$listenerId)
        $parent=Get-Process -Id $saved.Id -ErrorAction SilentlyContinue
        if(!$parent -or $info.ParentProcessId -ne $saved.Id -or !$info.CommandLine.Contains($privateRoot) -or $parent.StartTime.ToUniversalTime().Ticks -ne ([DateTimeOffset]$saved.StartedUtc).UtcDateTime.Ticks){throw '13306 no corresponde al proceso registrado.'}
        $actual=Get-Process -Id $listenerId
        @{Id=$actual.Id;StartedUtc=$actual.StartTime.ToUniversalTime().ToString('o');ParentId=$saved.Id}|ConvertTo-Json|Set-Content -LiteralPath $statePath
    }
    Write-Host 'MySQL local ya está iniciado.';return
}
$initPath=Join-Path $privateRoot 'init.sql'
$initializing=!(Test-Path -LiteralPath $credentialsPath)
if($initializing){
    $values=@{}
    foreach($name in @('root','publicApp','publicMigration','institutionApp','institutionMigration')){$values[$name]=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))}
    $credentials=[pscustomobject]@{}
    foreach($entry in $values.GetEnumerator()){$credentials|Add-Member -NotePropertyName $entry.Key -NotePropertyValue (ConvertTo-SecureString $entry.Value -AsPlainText -Force)}
    $credentials|Export-Clixml -LiteralPath $credentialsPath
    $sql="ALTER USER 'root'@'localhost' IDENTIFIED BY '"+$values.root+"';`nCREATE USER IF NOT EXISTS 'root'@'127.0.0.1' IDENTIFIED BY '"+$values.root+"';`nGRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;`n"
    foreach($pair in @(@('robust_public_test','publicApp','publicMigration'),@('robust_inst_test','institutionApp','institutionMigration'))){
        $schema=$pair[0];$appUser=$pair[1];$migration=$pair[2]
        $sql+="CREATE DATABASE IF NOT EXISTS $schema CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;`n"
        foreach($user in @($appUser,$migration)){
            $sql+="CREATE USER IF NOT EXISTS '$user'@'127.0.0.1' IDENTIFIED BY '"+$values[$user]+"';`n"
            $permissions=if($user -eq $migration){'SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES,DROP'}else{'SELECT,INSERT,UPDATE,DELETE'}
            $sql+="GRANT $permissions ON $schema.* TO '$user'@'127.0.0.1';`n"
        }
    }
    [IO.File]::WriteAllText($initPath,$sql)
    $values=$null;$sql=$null;$credentials=$null
}else{
    $savedCredentials=Import-Clixml -LiteralPath $credentialsPath
    $rootPassword=[Net.NetworkCredential]::new('',$savedCredentials.root).Password
    [IO.File]::WriteAllText($initPath,"CREATE USER IF NOT EXISTS 'root'@'127.0.0.1' IDENTIFIED BY '$rootPassword';`nGRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;`n")
    $savedCredentials=$null;$rootPassword=$null
}
$arguments=@('--no-defaults',('--basedir="'+$base+'"'),('--datadir="'+$data+'"'),'--bind-address=127.0.0.1','--port=13306','--mysqlx=0','--skip-name-resolve','--local-infile=0','--innodb-flush-log-at-trx-commit=1',('--log-error="'+(Join-Path $privateRoot 'mysql.log')+'"'))
if(Test-Path -LiteralPath $initPath){$arguments+=('--init-file="'+$initPath+'"')}
$child=Start-Process -FilePath $mysqld -ArgumentList $arguments -WindowStyle Hidden -PassThru -WorkingDirectory $privateRoot `
    -RedirectStandardOutput (Join-Path $privateRoot 'stdout.log') -RedirectStandardError (Join-Path $privateRoot 'stderr.log')
@{Id=$child.Id;StartedUtc=$child.StartTime.ToUniversalTime().ToString('o')}|ConvertTo-Json|Set-Content -LiteralPath $statePath
$deadline=(Get-Date).AddSeconds(40)
do{
    if($child.HasExited){throw 'MySQL terminó durante el arranque; revisar logs privados.'}
    $ready=Get-NetTCPConnection -LocalPort 13306 -State Listen -ErrorAction SilentlyContinue
    if(!$ready){Start-Sleep -Milliseconds 400}
}while(!$ready -and (Get-Date) -lt $deadline)
if(!$ready){throw 'No se abrió el puerto local de MySQL.'}
$actual=Get-Process -Id ($ready|Select-Object -First 1).OwningProcess
@{Id=$actual.Id;StartedUtc=$actual.StartTime.ToUniversalTime().ToString('o');ParentId=$child.Id}|ConvertTo-Json|Set-Content -LiteralPath $statePath
if(Test-Path -LiteralPath $initPath){Remove-Item -LiteralPath $initPath}
Write-Host 'MySQL 8.4 local listo en 127.0.0.1:13306. Dos esquemas de pruebas y cuentas separadas de aplicación/migración. Secretos protegidos con DPAPI.'
