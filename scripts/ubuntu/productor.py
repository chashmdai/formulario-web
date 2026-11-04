#!/usr/bin/env python3
"""Operación privada del productor instalado. Secretos siempre desde archivos, nunca argumentos.
La instalación contiene installation.json y un wrapper Compose externo. No imprime cuerpos ciudadanos.
"""
import argparse
import hashlib
import http.client
from http.cookies import SimpleCookie
import json
import os
from pathlib import Path
import re
import socket
import ssl
import subprocess
import uuid
import shutil
import urllib.parse
from html.parser import HTMLParser

def reception_authorization(a):
    if a.get('schema')=='RC1_PUBLICATION_AUTHORIZATION_V2':
        flags=['authorized','publicationAuthorized','receptionAuthorized','receiptSignerUseAuthorized','existingNoticeUseAuthorized']
    else:flags=['receiptSignerApproved','windowsReady','physicalE2eVerified','noticeApproved']
    if not all(a.get(k) is True for k in flags):raise ValueError('Apertura no autorizada')
    return a

class Productor:
    def __init__(self, root):
        self.root=Path(root).resolve();self.meta=json.loads((self.root/'installation.json').read_text())
        self.host=self.meta['hostname'];self.port=self.meta['port']
        self.context=ssl.create_default_context(cafile=str(self.root/'pki/proxy/origin-ca.crt'))
        self.cookies={}

    def request(self, method, path, kind=None, data=None, headers=None):
        h=dict(headers or {})
        if self.cookies:h['Cookie']='; '.join(k+'='+v for k,v in self.cookies.items())
        if kind: h['Authorization']='Bearer '+(self.root/'secrets'/kind).read_text().strip()
        if data is not None and not isinstance(data,bytes): h['Content-Type']='application/json'
        conn=http.client.HTTPSConnection(self.host,self.port,context=self.context,timeout=30)
        conn.sock=self.context.wrap_socket(socket.create_connection(('127.0.0.1',self.port),timeout=30),server_hostname=self.host)
        if path.startswith('/operacion/'):
            conn.close()
            context=ssl.create_default_context(cafile=str(self.root/'pki/app/ca.crt'))
            conn=http.client.HTTPSConnection('app',8443,context=context,timeout=30)
            conn.sock=context.wrap_socket(socket.create_connection((self.meta['backendIp'],8443),timeout=30),server_hostname='app')
        try:
            conn.request(method,path,data if isinstance(data,bytes) or data is None else json.dumps(data),h)
            response=conn.getresponse();body=response.read()
            for k,v in response.getheaders():
                if k.lower()=='set-cookie':
                    cookie=SimpleCookie();cookie.load(v);self.cookies.update({name:m.value for name,m in cookie.items()})
            return response.status,dict((k.lower(),v) for k,v in response.getheaders()),body
        finally: conn.close()

    def control(self, accept=False, pull=False):
        status,h,body=self.request('GET','/operacion/v1/control','operations-token')
        if status!=200: raise ValueError('Control no disponible')
        status,_,body=self.request('PUT','/operacion/v1/control','operations-token',
            {'aceptarNuevos':accept,'recuperar':pull,'contingencia':'NORMAL' if accept or pull else 'SOLO_CONSULTA','motivo':'MANTENIMIENTO' if not accept and not pull else 'REANUDACION'},
            {'If-Match':h['etag']})
        if status!=200: raise ValueError('Cambio CAS rechazado')
        value=json.loads(body)
        return {k:value[k] for k in ['revision','aceptarNuevos','recuperar','contingencia']}

    def compose(self, *args):
        cmd=self.meta['compose']+list(args)
        with (self.root/'operation-private.log').open('ab') as log:
            p=subprocess.run(cmd,stdin=subprocess.DEVNULL,stdout=log,stderr=log)
        if p.returncode:raise ValueError('Compose rechazado; diagnóstico privado retenido')

    def config(self, changes):
        p=self.root/'config/application.properties';values={}
        for l in p.read_text().splitlines():
            if '=' in l and not l.startswith('#'):k,v=l.split('=',1);values[k]=v
        values.update(changes)
        # Mantener inode: el fichero está montado RO en un contenedor.
        p.write_text('\n'.join(k+'='+v for k,v in values.items())+'\n');p.chmod(0o400)

    def verify(self):
        ident=str(uuid.uuid4());path=f'/integracion/v3/formulario-completo/entregas/{ident}/adjuntos/{ident}'
        extra={'X-Reserva':ident,'X-Formulario-Versiones':'1.0,1.1'}
        absent=self.request('GET',path,headers=extra)[0]
        wrong=self.request('GET',path,headers={**extra,'Authorization':'Bearer INVALID_SYNTHETIC_CREDENTIAL'})[0]
        valid=self.request('GET',path,'integration-token',headers=extra)[0]
        if (absent,wrong,valid)!=(401,401,404):raise ValueError('Verificación Bearer/ruta falló')
        status,_,body=self.request('GET','/api/session')
        if status==401:
            self.authorize_temporary_access()
            status,_,body=self.request('GET','/api/session')
        session=json.loads(body)
        if status!=200 or session.get('certificationMode'):raise ValueError('Aplicación completa no disponible')
        status,_,body=self.request('GET','/operacion/v1/control','operations-token')
        if status!=200:raise ValueError('Control no disponible')
        c=json.loads(body)
        return {'httpsVerified':True,'bearerAbsent':absent,'bearerWrong':wrong,'bearerValidUnknownResource':valid,
                'certificationMode':False,'loginAvailable':session['loginAvailable'],
                'acceptNew':c['aceptarNuevos'],'pull':c['recuperar']}

    def authorize_temporary_access(self):
        """Solo operación local: valor desde custodia; nunca argumentos, consola ni JSON."""
        status,_,body=self.request('GET','/acceso-temporal')
        if status==303:return
        if status!=200:raise ValueError('Acceso temporal no disponible')
        class Form(HTMLParser):
            token=None
            def handle_starttag(self,tag,attrs):
                a=dict(attrs)
                if tag=='input' and a.get('type')=='hidden' and a.get('name')=='_csrf':self.token=a.get('value')
        form=Form();form.feed(body.decode())
        if not form.token:raise ValueError('Falta protección CSRF')
        data=urllib.parse.urlencode({'_csrf':form.token,'password':(self.root/'secrets/acceso-temporal-password').read_text().strip()}).encode()
        status,_,_=self.request('POST','/acceso-temporal',data=data,headers={'Content-Type':'application/x-www-form-urlencoded'})
        if status!=303:raise ValueError('Acceso temporal rechazado')

    def install_ack(self, source, fingerprint):
        status,_,body=self.request('GET','/operacion/v1/control','operations-token');c=json.loads(body)
        if status!=200 or c['aceptarNuevos'] or c['recuperar']:raise ValueError('Cerrar recepción y PULL antes de instalar confianza')
        raw=Path(source).read_bytes()
        if len(raw)>65536 or hashlib.sha256(raw).hexdigest()!=fingerprint:raise ValueError('Huella de intercambio no coincide')
        trust=json.loads(raw)
        own=json.loads((self.root/'keys/receipt-trust.json').read_text())
        def der(pem):
            r=subprocess.run(['openssl','pkey','-pubin','-outform','DER'],input=pem.encode(),capture_output=True)
            if r.returncode:raise ValueError('Clave pública inválida')
            return r.stdout
        own_keys={der(pem) for pem in own.values()}
        if not isinstance(trust,dict) or not 1<=len(trust)<=20:raise ValueError('Confianza inválida')
        for kid,pem in trust.items():
            if not re.fullmatch('[A-Za-z0-9_-]{1,64}',kid) or kid in own or pem in own.values() or 'PRIVATE' in pem:
                raise ValueError('Identidad ACK debe ser independiente')
            check=subprocess.run(['openssl','pkey','-pubin','-text','-noout'],input=pem.encode(),capture_output=True)
            if check.returncode or b'ED25519' not in check.stdout:raise ValueError('ACK requiere Ed25519')
            if der(pem) in own_keys:raise ValueError('ACK no puede reutilizar la identidad criptográfica del Formulario')
        target=self.root/'keys/institutional-trust.json';target.write_bytes(raw);target.chmod(0o400)
        if os.geteuid()==0:os.chown(target,1000,1000)
        self.config({'publico.integracion.habilitada':'true'})
        self.compose('up','-d','--no-build','--no-deps','--force-recreate','--wait','app')
        return self.verify()

    def open(self, accept, pull):
        a=reception_authorization(json.loads((self.root/'authorization/open.approved.json').read_text()))
        trusted=bool(json.loads((self.root/'keys/institutional-trust.json').read_text()))
        if pull and not trusted:raise ValueError('Recuperación requiere identidad ACK Windows; recepción independiente')
        approval=self.root/'keys/acceso-temporal-apertura.json'
        approval.write_text(json.dumps({**a,'authorized':True}))
        approval.chmod(0o400)
        if os.geteuid()==0:os.chown(approval,1000,1000)
        self.control()
        previous_config=(self.root/'config/application.properties').read_bytes()
        self.config({'publico.integracion.habilitada':str(trusted).lower(),'publico.integracion.preparada':'true',
                     'publico.recepcion.custodia-durable-autorizada':'true','publico.recepcion.habilitada':'true','publico.recepcion.aviso-validado':'true',
                     'formulario.adultos.habilitado':'true','formulario.recepcion.habilitada':'true',
                     # NNA definitivo usa /api/formulario; el prototipo /api/nna sigue cerrado.
                     'publico.nna.habilitado':'false','formulario.srcei.envio-autorizado':'true'})
        if (self.root/'config/application.properties').read_bytes()!=previous_config:
            self.compose('up','-d','--no-build','--no-deps','--force-recreate','--wait','app')
        return self.control(accept,pull)

    def export_public(self, destination):
        target=Path(destination).absolute()
        if target.exists() or any(p.is_symlink() or (p/'.git').exists() for p in [target,*target.parents]):
            raise ValueError('Destino nuevo fuera de Git requerido')
        target.mkdir(mode=0o700)
        for src,name in [('keys/receipt-trust.json','formulario-signing-trust.json'),
                         ('intercambio-publico/formulario-signing-public.pem','formulario-signing-public.pem'),
                         ('pki/proxy/origin-ca.crt','origin-ca.crt')]:
            shutil.copyfile(self.root/src,target/name)
        profile={'versions':['1.0','1.1'],'producer':'https://'+self.host,
                 'path':'/integracion/v3/formulario-completo/entregas',
                 'credentialFile':'integration-token','versionsHeader':'X-Formulario-Versiones: 1.0,1.1',
                 'producerSigningApproval':json.loads((self.root/'authorization/signing-status.json').read_text())['approval'],
                 'pendingWindowsMaterial':['ACK_PUBLIC_ED25519_KEY','ACK_KEY_ID'],
                 'privateOriginPort':self.port,'privateOriginLoopbackOnly':True}
        profile['privateTunnelOrigin']='https://localhost:32443'
        profile['privateTransport']='SSH local forward 127.0.0.1:32443 -> DDN-UBUNTU 127.0.0.1:'+str(self.port)
        (target/'consumer-profile.json').write_text(json.dumps(profile,indent=2))
        hashes={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in target.iterdir()}
        (target/'SHA256SUMS.json').write_text(json.dumps(hashes,indent=2))
        return {'publicMaterialExported':True,'privateKeysIncluded':False,'bearerIncluded':False}

    def export_credential(self, destination):
        p=Path(destination).absolute()
        if any(q.is_symlink() or (q/'.git').exists() for q in [p,*p.parents]) or p.parent.stat().st_mode&0o077:
            raise ValueError('Entrega exige custodia privada fuera de Git')
        with p.open('xb') as f:f.write((self.root/'secrets/integration-token').read_bytes())
        p.chmod(0o400)
        return {'credentialReadyForSecureDelivery':True,'valuePrinted':False}

def main():
    os.umask(0o077)
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',required=True)
    s=p.add_subparsers(dest='action',required=True);s.add_parser('verify');s.add_parser('close')
    ack=s.add_parser('install-ack');ack.add_argument('--trust-file',required=True);ack.add_argument('--sha256',required=True)
    op=s.add_parser('open');op.add_argument('--accept-new',action='store_true');op.add_argument('--pull',action='store_true')
    for action in ['export-public','export-credential']:
        export=s.add_parser(action);export.add_argument('--destination',required=True)
    a=p.parse_args()
    try:
        op=Productor(a.root)
        result=op.verify() if a.action=='verify' else op.control() if a.action=='close' else op.install_ack(a.trust_file,a.sha256) if a.action=='install-ack' else op.export_public(a.destination) if a.action=='export-public' else op.export_credential(a.destination) if a.action=='export-credential' else op.open(a.accept_new,a.pull)
        print(json.dumps(result))
    except (OSError,ValueError,KeyError,TypeError):raise SystemExit('Operación rechazada; no se muestran secretos ni cuerpos')

if __name__=='__main__':main()
