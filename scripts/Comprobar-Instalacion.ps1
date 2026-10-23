#Requires -Version 7.4
[CmdletBinding()]
param([switch]$SoloHerramientas,[switch]$ConClaveUnica)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$results=[Collections.Generic.List[object]]::new()
function Check([string]$nombre,[bool]$ok,[string]$detalle){
 $results.Add([pscustomobject]@{Comprobacion=$nombre;Correcto=$ok;Detalle=$detalle})
}
function Tool([string]$command,[string[]]$arguments,[string]$pattern,[string]$requirement){
 $found=Get-Command $command -ErrorAction SilentlyContinue
 if(!$found){Check $command $false $requirement;return}
 try{$output=(& $found.Source @arguments 2>&1|Out-String);$ok=$LASTEXITCODE -eq 0 -and $output -match $pattern;Check $command $ok $requirement}catch{Check $command $false $requirement}
}
Check 'Windows x64' ($IsWindows -and [Runtime.InteropServices.RuntimeInformation]::OSArchitecture -eq 'X64') 'Los binarios locales fijados son Windows x64.'
Tool 'git.exe' @('--version') 'git version \d+' 'Git instalado y disponible en PATH.'
Tool 'java.exe' @('-version') 'version "25[.\"]' 'JDK 25 activo en PATH.'
Tool 'javac.exe' @('--version') 'javac 25(?:\.|\s|$)' 'Compilador JDK 25, no solamente un JRE.'
Tool 'mvn.cmd' @('-version') '(?s)Apache Maven 3\.9\..*Java version: 25(?:\.|,)' 'Maven 3.9.x ejecutándose con Java 25; revisar JAVA_HOME.'
Tool 'node.exe' @('--version') '^v24\.' 'Node.js 24.'
Tool 'npm.cmd' @('--version') '^\d+\.\d+' 'npm disponible en PATH.'
$runtime=Get-ItemProperty -LiteralPath 'HKLM:\SOFTWARE\Microsoft\VisualStudio\14.0\VC\Runtimes\x64' -ErrorAction SilentlyContinue
Check 'Visual C++ x64' ($null -ne $runtime -and $runtime.Installed -eq 1) 'Instalar Microsoft Visual C++ v14 Redistributable x64 si falta (ver manual).'
if(!$SoloHerramientas){
 foreach($app in @('formulario-publico','siger-institucional')){
  Check ($app+' fuente') (Test-Path -LiteralPath (Join-Path $repo ($app+'/backend/pom.xml'))) 'Conectar ambos clones antes de usar el laboratorio integrado.'
  Check ($app+' JAR') (Test-Path -LiteralPath (Join-Path $repo ($app+'/backend/target/'+$app+'-0.1.0-SNAPSHOT.jar'))) 'Ejecutar el build de esta aplicación antes de iniciar.'
 }
 foreach($file in @('.local/tools/caddy/caddy.exe','.local/tools/mkcert/mkcert.exe','.local/https/site.pem','.local/https/site-key.pem','.local/https/ca/rootCA.pem')){
  Check $file (Test-Path -LiteralPath (Join-Path $repo $file)) 'Ejecutar Prepare-LocalSandboxTools.ps1 desde la raíz operativa elegida.'
 }
 $ca=Join-Path $repo '.local/https/ca/rootCA.pem'
 try{
  $root=[Security.Cryptography.X509Certificates.X509Certificate2]::CreateFromPem([IO.File]::ReadAllText($ca))
  Check 'Confianza HTTPS local' (Test-Path ('Cert:/CurrentUser/Root/'+$root.Thumbprint)) 'CA de esta raíz operativa confiada en CurrentUser/Root; requiere una operación explícita del usuario.'
 }catch{Check 'Confianza HTTPS local' $false 'Falta una CA local válida. No se instala ni se cambia confianza desde este comprobador.'}
 try{
  $leaf=[Security.Cryptography.X509Certificates.X509Certificate2]::CreateFromPem([IO.File]::ReadAllText((Join-Path $repo '.local/https/site.pem')))
  Check 'Certificado público vigente' ($leaf.NotBefore -le (Get-Date) -and $leaf.NotAfter -gt (Get-Date) -and $leaf.GetNameInfo([Security.Cryptography.X509Certificates.X509NameType]::DnsName,$false) -eq 'claveunica.formularioadulto.defensorianinez.gob.cl') 'Nombre institucional exacto y fechas válidas; el preflight de arranque verificará TLS efectivo.'
 }catch{Check 'Certificado público vigente' $false 'Certificado público local ausente o ilegible.'}
 try{
  $addresses=[Net.Dns]::GetHostAddresses('claveunica.formularioadulto.defensorianinez.gob.cl')
  Check 'Resolución pública local' ($addresses.Count -gt 0 -and @($addresses|Where-Object{![Net.IPAddress]::IsLoopback($_)}).Count -eq 0) 'El nombre registrado debe resolver exclusivamente a loopback mediante Windows.'
 }catch{Check 'Resolución pública local' $false 'Preparar hosts con el script explícito y elevado; no cambiar DNS público.'}
 try{& (Join-Path $PSScriptRoot 'Origen-Funcionario.ps1')|Out-Null;Check 'Origen funcionario' $true 'HTTPS separado, localhost y puerto propio.'}catch{Check 'Origen funcionario' $false 'Revisar NUCLEO_LOCAL_ORIGIN y su resolución loopback.'}
}
if($ConClaveUnica){
 $path=if($env:CU_SANDBOX_CREDENTIALS_PATH){$env:CU_SANDBOX_CREDENTIALS_PATH}else{Join-Path $repo '../CLAVEUNICATEST/DefensoriadelaNinez.txt'}
 try{
  $text=[IO.File]::ReadAllText($path)
  $valid=$text -match '(?im)^\s*Client ID SANDBOX\s*:\s*\S+' -and $text -match '(?im)^\s*Client Secret SANDBOX\s*:\s*\S+'
  Check 'TXT Sandbox externo' $valid 'Dos líneas Sandbox legibles; no se imprimen ni se valida su aceptación por el proveedor.'
 }catch{Check 'TXT Sandbox externo' $false 'Definir CU_SANDBOX_CREDENTIALS_PATH hacia el TXT privado, fuera de Git.'}finally{$text=$null}
}
$results|Format-Table -AutoSize -Wrap
if(@($results|Where-Object{!$_.Correcto}).Count){throw 'Instalación incompleta: resolver las comprobaciones marcadas False. No se modificó la configuración del equipo.'}
Write-Host 'Comprobaciones locales correctas. No se iniciaron servicios ni se realizó autenticación gubernamental.'
