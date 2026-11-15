#!/usr/bin/env python3
"""LAB de punta a punta de la reinstalación limpia (scripts/formulario/formulario.py).

Emula la instalación anterior con su Compose histórico (infra/ubuntu/compose.yml +
compose.production.yml + un overlay de CA de origen), la versión anterior de la aplicación
(esquema V12), identidad PISEE, contraseña temporal activa y datos sintéticos. Después ejecuta
plan, respaldo verificado, ensayo de restauración, bloqueo de doble nodo PISEE, transición,
reversión, protección contra pérdida de datos y actualización. Solo recursos propios
`ddn-lab-*` / `formulario-lab*`; nunca /srv ni contenedores ajenos.

  python3 scripts/qa/lab_migracion.py --trabajo ~/.cache/fq/labmig --jar-anterior <jar V12> --imagen-app <imagen>
"""
import argparse
import hashlib
import html.parser
import http.client
import json
import os
from pathlib import Path
import secrets
import shlex
import socket
import ssl
import subprocess
import sys
import time

REPO = Path(__file__).resolve().parents[2]
TOOL = REPO / 'scripts/formulario/formulario.py'
TOOLS = 'ddn-simulacro-herramientas:local'
STUB_IMAGE = 'python:3.12-slim'
RUNTIME = 'eclipse-temurin:25-jre@sha256:ecdc0adb37bde1a281c4d245a3cd535004b1fc8322d560f5b09463b248c81df0'
HOST = 'formulario-lab.formulario.gob.cl'
LEGADO = 'ddn-lab-legado'
IGN = ['--ignorar-montaje', '/lab/stub.py']
SIN_SYSTEMD = ['--sin-systemd']  # el contenedor de herramientas no tiene systemctl


def sh(args, check=True, input=None):
    r = subprocess.run(args, input=input, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if check and r.returncode:
        raise SystemExit(f'FALLO {args[:4]}: {r.stderr.decode()[-1500:]}{r.stdout.decode()[-1500:]}')
    return r


class Lab:
    def __init__(self, work, jar, image):
        self.w = Path(work).absolute()
        self.jar, self.image = Path(jar).absolute(), image
        self.legado = self.w / 'srv/ddn-formulario-produccion'
        self.env = {'LAB_STUB_IMAGE': STUB_IMAGE, 'LAB_STUB': str(REPO / 'infra/formulario/lab/stub.py')}
        self.resultado = {}

    def root(self, command, check=True):
        """Comando como root en el contenedor de herramientas (rutas idénticas al anfitrión)."""
        env = sum((['-e', f'{k}={v}'] for k, v in self.env.items()), [])
        return sh(['docker', 'run', '--rm', '--user', '0:0', *env, '-v', '/var/run/docker.sock:/var/run/docker.sock',
                   '-v', f'{REPO}:{REPO}:ro', '-v', f'{self.w}:{self.w}', '--workdir', str(self.w),
                   '--entrypoint', 'sh', TOOLS, '-ec', command], check=check)

    def tool(self, *args, check=True):
        r = self.root('python3 ' + shlex.quote(str(TOOL)) + ' ' + ' '.join(shlex.quote(str(a)) for a in args), check=False)
        salida = r.stdout.decode() or r.stderr.decode()
        if check and r.returncode:
            raise SystemExit(f'formulario.py {args[0]} falló: {r.stderr.decode()[-3000:]}')
        try:
            return r.returncode, json.loads(salida)
        except json.JSONDecodeError:
            return r.returncode, {'salida': salida[-2000:]}

    # ------------------------------------------------------------------ origen emulado
    def preparar_legado(self, port):
        self.port = port
        self.root(f'mkdir -p {self.w}/srv && python3 {REPO}/scripts/ubuntu/preparar.py {self.legado} '
                  f'--commit {"0" * 40} --sin-claveunica --sin-confianza-ack --hostname {HOST} '
                  f'--project {LEGADO} --port {port} >/dev/null')
        password = secrets.token_urlsafe(32)
        salt = secrets.token_bytes(16)
        encoded = 'pbkdf2-v5_8:' + (salt + hashlib.pbkdf2_hmac('sha256', password.encode(), salt, 310000, 32)).hex()
        (self.w / 'password').write_text(password)
        L = self.legado
        props = ('formulario.acceso-temporal.habilitado=true\n'
                 'formulario.acceso-temporal.expira=2027-06-30T00:00:00-04:00[America/Santiago]\n')
        compose_env = '\n'.join([
            f'COMPOSE_PROJECT_NAME={LEGADO}', f'FORM_PRIVATE_DIR={L}', f'FORM_HOSTNAME={HOST}',
            f'FORM_HTTPS_PORT={port}', f'FORM_SOURCE_REVISION={"0" * 40}', 'FORM_IMAGE=ddn-lab-anterior:local',
            'MYSQL_IMAGE=mysql:8.4.11', 'PROXY_IMAGE=formulario-proxy:ci-local', f'INSPECTOR_IMAGE={STUB_IMAGE}',
            f'ANTIVIRUS_IMAGE={STUB_IMAGE}', f'PISEE_IMAGE={STUB_IMAGE}', f'PISEE_PRIVATE_DIR={L}/pisee',
            f'PISEE_NODE_CONFIG={L}/pisee/config-produccion.json', f'PISEE_STATE_DIR={L}/pisee/state',
            'PISEE_SUBNET=10.203.74.0/29', 'PISEE_NODE_IPV4=10.203.74.2', 'PISEE_BACKEND_IPV4=10.203.74.3',
            'FORM_TRUSTED_PROXIES=127.0.0.1/32', 'FORM_HEALTH_PATH=/acceso-temporal/salud', f'LAB_STUB_IMAGE={STUB_IMAGE}', f'LAB_STUB={self.env["LAB_STUB"]}', ''])
        # Como en DDN-UBUNTU: server.crt del proxy lo emite una CA de ORIGEN distinta de la CA interna (ca.crt,
        # confianza hacia app) y con el MISMO CN; el overlay real solo cambia el healthcheck del proxy (sin montajes).
        overlay_ca = ('services:\n  proxy:\n    healthcheck:\n      test: ["CMD-SHELL", "curl --fail --silent --cacert '
                      '/run/pki/origin-ca.crt --output /dev/null https://$$FORM_HOSTNAME:8443${FORM_HEALTH_PATH:-/api/session}"]\n')
        origin_ext = ('basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature,keyEncipherment\n'
                      f'extendedKeyUsage=serverAuth\nsubjectAltName=DNS:{HOST},DNS:localhost\n')
        files = {
            'acceso-temporal.pw': password, 'acceso-temporal.hash': encoded, 'props': props,
            'compose.env': compose_env, 'origin.yml': overlay_ca, 'origin.ext': origin_ext,
            'pisee.json': json.dumps({'servidores': {'interno': {'seguridad': {'ipPermitidas': ['127.0.0.1', '10.203.74.3']}}},
                                      'log': {'nivel': 'error', 'ruta': '/dev/null'}, 'credencialSintetica': 'LAB'}),
            'installation.json': json.dumps({'project': LEGADO, 'hostname': HOST, 'port': port, 'backendIp': '10.203.74.3'}),
        }
        staging = self.w / 'staging'
        staging.mkdir(exist_ok=True)
        for name, value in files.items():
            (staging / name).write_text(value)
        self.root(f'''set -e; L={L}; S={staging}
cd $L
install -m 0400 -o 0 -g 0 $S/acceso-temporal.pw secrets/acceso-temporal-password
install -m 0400 -o 1000 -g 1000 $S/acceso-temporal.hash keys/acceso-temporal-password.hash
printf '{{"authorized":false}}' > keys/acceso-temporal-apertura.json; chown 1000:1000 keys/acceso-temporal-apertura.json; chmod 0400 keys/acceso-temporal-apertura.json
chmod 0600 config/application.properties
sed -i '/^formulario.acceso-temporal.habilitado=/d;/^formulario.acceso-temporal.expira=/d' config/application.properties
cat $S/props >> config/application.properties; chmod 0400 config/application.properties
printf 'SINTETICO-CU-ID' > cu/siger.claveunica.client-id; printf 'SINTETICO-CU-SECRETO-0123456789abcdef0123456789' > cu/siger.claveunica.client-secret
chown 1000:1000 cu/*; chmod 0400 cu/*
mkdir -p pisee/runtime/certsX509-institucional pisee/runtime/assets pisee/runtime/conversiones pisee/state
openssl req -x509 -newkey rsa:2048 -nodes -days 30 -subj /CN=PISEE-LAB -keyout /tmp/k.pem -out pisee/runtime/certsX509-institucional/public.pem 2>/dev/null
head -c 2048 /dev/urandom > pisee/runtime/NodoV2; echo '{{}}' > pisee/runtime/assets/a.json; echo '{{}}' > pisee/runtime/conversiones/c.json
echo '{{"estado":"sintetico"}}' > pisee/state/estado.json
install -m 0400 $S/pisee.json pisee/config-produccion.json
chown -R 1000:1000 pisee
openssl req -x509 -newkey rsa:2048 -nodes -days 30 -subj '/CN=Formulario Origin CA privada' -keyout /tmp/o.pem -out pki/proxy/origin-ca.crt -addext basicConstraints=critical,CA:TRUE -addext keyUsage=critical,keyCertSign,cRLSign 2>/dev/null
openssl req -new -key pki/proxy/server.key -subj /CN={HOST} -out /tmp/o.csr 2>/dev/null
openssl x509 -req -sha256 -days 30 -in /tmp/o.csr -CA pki/proxy/origin-ca.crt -CAkey /tmp/o.pem -set_serial 1 -extfile $S/origin.ext -out pki/proxy/server.crt 2>/dev/null; rm -f /tmp/o.pem /tmp/o.csr
if openssl verify -CAfile pki/proxy/ca.crt pki/proxy/server.crt >/dev/null 2>&1; then echo 'LAB: ca.crt no debe verificar server.crt' >&2; exit 1; fi
openssl verify -CAfile pki/proxy/origin-ca.crt pki/proxy/server.crt >/dev/null
chown 1000:1000 pki/proxy/origin-ca.crt pki/proxy/server.crt; chmod 0664 pki/proxy/origin-ca.crt; chmod 0400 pki/proxy/server.crt
mkdir -p authorization store/objects store/chunks store/incoming store/derived; echo '{{"aprobacion":"sintetica"}}' > authorization/signing-status.json
for i in 1 2 3; do head -c 65536 /dev/urandom > store/objects/$(cat /proc/sys/kernel/random/uuid); done; chown -R 1000:1000 store; chmod 0700 store/*
install -m 0400 $S/compose.env compose.env; install -m 0400 $S/origin.yml compose.origin-ca.yml; install -m 0400 $S/installation.json installation.json
mkdir -p release software; echo historico > release/LEEME; echo binario > software/manifest.json
cp pki/proxy/origin-ca.crt {self.w}/ca.pem; chmod 0644 {self.w}/ca.pem''')
        # Imagen ANTERIOR: JAR del commit desplegado (V12) con la sonda de salud de la imagen actual.
        ctx = self.w / 'imagen-anterior'
        ctx.mkdir(exist_ok=True)
        (ctx / 'anterior.jar').write_bytes(self.jar.read_bytes())
        (ctx / 'Dockerfile').write_text(
            f'FROM {self.image} AS nuevo\nFROM {RUNTIME}\nWORKDIR /app\n'
            'COPY --from=nuevo --chown=1000:1000 /app/healthcheck /app/healthcheck\n'
            'COPY --chown=1000:1000 anterior.jar /app/formulario.jar\nUSER 1000:1000\n'
            'ENTRYPOINT ["java", "-jar", "/app/formulario.jar"]\n')
        sh(['docker', 'build', '-q', '-t', 'ddn-lab-anterior:local', str(ctx)])
        self.compose_legado('up', '-d', '--wait', '--wait-timeout', '300', 'mysql')
        self.compose_legado('--profile', 'migracion', 'run', '--rm', 'migrar')
        self.compose_legado('up', '-d', '--wait', '--wait-timeout', '400')
        now = int(time.time() * 1000)
        rows = ','.join(f"(UUID(),REPEAT('0',64),'ADULTO','LAB',FALSE,'{s}',0,{now},{now + 3600000})"
                        for s in ['BORRADOR', 'CANCELADO', 'RECIBIDO'])
        self.sql_legado('INSERT INTO citizen_journey(id,owner_session,kind,notice_version,accepted_channel,state,'
                        f'query_count,created_ms,expires_ms) VALUES {rows}')
        self.sql_legado("INSERT INTO public_delivery(id,owner_run,request_hash,body,state,created_ms,next_attempt_ms,attempts,"
                        f"lease_until_ms) VALUES (UUID(),'0','{'0' * 64}','{{}}','CONFIRMADA',{now},{now},0,0)")
        # Controles CAS tal como estén: la app rechaza controles abiertos sin autorización de recepción.

    def compose_legado(self, *args):
        L = self.legado
        files = [REPO / 'infra/ubuntu/compose.yml', REPO / 'infra/ubuntu/compose.production.yml',
                 L / 'compose.origin-ca.yml', REPO / 'infra/formulario/lab/legado.lab.yml']
        cmd = ['docker', 'compose', '-p', LEGADO, '--env-file', str(L / 'compose.env')]
        for f in files:
            cmd += ['-f', str(f)]
        return self.root(' '.join(shlex.quote(str(c)) for c in [*cmd, *args]))

    def sql_legado(self, q):
        return self.root(f'docker exec $(docker ps -q --filter label=com.docker.compose.project={LEGADO} '
                         f'--filter label=com.docker.compose.service=mysql) mysql --defaults-extra-file=/run/secrets/root.cnf '
                         f'--batch --skip-column-names formulario_publico -e {shlex.quote(q)}').stdout.decode().strip()

    def huella_legado(self):
        """Huella del árbol anterior (excluye estado mutable del acceso temporal y del nodo)."""
        out = self.root(f'cd {self.legado} && find . -type f ! -path "./acceso-temporal/*" ! -path "./pisee/state/*" '
                        '-exec sha256sum {} + | sort | sha256sum').stdout.decode()
        return out.split()[0]

    # ------------------------------------------------------------------ comprobaciones HTTP
    def acceso_temporal(self, port, password):
        ctx = ssl.create_default_context(cafile=str(self.w / 'ca.pem'))
        cookies = {}

        def request(method, path, body=None, headers=None):
            conn = http.client.HTTPSConnection(HOST, port, context=ctx, timeout=20)
            conn.sock = ctx.wrap_socket(socket.create_connection(('127.0.0.1', port), timeout=20), server_hostname=HOST)
            h = dict(headers or {})
            if cookies:
                h['Cookie'] = '; '.join(f'{k}={v}' for k, v in cookies.items())
            conn.request(method, path, body, h)
            r = conn.getresponse()
            data = r.read()
            for k, v in r.getheaders():
                if k.lower() == 'set-cookie':
                    name, value = v.split(';', 1)[0].split('=', 1)
                    cookies[name] = value
            conn.close()
            return r.status, data

        class Form(html.parser.HTMLParser):
            token = None

            def handle_starttag(self, tag, attrs):
                a = dict(attrs)
                if tag == 'input' and a.get('name') == '_csrf':
                    self.token = a.get('value')
        status, body = request('GET', '/acceso-temporal')
        if status != 200:
            return status
        form = Form()
        form.feed(body.decode())
        data = f'_csrf={form.token}&password={password}'
        status, _ = request('POST', '/acceso-temporal', data, {'Content-Type': 'application/x-www-form-urlencoded'})
        return status

    def esperar_puerto(self, port, timeout=120):
        fin = time.time() + timeout
        while time.time() < fin:
            try:
                socket.create_connection(('127.0.0.1', port), timeout=2).close()
                return
            except OSError:
                time.sleep(2)
        raise SystemExit(f'Puerto {port} no disponible')

    # ------------------------------------------------------------------ limpieza propia
    def limpiar(self):
        for proyecto in [LEGADO, 'formulario-lab', 'formulario-lab-ensayo', 'formulario-lab-2']:
            self.root(f'ids=$(docker ps -aq --filter label=com.docker.compose.project={proyecto}); '
                      f'[ -z "$ids" ] || docker rm -f $ids >/dev/null; '
                      f'for v in $(docker volume ls -q --filter label=com.docker.compose.project={proyecto}); do docker volume rm $v >/dev/null; done; '
                      f'for n in $(docker network ls -q --filter label=com.docker.compose.project={proyecto}); do docker network rm $n >/dev/null; done',
                      check=False)
        self.root(f'rm -rf {self.w}/srv {self.w}/staging {self.w}/respaldos', check=False)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--trabajo', required=True)
    p.add_argument('--jar-anterior', required=True)
    p.add_argument('--imagen-app', required=True)
    p.add_argument('--conservar', action='store_true')
    a = p.parse_args()
    lab = Lab(a.trabajo, a.jar_anterior, a.imagen_app)
    lab.w.mkdir(parents=True, exist_ok=True)
    sh(['docker', 'pull', '-q', STUB_IMAGE])
    lab.limpiar()
    R = lab.resultado
    P, V, P2, V2 = 39443, 39444, 39445, 39446
    clave = lab.w / 'clave'
    clave.write_text(secrets.token_urlsafe(48))
    clave.chmod(0o600)
    respaldos = lab.w / 'respaldos'
    try:
        lab.preparar_legado(P)
        password = (lab.w / 'password').read_text()
        lab.esperar_puerto(P)
        R['origenAccesoTemporal'] = {'correcta': lab.acceso_temporal(P, password), 'incorrecta': lab.acceso_temporal(P, 'x' * 40)}
        huella = lab.huella_legado()
        # 1. Plan solo lectura.
        _, R['plan'] = lab.tool('plan', '--legado', lab.legado, '--destino', lab.w / 'srv/formulario', '--limite-gib', 40, *IGN)
        assert not R['plan']['bloqueos'], R['plan']['bloqueos']
        # 2. Respaldo verificado y ensayo de restauración con validación cerrada.
        lab.root(f'mkdir -p {respaldos}')
        _, R['respaldoEnsayo'] = lab.tool('respaldar', '--legado', lab.legado, '--salida', respaldos / 'ensayo.gpg',
                                          '--clave', clave, '--mantenimiento', *IGN)
        lab.esperar_puerto(P)
        comun = ['--imagen-app', a.imagen_app, '--overlay', REPO / 'infra/formulario/lab/compose.lab.yml']
        _, R['restauracionEnsayo'] = lab.tool('restaurar', '--archivo', respaldos / 'ensayo.gpg', '--clave', clave,
                                              '--raiz', lab.w / 'srv/formulario-ensayo', '--proyecto', 'formulario-lab-ensayo',
                                              '--puerto', P2, '--puerto-validacion', V2, '--subred-salida', '10.203.77.0/28',
                                              '--subred-pisee', '10.203.78.0/29', *comun)
        lab.tool('arrancar', '--raiz', lab.w / 'srv/formulario-ensayo', '--validacion')
        lab.esperar_puerto(V2)
        R['ensayoAccesoTemporal'] = lab.acceso_temporal(V2, password)
        _, R['ensayoVerificacion'] = lab.tool('verificar', '--raiz', lab.w / 'srv/formulario-ensayo', '--validacion', check=False)
        # 3. Invariante PISEE: con el nodo anterior activo, el arranque productivo se rechaza.
        code, R['ensayoDobleNodo'] = lab.tool('arrancar', '--raiz', lab.w / 'srv/formulario-ensayo', check=False)
        R['ensayoDobleNodoRechazado'] = code != 0 and 'PISEE' in json.dumps(R['ensayoDobleNodo'])
        lab.tool('detener', '--raiz', lab.w / 'srv/formulario-ensayo')
        # 4. Transición al puerto público con respaldo final, validación cerrada y nodo único.
        _, R['transicion'] = lab.tool('transicion', '--legado', lab.legado, '--clave', clave, '--respaldos', respaldos,
                                      '--raiz', lab.w / 'srv/formulario', '--proyecto', 'formulario-lab', '--puerto', P,
                                      '--puerto-validacion', V, *IGN, *SIN_SYSTEMD, *comun)
        lab.esperar_puerto(P)
        R['nuevaAccesoTemporal'] = lab.acceso_temporal(P, password)
        R['nuevaOrigenCa'] = lab.root(f'sha256sum {lab.w}/srv/formulario/pki/proxy/origin-ca.crt '
                                      f'{lab.legado}/pki/proxy/origin-ca.crt | cut -c1-64 | uniq | wc -l').stdout.decode().strip() == '1'
        R['nuevaPiseeIps'] = json.loads(lab.root(f'cat {lab.w}/srv/formulario/pisee/config.json').stdout)['servidores']['interno']['seguridad']['ipPermitidas']
        R['nuevaCredencialPiseeIntacta'] = 'credencialSintetica' in lab.root(f'cat {lab.w}/srv/formulario/pisee/config.json').stdout.decode()
        R['nuevaControlCas'] = lab.root(f'docker exec $(docker ps -q --filter label=com.docker.compose.project=formulario-lab '
                                        f'--filter label=com.docker.compose.service=mysql) mysql --defaults-extra-file=/run/secrets/root.cnf '
                                        f'--batch --skip-column-names formulario_publico -e "SELECT accept_new FROM operation_control"').stdout.decode().strip()
        R['origenContenedoresConservados'] = len(lab.root(f'docker ps -aq --filter label=com.docker.compose.project={LEGADO}').stdout.split())
        R['origenActivos'] = len(lab.root(f'docker ps -q --filter label=com.docker.compose.project={LEGADO}').stdout.split())
        R['origenArbolIntacto'] = lab.huella_legado() == huella
        # 5. Reversión sin datos nuevos: origen intacto en el mismo puerto.
        _, R['reversion'] = lab.tool('revertir', '--legado', lab.legado, '--raiz', lab.w / 'srv/formulario', *SIN_SYSTEMD)
        lab.esperar_puerto(P)
        R['revertidoAccesoTemporal'] = lab.acceso_temporal(P, password)
        # 6. Segunda transición; datos nuevos bloquean la reversión hasta respaldarlos.
        _, R['transicion2'] = lab.tool('transicion', '--legado', lab.legado, '--clave', clave, '--respaldos', respaldos,
                                       '--raiz', lab.w / 'srv/formulario-2', '--proyecto', 'formulario-lab-2', '--puerto', P,
                                       '--puerto-validacion', V, '--subred-salida', '10.203.79.0/28',
                                       '--subred-pisee', '10.203.80.0/29', *IGN, *SIN_SYSTEMD, *comun)
        now = int(time.time() * 1000)
        lab.root(f'docker exec $(docker ps -q --filter label=com.docker.compose.project=formulario-lab-2 '
                 f'--filter label=com.docker.compose.service=mysql) mysql --defaults-extra-file=/run/secrets/root.cnf '
                 f'formulario_publico -e "INSERT INTO public_delivery(id,owner_run,request_hash,body,state,created_ms,'
                 f"next_attempt_ms,attempts,lease_until_ms) VALUES (UUID(),'0','{'1' * 64}','{{}}','PENDIENTE',{now},{now},0,0)\"")
        code, R['reversionConDatosNuevos'] = lab.tool('revertir', '--legado', lab.legado, '--raiz', lab.w / 'srv/formulario-2', *SIN_SYSTEMD, check=False)
        R['reversionConDatosNuevosRechazada'] = code != 0
        # 7. Actualización sobre la instalación nueva (respaldo previo verificado + migración + verificación).
        _, R['actualizacion'] = lab.tool('actualizar', '--raiz', lab.w / 'srv/formulario-2', '--imagen-app', a.imagen_app,
                                         '--clave', clave, '--respaldos', respaldos)
        _, R['respaldoNuevo'] = lab.tool('respaldar', '--raiz', lab.w / 'srv/formulario-2', '--salida',
                                         respaldos / 'nueva-con-datos.gpg', '--clave', clave, '--mantenimiento')
        _, R['reversionTrasRespaldar'] = lab.tool('revertir', '--legado', lab.legado, '--raiz', lab.w / 'srv/formulario-2',
                                                  '--clave', clave, '--respaldo-nuevo', respaldos / 'nueva-con-datos.gpg',
                                                  *SIN_SYSTEMD)
        lab.esperar_puerto(P)
        R['finalOrigenAccesoTemporal'] = lab.acceso_temporal(P, password)
        R['finalOrigenArbolIntacto'] = lab.huella_legado() == huella
    finally:
        (lab.w / 'resultado.json').write_text(json.dumps(R, indent=2, default=str))
        if not a.conservar:
            lab.limpiar()
    print(json.dumps(R, indent=2, default=str))
    esperado = [R['origenAccesoTemporal'] == {'correcta': 303, 'incorrecta': 401}, R['ensayoAccesoTemporal'] == 303,
                R['ensayoDobleNodoRechazado'], R['nuevaAccesoTemporal'] == 303, R['nuevaOrigenCa'],
                R['origenActivos'] == 0, R['origenArbolIntacto'], R['revertidoAccesoTemporal'] == 303,
                R['reversionConDatosNuevosRechazada'], R['finalOrigenAccesoTemporal'] == 303, R['finalOrigenArbolIntacto'],
                R['transicion']['verificacion']['ok'], R['nuevaCredencialPiseeIntacta']]
    return 0 if all(esperado) else 1


if __name__ == '__main__':
    raise SystemExit(main())
