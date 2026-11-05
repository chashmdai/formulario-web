#!/usr/bin/env python3
"""Respaldo productivo cifrado con cierre/restauración CAS y verificación GPG.
No borra respaldos. Si falla, conserva recepción cerrada y datos existentes.
"""
import argparse
import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from productor import Productor

class RevisionChanged(ValueError):
    """El operador cambió CAS: preservar su decisión sin invalidar una copia ya verificada."""


def put_control(op, etag, accept, pull):
    status,_,body=op.request('PUT','/operacion/v1/control','operations-token',
        {'aceptarNuevos':accept,'recuperar':pull,'contingencia':'NORMAL' if accept or pull else 'SOLO_CONSULTA',
         'motivo':'REANUDACION' if accept or pull else 'MANTENIMIENTO'}, {'If-Match':etag})
    if status==412:raise RevisionChanged('CAS cambió; preservar decisión operacional')
    if status!=200:raise ValueError('CAS rechazado o resultado incierto; no reintentar')
    return json.loads(body)

def run_backup(op,directory,key):
    with (op.root/'backup-operation.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        status,h,body=op.request('GET','/operacion/v1/control','operations-token')
        if status!=200:raise ValueError('Control no disponible')
        prior=json.loads(body);was_open=prior['aceptarNuevos'] or prior['recuperar']
        closed=put_control(op,h['etag'],False,False) if was_open else prior
        if shutil.disk_usage(op.root).free<16*2**30:raise ValueError('Capacidad insuficiente para respaldo; recepción permanece cerrada')
        directory=Path(directory);directory.mkdir(mode=0o700,exist_ok=True)
        stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
        archive=directory/('formulario-'+stamp+'.gpg')
        command=['python3',str(op.root/'release/scripts/ubuntu/respaldo.py'),'backup','--private-dir',str(op.root),
            '--project',op.meta['project'],'--pisee-private-dir',str(op.root/'pisee/runtime'),
            '--pisee-node-config',str(op.root/'pisee/config-produccion.json'),'--pisee-state-dir',str(op.root/'pisee/state'),
            '--key-file',str(key),'--output',str(archive),'--maintenance']
        success=False
        try:
            with (op.root/'backup-private.log').open('ab') as log:
                result=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=log,stderr=log)
            if result.returncode:raise ValueError('Respaldo no completado')
            with tempfile.TemporaryDirectory(prefix='verify-gpg-',dir=directory) as tmp:
                result=subprocess.run(['gpg','--no-options','--homedir',tmp,'--batch','--yes','--pinentry-mode','loopback',
                    '--passphrase-file',str(key),'--decrypt','--output','/dev/null',str(archive)],stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            if result.returncode:raise ValueError('Verificación de cifrado/integridad falló')
            with archive.open('rb') as f:digest=hashlib.file_digest(f,'sha256').hexdigest()
            if archive.with_suffix('.gpg.sha256').read_text().strip()!=digest:raise ValueError('SHA de respaldo incorrecto')
            success=True
        finally:
            # Reanudar únicamente este stack; sin reiniciar daemon ni servicios históricos.
            op.compose('up','-d','--no-build','--wait','--wait-timeout','240')
        restored=False;operator_change=False
        if success and was_open:
            # ETag exacto del cierre: un operador puede mantener el cierre durante la copia.
            try:
                put_control(op,'"'+str(closed['revision'])+'"',prior['aceptarNuevos'],prior['recuperar']);restored=True
            except RevisionChanged:
                operator_change=True  # Copia válida, intención actual del operador prevalece.
        record={'atUtc':stamp,'archive':str(archive),'sha256':digest,'cipherAndIntegrityVerified':True,
                'previousControlRestored':restored,'operatorDecisionPreserved':operator_change,'retentionDeletesPerformed':False}
        (op.root/'backup-last-success.json').write_text(json.dumps(record,indent=2));print(json.dumps(record));return record


def main():
    os.umask(0o077);p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',required=True);a=p.parse_args()
    op=Productor(a.root)
    if os.geteuid()!=0 or op.meta['project']!='ddn-formulario-produccion':raise ValueError('Proyecto productivo y operador requeridos')
    run_backup(op,Path('/srv/ddn-formulario-respaldos/productivo-diario'),Path('/srv/ddn-formulario-custodia/backup-encryption-key'))

if __name__=='__main__':
    try:main()
    except (OSError,ValueError,KeyError):raise SystemExit('Respaldo rechazado/fallido; revisar estado CAS y diagnóstico privado sin secretos')
