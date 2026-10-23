#Requires -Version 7.4
param([string]$Origen=$env:NUCLEO_LOCAL_ORIGIN,[switch]$PrepararCertificado)
$ErrorActionPreference='Stop'
if(!$Origen){$Origen='https://localhost:8443'}
$uri=[uri]$Origen
if(!$uri.IsAbsoluteUri -or $uri.Scheme -ne 'https' -or $uri.Host -notmatch '^(localhost|[a-z0-9-]+\.localhost)$' -or $uri.Port -lt 1024 -or $uri.Port -in @(8080,8081,13306) -or $uri.UserInfo -or $uri.Query -or $uri.Fragment -or $uri.AbsolutePath -ne '/') {throw 'Origen funcionario: HTTPS, hostname localhost o *.localhost resuelto, puerto propio >=1024, sin ruta ni credenciales.'}
try{$addresses=[Net.Dns]::GetHostAddresses($uri.Host)}catch{throw 'El hostname funcionario no resuelve mediante Windows. Use https://localhost:8443 o gestione su resolución local con autorización; no se modifica hosts.'}
if(!$addresses.Count -or @($addresses|Where-Object{![Net.IPAddress]::IsLoopback($_)}).Count){throw 'El origen funcionario debe resolver exclusivamente a loopback.'}
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$directory=Join-Path $repo '.local/nna/https'
$cert=Join-Path $directory ($uri.Host+'-'+$uri.Port+'.pem');$key=Join-Path $directory ($uri.Host+'-'+$uri.Port+'-key.pem')
if($PrepararCertificado){
 $ca=Join-Path $repo '.local/https/ca'
 $root=[Security.Cryptography.X509Certificates.X509Certificate2]::CreateFromPem([IO.File]::ReadAllText((Join-Path $ca 'rootCA.pem')))
 if(!(Test-Path ('Cert:/CurrentUser/Root/'+$root.Thumbprint))){throw 'Falta confianza en la CA previamente preparada. No se instala unilateralmente; coordinar autorización local.'}
 New-Item -ItemType Directory -Path $directory -Force|Out-Null
 if(!(Test-Path $cert) -and !(Test-Path $key)){
  $saved=$env:CAROOT
  try{$env:CAROOT=$ca;& (Join-Path $repo '.local/tools/mkcert/mkcert.exe') -cert-file $cert -key-file $key $uri.Host *> (Join-Path $directory 'emision.log');if($LASTEXITCODE -ne 0){throw 'No se pudo emitir certificado funcionario separado.'}}finally{$env:CAROOT=$saved}
 }
 if(!(Test-Path $cert) -or !(Test-Path $key)){throw 'Par de certificado funcionario incompleto; no se sobrescribe.'}
 $leaf=[Security.Cryptography.X509Certificates.X509Certificate2]::CreateFromPem([IO.File]::ReadAllText($cert))
 if($leaf.NotAfter -lt (Get-Date) -or $leaf.NotBefore -gt (Get-Date) -or $leaf.GetNameInfo([Security.Cryptography.X509Certificates.X509NameType]::DnsName,$false) -ne $uri.Host){throw 'Certificado funcionario vencido o nombre incorrecto; no se desactiva validación TLS.'}
}
[pscustomobject]@{Origen=$uri.GetLeftPart([UriPartial]::Authority);HostName=$uri.Host;Port=$uri.Port;Certificado=$cert;Clave=$key}

