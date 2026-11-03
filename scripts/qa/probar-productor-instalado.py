#!/usr/bin/env python3
"""Aceptación HTTPS del productor REAL con esquema/custodia/ACK sintéticos aislados.
No conecta Windows, no usa RUN, no consulta SRCeI y restaura configuración/custodia originales.
Ejecutar en ventana privada, recepción/PULL previamente cerrados.
"""
import argparse
import base64
import datetime
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import time
import uuid
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'ubuntu'))
from productor import Productor

def canonical(value):return json.dumps(value,sort_keys=True,separators=(',',':'),ensure_ascii=False).encode()
def digest(domain,value):return hashlib.sha256(('SIGER_FORMULARIO_COMPLETO_1\0'+domain+'\0').encode()+canonical(value)).hexdigest()
def b64(value):return base64.urlsafe_b64encode(value).rstrip(b'=').decode()
def decode(value):return base64.urlsafe_b64decode(value+'='*((-len(value))%4))

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',required=True);p.add_argument('--work',required=True);a=p.parse_args()
    os.umask(0o077);work=Path(a.work).absolute()
    if work.exists() or any((q/'.git').exists() or q.is_symlink() for q in [work,*work.parents]):raise ValueError('Directorio nuevo privado requerido')
    op=Productor(a.root)
    state=op.verify()
    if op.port==24443 or state['acceptNew'] or state['pull']:raise ValueError('Solo ventana privada cerrada')
    work.mkdir(mode=0o700);store=work/'store';store.mkdir();os.chown(store,1000,1000)
    config=op.root/'config/application.properties';saved=config.read_bytes();trust=op.root/'keys/institutional-trust.json';oldtrust=trust.read_bytes()
    schema='formulario_acceptance_'+uuid.uuid4().hex[:12]
    container=op.meta['project']+'-mysql-1'
    def sql(text):
        r=subprocess.run(['docker','exec','-i',container,'mysql','--defaults-extra-file=/run/secrets/root.cnf','--batch','--skip-column-names'],input=text.encode(),capture_output=True)
        if r.returncode:raise ValueError('SQL de aceptación rechazado')
        return r.stdout
    key=Ed25519PrivateKey.generate();kid='synthetic-local-ack-20261103';pub=key.public_key().public_bytes(serialization.Encoding.PEM,serialization.PublicFormat.SubjectPublicKeyInfo)
    (work/'synthetic-ack-key.pem').write_bytes(key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
    overlay=work/'acceptance.yml';overlay.write_text('services:\n  app:\n    volumes:\n      - '+str(store)+':/var/lib/formulario\n')
    base_compose=op.meta['compose'];created=False;result={'physicalWindows':False,'srcEiQueried':False,'syntheticOnly':True}
    try:
        sql(f"CREATE DATABASE {schema} CHARACTER SET utf8mb4; GRANT SELECT,INSERT,UPDATE,DELETE ON {schema}.* TO 'formulario_app'@'%'; GRANT ALL ON {schema}.* TO 'formulario_migrador'@'%';")
        created=True
        cmd=base_compose+['--profile','migracion','run','--rm','-T','-e',f'MIGRATION_DB_URL=jdbc:mysql://mysql:3306/{schema}?sslMode=VERIFY_IDENTITY','migrar']
        with (work/'migration.log').open('w') as f:r=subprocess.run(cmd,stdin=subprocess.DEVNULL,stdout=f,stderr=f)
        if r.returncode:raise ValueError('Migración sintética falló')
        trust.write_text(json.dumps({kid:pub.decode()}));os.chown(trust,1000,1000)
        # Sustituye SOLO la base y custodia del contenedor productor durante el ensayo.
        props=dict(l.split('=',1) for l in saved.decode().splitlines() if '=' in l and not l.startswith('#'))
        op.config({'publico.db.url':props['publico.db.url'].replace('/formulario_publico?','/'+schema+'?'),
            'publico.integracion.habilitada':'true','publico.recepcion.aviso-validado':'true',
            'formulario.adultos.habilitado':'true','formulario.recepcion.habilitada':'true'})
        op.meta['compose']=base_compose+['-f',str(overlay)]
        op.compose('up','-d','--no-build','--no-deps','--force-recreate','--wait','app')
        op.cookies={}
        def api(method,path,data=None,headers=None,code=200,technical=False):
            h=dict(headers or {})
            if not technical and method not in ['GET','HEAD']:
                s,_,body=op.request('GET','/api/csrf');assert s==200;c=json.loads(body);h[c['headerName']]=c['token']
            status,hout,body=op.request(method,path,'integration-token' if technical else None,data,h)
            if status!=code:raise ValueError('Aceptación HTTPS falló: '+str(status)+' esperado '+str(code)+' ruta '+path)
            return json.loads(body) if body and hout.get('content-type','').startswith('application/json') else body,hout
        op.control(True,False)
        caps,_=api('GET','/api/formulario/capacidades');ids=[]
        # 1.0 sin adjuntos y 1.1 FILE, ambos declaración extranjera SIN RUN.
        for multimedia in [False,True]:
            journey,_=api('POST','/api/formulario/recorridos',{'tipo':'ADULTO_DECLARADO','aceptaCanalDigital':True,'avisoVersion':caps['avisoAdulto']});jid=journey['id'];path='/api/formulario/recorridos/'+jid
            api('POST',path+'/identificacion-extranjera')
            api('POST',path+'/personas/presentador-adulto/declaracion',{'atributos':{'primerNombre':'SINTETICO','primerApellido':'ACEPTACION','nacionalidad':'Argentina','tipoDocumento':'OTRO','numeroDocumento':'SYNTHETIC-ACCEPTANCE-ONLY'}})
            attachments=[]
            if multimedia:
                data=b'Archivo sintetico para aceptacion del productor. Sin datos personales.\n';filehash=hashlib.sha256(data).hexdigest()
                upload,_=api('POST',path+'/cargas',{'nombre':'synthetic-acceptance.txt','tamanio':len(data),'sha256':filehash});uid=upload['id']
                api('PUT',path+'/cargas/'+uid+'/bloques/0',data,{'Content-Type':'application/octet-stream','X-SHA256':filehash})
                api('POST',path+'/cargas/'+uid+'/finalizar',code=202)
                for _ in range(60):
                    upload,_=api('GET',path+'/cargas/'+uid)
                    if upload['estado']=='VALIDADO':break
                    if upload.get('error'):raise ValueError('Inspección sintética rechazada')
                    time.sleep(1)
                else:raise ValueError('Carga no termina')
                lote,_=api('GET',path+'/multimedia/manifiesto')
                api('POST',path+'/multimedia/manifiesto',{'ids':[uid],'base':lote['revision']})
                attachments=[upload['archivo']['id']]
            payload={'campos':{'motivo':'consulta','tema':'general','consulta-confirmada':'true','consulta-general-detalle':'ACEPTACION TECNICA SINTETICA. No es una solicitud ciudadana.','adult-region':'13'},'personas':[],'instituciones':[],'adjuntos':attachments}
            ident=str(uuid.uuid4());receipt,_=api('POST',path+'/comunicaciones',payload,{'Idempotency-Key':ident});same,_=api('POST',path+'/comunicaciones',payload,{'Idempotency-Key':ident});assert receipt==same
            ids.append(receipt['id'])
        op.control(False,False);op.control(False,True)
        consumer=sqlite3.connect(work/'controlled-consumer.sqlite');consumer.execute('PRAGMA synchronous=FULL');consumer.execute('CREATE TABLE registry(id TEXT PRIMARY KEY, hash TEXT, ack TEXT, ack_pending INT)');consumer.commit()
        public=json.loads((op.root/'keys/receipt-trust.json').read_text())
        for index,version in enumerate(['1.0','1.1'],1):
            lote,_=api('POST','/integracion/v3/formulario-completo/entregas/reclamar',{'limite':10},{'X-Formulario-Versiones':version},technical=True)
            assert len(lote['entregas'])==1;reserved=lote['entregas'][0];delivery=reserved['entrega'];content=delivery['comunicacion'];cid=content['id'];assert cid in ids and content['version']==version
            assert all(not person['autenticada'] for person in content['personas'])
            assert digest('CONTENIDO',content)==delivery['sha256']
            assert digest('PROCEDENCIA',{person['id']:[person['atributos'],person['discrepancias']] for person in content['personas']})==delivery['huellaProcedencia']
            proof=delivery['custodia'];serialization.load_pem_public_key(public[proof['keyId']].encode()).verify(decode(proof['firma']),decode(proof['payload']))
            for attachment in content['adjuntos']:
                resource='/integracion/v3/formulario-completo/entregas/'+cid+'/adjuntos/'+attachment['id'];h={'X-Reserva':reserved['reserva']}
                data,hout=api('GET',resource,headers=h,technical=True);assert hashlib.sha256(data).hexdigest()==attachment['sha256']
                partial,_=api('GET',resource,headers={**h,'Range':'bytes=0-9'},technical=True,code=206);assert partial==data[:10]
                api('HEAD',resource,headers=h,technical=True)
            registro={'version':'1.0','emisor':'SIGER_DDN','hecho':'COMUNICACION_REGISTRADA','operacionPublicaId':cid,'comunicacionId':str(uuid.uuid4()),'numeroFolio':index,'folio':'RC1TEST-'+str(index),'registradaEn':datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'),'canalOrigen':'FORMULARIO_WEB_PUBLICO','sha256':delivery['sha256'],'huellaProcedencia':delivery['huellaProcedencia']}
            payload=json.dumps(registro,separators=(',',':'),ensure_ascii=False).encode();ack={'registro':registro,'comprobante':{'algoritmo':'Ed25519','keyId':kid,'payload':b64(payload),'firma':b64(key.sign(payload))}}
            # COMMIT durable del consumidor local controlado ANTES de mandar ACK.
            consumer.execute('INSERT INTO registry VALUES(?,?,?,1)',(cid,delivery['sha256'],json.dumps(ack)));consumer.commit()
            confirmation={'reserva':reserved['reserva'],'recepcion':ack};endpoint='/integracion/v3/formulario-completo/entregas/'+cid+'/confirmacion'
            api('POST',endpoint,confirmation,code=204,technical=True)
            # Simular pérdida de respuesta: repetir exactamente el ACK, sin duplicar registro.
            api('POST',endpoint,confirmation,code=204,technical=True)
            consumer.execute('UPDATE registry SET ack_pending=0 WHERE id=?',(cid,));consumer.commit()
        empty,_=api('POST','/integracion/v3/formulario-completo/entregas/reclamar',{'limite':10},{'X-Formulario-Versiones':'1.0,1.1'},technical=True);assert empty['entregas']==[]
        assert consumer.execute('SELECT COUNT(*),SUM(ack_pending) FROM registry').fetchone()==(2,0);consumer.close()
        confirmed=sql(f"SELECT COUNT(*) FROM {schema}.citizen_submission WHERE state='CONFIRMADA'").decode().strip();assert confirmed=='2'
        op.control(False,False)
        result.update(versions=['1.0','1.1'],communications=2,attachmentsSha256=True,rangeAndHead=True,ed25519=True,canonicalHashes=True,commitBeforeAckLocalFixture=True,duplicateAckIdempotent=True,ackPending=0,confirmed=2)
        (work/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
    finally:
        # La base productiva jamás recibió estas Comunicaciones ni el ACK del doble.
        config.write_bytes(saved);trust.write_bytes(oldtrust);os.chown(config,1000,1000);os.chown(trust,1000,1000)
        op.meta['compose']=base_compose
        op.compose('up','-d','--no-build','--no-deps','--force-recreate','--wait','app')
        if created:sql(f"REVOKE ALL PRIVILEGES ON {schema}.* FROM 'formulario_app'@'%'; REVOKE ALL PRIVILEGES ON {schema}.* FROM 'formulario_migrador'@'%';")
        (work/'production-restored.json').write_text(json.dumps(op.verify(),indent=2))

if __name__=='__main__':
    try:main()
    except (OSError,ValueError,AssertionError,KeyError):raise SystemExit('Aceptación rechazada; configuración restaurada; revisar estado agregado privado')
