#!/usr/bin/env python3
"""Verificación HTTPS del dominio público, sin consultas personales ni Comunicaciones.
Bearer/contraseña solo desde custodia. No sigue redirecciones ni imprime cuerpos/cookies.
"""
import argparse
import http.client
from http.cookies import SimpleCookie
import json
from pathlib import Path
import re
import ssl
import urllib.parse
import uuid
from productor import Productor

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',required=True);p.add_argument('--expect-reception',choices=['open','closed'],required=True);p.add_argument('--output',required=True);a=p.parse_args()
    op=Productor(a.root);context=ssl.create_default_context();cookies={}
    def request(method,path,data=None,headers=None,session=False):
        h={'User-Agent':'DDN-SIGER-Readiness/1.0',**(headers or {})}
        if session and cookies:h['Cookie']='; '.join(k+'='+v for k,v in cookies.items())
        conn=http.client.HTTPSConnection(op.host,443,context=context,timeout=25)
        try:
            conn.request(method,path,data,h);r=conn.getresponse();body=r.read();out={k.lower():v for k,v in r.getheaders()}
            if session:
                for k,v in r.getheaders():
                    if k.lower()=='set-cookie':
                        c=SimpleCookie();c.load(v);cookies.update({k:m.value for k,m in c.items()})
            return r.status,out,body
        finally:conn.close()
    ref=str(uuid.uuid4());path=f'/integracion/v3/formulario-completo/entregas/{ref}/adjuntos/{ref}'
    h={'X-Reserva':ref,'X-Formulario-Versiones':'1.0,1.1'}
    absent=request('GET',path,headers=h)[0];wrong=request('GET',path,headers={**h,'Authorization':'Bearer INVALID_READINESS_ONLY'})[0]
    valid,edge,_=request('GET',path,headers={**h,'Authorization':'Bearer '+(op.root/'secrets/integration-token').read_text().strip()})
    if (absent,wrong,valid)!=(401,401,404):raise ValueError('Transporte Bearer público no acreditado')
    anonymous=request('GET','/api/session')[0]
    if anonymous!=401:raise ValueError('La barrera temporal no protege las API')
    status,_,body=request('GET','/acceso-temporal',session=True)
    if status!=200:raise ValueError('Pantalla de acceso no disponible')
    token=re.search(r'name="_csrf" value="([^"]+)"',body.decode())
    if not token:raise ValueError('Falta CSRF')
    data=urllib.parse.urlencode({'_csrf':token[1],'password':(op.root/'secrets/acceso-temporal-password').read_text().strip()}).encode()
    status,hout,_=request('POST','/acceso-temporal',data,{'Content-Type':'application/x-www-form-urlencoded'},True)
    if status!=303 or hout.get('location')!='/?vista=inicio':raise ValueError('Acceso temporal público rechazado')
    status,_,body=request('GET','/api/session',session=True);s=json.loads(body)
    if status!=200 or s['certificationMode'] or s['authenticated']:raise ValueError('Instancia pública incorrecta')
    status,_,body=request('GET','/api/formulario/recepcion/capacidades',session=True);caps=json.loads(body)
    expected=a.expect_reception=='open'
    if status!=200 or caps.get('adultos')!=expected or caps.get('nna')!=expected or caps.get('multimedia') is not True:raise ValueError('Capacidades públicas no coinciden')
    result={'publicOrigin':'https://'+op.host,'tlsVerifiedSystemCa':True,'cloudflareEdgeObserved':'cf-ray' in edge,
            'technicalRequestsWithoutCookie':True,'bearerAbsent':absent,'bearerWrong':wrong,'bearerValidUnknownResource':valid,
            'anonymousCitizenApi':anonymous,'passwordLoginWorks':True,'certificationMode':False,'citizenAuthenticated':False,
            'reception':caps,'personalQueryPerformed':False,'communicationCreated':False,'pullClaimPerformed':False}
    Path(a.output).write_text(json.dumps(result,indent=2));print(json.dumps(result))

if __name__=='__main__':
    try:main()
    except (OSError,ValueError,KeyError,http.client.HTTPException):raise SystemExit('Verificación pública incompleta/rechazada; no se muestran secretos ni cuerpos')
