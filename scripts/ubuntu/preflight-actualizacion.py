#!/usr/bin/env python3
"""Preflight SOLO LECTURA antes y después de actualizar la instalación productiva.

Comprueba presencia, tipo, permisos y propietario de todo lo que una actualización debe
preservar (identidades, firmas, certificados, credenciales, contraseña temporal, PISEE,
configuración y custodia), el espacio libre y el estado del timer de respaldo. Nunca imprime
contenidos, valores secretos ni nombres de documentos ciudadanos: solo rutas de material
operacional, contadores, tamaños y SHA-256.

  preflight-actualizacion.py --root /srv/ddn-formulario-produccion --snapshot antes.json
  preflight-actualizacion.py --root /srv/ddn-formulario-produccion --compare antes.json

La instantánea se escribe 0600 en un archivo NUEVO fuera de Git (contiene hashes de secretos).
Código de salida: 0 sin bloqueos, 1 con bloqueos, 2 entrada inválida.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess

GIB = 2 ** 30
# (ruta relativa, tipo, motivo). Su ausencia BLOQUEA la actualización.
REQUIRED = [
    ('installation.json', 'file', 'proyecto, puertos y wrapper Compose'),
    ('compose.env', 'file', 'variables Compose privadas'),
    ('config/application.properties', 'file', 'configuración privada'),
    ('secrets', 'dir', 'credenciales MySQL, tokens operacional e integración'),
    ('keys', 'dir', 'firma Ed25519, confianza histórica y antiabuso'),
    ('keys/receipt-trust.json', 'file', 'confianza de firmas históricas del Formulario'),
    ('pki', 'dir', 'certificados app, MySQL, proxy y origen'),
    ('store', 'dir', 'custodia de documentos'),
    ('mysql', 'dir', 'credenciales cliente MySQL'),
    ('secrets/acceso-temporal-password', 'file', 'contraseña temporal entregada'),
    ('keys/acceso-temporal-password.hash', 'file', 'hash pbkdf2 de la contraseña temporal'),
    ('keys/acceso-temporal-apertura.json', 'file', 'aprobación de apertura al vencer'),
    ('acceso-temporal', 'dir', 'estado de límites del acceso temporal'),
    ('pisee/runtime', 'dir', 'runtime PISEE institucional'),
    ('pisee/state', 'dir', 'estado durable del nodo PISEE'),
    ('pisee/config-produccion.json', 'file', 'configuración del nodo PISEE'),
]
OPTIONAL = ['cu', 'authorization', 'transition', 'intercambio-publico', 'keys/institutional-trust.json',
            'compose.origin-ca.yml', 'mysql-init', 'software', 'release']
# Árboles cuyo contenido se hashea archivo por archivo (material operacional, no ciudadano).
HASHED_TREES = ['config', 'secrets', 'keys', 'pki', 'cu', 'mysql', 'mysql-init', 'authorization',
                'acceso-temporal', 'pisee/config-produccion.json', 'compose.env', 'installation.json',
                'compose.origin-ca.yml']
# Árboles grandes o de terceros: solo contadores y bytes (sin nombres ni hashes).
COUNTED_TREES = ['store', 'pisee/runtime', 'pisee/state']
TEMPORARY_ACCESS_KEYS = ['habilitado', 'expira', 'hash-path', 'apertura-path', 'limites-path']


def private_root(path):
    root = Path(path).absolute()
    if any(p.is_symlink() or (p / '.git').exists() for p in [root, *root.parents]):
        raise ValueError('Raíz privada fuera de Git y sin enlaces requerida')
    if not (root / 'installation.json').is_file():
        raise ValueError('installation.json ausente: no es una instalación preparada')
    return root


def describe(path):
    st = path.lstat()
    return {'mode': oct(stat.S_IMODE(st.st_mode)), 'uid': st.st_uid, 'gid': st.st_gid}


def sha256(path):
    with path.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()


def walk(base):
    if base.is_symlink():
        raise ValueError('Enlace simbólico en material preservado')
    if base.is_file():
        yield base
        return
    for child in sorted(base.rglob('*')):
        if child.is_symlink():
            raise ValueError('Enlace simbólico en material preservado')
        if child.is_file():
            yield child


def properties(root):
    values = {}
    for line in (root / 'config/application.properties').read_text().splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            values[key.strip()] = value.strip()
    return values


def snapshot(root):
    hashed, counted = {}, {}
    for name in HASHED_TREES:
        base = root / name
        if base.exists() or base.is_symlink():
            for file in walk(base):
                hashed[str(file.relative_to(root))] = {'sha256': sha256(file), 'bytes': file.stat().st_size,
                                                       **describe(file)}
    for name in COUNTED_TREES:
        base = root / name
        files = list(walk(base)) if base.exists() else []
        counted[name] = {'files': len(files), 'bytes': sum(f.stat().st_size for f in files)}
    temporary = {}
    if (root / 'config/application.properties').is_file():
        props = properties(root)
        temporary = {k: props.get('formulario.acceso-temporal.' + k) for k in TEMPORARY_ACCESS_KEYS}
    return {'schema': 'DDN_UPDATE_PREFLIGHT_V1', 'hashed': hashed, 'counted': counted, 'temporaryAccess': temporary}


def timer_state():
    if not shutil.which('systemctl'):
        return 'NO_DISPONIBLE'
    result = subprocess.run(['systemctl', 'is-enabled', 'ddn-formulario-produccion-backup.timer'],
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    return result.stdout.strip() or 'DESCONOCIDO'


def check(root, min_free):
    blockers, warnings, items = [], [], []
    for relative, kind, reason in REQUIRED:
        path = root / relative
        ok = path.is_dir() if kind == 'dir' else path.is_file()
        if path.is_symlink():
            ok = False
        entry = {'path': relative, 'present': ok, 'reason': reason}
        if ok:
            entry.update(describe(path))
            if kind == 'file' and stat.S_IMODE(path.lstat().st_mode) & 0o077 and relative.startswith(('secrets/', 'keys/')):
                warnings.append(relative + ': permisos de grupo/otros sobre material privado')
        else:
            blockers.append(relative + ' ausente: ' + reason)
        items.append(entry)
    optional = {name: (root / name).exists() for name in OPTIONAL}
    meta = json.loads((root / 'installation.json').read_text())
    project = meta.get('project')
    if project != 'ddn-formulario-produccion':
        warnings.append('Proyecto distinto del productivo; preflight informativo')
    temporary = {}
    if (root / 'config/application.properties').is_file():
        props = properties(root)
        for key in TEMPORARY_ACCESS_KEYS:
            # Solo nombres y fechas/rutas de montaje: ningún valor secreto vive en estas propiedades.
            temporary[key] = props.get('formulario.acceso-temporal.' + key)
        if temporary.get('habilitado') == 'true' and not all(temporary.values()):
            blockers.append('Configuración de acceso temporal incompleta')
        flags = {k: props.get(k, 'false') for k in ['ciclo-vida.online-habilitado', 'ciclo-vida.eliminacion.habilitada',
                                                       'formulario.minimizacion-borradores.habilitada']}
    else:
        flags = {}
    usage = shutil.disk_usage(root)
    if usage.free < min_free:
        blockers.append('Espacio libre insuficiente para respaldo y actualización')
    return {'project': project, 'blockers': blockers, 'warnings': warnings, 'required': items,
            'optionalPresent': optional, 'temporaryAccess': temporary, 'retentionFlags': flags,
            'disk': {'totalBytes': usage.total, 'freeBytes': usage.free, 'minimumFreeBytes': min_free},
            'backupTimer': timer_state()}


def compare(before, after):
    changed = sorted(k for k in before['hashed'] if k in after['hashed'] and before['hashed'][k] != after['hashed'][k])
    missing = sorted(k for k in before['hashed'] if k not in after['hashed'])
    added = sorted(k for k in after['hashed'] if k not in before['hashed'])
    shrunk = sorted(k for k, v in before['counted'].items()
                    if after['counted'].get(k, {'files': 0})['files'] < v['files'])
    # La contraseña temporal entregada conserva configuración y vencimiento exactos.
    temporary = before.get('temporaryAccess') == after.get('temporaryAccess')
    return {'changed': changed, 'missing': missing, 'added': added, 'countedTreesShrunk': shrunk,
            'temporaryAccessUnchanged': temporary}


def main(argv=None):
    os.umask(0o077)
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--root', required=True)
    p.add_argument('--snapshot', help='Archivo NUEVO donde guardar hashes (0600)')
    p.add_argument('--compare', help='Instantánea previa para comparar tras la actualización')
    p.add_argument('--min-free-gib', type=float, default=20.0)
    a = p.parse_args(argv)
    try:
        root = private_root(a.root)
        report = check(root, int(a.min_free_gib * GIB))
        current = snapshot(root)
        report['preserved'] = {'hashedFiles': len(current['hashed']), 'counted': current['counted'],
                               'aggregateSha256': hashlib.sha256(json.dumps(current['hashed'], sort_keys=True).encode()).hexdigest()}
        if a.snapshot:
            target = Path(a.snapshot).absolute()
            if target.exists() or any(q.is_symlink() or (q / '.git').exists() for q in [target.parent, *target.parent.parents]):
                raise ValueError('Instantánea: archivo nuevo fuera de Git requerido')
            with target.open('x') as f:
                json.dump(current, f, sort_keys=True)
            target.chmod(0o600)
        if a.compare:
            diff = compare(json.loads(Path(a.compare).read_text()), current)
            report['comparison'] = diff
            # Identidades, certificados, credenciales y contraseña temporal deben quedar idénticos.
            for key in diff['changed'] + diff['missing']:
                if not key.startswith(('acceso-temporal/', 'config/application.properties', 'installation.json')):
                    report['blockers'].append('Material preservado modificado o ausente: ' + key)
            if not diff['temporaryAccessUnchanged']:
                report['blockers'].append('Configuración o vencimiento del acceso temporal modificados')
            if diff['countedTreesShrunk']:
                report['blockers'].append('Custodia o estado PISEE con menos archivos que antes')
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(json.dumps({'error': type(error).__name__, 'detail': str(error) if isinstance(error, ValueError) else None}))
        return 2
    print(json.dumps(report, indent=2, sort_keys=True))
    return 1 if report['blockers'] else 0


if __name__ == '__main__':
    raise SystemExit(main())
