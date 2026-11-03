#!/usr/bin/env python3
"""Read-only TLS checks and rejected mutations. Never print cookies, state, tokens or identity."""
import argparse, http.client, json, socket, ssl, subprocess, urllib.parse
from pathlib import Path
from http.cookies import SimpleCookie
from email.utils import parsedate_to_datetime
from datetime import datetime, timezone

HOST = 'formulario.defensorianinez.gob.cl'
class LocalHTTPS(http.client.HTTPSConnection):
    def connect(self):
        raw=socket.create_connection(('127.0.0.1',self.port),timeout=15)
        self.sock=self._context.wrap_socket(raw,server_hostname=HOST)

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--private-dir',default='/srv/ddn-formulario-institucional');ap.add_argument('--output',required=True);args=ap.parse_args()
    root=Path(args.private_dir);ctx=ssl.create_default_context(cafile=str(root/'pki/proxy/ca.crt'));results={}
    def request(path,method='GET',headers=None,body=None):
        conn=LocalHTTPS(HOST,24443,context=ctx,timeout=15)
        conn.request(method,path,body,headers or {});r=conn.getresponse();data=r.read();h=dict((k.lower(),v) for k,v in r.getheaders());status=r.status;conn.close();return status,h,data
    def direct(path,method='GET',headers=None,body=None):
        # Credentials go only to curl stdin, never argv, environment or logs.
        config=['silent','show-error','include','max-time = 15','cacert = "/run/pki/ca.crt"',f'url = "https://localhost:8443{path}"',f'request = "{method}"']
        for k,v in (headers or {}).items():config.append('header = '+json.dumps(k+': '+v))
        if body is not None:config.append('data = '+json.dumps(body))
        p=subprocess.run(['docker','exec','-i','ddn-formulario-institucional-app-1','curl','--config','-'],input='\n'.join(config).encode(),capture_output=True)
        if p.returncode:raise RuntimeError('Direct TLS request failed; private output suppressed')
        head,_,data=p.stdout.partition(b'\r\n\r\n');lines=head.decode().split('\r\n');status=int(lines[0].split()[1]);h=dict((x.split(':',1)[0].lower(),x.split(':',1)[1].strip()) for x in lines[1:] if ':' in x);return status,h,data
    def check(name,condition):results[name]=bool(condition)
    status,h,body=request('/api/session');session=json.loads(body)
    check('production_certification_anonymous',status==200 and session['certificationMode'] and session['environment']=='PRODUCTION' and not session['authenticated'] and session['identity'] is None)
    check('oauth_enabled',session['loginAvailable'])
    check('security_headers',all(k in h for k in ['strict-transport-security','content-security-policy','x-frame-options','x-content-type-options','referrer-policy','permissions-policy']))
    check('no_store',h.get('cache-control')=='no-store')
    check('forged_host_rejected',request('/',headers={'Host':'attacker.invalid'})[0]==421)
    check('forwarded_headers_do_not_change_origin',request('/api/session',headers={'Forwarded':'proto=http;host=attacker.invalid','X-Forwarded-Host':'attacker.invalid','X-Forwarded-Proto':'http'})[0]==200)
    status,csrf_h,csrf_body=request('/api/csrf');csrf=json.loads(csrf_body);cookie=csrf_h.get('set-cookie','').split(';')[0]
    check('secure_session_cookie',all(x in csrf_h.get('set-cookie','').lower() for x in ['secure','httponly','samesite=lax']))
    check('logout_without_csrf_rejected',request('/auth/logout','POST',{'Cookie':cookie})[0]==403)
    status,logout_h,_=request('/auth/logout','POST',{'Cookie':cookie,csrf['headerName']:csrf['token']})
    loc=urllib.parse.urlparse(logout_h.get('location',''));params=urllib.parse.parse_qs(loc.query)
    check('logout_csrf_and_official_return',status==302 and loc.hostname=='accounts.claveunica.gob.cl' and params.get('redirect')==['https://'+HOST+'/logout'])
    deleted_cookie=SimpleCookie();deleted_cookie.load(logout_h.get('set-cookie',''));deleted=deleted_cookie.get('SIGERSESSION')
    check('logout_expires_cookie',deleted is not None and deleted.value=='' and (deleted['max-age']=='0' or bool(deleted['expires']) and parsedate_to_datetime(deleted['expires'])<datetime.now(timezone.utc)))
    check('old_csrf_and_session_invalidated',request('/auth/logout','POST',{'Cookie':cookie,csrf['headerName']:csrf['token']})[0]==403)
    check('logout_return',request('/logout')[0]==303)
    status,oauth_h,_=request('/oauth2/authorization/claveunica');loc=urllib.parse.urlparse(oauth_h.get('location',''));q=urllib.parse.parse_qs(loc.query)
    check('official_oauth_redirect',status==302 and loc.hostname=='accounts.claveunica.gob.cl' and q.get('redirect_uri')==['https://'+HOST+'/redirect'] and q.get('response_type')==['code'])
    check('oauth_state_and_nonce',len(q.get('state',[''])[0])>=32 and bool(q.get('nonce')))
    _,h2,_=request('/oauth2/authorization/claveunica');q2=urllib.parse.parse_qs(urllib.parse.urlparse(h2.get('location','')).query)
    check('dynamic_state',q.get('state')!=q2.get('state'))
    check('oauth_no_personal_session_after_start',not json.loads(request('/api/session')[2])['authenticated'])
    status,_,_=request('/redirect?code=invalid-certification-negative&state=invalid-certification-negative')
    check('unsolicited_callback_rejected',status in [302,303] and not json.loads(request('/api/session')[2])['authenticated'])
    paths=['/api/formulario/recorridos','/api/formulario/recorridos/actual','/api/formulario/recorridos/00000000-0000-0000-0000-000000000001/comunicaciones','/api/formulario/recorridos/00000000-0000-0000-0000-000000000001/adjuntos','/api/formulario/recorridos/00000000-0000-0000-0000-000000000001/cargas','/api/comunicaciones','/api/comunicaciones/preview','/api/nna/sesion','/api/identidad/atributos','/admin','/actuator/health']
    for transport,label in [(request,'proxy'),(direct,'direct_app')]:
        check(label+'_business_blocked',all(transport(path,'POST',{'Cookie':cookie,csrf['headerName']:csrf['token'],'Content-Type':'application/json'},'{}')[0]==503 for path in paths))
        for prefix,secret in [('/operacion/v1/control','operations-token'),('/integracion/v3/formulario-completo/entregas/reclamar','integration-token')]:
            token=(root/'secrets'/secret).read_text().strip()
            check(label+'_'+secret+'_blocked_with_valid_token',transport(prefix,'PUT' if secret=='operations-token' else 'POST',{'Authorization':'Bearer '+token,'Content-Type':'application/json','If-Match':'"4"'},'{}')[0]==503)
        check(label+'_chunks_blocked',transport('/api/formulario/recorridos/1/cargas/1/bloques/0','PUT',{'Cookie':cookie,csrf['headerName']:csrf['token']},'negative')[0]==503)
    for path in ['/?vista=formulario-nna','/?vista=formulario-adulto&modo=manual','/?vista=provisional']:
        check('legacy_entry_'+str(len(results)),request(path)[0]==200)
    # Strict TLS failure checks deliberately use no alternative trust and no insecure option.
    try:
        bad=LocalHTTPS(HOST,24443,context=ssl.create_default_context(),timeout=15);bad.request('GET','/');bad.getresponse();check('untrusted_ca_rejected',False)
    except ssl.SSLCertVerificationError:check('untrusted_ca_rejected',True)
    try:
        raw=socket.create_connection(('127.0.0.1',24443),timeout=15);ctx.wrap_socket(raw,server_hostname='attacker.invalid');check('wrong_san_rejected',False)
    except ssl.SSLCertVerificationError:check('wrong_san_rejected',True)
    out=Path(args.output);out.write_text(json.dumps({'checks':results,'passed':sum(results.values()),'total':len(results),'real_authentication_performed':False},indent=2)+'\n');out.chmod(0o600)
    print(json.dumps({'passed':sum(results.values()),'total':len(results),'failed':[k for k,v in results.items() if not v],'real_authentication_performed':False}))
    if not all(results.values()):raise SystemExit(1)
if __name__=='__main__':main()
