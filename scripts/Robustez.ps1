#Requires -Version 7.4
[CmdletBinding()]
param([ValidateSet('preparar','pruebas','carga','acumuladas','recuperacion','pisee','estado','detener-db')][string]$Accion='pruebas')
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if($Accion -eq 'pisee'){& (Join-Path $PSScriptRoot 'Auditar-PISEE.ps1') -Accion lecturas;return}
if($Accion -eq 'acumuladas'){& (Join-Path $PSScriptRoot 'Probar-Comunicaciones-Acumuladas.ps1');return}
if($Accion -eq 'estado'){& (Join-Path $PSScriptRoot 'Local-MySQL.ps1') estado;return}
if($Accion -eq 'detener-db'){& (Join-Path $PSScriptRoot 'Local-MySQL.ps1') detener;return}
if(Test-Path -LiteralPath (Join-Path $repo '.local/robustez/recepcion/laboratorio-activo.txt')){throw 'Detenga primero la recepción de laboratorio: scripts/Recepcion-Local.ps1 detener. Las pruebas limpian exclusivamente sus esquemas de prueba.'}
& (Join-Path $PSScriptRoot 'Local-MySQL.ps1') iniciar
if($Accion -eq 'preparar'){return}
foreach($tool in @('java','mvn')){if(!(Get-Command $tool -ErrorAction SilentlyContinue)){throw "Falta $tool"}}
$secret=Import-Clixml -LiteralPath (Join-Path $repo '.local/robustez/mysql/credentials.clixml')
$saved=@{};Get-ChildItem Env:ROBUST_DB_*|ForEach-Object{$saved[$_.Name]=$_.Value}
$mysqlInterrupted=$false
function Set-TestEnvironment([string]$app){
 $isPublic=$app -eq 'formulario-publico';$schema=if($isPublic){'robust_public_test'}else{'robust_inst_test'}
 $user=if($isPublic){'publicApp'}else{'institutionApp'};$migration=if($isPublic){'publicMigration'}else{'institutionMigration'}
 $env:ROBUST_DB_URL='jdbc:mysql://127.0.0.1:13306/'+$schema+'?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=3000&socketTimeout=5000'
 $env:ROBUST_DB_USER=$user;$env:ROBUST_DB_PASSWORD=[Net.NetworkCredential]::new('',$secret.$user).Password
 $env:ROBUST_DB_MIGRATION_USER=$migration;$env:ROBUST_DB_MIGRATION_PASSWORD=[Net.NetworkCredential]::new('',$secret.$migration).Password
}
function Wait-Signal([string]$path,$process){
 $deadline=(Get-Date).AddSeconds(100)
 while(!(Test-Path -LiteralPath $path)){
  $process.Refresh();if($process.HasExited){throw 'La prueba terminó antes de emitir la señal. Revise el log privado.'}
  if((Get-Date) -gt $deadline){throw 'Se agotó el plazo de la prueba de recuperación.'};Start-Sleep -Milliseconds 200
 }
}
try{
 if($Accion -eq 'recuperacion'){
  Set-TestEnvironment 'formulario-publico'
  $backend=Join-Path $repo 'formulario-publico/backend';$target=Join-Path $backend 'target'
  foreach($name in @('recuperacion-lista.json','recuperacion-caida.signal','recuperacion-sin-db.json','recuperacion-reinicio.signal','recuperacion-resultado.json')){
   $file=Join-Path $target $name;if(Test-Path -LiteralPath $file){Remove-Item -LiteralPath $file}
  }
  $maven=(Get-Command mvn.cmd).Source
  $process=Start-Process -FilePath $env:ComSpec -ArgumentList @('/d','/c',('""'+$maven+'" -B -ntp -Dtest=RecuperacionProcesoTest -Drecuperacion.real=true test"')) -WorkingDirectory $backend -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $repo '.local/recuperacion-maven.log') -RedirectStandardError (Join-Path $repo '.local/recuperacion-error.log')
  Wait-Signal (Join-Path $target 'recuperacion-lista.json') $process
  $state=Get-Content (Join-Path $repo '.local/robustez/mysql/process.json') -Raw|ConvertFrom-Json
  $owned=Get-Process -Id $state.Id
  $command=(Get-CimInstance Win32_Process -Filter ('ProcessId='+$state.Id)).CommandLine
  if($owned.StartTime.ToUniversalTime().Ticks -ne ([DateTimeOffset]$state.StartedUtc).UtcDateTime.Ticks -or !$command.Contains((Join-Path $repo '.local/robustez/mysql'))){throw 'El proceso MySQL no pertenece al laboratorio.'}
  Write-Host 'Simulando caída abrupta exclusivamente del MySQL portable de pruebas.'
  $mysqlInterrupted=$true
  if($state.ParentId -and $state.ParentId -ne $state.Id){
   $supervisor=Get-CimInstance Win32_Process -Filter ('ProcessId='+$state.ParentId)
   if($supervisor -and $supervisor.Name -eq 'mysqld.exe' -and $supervisor.CommandLine.Contains((Join-Path $repo '.local/robustez/mysql'))){Stop-Process -Id $state.ParentId}
  }
  Stop-Process -Id $owned.Id;$owned.WaitForExit(10000)|Out-Null
  Set-Content (Join-Path $target 'recuperacion-caida.signal') 'caida'
  Wait-Signal (Join-Path $target 'recuperacion-sin-db.json') $process
  & (Join-Path $PSScriptRoot 'Local-MySQL.ps1') iniciar
  $mysqlInterrupted=$false
  Set-Content (Join-Path $target 'recuperacion-reinicio.signal') 'reinicio'
  if(!$process.WaitForExit(45000)){throw 'La recuperación no terminó dentro del plazo.'}
  if($process.ExitCode -ne 0){throw 'Falló la prueba de recuperación; consulte .local/recuperacion-maven.log.'}
  Get-Content (Join-Path $target 'recuperacion-resultado.json');return
 }
 $apps=if($Accion -eq 'carga'){@('formulario-publico')}else{@('formulario-publico','siger-institucional')}
 foreach($app in $apps){
  Set-TestEnvironment $app
  Push-Location (Join-Path $repo ($app+'/backend'))
  try{
   $options=@('-B','-ntp');if($Accion -eq 'carga'){$options+=@('-Dtest=CargaHttpTest','-Dcarga.real=true')}
   & mvn @options test
   if($LASTEXITCODE -ne 0){throw "Pruebas fallidas en $app"}
  }finally{Pop-Location}
 }
 if($Accion -eq 'carga'){Get-Content (Join-Path $repo 'formulario-publico/backend/target/carga-http.json')}
}finally{
 if($mysqlInterrupted){try{& (Join-Path $PSScriptRoot 'Local-MySQL.ps1') iniciar}catch{Write-Warning 'No se pudo reiniciar MySQL tras el fallo de la prueba; revisar logs privados.'}}
 Get-ChildItem Env:ROBUST_DB_*|Remove-Item
 foreach($entry in $saved.GetEnumerator()){[Environment]::SetEnvironmentVariable($entry.Key,$entry.Value,'Process')}
 $secret=$null
}
