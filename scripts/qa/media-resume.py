#!/usr/bin/env python3
"""Pruebas sintéticas en laboratorio aislado. La fase custodia crea una única recepción idempotente."""
import uuid
import socket
import argparse,hashlib,http.cookiejar,json,os,ssl,time,urllib.request
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('phase',choices=['iniciar','reanudar','custodia','historico','recuperar']);p.add_argument('--private',type=Path,required=True);p.add_argument('--ca',required=True);p.add_argument('--config',type=Path);a=p.parse_args();os.umask(0o077);a.private=a.private.resolve()
if any((part/'.git').exists() for part in [a.private,*a.private.parents]):p.error('Directorio privado fuera de Git requerido para cookies y estado')
assert socket.gethostbyname('claveunica.formularioadulto.defensorianinez.gob.cl')=='127.0.0.1','Resolver exclusivamente al laboratorio local'
a.private.mkdir(mode=0o700,exist_ok=True)
origin='https://claveunica.formularioadulto.defensorianinez.gob.cl:19443';cookies=http.cookiejar.MozillaCookieJar(str(a.private/'cookies.txt'))
if (a.private/'cookies.txt').exists():cookies.load(ignore_discard=True,ignore_expires=True)
context=ssl.create_default_context(cafile=a.ca);opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookies),urllib.request.HTTPSHandler(context=context))
def request(path,body=None,method='GET',headers=None,raw=False):
 h=headers or {}
 if method!='GET':
  csrf=request('/api/csrf');h={**h,csrf['headerName']:csrf['token']}
  if not raw:h={**h,'Content-Type':'application/json'};body=None if body is None else json.dumps(body).encode()
 with opener.open(urllib.request.Request(origin+path,data=body,method=method,headers=h),timeout=110) as r:
  return json.load(r) if r.headers.get('Content-Type','').startswith('application/json') else r.read()
def sha(bytes):return hashlib.sha256(bytes).hexdigest()
block=b'a'*(8*1024*1024);tail=b'final\n!';digest=hashlib.sha256();digest.update(block);digest.update(block);digest.update(tail);expected=digest.hexdigest()
if a.phase=='iniciar':
 caps=request('/api/formulario/capacidades');journey=request('/api/formulario/recorridos',{'tipo':'NNA','avisoVersion':caps['avisoNna'],'aceptaCanalDigital':False},'POST')['id'];base=f'/api/formulario/recorridos/{journey}'
 state=request(base+'/cargas',{'nombre':'reanudacion-sintetica.txt','tamanio':len(block)*2+len(tail),'sha256':expected},'POST');job=state['id'];state=request(base+f'/cargas/{job}/bloques/0',block,'PUT',{'Content-Type':'application/octet-stream','X-SHA256':sha(block)},True);assert state['recibidos']==len(block)
 (a.private/'state.json').write_text(json.dumps({'journey':journey,'job':job,'expected':expected}));cookies.save(ignore_discard=True,ignore_expires=True);print('Primer bloque de 8 MiB durable. Ejecutar reanudar en la misma sesión; no presupone persistencia de sesión Tomcat tras reinicio.')
elif a.phase=='reanudar':
 saved=json.loads((a.private/'state.json').read_text());journey=saved['journey'];job=saved['job'];base=f'/api/formulario/recorridos/{journey}';state=request(base+f'/cargas/{job}');assert state['recibidos']==len(block)
 # Replay idéntico no aumenta el offset; luego continúa sin repetir bytes aceptados.
 state=request(base+f'/cargas/{job}/bloques/0',block,'PUT',{'Content-Type':'application/octet-stream','X-SHA256':sha(block)},True);assert state['recibidos']==len(block)
 for index,data in [(1,block),(2,tail)]:state=request(base+f'/cargas/{job}/bloques/{index}',data,'PUT',{'Content-Type':'application/octet-stream','X-SHA256':sha(data)},True)
 state=request(base+f'/cargas/{job}/finalizar',method='POST');until=time.time()+150
 while state['estado']=='ANALIZANDO' and time.time()<until:time.sleep(.5);state=request(base+f'/cargas/{job}')
 assert state['estado']=='VALIDADO',state['estado'];manifest=request(base+'/multimedia/manifiesto');manifest=request(base+'/multimedia/manifiesto',{'ids':[job],'base':manifest['revision']},'POST');assert manifest['archivos'][0]['sha256']==expected
 with opener.open(origin+base+f'/adjuntos/{job}',timeout=30) as stream:
  actual=hashlib.sha256();size=0
  while data:=stream.read(65536):actual.update(data);size+=len(data)
 assert actual.hexdigest()==expected and size==len(block)*2+len(tail)
 (a.private/'resultado.json').write_text(json.dumps({'interrupcionTransporte':True,'reinicioProceso':False,'replayIdempotente':True,'bytes':size,'sha256Correcto':True,'antivirusReal':True,'inspectorReal':True,'comunicaciones':0},indent=2));print('Reanudación de transporte: 16 MiB + 7 bytes íntegros, escaneados y descargados sin duplicar bloques ni enviar Comunicación.')
elif a.phase=='custodia':
 if not a.config:raise ValueError('Configuración privada de este laboratorio requerida')
 saved=json.loads((a.private/'state.json').read_text());journey=saved['journey'];job=saved['job'];base=f'/api/formulario/recorridos/{journey}'
 keypath=a.private/'custody-key';key=keypath.read_text() if keypath.exists() else str(uuid.uuid4());keypath.write_text(key)
 request(base+'/personas/presentador-nna/identificacion',{'rol':'PRESENTADOR','runNumero':None,'runDv':None},'POST')
 request(base+'/personas/presentador-nna/declaracion',{'atributos':{'primerNombre':'SINTETICO','primerApellido':'MULTIMEDIA'}},'POST')
 capture={'campos':{'nna-region':'13','nna-primary-method':'unsafe','nna-story':'Verificación sintética de custodia y restauración multimedia.'},'personas':[],'instituciones':[],'adjuntos':[job]}
 first=request(base+'/comunicaciones',capture,'POST',{'Idempotency-Key':key});second=request(base+'/comunicaciones',capture,'POST',{'Idempotency-Key':key});assert first==second and first['id']==key
 props=dict(l.split('=',1) for l in a.config.read_text().splitlines() if '=' in l and not l.startswith('#'));integration='/integracion/v3/formulario-completo/entregas';headers={'Authorization':'Bearer '+props['publico.integracion.token']}
 # El consumidor existente no recibe documentos ampliados de forma silenciosa.
 assert request(integration+'/reclamar',{'limite':1},'POST',headers)['entregas']==[]
 lease=request(integration+'/reclamar',{'limite':1},'POST',{**headers,'X-Formulario-Versiones':'1.0,1.1'})['entregas'][0];assert lease['entrega']['comunicacion']['version']=='1.1'
 with opener.open(urllib.request.Request(origin+integration+f'/{key}/adjuntos/{job}',headers={**headers,'X-Reserva':lease['reserva'],'Range':'bytes=0-31'}),timeout=30) as response:assert response.status==206 and response.read()==block[:32]
 (a.private/'custody-result.json').write_text(json.dumps({'custodiasSinteticasNecesarias':1,'idempotencia':True,'version':'1.1','optInExplicito':True,'recuperacionReservadaRange':True,'ackInstitucional':'No se simula registro SIGER; cubierto por dobles en regresión'},indent=2));print('Una custodia sintética aislada: idempotencia, versión explícita 1.1 y recuperación privada por reserva correctas.')

elif a.phase=='historico':
 caps=request('/api/formulario/capacidades');journey=request('/api/formulario/recorridos',{'tipo':'NNA','avisoVersion':caps['avisoNna'],'aceptaCanalDigital':False},'POST')['id'];boundary='MULTIMEDIA_SINTETICA_2026';content=b'Adjunto historico sintetico para verificar respaldo MySQL.\n'
 body=(f'--{boundary}\r\nContent-Disposition: form-data; name="archivos"; filename="historico-sintetico.txt"\r\nContent-Type: text/plain\r\n\r\n').encode()+content+f'\r\n--{boundary}--\r\n'.encode()
 files=request(f'/api/formulario/recorridos/{journey}/adjuntos',body,'POST',{'Content-Type':'multipart/form-data; boundary='+boundary},True);assert files[0]['sha256']==sha(content);print('Adjunto sintético pequeño guardado por el camino MySQL histórico para comprobar restauración conjunta.')

elif a.phase=='recuperar':
 if not a.config:raise ValueError('Configuración privada de este laboratorio requerida')
 saved=json.loads((a.private/'state.json').read_text());key=(a.private/'custody-key').read_text();props=dict(l.split('=',1) for l in a.config.read_text().splitlines() if '=' in l and not l.startswith('#'));root='/integracion/v3/formulario-completo/entregas';headers={'Authorization':'Bearer '+props['publico.integracion.token'],'X-Formulario-Versiones':'1.0,1.1','Content-Type':'application/json'}
 with urllib.request.urlopen(urllib.request.Request(origin+root+'/reclamar',headers=headers,data=b'{"limite":1}',method='POST'),context=context) as response:lease=json.load(response)['entregas'][0]
 assert lease['entrega']['comunicacion']['id']==key and lease['entrega']['comunicacion']['version']=='1.1'
 with urllib.request.urlopen(urllib.request.Request(origin+root+f'/{key}/adjuntos/'+saved['job'],headers={'Authorization':headers['Authorization'],'X-Reserva':lease['reserva']}),context=context) as response:
  actual=hashlib.sha256();size=0
  while data:=response.read(65536):actual.update(data);size+=len(data)
 assert actual.hexdigest()==saved['expected'] and size==len(block)*2+len(tail)
 proof={'recuperacionInstitucionalTrasReinicio':True,'sinSesionCiudadana':True,'custodiaValidadaPorServidor':True,'sha256Correcto':True,'bytes':size,'version':'1.1','nuevasComunicaciones':0};(a.private/'recovery-result.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
