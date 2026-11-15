#!/usr/bin/env python3
"""Actualización desde la versión instalada (c16d135): el procedimiento solo usa opciones que
existen en la herramienta que invoca, el respaldo previo no depende ni modifica `release/`, y un
respaldo de la herramienta instalada se verifica y restaura con la nueva. Sin Docker real ni /srv.
"""
import contextlib
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
NEW = ROOT / 'scripts/ubuntu'
OLD = ROOT / 'scripts/qa/fixtures/instalado-c16d135'
DOC = ROOT / 'docs/ACTUALIZACION_UBUNTU_PRODUCCION.md'
INSTALLED_COMMIT = 'c16d135690dce07e1e3a52b12d83fd71fa9650fa'
SCRIPTS = ['respaldo.py', 'respaldo-programado.py', 'productor.py', 'preflight-actualizacion.py',
           'simulacro-restauracion.py', 'empaquetar-release.py']
SUBCOMMANDS = {'backup', 'restore', 'estimate', 'verify', 'close', 'open', 'install-ack', 'export-public',
               'export-credential', 'origen-lab', 'restaurar'}

FAKE_DOCKER = r'''#!/usr/bin/env python3
import json, os, sys
args = sys.argv[1:]
if args[0] == 'ps':
    print('fixture-mysql fixture-app')
elif args[0] == 'inspect':
    print(json.dumps([{'Id': 'fixture-mysql', 'Config': {'Labels': {'com.docker.compose.service': 'mysql'}},
                       'State': {'Running': True}, 'Mounts': []},
                      {'Id': 'fixture-app', 'Config': {'Labels': {'com.docker.compose.service': 'app'}},
                       'State': {'Running': True}}]))
elif args[0] in ('stop', 'start'):
    pass
elif args[0] == 'exec' and 'mysqldump' in args:
    sys.stdout.write('-- SINTETICO\nCREATE TABLE fixture (id INTEGER);\n')
elif args[0] == 'exec' and 'mysql' in args:
    print('1024')
else:
    sys.exit(90)
'''


def load(name, path, extra_path=None):
    if extra_path:
        sys.path.insert(0, str(extra_path))
        sys.modules.pop('productor', None)
    try:
        spec = importlib.util.spec_from_file_location(name, path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module
    finally:
        if extra_path:
            sys.path.remove(str(extra_path))


def documented_invocations():
    """Comandos de las herramientas Python citados en el procedimiento (bloques y código en línea)."""
    text = DOC.read_text().replace('\\\n', ' ')
    candidates = re.findall(r'```bash\n(.*?)```', text, re.S)
    candidates += re.findall(r'`([^`\n]*\.py[^`\n]*)`', text)
    commands = []
    for block in candidates:
        for line in block.splitlines():
            if not any(s in line for s in SCRIPTS) or 'python3' not in line:
                continue
            for part in re.split(r'&&|;|\|', line):
                if 'python3' in part and any(s in part for s in SCRIPTS):
                    commands.append(part.strip())
    return commands


def help_text(script, subcommand=None):
    args = [sys.executable, str(script)] + ([subcommand] if subcommand else []) + ['--help']
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, cwd=script.parent)
    return result.returncode, result.stdout + result.stderr


class InstalledVersionTest(unittest.TestCase):
    def test_fixture_is_the_installed_version(self):
        for line in (OLD / 'SHA256SUMS').read_text().splitlines():
            digest, name = line.split()
            self.assertEqual(hashlib.sha256((OLD / name).read_bytes()).hexdigest(), digest, name)
            git = subprocess.run(['git', '-C', str(ROOT), 'show', f'{INSTALLED_COMMIT}:scripts/ubuntu/{name}'],
                                 stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
            if git.returncode == 0:  # La CI clona sin historial; localmente se contrasta con Git.
                self.assertEqual(git.stdout, (OLD / name).read_bytes(), name)

    def test_installed_tools_lack_the_new_options(self):
        code, text = help_text(OLD / 'respaldo-programado.py')
        self.assertEqual(code, 0)
        for flag in ['--directory', '--keep', '--key-file']:
            self.assertNotIn(flag, text)
        for sub in ['estimate', 'verify']:
            self.assertNotEqual(help_text(OLD / 'respaldo.py', sub)[0], 0, sub)

    def test_every_documented_command_is_accepted_by_the_version_it_invokes(self):
        commands = documented_invocations()
        self.assertGreaterEqual(len(commands), 6)
        for command in commands:
            with self.subTest(command=command):
                tokens = shlex.split(command.replace('"', '').replace('<', '').replace('>', ''), posix=True)
                path = next(t for t in tokens if any(t.endswith(s) for s in SCRIPTS))
                name = Path(path).name
                installed = '$R/release/' in path or path.startswith('/srv/')
                if installed:
                    self.assertTrue((OLD / name).is_file(), 'La versión instalada no tiene ' + name)
                    script = OLD / name
                else:
                    script = NEW / name if (NEW / name).is_file() else ROOT / 'scripts/linux' / name
                rest = tokens[tokens.index(path) + 1:]
                sub = next((t for t in rest if t in SUBCOMMANDS), None)
                code, text = help_text(script, sub)
                self.assertEqual(code, 0, f'{script.relative_to(ROOT)} no admite el subcomando {sub}')
                main_text = help_text(script)[1] if sub else ''
                for flag in [t.split('=')[0] for t in rest if t.startswith('--')]:
                    self.assertTrue(flag in text or flag in main_text, f'{flag} no existe en {script.relative_to(ROOT)}')

    def test_backup_before_update_never_uses_installed_tools_for_new_options(self):
        doc = DOC.read_text()
        self.assertNotRegex(doc, r'\$R/release/scripts/ubuntu/respaldo\.py["\s]+(estimate|verify)')
        self.assertNotRegex(doc, r'\$R/release/scripts/ubuntu/respaldo-programado\.py["\s]+--root\s+"?\$R"?\s+--')
        order = [doc.index(marker) for marker in ['respaldo-programado.py" --root "$R" --directory',
                                                  'copia-externa-ok', '**Solo ahora** actualizar `release/`']]
        self.assertEqual(order, sorted(order), 'Respaldo y copia externa deben preceder a la modificación de release/')


class PackageBackupTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='qa-actualizacion-')
        base = Path(self.temp.name)
        self.root = base / 'produccion'
        installed = self.root / 'release/scripts/ubuntu'
        installed.mkdir(parents=True)
        for name in ['respaldo.py', 'respaldo-programado.py', 'productor.py']:
            shutil.copy2(OLD / name, installed / name)
        self.package = base / 'paquete/fuente/scripts/ubuntu'
        self.package.mkdir(parents=True)
        for name in ['respaldo.py', 'respaldo-programado.py', 'productor.py']:
            shutil.copy2(NEW / name, self.package / name)
        self.directory = base / 'respaldos'
        self.key = base / 'clave'
        self.key.write_bytes(b'SINTETICO' * 8)
        self.key.chmod(0o600)

    def tearDown(self):
        self.temp.cleanup()

    def tree(self, path):
        return {str(p.relative_to(path)): hashlib.sha256(p.read_bytes()).hexdigest() for p in path.rglob('*') if p.is_file()}

    def test_package_scheduler_uses_its_sibling_and_leaves_release_untouched(self):
        scheduled = load('programado_paquete', self.package / 'respaldo-programado.py', self.package)
        before = self.tree(self.root / 'release')
        calls = []

        class FakeProductor:
            def __init__(self, path):
                self.root = Path(path)
                self.meta = {'project': 'ddn-formulario-produccion'}

            def request(self, method, path, kind, data=None, headers=None):
                if method == 'GET':
                    return 200, {'etag': '"4"'}, json.dumps({'aceptarNuevos': True, 'recuperar': False, 'revision': 4}).encode()
                return 200, {}, json.dumps(dict(data, revision=5)).encode()

            def compose(self, *args):
                calls.append(('compose',) + args)

        def fake_run(command, **kwargs):
            calls.append(tuple(command))
            if command[2] == 'backup':
                archive = Path(command[command.index('--output') + 1])
                archive.write_bytes(b'CIFRADO-SINTETICO')
                archive.with_suffix('.gpg.sha256').write_text(hashlib.sha256(b'CIFRADO-SINTETICO').hexdigest() + '\n')
            return types.SimpleNamespace(returncode=0)

        with patch.object(scheduled, 'Productor', FakeProductor), patch.object(scheduled.os, 'geteuid', return_value=0), \
                patch.object(scheduled.subprocess, 'run', side_effect=fake_run), contextlib.redirect_stdout(io.StringIO()):
            scheduled.main(['--root', str(self.root), '--directory', str(self.directory), '--key-file', str(self.key)])
        tools = [c for c in calls if c[0] == 'python3']
        self.assertEqual([c[2] for c in tools], ['backup', 'verify'])
        for command in tools:
            self.assertEqual(Path(command[1]), self.package / 'respaldo.py')
            self.assertNotIn(str(self.root / 'release'), ' '.join(command))
        self.assertEqual(before, self.tree(self.root / 'release'))
        record = json.loads((self.root / 'backup-last-success.json').read_text())
        self.assertTrue(record['fileManifestVerified'] and record['previousControlRestored'])


@unittest.skipUnless(shutil.which('gpg'), 'gpg requerido')
class InstalledArchiveCompatibilityTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='qa-formato-')
        base = Path(self.temp.name)
        self.origin = base / 'origen'
        files = {'config/application.properties': 'sintetico=true\n', 'secrets/acceso-temporal-password': 'SINTETICO',
                 'keys/acceso-temporal-password.hash': 'pbkdf2-v5_8:00', 'keys/receipt-trust.json': '{}',
                 'pki/app/ca.crt': 'CERT', 'mysql/root.cnf': '[client]', 'store/objeto': 'DOCUMENTO-SINTETICO',
                 'acceso-temporal/limites.json': '{}', 'compose.env': 'X=1\n', 'installation.json': '{}',
                 'pisee/runtime/nodo': 'NODO', 'pisee/state/estado': 'ESTADO', 'pisee/config-produccion.json': '{}'}
        for name, value in files.items():
            path = self.origin / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(value)
            path.chmod(0o600)
        self.files = files
        self.bin = base / 'bin'
        self.bin.mkdir()
        (self.bin / 'docker').write_text(FAKE_DOCKER)
        (self.bin / 'docker').chmod(0o700)
        self.key = base / 'clave'
        self.key.write_text('SINTETICO-' * 6)
        self.key.chmod(0o600)
        self.archive = base / 'respaldos/formulario-anterior.gpg'
        self.archive.parent.mkdir()
        self.env = dict(os.environ, PATH=f'{self.bin}{os.pathsep}{os.environ["PATH"]}')

    def tearDown(self):
        self.temp.cleanup()

    def run_tool(self, script, *args):
        return subprocess.run([sys.executable, str(script), *args], env=self.env, cwd=script.parent,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

    def test_archive_from_installed_tool_verifies_and_restores_with_new_tool(self):
        made = self.run_tool(OLD / 'respaldo.py', 'backup', '--private-dir', str(self.origin), '--project', 'fixture',
                             '--output', str(self.archive), '--key-file', str(self.key), '--maintenance',
                             '--pisee-private-dir', str(self.origin / 'pisee/runtime'),
                             '--pisee-node-config', str(self.origin / 'pisee/config-produccion.json'),
                             '--pisee-state-dir', str(self.origin / 'pisee/state'))
        self.assertEqual(made.returncode, 0, made.stdout + made.stderr)
        verified = self.run_tool(NEW / 'respaldo.py', 'verify', '--archive', str(self.archive), '--key-file', str(self.key))
        self.assertEqual(verified.returncode, 0, verified.stdout + verified.stderr)
        target = self.archive.parent / 'restaurado'
        restored = self.run_tool(NEW / 'respaldo.py', 'restore', '--destination', str(target),
                                 '--archive', str(self.archive), '--key-file', str(self.key))
        self.assertEqual(restored.returncode, 0, restored.stdout + restored.stderr)
        for name, value in self.files.items():
            if name.startswith('pisee/'):
                name = name.replace('pisee/runtime/', 'pisee/')
            self.assertEqual((target / name).read_text(), value, name)
        self.assertIn('CREATE TABLE fixture', (target / 'database.sql').read_text())
        tampered = self.archive.read_bytes()
        self.archive.write_bytes(tampered[:-40] + bytes(40))
        self.assertNotEqual(self.run_tool(NEW / 'respaldo.py', 'verify', '--archive', str(self.archive),
                                          '--key-file', str(self.key)).returncode, 0)


if __name__ == '__main__':
    unittest.main()
