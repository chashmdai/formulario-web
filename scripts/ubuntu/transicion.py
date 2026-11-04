#!/usr/bin/env python3
"""Transición reversible del puerto de origen, sin cambiar Cloudflare/DNS.
prepare solo lee. apply institucional requiere aprobación explícita vinculada al plan.
El ensayo usa otro puerto y un origen fixture, nunca certificación.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time
from productor import Productor,reception_authorization

def docker(*args):
    p=subprocess.run(['docker',*args],capture_output=True)
    if p.returncode:raise ValueError('Docker rechazó operación')
    return p.stdout

def snapshot(name):
    x=json.loads(docker('inspect',name))[0]
    mounts={}
    for m in x['Mounts']:
        path=Path(m['Source'])
        if path.is_file():mounts[str(path)]=sha(path)
        elif path.is_dir() and m['Destination']=='/run/pki':
            for f in path.iterdir():
                if f.is_file():mounts[str(f)]=sha(f)
    return {'id':x['Id'],'image':x['Image'],'configHash':hashlib.sha256(json.dumps(x['Config'],sort_keys=True).encode()).hexdigest(),
            'ports':x['HostConfig']['PortBindings'],'project':x['Config']['Labels'].get('com.docker.compose.project'),
            'running':x['State']['Running'],'mountFileHashes':mounts}

def sha(path):
    with Path(path).open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()

def set_port(op, port):
    p=op.root/'compose.env';lines=p.read_text().splitlines()
    p.write_text('\n'.join('FORM_HTTPS_PORT='+str(port) if l.startswith('FORM_HTTPS_PORT=') else l for l in lines)+'\n')
    op.meta['port']=port;(op.root/'installation.json').write_text(json.dumps(op.meta,indent=2));op.port=port

def restore(op, plan):
    # Liberar el puerto antes de devolverlo al Caddy anterior.
    docker('stop',op.meta['project']+'-proxy-1')
    docker('start',plan['source']['id'])
    set_port(op,plan['privatePort']);op.compose('up','-d','--no-build','--no-deps','--wait','proxy')
    if plan.get('sourceUnitWasEnabled'):
        result=subprocess.run(['systemctl','enable',plan['sourceUnit']],capture_output=True)
        if result.returncode:raise ValueError('Revisar arranque del origen anterior')

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',required=True)
    s=p.add_subparsers(dest='action',required=True)
    pre=s.add_parser('prepare');pre.add_argument('--source-container',required=True);pre.add_argument('--source-backup',required=True)
    ap=s.add_parser('apply');ap.add_argument('--test-only',action='store_true');ap.add_argument('--authorization')
    s.add_parser('rollback');s.add_parser('emergency-rollback');a=p.parse_args()
    try:
        op=Productor(a.root);folder=op.root/'transition';folder.mkdir(mode=0o700,exist_ok=True);path=folder/'plan.json'
        if a.action=='prepare':
            state=op.verify()
            if state['acceptNew'] or state['pull']:raise ValueError('Cerrar productor antes de preparar transición')
            source=snapshot(a.source_container)
            if not source['running']:raise ValueError('Origen anterior no está operativo')
            ports=source['ports'].get('8443/tcp',[])
            if len(ports)!=1 or ports[0]['HostIp']!='127.0.0.1':raise ValueError('Origen debe ser loopback dedicado')
            digest=sha(a.source_backup)
            if Path(a.source_backup+'.sha256').read_text().strip()!=digest:raise ValueError('Respaldo previo no íntegro')
            plan={'source':source,'backup':str(Path(a.source_backup).resolve()),'backupSha256':digest,
                  'privatePort':op.port,'publicPort':int(ports[0]['HostPort']),'targetProject':op.meta['project'],'status':'PREPARED'}
            if source['project']=='ddn-formulario-institucional':
                plan['sourceUnit']='ddn-formulario-institucional.service'
                plan['sourceUnitWasEnabled']=subprocess.run(['systemctl','is-enabled',plan['sourceUnit']],capture_output=True).returncode==0
            path.write_text(json.dumps(plan,indent=2));path.chmod(0o600)
            print(json.dumps({'prepared':True,'sourcePort':plan['publicPort'],'targetPrivatePort':op.port,'planSha256':sha(path)}));return
        plan=json.loads(path.read_text())
        if a.action in ['rollback','emergency-rollback']:
            if plan['status']!='APPLIED':raise ValueError('No hay transición aplicada')
            if a.action=='emergency-rollback':
                try:op.control(False,False)
                except (OSError,ValueError):pass
                # No dejar escritores accesibles durante una caída crítica; conservar SQL/volúmenes.
                docker('stop',op.meta['project']+'-app-1')
            else:
                state=op.verify()
                if state['acceptNew'] or state['pull']:raise ValueError('Cerrar controles y conciliar antes de revertir')
            restore(op,plan);plan['status']='ROLLED_BACK';path.write_text(json.dumps(plan,indent=2));print('Rollback completado');return
        if a.test_only:
            if plan['source']['project']=='ddn-formulario-institucional' or plan['publicPort']==24443:
                raise ValueError('El ensayo no puede tocar certificación')
        else:
            auth=json.loads(Path(a.authorization).read_text())
            if auth.get('planSha256')!=sha(path):raise ValueError('Autorización institucional del plan requerida')
            if auth.get('schema')=='RC1_PUBLICATION_AUTHORIZATION_V2':
                reception_authorization(auth)
                if auth.get('existingCallbackUseAuthorized') is not True:raise ValueError('Uso del callback requiere autorización')
            elif not all(auth.get(k) is True for k in ['authorized','windowsPhysicalE2e','receiptSignerApproved','providerAndCallbackApproved']):
                raise ValueError('Autorización institucional del plan requerida')
        if snapshot(plan['source']['id'])!=plan['source'] or sha(plan['backup'])!=plan['backupSha256']:
            raise ValueError('Origen o respaldo cambiaron: regenerar plan')
        state=op.verify()
        if state['acceptNew'] or state['pull']:raise ValueError('No realizar cutover con controles abiertos')
        docker('stop',plan['source']['id'])
        try:
            set_port(op,plan['publicPort']);op.compose('up','-d','--no-build','--no-deps','--wait','proxy')
            for _ in range(10):
                try:op.verify();break
                except (ValueError,OSError):time.sleep(1)
            else:raise ValueError('Smoke no conforme')
            if plan.get('sourceUnitWasEnabled'):
                result=subprocess.run(['systemctl','disable',plan['sourceUnit']],capture_output=True)
                if result.returncode:raise ValueError('No se pudo retirar autoarranque del origen sustituido')
        except Exception:
            restore(op,plan);raise
        plan['status']='APPLIED';path.write_text(json.dumps(plan,indent=2));print('Transición aplicada; controles siguen cerrados')
    except (OSError,ValueError,KeyError,TypeError):raise SystemExit('Transición rechazada o revertida; diagnóstico sin secretos')

if __name__=='__main__':main()
