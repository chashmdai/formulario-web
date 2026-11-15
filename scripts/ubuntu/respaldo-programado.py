#!/usr/bin/env python3
"""Respaldo productivo con cierre/restauración CAS y verificación completa.
Retención opt-in (--keep >=2), únicamente de copias propias verificadas.
Si falla, conserva recepción cerrada y datos existentes.
"""
import argparse
import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
from productor import Productor

SCHEMA = 'DDN_SCHEDULED_BACKUP_VERIFIED_V1'
NAME = re.compile(r'formulario-\d{8}T\d{6}Z\.gpg')


class RetentionFailed(Exception):
    """El respaldo quedó verificado y registrado; solo la retención opt-in falló."""


def put_control(op, etag, accept, pull):
    status, _, body = op.request('PUT', '/operacion/v1/control', 'operations-token',
        {'aceptarNuevos': accept, 'recuperar': pull,
         'contingencia': 'NORMAL' if accept or pull else 'SOLO_CONSULTA',
         'motivo': 'REANUDACION' if accept or pull else 'MANTENIMIENTO'}, {'If-Match': etag})
    if status != 200:
        raise ValueError('CAS cambió; no sobreescribir decisiones operacionales')
    return json.loads(body)


def digest(path):
    with path.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()


def marker(archive):
    return archive.with_suffix('.gpg.verified.json')


def mark_verified(archive, sha):
    # Procedencia adicional al patrón y SHA: nunca adoptar copias/archivos ajenos
    # ni antiguos no verificados por esta versión para borrarlos automáticamente.
    with marker(archive).open('x') as f:
        json.dump({'schema': SCHEMA, 'archive': archive.name, 'sha256': sha}, f)


def retain(directory, current, keep):
    if keep is None:
        return 0
    if keep < 2:
        raise ValueError('Retención requiere al menos dos copias')
    candidates = []
    for archive in directory.iterdir():
        if not NAME.fullmatch(archive.name):
            continue
        sidecar = archive.with_suffix('.gpg.sha256')
        proof = marker(archive)
        if any(p.is_symlink() or not p.is_file() for p in [archive, sidecar, proof]):
            continue
        try:
            record = json.loads(proof.read_text())
            sha = sidecar.read_text().strip()
            if (isinstance(record, dict) and record.get('schema') == SCHEMA and record.get('archive') == archive.name
                    and record.get('sha256') == sha and re.fullmatch('[a-f0-9]{64}', sha)
                    and digest(archive) == sha):
                candidates.append(archive)
        except (OSError, ValueError):
            continue
    if current not in candidates:
        raise ValueError('Último respaldo verificado no identificado; no borrar')
    # Conservar siempre el recién verificado, incluso ante relojes/fechas futuras.
    others = sorted((p for p in candidates if p != current), reverse=True)
    deletes = 0
    for archive in others[keep - 1:]:
        archive.unlink()
        archive.with_suffix('.gpg.sha256').unlink()
        marker(archive).unlink()
        deletes += 1
    return deletes


def main(argv=None):
    os.umask(0o077)
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root', required=True)
    p.add_argument('--directory', default='/srv/ddn-formulario-respaldos/productivo-diario')
    p.add_argument('--key-file', default='/srv/ddn-formulario-custodia/backup-encryption-key')
    p.add_argument('--keep', type=int, help='Opt-in, mínimo 2; sin esta opción no elimina nada')
    p.add_argument('--capacity-factor', type=float, default=1.25)
    p.add_argument('--reserve-bytes', type=int, help='Por defecto max(10 GiB, 10%% del filesystem)')
    a = p.parse_args(argv)
    if a.keep is not None and a.keep < 2:
        p.error('--keep requiere N >= 2')
    op = Productor(a.root)
    if os.geteuid() != 0 or op.meta['project'] != 'ddn-formulario-produccion':
        raise ValueError('Proyecto productivo y operador requeridos')
    with (op.root / 'backup-operation.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        status, h, body = op.request('GET', '/operacion/v1/control', 'operations-token')
        if status != 200:
            raise ValueError('Control no disponible')
        prior = json.loads(body)
        was_open = prior['aceptarNuevos'] or prior['recuperar']
        closed = put_control(op, h['etag'], False, False) if was_open else prior
        directory = Path(a.directory).absolute()
        if any(p.is_symlink() or (p / '.git').exists() for p in [directory, *directory.parents]):
            raise ValueError('Directorio privado externo sin enlaces requerido')
        directory.mkdir(mode=0o700, exist_ok=True)
        stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
        archive = directory / ('formulario-' + stamp + '.gpg')
        # Siempre la herramienta hermana de ESTE script: ejecutado desde un paquete verificado no
        # mezcla versiones con release/ (la instalada puede carecer de verify/estimate/capacidad).
        sibling = Path(__file__).resolve().parent / 'respaldo.py'
        if sibling.is_symlink() or not sibling.is_file():
            raise ValueError('respaldo.py hermano requerido')
        script = str(sibling)
        command = ['python3', script, 'backup', '--private-dir', str(op.root),
            '--project', op.meta['project'], '--pisee-private-dir', str(op.root / 'pisee/runtime'),
            '--pisee-node-config', str(op.root / 'pisee/config-produccion.json'),
            '--pisee-state-dir', str(op.root / 'pisee/state'), '--key-file', a.key_file,
            '--output', str(archive), '--maintenance', '--capacity-factor', str(a.capacity_factor)]
        if a.reserve_bytes is not None:
            command += ['--reserve-bytes', str(a.reserve_bytes)]
        success = False
        try:
            with (op.root / 'backup-private.log').open('ab') as log:
                result = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=log, stderr=log)
                if result.returncode:
                    raise ValueError('Respaldo no completado (incluye preflight de capacidad)')
                result = subprocess.run(['python3', script, 'verify', '--archive', str(archive),
                    '--key-file', a.key_file], stdin=subprocess.DEVNULL, stdout=log, stderr=log)
            if result.returncode:
                raise ValueError('Verificación de cifrado/SHA/manifiesto falló')
            sha = digest(archive)
            mark_verified(archive, sha)
            success = True
        finally:
            # Reanudar únicamente este stack; sin reiniciar daemon ni servicios históricos.
            op.compose('up', '-d', '--no-build', '--wait', '--wait-timeout', '240')
        restored = False
        if success and was_open:
            # Reabrir antes de retener: un fallo de retención nunca debe dejar la recepción cerrada.
            put_control(op, '"' + str(closed['revision']) + '"',
                        prior['aceptarNuevos'], prior['recuperar'])
            restored = True
        record = {'atUtc': stamp, 'archive': str(archive), 'sha256': sha,
                  'cipherAndIntegrityVerified': True, 'fileManifestVerified': True,
                  'previousControlRestored': restored, 'retentionDeletesPerformed': 0}
        success_file = op.root / 'backup-last-success.json'
        success_file.write_text(json.dumps(record, indent=2))
        retention_error = None
        try:
            record['retentionDeletesPerformed'] = retain(directory, archive, a.keep)
        except (OSError, ValueError) as error:
            # Sin rutas ni nombres: solo la clase del error. El respaldo sigue siendo válido.
            retention_error = type(error).__name__
            record['retentionError'] = retention_error
        success_file.write_text(json.dumps(record, indent=2))
        print(json.dumps(record))
        if retention_error:
            raise RetentionFailed('Respaldo verificado y control restaurado; retención no aplicada')


if __name__ == '__main__':
    try:
        main()
    except RetentionFailed:
        raise SystemExit(3)
    except (OSError, ValueError, KeyError):
        raise SystemExit('Respaldo rechazado/fallido; revisar estado CAS y diagnóstico privado sin secretos')
