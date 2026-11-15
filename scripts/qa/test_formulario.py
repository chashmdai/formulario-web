#!/usr/bin/env python3
"""Herramienta única formulario.py sin Docker real: clasificación de montajes, vista efectiva,
formato de respaldo (GPG real), entorno generado e invariante de nodo PISEE único."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('formulario', ROOT / 'scripts/formulario/formulario.py')
f = importlib.util.module_from_spec(spec)
spec.loader.exec_module(f)


class ClasificacionTest(unittest.TestCase):
    def test_montajes_productivos_conocidos(self):
        self.assertEqual(f.clasificar('app', '/run/keys'), 'keys')
        self.assertEqual(f.clasificar('pisee', '/var/lib/pisee'), 'pisee/state')
        self.assertEqual(f.clasificar('pisee', '/var/lib/pisee/config.json'), 'pisee/config.json')
        self.assertEqual(f.clasificar('app', '/run/secrets/operacion.token'), 'secrets/operations-token')
        self.assertEqual(f.clasificar('proxy', '/etc/caddy/Caddyfile'), 'release')

    def test_archivo_anidado_de_overlay_se_conserva_y_lo_desconocido_bloquea(self):
        self.assertEqual(f.clasificar('proxy', '/run/pki/origin-ca.crt'), 'pki/proxy/origin-ca.crt')
        self.assertIsNone(f.clasificar('app', '/opt/otra-cosa'))
        self.assertIsNone(f.clasificar('desconocido', '/run/keys'))

    def test_compose_nuevo_monta_exactamente_los_destinos_clasificados(self):
        texto = (ROOT / 'infra/formulario/compose.yml').read_text()
        for (svc, destino), clase in f.MONTAJES.items():
            if clase in ('base',) or svc == 'mysql' and destino == '/var/lib/mysql':
                continue
            self.assertIn(':' + destino, texto, f'{svc}:{destino} sin montaje en el Compose nuevo')


class VistaEfectivaTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.base = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def archivo(self, rel, data):
        p = self.base / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(data)
        return p

    def test_directorio_montado_reemplaza_la_base_y_archivo_anidado_la_sobrescribe(self):
        self.archivo('legado/pki/proxy/server.crt', 'BASE')
        self.archivo('legado/pki/proxy/obsoleto.crt', 'OBSOLETO')
        self.archivo('legado/pki/origen/server.crt', 'EFECTIVO')
        self.archivo('legado/pki/origen/ca.crt', 'CA-ORIGEN')
        entradas = [('pki', self.base / 'legado/pki', 'base'),
                    ('pki/proxy', self.base / 'legado/pki/origen', 'proxy:/run/pki'),
                    ('pki/proxy/ca.crt', self.base / 'legado/pki/origen/ca.crt', 'proxy:/run/pki/ca.crt')]
        archivos, directorios = f.expandir(entradas)
        self.assertEqual(Path(archivos['pki/proxy/server.crt']).read_text(), 'EFECTIVO')
        self.assertNotIn('pki/proxy/obsoleto.crt', archivos)
        self.assertIn('pki/origen/server.crt', archivos)  # la base completa también se preserva
        self.assertIn('pki', directorios)

    def test_enlaces_bloquean_y_sockets_se_omiten(self):
        self.archivo('legado/keys/k', 'K')
        (self.base / 'legado/keys/enlace').symlink_to(self.base / 'legado/keys/k')
        with self.assertRaises(f.Rechazo):
            f.expandir([('keys', self.base / 'legado/keys', 'base')])


@unittest.skipUnless(shutil.which('gpg'), 'gpg requerido')
class FormatoRespaldoTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.base = Path(self.temp.name)
        self.clave = self.base / 'clave'
        self.clave.write_text('C' * 48)
        self.clave.chmod(0o600)
        datos = {'config/application.properties': 'a=1\n', 'secrets/operations-token': 'SINTETICO',
                 'store/objects/x': 'DOC' * 1000, 'pisee/runtime/certsX509-institucional/public.pem': 'PEM'}
        for rel, valor in datos.items():
            p = self.base / 'origen' / rel
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(valor)
            p.chmod(0o400)
        self.datos = datos
        self.archivos, self.directorios = f.expandir([(p.name, p, 'b') for p in sorted((self.base / 'origen').iterdir())])
        self.sql = self.base / 'database.sql'
        self.sql.write_text('-- SINTETICO\n')
        self.salida = self.base / 'respaldos/r.gpg'
        self.salida.parent.mkdir()

    def tearDown(self):
        self.temp.cleanup()

    def crear(self):
        return f.escribir_respaldo(self.salida, self.clave, self.archivos, self.directorios, {'database.sql': self.sql},
                                   {'origen': 'prueba', 'baseDatos': {'conteos': {}, 'flyway': '13'}})

    def test_ida_y_vuelta_conserva_contenido_modos_y_detecta_manipulacion(self):
        m = self.crear()
        self.assertEqual(oct(self.salida.stat().st_mode & 0o777), '0o600')
        self.assertNotIn(b'SINTETICO', self.salida.read_bytes())
        destino = self.base / 'extraido'
        destino.mkdir()
        leido = f.leer_respaldo(self.salida, self.clave, destino)
        self.assertEqual(leido['archivos'], m['archivos'])
        for rel, valor in self.datos.items():
            self.assertEqual((destino / 'payload' / rel).read_text(), valor)
            self.assertEqual(m['archivos'][rel]['mode'], 0o400)
        self.assertEqual((destino / 'database.sql').read_text(), '-- SINTETICO\n')
        data = bytearray(self.salida.read_bytes())
        data[len(data) // 2] ^= 0xFF
        self.salida.write_bytes(bytes(data))
        with self.assertRaises(f.Rechazo):
            f.leer_respaldo(self.salida, self.clave)
        f.escribir(self.salida.with_name('r.gpg.sha256'), f.sha256(self.salida) + '\n')
        with self.assertRaises(f.Rechazo):  # integridad GPG aun con SHA externo actualizado
            f.leer_respaldo(self.salida, self.clave)

    def test_destino_existente_y_clave_debil_se_rechazan(self):
        self.crear()
        with self.assertRaises(f.Rechazo):
            self.crear()
        debil = self.base / 'debil'
        debil.write_text('corta')
        debil.chmod(0o600)
        with self.assertRaises(f.Rechazo):
            f.clave(debil)
        debil.write_text('x' * 40)
        debil.chmod(0o644)
        with self.assertRaises(f.Rechazo):
            f.clave(debil)


class EntornoTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.raiz = Path(self.temp.name) / 'formulario'
        self.raiz.mkdir()

    def tearDown(self):
        self.temp.cleanup()

    def args(self, **extra):
        valores = dict(proyecto='formulario', hostname=None, puerto=None, puerto_validacion=30444,
                       subred_salida='10.203.76.0/28', subred_pisee='10.203.75.0/29', imagen_app='img:nueva',
                       imagen_proxy=None, imagen_pisee=None, imagen_mysql=None, imagen_antivirus=None, imagen_inspector=None)
        valores.update(extra)
        return argparse.Namespace(**valores)

    def test_puerta_de_enlace_ips_pisee_salud_e_imagenes_heredadas(self):
        manifiesto = {'hostname': 'formulario.example.gob.cl', 'puerto': '30443', 'piseeIdentidad': 'ab' * 32,
                      'saludPath': '/acceso-temporal/salud',
                      'imagenes': {s: 'sha256:' + s for s in ['app', 'proxy', 'pisee', 'mysql', 'antivirus', 'inspector']}}
        v = f.generar_env(self.raiz, manifiesto, self.args())
        self.assertEqual(v['FORM_TRUSTED_PROXIES'], '10.203.76.1/32')
        self.assertEqual((v['PISEE_NODE_IPV4'], v['PISEE_BACKEND_IPV4']), ('10.203.75.2', '10.203.75.3'))
        self.assertEqual(v['FORM_IMAGE'], 'img:nueva')
        self.assertEqual(v['PISEE_IMAGE'], 'sha256:pisee')  # mismo nodo instalado, sin reconstruir
        self.assertEqual(v['FORM_HEALTH_PATH'], '/acceso-temporal/salud')
        self.assertEqual(oct((self.raiz / 'formulario.env').stat().st_mode & 0o777), '0o600')

    def test_imagen_faltante_bloquea(self):
        with self.assertRaises(f.Rechazo):
            f.generar_env(self.raiz, {'hostname': 'h', 'puerto': '1', 'imagenes': {}}, self.args())

    def test_config_pisee_solo_cambia_la_ip_permitida(self):
        config = self.raiz / 'pisee/config.json'
        config.parent.mkdir()
        original = {'servidores': {'interno': {'seguridad': {'ipPermitidas': ['127.0.0.1', '10.203.74.3']}}},
                    'credencial': 'INTACTA', 'log': {'nivel': 'error'}}
        config.write_text(json.dumps(original))
        config.chmod(0o400)
        f.ajustar_pisee(self.raiz, '10.203.75.3')
        nuevo = json.loads(config.read_text())
        self.assertEqual(nuevo['servidores']['interno']['seguridad']['ipPermitidas'], ['127.0.0.1', '10.203.75.3'])
        self.assertEqual({k: v for k, v in nuevo.items() if k != 'servidores'}, {k: v for k, v in original.items() if k != 'servidores'})
        self.assertEqual(oct(config.stat().st_mode & 0o777), '0o400')


class PiseeUnicoTest(unittest.TestCase):
    def test_detecta_nodo_ajeno_por_certificado_montado_o_etiqueta(self):
        with tempfile.TemporaryDirectory() as temp:
            runtime = Path(temp) / 'runtime'
            (runtime / 'certsX509-institucional').mkdir(parents=True)
            pem = runtime / 'certsX509-institucional/public.pem'
            pem.write_text('CERT-INSTITUCIONAL')
            identidad = hashlib.sha256(b'CERT-INSTITUCIONAL').hexdigest()
            activos = [
                {'Name': '/legado-pisee', 'Config': {'Labels': {'com.docker.compose.project': 'ddn-formulario-produccion'}},
                 'Mounts': [{'Destination': '/opt/pisee', 'Source': str(runtime)}]},
                {'Name': '/propio', 'Config': {'Labels': {'com.docker.compose.project': 'formulario',
                                                          'cl.ddn.formulario.pisee-identidad': identidad}}, 'Mounts': []},
                {'Name': '/otro', 'Config': {'Labels': {'com.docker.compose.project': 'x'}}, 'Mounts': []}]
            with patch.object(f, 'run', return_value='a b c'), patch.object(f, 'docker_inspect', return_value=activos):
                self.assertEqual(f.nodos_pisee_en_conflicto(identidad, 'formulario'), ['/legado-pisee'])
                self.assertEqual(f.nodos_pisee_en_conflicto('otra', 'formulario'), [])


if __name__ == '__main__':
    unittest.main()
