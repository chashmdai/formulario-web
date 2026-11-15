#!/usr/bin/env python3
"""Herramienta única formulario.py sin Docker real: clasificación de montajes, vista efectiva,
formato de respaldo (GPG real), entorno generado, invariante de nodo PISEE único y CA del
healthcheck del proxy (PKI real con openssl, healthcheck real con sh + curl)."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import ssl
import subprocess
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



class FakeInstalacion:
    """Instalación nueva simulada: MySQL activo o no, huellas controladas y registro de acciones."""
    def __init__(self, raiz, mysql_activo=True, huellas=None, escritores=('p', 'a')):
        self.raiz, self.proyecto, self.acciones = Path(raiz), 'formulario', []
        self.env = {'PISEE_IDENTIDAD': 'id'}
        self.mysql_activo, self._huellas, self._escritores = mysql_activo, huellas, list(escritores)

    def contenedor(self, svc):
        if svc == 'mysql':
            return {'Id': 'mysql', 'State': {'Running': self.mysql_activo}}
        if svc in ('proxy', 'app') and self._escritores:
            return {'Id': 'proxy' if svc == 'proxy' else 'app', 'State': {'Running': True}}
        return None

    def sql(self, q):
        assert q.startswith('CHECKSUM TABLE')
        return '\n'.join(f'formulario_publico.{t}\t{v}' for t, v in self._huellas.items())

    def compose(self, *args, **kw):
        self.acciones.append(('compose',) + args)


def base_huellas(valor='1'):
    return {t: valor for t in f.TABLAS_HUELLA}


class ReversionSeguraTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.raiz = Path(self.temp.name) / 'formulario'
        (self.raiz / 'registro').mkdir(parents=True)
        (self.raiz / 'instalacion.json').write_text('{}')
        (self.raiz / 'registro/linea-base.json').write_text(json.dumps({'huellas': base_huellas()}))
        self.eventos, self.activos = [], []
        self.patches = [patch.object(f, 'detener', side_effect=lambda ids: self.eventos.append(('detener', list(ids)))),
                        patch.object(f, 'arrancar_ids', side_effect=lambda ids: self.eventos.append(('arrancar', list(ids)))),
                        patch.object(f, 'legado_arriba', side_effect=lambda *x, **k: self.eventos.append(('legado',)) or 'ok'),
                        patch.object(f, 'unidad_nueva', return_value='no-instalada'),
                        patch.object(f, 'contenedores', side_effect=lambda proyecto, activos=False: list(self.activos)),
                        patch.object(f, 'requiere_root')]
        for p in self.patches:
            p.start()

    def tearDown(self):
        for p in self.patches:
            p.stop()
        self.temp.cleanup()

    def args(self, **extra):
        return argparse.Namespace(**{'legado': '/srv/legado', 'raiz': str(self.raiz), 'unidad_legado': 'u.service',
                                     'sin_systemd': False, 'clave': None, 'respaldo_nuevo': None, **extra})

    def con(self, inst):
        return patch.object(f, 'Instalacion', return_value=inst)

    def test_mysql_detenido_bloquea_y_no_reanuda_el_origen(self):
        with self.con(FakeInstalacion(self.raiz, mysql_activo=False, huellas=base_huellas())):
            with self.assertRaisesRegex(f.Rechazo, 'MySQL'):
                f.revertir(self.args())
        self.assertNotIn(('legado',), self.eventos)

    def test_sin_linea_base_bloquea(self):
        (self.raiz / 'registro/linea-base.json').unlink()
        with self.con(FakeInstalacion(self.raiz, huellas=base_huellas())):
            with self.assertRaisesRegex(f.Rechazo, 'línea base'):
                f.revertir(self.args())
        self.assertNotIn(('legado',), self.eventos)

    def test_modificacion_de_fila_existente_con_igual_conteo_bloquea_y_restablece_escritores(self):
        cambiadas = {**base_huellas(), 'public_delivery': '999'}  # p. ej. estado de integración actualizado
        inst = FakeInstalacion(self.raiz, huellas=cambiadas)
        with self.con(inst):
            with self.assertRaisesRegex(f.Rechazo, 'public_delivery'):
                f.revertir(self.args())
        self.assertEqual(self.eventos[0][0], 'detener')
        self.assertEqual(self.eventos[-1], ('arrancar', ['app', 'proxy']))
        self.assertNotIn(('legado',), self.eventos)

    def test_respaldo_nuevo_que_no_contiene_el_estado_actual_bloquea(self):
        clave = Path(self.temp.name) / 'k'
        clave.write_text('k' * 40)
        clave.chmod(0o600)
        actuales = {**base_huellas(), 'citizen_submission': '7'}
        with self.con(FakeInstalacion(self.raiz, huellas=actuales)), \
                patch.object(f, 'leer_respaldo', return_value={'proyectoOrigen': 'formulario',
                                                               'baseDatos': {'huellas': base_huellas()}}):
            with self.assertRaisesRegex(f.Rechazo, 'estado actual'):
                f.revertir(self.args(clave=str(clave), respaldo_nuevo='/x.gpg'))
        with self.con(FakeInstalacion(self.raiz, huellas=actuales)), \
                patch.object(f, 'leer_respaldo', return_value={'proyectoOrigen': 'formulario', 'baseDatos': {'huellas': actuales}}):
            r = f.revertir(self.args(clave=str(clave), respaldo_nuevo='/x.gpg'))
        self.assertEqual(r['cambiosRespaldados'], ['citizen_submission'])
        self.assertIn(('legado',), self.eventos)

    def test_sin_cambios_revierte(self):
        inst = FakeInstalacion(self.raiz, huellas=base_huellas())
        with self.con(inst):
            r = f.revertir(self.args())
        self.assertEqual(r['origen'], 'reanudado')
        self.assertIn(('compose', 'stop', '--timeout', '40'), inst.acciones)

    def test_automatica_tras_produccion_exige_demostracion(self):
        with self.con(FakeInstalacion(self.raiz, mysql_activo=False, huellas=base_huellas())):
            with self.assertRaisesRegex(f.Rechazo, 'bloqueada'):
                f.revertir_automatico(self.args(), produccion_iniciada=True)
        self.assertNotIn(('legado',), self.eventos)
        with self.con(FakeInstalacion(self.raiz, huellas={**base_huellas(), 'operation_control': '2'})):
            with self.assertRaisesRegex(f.Rechazo, 'operation_control'):
                f.revertir_automatico(self.args(), produccion_iniciada=True)
        self.assertNotIn(('legado',), self.eventos)

    def test_automatica_antes_de_produccion_no_necesita_base_de_datos(self):
        inst = FakeInstalacion(self.raiz, mysql_activo=False, huellas=base_huellas())
        with self.con(inst):
            r = f.revertir_automatico(self.args(), produccion_iniciada=False)
        self.assertEqual(r['origen'], 'reanudado')
        self.assertIn(('legado',), self.eventos)


    def test_no_reanuda_el_origen_si_la_nueva_sigue_activa(self):
        self.activos = [{'Name': '/formulario-pisee-1', 'Config': {'Labels': {'com.docker.compose.service': 'pisee'}}}]
        for llamada in [lambda: f.revertir(self.args()), lambda: f.revertir_automatico(self.args(), produccion_iniciada=False),
                        lambda: f.revertir_automatico(self.args(), produccion_iniciada=True)]:
            with self.con(FakeInstalacion(self.raiz, huellas=base_huellas())):
                with self.assertRaisesRegex(f.Rechazo, 'No se pudo detener'):
                    llamada()
        self.assertNotIn(('legado',), self.eventos)


class LegadoArribaTest(unittest.TestCase):
    def test_no_arranca_el_origen_con_otro_nodo_pisee_activo(self):
        arranques = []
        with patch.object(f, 'inventario_legado', return_value={'proyecto': 'legado', 'servicios': {}}), \
                patch.object(f, 'nodos_pisee_en_conflicto', return_value=['/formulario-pisee-1']), \
                patch.object(f, 'arrancar_ids', side_effect=arranques.append):
            with self.assertRaisesRegex(f.Rechazo, 'PISEE'):
                f.legado_arriba('/srv/l', 'u.service', True, 'id')
        self.assertEqual(arranques, [])


class TransicionExcepcionesTest(unittest.TestCase):
    def test_error_no_rechazo_en_restauracion_pasa_por_la_recuperacion(self):
        llamado = []
        a = argparse.Namespace(legado='/srv/l', raiz='/srv/n', clave='k', respaldos='/e', unidad_legado='u.service',
                               limite_gib=40, respaldo_local=False, ignorar_montaje=[], sin_systemd=True)
        for error in [OSError('disco'), KeyError('FORM_HOSTNAME'), ValueError('json')]:
            llamado.clear()
            with patch.object(f, 'requiere_root'), patch.object(f, 'clave'), \
                    patch.object(f, 'plan', return_value={'bloqueos': []}), \
                    patch.object(f, 'inventario_legado', return_value={'servicios': {}}), \
                    patch.object(f, 'preflight_systemd'), patch.object(f, 'detener'), \
                    patch.object(f, 'respaldar_legado', return_value={'verificado': True}), \
                    patch.object(f, 'exigir_unidad', return_value='omitido'), \
                    patch.object(f, 'restaurar', side_effect=error), \
                    patch.object(f, 'revertir_automatico', side_effect=lambda a, p: llamado.append(p) or {'origen': 'reanudado'}):
                with self.assertRaisesRegex(f.Rechazo, type(error).__name__):
                    f.transicion(a)
            self.assertEqual(llamado, [False])


class HuellasTest(unittest.TestCase):
    def test_huella_incompleta_falla(self):
        with self.assertRaises(f.Rechazo):
            f.huellas(lambda q: 'formulario_publico.citizen_journey\t1')
        completo = '\n'.join(f'formulario_publico.{t}\t{i}' for i, t in enumerate(f.TABLAS_HUELLA))
        self.assertEqual(len(f.huellas(lambda q: completo)), len(f.TABLAS_HUELLA))


class SystemdTest(unittest.TestCase):
    def test_fallo_o_estado_inesperado_bloquean_y_omitido_solo_en_lab(self):
        with patch.object(f, 'systemctl', return_value='fallo'), patch.object(f, 'estado_unidad', return_value='enabled'):
            with self.assertRaises(f.Rechazo):
                f.exigir_unidad('disable', 'u.service', False)
        with patch.object(f, 'systemctl', return_value='ok'), patch.object(f, 'estado_unidad', return_value='enabled'):
            with self.assertRaisesRegex(f.Rechazo, 'disabled'):
                f.exigir_unidad('disable', 'u.service', False)
            self.assertEqual(f.exigir_unidad('enable', 'u.service', False), 'ok')
        with patch.object(f, 'systemctl', return_value='omitido'):
            with self.assertRaises(f.Rechazo):
                f.exigir_unidad('enable', 'u.service', False)
            self.assertEqual(f.exigir_unidad('enable', 'u.service', True), 'omitido')

    def transicion_args(self):
        return argparse.Namespace(legado='/srv/l', raiz='/srv/n', clave='k', respaldos='/e', unidad_legado='u.service',
                                  limite_gib=40, respaldo_local=False, ignorar_montaje=[], sin_systemd=False)

    def test_transicion_no_interrumpe_si_systemd_previo_falla(self):
        llamado = []
        with patch.object(f, 'requiere_root'), patch.object(f, 'clave'), \
                patch.object(f, 'plan', return_value={'bloqueos': []}), \
                patch.object(f, 'inventario_legado', return_value={'servicios': {}}), \
                patch.object(f, 'preflight_systemd', side_effect=f.Rechazo('unidad anterior no habilitada')), \
                patch.object(f, 'respaldar_legado', side_effect=lambda *a, **k: llamado.append('respaldo')):
            with self.assertRaisesRegex(f.Rechazo, 'no habilitada'):
                f.transicion(self.transicion_args())
        self.assertEqual(llamado, [])

    def test_transicion_bloquea_si_no_puede_deshabilitar_la_unidad_anterior(self):
        llamado = []
        with patch.object(f, 'requiere_root'), patch.object(f, 'clave'), \
                patch.object(f, 'plan', return_value={'bloqueos': []}), \
                patch.object(f, 'inventario_legado', return_value={'servicios': {}}), \
                patch.object(f, 'preflight_systemd'), patch.object(f, 'detener'), \
                patch.object(f, 'respaldar_legado', return_value={'verificado': True}), \
                patch.object(f, 'exigir_unidad', side_effect=f.Rechazo('disable falló')), \
                patch.object(f, 'restaurar', side_effect=lambda a: llamado.append('restaurar')), \
                patch.object(f, 'revertir_automatico', side_effect=lambda a, p: llamado.append(('revertir', p)) or {}):
            with self.assertRaisesRegex(f.Rechazo, 'disable falló'):
                f.transicion(self.transicion_args())
        self.assertEqual(llamado, [('revertir', False)])


class ServiciosObligatoriosTest(unittest.TestCase):
    sanos = {s: 'healthy' for s in f.SERVICIOS}

    def test_produccion_exige_los_seis(self):
        self.assertEqual(f.problemas_servicios(self.sanos, False), [])
        sin_pisee = {k: v for k, v in self.sanos.items() if k != 'pisee'}
        self.assertIn('Servicios ausentes: pisee', f.problemas_servicios(sin_pisee, False))
        self.assertTrue(f.problemas_servicios({**self.sanos, 'antivirus': 'starting'}, False))
        self.assertTrue(f.problemas_servicios({'mysql': 'healthy'}, False))

    def test_validacion_exige_todo_salvo_pisee_y_rechaza_pisee_activo(self):
        sin_pisee = {k: v for k, v in self.sanos.items() if k != 'pisee'}
        self.assertEqual(f.problemas_servicios(sin_pisee, True), [])
        self.assertIn('Nodo PISEE activo durante la validación', f.problemas_servicios(self.sanos, True))
        self.assertTrue(f.problemas_servicios({k: v for k, v in sin_pisee.items() if k != 'inspector'}, True))


HOST = 'formulario.defensorianinez.gob.cl'


import http.server as _http_server


class ServidorSilencioso(_http_server.HTTPServer):
    def handle_error(self, request, client_address):
        pass  # el cliente cierra la conexión TLS al terminar


@unittest.skipUnless(shutil.which('openssl'), 'openssl requerido')
class TlsProxyTest(unittest.TestCase):
    """Regresión DDN-UBUNTU 15/11/2026: en producción pki/proxy/server.crt lo emite origin-ca.crt y
    pki/proxy/ca.crt es la CA interna (confianza hacia app), ambas con el MISMO CN. El healthcheck del
    Compose nuevo usaba ca.crt y el proxy nunca quedaba sano; `plan` no lo detectaba."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.base = Path(self.temp.name)
        self.interna = self.ca('interna')
        self.origen = self.ca('origen')

    def tearDown(self):
        self.temp.cleanup()

    def openssl(self, *args):
        subprocess.run(['openssl', *args], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    def ca(self, nombre):
        d = self.base / nombre
        d.mkdir()
        self.openssl('req', '-x509', '-newkey', 'ec', '-pkeyopt', 'ec_paramgen_curve:prime256v1', '-nodes', '-days', '30',
                     '-subj', '/CN=Formulario Origin CA privada', '-keyout', str(d / 'ca.key'), '-out', str(d / 'ca.crt'),
                     '-addext', 'basicConstraints=critical,CA:TRUE', '-addext', 'keyUsage=critical,keyCertSign,cRLSign')
        return d

    def servidor(self, ca, nombre, sans=f'DNS:{HOST},DNS:localhost'):
        d = self.base / nombre
        d.mkdir()
        (d / 'ext').write_text('basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature\n'
                               f'extendedKeyUsage=serverAuth\nsubjectAltName={sans}\n')
        self.openssl('req', '-new', '-newkey', 'ec', '-pkeyopt', 'ec_paramgen_curve:prime256v1', '-nodes', '-subj', f'/CN={HOST}',
                     '-keyout', str(d / 'server.key'), '-out', str(d / 'server.csr'))
        self.openssl('x509', '-req', '-in', str(d / 'server.csr'), '-CA', str(ca / 'ca.crt'), '-CAkey', str(ca / 'ca.key'),
                     '-set_serial', '1', '-days', '30', '-extfile', str(d / 'ext'), '-out', str(d / 'server.crt'))
        return d

    def pki(self, servidor, ca=True, origen=None):
        archivos = {'pki/proxy/server.crt': servidor / 'server.crt', 'pki/proxy/server.key': servidor / 'server.key'}
        if ca:
            archivos['pki/proxy/ca.crt'] = self.interna / 'ca.crt'
        if origen:
            archivos['pki/proxy/origin-ca.crt'] = origen / 'ca.crt'
        return archivos

    def test_layout_productivo_origin_ca_firma_server_y_ca_interna_no(self):
        archivos = self.pki(self.servidor(self.origen, 's'), origen=self.origen)
        informe, bloqueos = f.tls_proxy(archivos, HOST)
        self.assertEqual(bloqueos, [])
        self.assertEqual(informe['caSalud'], 'pki/proxy/origin-ca.crt')
        self.assertTrue(informe['verificado'])
        # El healthcheck anterior (siempre ca.crt) falla con este mismo material: es el defecto corregido.
        with self.assertRaises(ssl.SSLCertVerificationError):
            f.handshake_tls(archivos['pki/proxy/server.crt'], archivos['pki/proxy/server.key'], self.interna / 'ca.crt', HOST)

    def test_sin_origin_ca_se_usa_ca_crt(self):
        informe, bloqueos = f.tls_proxy(self.pki(self.servidor(self.interna, 's')), HOST)
        self.assertEqual(bloqueos, [])
        self.assertEqual(informe['caSalud'], 'pki/proxy/ca.crt')

    def test_ca_seleccionada_incompatible_bloquea(self):
        # Sin origin-ca.crt, server.crt emitido por otra CA con el mismo CN.
        informe, bloqueos = f.tls_proxy(self.pki(self.servidor(self.origen, 's')), HOST)
        self.assertFalse(informe['verificado'])
        self.assertEqual(len(bloqueos), 1)
        self.assertIn('pki/proxy/ca.crt no verifica pki/proxy/server.crt', bloqueos[0])
        # origin-ca.crt presente tiene prioridad: si no emitió server.crt, también bloquea (no se cae a ca.crt).
        _, bloqueos = f.tls_proxy(self.pki(self.servidor(self.interna, 's2'), origen=self.origen), HOST)
        self.assertIn('pki/proxy/origin-ca.crt no verifica', bloqueos[0])

    def test_nombre_clave_o_material_ausente_bloquean(self):
        ajeno = self.servidor(self.origen, 'ajeno', sans='DNS:otro.example')
        self.assertTrue(f.tls_proxy(self.pki(ajeno, origen=self.origen), HOST)[1])
        mezclado = self.pki(self.servidor(self.origen, 's'), origen=self.origen)
        mezclado['pki/proxy/server.key'] = self.servidor(self.origen, 'otra') / 'server.key'  # clave que no corresponde
        self.assertTrue(f.tls_proxy(mezclado, HOST)[1])
        sin_ca = self.pki(self.servidor(self.interna, 's3'), ca=False)
        self.assertIn('sin CA', f.tls_proxy(sin_ca, HOST)[1][0])
        sin_key = {k: v for k, v in sin_ca.items() if not k.endswith('server.key')}
        self.assertTrue(f.tls_proxy(sin_key, HOST)[1])
        self.assertTrue(f.tls_proxy(self.pki(self.servidor(self.interna, 's4')), None)[1])

    def plan_con(self, pki_dir):
        inv = {'raiz': self.base, 'proyecto': 'legado', 'env': {'FORM_HOSTNAME': HOST}, 'meta': {},
               'servicios': {'mysql': {'Id': 'm'}}, 'entradas': [('pki/proxy', pki_dir, 'proxy:/run/pki')],
               'montajes': [], 'bloqueos': [], 'noMigrado': [], 'imagenes': {}}
        a = argparse.Namespace(legado=str(self.base), destino=None, limite_gib=40, respaldo_local=True, ignorar_montaje=[])
        with patch.object(f, 'inventario_legado', return_value=inv), \
                patch.object(f, 'base_datos', return_value={'bytes': 0, 'conteos': {}, 'flyway': '12', 'huellas': {},
                                                            'controles': {'aceptarNuevos': True, 'recuperar': True}}):
            return f.plan(a)

    def directorio_proxy(self, nombre, servidor, origen=None):
        d = self.base / nombre
        d.mkdir()
        for archivo in ['server.crt', 'server.key']:
            shutil.copy(servidor / archivo, d / archivo)
        shutil.copy(self.interna / 'ca.crt', d / 'ca.crt')
        if origen:
            shutil.copy(origen / 'ca.crt', d / 'origin-ca.crt')
        return d

    def test_plan_informa_la_ca_y_bloquea_la_incompatibilidad(self):
        bueno = self.plan_con(self.directorio_proxy('p-ok', self.servidor(self.origen, 's'), self.origen))
        self.assertEqual(bueno['bloqueos'], [])
        self.assertEqual(bueno['proxyTls']['caSalud'], 'pki/proxy/origin-ca.crt')
        malo = self.plan_con(self.directorio_proxy('p-mal', self.servidor(self.origen, 's2')))
        self.assertTrue(any('healthcheck del proxy' in b for b in malo['bloqueos']))

    def test_transicion_no_interrumpe_con_ca_incompatible(self):
        llamado = []
        pki_dir = self.directorio_proxy('p-mal', self.servidor(self.origen, 's'))
        plan_real = self.plan_con(pki_dir)
        a = argparse.Namespace(legado='/srv/l', raiz='/srv/n', clave='k', respaldos='/e', unidad_legado='u.service',
                               limite_gib=40, respaldo_local=True, ignorar_montaje=[], sin_systemd=False)
        with patch.object(f, 'requiere_root'), patch.object(f, 'clave'), patch.object(f, 'plan', return_value=plan_real), \
                patch.object(f, 'detener', side_effect=lambda ids: llamado.append('detener')), \
                patch.object(f, 'respaldar_legado', side_effect=lambda *x, **k: llamado.append('respaldo')):
            with self.assertRaisesRegex(f.Rechazo, 'healthcheck del proxy'):
                f.transicion(a)
        self.assertEqual(llamado, [])


class HealthcheckProxyComposeTest(unittest.TestCase):
    """El `test` real del healthcheck del proxy (infra/formulario/compose.yml) ejecutado con sh y curl
    contra un servidor TLS local: misma regla que PROXY_CA_SALUD y verificación estricta."""

    @staticmethod
    def comando():
        texto = (ROOT / 'infra/formulario/compose.yml').read_text()
        proxy = texto.split('\n  proxy:\n', 1)[1].split('\n  inspector:\n', 1)[0]
        linea = next(l for l in proxy.splitlines() if l.strip().startswith('test:'))
        tipo, cmd = json.loads(linea.split('test:', 1)[1])
        assert tipo == 'CMD-SHELL'
        return cmd

    def test_sin_modo_inseguro_y_mismo_orden_que_formulario(self):
        cmd = self.comando()
        for inseguro in [' -k ', '--insecure', '--no-verify', 'verify=0']:
            self.assertNotIn(inseguro, cmd + ' ')
        self.assertIn('--cacert', cmd)
        posiciones = [cmd.index('/run/pki/' + n) for n in f.PROXY_CA_SALUD]
        self.assertEqual(posiciones, sorted(posiciones))

    @unittest.skipUnless(shutil.which('openssl') and shutil.which('curl') and shutil.which('sh'), 'openssl, curl y sh requeridos')
    def test_ejecuta_el_healthcheck_con_ambas_ca_y_rechaza_la_incompatible(self):
        import http.server
        import threading
        pk = TlsProxyTest('run')
        pk.setUp()
        self.addCleanup(pk.tearDown)

        def probar(nombre, servidor, origen):
            pki = pk.directorio_proxy(nombre, servidor, origen)
            contexto = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            contexto.load_cert_chain(str(pki / 'server.crt'), str(pki / 'server.key'))

            class Salud(http.server.BaseHTTPRequestHandler):
                def do_GET(self):
                    self.send_response(200 if self.path == '/acceso-temporal/salud' else 404)
                    self.send_header('Content-Length', '0')  # sin él, curl ve EOF TLS sin close_notify (56)
                    self.end_headers()

                def log_message(self, *args):
                    pass

            httpd = ServidorSilencioso(('127.0.0.1', 0), Salud)
            httpd.socket = contexto.wrap_socket(httpd.socket, server_side=True)
            hilo = threading.Thread(target=httpd.serve_forever, daemon=True)
            hilo.start()
            try:
                # Interpolación de Compose: $$ -> $, ${FORM_HEALTH_PATH:-x} -> x; rutas y puerto del entorno de prueba.
                cmd = (self.comando().replace('$$', '$').replace('${FORM_HEALTH_PATH:-/acceso-temporal/salud}',
                                                                 '/acceso-temporal/salud')
                       .replace('/run/pki', str(pki)).replace(':8443', f':{httpd.server_port}'))
                r = subprocess.run(['sh', '-c', cmd], env={'PATH': os.environ['PATH'], 'FORM_HOSTNAME': 'localhost'},
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)
                return r.returncode
            finally:
                httpd.shutdown()
                httpd.server_close()

        self.assertEqual(probar('hc-origen', pk.servidor(pk.origen, 'a'), pk.origen), 0)
        self.assertEqual(probar('hc-interna', pk.servidor(pk.interna, 'b'), None), 0)
        self.assertNotEqual(probar('hc-productivo-sin-origin', pk.servidor(pk.origen, 'c'), None), 0)
        self.assertNotEqual(probar('hc-origin-incompatible', pk.servidor(pk.interna, 'd'), pk.origen), 0)




PROPS_PRODUCCION = ('publico.recepcion.habilitada=true\nformulario.recepcion.habilitada=true\n'
                    'publico.integracion.habilitada=true\npublico.recepcion.custodia-durable-autorizada=true\n')


class ValidacionConPullTest(unittest.TestCase):
    """Regresión DDN-UBUNTU 15/11/2026: con operation_control.pull_enabled=1 la validación (que cerraba la
    integración por propiedad) no arrancaba. Ahora conserva la integración y cierra /integracion y /operacion."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.base = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def props(self, texto):
        p = self.base / 'application.properties'
        p.write_text(texto)
        return p

    def test_entorno_de_validacion_no_cierra_integracion_y_activa_el_filtro(self):
        self.assertNotIn('PUBLICO_INTEGRACION_HABILITADA', f.CERRADO)
        self.assertEqual(f.CERRADO['FORMULARIO_VALIDACION_CERRADA'], 'true')
        for clave in ['PUBLICO_RECEPCION_HABILITADA', 'FORMULARIO_RECEPCION_HABILITADA', 'PUBLICO_NNA_HABILITADO',
                      'PISEE_SRCEI_ENABLED', 'CICLOVIDA_ONLINEHABILITADO', 'CICLOVIDA_ELIMINACION_HABILITADA']:
            self.assertEqual(f.CERRADO[clave], 'false', clave)

    def test_arranque_con_pull_abierto_y_cerrado(self):
        props = self.props(PROPS_PRODUCCION)
        for aceptar, pull in [(True, True), (True, False), (False, True), (False, False)]:
            informe, bloqueos = f.arranque_validacion(props, {'aceptarNuevos': aceptar, 'recuperar': pull})
            self.assertEqual(bloqueos, [], (aceptar, pull))
            self.assertTrue(informe['integracion'])

    def test_integracion_cerrada_en_properties_con_pull_abierto_bloquea_el_plan(self):
        props = self.props(PROPS_PRODUCCION.replace('publico.integracion.habilitada=true', 'publico.integracion.habilitada=false'))
        self.assertTrue(f.arranque_validacion(props, {'aceptarNuevos': False, 'recuperar': True})[1])
        self.assertEqual(f.arranque_validacion(props, {'aceptarNuevos': True, 'recuperar': False})[1], [])  # custodia autorizada
        # Con la regla anterior (integración cerrada por entorno) la validación productiva no arrancaba:
        with patch.dict(f.CERRADO, {'PUBLICO_INTEGRACION_HABILITADA': 'false'}):
            self.assertTrue(f.arranque_validacion(self.props(PROPS_PRODUCCION), {'aceptarNuevos': True, 'recuperar': True})[1])

    def test_caddyfile_de_validacion_bloquea_antes_de_todo_handle(self):
        original = (ROOT / 'infra/ubuntu/Caddyfile.production').read_text()
        texto = f.caddyfile_validacion(original)
        bloque = texto.index('@validacion path /integracion /integracion/* /operacion /operacion/*')
        self.assertLess(texto.index('respond @private'), bloque)
        self.assertLess(bloque, texto.index('respond @validacion "Validación cerrada" 503'))
        self.assertLess(texto.index('respond @validacion'), texto.index('handle @bloque'))
        self.assertEqual(texto.replace(f.BLOQUE_CADDY_VALIDACION, ''), original)  # nada más cambia
        with self.assertRaises(f.Rechazo):
            f.caddyfile_validacion(original.replace(f.ANCLA_CADDY, ''))

    def test_validacion_yml_monta_el_caddyfile_de_validacion(self):
        infra = self.base / 'infra'
        infra.mkdir()
        (infra / 'Caddyfile').write_text((ROOT / 'infra/ubuntu/Caddyfile.production').read_text())
        f.generar_validacion(self.base)
        yml = (infra / 'validacion.yml').read_text()
        self.assertIn('FORMULARIO_VALIDACION_CERRADA: "true"', yml)
        self.assertNotIn('PUBLICO_INTEGRACION_HABILITADA', yml)
        self.assertIn('volumes: !override', yml)
        self.assertIn('${FORMULARIO_RAIZ}/infra/Caddyfile.validacion:/etc/caddy/Caddyfile:ro', yml)
        self.assertIn('${FORMULARIO_RAIZ}/pki/proxy:/run/pki:ro', yml)
        self.assertIn('respond @validacion', (infra / 'Caddyfile.validacion').read_text())

    def test_produccion_rechaza_la_marca_de_validacion(self):
        self.assertTrue(f.app_en_validacion({'Config': {'Env': ['A=1', 'FORMULARIO_VALIDACION_CERRADA=true']}}))
        self.assertFalse(f.app_en_validacion({'Config': {'Env': ['PUBLICO_INTEGRACION_HABILITADA=true']}}))
        self.assertFalse(f.app_en_validacion(None))

    def test_sonda_directa_a_la_app_exige_503_con_cabecera(self):
        cerrada = 'HTTP/1.1 503 \r\nx-formulario-validacion: cerrada\r\ncache-control: no-store\r\n\r\n'
        with patch.object(f, 'run', return_value=cerrada):
            self.assertEqual(f.sondear_app_validacion('p')[1], [])
        for respuesta in ['HTTP/1.1 401 \r\n\r\n', 'HTTP/1.1 503 \r\n\r\n', 'HTTP/1.1 200 \r\n\r\n', '']:
            with patch.object(f, 'run', return_value=respuesta):
                self.assertEqual(len(f.sondear_app_validacion('p')[1]), len(f.RUTAS_CERRADAS_VALIDACION), respuesta)
        llamadas = []
        with patch.object(f, 'run', side_effect=lambda args, **k: llamadas.append(args) or cerrada):
            f.sondear_app_validacion('p')
        self.assertFalse(any('Authorization' in ' '.join(a) for a in llamadas))  # nunca envía credenciales


@unittest.skipUnless(shutil.which('openssl'), 'openssl requerido')
class SondaProxyValidacionTest(unittest.TestCase):
    """Sonda HTTP real (TLS estricto) contra servidores que emulan el proxy de validación."""

    def servidor(self, responder):
        import http.server
        import threading
        pk = TlsProxyTest('run')
        pk.setUp()
        self.addCleanup(pk.tearDown)
        raiz = pk.base / 'raiz'
        (raiz / 'pki').mkdir(parents=True)
        pki = pk.directorio_proxy('raiz/pki/proxy', pk.servidor(pk.origen, 's'), pk.origen)
        contexto = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        contexto.load_cert_chain(str(pki / 'server.crt'), str(pki / 'server.key'))

        class Manejador(http.server.BaseHTTPRequestHandler):
            def responder(self):
                n = int(self.headers.get('Content-Length') or 0)
                self.rfile.read(n)
                estado, cabeceras = responder(self.command, self.path)
                self.send_response(estado)
                for k, v in cabeceras.items():
                    self.send_header(k, v)
                self.send_header('Content-Length', '0')
                self.end_headers()
            do_GET = do_POST = do_PUT = responder

            def log_message(self, *args):
                pass

        httpd = ServidorSilencioso(('127.0.0.1', 0), Manejador)
        httpd.socket = contexto.wrap_socket(httpd.socket, server_side=True)
        threading.Thread(target=httpd.serve_forever, daemon=True).start()
        self.addCleanup(httpd.server_close)
        self.addCleanup(httpd.shutdown)
        return raiz, httpd.server_port

    @staticmethod
    def caddy(metodo, ruta):
        cerrada = ruta.lower().startswith(('/integracion', '/operacion'))
        return (503, {}) if cerrada else (200, {})

    def test_proxy_que_bloquea_pasa(self):
        raiz, puerto = self.servidor(self.caddy)
        resultados, problemas = f.sondear_proxy_validacion(raiz, HOST, puerto)
        self.assertEqual(problemas, [])
        self.assertEqual(resultados['POST /integracion/v3/formulario-completo/entregas/reclamar'], 503)

    def test_proxy_que_deja_pasar_integracion_falla(self):
        # Sin bloqueo en el proxy: la solicitud llega a la app (503 CON cabecera) o a un backend abierto (200/401).
        for respuesta in [(503, {'X-Formulario-Validacion': 'cerrada'}), (401, {}), (200, {})]:
            raiz, puerto = self.servidor(lambda m, r, x=respuesta: x if r.lower().startswith('/integracion') else (200, {}))
            problemas = f.sondear_proxy_validacion(raiz, HOST, puerto)[1]
            self.assertTrue(any('/integracion' in p for p in problemas), respuesta)

    def test_proxy_sin_salud_o_inaccesible_falla(self):
        raiz, puerto = self.servidor(lambda m, r: (502, {}))
        self.assertIn('sin salud', ' '.join(f.sondear_proxy_validacion(raiz, HOST, puerto)[1]))
        self.assertIn('inaccesible', ' '.join(f.sondear_proxy_validacion(raiz, HOST, 1)[1]))


@unittest.skipUnless(os.environ.get('FORMULARIO_PROXY_IMAGE') and shutil.which('docker') and shutil.which('openssl'),
                     'FORMULARIO_PROXY_IMAGE (imagen Caddy del proxy), docker y openssl requeridos')
class CaddyValidacionHttpTest(unittest.TestCase):
    """Caddy REAL con el Caddyfile de validación derivado del productivo: /integracion y /operacion 503 incluso con
    credencial; el resto se encamina al backend (502 aquí: sin app)."""

    def test_caddy_real_bloquea_integracion_y_operacion(self):
        pk = TlsProxyTest('run')
        pk.setUp()
        self.addCleanup(pk.tearDown)
        # FORM_HOSTNAME no puede ser 'localhost' (Caddy rechaza el host repetido en @wrongHost): hostname real + --resolve.
        pki = pk.directorio_proxy('pki', pk.servidor(pk.origen, 's'), pk.origen)
        for archivo in pki.iterdir():
            archivo.chmod(0o644)
        caddy = pk.base / 'Caddyfile'
        caddy.write_text(f.caddyfile_validacion((ROOT / 'infra/ubuntu/Caddyfile.production').read_text()))
        caddy.chmod(0o644)
        rutas = [m + ' ' + r for m, r in f.RUTAS_CERRADAS_VALIDACION] + [
            'GET /integracion', 'GET /integracion/', 'GET /Integracion/v1/comunicaciones/pendientes',
            'GET /integracion%2Fv1/comunicaciones/pendientes', 'GET /operacion', 'GET /api/session']
        guion = ('caddy run --config /etc/caddy/Caddyfile --adapter caddyfile >/dev/null 2>&1 & '
                 'for i in $(seq 50); do curl -s -o /dev/null --cacert /run/pki/origin-ca.crt https://localhost:8443/ && break; sleep 0.2; done; '
                 + ' '.join(f'printf "%s|" {shlex_quote(r)}; curl -s -o /tmp/b -w "%{{http_code}}|" --cacert /run/pki/origin-ca.crt '
                            f'-X {r.split()[0]} -H "Authorization: Bearer integracion-sintetica-validacion-cerrada-2026" '
                            f'--path-as-is --resolve {HOST}:8443:127.0.0.1 https://{HOST}:8443{r.split()[1]}; head -c 40 /tmp/b; echo;' for r in rutas))
        r = subprocess.run(['docker', 'run', '--rm', '--network', 'none', '--user', '0:0', '-e', f'FORM_HOSTNAME={HOST}',
                            '-e', 'XDG_CONFIG_HOME=/tmp', '-e', 'XDG_DATA_HOME=/tmp',
                            '-v', f'{caddy}:/etc/caddy/Caddyfile:ro', '-v', f'{pki}:/run/pki:ro',
                            '--entrypoint', 'sh', os.environ['FORMULARIO_PROXY_IMAGE'], '-c', guion],
                           stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=120)
        filas = dict(l.split('|', 1) for l in r.stdout.decode().splitlines() if '|' in l)
        self.assertEqual(len(filas), len(rutas), r.stdout.decode() + r.stderr.decode())
        for ruta, valor in filas.items():
            estado, cuerpo = valor.split('|', 1)
            if ruta == 'GET /api/session':
                self.assertEqual(estado, '502', ruta)  # se encamina a la app (ausente): no bloqueado
            else:
                self.assertEqual(estado, '503', f'{ruta}: {valor}')
                if '/operacion' not in ruta.lower():
                    self.assertIn('Validaci', cuerpo, ruta)


def shlex_quote(texto):
    import shlex
    return shlex.quote(texto)

class PaqueteReleaseTest(unittest.TestCase):
    def test_docs_operativos_del_paquete_incluyen_los_que_enlazan(self):
        """El paquete 7395a3f omitía GUIA_OPERATIVA_PRODUCCION.md aunque REINSTALACION la enlaza."""
        import re
        spec_p = importlib.util.spec_from_file_location('empaquetar', ROOT / 'scripts/linux/empaquetar-release.py')
        paquete = importlib.util.module_from_spec(spec_p)
        spec_p.loader.exec_module(paquete)
        docs = {i for i in paquete.INCLUDE if i.startswith('docs/')}
        self.assertIn('docs/GUIA_OPERATIVA_PRODUCCION.md', docs)
        for doc in docs:
            self.assertTrue((ROOT / doc).is_file(), doc)
        # El procedimiento de reinstalación (y lo que enlaza) debe poder seguirse solo con el paquete.
        for doc in ['docs/REINSTALACION_LIMPIA_FORMULARIO.md', 'docs/GUIA_OPERATIVA_PRODUCCION.md']:
            for enlace in re.findall(r'\]\(([A-Z0-9_]+\.md)(?:#[^)]*)?\)', (ROOT / doc).read_text()):
                self.assertIn('docs/' + enlace, docs, f'{doc} enlaza {enlace}, que no viaja en el paquete')


if __name__ == '__main__':
    unittest.main()
