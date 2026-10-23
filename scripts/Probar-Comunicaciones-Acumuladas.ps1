#Requires -Version 7.4
[CmdletBinding()]
param([ValidateRange(100,20000)][int]$Cantidad=5000,[ValidateRange(1,8)][int]$Paralelismo=4)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$work=Join-Path $repo '.local/robustez/acumuladas'
$keys=Join-Path $repo '.local/robustez/recepcion'
if(Test-Path -LiteralPath (Join-Path $keys 'laboratorio-activo.txt')){throw 'Detenga primero Recepcion-Local.ps1 detener. La prueba limpia solo los esquemas *_test.'}
if(Get-NetTCPConnection -LocalPort 8081,18081 -State Listen -ErrorAction SilentlyContinue){throw 'SIGER debe estar detenido durante la acumulación; no se detendrán procesos ajenos.'}
& (Join-Path $PSScriptRoot 'Local-MySQL.ps1') iniciar
New-Item -ItemType Directory -Path $work,$keys -Force|Out-Null
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
foreach($dir in @($work,$keys)){& icacls.exe $dir /inheritance:r /grant:r ('*'+$sid+':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' /Q|Out-Null;if($LASTEXITCODE -ne 0){throw 'No se protegió el laboratorio.'}}
& node (Join-Path $PSScriptRoot 'Crear-Claves-Comprobante.mjs') $keys
& node (Join-Path $PSScriptRoot 'Crear-Claves-Comprobante.mjs') $keys --institucional
if($LASTEXITCODE -ne 0){throw 'No se prepararon claves locales.'}
$jar=Join-Path $repo 'siger-institucional/backend/target/siger-institucional-0.1.0-SNAPSHOT.jar'
if(!(Test-Path -LiteralPath $jar)){throw 'Construya el JAR institucional antes de ejecutar esta prueba.'}
foreach($name in @('publico-listo.json','siger-iniciado.signal','resultado-publico.json','progreso.json','fin.signal')){
 $file=Join-Path $work $name;if(Test-Path -LiteralPath $file){Remove-Item -LiteralPath $file}
}
$secret=Import-Clixml -LiteralPath (Join-Path $repo '.local/robustez/mysql/credentials.clixml')
$names=@('SIGER_REGISTRATION_PARALLELISM','SIGER_OPERATIONS_TOKEN','OPERATIONS_LOCAL_HTTP','ROBUST_DB_URL','ROBUST_DB_USER','ROBUST_DB_PASSWORD','ROBUST_DB_MIGRATION_USER','ROBUST_DB_MIGRATION_PASSWORD','MIGRATION_DB_URL','MIGRATION_DB_USER','MIGRATION_DB_PASSWORD','LOCAL_DB_ALLOWED','BATCH_DIRECTORY','BATCH_PUBLIC_PRIVATE','BATCH_PUBLIC_TRUST','BATCH_INSTITUTION_TRUST','BATCH_INTEGRATION_TOKEN','SPRING_PROFILES_ACTIVE','SIGER_INST_PORT','SIGER_INST_BIND_ADDRESS','SIGER_DB_URL','SIGER_DB_USER','SIGER_DB_PASSWORD','SIGER_V2_ENABLED','SIGER_V2_LOCAL_HTTP','SIGER_V2_CA_PATH','SIGER_PUBLIC_SOURCE','SIGER_INTEGRATION_TOKEN','RECEIPT_TRUSTED_KEYS_PATH','SIGER_RECEIPT_KEY_ID','SIGER_RECEIPT_PRIVATE_KEY_PATH','SIGER_RECEIPT_TRUSTED_KEYS_PATH')
$saved=@{};foreach($name in $names){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
$institution=$null;$producer=$null
function Plain($value){[Net.NetworkCredential]::new('',$value).Password}
function Wait-File([string]$name,[int]$seconds){
 $path=Join-Path $work $name;$deadline=(Get-Date).AddSeconds($seconds)
 while(!(Test-Path -LiteralPath $path)){
  if($producer){$producer.Refresh();if($producer.HasExited){throw 'El productor de prueba terminó; revisar log privado.'}}
  if($institution){$institution.Refresh();if($institution.HasExited){throw 'SIGER terminó; revisar log privado.'}}
  if((Get-Date) -gt $deadline){throw "Faltó señal $name"};Start-Sleep -Milliseconds 200
 }
}
function SQL-Institucional([string]$sql){
 $defaults=Join-Path $work 'mysql-private.cnf';$inputPath=Join-Path $work 'query.sql';$outputPath=Join-Path $work 'query-out.txt'
 try{
  $password=Plain $secret.institutionApp
  [IO.File]::WriteAllText($defaults,"[client]`nuser=institutionApp`npassword=$password`nhost=127.0.0.1`nport=13306`ndatabase=robust_inst_test`n")
  [IO.File]::WriteAllText($inputPath,$sql)
  $mysql=Join-Path $repo '.local/tools/mysql/mysql-8.4.11-winx64/bin/mysql.exe'
  $process=Start-Process -FilePath $mysql -ArgumentList @(('--defaults-extra-file="'+$defaults+'"'),'--batch','--skip-column-names') -WindowStyle Hidden -PassThru -Wait -RedirectStandardInput $inputPath -RedirectStandardOutput $outputPath -RedirectStandardError (Join-Path $work 'query-error-private.log')
  if($process.ExitCode -ne 0){throw 'Falló la consulta aislada de testing.'}
  if(Test-Path -LiteralPath $outputPath){Get-Content -LiteralPath $outputPath}
 }finally{foreach($file in @($defaults,$inputPath)){if(Test-Path -LiteralPath $file){Remove-Item -LiteralPath $file}};$password=$null}
}
try{
 $env:LOCAL_DB_ALLOWED='true'
 $env:MIGRATION_DB_URL='jdbc:mysql://127.0.0.1:13306/robust_inst_test?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=3000&socketTimeout=5000'
 $env:MIGRATION_DB_USER='institutionMigration';$env:MIGRATION_DB_PASSWORD=Plain $secret.institutionMigration
 & java -jar $jar --migrate *> (Join-Path $work 'migration-private.log')
 if($LASTEXITCODE -ne 0){throw 'Falló la migración institucional de pruebas.'}
 $env:MIGRATION_DB_PASSWORD=$null
 SQL-Institucional "DELETE FROM communication_registry; DELETE FROM institutional_inbox; DELETE FROM operational_event; DELETE FROM external_operation; DELETE FROM control_audit; UPDATE operation_control SET revision=0,accept_new=TRUE,pull_enabled=TRUE,contingency='NORMAL';"|Out-Null
 $env:ROBUST_DB_URL='jdbc:mysql://127.0.0.1:13306/robust_public_test?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=3000&socketTimeout=5000'
 $env:ROBUST_DB_USER='publicApp';$env:ROBUST_DB_PASSWORD=Plain $secret.publicApp;$env:ROBUST_DB_MIGRATION_USER='publicMigration';$env:ROBUST_DB_MIGRATION_PASSWORD=Plain $secret.publicMigration
 $env:BATCH_DIRECTORY=$work;$env:BATCH_PUBLIC_PRIVATE=Join-Path $keys 'receipt-private.pem';$env:BATCH_PUBLIC_TRUST=Join-Path $keys 'receipt-trust.json';$env:BATCH_INSTITUTION_TRUST=Join-Path $keys 'institution-trust.json'
 $env:BATCH_INTEGRATION_TOKEN=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
 $maven=(Get-Command mvn.cmd).Source
 $plazo=[Math]::Max(360,[int]($Cantidad/10)+180)
 $producer=Start-Process -FilePath $env:ComSpec -ArgumentList @('/d','/c',('""'+$maven+'" -B -ntp -Dtest=CargaAcumuladaTest -Dcarga.acumulada=true -Dcarga.cantidad='+$Cantidad+' -Dcarga.plazo-segundos='+$plazo+' test"')) -WorkingDirectory (Join-Path $repo 'formulario-publico/backend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $work 'publico-private.log') -RedirectStandardError (Join-Path $work 'publico-error-private.log')
 Write-Host "Generando $Cantidad Comunicaciones sintéticas con SIGER detenido..."
 Wait-File 'publico-listo.json' 240
 $ready=Get-Content (Join-Path $work 'publico-listo.json') -Raw|ConvertFrom-Json
 if(!(($ready.origen -as [uri]).Host -eq '127.0.0.1') -or $ready.cantidad -ne $Cantidad){throw 'Origen o volumen no esperado'}
 Write-Host 'Custodia acumulada confirmada. Iniciando el JAR real de SIGER y midiendo su recuperación.'
 $env:SPRING_PROFILES_ACTIVE='jdbc-reception';$env:SIGER_INST_PORT='18081';$env:SIGER_INST_BIND_ADDRESS='127.0.0.1'
 $env:SIGER_DB_URL=$env:MIGRATION_DB_URL;$env:SIGER_DB_USER='institutionApp';$env:SIGER_DB_PASSWORD=Plain $secret.institutionApp
 $env:SIGER_V2_ENABLED='true';$env:SIGER_V2_LOCAL_HTTP='true';$env:SIGER_V2_CA_PATH=$null
 $env:SIGER_REGISTRATION_PARALLELISM=$Paralelismo.ToString()
 $env:SIGER_PUBLIC_SOURCE=$ready.origen;$env:SIGER_INTEGRATION_TOKEN=$env:BATCH_INTEGRATION_TOKEN
 $env:SIGER_OPERATIONS_TOKEN=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32));$env:OPERATIONS_LOCAL_HTTP='true'
 $env:RECEIPT_TRUSTED_KEYS_PATH=$env:BATCH_PUBLIC_TRUST;$env:SIGER_RECEIPT_KEY_ID='institution-ed25519-v1';$env:SIGER_RECEIPT_PRIVATE_KEY_PATH=Join-Path $keys 'institution-private.pem';$env:SIGER_RECEIPT_TRUSTED_KEYS_PATH=$env:BATCH_INSTITUTION_TRUST
 $institution=Start-Process -FilePath (Get-Command java).Source -ArgumentList @('-jar',('"'+$jar+'"')) -WorkingDirectory $repo -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $work 'siger-private.log') -RedirectStandardError (Join-Path $work 'siger-error-private.log')
 Set-Content (Join-Path $work 'siger-iniciado.signal') 'inicio'
 Wait-File 'resultado-publico.json' ($plazo+120)
 $ackDeadline=(Get-Date).AddSeconds(30)
 do{$pending=[int]((SQL-Institucional 'SELECT COUNT(*) FROM institutional_inbox WHERE ack_pending=TRUE;')|Select-Object -Last 1);if($pending){Start-Sleep -Milliseconds 500}}while($pending -and (Get-Date) -lt $ackDeadline)
 if($pending){throw 'Quedan ACK pendientes de conciliación.'}
 $counts=(SQL-Institucional 'SELECT COUNT(*),COUNT(DISTINCT numero),COUNT(DISTINCT comunicacion_id) FROM communication_registry;')|Select-Object -Last 1
 if(@($counts -split "`t"|Where-Object {[int]$_ -ne $Cantidad}).Count){throw 'Conteos o unicidad institucional distintos del volumen generado'}
 $metrics=Invoke-RestMethod 'http://127.0.0.1:18081/operacion/v1/recuperacion/metricas' -Headers @{Authorization='Bearer '+$env:SIGER_OPERATIONS_TOKEN} -NoProxy -TimeoutSec 5
 $report=Get-Content (Join-Path $work 'resultado-publico.json') -Raw|ConvertFrom-Json
 $report|Add-Member -NotePropertyName metricasInstitucionales -NotePropertyValue $metrics
 $report|Add-Member -NotePropertyName ackPendientesAlFinal -NotePropertyValue $pending
 $report|ConvertTo-Json -Depth 5|Set-Content (Join-Path $repo 'formulario-publico/backend/target/carga-acumulada.json')
 Set-Content (Join-Path $work 'fin.signal') 'verificado'
 if(!$producer.WaitForExit(45000)){throw 'El test no terminó después de la verificación.'}
 if($producer.ExitCode -ne 0){throw 'Falló la prueba; revisar log privado.'}
 $report|ConvertTo-Json -Depth 5
 Write-Host 'Todos los registros, folios, comprobantes y ACK fueron comprobados en ambos almacenes.'
}finally{
 if($institution){$institution.Refresh();if(!$institution.HasExited){Stop-Process -Id $institution.Id;$institution.WaitForExit(10000)|Out-Null}}
 if($producer){
  $producer.Refresh()
  if(!$producer.HasExited){
   Set-Content (Join-Path $work 'fin.signal') 'fin'
   # Dar salida normal al test; si falló antes de leer la señal, cerrar solo el árbol que creamos.
   if(!$producer.WaitForExit(10000)){$producer.Kill($true);$producer.WaitForExit(10000)|Out-Null}
  }
 }
 foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')};$secret=$null
}
