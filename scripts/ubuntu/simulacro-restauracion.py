#!/usr/bin/env python3
"""Simulacro de recuperación en un proyecto Docker AISLADO, sin tocar recursos existentes.

  origen-lab  Crea un origen LAB sintético NUEVO (sin secretos institucionales: claves generadas
              por preparar.py, sin ClaveÚnica, sin confianza ACK, PISEE simulado), migra su base
              con el JAR ANTERIOR, inserta registros sintéticos y genera un respaldo cifrado con
              respaldo.py. Elimina después su propio proyecto.
  restaurar   Restaura un respaldo (formato respaldo.py) en un directorio NUEVO, levanta MySQL y la
              aplicación con la imagen indicada en un proyecto propio `ddn-simulacro-*` con redes
              internas y sin puertos publicados, importa el volcado, aplica las migraciones nuevas,
              comprueba salud, versión Flyway, conteos y objetos restaurados, e informa en JSON.
              Al terminar elimina SOLO su proyecto (contenedores, redes y su volumen MySQL tras
              comprobar la etiqueta de proyecto). Recepción, PULL, PISEE, ClaveÚnica, multimedia
              y ciclo de vida quedan cerrados por variables de entorno.

Sin root, las operaciones con propietarios 0/999/1000 se ejecutan en un contenedor efímero de
herramientas (docker CLI, python3, gpg, openssl) con el socket Docker local: usar solo en un
equipo de laboratorio o un host aislado, nunca en la VM productiva. No imprime secretos.
"""
import argparse
import datetime
import json
import os
from pathlib import Path
import shlex
import socket
import subprocess
import sys
import time

REPO = Path(__file__).resolve().parents[2]
COMPOSE = REPO / 'infra/ubuntu/compose.yml'
RUNTIME = 'eclipse-temurin:25-jre@sha256:ecdc0adb37bde1a281c4d245a3cd535004b1fc8322d560f5b09463b248c81df0'
TOOLS = 'ddn-simulacro-herramientas:local'
TOOLS_DOCKERFILE = (
    'FROM docker:27-cli\n'
    'RUN apk add --no-cache python3 openssl gnupg coreutils\n'
    'LABEL ddn.simulacro=herramientas\n')
HOSTNAME = 'simulacro-lab.formulario.gob.cl'  # Solo SAN de certificados sintéticos; nunca se resuelve.
# Variables de entorno que prevalecen sobre application.properties: todo cerrado durante el simulacro.
CLOSED = {
    'FORMULARIO_MULTIMEDIA_HABILITADA': 'false', 'PUBLICO_RECEPCION_HABILITADA': 'false',
    'FORMULARIO_RECEPCION_HABILITADA': 'false', 'PUBLICO_INTEGRACION_HABILITADA': 'false',
    'PUBLICO_NNA_HABILITADO': 'false', 'PISEE_SRCEI_ENABLED': 'false', 'SIGER_CLAVEUNICA_HABILITADA': 'false',
    'CICLOVIDA_ONLINEHABILITADO': 'false', 'CICLOVIDA_ELIMINACION_HABILITADA': 'false',
    'FORMULARIO_MINIMIZACIONBORRADORES_HABILITADA': 'false', 'FORMULARIO_ACCESOTEMPORAL_HABILITADO': 'false',
}
TABLES = ['citizen_journey', 'citizen_submission', 'public_delivery', 'operational_event', 'media_upload']


def run(args, *, input=None, check=True, capture=True):
    result = subprocess.run(args, input=input, stdout=subprocess.PIPE if capture else subprocess.DEVNULL,
                            stderr=subprocess.PIPE if capture else subprocess.DEVNULL)
    if check and result.returncode:
        detail = (result.stderr or b'').decode(errors='replace')[-600:]
        raise RuntimeError('Falló ' + Path(args[0]).name + ' ' + ' '.join(args[1:3]) + ': ' + detail)
    return (result.stdout or b'').decode(errors='replace')


def new_workdir(path):
    work = Path(path).absolute()
    # Antes de tocar el sistema de archivos: estas rutas pueden ni siquiera ser legibles.
    if work.is_relative_to('/srv') or work.is_relative_to('/var/lib/docker'):
        raise ValueError('El simulacro nunca trabaja bajo /srv ni el almacenamiento Docker')
    if any(p.is_symlink() or (p / '.git').exists() for p in [work, *work.parents]):
        raise ValueError('Directorio de trabajo fuera de Git y sin enlaces requerido')
    work.mkdir(mode=0o700, parents=True, exist_ok=True)
    return work


def tools():
    if subprocess.run(['docker', 'image', 'inspect', TOOLS], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
        run(['docker', 'build', '-q', '-t', TOOLS, '-'], input=TOOLS_DOCKERFILE.encode())
    return TOOLS


def as_root(work, command, extra_mounts=()):
    """Ejecuta un comando como root del contenedor de herramientas, con rutas idénticas al anfitrión."""
    mounts = ['-v', '/var/run/docker.sock:/var/run/docker.sock', '-v', f'{REPO}:{REPO}:ro', '-v', f'{work}:{work}']
    for m in extra_mounts:
        mounts += ['-v', f'{m}:{m}:ro']
    return run(['docker', 'run', '--rm', '--user', '0:0', *mounts, '--workdir', str(work), '--entrypoint', 'sh',
                tools(), '-ec', command])


def free_port():
    with socket.socket() as s:
        s.bind(('127.0.0.1', 0))
        return s.getsockname()[1]


class Proyecto:
    def __init__(self, work, name, private, image, revision):
        self.work, self.name, self.private = work, name, private
        self.env = work / (name + '.env')
        self.overlay = work / (name + '.overlay.yml')
        values = {'COMPOSE_PROJECT_NAME': name, 'FORM_PRIVATE_DIR': str(private), 'FORM_HOSTNAME': HOSTNAME,
                  'FORM_HTTPS_PORT': str(free_port()), 'FORM_SOURCE_REVISION': revision, 'FORM_IMAGE': image,
                  'INSPECTOR_IMAGE': 'no-usado', 'ANTIVIRUS_IMAGE': 'no-usado', 'PROXY_IMAGE': 'no-usado'}
        self.env.write_text(''.join(f'{k}={v}\n' for k, v in values.items()))
        environment = ''.join(f'      {k}: "{v}"\n' for k, v in CLOSED.items())
        # Sin antivirus, inspector ni proxy: la app depende solo de MySQL y ninguna red sale al exterior.
        self.overlay.write_text(
            'services:\n'
            '  app:\n'
            '    depends_on: !reset\n'
            '      mysql: {condition: service_healthy}\n'
            '    environment:\n' + environment +
            'networks:\n'
            '  salida: {internal: true}\n')

    def compose(self, *args, check=True):
        return run(['docker', 'compose', '--project-name', self.name, '--env-file', str(self.env),
                    '-f', str(COMPOSE), '-f', str(self.overlay), *args], check=check)

    def container(self, service):
        cid = self.compose('ps', '-q', service).strip()
        if not cid:
            raise RuntimeError('Servicio sin contenedor: ' + service)
        return cid

    def sql(self, statement):
        return run(['docker', 'exec', self.container('mysql'), 'mysql', '--defaults-extra-file=/run/secrets/root.cnf',
                    '--batch', '--skip-column-names', 'formulario_publico', '-e', statement]).strip()

    def counts(self):
        result = {t: int(self.sql(f'SELECT COUNT(*) FROM {t}')) for t in TABLES}
        result['flywayVersion'] = self.sql('SELECT version FROM flyway_schema_history WHERE success=1 '
                                           'ORDER BY installed_rank DESC LIMIT 1')
        result['flywayFailed'] = int(self.sql('SELECT COUNT(*) FROM flyway_schema_history WHERE success=0'))
        return result

    def down(self):
        """Elimina SOLO recursos con la etiqueta exacta de este proyecto."""
        self.compose('down', '--remove-orphans', '--timeout', '20', check=False)
        volumes = run(['docker', 'volume', 'ls', '-q', '--filter', 'label=com.docker.compose.project=' + self.name]).split()
        for volume in volumes:
            label = run(['docker', 'volume', 'inspect', '--format',
                         '{{ index .Labels "com.docker.compose.project" }}', volume]).strip()
            if label == self.name and volume.startswith(self.name + '_'):
                run(['docker', 'volume', 'rm', volume])
        return len(volumes)


def wait_healthy(project, service, timeout=420):
    deadline = time.monotonic() + timeout
    cid = project.container(service)
    while time.monotonic() < deadline:
        status = run(['docker', 'inspect', '--format', '{{.State.Health.Status}}', cid]).strip()
        if status == 'healthy':
            return True
        if status == 'unhealthy' or run(['docker', 'inspect', '--format', '{{.State.Running}}', cid]).strip() != 'true':
            break
        time.sleep(5)
    raise RuntimeError(f'{service} no quedó saludable (ver `docker logs` del proyecto {project.name})')


def origen_lab(a):
    work = new_workdir(a.trabajo)
    jar = Path(a.jar_anterior).absolute()
    if not jar.is_file():
        raise ValueError('JAR anterior requerido para crear una base en la versión previa')
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dt%H%M%S')
    origin = work / 'origen'
    commit = a.commit or run(['git', '-C', str(REPO), 'rev-parse', 'HEAD']).strip()
    run(['docker', 'image', 'inspect', RUNTIME])
    # 1. Origen nuevo: identidades y certificados sintéticos generados al vuelo.
    as_root(work, 'python3 ' + shlex.quote(str(REPO / 'scripts/ubuntu/preparar.py')) + ' ' + shlex.quote(str(origin)) +
            ' --commit ' + commit + ' --sin-claveunica --sin-confianza-ack --hostname ' + HOSTNAME +
            ' --project ddn-simulacro-origen --port ' + str(free_port()) + ' >/dev/null')
    as_root(work, f'umask 077; cd {shlex.quote(str(origin))}; mkdir -p pisee/runtime pisee/state store/simulacro; '
            'head -c 4096 /dev/urandom > pisee/runtime/nodo-sintetico.bin; echo "{}" > pisee/state/estado.json; '
            'echo "{\\"simulacro\\":true}" > pisee/config-produccion.json; '
            'head -c 65536 /dev/urandom > store/simulacro/objeto-sintetico.bin; chown -R 1000:1000 pisee store')
    project = Proyecto(work, 'ddn-simulacro-origen-' + stamp, origin, 'no-usado', commit)
    try:
        project.compose('up', '-d', '--wait', '--wait-timeout', '300', 'mysql')
        # 2. Esquema de la versión ANTERIOR (sin las migraciones nuevas), como en producción.
        run(['docker', 'run', '--rm', '--user', '1000:1000', '--network', project.name + '_datos',
             '-v', f'{jar}:/app/anterior.jar:ro',
             '-v', f'{origin}/pki/app/java-truststore:/run/pki/java-truststore:ro',
             '-v', f'{origin}/secrets/migration-password:/run/secrets/migration-password:ro',
             '-e', 'MIGRATION_DB_URL=jdbc:mysql://mysql:3306/formulario_publico?sslMode=VERIFY_IDENTITY&connectTimeout=5000',
             '-e', 'MIGRATION_DB_USER=formulario_migrador', '-e', 'MIGRATION_DB_PASSWORD_FILE=/run/secrets/migration-password',
             '-e', 'JAVA_TOOL_OPTIONS=-Xmx384m -Djavax.net.ssl.trustStore=/run/pki/java-truststore -Djavax.net.ssl.trustStorePassword=changeit',
             RUNTIME, 'java', '-jar', '/app/anterior.jar', '--migrate'])
        # 3. Registros exclusivamente sintéticos (sin RUN ni datos personales).
        now = int(time.time() * 1000)
        rows = ','.join(f"(UUID(),REPEAT('0',64),'ADULTO','SIMULACRO',FALSE,'{state}',0,{now},{now + 3600000})"
                        for state in ['BORRADOR', 'BORRADOR', 'CANCELADO'])
        project.sql('INSERT INTO citizen_journey(id,owner_session,kind,notice_version,accepted_channel,state,'
                    f'query_count,created_ms,expires_ms) VALUES {rows}')
        events = ','.join(f"({now},'SIMULACRO','sintetico-{i}','REGISTRO_SINTETICO')" for i in range(5))
        project.sql(f'INSERT INTO operational_event(at_ms,category,aggregate_id,code) VALUES {events}')
        before = project.counts()
        # 4. Respaldo con la herramienta productiva (clave externa al origen).
        backups = work / 'respaldos'
        key = work / 'clave-respaldo'
        archive = backups / f'formulario-lab-{stamp}.gpg'
        as_root(work, f'umask 077; mkdir -p {shlex.quote(str(backups))}; head -c 48 /dev/urandom | base64 > {shlex.quote(str(key))}; '
                f'python3 {shlex.quote(str(REPO / "scripts/ubuntu/respaldo.py"))} backup --private-dir {shlex.quote(str(origin))} '
                f'--project {project.name} --pisee-private-dir {shlex.quote(str(origin / "pisee/runtime"))} '
                f'--pisee-node-config {shlex.quote(str(origin / "pisee/config-produccion.json"))} '
                f'--pisee-state-dir {shlex.quote(str(origin / "pisee/state"))} --key-file {shlex.quote(str(key))} '
                f'--output {shlex.quote(str(archive))} --maintenance --reserve-bytes 0 >/dev/null; '
                f'python3 {shlex.quote(str(REPO / "scripts/ubuntu/respaldo.py"))} verify --archive {shlex.quote(str(archive))} '
                f'--key-file {shlex.quote(str(key))} >/dev/null; '
                f'chown 1000:1000 {shlex.quote(str(key))} {shlex.quote(str(archive))} {shlex.quote(str(archive))}.sha256')
        (work / 'origen-conteos.json').write_text(json.dumps(before, indent=2))
        report = {'origen': project.name, 'archivo': str(archive), 'clave': str(key), 'conteos': before,
                  'secretosInstitucionales': False, 'proveedoresReales': False}
    finally:
        removed = project.down()
    if not a.conservar:
        as_root(work, f'rm -rf {shlex.quote(str(origin))}')
    report['volumenesPropiosEliminados'] = removed
    print(json.dumps(report, indent=2))


def restaurar(a):
    work = new_workdir(a.trabajo)
    archive, key = Path(a.archivo).absolute(), Path(a.clave).absolute()
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dt%H%M%S')
    target = work / ('restaurado-' + stamp)
    started = time.monotonic()
    as_root(work, f'python3 {shlex.quote(str(REPO / "scripts/ubuntu/respaldo.py"))} restore --destination {shlex.quote(str(target))} '
            f'--archive {shlex.quote(str(archive))} --key-file {shlex.quote(str(key))} >/dev/null',
            extra_mounts=[archive.parent] if archive.parent != work and work not in archive.parents else [])
    revision = run(['git', '-C', str(REPO), 'rev-parse', 'HEAD']).strip()
    project = Proyecto(work, 'ddn-simulacro-' + stamp, target, a.imagen, revision)
    report = {'proyecto': project.name, 'imagen': a.imagen, 'restauradoEn': str(target)}
    try:
        project.compose('up', '-d', '--wait', '--wait-timeout', '300', 'mysql')
        mysql = project.container('mysql')
        # El volcado restaurado pertenece a root: se importa desde el contenedor de herramientas.
        as_root(work, f'docker exec -i {mysql} mysql --defaults-extra-file=/run/secrets/root.cnf formulario_publico '
                f'< {shlex.quote(str(target / "database.sql"))}')
        report['antesDeMigrar'] = project.counts()
        project.compose('--profile', 'migracion', 'run', '--rm', 'migrar')
        project.compose('up', '-d', 'app')
        wait_healthy(project, 'app')
        report['despuesDeMigrar'] = project.counts()
        report['appSaludable'] = True
        report['puertosPublicados'] = run(['docker', 'port', project.container('app')]).strip() or None
        hashes = as_root(work, f'cd {shlex.quote(str(target))} && find store -type f -exec sha256sum {{}} + | sort | sha256sum')
        report['storeAgregadoSha256'] = hashes.split()[0]
        report['storeArchivos'] = int(as_root(work, f'find {shlex.quote(str(target / "store"))} -type f | wc -l').strip())
        expected = work / 'origen-conteos.json'
        if expected.is_file():
            origin = json.loads(expected.read_text())
            report['coincideConOrigen'] = all(report['antesDeMigrar'][t] == origin[t] for t in TABLES)
    finally:
        report['volumenesPropiosEliminados'] = project.down()
        if not a.conservar:
            as_root(work, f'rm -rf {shlex.quote(str(target))}')
    report['duracionSegundos'] = round(time.monotonic() - started)
    print(json.dumps(report, indent=2))
    ok = report.get('appSaludable') and report['despuesDeMigrar']['flywayFailed'] == 0 and report.get('coincideConOrigen', True)
    return 0 if ok else 1


def main():
    os.umask(0o077)
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest='accion', required=True)
    o = sub.add_parser('origen-lab')
    o.add_argument('--trabajo', required=True)
    o.add_argument('--jar-anterior', required=True, help='JAR de la versión desplegada (esquema previo)')
    o.add_argument('--commit')
    o.add_argument('--conservar', action='store_true')
    r = sub.add_parser('restaurar')
    r.add_argument('--trabajo', required=True)
    r.add_argument('--archivo', required=True)
    r.add_argument('--clave', required=True)
    r.add_argument('--imagen', required=True, help='Imagen candidata por tag local o digest')
    r.add_argument('--conservar', action='store_true', help='Conserva el directorio restaurado para inspección')
    a = p.parse_args()
    try:
        return origen_lab(a) or 0 if a.accion == 'origen-lab' else restaurar(a)
    except (OSError, ValueError, RuntimeError, KeyError) as error:
        print('Simulacro detenido: ' + str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
