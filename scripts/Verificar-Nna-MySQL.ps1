#Requires -Version 7.4
[CmdletBinding()]
param([ValidateSet('antes','despues')][string]$Accion='despues',[string]$Evidencias)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$private=Join-Path $repo '.local/robustez/mysql'
$output=if($Evidencias){[IO.Path]::GetFullPath($Evidencias)}else{Join-Path $repo 'docs/evidencias/nna-2026-10-19'}
New-Item -ItemType Directory -Path $output -Force|Out-Null
$secret=Import-Clixml (Join-Path $private 'credentials.clixml')
function Query([string]$user,[string]$schema,[string]$sql){
 $key=[Guid]::NewGuid().ToString('N');$cnf=Join-Path $private ($key+'.cnf');$inputFile=Join-Path $private ($key+'.sql');$resultFile=Join-Path $private ($key+'.out');$errorFile=Join-Path $private ($key+'.err')
 try{
  $password=[Net.NetworkCredential]::new('',$secret.$user).Password
  [IO.File]::WriteAllText($cnf,"[client]`nuser=$user`npassword=$password`nhost=127.0.0.1`nport=13306`ndatabase=$schema`n")
  [IO.File]::WriteAllText($inputFile,$sql)
  $p=Start-Process -FilePath (Join-Path $repo '.local/tools/mysql/mysql-8.4.11-winx64/bin/mysql.exe') -ArgumentList @(('--defaults-extra-file="'+$cnf+'"'),'--batch','--raw','--skip-column-names','--default-character-set=utf8mb4') -WindowStyle Hidden -PassThru -Wait -RedirectStandardInput $inputFile -RedirectStandardOutput $resultFile -RedirectStandardError $errorFile
  if($p.ExitCode -ne 0){throw 'Falló la comprobación MySQL local. No se publican sus logs.'}
  Get-Content -LiteralPath $resultFile -Encoding utf8
 }finally{foreach($file in @($cnf,$inputFile,$resultFile,$errorFile)){if(Test-Path -LiteralPath $file){Remove-Item -LiteralPath $file}};$password=$null}
}
try{
 $hashes=@{}
 foreach($line in @(Query 'publicLocalApp' 'robust_public_local' "SELECT id,SHA2(CONCAT(body,'|',COALESCE(ack,'')),256) FROM public_delivery ORDER BY id;")){
  $parts=$line.Split("`t");$hashes['publico:'+ $parts[0]]=$parts[1]
 }
 foreach($line in @(Query 'institutionLocalApp' 'robust_inst_local' "SELECT id,SHA2(CONCAT(body,'|',receipt,'|',content_hash),256) FROM institutional_inbox ORDER BY id;")){
  $parts=$line.Split("`t");$hashes['siger:'+ $parts[0]]=$parts[1]
 }
 if($Accion -eq 'antes'){$hashes|ConvertTo-Json|Set-Content (Join-Path $output 'historicos-antes.json');Write-Host 'Huellas históricas guardadas sin leer datos personales.';return}
 $before=Get-Content (Join-Path $output 'historicos-antes.json') -Raw|ConvertFrom-Json -AsHashtable
 foreach($entry in $before.GetEnumerator()){if($hashes[$entry.Key] -ne $entry.Value){throw 'Una huella histórica cambió; revisar antes de declarar compatibilidad.'}}
 $result=Get-Content (Join-Path $output 'resultado-http.json') -Raw|ConvertFrom-Json
 $id=$result.operacionId
 if($id -notmatch '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$'){throw 'Operación inválida'}
 $public=Query 'publicLocalApp' 'robust_public_local' "SELECT JSON_OBJECT('estado',state,'sinOwnerRun',owner_run IS NULL,'sesionPresente',owner_session IS NOT NULL,'body',CAST(body AS JSON),'ack',CAST(ack AS JSON)) FROM public_delivery WHERE id='$id';"|ConvertFrom-Json
 $inst=Query 'institutionLocalApp' 'robust_inst_local' "SELECT JSON_OBJECT('ackPendiente',ack_pending,'body',CAST(body AS JSON),'ack',CAST(receipt AS JSON)) FROM institutional_inbox WHERE id='$id';"|ConvertFrom-Json
 $registered=[int](Query 'institutionLocalApp' 'robust_inst_local' "SELECT COUNT(*) FROM communication_registry WHERE operacion_id='$id';")
 if($registered -ne 1){throw 'El número de registros institucionales por operación es incorrecto.'}
 if(!$public -or !$inst -or $public.estado -ne 'CONFIRMADA' -or !$public.sinOwnerRun -or !$public.sesionPresente -or $inst.ackPendiente){throw 'Custodia/ACK no confirmados en ambas bases.'}
 if(($public.body|ConvertTo-Json -Depth 20 -Compress) -cne ($inst.body|ConvertTo-Json -Depth 20 -Compress) -or ($public.ack|ConvertTo-Json -Depth 20 -Compress) -cne ($inst.ack|ConvertTo-Json -Depth 20 -Compress)){throw 'Cuerpo o ACK distintos entre aplicaciones.'}
 if($public.body.version -ne '2.1' -or $public.body.comunicacion.declaracionNna.runNumero -or $public.body.comunicacion.identidad){throw 'La variante declarada fue alterada.'}
 foreach($attribute in $public.body.procedencia.PSObject.Properties.Value){if($attribute.fuente -ne 'CIUDADANO' -or $attribute.nivel -ne 'DECLARADO_NO_CONTRASTADO'){throw 'Procedencia incorrecta'}}
 [pscustomobject]@{operacionId=$id;historicosComparados=$before.Count;historicosSinCambios=$true;persistidaEnAmbasBases=$true;cuerpoYACKIguales=$true;ackPendiente=$false;runAusente=$true;ownerSessionPresente=$true;registrosInstitucionales=$registered;folio=$public.ack.registro.folio;datosSinteticos=$public.body.comunicacion.declaracionNna;procedencia=$public.body.procedencia}|ConvertTo-Json -Depth 10|Set-Content (Join-Path $output 'verificacion-mysql.json')
 Write-Host ('MySQL: ambos registros y ACK comprobados; '+$before.Count+' históricos intactos.')
}finally{$secret=$null}
