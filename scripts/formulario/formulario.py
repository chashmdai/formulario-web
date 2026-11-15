#!/usr/bin/env python3
"""Operación única del Formulario: instalación, respaldo, restauración, migración, transición,
actualización y reversión, con un solo layout y un solo Compose (infra/formulario/compose.yml).

Layout de la instalación (RAIZ, p. ej. /srv/formulario):
  instalacion.json formulario.env bin/ infra/          generados por esta herramienta
  config/ secrets/ keys/ pki/ cu/ mysql/ store/        material preservado (identidad, firmas, custodia)
  acceso-temporal/ pisee/{runtime,state,config.json}   contraseña temporal y nodo PISEE
  clamav/ ipc/ registro/                                firmas AV, sockets efímeros, registros de origen
  volumen Docker <proyecto>_mysql-data                  base MySQL (se puebla desde el volcado)

Migración desde la instalación anterior: no usa sus scripts ni sus overlays. Inventaría los
montajes REALES de sus contenedores (`docker inspect`), los clasifica (preservar / release /
efímero) y BLOQUEA ante cualquier montaje no clasificado. El respaldo de la instalación anterior
se escribe ya con el layout nuevo, de modo que migrar = restaurar un respaldo verificado.

Invariantes: nunca dos nodos PISEE activos con la misma identidad; nunca se borra la instalación
anterior (sus contenedores solo se detienen y su unidad systemd solo se deshabilita); nunca se
revierte si la instalación nueva tiene datos posteriores sin respaldar. No imprime secretos.
"""
import argparse
import datetime
import gzip
import hashlib
import io
import ipaddress
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import socket
import stat
import subprocess
import sys
import tarfile
import tempfile
import time

ESQUEMA = 'DDN_FORMULARIO_RESPALDO_V1'
PAQUETE = Path(__file__).resolve().parents[2]
INFRA = {'compose.yml': 'infra/formulario/compose.yml', 'Caddyfile': 'infra/ubuntu/Caddyfile.production',
         'mysql.cnf': 'infra/ubuntu/mysql.cnf', 'clamd.conf': 'infra/ubuntu/clamd.conf',
         'formulario.service': 'infra/formulario/formulario.service'}
PISEE_PEM = 'pisee/runtime/certsX509-institucional/public.pem'
GIB = 2 ** 30
ESCRITORES = ['proxy', 'app', 'pisee']
ORDEN_ARRANQUE = ['mysql', 'antivirus', 'inspector', 'pisee', 'app', 'proxy']
# Base copiada desde la raíz anterior (ruta nueva, ruta anterior). Los montajes efectivos la sobrescriben.
BASE_LEGADO = [
    ('config', 'config'), ('secrets', 'secrets'), ('keys', 'keys'), ('pki', 'pki'), ('cu', 'cu'),
    ('store', 'store'), ('acceso-temporal', 'acceso-temporal'), ('mysql/init', 'mysql-init'),
    ('mysql/root.cnf', 'mysql/root.cnf'), ('mysql/health.cnf', 'mysql/health.cnf'),
    ('pisee/runtime', 'pisee/runtime'), ('pisee/state', 'pisee/state'),
    ('pisee/config.json', 'pisee/config-produccion.json'), ('clamav', 'clamav'),
    ('registro/legado/installation.json', 'installation.json'), ('registro/legado/compose.env', 'compose.env'),
    ('registro/legado/compose.origin-ca.yml', 'compose.origin-ca.yml'),
    ('registro/legado/authorization', 'authorization'), ('registro/legado/transition', 'transition'),
    ('registro/legado/intercambio-publico', 'intercambio-publico'),
    ('registro/legado/delivery-private', 'delivery-private'),
]
# (servicio, destino en el contenedor) -> ruta nueva | 'release' | 'efimero' | 'base'
MONTAJES = {
    ('mysql', '/var/lib/mysql'): 'base', ('mysql', '/docker-entrypoint-initdb.d'): 'mysql/init',
    ('mysql', '/run/pki'): 'pki/mysql', ('mysql', '/run/secrets/root.cnf'): 'mysql/root.cnf',
    ('mysql', '/run/secrets/health.cnf'): 'mysql/health.cnf',
    ('mysql', '/run/secrets/mysql-root-password'): 'secrets/mysql-root-password',
    ('mysql', '/etc/mysql/conf.d/formulario.cnf'): 'release',
    ('app', '/run/cu'): 'cu', ('app', '/run/config/application.properties'): 'config/application.properties',
    ('app', '/run/keys'): 'keys', ('app', '/run/pki'): 'pki/app', ('app', '/var/lib/formulario'): 'store',
    ('app', '/var/lib/acceso-temporal'): 'acceso-temporal', ('app', '/run/pisee/public.pem'): PISEE_PEM,
    ('app', '/ipc/inspector'): 'efimero', ('app', '/ipc/antivirus'): 'efimero',
    ('app', '/run/config/clamd.conf'): 'release',
    ('app', '/run/secrets/publico.db.password'): 'secrets/app-password',
    ('app', '/run/secrets/server.ssl.key-store-password'): 'secrets/keystore-password',
    ('app', '/run/secrets/operacion.token'): 'secrets/operations-token',
    ('app', '/run/secrets/publico.integracion.token'): 'secrets/integration-token',
    ('proxy', '/etc/caddy/Caddyfile'): 'release', ('proxy', '/run/pki'): 'pki/proxy',
    ('inspector', '/ipc'): 'efimero', ('antivirus', '/ipc'): 'efimero', ('antivirus', '/var/lib/clamav'): 'clamav',
    ('antivirus', '/etc/clamd-media.conf'): 'release',
    ('pisee', '/opt/pisee'): 'pisee/runtime', ('pisee', '/var/lib/pisee/config.json'): 'pisee/config.json',
    ('pisee', '/var/lib/pisee/assets'): 'pisee/runtime/assets',
    ('pisee', '/var/lib/pisee/conversiones'): 'pisee/runtime/conversiones', ('pisee', '/var/lib/pisee'): 'pisee/state',
}
# Cambian legítimamente durante la operación: no se exige igualdad tras restaurar/arrancar.
MUTABLES = ('store/', 'acceso-temporal/', 'pisee/state/', 'pisee/config.json', 'registro/', 'clamav/')
# Recepción, integración, PISEE y retiradas cerrados durante la validación (variables > properties).
CERRADO = {
    'PUBLICO_RECEPCION_HABILITADA': 'false', 'FORMULARIO_RECEPCION_HABILITADA': 'false',
    'PUBLICO_INTEGRACION_HABILITADA': 'false', 'PUBLICO_NNA_HABILITADO': 'false', 'PISEE_SRCEI_ENABLED': 'false',
    'CICLOVIDA_ONLINEHABILITADO': 'false', 'CICLOVIDA_ELIMINACION_HABILITADA': 'false',
    'FORMULARIO_MINIMIZACIONBORRADORES_HABILITADA': 'false',
}
SOLO_LAB = 'SOLO LAB: destino de montaje de stubs que no se migra (nunca en producción)'
DATOS_CIUDADANOS = ['citizen_submission', 'public_delivery', 'citizen_journey']


class Rechazo(Exception):
    """Operación rechazada sin cambios adicionales; el mensaje nunca contiene secretos."""


# ----------------------------------------------------------------------------- utilidades
def run(args, *, input=None, check=True, stdout=subprocess.PIPE, stdin=None):
    result = subprocess.run(args, input=input, stdin=stdin, stdout=stdout, stderr=subprocess.PIPE)
    if check and result.returncode:
        detail = result.stderr.decode(errors='replace').strip().splitlines()[-1:] if result.stderr else []
        raise Rechazo(f'{Path(args[0]).name} {" ".join(args[1:3])} falló: {" ".join(detail)[:300]}')
    return result.stdout.decode(errors='replace') if isinstance(result.stdout, bytes) else ''


def sha256(path):
    with Path(path).open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()


def privado(path, nuevo=False):
    p = Path(path).absolute()
    if any(q.is_symlink() or (q / '.git').exists() for q in [p, *p.parents]):
        raise Rechazo(f'{p}: ruta privada fuera de Git y sin enlaces requerida')
    if nuevo and p.exists():
        raise Rechazo(f'{p}: debe ser un destino NUEVO')
    return p


def clave(path):
    p = privado(path)
    if not p.is_file() or p.stat().st_mode & 0o077 or p.stat().st_size < 32:
        raise Rechazo('Clave de respaldo externa 0600/0400 de al menos 32 bytes requerida')
    return p


def requiere_root():
    if os.geteuid() != 0:
        raise Rechazo('Ejecutar como root: preserva propietarios 0/999/1000 y lee material 0400')


def leer_env(path):
    values = {}
    for line in Path(path).read_text().splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            k, v = line.split('=', 1)
            values[k.strip()] = v.strip()
    return values


def escribir(path, data, mode=0o600, uid=None):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name('.' + path.name + '.tmp')
    tmp.write_bytes(data if isinstance(data, bytes) else data.encode())
    tmp.chmod(mode)
    if uid is not None and os.geteuid() == 0:
        os.chown(tmp, uid, uid)
    os.replace(tmp, path)


def docker_inspect(ids):
    return json.loads(run(['docker', 'inspect', *ids])) if ids else []


def contenedores(proyecto, activos=False):
    args = ['docker', 'ps', '-q', '--filter', 'label=com.docker.compose.project=' + proyecto]
    if not activos:
        args.insert(2, '-a')
    return docker_inspect(run(args).split())


def servicio(c):
    return c['Config']['Labels'].get('com.docker.compose.service')


def esperar_salud(ids, timeout=420):
    fin = time.monotonic() + timeout
    pendientes = set(ids)
    while pendientes and time.monotonic() < fin:
        for c in docker_inspect(list(pendientes)):
            state = c['State']
            salud = (state.get('Health') or {}).get('Status')
            if state['Running'] and salud in (None, 'healthy'):
                pendientes.discard(c['Id'])
            elif not state['Running'] or salud == 'unhealthy':
                raise Rechazo(f'{servicio(c)} no quedó saludable')
        if pendientes:
            time.sleep(3)
    if pendientes:
        raise Rechazo('Tiempo de espera de salud agotado')


# ----------------------------------------------------------------------------- inventario anterior
def clasificar(svc, destino):
    if (svc, destino) in MONTAJES:
        return MONTAJES[(svc, destino)]
    # Archivo montado DENTRO de un directorio clasificado (p. ej. overlay de CA): ruta anidada.
    candidatos = [(d, r) for (s, d), r in MONTAJES.items() if s == svc and destino.startswith(d.rstrip('/') + '/')
                  and r not in ('release', 'efimero', 'base')]
    if candidatos:
        d, r = max(candidatos, key=lambda x: len(x[0]))
        return r + destino[len(d):]
    return None


def inventario_legado(raiz, ignorar=()):
    raiz = privado(raiz)
    meta = json.loads((raiz / 'installation.json').read_text())
    proyecto = meta['project']
    env = leer_env(raiz / 'compose.env')
    servicios = {}
    for c in contenedores(proyecto):
        servicios[servicio(c)] = c
    for requerido in ['mysql', 'app', 'proxy']:
        if requerido not in servicios:
            raise Rechazo(f'Instalación anterior sin contenedor {requerido}')
    entradas, montajes, bloqueos = [], [], []
    for nuevo, anterior in BASE_LEGADO:
        if (raiz / anterior).exists():
            entradas.append((nuevo, raiz / anterior, 'base'))
    for svc, c in sorted(servicios.items()):
        for m in c.get('Mounts', []):
            destino, tipo = m['Destination'], m['Type']
            clase = 'ignorado-lab' if destino in ignorar else clasificar(svc, destino)
            montajes.append({'servicio': svc, 'destino': destino, 'tipo': tipo, 'clase': clase})
            if tipo == 'volume':
                if (svc, destino) != ('mysql', '/var/lib/mysql'):
                    bloqueos.append(f'Volumen no clasificado {svc}:{destino}')
                continue
            if clase is None:
                bloqueos.append(f'Montaje no clasificado {svc}:{destino}')
            elif clase not in ('release', 'efimero', 'base', 'ignorado-lab'):
                entradas.append((clase, Path(m['Source']), f'{svc}:{destino}'))
    no_migrado = sorted(p.name for p in raiz.iterdir()
                        if not any(p.name == a.split('/')[0] for _, a in BASE_LEGADO))
    return {'raiz': raiz, 'proyecto': proyecto, 'env': env, 'meta': meta, 'servicios': servicios,
            'entradas': entradas, 'montajes': montajes, 'bloqueos': bloqueos, 'noMigrado': no_migrado,
            'imagenes': {svc: c['Image'] for svc, c in servicios.items()}}


def expandir(entradas):
    """rel -> origen (archivos) y rel -> origen (directorios); una entrada de directorio posterior
    REEMPLAZA lo recogido bajo su prefijo: resultado = vista efectiva de los contenedores."""
    archivos, directorios = {}, {}
    for rel, origen, _ in entradas:
        origen = Path(origen)
        if origen.is_symlink():
            raise Rechazo(f'Enlace simbólico en material preservado: {rel}')
        if origen.is_dir():
            prefijo = rel.rstrip('/') + '/'
            for k in [k for k in archivos if k.startswith(prefijo)]:
                del archivos[k]
            directorios[rel] = origen
            for hijo in sorted(origen.rglob('*')):
                destino = rel + '/' + str(hijo.relative_to(origen))
                if hijo.is_symlink():
                    raise Rechazo(f'Enlace simbólico en material preservado: {destino}')
                modo = hijo.lstat().st_mode
                if stat.S_ISSOCK(modo) or stat.S_ISFIFO(modo):
                    continue  # IPC efímero: se recrea al arrancar
                if hijo.is_dir():
                    directorios[destino] = hijo
                elif hijo.is_file():
                    archivos[destino] = hijo
        elif origen.is_file():
            archivos[rel] = origen
    for rel in list(archivos):
        partes = PurePosixPath(rel).parents
        for padre in partes:
            if str(padre) != '.' and str(padre) not in directorios:
                directorios[str(padre)] = None
    return archivos, directorios


def tamano(archivos):
    return sum(Path(p).stat().st_size for p in archivos.values())


def base_datos(mysql_id):
    def sql(q):
        return run(['docker', 'exec', mysql_id, 'mysql', '--defaults-extra-file=/run/secrets/root.cnf', '--batch',
                    '--skip-column-names', 'formulario_publico', '-e', q]).strip()
    tablas = sql("SELECT table_name FROM information_schema.tables WHERE table_schema='formulario_publico' "
                 "AND table_type='BASE TABLE' ORDER BY table_name").split()
    conteos = {t: int(sql(f'SELECT COUNT(*) FROM `{t}`')) for t in tablas}
    version = sql('SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1')
    bytes_db = int(sql("SELECT COALESCE(SUM(data_length+index_length),0) FROM information_schema.tables "
                       "WHERE table_schema='formulario_publico'") or 0)
    return {'conteos': conteos, 'flyway': version, 'bytes': bytes_db}


def plan(a):
    inv = inventario_legado(a.legado, a.ignorar_montaje)
    archivos, _ = expandir(inv['entradas'])
    db = base_datos(inv['servicios']['mysql']['Id'])
    datos = tamano(archivos)
    # Disco del servidor: árbol nuevo + volumen MySQL (~3x datos) + imagen nueva + respaldo local si aplica.
    requerido = datos + 3 * db['bytes'] + int(0.8 * GIB) + (datos + db['bytes'] if a.respaldo_local else 0) + 2 * GIB
    libre = shutil.disk_usage(Path(a.destino).absolute().parent if a.destino else inv['raiz']).free
    limite = int(a.limite_gib * GIB)
    bloqueos = list(inv['bloqueos'])
    if requerido > min(libre, limite):
        bloqueos.append('Espacio insuficiente dentro del límite configurado')
    identidad = inv['raiz'] / 'pisee/runtime/certsX509-institucional/public.pem'
    return {'proyectoAnterior': inv['proyecto'], 'montajes': inv['montajes'], 'bloqueos': bloqueos,
            'noMigradoPermaneceEnOrigen': inv['noMigrado'], 'archivosPreservados': len(archivos),
            'bytesPreservados': datos, 'baseDatos': db, 'bytesRequeridos': requerido, 'bytesLibres': libre,
            'limiteBytes': limite, 'imagenes': inv['imagenes'],
            'piseeIdentidad': sha256(identidad) if identidad.is_file() else None}


# ----------------------------------------------------------------------------- formato de respaldo
class Lector:
    def __init__(self, f):
        self.f, self.h = f, hashlib.sha256()

    def read(self, n=-1):
        data = self.f.read(n)
        self.h.update(data)
        return data


def gpg(home, passfile, *args):
    return ['gpg', '--no-options', '--homedir', str(home), '--batch', '--yes', '--pinentry-mode', 'loopback',
            '--passphrase-file', str(passfile), *args]


def escribir_respaldo(salida, passfile, archivos, directorios, extra, meta):
    """TAR -> gzip -> GPG AES256 directo a `salida`; nada en claro salvo `extra` (volcado SQL 0600)."""
    salida = privado(salida, nuevo=True)
    with tempfile.TemporaryDirectory(prefix='.formulario-gpg-', dir=salida.parent) as home:
        os.chmod(home, 0o700)
        proc = subprocess.Popen(gpg(home, passfile, '--symmetric', '--cipher-algo', 'AES256', '--compress-algo', 'none',
                                    '--output', str(salida)), stdin=subprocess.PIPE, stderr=subprocess.DEVNULL)
        manifiesto = {'schema': ESQUEMA, **meta, 'archivos': {}, 'directorios': {}}
        try:
            with gzip.GzipFile(fileobj=proc.stdin, mode='wb', compresslevel=6, mtime=0) as gz, \
                    tarfile.open(fileobj=gz, mode='w|', format=tarfile.PAX_FORMAT) as tar:
                for rel in sorted(directorios):
                    origen = directorios[rel]
                    st = origen.stat() if origen else None
                    info = tarfile.TarInfo('payload/' + rel)
                    info.type, info.mode = tarfile.DIRTYPE, stat.S_IMODE(st.st_mode) if st else 0o700
                    info.uid = info.gid = 0
                    manifiesto['directorios'][rel] = {'mode': info.mode, 'uid': st.st_uid if st else 0,
                                                      'gid': st.st_gid if st else 0}
                    tar.addfile(info)
                for rel, origen in sorted({**archivos, **extra}.items()):
                    st = Path(origen).stat()
                    info = tarfile.TarInfo(('payload/' + rel) if rel in archivos else rel)
                    info.size, info.mode, info.uid, info.gid, info.mtime = st.st_size, 0o600, 0, 0, int(st.st_mtime)
                    with Path(origen).open('rb') as f:
                        lector = Lector(f)
                        tar.addfile(info, lector)
                        if f.read(1):
                            raise Rechazo(f'{rel} cambió durante el respaldo; detener escritores')
                    manifiesto['archivos'][rel] = {'sha256': lector.h.hexdigest(), 'size': st.st_size,
                                                   'mode': stat.S_IMODE(st.st_mode), 'uid': st.st_uid, 'gid': st.st_gid}
                data = json.dumps(manifiesto, sort_keys=True, indent=1).encode()
                info = tarfile.TarInfo('manifest.json')
                info.size, info.mode = len(data), 0o600
                tar.addfile(info, io.BytesIO(data))
            proc.stdin.close()
            if proc.wait():
                raise Rechazo('Cifrado GPG rechazado')
        except BaseException:
            if proc.poll() is None:
                proc.kill()
            if salida.exists():
                salida.unlink()
            raise
    salida.chmod(0o600)
    escribir(salida.with_name(salida.name + '.sha256'), sha256(salida) + '\n', 0o600)
    return manifiesto


def leer_respaldo(archivo, passfile, destino=None):
    """Descifra hasta EOF, comprueba SHA externo, integridad GPG y cada archivo contra el manifiesto.
    Con `destino`, extrae (0600/0700) sin seguir rutas fuera de él."""
    archivo = privado(archivo)
    lateral = archivo.with_name(archivo.name + '.sha256')
    if not lateral.is_file() or lateral.read_text().strip() != sha256(archivo):
        raise Rechazo('SHA-256 del respaldo no coincide con su archivo .sha256')
    with tempfile.TemporaryDirectory(prefix='.formulario-gpg-', dir=(destino or archivo).parent) as home:
        os.chmod(home, 0o700)
        proc = subprocess.Popen(gpg(home, passfile, '--decrypt', str(archivo)), stdout=subprocess.PIPE,
                                stderr=subprocess.DEVNULL)
        vistos, directorios, manifiesto = {}, set(), None
        try:
            with tarfile.open(fileobj=proc.stdout, mode='r|gz') as tar:
                for m in tar:
                    nombre = PurePosixPath(m.name)
                    if nombre.is_absolute() or '..' in nombre.parts or not (m.isfile() or m.isdir()):
                        raise Rechazo('Miembro de respaldo no permitido')
                    if m.name == 'manifest.json':
                        manifiesto = json.loads(tar.extractfile(m).read())
                        continue
                    rel = str(PurePosixPath(*nombre.parts[1:])) if nombre.parts[0] == 'payload' else m.name
                    if m.isdir():
                        directorios.add(rel)
                        if destino:
                            (destino / 'payload' / rel).mkdir(mode=0o700, parents=True, exist_ok=True)
                        continue
                    origen = tar.extractfile(m)
                    h = hashlib.sha256()
                    salida = None
                    if destino:
                        ruta = destino / (('payload/' + rel) if nombre.parts[0] == 'payload' else rel)
                        ruta.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
                        salida = os.fdopen(os.open(ruta, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'wb')
                    try:
                        while bloque := origen.read(1 << 20):
                            h.update(bloque)
                            if salida:
                                salida.write(bloque)
                    finally:
                        if salida:
                            salida.close()
                    vistos[rel] = h.hexdigest()
            proc.stdout.close()
            if proc.wait():
                raise Rechazo('Descifrado o integridad GPG fallidos')
        except (tarfile.TarError, OSError, EOFError, json.JSONDecodeError) as error:
            raise Rechazo(f'Respaldo ilegible: {type(error).__name__}') from error
        finally:
            if proc.poll() is None:
                proc.kill()
    if not manifiesto or manifiesto.get('schema') != ESQUEMA:
        raise Rechazo('Manifiesto ausente o de otro formato')
    esperados = {k: v['sha256'] for k, v in manifiesto['archivos'].items()}
    if vistos != esperados or directorios != set(manifiesto['directorios']):
        raise Rechazo('Contenido del respaldo no coincide con su manifiesto')
    return manifiesto


# ----------------------------------------------------------------------------- respaldo
def volcar(mysql_id, carpeta):
    destino = Path(carpeta) / 'database.sql'
    with destino.open('xb') as f:
        os.chmod(destino, 0o600)
        run(['docker', 'exec', mysql_id, 'mysqldump', '--defaults-extra-file=/run/secrets/root.cnf',
             '--single-transaction', '--quick', '--hex-blob', '--routines', '--triggers', '--events',
             '--no-tablespaces', '--set-gtid-purged=OFF', '--default-character-set=utf8mb4', 'formulario_publico'],
            stdout=f)
    return destino


def detener(ids):
    if ids:
        run(['docker', 'stop', '--time', '40', *ids])


def respaldar_legado(legado, salida, passfile, mantenimiento, reanudar=True, ignorar=()):
    inv = inventario_legado(legado, ignorar)
    if inv['bloqueos']:
        raise Rechazo('Inventario con bloqueos: ' + '; '.join(inv['bloqueos']))
    activos = [inv['servicios'][s]['Id'] for s in ESCRITORES if s in inv['servicios']
               and inv['servicios'][s]['State']['Running']]
    if activos and not mantenimiento:
        raise Rechazo('Respaldo consistente requiere --mantenimiento (detiene proxy, app y PISEE)')
    mysql = inv['servicios']['mysql']['Id']
    salida = privado(salida, nuevo=True)
    detenidos = []
    try:
        detener(activos)
        detenidos = activos
        archivos, directorios = expandir(inv['entradas'])  # releer tras detener escritores
        with tempfile.TemporaryDirectory(prefix='.formulario-sql-', dir=salida.parent) as carpeta:
            os.chmod(carpeta, 0o700)
            dump = volcar(mysql, carpeta)
            db = base_datos(mysql)
            pem = archivos.get(PISEE_PEM)
            meta = {'origen': 'legado', 'proyectoOrigen': inv['proyecto'], 'creado': ahora(),
                    'hostname': inv['env'].get('FORM_HOSTNAME'), 'puerto': inv['env'].get('FORM_HTTPS_PORT'),
                    'saludPath': inv['env'].get('FORM_HEALTH_PATH', '/acceso-temporal/salud'), 'imagenes': inv['imagenes'], 'baseDatos': db, 'montajes': inv['montajes'],
                    'piseeIdentidad': sha256(pem) if pem else None}
            manifiesto = escribir_respaldo(salida, passfile, archivos, directorios, {'database.sql': dump}, meta)
    finally:
        if reanudar and detenidos:
            arrancar_ids(list(reversed(detenidos)))
    leer_respaldo(salida, passfile)  # verificación completa antes de declarar éxito
    return resumen_respaldo(salida, manifiesto)


def resumen_respaldo(salida, manifiesto):
    return {'archivo': str(salida), 'sha256': sha256(salida), 'archivos': len(manifiesto['archivos']),
            'baseDatos': {'flyway': manifiesto['baseDatos']['flyway'],
                          'conteos': {t: manifiesto['baseDatos']['conteos'].get(t) for t in DATOS_CIUDADANOS}},
            'piseeIdentidad': manifiesto.get('piseeIdentidad'), 'verificado': True}


def ahora():
    return datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')


def arrancar_ids(ids):
    if ids:
        run(['docker', 'start', *ids])
        esperar_salud(ids)


# ----------------------------------------------------------------------------- instalación nueva
class Instalacion:
    def __init__(self, raiz):
        self.raiz = privado(raiz)
        self.datos = json.loads((self.raiz / 'instalacion.json').read_text())
        self.env = leer_env(self.raiz / 'formulario.env')
        self.proyecto = self.env['COMPOSE_PROJECT_NAME']

    def compose(self, *args, validacion=False, check=True):
        archivos = [self.raiz / 'infra/compose.yml', *[Path(p) for p in self.datos.get('overlays', [])]]
        if validacion:
            archivos.append(self.raiz / 'infra/validacion.yml')
        cmd = ['docker', 'compose', '--project-name', self.proyecto, '--env-file', str(self.raiz / 'formulario.env')]
        for f in archivos:
            cmd += ['-f', str(f)]
        return run([*cmd, *args], check=check)

    def contenedor(self, svc):
        for c in contenedores(self.proyecto):
            if servicio(c) == svc:
                return c
        return None

    def sql(self, q):
        c = self.contenedor('mysql')
        return run(['docker', 'exec', c['Id'], 'mysql', '--defaults-extra-file=/run/secrets/root.cnf', '--batch',
                    '--skip-column-names', 'formulario_publico', '-e', q]).strip()

    def guardar(self):
        escribir(self.raiz / 'instalacion.json', json.dumps(self.datos, indent=2, sort_keys=True) + '\n', 0o600)


def red_libre(subred, propias=()):
    red = ipaddress.ip_network(subred)
    if not red.is_private:
        raise Rechazo(f'{subred}: subred privada requerida')
    for nid in run(['docker', 'network', 'ls', '-q']).split():
        info = docker_inspect([nid])[0] if nid else {}
        if info.get('Name') in propias:
            continue
        for cfg in (info.get('IPAM') or {}).get('Config') or []:
            if cfg.get('Subnet') and red.overlaps(ipaddress.ip_network(cfg['Subnet'], strict=False)):
                raise Rechazo(f'{subred} se superpone con la red Docker {info.get("Name")}')
    return red


def generar_env(raiz, manifiesto, a):
    salida = ipaddress.ip_network(a.subred_salida)
    pisee = ipaddress.ip_network(a.subred_pisee)
    hosts_pisee = list(pisee.hosts())
    imagenes = dict(manifiesto.get('imagenes', {}))
    for svc, valor in [('app', a.imagen_app), ('proxy', a.imagen_proxy), ('pisee', a.imagen_pisee),
                       ('mysql', a.imagen_mysql), ('antivirus', a.imagen_antivirus), ('inspector', a.imagen_inspector)]:
        if valor:
            imagenes[svc] = valor
    faltan = [s for s in ['app', 'proxy', 'pisee', 'mysql', 'antivirus', 'inspector'] if not imagenes.get(s)]
    if faltan:
        raise Rechazo('Imágenes sin definir: ' + ', '.join(faltan))
    valores = {
        'COMPOSE_PROJECT_NAME': a.proyecto, 'FORMULARIO_RAIZ': str(raiz),
        'FORM_HOSTNAME': a.hostname or manifiesto.get('hostname'), 'FORM_HTTPS_PORT': str(a.puerto or manifiesto.get('puerto')),
        'FORM_VALIDACION_PORT': str(a.puerto_validacion), 'FORM_IMAGE': imagenes['app'],
        # Con contraseña temporal activa, /api/session no responde 200 sin acceso: salud mínima propia.
        'FORM_HEALTH_PATH': manifiesto.get('saludPath') or '/acceso-temporal/salud',
        'PROXY_IMAGE': imagenes['proxy'], 'PISEE_IMAGE': imagenes['pisee'], 'MYSQL_IMAGE': imagenes['mysql'],
        'ANTIVIRUS_IMAGE': imagenes['antivirus'], 'INSPECTOR_IMAGE': imagenes['inspector'],
        'SALIDA_SUBNET': str(salida), 'FORM_TRUSTED_PROXIES': f'{next(salida.hosts())}/32',
        'PISEE_SUBNET': str(pisee), 'PISEE_NODE_IPV4': str(hosts_pisee[1]), 'PISEE_BACKEND_IPV4': str(hosts_pisee[2]),
        'PISEE_IDENTIDAD': manifiesto.get('piseeIdentidad') or 'sin-identidad',
    }
    if not valores['FORM_HOSTNAME'] or valores['FORM_HTTPS_PORT'] in ('None', ''):
        raise Rechazo('Hostname y puerto público requeridos')
    escribir(raiz / 'formulario.env', ''.join(f'{k}={v}\n' for k, v in valores.items()), 0o600)
    return valores


def generar_validacion(raiz):
    entorno = ''.join(f'      {k}: "{v}"\n' for k, v in CERRADO.items())
    escribir(raiz / 'infra/validacion.yml',
             '# Validación: puerto alternativo, sin nodo PISEE, recepción/integración/retiradas cerradas.\n'
             'services:\n'
             '  app:\n'
             '    depends_on: !reset\n'
             '      mysql: {condition: service_healthy}\n'
             '      antivirus: {condition: service_healthy}\n'
             '      inspector: {condition: service_healthy}\n'
             '    environment:\n' + entorno +
             '  proxy:\n'
             '    ports: !override ["127.0.0.1:${FORM_VALIDACION_PORT}:8443"]\n', 0o644)


def instalar_infra(raiz):
    if not (PAQUETE / INFRA['compose.yml']).is_file():
        raise Rechazo('Ejecutar desde el paquete o repositorio (falta infra/formulario/compose.yml)')
    for nombre, origen in INFRA.items():
        escribir(raiz / 'infra' / nombre, (PAQUETE / origen).read_bytes(), 0o644)
    escribir(raiz / 'bin/formulario.py', Path(__file__).read_bytes(), 0o700)
    generar_validacion(raiz)


def ajustar_pisee(raiz, backend_ip):
    """Solo la IP permitida del backend cambia (red nueva); credenciales e identidad intactas."""
    config = raiz / 'pisee/config.json'
    if not config.is_file():
        return False
    data = json.loads(config.read_text())
    seguridad = data.get('servidores', {}).get('interno', {}).get('seguridad')
    if not isinstance(seguridad, dict) or 'ipPermitidas' not in seguridad:
        raise Rechazo('Config PISEE sin servidores.interno.seguridad.ipPermitidas')
    seguridad['ipPermitidas'] = ['127.0.0.1', backend_ip]
    st = config.stat()
    escribir(config, json.dumps(data, indent=2), stat.S_IMODE(st.st_mode))
    if os.geteuid() == 0:
        os.chown(config, st.st_uid, st.st_gid)
    return True


def restaurar(a):
    requiere_root()
    archivo, passfile = privado(a.archivo), clave(a.clave)
    raiz = privado(a.raiz, nuevo=True)
    red_libre(a.subred_salida)
    red_libre(a.subred_pisee)
    tmp = raiz.with_name('.' + raiz.name + '.restaurando')
    if tmp.exists():
        raise Rechazo(f'{tmp} existe: restauración previa interrumpida, revisar a mano')
    tmp.mkdir(mode=0o700)
    manifiesto = leer_respaldo(archivo, passfile, tmp)
    payload = tmp / 'payload'
    for rel, m in sorted(manifiesto['directorios'].items(), key=lambda x: -len(x[0])):
        p = payload / rel
        p.mkdir(parents=True, exist_ok=True)
        os.chown(p, m['uid'], m['gid'])
        p.chmod(m['mode'])
    for rel, m in manifiesto['archivos'].items():
        if rel == 'database.sql':
            continue
        p = payload / rel
        os.chown(p, m['uid'], m['gid'])
        p.chmod(m['mode'])
    sql = tmp / 'database.sql'
    os.replace(payload, raiz)
    raiz.chmod(0o700)
    for ipc in ['ipc/inspector', 'ipc/antivirus']:
        (raiz / ipc).mkdir(parents=True, exist_ok=True)
        os.chown(raiz / ipc, 1000, 1000)
        (raiz / ipc).chmod(0o700)
    os.chown(raiz / 'ipc', 1000, 1000)
    instalar_infra(raiz)
    valores = generar_env(raiz, manifiesto, a)
    ajustar_pisee(raiz, valores['PISEE_BACKEND_IPV4'])
    escribir(raiz / 'registro/origen-manifiesto.json', json.dumps(manifiesto, sort_keys=True), 0o600)
    inst = {'version': 1, 'creada': ahora(), 'desde': {'archivo': str(archivo), 'sha256': sha256(archivo),
            'origen': manifiesto.get('origen'), 'proyectoOrigen': manifiesto.get('proyectoOrigen')},
            'overlays': [str(Path(o).absolute()) for o in a.overlay], 'piseeIdentidad': manifiesto.get('piseeIdentidad'),
            'paqueteCommit': commit_paquete()}
    escribir(raiz / 'instalacion.json', json.dumps(inst, indent=2, sort_keys=True) + '\n', 0o600)
    n = Instalacion(raiz)
    n.compose('up', '-d', '--wait', '--wait-timeout', '300', 'mysql')
    with sql.open('rb') as f:
        run(['docker', 'exec', '-i', n.contenedor('mysql')['Id'], 'mysql', '--defaults-extra-file=/run/secrets/root.cnf',
             'formulario_publico'], stdin=f)
    importado = {t: int(n.sql(f'SELECT COUNT(*) FROM `{t}`')) for t in manifiesto['baseDatos']['conteos']}
    if importado != manifiesto['baseDatos']['conteos']:
        raise Rechazo('Conteos importados distintos del respaldo')
    n.compose('--profile', 'migracion', 'run', '--rm', 'migrar')
    shutil.rmtree(tmp)
    return {'raiz': str(raiz), 'proyecto': n.proyecto, 'flywayAntes': manifiesto['baseDatos']['flyway'],
            'flywayDespues': n.sql('SELECT version FROM flyway_schema_history WHERE success=1 '
                                   'ORDER BY installed_rank DESC LIMIT 1'),
            'conteosIguales': True, 'archivosRestaurados': len(manifiesto['archivos']) - 1}


def commit_paquete():
    manifest = PAQUETE.parent / 'manifiesto.json'
    if manifest.is_file():
        return json.loads(manifest.read_text()).get('commit')
    r = subprocess.run(['git', '-C', str(PAQUETE), 'rev-parse', 'HEAD'], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    return r.stdout.decode().strip() or None


def nodos_pisee_en_conflicto(identidad, proyecto):
    """Contenedores ACTIVOS de otros proyectos con la misma identidad PISEE (etiqueta o certificado)."""
    conflictos = []
    for c in docker_inspect(run(['docker', 'ps', '-q']).split()):
        if c['Config']['Labels'].get('com.docker.compose.project') == proyecto:
            continue
        if c['Config']['Labels'].get('cl.ddn.formulario.pisee-identidad') == identidad:
            conflictos.append(c['Name'])
            continue
        for m in c.get('Mounts', []):
            if m.get('Destination') == '/opt/pisee':
                pem = Path(m['Source']) / 'certsX509-institucional/public.pem'
                if pem.is_file() and sha256(pem) == identidad:
                    conflictos.append(c['Name'])
    return conflictos


def arrancar(a):
    n = Instalacion(a.raiz)
    if a.validacion:
        n.compose('up', '-d', '--wait', '--wait-timeout', '300', 'mysql', 'antivirus', 'inspector', 'app', 'proxy',
                  validacion=True)
        return {'modo': 'validacion', 'puerto': n.env['FORM_VALIDACION_PORT'], 'pisee': 'no iniciado'}
    conflictos = nodos_pisee_en_conflicto(n.env['PISEE_IDENTIDAD'], n.proyecto)
    if conflictos:
        raise Rechazo('Otro nodo PISEE activo con la misma identidad: ' + ', '.join(conflictos))
    n.compose('up', '-d', '--wait', '--wait-timeout', '300')
    return {'modo': 'produccion', 'puerto': n.env['FORM_HTTPS_PORT'], 'pisee': 'iniciado'}


def detener_instalacion(a):
    n = Instalacion(a.raiz)
    n.compose('stop', '--timeout', '40')
    return {'detenida': n.proyecto}


def verificar(a):
    n = Instalacion(a.raiz)
    manifiesto = json.loads((n.raiz / 'registro/origen-manifiesto.json').read_text())
    problemas = []
    for rel, m in manifiesto['archivos'].items():
        if rel == 'database.sql' or rel.startswith(MUTABLES):
            continue
        p = n.raiz / rel
        if not p.is_file():
            problemas.append('Falta ' + rel)
        elif sha256(p) != m['sha256'] or p.stat().st_uid != m['uid'] or stat.S_IMODE(p.stat().st_mode) != m['mode']:
            problemas.append('Distinto ' + rel)
    for rel, m in manifiesto['archivos'].items():
        if rel.startswith('store/') and not (n.raiz / rel).is_file():
            problemas.append('Custodia incompleta')
            break
    conteos = {t: int(n.sql(f'SELECT COUNT(*) FROM `{t}`')) for t in DATOS_CIUDADANOS}
    for t, v in conteos.items():
        if v < manifiesto['baseDatos']['conteos'].get(t, 0):
            problemas.append(f'Menos filas en {t} que en el origen')
    fallidas = int(n.sql('SELECT COUNT(*) FROM flyway_schema_history WHERE success=0'))
    if fallidas:
        problemas.append('Migraciones fallidas')
    salud = {}
    for c in contenedores(n.proyecto, activos=True):
        salud[servicio(c)] = (c['State'].get('Health') or {}).get('Status', 'running')
    if any(v != 'healthy' for k, v in salud.items() if k != 'migrar'):
        problemas.append('Servicios no saludables')
    red = docker_inspect([n.proyecto + '_salida'])
    puerta = (red[0]['IPAM']['Config'] or [{}])[0].get('Gateway') if red else None
    if puerta and n.env['FORM_TRUSTED_PROXIES'] != f'{puerta}/32':
        problemas.append('FORM_TRUSTED_PROXIES no coincide con la puerta de enlace de salida')
    if 'pisee' in salud:
        if nodos_pisee_en_conflicto(n.env['PISEE_IDENTIDAD'], n.proyecto):
            problemas.append('Dos nodos PISEE activos con la misma identidad')
    return {'proyecto': n.proyecto, 'problemas': problemas, 'conteos': conteos, 'salud': salud,
            'puertaSalida': puerta, 'ok': not problemas}


# ----------------------------------------------------------------------------- transición y reversión
def systemctl(*args):
    if os.geteuid() != 0 or not shutil.which('systemctl'):
        return 'omitido'
    return 'ok' if subprocess.run(['systemctl', *args], stdout=subprocess.DEVNULL,
                                  stderr=subprocess.DEVNULL).returncode == 0 else 'fallo'


def unidad_nueva(n, accion):
    if accion == 'instalar':
        if os.geteuid() != 0 or not shutil.which('systemctl'):
            return 'omitido'
        texto = (n.raiz / 'infra/formulario.service').read_text().replace('/srv/formulario', str(n.raiz))
        escribir(Path('/etc/systemd/system/formulario.service'), texto, 0o644)
        systemctl('daemon-reload')
        return systemctl('enable', 'formulario.service')
    return systemctl('disable', 'formulario.service')


def legado_arriba(legado, unidad):
    inv = inventario_legado(legado)
    ids = [inv['servicios'][s]['Id'] for s in ORDEN_ARRANQUE if s in inv['servicios']]
    arrancar_ids(ids)
    return systemctl('enable', unidad)


def transicion(a):
    requiere_root()
    passfile = clave(a.clave)
    destino = Path(a.respaldos).absolute()
    archivo = destino / ('formulario-transicion-' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '.gpg')
    informe = {'pasos': []}
    pre = plan(argparse.Namespace(legado=a.legado, destino=a.raiz, limite_gib=a.limite_gib,
                                  respaldo_local=a.respaldo_local, ignorar_montaje=a.ignorar_montaje))
    if pre['bloqueos']:
        raise Rechazo('Plan con bloqueos: ' + '; '.join(pre['bloqueos']))
    inv = inventario_legado(a.legado, a.ignorar_montaje)
    restantes = [inv['servicios'][s]['Id'] for s in ['mysql', 'antivirus', 'inspector'] if s in inv['servicios']]
    legado_detenido = False
    try:
        # 1. Escritores del origen detenidos; respaldo final consistente y verificado (MySQL sigue activo).
        informe['respaldo'] = respaldar_legado(a.legado, archivo, passfile, mantenimiento=True, reanudar=False,
                                               ignorar=a.ignorar_montaje)
        informe['pasos'].append('respaldo-final-verificado')
        # 2. Origen completamente detenido (nunca borrado) y su unidad deshabilitada: un solo nodo PISEE.
        detener(restantes)
        legado_detenido = True
        informe['unidadAnterior'] = systemctl('disable', a.unidad_legado)
        informe['pasos'].append('origen-detenido')
        # 3. Restauración en la raíz nueva + validación cerrada en puerto alternativo.
        r = argparse.Namespace(**{**vars(a), 'archivo': str(archivo)})
        informe['restauracion'] = restaurar(r)
        arrancar(argparse.Namespace(raiz=a.raiz, validacion=True))
        v = verificar(argparse.Namespace(raiz=a.raiz))
        if not v['ok']:
            raise Rechazo('Validación cerrada fallida: ' + '; '.join(v['problemas']))
        informe['pasos'].append('validacion-cerrada')
        # 4. Producción: puerto público, nodo PISEE único y controles CAS heredados del origen.
        n = Instalacion(a.raiz)
        n.compose('stop', 'proxy', 'app', validacion=True)
        arrancar(argparse.Namespace(raiz=a.raiz, validacion=False))
        v = verificar(argparse.Namespace(raiz=a.raiz))
        if not v['ok']:
            raise Rechazo('Verificación productiva fallida: ' + '; '.join(v['problemas']))
        informe['unidadNueva'] = unidad_nueva(n, 'instalar')
        informe['pasos'].append('produccion')
        informe['verificacion'] = v
        return informe
    except Rechazo as error:
        informe['error'] = str(error)
        try:
            informe['reversion'] = revertir_automatico(a)
        except Rechazo as fallo:
            informe['reversion'] = {'error': str(fallo)}
        raise Rechazo(json.dumps(informe, default=str)) from error


def comunicaciones_nuevas(n):
    """Filas ciudadanas que la instalación nueva tiene por encima de su respaldo de origen."""
    manifiesto = json.loads((n.raiz / 'registro/origen-manifiesto.json').read_text())
    c = n.contenedor('mysql')
    if not c or not c['State']['Running']:
        return {}
    conteos = {t: int(n.sql(f'SELECT COUNT(*) FROM `{t}`')) for t in ['citizen_submission', 'public_delivery']}
    return {t: v - manifiesto['baseDatos']['conteos'].get(t, 0) for t, v in conteos.items()
            if v > manifiesto['baseDatos']['conteos'].get(t, 0)}


def revertir_automatico(a):
    """Detiene lo nuevo y reanuda el origen intacto, salvo que lo nuevo ya tenga datos propios."""
    resultado = {}
    if (Path(a.raiz) / 'registro/origen-manifiesto.json').is_file():
        n = Instalacion(a.raiz)
        nuevos = comunicaciones_nuevas(n)
        if nuevos:
            # Nunca perder comunicaciones: se deja la instalación nueva activa y se corrige hacia adelante.
            raise Rechazo('Reversión automática omitida: la instalación nueva ya tiene comunicaciones propias')
        n.compose('stop', '--timeout', '40', check=False)
        resultado['nueva'] = 'detenida'
    resultado['unidadAnterior'] = legado_arriba(a.legado, a.unidad_legado)
    resultado['origen'] = 'reanudado'
    return resultado


def revertir(a):
    requiere_root()
    n = Instalacion(a.raiz)
    if comunicaciones_nuevas(n) and not a.respaldo_nuevo:
        raise Rechazo('La instalación nueva tiene comunicaciones posteriores a la transición: respaldarla '
                      '(respaldar --raiz) y pasar --respaldo-nuevo; conciliar antes de reanudar el origen')
    if a.respaldo_nuevo:
        if not a.clave:
            raise Rechazo('--clave requerida para verificar --respaldo-nuevo')
        if leer_respaldo(a.respaldo_nuevo, clave(a.clave)).get('proyectoOrigen') != n.proyecto:
            raise Rechazo('--respaldo-nuevo no corresponde a esta instalación')
    n.compose('stop', '--timeout', '40')
    resultado = {'nueva': 'detenida', 'unidadNueva': unidad_nueva(n, 'deshabilitar')}
    resultado['unidadAnterior'] = legado_arriba(a.legado, a.unidad_legado)
    resultado['origen'] = 'reanudado'
    return resultado


# ----------------------------------------------------------------------------- respaldo / actualización nueva
def respaldar_nueva(raiz, salida, passfile, mantenimiento=True):
    n = Instalacion(raiz)
    activos = [c['Id'] for s in ESCRITORES if (c := n.contenedor(s)) and c['State']['Running']]
    if activos and not mantenimiento:
        raise Rechazo('Respaldo consistente requiere --mantenimiento')
    entradas = [(p.name, p, 'instalacion') for p in sorted(n.raiz.iterdir())
                if p.name not in ('bin', 'infra', 'ipc', 'formulario.env', 'instalacion.json')]
    salida = privado(salida, nuevo=True)
    try:
        detener(activos)
        archivos, directorios = expandir(entradas)
        with tempfile.TemporaryDirectory(prefix='.formulario-sql-', dir=salida.parent) as carpeta:
            os.chmod(carpeta, 0o700)
            mysql = n.contenedor('mysql')['Id']
            dump = volcar(mysql, carpeta)
            meta = {'origen': 'formulario', 'proyectoOrigen': n.proyecto, 'creado': ahora(),
                    'hostname': n.env['FORM_HOSTNAME'], 'puerto': n.env['FORM_HTTPS_PORT'],
                    'saludPath': n.env.get('FORM_HEALTH_PATH', '/acceso-temporal/salud'), 'imagenes': {s: n.env[k] for s, k in [('app', 'FORM_IMAGE'), ('proxy', 'PROXY_IMAGE'),
                                 ('pisee', 'PISEE_IMAGE'), ('mysql', 'MYSQL_IMAGE'), ('antivirus', 'ANTIVIRUS_IMAGE'),
                                 ('inspector', 'INSPECTOR_IMAGE')]},
                    'baseDatos': base_datos(mysql), 'piseeIdentidad': n.env['PISEE_IDENTIDAD']}
            manifiesto = escribir_respaldo(salida, passfile, archivos, directorios, {'database.sql': dump}, meta)
    finally:
        arrancar_ids(list(reversed(activos)))
    leer_respaldo(salida, passfile)
    return resumen_respaldo(salida, manifiesto)


def actualizar(a):
    requiere_root()
    n = Instalacion(a.raiz)
    passfile = clave(a.clave)
    destino = Path(a.respaldos).absolute()
    archivo = destino / ('formulario-preactualizacion-' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '.gpg')
    informe = {'respaldo': respaldar_nueva(a.raiz, archivo, passfile)}
    anterior_env = (n.raiz / 'formulario.env').read_bytes()
    anterior_infra = {p.name: p.read_bytes() for p in (n.raiz / 'infra').iterdir() if p.is_file()}
    anterior_bin = (n.raiz / 'bin/formulario.py').read_bytes()
    try:
        instalar_infra(n.raiz)
        valores = leer_env(n.raiz / 'formulario.env')
        valores['FORM_IMAGE'] = a.imagen_app
        escribir(n.raiz / 'formulario.env', ''.join(f'{k}={v}\n' for k, v in valores.items()), 0o600)
        n = Instalacion(a.raiz)
        n.compose('--profile', 'migracion', 'run', '--rm', 'migrar')
        n.compose('up', '-d', '--no-deps', '--wait', '--wait-timeout', '300', 'app', 'proxy')
        v = verificar(argparse.Namespace(raiz=a.raiz))
        if not v['ok']:
            raise Rechazo('; '.join(v['problemas']))
        n.datos['actualizaciones'] = n.datos.get('actualizaciones', []) + [
            {'fecha': ahora(), 'imagen': a.imagen_app, 'respaldo': informe['respaldo']['sha256'], 'commit': commit_paquete()}]
        n.guardar()
        informe['verificacion'] = v
        return informe
    except Rechazo as error:
        escribir(n.raiz / 'formulario.env', anterior_env, 0o600)
        for nombre, data in anterior_infra.items():
            escribir(n.raiz / 'infra' / nombre, data, 0o644)
        escribir(n.raiz / 'bin/formulario.py', anterior_bin, 0o700)
        Instalacion(a.raiz).compose('up', '-d', '--no-deps', '--wait', '--wait-timeout', '300', 'app', 'proxy', check=False)
        # Flyway solo avanza: las migraciones son aditivas y la imagen anterior sigue siendo compatible.
        raise Rechazo(f'Actualización revertida a la imagen anterior: {error}') from error


# ----------------------------------------------------------------------------- CLI
def main(argv=None):
    os.umask(0o077)
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest='accion', required=True)

    def opciones_nueva(s):
        s.add_argument('--raiz', required=True)
        s.add_argument('--proyecto', default='formulario')
        s.add_argument('--hostname')
        s.add_argument('--puerto', type=int)
        s.add_argument('--puerto-validacion', type=int, default=30444)
        s.add_argument('--subred-salida', default='10.203.76.0/28')
        s.add_argument('--subred-pisee', default='10.203.75.0/29')
        for img in ['app', 'proxy', 'pisee', 'mysql', 'antivirus', 'inspector']:
            s.add_argument('--imagen-' + img, required=img == 'app')
        s.add_argument('--overlay', action='append', default=[], help='Solo LAB: Compose adicional (stubs)')

    s = sub.add_parser('plan', help='Inventario SOLO LECTURA de la instalación anterior y del espacio')
    s.add_argument('--legado', required=True)
    s.add_argument('--destino')
    s.add_argument('--limite-gib', type=float, default=40)
    s.add_argument('--respaldo-local', action='store_true', help='El respaldo queda en el mismo disco')
    s.add_argument('--ignorar-montaje', action='append', default=[], help=SOLO_LAB)
    s = sub.add_parser('respaldar', help='Respaldo cifrado y verificado (anterior o nueva)')
    g = s.add_mutually_exclusive_group(required=True)
    g.add_argument('--legado')
    g.add_argument('--raiz')
    s.add_argument('--salida', required=True)
    s.add_argument('--clave', required=True)
    s.add_argument('--mantenimiento', action='store_true', help='Detiene brevemente proxy, app y PISEE')
    s.add_argument('--ignorar-montaje', action='append', default=[], help=SOLO_LAB)
    s = sub.add_parser('verificar-respaldo')
    s.add_argument('--archivo', required=True)
    s.add_argument('--clave', required=True)
    s = sub.add_parser('restaurar', help='Respaldo -> instalación NUEVA (MySQL importado y migrado; app sin iniciar)')
    s.add_argument('--archivo', required=True)
    s.add_argument('--clave', required=True)
    opciones_nueva(s)
    s = sub.add_parser('arrancar')
    s.add_argument('--raiz', required=True)
    s.add_argument('--validacion', action='store_true')
    s = sub.add_parser('detener')
    s.add_argument('--raiz', required=True)
    s = sub.add_parser('verificar')
    s.add_argument('--raiz', required=True)
    s = sub.add_parser('transicion', help='Origen -> instalación nueva en el puerto público, con reversión automática')
    s.add_argument('--legado', required=True)
    s.add_argument('--clave', required=True)
    s.add_argument('--respaldos', required=True, help='Directorio de respaldos (preferible externo)')
    s.add_argument('--unidad-legado', default='ddn-formulario-produccion.service')
    s.add_argument('--limite-gib', type=float, default=40)
    s.add_argument('--respaldo-local', action='store_true')
    s.add_argument('--ignorar-montaje', action='append', default=[], help=SOLO_LAB)
    opciones_nueva(s)
    s = sub.add_parser('revertir', help='Instalación nueva -> origen intacto')
    s.add_argument('--legado', required=True)
    s.add_argument('--raiz', required=True)
    s.add_argument('--clave')
    s.add_argument('--respaldo-nuevo')
    s.add_argument('--unidad-legado', default='ddn-formulario-produccion.service')
    s = sub.add_parser('actualizar', help='Respaldo verificado + imagen nueva + migración + verificación')
    s.add_argument('--raiz', required=True)
    s.add_argument('--imagen-app', required=True)
    s.add_argument('--clave', required=True)
    s.add_argument('--respaldos', required=True)
    a = p.parse_args(argv)
    try:
        if a.accion == 'plan':
            r = plan(a)
        elif a.accion == 'respaldar':
            requiere_root()
            r = (respaldar_legado(a.legado, a.salida, clave(a.clave), a.mantenimiento, ignorar=a.ignorar_montaje) if a.legado
                 else respaldar_nueva(a.raiz, a.salida, clave(a.clave), a.mantenimiento))
        elif a.accion == 'verificar-respaldo':
            m = leer_respaldo(a.archivo, clave(a.clave))
            r = resumen_respaldo(Path(a.archivo).absolute(), m)
        elif a.accion == 'restaurar':
            r = restaurar(a)
        elif a.accion == 'arrancar':
            r = arrancar(a)
        elif a.accion == 'detener':
            r = detener_instalacion(a)
        elif a.accion == 'verificar':
            r = verificar(a)
        elif a.accion == 'transicion':
            r = transicion(a)
        elif a.accion == 'revertir':
            r = revertir(a)
        else:
            r = actualizar(a)
    except (Rechazo, OSError, KeyError, ValueError) as error:
        print(json.dumps({'ok': False, 'error': str(error) if isinstance(error, Rechazo) else type(error).__name__ + ': ' + str(error)[:200]}),
              file=sys.stderr)
        return 1
    print(json.dumps(r, indent=2, default=str))
    return 1 if isinstance(r, dict) and (r.get('bloqueos') or r.get('ok') is False) else 0


if __name__ == '__main__':
    raise SystemExit(main())
