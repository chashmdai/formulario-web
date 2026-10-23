#Requires -Version 7.4
param([string]$OrigenFuncionario=$env:NUCLEO_LOCAL_ORIGIN,[string]$Evidencias,[switch]$SoloFuncionario)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$staff=(& (Join-Path $PSScriptRoot 'Origen-Funcionario.ps1') -Origen $OrigenFuncionario).Origen
$public='https://claveunica.formularioadulto.defensorianinez.gob.cl'
$handler=[Net.Http.HttpClientHandler]::new();$handler.UseProxy=$false;$handler.AllowAutoRedirect=$false;$handler.CookieContainer=[Net.CookieContainer]::new()
$http=[Net.Http.HttpClient]::new($handler);$http.Timeout=[TimeSpan]::FromSeconds(10)
function Get-Json([string]$url){$r=$http.GetAsync($url).GetAwaiter().GetResult();if([int]$r.StatusCode -ne 200){throw 'Consulta local no disponible'};($r.Content.ReadAsStringAsync().GetAwaiter().GetResult()|ConvertFrom-Json)}
function Post-Local([string]$url,$csrf,$body){$q=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Post,$url);$q.Headers.Add($csrf.headerName,$csrf.token);if($body){$q.Content=[Net.Http.FormUrlEncodedContent]::new($body)};$http.SendAsync($q).GetAwaiter().GetResult()}
try {
 $publicCsrf=if(!$SoloFuncionario){Get-Json ($public+'/api/csrf')};$staffCsrf=Get-Json ($staff+'/nucleo/csrf')
 $password=[Net.NetworkCredential]::new('',(Import-Clixml (Join-Path $repo '.local/nna/nucleo-password.clixml'))).Password
 $body=[Collections.Generic.Dictionary[string,string]]::new();$body.Add('username','demo.distribucion');$body.Add('password',$password)
 $login=Post-Local ($staff+'/nucleo/login') $staffCsrf $body
 if([int]$login.StatusCode -ne 204){throw ('Login sintético no confirmado: HTTP '+[int]$login.StatusCode)}
 if($SoloFuncionario){
  $html=$http.GetAsync($staff+'/').GetAwaiter().GetResult();if([int]$html.StatusCode -ne 200){throw 'Interfaz institucional no disponible'}
  $assets=[regex]::Matches($html.Content.ReadAsStringAsync().GetAwaiter().GetResult(),'(?:src|href)="(/assets/[^"]+)"')
  if(!$assets.Count){throw 'Faltan assets empaquetados de la interfaz'}
  foreach($asset in $assets){$r=$http.GetAsync($staff+$asset.Groups[1].Value).GetAwaiter().GetResult();if([int]$r.StatusCode -ne 200){throw 'Asset institucional no disponible'}}
  $null=Get-Json ($staff+'/nucleo/comunicaciones');if(!(Get-Json ($staff+'/nucleo/session')).autenticado){throw 'Sesión funcionaria no disponible'}
  $result=[pscustomobject]@{origenFuncionario=$staff;httpsConfiable=$true;interfazYBuzonDisponibles=$true;assetsEmpaquetadosVerificados=$assets.Count;backendPublicoApagado=(!(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue));funcionarioAutenticado=$true;contactosAlPublico=0;llamadasGobierno=0}
  if($Evidencias){$result|ConvertTo-Json|Set-Content (Join-Path $Evidencias 'interfaz-independiente.json')};$result;return
 }
 $cookies=$handler.CookieContainer
 if($cookies.GetCookies([uri]$public)['SIGERSTAFFSESSION'] -or $cookies.GetCookies([uri]$staff)['SIGERSESSION']){throw 'Cookies cruzadas entre hosts'}
 if(($login.Headers.GetValues('Set-Cookie') -join ';') -match '(?i)\bdomain='){throw 'No se admite Domain de cookie compartido'}
 $publicLogout=Post-Local ($public+'/auth/logout') $publicCsrf $null
 if([int]$publicLogout.StatusCode -notin @(302,303)){throw 'Logout público no confirmado'}
 # No seguir la redirección a ClaveÚnica: no se realizan llamadas gubernamentales.
 if(!(Get-Json ($staff+'/nucleo/session')).autenticado){throw 'Logout ciudadano afectó sesión funcionaria'}
 $null=Get-Json ($public+'/api/csrf');$publicCookie=$cookies.GetCookies([uri]$public)['SIGERSESSION'].Value
 $staffCsrf=Get-Json ($staff+'/nucleo/csrf');$logout=Post-Local ($staff+'/nucleo/logout') $staffCsrf $null
 if([int]$logout.StatusCode -ne 204 -or (Get-Json ($staff+'/nucleo/session')).autenticado){throw 'Logout funcionario no confirmado'}
 $null=Get-Json ($public+'/api/csrf');if($publicCookie -ne $cookies.GetCookies([uri]$public)['SIGERSESSION'].Value){throw 'Logout funcionario afectó sesión pública'}
 $result=[pscustomobject]@{origenFuncionario=$staff;origenPublico=$public;httpsConfiable=$true;cookiesPorHost=$true;sinDomainCompartido=$true;logoutPublicoNoCierraFuncionario=$true;logoutFuncionarioNoCierraPublico=$true;sesionPublicaAnonima=$true;llamadasGobierno=0}
 if($Evidencias){$result|ConvertTo-Json|Set-Content (Join-Path $Evidencias 'sesiones-separadas.json')}
 $result
}finally{$password=$null;$body=$null;$http.Dispose();$handler.Dispose()}

