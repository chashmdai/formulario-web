#Requires -Version 7.4
[CmdletBinding()]
param([ValidateSet('preparar','publico','siger','habilitar','estado','detener')][string]$Accion='estado')
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$private=Join-Path $repo '.local/robustez/recepcion'
$marker=Join-Path $private 'laboratorio-activo.txt'
if($Accion -eq 'estado'){
 $tokenPath=Join-Path $private 'tokens.clixml'
 if(!(Test-Path -LiteralPath $tokenPath)){Write-Host 'Recepción durable local no preparada.';return}
 $readTokens=Import-Clixml -LiteralPath $tokenPath
 try{
  foreach($target in @(@('https://claveunica.formularioadulto.defensorianinez.gob.cl','publico'),@('http://127.0.0.1:8081','siger'))){
   $headers=@{Authorization='Bearer '+[Net.NetworkCredential]::new('',$readTokens[$target[1]]).Password}
   try{Invoke-RestMethod ($target[0]+'/operacion/v1/entregas') -Headers $headers -NoProxy -TimeoutSec 5|ConvertTo-Json -Depth 5}catch{Write-Host ('No disponible: '+$target[1])}
  }
 }finally{$readTokens=$null};return
}
if($Accion -eq 'detener'){
 & (Join-Path $repo 'formulario-publico/scripts/Formulario.ps1') detener
 & (Join-Path $repo 'siger-institucional/scripts/Siger.ps1') detener
 if(Test-Path -LiteralPath $marker){Remove-Item -LiteralPath $marker};return
}
& (Join-Path $PSScriptRoot 'Local-MySQL.ps1') iniciar
New-Item -ItemType Directory -Path $private -Force|Out-Null
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe $private /inheritance:r /grant:r ('*'+$sid+':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' /Q|Out-Null
if($LASTEXITCODE -ne 0){throw 'No se protegió el directorio privado.'}
& node (Join-Path $PSScriptRoot 'Crear-Claves-Comprobante.mjs') $private
if($LASTEXITCODE -ne 0){throw 'No se pudo preparar el firmante.'}
& node (Join-Path $PSScriptRoot 'Crear-Claves-Comprobante.mjs') $private --institucional
if($LASTEXITCODE -ne 0){throw 'No se pudo preparar el firmante institucional.'}
$tokensPath=Join-Path $private 'tokens.clixml'
if(!(Test-Path -LiteralPath $tokensPath)){
 $tokens=@{};foreach($name in @('integracion','publico','siger')){$tokens[$name]=ConvertTo-SecureString ([Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))) -AsPlainText -Force}
 $tokens|Export-Clixml -LiteralPath $tokensPath
}
$tokens=Import-Clixml -LiteralPath $tokensPath
if($Accion -eq 'preparar'){& (Join-Path $PSScriptRoot 'Preparar-Datos-Recepcion.ps1')}
$dbSecrets=Import-Clixml -LiteralPath (Join-Path $repo '.local/robustez/mysql/credentials.clixml')
$names=@('SIGER_RECEIPT_KEY_ID','SIGER_RECEIPT_PRIVATE_KEY_PATH','SIGER_RECEIPT_TRUSTED_KEYS_PATH','LOCAL_DB_ALLOWED','MIGRATION_DB_URL','MIGRATION_DB_USER','MIGRATION_DB_PASSWORD','PUBLIC_DURABLE_ENABLED','PUBLIC_DEMO_ENABLED','PUBLIC_DB_URL','PUBLIC_DB_USER','PUBLIC_DB_PASSWORD','PUBLIC_OPERATIONS_TOKEN','PUBLIC_INTEGRATION_TOKEN','PUBLIC_RECEPTION_ENABLED','PUBLIC_PRIVACY_APPROVED','PUBLIC_PRIVACY_VERSION','RECEIPT_KEY_ID','RECEIPT_PRIVATE_KEY_PATH','RECEIPT_TRUSTED_KEYS_PATH','OPERATIONS_LOCAL_HTTP','SIGER_DB_URL','SIGER_DB_USER','SIGER_DB_PASSWORD','SIGER_OPERATIONS_TOKEN','SIGER_V2_ENABLED','SIGER_V2_LOCAL_HTTP','SIGER_V2_CA_PATH','SIGER_PUBLIC_SOURCE','SIGER_INTEGRATION_TOKEN')
$saved=@{};foreach($name in $names){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
function Plain($value){[Net.NetworkCredential]::new('',$value).Password}
try{
 $env:LOCAL_DB_ALLOWED='true';$env:OPERATIONS_LOCAL_HTTP='true'
 $env:SIGER_RECEIPT_KEY_ID='institution-ed25519-v1';$env:SIGER_RECEIPT_TRUSTED_KEYS_PATH=Join-Path $private 'institution-trust.json'
 $env:RECEIPT_KEY_ID='local-ed25519-v1';$env:RECEIPT_PRIVATE_KEY_PATH=Join-Path $private 'receipt-private.pem';$env:RECEIPT_TRUSTED_KEYS_PATH=Join-Path $private 'receipt-trust.json'
 if($Accion -eq 'preparar'){
  foreach($app in @('formulario-publico','siger-institucional')){
   $isPublic=$app -eq 'formulario-publico';$schema=if($isPublic){'robust_public_local'}else{'robust_inst_local'};$user=if($isPublic){'publicLocalMigration'}else{'institutionLocalMigration'}
   $jar=Join-Path $repo ($app+'/backend/target/'+$app+'-0.1.0-SNAPSHOT.jar')
   if(!(Test-Path -LiteralPath $jar)){throw 'Primero ejecute scripts/Build-Todo.ps1.'}
   $env:MIGRATION_DB_URL='jdbc:mysql://127.0.0.1:13306/'+$schema+'?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=3000&socketTimeout=5000'
   $env:MIGRATION_DB_USER=$user;$env:MIGRATION_DB_PASSWORD=Plain $dbSecrets.$user
   & java -jar $jar --migrate
   if($LASTEXITCODE -ne 0){throw "Migración fallida en $app"}
  };return
 }
 if($Accion -eq 'publico'){
  $env:SIGER_RECEIPT_PRIVATE_KEY_PATH=$null
  $env:PUBLIC_DURABLE_ENABLED='true';$env:PUBLIC_DEMO_ENABLED='false'
  $env:PUBLIC_DB_URL='jdbc:mysql://127.0.0.1:13306/robust_public_local?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=3000&socketTimeout=5000'
  $env:PUBLIC_DB_USER='publicLocalApp';$env:PUBLIC_DB_PASSWORD=Plain $dbSecrets.publicLocalApp
  $env:PUBLIC_OPERATIONS_TOKEN=Plain $tokens.publico;$env:PUBLIC_INTEGRATION_TOKEN=Plain $tokens.integracion
  $env:PUBLIC_RECEPTION_ENABLED='true';$env:PUBLIC_PRIVACY_APPROVED='true';$env:PUBLIC_PRIVACY_VERSION='laboratorio-v1'
  Set-Content -LiteralPath $marker 'Datos sintéticos exclusivamente. No es un canal institucional habilitado.'
  & (Join-Path $repo 'formulario-publico/scripts/Formulario.ps1') iniciar -NoAbrirNavegador;return
 }
 if($Accion -eq 'siger'){
  $env:RECEIPT_PRIVATE_KEY_PATH=$null
  $env:SIGER_RECEIPT_PRIVATE_KEY_PATH=Join-Path $private 'institution-private.pem'
  $env:SIGER_DB_URL='jdbc:mysql://127.0.0.1:13306/robust_inst_local?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=3000&socketTimeout=5000'
  $env:SIGER_DB_USER='institutionLocalApp';$env:SIGER_DB_PASSWORD=Plain $dbSecrets.institutionLocalApp
  $env:SIGER_OPERATIONS_TOKEN=Plain $tokens.siger;$env:SIGER_INTEGRATION_TOKEN=Plain $tokens.integracion
  $env:SIGER_PUBLIC_SOURCE='https://claveunica.formularioadulto.defensorianinez.gob.cl';$env:SIGER_V2_ENABLED='true';$env:SIGER_V2_LOCAL_HTTP='false'
  $env:SIGER_V2_CA_PATH=Join-Path $repo '.local/https/ca/rootCA.pem'
  Set-Content -LiteralPath $marker 'Datos sintéticos exclusivamente. No es un canal institucional habilitado.'
  & (Join-Path $repo 'siger-institucional/scripts/Siger.ps1') iniciar -RecepcionJdbc;return
 }
 foreach($target in @(@('https://claveunica.formularioadulto.defensorianinez.gob.cl','publico'),@('http://127.0.0.1:8081','siger'))){
  $headers=@{Authorization='Bearer '+(Plain $tokens[$target[1]])}
  try{
   $control=Invoke-WebRequest ($target[0]+'/operacion/v1/control') -Headers $headers -NoProxy -TimeoutSec 6
   if($Accion -eq 'habilitar'){
    $headers['If-Match']=$control.Headers.ETag[0]
    Invoke-RestMethod ($target[0]+'/operacion/v1/control') -Method Put -Headers $headers -ContentType 'application/json' -Body '{"aceptarNuevos":true,"recuperar":true,"contingencia":"NORMAL","motivo":"PRUEBA_LOCAL"}' -NoProxy|Out-Null
   }
   Invoke-RestMethod ($target[0]+'/operacion/v1/entregas') -Headers $headers -NoProxy|ConvertTo-Json -Depth 5
  }catch{Write-Host ('No disponible: '+$target[1]);if($Accion -eq 'habilitar'){throw}}
 }
}finally{foreach($entry in $saved.GetEnumerator()){[Environment]::SetEnvironmentVariable($entry.Key,$entry.Value,'Process')};$dbSecrets=$null;$tokens=$null}
