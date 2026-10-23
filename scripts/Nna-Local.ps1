#Requires -Version 7.4
[CmdletBinding()]
param([ValidateSet('iniciar','publico','siger','demostrar','estado','detener','detener-publico','detener-siger','pruebas')][string]$Accion='estado',[switch]$SinClaveUnica,[switch]$NucleoInstitucional,[string]$OrigenFuncionario=$env:NUCLEO_LOCAL_ORIGIN)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$work=Join-Path $repo '.local/nna'
$keys=Join-Path $repo '.local/robustez/recepcion'
$domain='claveunica.formularioadulto.defensorianinez.gob.cl'
$origin='https://'+$domain
$statePath=Join-Path $work 'processes.json'
New-Item -ItemType Directory -Path $work -Force|Out-Null
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe $work /inheritance:r /grant:r ('*'+$sid+':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' /Q|Out-Null
if($LASTEXITCODE -ne 0){throw 'No se protegió el directorio de laboratorio.'}
$state=if(Test-Path -LiteralPath $statePath){Get-Content $statePath -Raw|ConvertFrom-Json -AsHashtable}else{@{}}
function Save-State {$state|ConvertTo-Json -Depth 5|Set-Content -LiteralPath $statePath}
function Stop-Owned([string]$name){
 if(!$state.ContainsKey($name)){return};$record=$state[$name];$process=Get-Process -Id $record.Id -ErrorAction SilentlyContinue
 if($process){
  $command=(Get-CimInstance Win32_Process -Filter ('ProcessId='+$record.Id)).CommandLine
  if($process.StartTime.ToUniversalTime().Ticks -ne ([DateTimeOffset]$record.StartedUtc).UtcDateTime.Ticks -or !$command.Contains($record.Argument)){throw "El proceso $name no coincide con el propietario registrado."}
  Stop-Process -Id $process.Id;$process.WaitForExit(10000)|Out-Null
 }
 $state.Remove($name);Save-State
}
if($Accion -eq 'detener'){foreach($name in @('siger','publico','proxy')){Stop-Owned $name};Write-Host 'Procesos NNA detenidos. MySQL y datos conservados.';return}
if($Accion -eq 'detener-publico'){Stop-Owned 'publico';Write-Host 'Backend público detenido; proxy y SIGER conservados.';return}
if($Accion -eq 'detener-siger'){Stop-Owned 'siger';Write-Host 'SIGER detenido; público y proxy conservados.';return}
if($Accion -eq 'pruebas'){& (Join-Path $PSScriptRoot 'Robustez.ps1') pruebas;return}
$staff=& (Join-Path $PSScriptRoot 'Origen-Funcionario.ps1') -Origen $OrigenFuncionario
if($Accion -eq 'estado'){
 foreach($port in @(443,$staff.Port,8080,8081,13306)){Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue|Select-Object LocalAddress,LocalPort}
 Write-Host ($origin+'/?formulario=nna');if($NucleoInstitucional){Write-Host ($staff.Origen+'/')};return
}
if($Accion -eq 'demostrar'){
 # Cliente ciudadano HTTPS, sin autenticación CU ni credencial técnica.
 $output=Join-Path $work ('evidencias/'+(Get-Date -Format 'yyyyMMdd-HHmmss'));New-Item -ItemType Directory -Path $output -Force|Out-Null
 & (Join-Path $PSScriptRoot 'Verificar-Nna-MySQL.ps1') antes -Evidencias $output
 $previousCA=[Environment]::GetEnvironmentVariable('NODE_EXTRA_CA_CERTS','Process')
 $env:NODE_EXTRA_CA_CERTS=Join-Path $repo '.local/https/ca/rootCA.pem'
 try {& node (Join-Path $PSScriptRoot 'Probar-Nna.mjs') $origin $output;if($LASTEXITCODE -ne 0){throw 'Demostración fallida'};& (Join-Path $PSScriptRoot 'Verificar-Nna-MySQL.ps1') despues -Evidencias $output;Set-Content (Join-Path $work 'ultima-prueba.txt') $output;Write-Host ('Evidencias: '+$output)}finally{[Environment]::SetEnvironmentVariable('NODE_EXTRA_CA_CERTS',$previousCA,'Process')}
 return
}
if($Accion -in @('iniciar','publico','siger')){
 $addresses=[Net.Dns]::GetHostAddresses($domain)
 if(!$addresses.Count -or @($addresses|Where-Object{![Net.IPAddress]::IsLoopback($_)}).Count){throw 'Se requiere la resolución hosts local ya autorizada a 127.0.0.1. Este script no modifica DNS ni hosts.'}
}
if($Accion -in @('iniciar','publico')){
 foreach($port in @(443,8080)){if((Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) -and !($port -eq 443 -and $state.ContainsKey('proxy') -and (Get-Process -Id $state.proxy.Id -ErrorAction SilentlyContinue))){throw "Puerto $port ocupado. Detenga la instancia anterior con su script; no se detendrá un proceso ajeno."}}
 foreach($file in @('.local/https/site.pem','.local/https/site-key.pem','.local/https/ca/rootCA.pem','.local/tools/caddy/caddy.exe')){if(!(Test-Path (Join-Path $repo $file))){throw 'Faltan las herramientas/certificados HTTPS locales previamente preparados.'}}
}
foreach($app in @('formulario-publico','siger-institucional')){if(!(Test-Path (Join-Path $repo ($app+'/backend/target/'+$app+'-0.1.0-SNAPSHOT.jar')))){throw 'Ejecute scripts/Build-Todo.ps1 antes de iniciar.'}}
# Provisión/migración solo de esquemas locales robust_*_local, nunca de bases institucionales.
& (Join-Path $PSScriptRoot 'Recepcion-Local.ps1') preparar
$secret=Import-Clixml (Join-Path $repo '.local/robustez/mysql/credentials.clixml')
$tokens=Import-Clixml (Join-Path $keys 'tokens.clixml')
function Plain($value){[Net.NetworkCredential]::new('',$value).Password}
function Spawn([string]$name,[string]$file,[string[]]$arguments,[hashtable]$environment,[string]$identity){
 $saved=@{};foreach($entry in $environment.GetEnumerator()){$saved[$entry.Key]=[Environment]::GetEnvironmentVariable($entry.Key,'Process');[Environment]::SetEnvironmentVariable($entry.Key,$entry.Value,'Process')}
 try{
  $p=Start-Process -FilePath $file -ArgumentList $arguments -WorkingDirectory $repo -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $work ($name+'.log')) -RedirectStandardError (Join-Path $work ($name+'-error.log'))
  $state[$name]=@{Id=$p.Id;StartedUtc=$p.StartTime.ToUniversalTime().ToString('o');Argument=$identity};Save-State
 }finally{foreach($entry in $saved.GetEnumerator()){[Environment]::SetEnvironmentVariable($entry.Key,$entry.Value,'Process')}}
}
function Wait-Http([string]$url){
 $deadline=(Get-Date).AddSeconds(45)
 do{try{Invoke-WebRequest $url -NoProxy -TimeoutSec 3|Out-Null;return}catch{Start-Sleep -Milliseconds 300}}while((Get-Date) -lt $deadline)
 throw 'El servicio local no respondió dentro del plazo; revisar .local/nna sin publicar sus logs.'
}
function Enable-Control([string]$url,[string]$name){
 $headers=@{Authorization='Bearer '+(Plain $tokens[$name])}
 $current=Invoke-WebRequest ($url+'/operacion/v1/control') -Headers $headers -NoProxy
 $headers['If-Match']=$current.Headers.ETag[0]
 Invoke-RestMethod ($url+'/operacion/v1/control') -Method Put -Headers $headers -ContentType 'application/json' -Body '{"aceptarNuevos":true,"recuperar":true,"contingencia":"NORMAL","motivo":"PRUEBA_LOCAL"}' -NoProxy|Out-Null
}
function Ensure-Proxy {
 if($state.ContainsKey('proxy')){
  $record=$state['proxy'];$process=Get-Process -Id $record.Id -ErrorAction SilentlyContinue
  if($process){
   $command=(Get-CimInstance Win32_Process -Filter ('ProcessId='+$record.Id)).CommandLine
   if($process.StartTime.ToUniversalTime().Ticks -ne ([DateTimeOffset]$record.StartedUtc).UtcDateTime.Ticks -or !$command.Contains($record.Argument)){throw 'Proxy ajeno o registro obsoleto.'}
   if($NucleoInstitucional -and (!(Test-Path $record.Argument) -or !(Get-Content $record.Argument -Raw).Contains($staff.Origen+' {'))){throw 'El proxy activo usa otro origen. Detenga sus procesos y reinicie con el origen deseado.'}
   return
  }
 }
 foreach($port in @(443,$staff.Port)){if(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue){throw "Puerto proxy $port ocupado; no se reemplaza un proceso ajeno."}}
  $caddyConfig=Join-Path $repo 'formulario-publico/config/local-sandbox/Caddyfile'
  if($NucleoInstitucional){
   # Reutiliza certificados y proxy existentes. El Caddyfile público original no cambia.
   $baseConfig=Get-Content -LiteralPath $caddyConfig -Raw
   $staffConfig=@'

STAFF_ORIGIN {
 bind 127.0.0.1 ::1
 tls {$SIGER_STAFF_TLS_CERT} {$SIGER_STAFF_TLS_KEY} {
  protocols tls1.2 tls1.3
 }
 reverse_proxy 127.0.0.1:8081 {
  header_up -Forwarded
  header_up X-Forwarded-Proto https
  header_up X-Forwarded-Host STAFF_AUTHORITY
  header_up X-Forwarded-Port STAFF_PORT
 }
}
'@
   $staff=& (Join-Path $PSScriptRoot 'Origen-Funcionario.ps1') -Origen $OrigenFuncionario -PrepararCertificado
   $staffConfig=$staffConfig.Replace('STAFF_ORIGIN',$staff.Origen).Replace('STAFF_AUTHORITY',([uri]$staff.Origen).Authority).Replace('STAFF_PORT',[string]$staff.Port)
   $caddyConfig=Join-Path $work 'Caddyfile-nucleo';[IO.File]::WriteAllText($caddyConfig,$baseConfig+$staffConfig)
  }
  Spawn 'proxy' (Join-Path $repo '.local/tools/caddy/caddy.exe') @('run','--config',('"'+$caddyConfig+'"'),'--adapter','caddyfile') @{SIGER_STAFF_TLS_CERT=$staff.Certificado.Replace('\','/');SIGER_STAFF_TLS_KEY=$staff.Clave.Replace('\','/');SIGER_TLS_CERT=(Join-Path $repo '.local/https/site.pem').Replace('\','/');SIGER_TLS_KEY=(Join-Path $repo '.local/https/site-key.pem').Replace('\','/');CU_CLIENT_ID=$null;CU_CLIENT_SECRET=$null} $caddyConfig

}
try{
 if($Accion -in @('iniciar','publico')){
  $jar=Join-Path $repo 'formulario-publico/backend/target/formulario-publico-0.1.0-SNAPSHOT.jar'
  $config=Join-Path $repo 'formulario-publico/config/local-sandbox/application-local-sandbox-https.yml'
  $envPublic=@{
   SPRING_CONFIG_LOCATION='classpath:/';SPRING_APPLICATION_JSON=$null;SPRING_CONFIG_ADDITIONAL_LOCATION=([URI]::new($config)).AbsoluteUri;SPRING_PROFILES_ACTIVE='local-sandbox-https,jdbc-reception,nna-lab'
   CU_CLIENT_ID='nna-lab-oidc-disabled';CU_CLIENT_SECRET='nna-lab-oidc-disabled';CU_ENVIRONMENT='SANDBOX';CU_ID_TOKEN_ALGORITHM='RS256';CU_CALLBACK_APPROVED='false'
   SIGER_COOKIE_SECURE='true';SIGER_BIND_ADDRESS='127.0.0.1';SIGER_PORT='8080';PUBLIC_NNA_ENABLED='true';LOCAL_DB_ALLOWED='true'
   PUBLIC_DB_URL='jdbc:mysql://127.0.0.1:13306/robust_public_local?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=3000&socketTimeout=5000'
   PUBLIC_DB_USER='publicLocalApp';PUBLIC_DB_PASSWORD=(Plain $secret.publicLocalApp)
   PUBLIC_INTEGRATION_TOKEN=(Plain $tokens.integracion);PUBLIC_OPERATIONS_TOKEN=(Plain $tokens.publico)
   PUBLIC_RECEPTION_ENABLED='true';PUBLIC_PRIVACY_APPROVED='true';PUBLIC_PRIVACY_VERSION='laboratorio-v1'
   RECEIPT_KEY_ID='local-ed25519-v1';RECEIPT_PRIVATE_KEY_PATH=(Join-Path $keys 'receipt-private.pem');RECEIPT_TRUSTED_KEYS_PATH=(Join-Path $keys 'receipt-trust.json');SIGER_RECEIPT_TRUSTED_KEYS_PATH=(Join-Path $keys 'institution-trust.json')
  }
  if(!$SinClaveUnica){
   # Solo Sandbox. No se seleccionan pares QA/Producción ni se guardan secretos en archivos nuevos.
   $cuPath=if($env:CU_SANDBOX_CREDENTIALS_PATH){$env:CU_SANDBOX_CREDENTIALS_PATH}else{Join-Path $repo '../CLAVEUNICATEST/DefensoriadelaNinez.txt'}
   if(!(Test-Path -LiteralPath $cuPath)){throw 'Falta el TXT institucional Sandbox; use -SinClaveUnica únicamente para pruebas NNA aisladas.'}
   $cuText=Get-Content -LiteralPath $cuPath -Raw
   $cuId=[regex]::Match($cuText,'(?im)^\s*Client ID SANDBOX\s*:\s*([^\r\n]+)')
   $cuSecret=[regex]::Match($cuText,'(?im)^\s*Client Secret SANDBOX\s*:\s*([^\r\n]+)')
   if(!$cuId.Success -or !$cuSecret.Success){throw 'No se encontró el par Sandbox institucional.'}
   $envPublic.CU_CLIENT_ID=$cuId.Groups[1].Value.Trim();$envPublic.CU_CLIENT_SECRET=$cuSecret.Groups[1].Value.Trim()
   $cuText=$null;$cuId=$null;$cuSecret=$null
  }
  Spawn 'publico' (Get-Command java).Source @('-jar',('"'+$jar+'"')) $envPublic $jar
  Ensure-Proxy
  Wait-Http ($origin+'/api/nna/capacidades')
  if(!$SinClaveUnica){
   # Comprobar origen/certificado/cookie/callback antes de habilitar. Solo peticiones locales; no seguir redirects.
   & (Join-Path $repo 'formulario-publico/scripts/Test-LocalSandbox.ps1') -ForActivation|Out-Null
   Stop-Owned 'publico'
   $envPublic.CU_CALLBACK_APPROVED='true'
   Spawn 'publico' (Get-Command java).Source @('-jar',('"'+$jar+'"')) $envPublic $jar
   Wait-Http ($origin+'/api/nna/capacidades')
   try{& (Join-Path $repo 'formulario-publico/scripts/Test-LocalSandbox.ps1') -RequireLoginEnabled|Out-Null}
   catch{Stop-Owned 'publico';throw 'No se validó el acceso Sandbox; backend público detenido para mantener el cierre seguro.'}
  }
  Enable-Control $origin 'publico'
 }
 if($Accion -in @('iniciar','siger')){
  if($NucleoInstitucional){Ensure-Proxy}
  if(Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue){throw '8081 ocupado. No se detendrá un servicio ajeno.'}
  $jar=Join-Path $repo 'siger-institucional/backend/target/siger-institucional-0.1.0-SNAPSHOT.jar'
  $envInst=@{
   NUCLEO_LAB_ENABLED='false';NUCLEO_ENVIRONMENT='CERRADO';NUCLEO_LAB_PASSWORD=$null
   SPRING_CONFIG_LOCATION='classpath:/';SPRING_CONFIG_ADDITIONAL_LOCATION=$null;SPRING_APPLICATION_JSON=$null;SPRING_PROFILES_ACTIVE='jdbc-reception';LOCAL_DB_ALLOWED='true';OPERATIONS_LOCAL_HTTP='true';CU_CLIENT_ID=$null;CU_CLIENT_SECRET=$null
   SIGER_INST_BIND_ADDRESS='127.0.0.1';SIGER_INST_PORT='8081';SIGER_DB_USER='institutionLocalApp';SIGER_DB_PASSWORD=(Plain $secret.institutionLocalApp)
   SIGER_DB_URL='jdbc:mysql://127.0.0.1:13306/robust_inst_local?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=3000&socketTimeout=5000'
   SIGER_OPERATIONS_TOKEN=(Plain $tokens.siger);SIGER_V2_ENABLED='true';SIGER_V2_LOCAL_HTTP='false';SIGER_V2_CA_PATH=(Join-Path $repo '.local/https/ca/rootCA.pem');SIGER_PUBLIC_SOURCE=$origin;SIGER_INTEGRATION_TOKEN=(Plain $tokens.integracion);SIGER_PULL_ENABLED='false'
   SIGER_RECEIPT_KEY_ID='institution-ed25519-v1';SIGER_RECEIPT_PRIVATE_KEY_PATH=(Join-Path $keys 'institution-private.pem');SIGER_RECEIPT_TRUSTED_KEYS_PATH=(Join-Path $keys 'institution-trust.json');RECEIPT_TRUSTED_KEYS_PATH=(Join-Path $keys 'receipt-trust.json')
  }
  if($NucleoInstitucional){
   $passwordPath=Join-Path $work 'nucleo-password.clixml'
   if(!(Test-Path -LiteralPath $passwordPath)){[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(36))|ConvertTo-SecureString -AsPlainText -Force|Export-Clixml -LiteralPath $passwordPath}
   $envInst.NUCLEO_LAB_PASSWORD=Plain (Import-Clixml -LiteralPath $passwordPath)
   $envInst.NUCLEO_LAB_ENABLED='true';$envInst.NUCLEO_ENVIRONMENT='LABORATORIO';$envInst.SPRING_PROFILES_ACTIVE='jdbc-reception,nucleo-lab'
   $config=Join-Path $repo 'siger-institucional/config/nucleo-lab/application-nucleo-lab-https.yml'
   $envInst.SPRING_CONFIG_ADDITIONAL_LOCATION=([URI]::new($config)).AbsoluteUri
  }
  Spawn 'siger' (Get-Command java).Source @('-jar',('"'+$jar+'"')) $envInst $jar
  Wait-Http 'http://127.0.0.1:8081/api/estado';Enable-Control 'http://127.0.0.1:8081' 'siger'
  if($NucleoInstitucional -and $Accion -eq 'iniciar'){Wait-Http ($staff.Origen+'/nucleo/session');Write-Host ('Núcleo institucional: '+$staff.Origen+'/')}
 }
 Write-Host ($origin+'/?formulario=nna')
 if($Accion -in @('iniciar','publico')){Write-Host $(if($SinClaveUnica){'ClaveÚnica deshabilitada explícitamente con -SinClaveUnica.'}else{'ClaveÚnica Sandbox habilitada y comprobada localmente: '+$origin+'/'})}
 Write-Host 'Solo datos sintéticos. SIGER inicia HTTPS saliente.'
}catch{
 Write-Warning 'Arranque incompleto. Use Nna-Local.ps1 detener para cerrar únicamente sus procesos registrados.';throw
}finally{$secret=$null;$tokens=$null;$envPublic=$null;$envInst=$null;$cuText=$null;$cuId=$null;$cuSecret=$null}




