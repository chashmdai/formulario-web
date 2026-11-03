#!/usr/bin/env python3
"""Respaldo cifrado consistente y restauración a directorio NUEVO, nunca sobre origen.
Clave de cifrado externa 0400/0600 (>=32 bytes), separada de la copia.
Los mensajes no contienen SQL, secretos, nombres de archivos de evidencia ni PII.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile

def run(args, **kwargs):
    p = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kwargs)
    if p.returncode:
        raise RuntimeError('Herramienta falló; diagnóstico omitido para proteger datos')
    return p.stdout

def private(path):
    p = Path(path).absolute()
    if any(x.is_symlink() or (x / '.git').exists() for x in [p, *p.parents]):
        raise ValueError('Custodia externa sin enlaces requerida')
    return p

def key(path):
    p = private(path)
    if not p.is_file() or p.stat().st_mode & 0o077 or p.stat().st_size < 32:
        raise ValueError('Clave externa privada de al menos 32 bytes requerida')
    return p

def containers(project):
    if project in {'ddn-formulario-institucional', 'ddn-release-pisee-lab-a', 'ddn-release-pisee-lab-b'}:
        raise ValueError('Proyecto protegido')
    ids = run(['docker', 'ps', '-aq', '--filter', 'label=com.docker.compose.project=' + project]).decode().split()
    if not ids:
        raise ValueError('Proyecto inexistente')
    return json.loads(run(['docker', 'inspect', *ids]))

def inventory(root):
    result = {}
    for p in root.rglob('*'):
        if p.is_symlink():
            raise ValueError('Enlace en respaldo no permitido')
        if p.is_file() and p.name != 'backup-manifest.json':
            with p.open('rb') as f:
                result[str(p.relative_to(root))] = hashlib.file_digest(f, 'sha256').hexdigest()
    return result

def backup(a):
    origin, output, password = private(a.private_dir), private(a.output), key(a.key_file)
    if output.exists() or origin == output or origin in output.parents:
        raise ValueError('Salida nueva fuera del origen requerida')
    cs = containers(a.project)
    mysql = next(c for c in cs if c['Config']['Labels'].get('com.docker.compose.service') == 'mysql')
    writers = [c['Id'] for c in cs if c['Config']['Labels'].get('com.docker.compose.service') in {'app', 'pisee', 'proxy'} and c['State']['Running']]
    if writers and not a.maintenance:
        raise ValueError('Respaldo consistente requiere ventana --maintenance')
    try:
        if writers:
            run(['docker', 'stop', '--time', '40', *writers])
        with tempfile.TemporaryDirectory(prefix='ddn-backup-', dir=output.parent) as folder:
            gpg = Path(folder) / 'gpg'; gpg.mkdir(mode=0o700)
            root = Path(folder) / 'payload'; root.mkdir(mode=0o700)
            for name in ['config', 'secrets', 'cu', 'keys', 'pki', 'store', 'mysql', 'mysql-init', 'compose.env', 'clamav']:
                src = origin / name
                if src.is_dir(): shutil.copytree(src, root / name, symlinks=True)
                elif src.is_file(): shutil.copy2(src, root / name)
            # Copia criptografía/config/estado institucional; no datos de campañas ajenas.
            shutil.copytree(private(a.pisee_private_dir), root / 'pisee', symlinks=True)
            node_config = private(a.pisee_node_config)
            shutil.copy2(node_config, root / 'pisee' / 'config-produccion.json')
            sql = root / 'database.sql'
            with sql.open('wb') as f:
                p = subprocess.run(['docker','exec',mysql['Id'],'mysqldump','--defaults-extra-file=/run/secrets/root.cnf',
                    '--single-transaction','--quick','--hex-blob','--routines','--triggers','--events','--no-tablespaces',
                    '--set-gtid-purged=OFF','--default-character-set=utf8mb4','formulario_publico'],stdout=f,stderr=subprocess.PIPE)
                if p.returncode: raise RuntimeError('Respaldo SQL rechazado')
            manifest = {'version':1, 'project':a.project, 'files':inventory(root)}
            (root / 'backup-manifest.json').write_text(json.dumps(manifest, sort_keys=True))
            archive = Path(folder) / 'archive.tar'
            with tarfile.open(archive, 'w') as tar:
                for child in root.iterdir(): tar.add(child, arcname=child.name)
            run(['gpg','--no-options','--homedir',str(gpg),'--batch','--yes','--pinentry-mode','loopback','--passphrase-file',str(password),
                 '--symmetric','--cipher-algo','AES256','--output',str(output),str(archive)])
            output.chmod(0o600)
            digest = hashlib.file_digest(output.open('rb'), 'sha256').hexdigest()
            output.with_suffix(output.suffix + '.sha256').write_text(digest + '\n')
    finally:
        if writers: run(['docker','start',*reversed(writers)])
    print('Respaldo cifrado completo; escritores reanudados; conservar clave en custodia separada')

def restore(a):
    target, source, password = private(a.destination), private(a.archive), key(a.key_file)
    if target.exists(): raise ValueError('Restauración requiere directorio NUEVO')
    expected = source.with_suffix(source.suffix + '.sha256').read_text().strip()
    if hashlib.file_digest(source.open('rb'), 'sha256').hexdigest() != expected:
        raise ValueError('Hash de respaldo inválido')
    with tempfile.TemporaryDirectory(prefix='ddn-restore-', dir=target.parent) as folder:
        gpg = Path(folder) / 'gpg'; gpg.mkdir(mode=0o700)
        archive = Path(folder) / 'archive.tar'
        run(['gpg','--no-options','--homedir',str(gpg),'--batch','--yes','--pinentry-mode','loopback','--passphrase-file',str(password),
             '--output',str(archive),'--decrypt',str(source)])
        root = Path(folder) / 'payload'; root.mkdir(mode=0o700)
        with tarfile.open(archive) as tar:
            for entry in tar.getmembers():
                p = Path(entry.name)
                if p.is_absolute() or '..' in p.parts or not (entry.isfile() or entry.isdir()):
                    raise ValueError('Miembro de respaldo no permitido')
            tar.extractall(root, filter='data')
        manifest = json.loads((root / 'backup-manifest.json').read_text())
        if inventory(root) != manifest['files']: raise ValueError('Contenido restaurado no coincide')
        # Preserva propietarios del runtime; filter=data elimina uid arbitrarios del TAR.
        if os.geteuid() == 0:
            for name in ['config','secrets','cu','keys','pki','store','clamav','pisee']:
                for p in [root / name, *(root / name).rglob('*')]:
                    if p.exists(): os.chown(p, 1000, 1000)
            for name in ['pki/mysql','mysql','pki/authority']:
                uid = 999 if name != 'pki/authority' else 0
                for p in [root / name, *(root / name).rglob('*')]:
                    if p.exists(): os.chown(p, uid, uid)
            for name in ['secrets/mysql-root-password','mysql/root.cnf','compose.env']:
                if (root / name).exists(): os.chown(root / name, 0, 0)
        shutil.move(root, target)
    print('Restauración descifrada e íntegra en destino nuevo; NO se inició aplicación ni se importó SQL')

def main():
    os.umask(0o077)
    p = argparse.ArgumentParser(description=__doc__); sub = p.add_subparsers(dest='command', required=True)
    b=sub.add_parser('backup')
    for name in ['private-dir','project','output','key-file','pisee-private-dir','pisee-node-config']:
        b.add_argument('--'+name,required=True)
    b.add_argument('--maintenance',action='store_true')
    r=sub.add_parser('restore')
    for name in ['destination','archive','key-file']: r.add_argument('--'+name,required=True)
    a=p.parse_args()
    try: backup(a) if a.command=='backup' else restore(a)
    except (OSError, ValueError, RuntimeError, KeyError, StopIteration, tarfile.TarError):
        raise SystemExit('Operación rechazada; origen preservado; revisar entrada y estado sin imprimir secretos')

if __name__ == '__main__': main()
