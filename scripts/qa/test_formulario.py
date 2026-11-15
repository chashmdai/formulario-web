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



class FakeInstalacion:
    """Instalación nueva simulada: MySQL activo o no, huellas controladas y registro de acciones."""
    def __init__(self, raiz, mysql_activo=True, huellas=None, escritores=('p', 'a')):
        self.raiz, self.proyecto, self.acciones = Path(raiz), 'formulario', []
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
        self.eventos = []
        self.patches = [patch.object(f, 'detener', side_effect=lambda ids: self.eventos.append(('detener', list(ids)))),
                        patch.object(f, 'arrancar_ids', side_effect=lambda ids: self.eventos.append(('arrancar', list(ids)))),
                        patch.object(f, 'legado_arriba', side_effect=lambda *x, **k: self.eventos.append(('legado',)) or 'ok'),
                        patch.object(f, 'unidad_nueva', return_value='no-instalada'),
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

if __name__ == '__main__':
    unittest.main()
