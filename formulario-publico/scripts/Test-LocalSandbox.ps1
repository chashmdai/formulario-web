[CmdletBinding()]
param([switch]$RequireLoginBlocked, [switch]$ForActivation, [switch]$RequireLoginEnabled)
$ErrorActionPreference = 'Stop'
$domain = 'claveunica.formularioadulto.defensorianinez.gob.cl'
$origin = 'https://' + $domain
$tlsDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../.local/https'))
$addresses = @([Net.Dns]::GetHostAddresses($domain) | ForEach-Object { $_.IPAddressToString })
if ($addresses.Count -eq 0 -or ($addresses | Where-Object { $_ -ne '127.0.0.1' })) {
    throw 'El resolvedor del sistema debe devolver exclusivamente 127.0.0.1. No habilitar inicio.'
}
$root = [Security.Cryptography.X509Certificates.X509Certificate2]::CreateFromPem([IO.File]::ReadAllText((Join-Path $tlsDirectory 'ca/rootCA.pem')))
$leaf = [Security.Cryptography.X509Certificates.X509Certificate2]::CreateFromPem([IO.File]::ReadAllText((Join-Path $tlsDirectory 'site.pem')))
if (!(Test-Path -LiteralPath ('Cert:/CurrentUser/Root/' + $root.Thumbprint))) { throw 'La CA propia no está confiada por el usuario actual.' }
if ($leaf.NotAfter -lt (Get-Date) -or $leaf.NotBefore -gt (Get-Date)) { throw 'Certificado fuera de vigencia.' }
# No validation callback and no SkipCertificateCheck. Windows checks trust, hostname and expiry.
$handler = [Net.Http.HttpClientHandler]::new()
$handler.AllowAutoRedirect = $false; $handler.UseProxy = $false
$handler.CookieContainer = [Net.CookieContainer]::new()
$client = [Net.Http.HttpClient]::new($handler)
$client.Timeout = [TimeSpan]::FromSeconds(15)
try {
    $page = $client.GetAsync($origin + '/').GetAwaiter().GetResult()
    if ([int]$page.StatusCode -ne 200) { throw 'La página empaquetada no responde HTTP 200.' }
    if (!$page.Headers.Contains('Strict-Transport-Security')) { throw 'Spring no reconoce HTTPS detrás del proxy; revisar forwarded headers.' }
    $sessionResponse = $client.GetAsync($origin + '/api/session').GetAwaiter().GetResult()
    $session = $sessionResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
    if ($session.environment -ne 'SANDBOX' -or $session.authenticated) { throw 'Preflight requiere Sandbox y una sesión nueva anónima.' }
    if (($RequireLoginBlocked -or $ForActivation) -and $session.loginAvailable) { throw 'El inicio debe permanecer bloqueado durante preflight.' }
    if ($RequireLoginEnabled -and !$session.loginAvailable) { throw 'Sandbox sigue bloqueado; no se completó la habilitación del perfil local.' }
    $csrfResponse = $client.GetAsync($origin + '/api/csrf').GetAwaiter().GetResult()
    $cookie = $handler.CookieContainer.GetCookies([URI]$origin)['SIGERSESSION']
    $cookieHeader = ($csrfResponse.Headers.GetValues('Set-Cookie') -join '; ')
    if (!$cookie -or !$cookie.Secure -or !$cookie.HttpOnly -or $cookieHeader -notmatch 'SameSite=Lax') { throw 'Cookie debe ser Secure, HttpOnly y SameSite=Lax.' }
    $logout = $client.GetAsync($origin + '/logout').GetAwaiter().GetResult()
    if ([int]$logout.StatusCode -ne 303 -or $logout.Headers.Location.AbsoluteUri -ne ($origin + '/?session=closed')) { throw 'Retorno de logout/origen incorrecto.' }
    # Negative automated callback check: no saved authorization request, thus no token exchange.
    $callback = $client.GetAsync($origin + '/redirect?code=preflight-invalid&state=preflight-invalid').GetAwaiter().GetResult()
    if ([int]$callback.StatusCode -ne 302 -or $callback.Headers.Location.AbsoluteUri -ne ($origin + '/?auth=failed')) { throw 'Callback no llega a Spring o su manejo seguro no coincide.' }
    if ($RequireLoginBlocked -or $ForActivation) {
        $gate = $client.GetAsync($origin + '/oauth2/authorization/claveunica').GetAwaiter().GetResult()
        if ([int]$gate.StatusCode -ne 503) { throw 'La autorización no está bloqueada durante la comprobación.' }
    }
    if ($RequireLoginEnabled) {
        # Follow no redirects: inspect only SIGER's authorization redirect, never send credentials or login requests here.
        $authorization = $client.GetAsync($origin + '/oauth2/authorization/claveunica').GetAwaiter().GetResult()
        $location = $authorization.Headers.Location
        if ([int]$authorization.StatusCode -ne 302 -or !$location -or $location.Scheme -ne 'https' -or $location.Host -ne 'accounts.claveunica.gob.cl' -or $location.AbsolutePath -ne '/openid/authorize/') { throw 'No se recibió la redirección oficial de ClaveÚnica.' }
        $parameters = @{}
        foreach ($part in $location.Query.TrimStart('?').Split('&')) {
            $pair = $part.Split('=', 2)
            if ($pair.Length -eq 2) { $parameters[[Net.WebUtility]::UrlDecode($pair[0])] = [Net.WebUtility]::UrlDecode($pair[1]) }
        }
        if ($parameters['redirect_uri'] -ne ($origin + '/redirect') -or $parameters['response_type'] -ne 'code' -or $parameters['scope'] -ne 'openid run name' -or !$parameters['state'] -or $parameters['state'].Length -lt 30 -or $parameters.ContainsKey('client_secret')) { throw 'Los parámetros de autorización no coinciden con el perfil Sandbox esperado.' }
        # Do not print the Location, client_id, nonce, or state.
    }
    $result = [pscustomobject]@{
        Domain=$domain; ResolvedAddresses=$addresses; ValidatedAtUtc=(Get-Date).ToUniversalTime().ToString('o')
        SystemResolution=$true; HttpsTrustAndHostname=$true; RootThumbprint=$root.Thumbprint; LeafThumbprint=$leaf.Thumbprint
        PackagedReact=$true; ForwardedHttps=$true; SecureHttpOnlySameSiteLax=$true; CallbackReachesBackend=$true
        LogoutReachesBackend=$true; Environment='SANDBOX'; LoginBlocked=(!$session.loginAvailable); AuthenticationVerified=$false
        AuthorizationRedirectChecked=$RequireLoginEnabled.IsPresent
    }
    $result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $tlsDirectory 'preflight.json')
    $result | ConvertTo-Json
} finally { $client.Dispose(); $handler.Dispose() }
