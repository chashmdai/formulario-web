#!/usr/bin/env python3
"""Guardas del simulacro de recuperación: aislamiento, cierre y limpieza acotada. Sin Docker real."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('simulacro', ROOT / 'scripts/ubuntu/simulacro-restauracion.py')
simulacro = importlib.util.module_from_spec(spec)
spec.loader.exec_module(simulacro)


class SimulacroTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.work = Path(self.temp.name) / 'trabajo'

    def tearDown(self):
        self.temp.cleanup()

    def test_workdir_rejects_git_production_and_docker_storage(self):
        (Path(self.temp.name) / '.git').mkdir()
        with self.assertRaises(ValueError):
            simulacro.new_workdir(self.work)
        # Se rechazan sin consultar el disco (en CI /var/lib/docker no es legible).
        with patch.object(Path, 'exists', side_effect=PermissionError('sin acceso')):
            for forbidden in ['/srv/ddn-formulario-produccion/simulacro', '/var/lib/docker/simulacro', '/srv']:
                with self.assertRaises(ValueError):
                    simulacro.new_workdir(forbidden)

    def test_overlay_closes_everything_isolates_network_and_publishes_nothing(self):
        work = simulacro.new_workdir(self.work)
        project = simulacro.Proyecto(work, 'ddn-simulacro-prueba', work / 'restaurado', 'imagen:candidata', 'a' * 40)
        overlay = project.overlay.read_text()
        self.assertIn('depends_on: !reset', overlay)
        self.assertIn('salida: {internal: true}', overlay)
        for key in ['PUBLICO_RECEPCION_HABILITADA', 'FORMULARIO_RECEPCION_HABILITADA', 'PUBLICO_INTEGRACION_HABILITADA',
                    'PISEE_SRCEI_ENABLED', 'CICLOVIDA_ELIMINACION_HABILITADA', 'FORMULARIO_MINIMIZACIONBORRADORES_HABILITADA']:
            self.assertIn(f'{key}: "false"', overlay)
        self.assertNotIn('ports', overlay)
        env = project.env.read_text()
        self.assertIn('COMPOSE_PROJECT_NAME=ddn-simulacro-prueba', env)
        self.assertIn('FORM_PRIVATE_DIR=' + str(work / 'restaurado'), env)
        self.assertNotIn('/srv/', env)

    def test_down_removes_only_volumes_with_exact_project_label(self):
        work = simulacro.new_workdir(self.work)
        project = simulacro.Proyecto(work, 'ddn-simulacro-prueba', work / 'r', 'img', 'a' * 40)
        removed = []

        def fake(args, **kwargs):
            if args[:3] == ['docker', 'volume', 'ls']:
                return 'ddn-simulacro-prueba_mysql-data\notro-proyecto_mysql-data\n'
            if args[:3] == ['docker', 'volume', 'inspect']:
                return 'ddn-simulacro-prueba\n' if args[-1].startswith('ddn-simulacro-prueba_') else 'otro-proyecto\n'
            if args[:3] == ['docker', 'volume', 'rm']:
                removed.append(args[-1])
            return ''
        with patch.object(simulacro, 'run', side_effect=fake):
            project.down()
        self.assertEqual(removed, ['ddn-simulacro-prueba_mysql-data'])

    def test_compose_always_targets_own_project_and_env(self):
        work = simulacro.new_workdir(self.work)
        project = simulacro.Proyecto(work, 'ddn-simulacro-prueba', work / 'r', 'img', 'a' * 40)
        calls = []
        with patch.object(simulacro, 'run', side_effect=lambda args, **k: calls.append(args) or ''):
            project.compose('up', '-d', 'mysql')
        self.assertEqual(calls[0][:6], ['docker', 'compose', '--project-name', 'ddn-simulacro-prueba', '--env-file', str(project.env)])


if __name__ == '__main__':
    unittest.main()
