#!/usr/bin/env python3
"""Provisiona/rota acceso temporal fuera de Git; jamás muestra contraseña ni hash."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import tempfile
from productor import Productor

def write(path,data,uid):
    if path.is_symlink():raise ValueError('No se admiten enlaces')
    fd,name=tempfile.mkstemp(prefix='.acceso-',dir=path.parent)
    try:
        with os.fdopen(fd,'wb') as f:f.write(data);f.flush();os.fsync(f.fileno())
        os.chmod(name,0o400);os.chown(name,uid,uid);os.replace(name,path)
    finally:
        if Path(name).exists():Path(name).unlink()

def main():
    os.umask(0o077);p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',required=True);p.add_argument('action',choices=['provision','rotate']);a=p.parse_args()
    op=Productor(a.root)
    if os.geteuid()!=0 or op.meta['project']!='ddn-formulario-produccion':raise ValueError('Solo instalación definitiva y administrador')
    if any(x.is_symlink() or (x/'.git').exists() for x in [op.root,*op.root.parents]):raise ValueError('Custodia privada fuera de Git requerida')
    plain=op.root/'secrets/acceso-temporal-password';encoded=op.root/'keys/acceso-temporal-password.hash'
    if a.action=='provision' and (plain.exists() or encoded.exists()):raise ValueError('Ya provisionado; no sobrescribir')
    password=secrets.token_urlsafe(32);salt=secrets.token_bytes(16)
    value='pbkdf2-v5_8:'+(salt+hashlib.pbkdf2_hmac('sha256',password.encode(),salt,310000,32)).hex()
    write(plain,password.encode(),0);write(encoded,value.encode(),1000)
    approval=op.root/'keys/acceso-temporal-apertura.json'
    if not approval.exists():write(approval,b'{"authorized":false}',1000)
    op.config({'formulario.acceso-temporal.habilitado':'true','formulario.acceso-temporal.expira':'2026-12-15T00:00:00-03:00[America/Santiago]',
        'formulario.acceso-temporal.hash-path':'/run/keys/acceso-temporal-password.hash',
        'formulario.acceso-temporal.apertura-path':'/run/keys/acceso-temporal-apertura.json',
        'formulario.acceso-temporal.limites-path':'/var/lib/formulario/acceso-temporal/limites.json'})
    print(json.dumps({'credentialProvisioned':True,'plaintextPrinted':False,'restartRequired':a.action=='provision','expiresLocal':'2026-12-15T00:00:00 America/Santiago'}))

if __name__=='__main__':
    try:main()
    except (OSError,ValueError,KeyError):raise SystemExit('Operación rechazada; valores secretos omitidos')
