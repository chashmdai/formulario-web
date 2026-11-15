#!/usr/bin/env python3
"""Respaldo cifrado consistente y restauración a directorio NUEVO, nunca sobre origen.
Clave de cifrado externa 0400/0600 (>=32 bytes), separada de la copia.
Los mensajes no contienen SQL, secretos, nombres de archivos de evidencia ni PII.
"""
import argparse
import hashlib
import io
import math
import re
import json
import os
from pathlib import Path
import shutil
import stat
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

def containers(project, certification=False):
    if project in {'ddn-formulario-institucional', 'ddn-release-pisee-lab-a', 'ddn-release-pisee-lab-b'} and not (certification and project=='ddn-formulario-institucional'):
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
        if p.is_file() and p != root / 'backup-manifest.json':
            with p.open('rb') as f:
                result[str(p.relative_to(root))] = hashlib.file_digest(f, 'sha256').hexdigest()
    return result

def ephemeral_sockets(directory, names):
    # IPC del nodo se recrea al arrancar; jamás copiarlo como estado durable.
    return [name for name in names if stat.S_ISSOCK((Path(directory)/name).lstat().st_mode)]

# Política conservadora: identidades (también acceso temporal), firmas históricas,
# configuración, SQL, documentos y todo PISEE durable son obligatorios. mysql/
# contiene credenciales .cnf: NO es sinónimo de datadir. Solo se omite un datadir
# bajo mysql/ identificado por el mount Docker /var/lib/mysql. ClamAV: solo firmas.
# release/software se conservan salvo SHA exacto por archivo declarado en
# installation.json.backupReproducibleFiles; un digest de imagen no basta.
CATEGORIES = {
    'identity': ['secrets', 'cu', 'keys', 'pki', 'acceso-temporal', 'intercambio-publico'],
    'configuration': ['config', 'compose.env', 'installation.json', 'compose.origin-ca.yml',
                      'authorization', 'transition', 'mysql-init', 'mysql'],
    'documents': ['store'],
    'reproducible': ['release', 'software', 'clamav'],
}


def sha256(path):
    with path.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()


def mysql_container(a):
    cs = containers(a.project, a.certification_readonly)
    mysql = next(c for c in cs if c['Config']['Labels'].get('com.docker.compose.service') == 'mysql')
    return cs, mysql


def selection(a, mysql):
    origin = private(a.private_dir)
    installed = origin / 'installation.json'
    # Un destino preparado aún no instalado no declara digests: se conserva release/ y software/ completos.
    metadata = json.loads(installed.read_text()) if installed.is_file() else {}
    if not isinstance(metadata, dict):
        raise ValueError('Metadatos de instalación inválidos')
    reproducible = metadata.get('backupReproducibleFiles', {})
    if not isinstance(reproducible, dict):
        raise ValueError('Inventario reproducible inválido')
    datadirs = set()
    for mount in mysql.get('Mounts', []):
        if mount.get('Destination') == '/var/lib/mysql' and mount.get('Type') == 'bind':
            source = private(mount['Source'])
            # Nunca omitir mysql/ entero, pues root.cnf/health.cnf son obligatorios.
            if origin / 'mysql' in source.parents:
                datadirs.add(source)
    entries, counts = {}, {name: 0 for name in CATEGORIES}
    counts.update(pisee=0, excludedRegenerable=0)

    def walk(source, name, category, ipc=False):
        mode = source.lstat().st_mode
        if stat.S_ISLNK(mode):
            raise ValueError('Enlace en respaldo no permitido')
        if stat.S_ISSOCK(mode) and ipc:
            return
        if not (stat.S_ISDIR(mode) or stat.S_ISREG(mode)):
            raise ValueError('Miembro de respaldo no permitido')
        if source in datadirs:
            # No recorrer datadir activo; SQL transaccional es la copia recuperable.
            counts['excludedRegenerable'] += tree_bytes(source)
            return
        if stat.S_ISDIR(mode):
            entries[name] = (source, category)
            for child in sorted(source.iterdir()):
                walk(child, name + '/' + child.name, category, ipc)
            return
        size = source.stat().st_size
        signature = name.startswith('clamav/') and re.fullmatch(r'(main|daily|bytecode)\.(cvd|cld)', source.name)
        expected = reproducible.get(name)
        identified = (name.split('/')[0] in {'release', 'software'}
                      and isinstance(expected, str) and re.fullmatch('[a-f0-9]{64}', expected)
                      and sha256(source) == expected)
        if signature or identified:
            counts['excludedRegenerable'] += size
        else:
            entries[name] = (source, category)

    for category, names in CATEGORIES.items():
        for name in names:
            source = origin / name
            if source.exists() or source.is_symlink():
                walk(source, name, category)
    if not a.certification_readonly:
        if not all([a.pisee_private_dir, a.pisee_node_config, a.pisee_state_dir]):
            raise ValueError('Respaldo completo requiere runtime/config/estado del nodo')
        runtime = private(a.pisee_private_dir)
        state = private(a.pisee_state_dir)
        node_config = private(a.pisee_node_config)
        if not runtime.is_dir() or not state.is_dir() or not node_config.is_file():
            raise ValueError('Runtime/estado y configuración PISEE requeridos')
        walk(runtime, 'pisee', 'pisee', True)
        # Sustituir state/config del runtime por los destinos explícitos, como antes.
        entries = {n: e for n, e in entries.items()
                   if n != 'pisee/state' and not n.startswith('pisee/state/')
                   and n != 'pisee/config-produccion.json'}
        walk(state, 'pisee/state', 'pisee', True)
        walk(node_config, 'pisee/config-produccion.json', 'pisee')
    for source, category in entries.values():
        if source.is_file():
            counts[category] += source.stat().st_size
    return entries, counts


def tree_bytes(root):
    # Solo contadores; jamás seguir enlaces, ni imprimir rutas del almacén.
    total = 0
    for base, dirs, files in os.walk(root, followlinks=False):
        for name in dirs + files:
            p = Path(base) / name
            mode = p.lstat().st_mode
            if stat.S_ISLNK(mode):
                raise ValueError('Enlace en respaldo no permitido')
            if stat.S_ISREG(mode):
                total += p.stat().st_size
    return total


def capacity(a, mysql, entries, counts, sql_bytes=None):
    if sql_bytes is None:
        # Solo metadatos de MySQL, sin filas ni consultas a proveedores. Hex-blob,
        # escapes/INSERT e índices justifican margen; no promete ratio de compresión.
        allocated = int(run(['docker', 'exec', mysql['Id'], 'mysql',
            '--defaults-extra-file=/run/secrets/root.cnf', '--batch', '--skip-column-names',
            '-e', "SELECT COALESCE(SUM(data_length+index_length),0) FROM information_schema.tables "
                  "WHERE table_schema='formulario_publico'"]).decode().strip())
        if allocated < 0:
            raise ValueError('Tamaño SQL inválido')
        sql_bytes = allocated * 6 + 16 * 2**20
    counts = dict(counts, sqlDump=sql_bytes)
    # TAR/PAX, manifiesto por archivo, gzip incluso incompresible y cabeceras GPG.
    raw = sum(v for k, v in counts.items() if k != 'excludedRegenerable')
    estimated = math.ceil((raw + (len(entries) + 2) * 4096 + 10240) * 1.02) + 2**20
    usage = shutil.disk_usage(private(a.output).parent)
    factor = a.capacity_factor
    reserve = a.reserve_bytes if a.reserve_bytes is not None else max(10 * 2**30, math.ceil(usage.total * .10))
    if not math.isfinite(factor) or factor < 1 or reserve < 0:
        raise ValueError('Margen de capacidad inválido')
    # SQL es el único temporal en claro; presupuestarlo además del archivo cifrado.
    required = math.ceil(estimated * factor) + reserve + sql_bytes
    return {'bytesByCategory': counts, 'estimatedArchiveBytes': estimated,
            'sqlTemporaryBytes': sql_bytes, 'freeBytes': usage.free,
            'reserveBytes': reserve, 'capacityFactor': factor, 'requiredFreeBytes': required}


def check_capacity(report):
    if report['freeBytes'] < report['requiredFreeBytes']:
        raise ValueError('Capacidad insuficiente; recepción permanece cerrada')


def estimate(a):
    _, mysql = mysql_container(a)
    entries, counts = selection(a, mysql)
    print(json.dumps(capacity(a, mysql, entries, counts), sort_keys=True))


def gpg_command(home, password):
    return ['gpg', '--no-options', '--homedir', str(home), '--batch', '--yes',
            '--pinentry-mode', 'loopback', '--passphrase-file', str(password)]


class HashReader:
    def __init__(self, stream):
        self.stream, self.hash = stream, hashlib.sha256()

    def read(self, size=-1):
        block = self.stream.read(size)
        self.hash.update(block)
        return block


def encrypt_tar(encrypted, password, home, entries, project):
    # El único archivo de salida contiene ciphertext desde el primer byte. Sin
    # copytree ni TAR en claro; gzip/TAR y hash por archivo comparten el mismo flujo.
    proc = subprocess.Popen(gpg_command(home, password) + [
        '--compress-algo', 'none', '--symmetric', '--cipher-algo', 'AES256', '--output', '-'],
        stdin=subprocess.PIPE, stdout=encrypted, stderr=subprocess.DEVNULL, bufsize=0)
    try:
        hashes = {}
        with tarfile.open(fileobj=proc.stdin, mode='w|gz') as tar:
            for name, (source, _) in entries.items():
                mode = source.lstat().st_mode
                if not (stat.S_ISREG(mode) or stat.S_ISDIR(mode)):
                    raise ValueError('Miembro de respaldo no permitido')
                info = tarfile.TarInfo(name)
                info.mode = mode & 0o777
                info.mtime = int(source.stat().st_mtime)
                if stat.S_ISDIR(mode):
                    info.type = tarfile.DIRTYPE
                    tar.addfile(info)
                else:
                    with source.open('rb') as stream:
                        info.size = os.fstat(stream.fileno()).st_size
                        reader = HashReader(stream)
                        tar.addfile(info, reader)
                        hashes[name] = reader.hash.hexdigest()
            manifest = json.dumps({'version': 2, 'project': project, 'files': hashes}, sort_keys=True).encode()
            info = tarfile.TarInfo('backup-manifest.json')
            info.mode, info.size = 0o600, len(manifest)
            tar.addfile(info, io.BytesIO(manifest))
        proc.stdin.close()
        if proc.wait():
            raise RuntimeError('Cifrado no completado')
        encrypted.flush()
        os.fsync(encrypted.fileno())
    finally:
        if not proc.stdin.closed:
            proc.stdin.close()
        if proc.poll() is None:
            proc.kill()
        proc.wait()


def dump_sql(mysql, sql, limit):
    command = ['docker', 'exec', mysql['Id'], 'mysqldump',
               '--defaults-extra-file=/run/secrets/root.cnf', '--single-transaction', '--quick',
               '--hex-blob', '--routines', '--triggers', '--events', '--no-tablespaces',
               '--set-gtid-purged=OFF', '--default-character-set=utf8mb4', 'formulario_publico']
    with sql.open('xb') as f:
        os.fchmod(f.fileno(), 0o600)
        proc = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        try:
            written = 0
            while block := proc.stdout.read(1024 * 1024):
                written += len(block)
                if written > limit:
                    raise ValueError('Dump excede presupuesto estimado; no agotar disco')
                f.write(block)
            if proc.wait():
                raise RuntimeError('Respaldo SQL rechazado')
        finally:
            proc.stdout.close()
            if proc.poll() is None:
                proc.kill()
            proc.wait()


def backup(a):
    origin, output, password = private(a.private_dir), private(a.output), key(a.key_file)
    sidecar = output.with_suffix(output.suffix + '.sha256')
    if origin in password.parents:
        raise ValueError('Clave de respaldo debe custodiarse fuera del origen respaldado')
    if output.exists() or sidecar.exists() or origin == output or origin in output.parents:
        raise ValueError('Salida nueva fuera del origen requerida')
    cs, mysql = mysql_container(a)
    writers = [c['Id'] for c in cs if c['Config']['Labels'].get('com.docker.compose.service')
               in {'app', 'pisee', 'proxy'} and c['State']['Running']]
    if a.certification_readonly:
        props = dict(l.split('=', 1) for l in (origin / 'config/application.properties').read_text().splitlines()
                     if '=' in l and not l.startswith('#'))
        if props.get('siger.certificacion-claveunica') != 'true' or a.maintenance:
            raise ValueError('Respaldo de certificación solo de lectura y sin mantenimiento')
        for flag in ['publico.recepcion.habilitada', 'publico.nna.habilitado', 'formulario.adultos.habilitado',
                     'formulario.recepcion.habilitada', 'ciclo-vida.online-habilitado',
                     'ciclo-vida.eliminacion.habilitada', 'PISEE_SRCEI_ENABLED']:
            if props.get(flag, 'false') != 'false':
                raise ValueError('Origen no es certificación cerrada')
        control = run(['docker', 'exec', mysql['Id'], 'mysql', '--defaults-extra-file=/run/secrets/root.cnf',
                       '--batch', '--skip-column-names', 'formulario_publico', '-e',
                       'SELECT accept_new,pull_enabled FROM operation_control']).decode().strip()
        if control != '0\t0':
            raise ValueError('Certificación exige controles cerrados')
        writers = []
    if writers and not a.maintenance:
        raise ValueError('Respaldo consistente requiere ventana --maintenance')
    entries, counts = selection(a, mysql)
    for source, _ in entries.values():
        if source in {password, output} or (source.is_dir()
                and (source in password.parents or source in output.parents)):
            raise ValueError('Clave y salida externas a todos los árboles respaldados requeridas')
    check_capacity(capacity(a, mysql, entries, counts))
    started = False
    output_owned = sidecar_owned = False
    try:
        if writers:
            started = True
            run(['docker', 'stop', '--time', '40', *writers])
        # Releer inventario después de detener escritores. Solo SQL en temporal 0700.
        entries, counts = selection(a, mysql)
        plan = capacity(a, mysql, entries, counts)
        check_capacity(plan)
        with tempfile.TemporaryDirectory(prefix='ddn-backup-', dir=output.parent) as folder:
            root = Path(folder)
            root.chmod(0o700)
            home = root / 'gpg'
            home.mkdir(mode=0o700)
            sql = root / 'database.sql'
            dump_sql(mysql, sql, plan['sqlTemporaryBytes'])
            check_capacity(capacity(a, mysql, entries, counts, sql.stat().st_size))
            entries['database.sql'] = (sql, 'sqlDump')
            with output.open('xb') as encrypted:
                output_owned = True
                os.fchmod(encrypted.fileno(), 0o600)
                encrypt_tar(encrypted, password, home, entries, a.project)
            with sidecar.open('x') as f:
                sidecar_owned = True
                os.fchmod(f.fileno(), 0o600)
                f.write(sha256(output) + '\n')
    except BaseException:
        if output_owned:
            output.unlink(missing_ok=True)
        if sidecar_owned:
            sidecar.unlink(missing_ok=True)
        raise
    finally:
        if started:
            run(['docker', 'start', *reversed(writers)])
    print('Certificación respaldada en solo lectura; servicios sin cambios' if a.certification_readonly
          else 'Respaldo cifrado completo; escritores reanudados; conservar clave en custodia separada')


def read_archive(source, password, home, root=None):
    expected = source.with_suffix(source.suffix + '.sha256').read_text().strip()
    if sha256(source) != expected:
        raise ValueError('Hash de respaldo inválido')
    proc = subprocess.Popen(gpg_command(home, password) + ['--decrypt', str(source)],
                            stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    try:
        hashes, modes, manifest, seen = {}, {}, None, set()
        # r|* acepta TAR antiguo sin comprimir y nuevo gzip. Nunca crea archive.tar.
        with tarfile.open(fileobj=proc.stdout, mode='r|*') as tar:
            for entry in tar:
                path = Path(entry.name)
                if (path.is_absolute() or '..' in path.parts or not path.parts
                        or not (entry.isfile() or entry.isdir()) or str(path) in seen):
                    raise ValueError('Miembro de respaldo no permitido')
                name = str(path)
                seen.add(name)
                modes[name] = entry.mode & 0o777 & ~0o022
                if entry.isdir():
                    if root is not None:
                        (root / path).mkdir(parents=True, exist_ok=True, mode=0o700)
                    continue
                stream = tar.extractfile(entry)
                if name == 'backup-manifest.json':
                    if entry.size > 128 * 2**20:
                        raise ValueError('Manifiesto demasiado grande')
                    raw = stream.read()
                    manifest = json.loads(raw)
                    if root is not None:
                        (root / path).write_bytes(raw)
                else:
                    reader = HashReader(stream)
                    if root is None:
                        while reader.read(1024 * 1024):
                            pass
                    else:
                        (root / path).parent.mkdir(parents=True, exist_ok=True, mode=0o700)
                        with (root / path).open('xb') as out:
                            shutil.copyfileobj(reader, out, 1024 * 1024)
                    hashes[name] = reader.hash.hexdigest()
        # TAR puede terminar antes de EOF; consumir TODO para comprobar MDC/GPG
        # y evitar un proceso bloqueado en stdout, incluso con cola tras el TAR.
        while proc.stdout.read(1024 * 1024):
            pass
        if proc.wait():
            raise ValueError('Cifrado/integridad inválidos')
        if not isinstance(manifest, dict) or manifest.get('version') not in {1, 2}:
            raise ValueError('Manifiesto de respaldo inválido')
        if manifest['version'] == 1:
            # Compatibilidad exacta: inventory() histórico omitía también los
            # archivos anidados con este basename. V2 cubre todos salvo el raíz.
            hashes = {n: h for n, h in hashes.items() if Path(n).name != 'backup-manifest.json'}
        if hashes != manifest.get('files'):
            raise ValueError('Contenido restaurado no coincide')
        if root is not None:
            # Aplicar modos después de escribir todo, preservando ejecución sin bits especiales.
            for name in sorted(modes, key=lambda n: n.count('/'), reverse=True):
                (root / name).chmod(modes[name])
    finally:
        proc.stdout.close()
        if proc.poll() is None:
            proc.kill()
        proc.wait()


def verify(a):
    source, password = private(a.archive), key(a.key_file)
    with tempfile.TemporaryDirectory(prefix='ddn-verify-', dir=source.parent) as home:
        read_archive(source, password, home)
    print('Cifrado completo, SHA externo y manifiesto por archivo verificados')


def restore(a):
    target, source, password = private(a.destination), private(a.archive), key(a.key_file)
    if target.exists(): raise ValueError('Restauración requiere directorio NUEVO')
    with tempfile.TemporaryDirectory(prefix='ddn-restore-', dir=target.parent) as folder:
        gpg = Path(folder) / 'gpg'; gpg.mkdir(mode=0o700)
        root = Path(folder) / 'payload'; root.mkdir(mode=0o700)
        read_archive(source, password, gpg, root)
        # Preserva propietarios del runtime; no se usan uid/gid arbitrarios del TAR.
        if os.geteuid() == 0:
            for name in ['config','secrets','cu','keys','pki','store','clamav','pisee','acceso-temporal']:
                for p in [root / name, *(root / name).rglob('*')]:
                    if p.exists(): os.chown(p, 1000, 1000)
            for name in ['pki/mysql','mysql','mysql-init','pki/authority','pki/origin-authority']:
                uid = 0 if name.endswith('authority') else 999
                for p in [root / name, *(root / name).rglob('*')]:
                    if p.exists(): os.chown(p, uid, uid)
            for name in ['secrets/mysql-root-password','secrets/acceso-temporal-password','compose.env']:
                if (root / name).exists(): os.chown(root / name, 0, 0)
        shutil.move(root, target)
    print('Restauración descifrada e íntegra en destino nuevo; NO se inició aplicación ni se importó SQL')

def main():
    os.umask(0o077)
    p = argparse.ArgumentParser(description=__doc__); sub = p.add_subparsers(dest='command', required=True)
    for command in ['backup', 'estimate']:
        b = sub.add_parser(command)
        for name in ['private-dir', 'project', 'output']:
            b.add_argument('--' + name, required=True)
        if command == 'backup':
            b.add_argument('--key-file', required=True)
        for name in ['pisee-private-dir', 'pisee-node-config', 'pisee-state-dir']:
            b.add_argument('--' + name)
        b.add_argument('--capacity-factor', type=float, default=1.25)
        b.add_argument('--reserve-bytes', type=int, help='Por defecto max(10 GiB, 10%% del filesystem)')
        b.add_argument('--certification-readonly', action='store_true',
                       help='Solo origen CU cerrado; no detiene escritores ni cambia servicios')
        b.add_argument('--maintenance', action='store_true')
    for command in ['restore', 'verify']:
        r = sub.add_parser(command)
        for name in ['archive', 'key-file']:
            r.add_argument('--' + name, required=True)
        if command == 'restore':
            r.add_argument('--destination', required=True)
    a = p.parse_args()
    try:
        {'backup': backup, 'estimate': estimate, 'restore': restore, 'verify': verify}[a.command](a)
    except (OSError, ValueError, RuntimeError, KeyError, StopIteration, tarfile.TarError):
        raise SystemExit('Operación rechazada; origen preservado; revisar entrada y estado sin imprimir secretos')

if __name__ == '__main__': main()
