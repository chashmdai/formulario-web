#!/usr/bin/env python3
"""Laboratorio multimedia independiente. No opera el JAR ni el esquema del baseline."""
import argparse,json,os,signal,socket,subprocess,time,shutil
from pathlib import Path
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('private',type=Path);parser.add_argument('--jar',type=Path);parser.add_argument('--migrar',action='store_true');parser.add_argument('--detener',action='store_true');args=parser.parse_args();p=args.private.resolve();os.umask(0o077)
if not p.is_dir() or any((v/'.git').exists() for v in [p,*p.parents]):parser.error('Directorio privado existente fuera de Git requerido')
props=dict(line.split('=',1) for line in (p/'application.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
if not (props.get('server.address')=='127.0.0.1' and props.get('server.port')=='19443' and props.get('server.ssl.enabled')=='true' and props.get('siger.claveunica.callback-approved')=='false' and props.get('PISEE_SRCEI_ENABLED')=='false' and props.get('formulario.multimedia.habilitada')=='true' and '/robust_public_media_lab?' in props.get('publico.db.url','') and 'PISEE_ALLOWED_RUNS_PATH' not in props):parser.error('Solo laboratorio multimedia aislado sin proveedores externos')
jar=p/'runtime.jar'
if args.detener:
 pid=int((p/'app.pid').read_text());proc=Path(f'/proc/{pid}')
 if not proc.exists():raise SystemExit('El proceso ya está detenido')
 if str(jar).encode() not in (proc/'cmdline').read_bytes() or str(p/'application.properties').encode() not in (proc/'environ').read_bytes():parser.error('El PID no corresponde a este laboratorio')
 os.kill(pid,signal.SIGTERM);print('Se detuvo exclusivamente el laboratorio multimedia:',pid);raise SystemExit(0)
with socket.socket() as sock:
 if sock.connect_ex(('127.0.0.1',19443))==0:parser.error('Puerto ocupado; no se detiene ni sobrescribe proceso alguno')
if args.jar:
 if not args.jar.is_file():parser.error('JAR compilado requerido')
 shutil.copyfile(args.jar,p/'runtime.jar.next');os.replace(p/'runtime.jar.next',jar)
if not jar.is_file():parser.error('Falta JAR privado; proporcionar --jar')
if args.migrar:
 env=dict(os.environ,**json.loads((p/'migration.json').read_text()))
 with (p/'migration.log').open('ab') as log:result=subprocess.run(['java','-Xmx256m','-jar',str(jar),'--migrate'],env=env,stdout=log,stderr=subprocess.STDOUT)
 if result.returncode:raise SystemExit('Migración no completada; revisar diagnóstico privado')
with (p/'app.log').open('ab') as log:process=subprocess.Popen(['java','-Djava.net.preferIPv4Stack=true','-Xms64m','-Xmx384m','-jar',str(jar)],env=dict(os.environ,SPRING_CONFIG_ADDITIONAL_LOCATION='file:'+str(p/'application.properties')),stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
(p/'app.pid').write_text(str(process.pid));print('Laboratorio multimedia iniciado en HTTPS loopback 19443; PID',process.pid)