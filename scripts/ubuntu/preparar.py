#!/usr/bin/env python3
"""Inicializa SOLO un destino POSIX nuevo; no arranca servicios ni migra bases.
Requiere root para asignar uid 1000 (aplicación) y 999 (MySQL oficial).
No sirve para actualizar ni trasladar una instalación con firmas históricas.
"""
import argparse, hashlib, json, os, re, secrets, shutil, stat, subprocess, sys
from pathlib import Path
REPO=Path(__file__).resolve().parents[2]
RUNTIME="eclipse-temurin:25-jre@sha256:ecdc0adb37bde1a281c4d245a3cd535004b1fc8322d560f5b09463b248c81df0"
def run(*args):
    result=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    if result.returncode: raise RuntimeError("Falló herramienta de preparación: "+args[0]+"; diagnóstico omitido para proteger secretos")
    return result.stdout
def private_source(path):
    path=Path(path).absolute()
    if any(part.is_symlink() for part in [path,*path.parents]) or not path.is_file() or path.stat().st_size>65536:
        raise ValueError("Fuente privada regular y acotada requerida")
    if stat.S_IMODE(path.stat().st_mode)&0o077: raise ValueError("La fuente privada debe tener permisos 0600 o 0400")
    return path
def new_destination(path):
    path=Path(path).absolute()
    if path.exists() or path.is_symlink(): raise ValueError("Destino NUEVO requerido; nunca se sobrescribe una instalación")
    for part in [path,*path.parents]:
        if part.is_symlink() or (part/'.git').exists(): raise ValueError("Destino fuera de Git y sin enlaces requerido")
    if not path.parent.is_dir(): raise ValueError("El directorio padre debe existir")
    return path
def write(path,value,uid=1000,mode=0o400):
    path.write_bytes(value if isinstance(value,bytes) else value.encode())
    os.chown(path,uid,uid);os.chmod(path,mode)
def installation_inputs(args):
    if args.sin_claveunica:
        if args.cu_client_id or args.cu_secret: raise ValueError("Modo CU cerrado incompatible con credenciales")
        credentials=[]
    else:
        if not args.cu_client_id or not args.cu_secret: raise ValueError("Entregar ambos archivos CU o seleccionar --sin-claveunica")
        credentials=[private_source(p).read_text().strip() for p in [args.cu_client_id,args.cu_secret]]
        if any(not v or '\n' in v or '\r' in v for v in credentials) or len(credentials[1].encode())<32:
            raise ValueError("Credenciales CU de archivo inválidas; HS256 requiere al menos 32 bytes")
    if args.sin_confianza_ack:
        if args.institutional_trust: raise ValueError("Modo ACK cerrado incompatible con confianza suministrada")
        trust=None
    else:
        if not args.institutional_trust: raise ValueError("Entregar confianza ACK o seleccionar --sin-confianza-ack")
        trust=json.loads(private_source(args.institutional_trust).read_text())
        if not isinstance(trust,dict) or not 1<=len(trust)<=20: raise ValueError("Confianza ACK requerida del responsable SIGER")
        for key,pem in trust.items():
            if not re.fullmatch(r'[A-Za-z0-9_-]{1,64}',key) or not isinstance(pem,str) or 'PRIVATE' in pem:
                raise ValueError("Mapa público ACK inválido")
            check=subprocess.run(['openssl','pkey','-pubin','-text','-noout'],input=pem.encode(),capture_output=True)
            if check.returncode or b'ED25519' not in check.stdout: raise ValueError("Clave pública ACK Ed25519 requerida")
    return credentials,trust
def prepare(args):
    if os.geteuid()!=0: raise ValueError("Ejecutar con sudo para asignar propietarios de los bind mounts")
    if not re.fullmatch(r'[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?',args.hostname) or not args.hostname.endswith('.gob.cl'):
        raise ValueError("Hostname HTTPS institucional requerido")
    if not re.fullmatch(r'[a-z][a-z0-9-]{2,40}',args.project) or not 1024<=args.port<=65535:
        raise ValueError("Proyecto o puerto inválido")
    if not re.fullmatch(r'[a-f0-9]{40}',args.commit): raise ValueError("Indicar SHA completo de fuente, nunca latest")
    credentials,trust=installation_inputs(args)
    receipt_material=None
    if args.produccion:
        if not args.receipt_private_key or not args.receipt_trust or not re.fullmatch(r'[A-Za-z0-9_-]{1,64}',args.receipt_key_id or ''):
            raise ValueError('Producción requiere identidad de firma propia aprobada: clave, confianza e identificador externos')
        receipt_material=private_source(args.receipt_private_key).read_bytes()
        receipt_trust=json.loads(private_source(args.receipt_trust).read_text())
        public=run('openssl','pkey','-in',str(private_source(args.receipt_private_key)),'-passin','pass:','-pubout').decode()
        if not isinstance(receipt_trust,dict) or not 1<=len(receipt_trust)<=20 or receipt_trust.get(args.receipt_key_id)!=public:
            raise ValueError('La confianza de firma debe contener la clave pública correspondiente')
        check=subprocess.run(['openssl','pkey','-pubin','-text','-noout'],input=public.encode(),capture_output=True)
        if check.returncode or b'ED25519' not in check.stdout: raise ValueError('Firma de comprobante exige Ed25519')
    target=new_destination(args.destination)
    for tool in ['docker','openssl']:
        if not shutil.which(tool): raise ValueError("Dependencia ausente: "+tool)
    run('docker','image','inspect',RUNTIME) # obtener imagen oficial antes, sin descargas ocultas
    os.umask(0o077);target.mkdir(mode=0o700)
    for folder in ['config','secrets','cu','keys','store','ipc/inspector','ipc/antivirus','clamav','mysql','mysql-init','pki/authority','pki/app','pki/mysql','pki/proxy']:
        p=target/folder;p.mkdir(mode=0o700,parents=True,exist_ok=True);os.chown(p,1000,1000)
    for base in ['ipc','pki']:os.chown(target/base,1000,1000)
    tokens={name:secrets.token_hex(32) for name in ['mysql-root-password','app-password','migration-password','keystore-password','operations-token','integration-token']}
    for name,value in tokens.items():write(target/'secrets'/name,value,uid=0 if name=='mysql-root-password' else 1000)
    for name,value in zip(['siger.claveunica.client-id','siger.claveunica.client-secret'],credentials):write(target/'cu'/name,value)
    if trust is not None: write(target/'keys/institutional-trust.json',json.dumps(trust,indent=2))
    write(target/'keys/abuso.key',secrets.token_bytes(48))
    key=target/'keys/receipt-key.pem'
    if receipt_material is None: run('openssl','genpkey','-algorithm','ED25519','-out',str(key))
    else: key.write_bytes(receipt_material)
    os.chown(key,1000,1000);os.chmod(key,0o400)
    pub=run('openssl','pkey','-in',str(key),'-pubout').decode()
    write(target/'keys/receipt-trust.json',json.dumps(receipt_trust if receipt_material is not None else {'publico-candidato':pub},indent=2))
    authority=target/'pki/authority'
    run('openssl','req','-x509','-newkey','rsa:2048','-nodes','-sha256','-days','397','-subj','/CN=Formulario Origin CA privada','-keyout',str(authority/'ca.key'),'-out',str(authority/'ca.crt'),'-addext','basicConstraints=critical,CA:TRUE','-addext','keyUsage=critical,keyCertSign,cRLSign')
    os.chown(authority,0,0);os.chmod(authority/'ca.key',0o400)
    for name,cn,sans,uid in [('app','app','DNS:app,DNS:localhost,IP:127.0.0.1',1000),('mysql','mysql','DNS:mysql,DNS:localhost,IP:127.0.0.1',999),('proxy',args.hostname,'DNS:'+args.hostname,1000)]:
        folder=target/'pki'/name
        ext=authority/(name+'.ext')
        write(ext,'basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth\nsubjectAltName='+sans+'\n',uid=0)
        run('openssl','req','-new','-newkey','rsa:2048','-nodes','-subj','/CN='+cn,'-keyout',str(folder/'server.key'),'-out',str(authority/(name+'.csr')))
        run('openssl','x509','-req','-sha256','-days','397','-in',str(authority/(name+'.csr')),'-CA',str(authority/'ca.crt'),'-CAkey',str(authority/'ca.key'),'-set_serial',str(secrets.randbits(128)),'-extfile',str(ext),'-out',str(folder/'server.crt'))
        shutil.copyfile(authority/'ca.crt',folder/'ca.crt')
        os.chown(folder,uid,uid)
        for file in folder.iterdir():os.chown(file,uid,uid);os.chmod(file,0o400)
    app=target/'pki/app'
    run('openssl','pkcs12','-export','-inkey',str(app/'server.key'),'-in',str(app/'server.crt'),'-certfile',str(app/'ca.crt'),'-out',str(app/'server.p12'),'-passout','file:'+str(target/'secrets/keystore-password'))
    os.chown(app/'server.p12',1000,1000);os.chmod(app/'server.p12',0o400)
    # Se conservan todas las CA públicas del JRE; no se reemplaza su truststore global.
    run('docker','run','--rm','--network','none','--user','1000:1000','--entrypoint','sh','--mount','type=bind,src='+str(app)+',dst=/out',RUNTIME,'-ec',
        'cp "$JAVA_HOME/lib/security/cacerts" /out/java-truststore; keytool -importcert -noprompt -alias formulario-origin -file /out/ca.crt -keystore /out/java-truststore -storepass changeit; chmod 400 /out/java-truststore')
    config=(REPO/'infra/ubuntu/application.properties.example').read_text().replace('formulario.defensorianinez.gob.cl',args.hostname)
    if args.produccion: config=config.replace('publico.comprobante.key-id=publico-candidato','publico.comprobante.key-id='+args.receipt_key_id)
    if args.sin_claveunica:
        config=config.replace('siger.claveunica.habilitada=true','siger.claveunica.habilitada=false').replace('siger.claveunica.callback-approved=true','siger.claveunica.callback-approved=false')
    if args.sin_confianza_ack: config=config.replace('publico.integracion.habilitada=true','publico.integracion.habilitada=false')
    write(target/'config/application.properties',config)
    # Hex aleatorio: sin caracteres especiales SQL, ni contraseñas en argumentos.
    ddl="CREATE DATABASE formulario_publico CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n"
    for user,password,grants in [('formulario_app',tokens['app-password'],'SELECT,INSERT,UPDATE,DELETE'),('formulario_migrador',tokens['migration-password'],'ALL PRIVILEGES')]:
        ddl+=f"CREATE USER '{user}'@'%' IDENTIFIED BY '{password}' REQUIRE SSL;\nGRANT {grants} ON formulario_publico.* TO '{user}'@'%';\n"
    os.chown(target/'mysql-init',999,999)
    write(target/'mysql-init/01-formulario.sql',ddl,uid=999)
    write(target/'mysql/root.cnf','[client]\nuser=root\npassword='+tokens['mysql-root-password']+'\nprotocol=socket\n',uid=999)
    write(target/'mysql/health.cnf','[client]\nhost=mysql\nuser=formulario_app\npassword='+tokens['app-password']+'\nssl-mode=VERIFY_IDENTITY\nssl-ca=/run/pki/ca.crt\n',uid=999)
    env=(REPO/'infra/ubuntu/.env.example').read_text().replace('/srv/ddn-formulario-candidato',str(target)).replace('ddn-formulario-candidato',args.project).replace('formulario.defensorianinez.gob.cl',args.hostname).replace('=24443','='+str(args.port)).replace('COMMIT',args.commit)
    write(target/'compose.env',env,uid=0)
    print(json.dumps({'preparacion':'COMPLETA','destino':str(target),'fuente':args.commit,'serviciosIniciados':False,'migracionesEjecutadas':False,'recepcion':False,'pull':False,'retirada':False}))
def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('destination');p.add_argument('--commit',required=True)
    p.add_argument('--cu-client-id');p.add_argument('--cu-secret');p.add_argument('--institutional-trust')
    p.add_argument('--sin-claveunica',action='store_true',help='Sin registro OAuth ni credenciales; login y callback responden 503')
    p.add_argument('--sin-confianza-ack',action='store_true',help='Sin claves ACK; integración, recepción y PULL cerrados')
    p.add_argument('--produccion',action='store_true',help='Exige identidad de firma externa aprobada; no genera una identidad de laboratorio')
    p.add_argument('--receipt-private-key');p.add_argument('--receipt-trust');p.add_argument('--receipt-key-id')
    p.add_argument('--hostname',default='formulario.defensorianinez.gob.cl');p.add_argument('--project',default='ddn-formulario-candidato');p.add_argument('--port',type=int,default=24443)
    try:prepare(p.parse_args())
    except (OSError,ValueError,RuntimeError) as e:print('Preparación detenida:',str(e),file=sys.stderr);return 1
    return 0
if __name__=='__main__':raise SystemExit(main())
