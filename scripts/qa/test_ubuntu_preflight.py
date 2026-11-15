#!/usr/bin/env python3
"""Preflight de actualización: sólo lectura, sin contenidos secretos. Árbol sintético, sin Docker."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('preflight', ROOT / 'scripts/ubuntu/preflight-actualizacion.py')
preflight = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preflight)
SECRET = 'VALOR-SECRETO-SINTETICO-NO-DEBE-APARECER'
PROPS = '\n'.join([
    'formulario.acceso-temporal.habilitado=true',
    'formulario.acceso-temporal.expira=2026-12-15T00:00:00-03:00[America/Santiago]',
    'formulario.acceso-temporal.hash-path=/run/keys/acceso-temporal-password.hash',
    'formulario.acceso-temporal.apertura-path=/run/keys/acceso-temporal-apertura.json',
    'formulario.acceso-temporal.limites-path=/var/lib/acceso-temporal/limites.json',
    'ciclo-vida.online-habilitado=false', ''])


class PreflightTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name) / 'produccion'
        files = {
            'installation.json': json.dumps({'project': 'ddn-formulario-produccion'}),
            'compose.env': 'COMPOSE_PROJECT_NAME=ddn-formulario-produccion\n',
            'config/application.properties': PROPS,
            'secrets/acceso-temporal-password': SECRET,
            'secrets/operations-token': SECRET + '-op',
            'keys/acceso-temporal-password.hash': 'pbkdf2-v5_8:' + 'ab' * 48,
            'keys/acceso-temporal-apertura.json': '{"authorized":false}',
            'keys/receipt-trust.json': '{"publico":"PUBLIC"}',
            'keys/receipt-key.pem': SECRET + '-firma',
            'pki/app/server.crt': 'CERT', 'mysql/root.cnf': SECRET + '-mysql',
            'acceso-temporal/limites.json': '{"windowEnds":0,"global":0,"clients":{}}',
            'pisee/runtime/nodo.bin': 'x', 'pisee/state/estado.db': 'y',
            'pisee/config-produccion.json': '{}', 'store/objetos/documento-ciudadano.pdf': 'PDF',
        }
        for name, value in files.items():
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(value)
            if name.startswith(('secrets/', 'keys/')):
                path.chmod(0o400)

    def tearDown(self):
        for path in self.root.rglob('*'):
            if path.is_file():
                path.chmod(0o600)
        self.temp.cleanup()

    def run_main(self, *args):
        out = io.StringIO()
        with contextlib.redirect_stdout(out), patch.object(preflight, 'timer_state', return_value='disabled'):
            code = preflight.main(['--root', str(self.root), '--min-free-gib', '0', *args])
        return code, out.getvalue()

    def test_complete_installation_passes_without_printing_secrets_or_citizen_names(self):
        code, output = self.run_main()
        self.assertEqual(code, 0, output)
        report = json.loads(output)
        self.assertEqual(report['blockers'], [])
        self.assertEqual(report['backupTimer'], 'disabled')
        self.assertEqual(report['temporaryAccess']['expira'], '2026-12-15T00:00:00-03:00[America/Santiago]')
        self.assertEqual(report['preserved']['counted']['store'], {'files': 1, 'bytes': 3})
        self.assertNotIn(SECRET, output)
        self.assertNotIn('documento-ciudadano', output)

    def assert_blocked_without(self, name, folder=False):
        path = self.root / name
        path.chmod(0o600)
        path.unlink()
        if folder:
            path.parent.rmdir()
        code, output = self.run_main()
        self.assertEqual(code, 1)
        self.assertTrue(any(name.split('/')[0] in b for b in json.loads(output)['blockers']))

    def test_missing_temporary_password_hash_blocks(self):
        self.assert_blocked_without('keys/acceso-temporal-password.hash')

    def test_missing_pisee_state_blocks(self):
        self.assert_blocked_without('pisee/state/estado.db', folder=True)

    def test_snapshot_is_private_new_file_and_compare_detects_identity_change(self):
        snap = Path(self.temp.name) / 'antes.json'
        code, _ = self.run_main('--snapshot', str(snap))
        self.assertEqual(code, 0)
        self.assertEqual(oct(snap.stat().st_mode & 0o777), '0o600')
        self.assertNotIn(SECRET, snap.read_text())
        # Nunca sobrescribe una instantánea previa.
        self.assertEqual(self.run_main('--snapshot', str(snap))[0], 2)
        code, output = self.run_main('--compare', str(snap))
        self.assertEqual(code, 0, output)
        self.assertTrue(json.loads(output)['comparison']['temporaryAccessUnchanged'])
        key = self.root / 'keys/receipt-key.pem'
        key.chmod(0o600)
        key.write_text('OTRA-IDENTIDAD')
        code, output = self.run_main('--compare', str(snap))
        self.assertEqual(code, 1)
        self.assertIn('keys/receipt-key.pem', json.dumps(json.loads(output)['blockers']))

    def test_limits_state_may_change_but_expiry_change_blocks(self):
        snap = Path(self.temp.name) / 'antes.json'
        self.run_main('--snapshot', str(snap))
        (self.root / 'acceso-temporal/limites.json').write_text('{"windowEnds":1,"global":1,"clients":{}}')
        self.assertEqual(self.run_main('--compare', str(snap))[0], 0)
        props = self.root / 'config/application.properties'
        props.write_text(PROPS.replace('2026-12-15', '2027-01-15'))
        code, output = self.run_main('--compare', str(snap))
        self.assertEqual(code, 1)
        self.assertIn('acceso temporal', json.dumps(json.loads(output)['blockers']))

    def test_custody_shrink_and_symlinks_are_rejected(self):
        snap = Path(self.temp.name) / 'antes.json'
        self.run_main('--snapshot', str(snap))
        (self.root / 'store/objetos/documento-ciudadano.pdf').unlink()
        code, output = self.run_main('--compare', str(snap))
        self.assertEqual(code, 1)
        self.assertNotIn('documento-ciudadano', output)
        (self.root / 'keys/enlace').symlink_to(self.root / 'keys/receipt-trust.json')
        self.assertEqual(self.run_main()[0], 2)

    def test_insufficient_space_blocks(self):
        out = io.StringIO()
        with contextlib.redirect_stdout(out), patch.object(preflight, 'timer_state', return_value='disabled'):
            code = preflight.main(['--root', str(self.root), '--min-free-gib', str(10 ** 9)])
        self.assertEqual(code, 1)
        self.assertIn('Espacio', out.getvalue())

    def test_never_writes_inside_installation(self):
        before = sorted(str(p) for p in self.root.rglob('*'))
        self.run_main()
        self.assertEqual(before, sorted(str(p) for p in self.root.rglob('*')))


if __name__ == '__main__':
    unittest.main()
