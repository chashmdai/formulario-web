#!/usr/bin/env python3
"""U8a: respaldo/restore y retención con datos sintéticos, Docker/GPG simulados en PATH.
No accede a /srv, contenedores reales, servicios ni proveedores gubernamentales.
"""
import contextlib
import hashlib
import importlib.util
import io
import json
import os
import stat
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import types
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/ubuntu/respaldo.py'
sys.path.insert(0, str(ROOT / 'scripts/ubuntu'))


def module(name, file):
    spec = importlib.util.spec_from_file_location(name, file)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


backup = module('backup_u8a', SCRIPT)
scheduled = module('scheduled_u8a', ROOT / 'scripts/ubuntu/respaldo-programado.py')
SQL = b'-- SYNTHETIC ONLY\nCREATE TABLE fixture (id INTEGER);\n'

DOCKER = r'''#!/usr/bin/env python3
import json, os, pathlib, sys
args = sys.argv[1:]
with open(os.environ['DOCKER_AUDIT'], 'a') as f:
    f.write(json.dumps(args) + '\n')
if args[0] == 'ps':
    print('fixture-mysql fixture-app')
elif args[0] == 'inspect':
    print(json.dumps([
        {'Id': 'fixture-mysql', 'Config': {'Labels': {'com.docker.compose.service': 'mysql'}},
         'State': {'Running': True}, 'Mounts': [{'Type': 'bind', 'Destination': '/var/lib/mysql',
         'Source': os.environ['FAKE_ORIGIN'] + '/mysql/data'}]},
        {'Id': 'fixture-app', 'Config': {'Labels': {'com.docker.compose.service': 'app'}},
         'State': {'Running': True}}]))
elif args[0] in ['stop', 'start']:
    pass
elif args[0] == 'exec' and args[2] == 'mysql':
    print('0\t0' if 'operation_control' in args[-1] else '1024')
elif args[0] == 'exec' and args[2] == 'mysqldump':
    path = next(pathlib.Path(os.environ['FAKE_ORIGIN']).parent.rglob('ddn-backup-*/database.sql'))
    assert path.name == 'database.sql'
    assert path.stat().st_mode & 0o777 == 0o600
    assert path.parent.stat().st_mode & 0o777 == 0o700
    assert set(p.name for p in path.parent.iterdir()) == {'gpg', 'database.sql'}
    sys.stdout.buffer.write(b'-- SYNTHETIC ONLY\nCREATE TABLE fixture (id INTEGER);\n')
    if os.environ.get('FAIL_DUMP'):
        sys.exit(17)
    if os.environ.get('OVERSIZED_DUMP'):
        for _ in range(272):
            sys.stdout.buffer.write(b'X' * 65536)
else:
    sys.exit(90)
'''

# Simulador de transporte en streaming, NO evidencia de criptografía AES real.
# Comprueba el comando y simula integridad autenticada al consumir hasta EOF.
GPG = r'''#!/usr/bin/env python3
import hashlib, os, pathlib, sys
args = sys.argv[1:]
if '--symmetric' in args:
    assert args[-2:] == ['--output', '-']
    assert args[args.index('--cipher-algo') + 1] == 'AES256'
    if not os.environ.get('FIXTURE_LEGACY'):
        home = pathlib.Path(args[args.index('--homedir') + 1])
        assert set(p.name for p in home.parent.iterdir()) == {'gpg', 'database.sql'}
    if os.environ.get('FAIL_GPG'):
        sys.stdin.buffer.read(64)
        sys.exit(19)
    out = sys.stdout.buffer
    out.write(b'FAKE_GPG_ONLY\0')
    digest = hashlib.sha256()
    while block := sys.stdin.buffer.read(65536):
        data = bytes(b ^ 0xa5 for b in block)
        digest.update(data)
        out.write(data)
    out.write(digest.digest())
elif '--decrypt' in args:
    with open(args[-1], 'rb') as source:
        assert source.read(14) == b'FAKE_GPG_ONLY\0'
        digest = hashlib.sha256()
        pending = b''
        while block := source.read(65536):
            pending += block
            if len(pending) > 32:
                data, pending = pending[:-32], pending[-32:]
                digest.update(data)
                sys.stdout.buffer.write(bytes(b ^ 0xa5 for b in data))
        if pending != digest.digest():
            sys.exit(23)
else:
    sys.exit(90)
'''

# El sandbox de esta sesión puede poner un marcador .git vacío en /tmp. Ocultar
# exclusivamente ese marcador en el proceso de QA permite probar rutas privadas
# sintéticas; la implementación y sus guardas de Git/enlaces no se modifican.
DRIVER = r'''
from pathlib import Path
import runpy, sys
from unittest.mock import patch
original = Path.exists
def exists(path):
    return False if path == Path('/tmp/.git') else original(path)
sys.argv = sys.argv[1:]
with patch.object(Path, 'exists', exists):
    runpy.run_path(sys.argv[0], run_name='__main__')
'''


def fixture_exists(path):
    return False if path == Path('/tmp/.git') else ORIGINAL_EXISTS(path)


ORIGINAL_EXISTS = Path.exists

class StreamingBackupTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='qa-u8a-')
        self.base = Path(self.temp.name)
        self.origin = self.base / 'origin'
        self.origin.mkdir(mode=0o700)
        self.output = self.base / 'fixture.gpg'
        self.key = self.base / 'key'
        self.key.write_bytes(b'SYNTHETIC_BACKUP_PASSWORD_0000000000000000')
        self.key.chmod(0o600)
        files = {
            'config/application.properties': b'synthetic=true\n',
            'secrets/acceso-temporal-password': b'SYNTHETIC_ONLY',
            'keys/acceso-temporal-password.hash': b'SYNTHETIC_HASH',
            'keys/acceso-temporal-apertura.json': b'{}',
            'keys/historical-signing-key': b'SYNTHETIC_HISTORICAL_SIGNER',
            'pki/authority/ca.key': b'SYNTHETIC_CA', 'cu/client': b'SYNTHETIC_CLIENT',
            'acceso-temporal/state': b'SYNTHETIC_ACCESS_STATE',
            'authorization/approval': b'{}', 'transition/state': b'{}',
            'mysql/root.cnf': b'SYNTHETIC_MYSQL_CONFIG', 'mysql/health.cnf': b'SYNTHETIC_HEALTH',
            'mysql/data/ibdata1': b'SYNTHETIC_REGENERABLE_DATADIR' * 40,
            'mysql-init/init.sql': b'-- SYNTHETIC INIT',
            'store/objects/synthetic-document': b'SYNTHETIC_DOCUMENT' * 100,
            'store/objects/backup-manifest.json': b'SYNTHETIC_NESTED_NAME',
            'compose.env': b'SYNTHETIC_ENV', 'clamav/main.cvd': b'SYNTHETIC_SIGNATURES' * 40,
            'clamav/config': b'SYNTHETIC_AV_CONFIG',
            'release/reproducible.bin': b'SYNTHETIC_RELEASE',
            'software/unidentified.bin': b'SYNTHETIC_UNKNOWN_SOFTWARE',
            'pisee/runtime/bin/node': b'SYNTHETIC_EXECUTABLE',
            'pisee/runtime/keys/identity': b'SYNTHETIC_PISEE_IDENTITY',
            'pisee/state/durable': b'SYNTHETIC_STATE',
            'pisee/config-produccion.json': b'{"synthetic":true}',
        }
        for name, content in files.items():
            file = self.origin / name
            file.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            file.write_bytes(content)
            file.chmod(0o600)
        (self.origin / 'pisee/runtime/bin/node').chmod(0o750)
        self.metadata = {'project': 'ddn-formulario-produccion', 'backupReproducibleFiles': {
            'release/reproducible.bin': hashlib.sha256(files['release/reproducible.bin']).hexdigest()}}
        self.write_metadata()
        self.bin = self.base / 'bin'
        self.bin.mkdir()
        for name, source in [('docker', DOCKER), ('gpg', GPG)]:
            file = self.bin / name
            file.write_text(source)
            file.chmod(0o700)
        self.audit = self.base / 'docker-audit.jsonl'
        self.env = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ['PATH'],
                        FAKE_ORIGIN=str(self.origin), DOCKER_AUDIT=str(self.audit))
        self.args = ['--private-dir', str(self.origin), '--project', 'fixture',
                     '--pisee-private-dir', str(self.origin / 'pisee/runtime'),
                     '--pisee-state-dir', str(self.origin / 'pisee/state'),
                     '--pisee-node-config', str(self.origin / 'pisee/config-produccion.json'),
                     '--output', str(self.output), '--reserve-bytes', '0']

    def tearDown(self):
        self.temp.cleanup()

    def write_metadata(self):
        (self.origin / 'installation.json').write_text(json.dumps(self.metadata))

    def call(self, command, *args, good=True, env=None):
        result = subprocess.run(['python3', '-c', DRIVER, str(SCRIPT), command, *args], env=env or self.env,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        if good:
            self.assertEqual(result.returncode, 0, result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0)
            self.assertNotIn('synthetic-document', result.stderr)
            self.assertNotIn('SYNTHETIC_', result.stderr)
        return result

    def create(self, **kwargs):
        return self.call('backup', *self.args, '--key-file', str(self.key), '--maintenance', **kwargs)

    def check_no_temporaries(self):
        for pattern in ['ddn-backup-*', 'ddn-verify-*', 'ddn-restore-*', '*.tar', '*.sql']:
            self.assertEqual(list(self.base.glob(pattern)), [], pattern)

    def test_backup_restore_streaming_manifest_categories_and_permissions(self):
        self.create()
        self.assertEqual(self.output.stat().st_mode & 0o777, 0o600)
        self.call('verify', '--archive', str(self.output), '--key-file', str(self.key))
        target = self.base / 'restored'
        self.call('restore', '--archive', str(self.output), '--key-file', str(self.key),
                  '--destination', str(target))
        manifest = json.loads((target / 'backup-manifest.json').read_text())
        self.assertEqual(backup.inventory(target), manifest['files'])
        for name in ['secrets/acceso-temporal-password', 'keys/acceso-temporal-password.hash',
                     'keys/acceso-temporal-apertura.json', 'acceso-temporal/state',
                     'keys/historical-signing-key', 'mysql/root.cnf', 'mysql/health.cnf',
                     'software/unidentified.bin', 'clamav/config', 'pisee/keys/identity',
                     'pisee/state/durable', 'pisee/config-produccion.json',
                     'authorization/approval', 'transition/state', 'store/objects/synthetic-document']:
            self.assertIn(name, manifest['files'])
        for name in ['mysql/data/ibdata1', 'clamav/main.cvd', 'release/reproducible.bin']:
            self.assertNotIn(name, manifest['files'])
        self.assertEqual((target / 'database.sql').read_bytes(), SQL)
        self.assertEqual((target / 'pisee/bin/node').stat().st_mode & 0o777, 0o750)
        if os.geteuid() == 0:
            self.assertEqual((target / 'secrets/acceso-temporal-password').stat().st_uid, 0)
            self.assertEqual((target / 'mysql/root.cnf').stat().st_uid, 999)
            self.assertEqual((target / 'pisee/bin/node').stat().st_uid, 1000)
        calls = [json.loads(line) for line in self.audit.read_text().splitlines()]
        self.assertEqual([c[0] for c in calls if c[0] in ['stop', 'start']], ['stop', 'start'])
        self.check_no_temporaries()

    def test_scheduled_end_to_end_verifies_and_retains_with_simulated_tools(self):
        # El programado usa su respaldo.py hermano; release/ no interviene (ver test_actualizacion_desde_anterior).
        directory = self.base / 'scheduled'
        directory.mkdir(mode=0o700)
        old = []
        for day in [1, 2]:
            path = directory / ('formulario-202611%02dT030000Z.gpg' % day)
            path.write_bytes(b'SYNTHETIC_PREVIOUS_CIPHERTEXT')
            sha = scheduled.digest(path)
            path.with_suffix('.gpg.sha256').write_text(sha + '\n')
            scheduled.mark_verified(path, sha)
            old.append(path)
        foreign = directory / 'foreign.gpg'
        foreign.write_bytes(b'SYNTHETIC_FOREIGN')
        events = []
        origin = self.origin
        class FakeProductor:
            def __init__(self, root):
                self.root, self.meta = origin, {'project': 'ddn-formulario-produccion'}
            def request(self, method, path, kind, data=None, headers=None):
                if method == 'GET':
                    return 200, {'etag': '"20"'}, json.dumps({
                        'revision': 20, 'aceptarNuevos': True, 'recuperar': False}).encode()
                events.append((data, headers))
                return 200, {}, json.dumps(dict(data, revision=21)).encode()
            def compose(self, *args):
                events.append(('compose', args))
        real_run = subprocess.run
        def run(command, **kwargs):
            return real_run(['python3', '-c', DRIVER, *command[1:]], env=self.env, **kwargs)
        with patch.object(scheduled, 'Productor', FakeProductor), \
                patch.object(scheduled.os, 'geteuid', return_value=0), \
                patch.object(scheduled.subprocess, 'run', side_effect=run), \
                patch.object(Path, 'exists', fixture_exists), contextlib.redirect_stdout(io.StringIO()):
            scheduled.main(['--root', str(self.origin), '--directory', str(directory),
                            '--key-file', str(self.key), '--reserve-bytes', '0', '--keep', '2'])
        record = json.loads((self.origin / 'backup-last-success.json').read_text())
        self.assertTrue(record['fileManifestVerified'])
        self.assertTrue(record['previousControlRestored'])
        self.assertEqual(record['retentionDeletesPerformed'], 1)
        self.assertFalse(old[0].exists())
        self.assertTrue(old[1].exists())
        self.assertTrue(foreign.exists())
        self.assertTrue(Path(record['archive']).is_file())
        self.assertEqual(events[-1][1], {'If-Match': '"21"'})
        self.assertEqual(list(directory.glob('ddn-*')), [])
        self.check_no_temporaries()

    def test_estimate_readonly_aggregate_and_default_reserve(self):
        args = self.args[:-2]  # reserva por defecto
        before = backup.inventory(self.origin)
        report = json.loads(self.call('estimate', *args).stdout)
        self.assertEqual(backup.inventory(self.origin), before)
        self.assertGreaterEqual(report['reserveBytes'], 10 * 2**30)
        self.assertGreater(report['bytesByCategory']['excludedRegenerable'], 0)
        self.assertGreater(report['bytesByCategory']['identity'], 0)
        self.assertEqual(report['requiredFreeBytes'],
                         __import__('math').ceil(report['estimatedArchiveBytes'] * 1.25)
                         + report['reserveBytes'] + report['sqlTemporaryBytes'])
        self.assertNotIn(str(self.origin), json.dumps(report))
        self.assertNotIn('synthetic-document', json.dumps(report))
        calls = self.audit.read_text()
        for word in ['mysqldump', 'stop', 'start']:
            self.assertNotIn(word, calls)
        self.assertFalse(self.output.exists())
        self.check_no_temporaries()

    def test_certification_stays_readonly_without_maintenance(self):
        (self.origin / 'config/application.properties').write_text('siger.certificacion-claveunica=true\n')
        args = [*self.args, '--key-file', str(self.key), '--certification-readonly']
        self.call('backup', *args, '--maintenance', good=False)
        self.call('backup', *args)
        self.assertNotIn('"stop"', self.audit.read_text())
        self.assertNotIn('"start"', self.audit.read_text())
        self.check_no_temporaries()

    def test_pisee_ignores_only_ephemeral_socket_and_preserves_durable_state(self):
        sock = self.origin / 'pisee/state/ipc.sock'
        sock.write_bytes(b'SYNTHETIC_SOCKET_STAND_IN')
        original = Path.lstat
        def fake_lstat(path, *args, **kwargs):
            return types.SimpleNamespace(st_mode=stat.S_IFSOCK) if path == sock else original(path, *args, **kwargs)
        ns = types.SimpleNamespace(private_dir=self.origin, certification_readonly=False,
            pisee_private_dir=self.origin / 'pisee/runtime', pisee_state_dir=self.origin / 'pisee/state',
            pisee_node_config=self.origin / 'pisee/config-produccion.json')
        with patch.object(Path, 'exists', fixture_exists), patch.object(Path, 'lstat', fake_lstat):
            entries, _ = backup.selection(ns, {'Mounts': []})
        self.assertNotIn('pisee/state/ipc.sock', entries)
        self.assertIn('pisee/state/durable', entries)

    def test_digest_absent_or_mismatch_never_excludes_software(self):
        self.metadata['backupReproducibleFiles'] = {'release/reproducible.bin': '0' * 64}
        self.write_metadata()
        ns = types.SimpleNamespace(private_dir=self.origin, certification_readonly=False,
            pisee_private_dir=self.origin / 'pisee/runtime', pisee_state_dir=self.origin / 'pisee/state',
            pisee_node_config=self.origin / 'pisee/config-produccion.json')
        with patch.object(Path, 'exists', fixture_exists):
            entries, _ = backup.selection(ns, {'Mounts': []})
        for name in ['release/reproducible.bin', 'software/unidentified.bin', 'mysql/data/ibdata1']:
            self.assertIn(name, entries)

    def test_prepared_origin_without_installation_json_keeps_everything(self):
        # Destino de preparar.py aún no instalado (p. ej. simulacro LAB): sin digests declarados.
        (self.origin / 'installation.json').unlink()
        ns = types.SimpleNamespace(private_dir=self.origin, certification_readonly=False,
            pisee_private_dir=self.origin / 'pisee/runtime', pisee_state_dir=self.origin / 'pisee/state',
            pisee_node_config=self.origin / 'pisee/config-produccion.json')
        with patch.object(Path, 'exists', fixture_exists):
            entries, _ = backup.selection(ns, {'Mounts': []})
        for name in ['release/reproducible.bin', 'software/unidentified.bin', 'compose.env']:
            self.assertIn(name, entries)

    def test_backup_rejects_symlinks_without_plaintext_or_service_changes(self):
        (self.origin / 'store/objects/link').symlink_to(self.origin / 'compose.env')
        self.create(good=False)
        self.assertFalse(self.output.exists())
        self.assertNotIn('"stop"', self.audit.read_text())
        self.check_no_temporaries()

    def test_capacity_rejects_before_dump_or_stop(self):
        self.call('backup', *self.args, '--reserve-bytes', str(2**63),
                  '--key-file', str(self.key), '--maintenance', good=False)
        calls = self.audit.read_text()
        self.assertNotIn('mysqldump', calls)
        self.assertNotIn('"stop"', calls)
        self.assertFalse(self.output.exists())
        self.check_no_temporaries()

    def test_dump_and_cipher_failures_cleanup_plaintext_and_restart_writers(self):
        for flag in ['FAIL_DUMP', 'FAIL_GPG', 'OVERSIZED_DUMP']:
            with self.subTest(flag=flag):
                self.create(good=False, env=dict(self.env, **{flag: '1'}))
                self.assertFalse(self.output.exists())
                self.assertFalse(self.output.with_suffix('.gpg.sha256').exists())
                self.check_no_temporaries()
                calls = [json.loads(line) for line in self.audit.read_text().splitlines()]
                self.assertEqual(calls[-1], ['start', 'fixture-app'])

    def old_archive(self, members=None, corrupt_manifest=False, nested_legacy=False):
        content = b'SYNTHETIC_OLD_CONTENT'
        payload = io.BytesIO()
        with tarfile.open(fileobj=payload, mode='w') as tar:
            info = tarfile.TarInfo('config/old')
            info.mode, info.size = 0o7777, len(content)
            info.uid, info.gid = 12345, 12345
            tar.addfile(info, io.BytesIO(content))
            for entry in members or []:
                tar.addfile(entry)
            if nested_legacy:
                info = tarfile.TarInfo('store/backup-manifest.json')
                info.size = len(content)
                tar.addfile(info, io.BytesIO(content))
            manifest = {'version': 1, 'files': {
                'config/old': '0' * 64 if corrupt_manifest else hashlib.sha256(content).hexdigest()}}
            data = json.dumps(manifest).encode()
            info = tarfile.TarInfo('backup-manifest.json')
            info.size = len(data)
            tar.addfile(info, io.BytesIO(data))
        home = self.base / 'old-gpg'
        home.mkdir(mode=0o700, exist_ok=True)
        result = subprocess.run([str(self.bin / 'gpg'), '--no-options', '--homedir', str(home), '--batch',
            '--yes', '--pinentry-mode', 'loopback', '--passphrase-file', str(self.key),
            '--symmetric', '--cipher-algo', 'AES256', '--output', '-'],
            input=payload.getvalue(), stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            env=dict(self.env, FIXTURE_LEGACY='1'))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.output.write_bytes(result.stdout)
        self.output.with_suffix('.gpg.sha256').write_text(backup.sha256(self.output) + '\n')

    def test_restore_old_uncompressed_encrypted_format_and_safe_modes(self):
        self.old_archive(nested_legacy=True)
        target = self.base / 'old-restored'
        self.call('restore', '--archive', str(self.output), '--key-file', str(self.key),
                  '--destination', str(target))
        self.assertEqual((target / 'config/old').read_bytes(), b'SYNTHETIC_OLD_CONTENT')
        self.assertEqual((target / 'config/old').stat().st_mode & 0o7777, 0o755)
        self.call('restore', '--archive', str(self.output), '--key-file', str(self.key),
                  '--destination', str(target), good=False)
        self.check_no_temporaries()

    def test_full_decryption_rejects_damaged_gpg_even_with_updated_sidecar(self):
        self.create()
        raw = bytearray(self.output.read_bytes())
        raw[-10] ^= 1
        self.output.write_bytes(raw)
        self.output.with_suffix('.gpg.sha256').write_text(backup.sha256(self.output) + '\n')
        self.call('verify', '--archive', str(self.output), '--key-file', str(self.key), good=False)
        self.check_no_temporaries()

    def test_verification_and_restore_reject_bad_manifest(self):
        self.old_archive(corrupt_manifest=True)
        self.call('verify', '--archive', str(self.output), '--key-file', str(self.key), good=False)
        self.call('restore', '--archive', str(self.output), '--key-file', str(self.key),
                  '--destination', str(self.base / 'bad-restored'), good=False)
        self.assertFalse((self.base / 'bad-restored').exists())
        self.check_no_temporaries()

    def test_restore_rejects_links_devices_absolute_and_traversal(self):
        for index, kind in enumerate(['link', 'hardlink', 'device', 'absolute', 'traversal']):
            with self.subTest(kind=kind):
                entry = tarfile.TarInfo({'absolute': '/escape', 'traversal': '../escape'}.get(kind, 'unsafe'))
                entry.type = {'link': tarfile.SYMTYPE, 'hardlink': tarfile.LNKTYPE,
                              'device': tarfile.CHRTYPE}.get(kind, tarfile.DIRTYPE)
                entry.linkname = 'config/old'
                self.old_archive([entry])
                dest = self.base / ('unsafe-' + str(index))
                self.call('restore', '--archive', str(self.output), '--key-file', str(self.key),
                          '--destination', str(dest), good=False)
                self.assertFalse(dest.exists())
                self.check_no_temporaries()


class ScheduledBackupTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='qa-u8a-scheduled-')
        self.base = Path(self.temp.name)
        self.directory = self.base / 'backups'
        self.directory.mkdir()

    def tearDown(self):
        self.temp.cleanup()

    def archive(self, day, own=True):
        path = self.directory / ('formulario-202611%02dT030000Z.gpg' % day)
        path.write_bytes(b'SYNTHETIC_ENCRYPTED_FIXTURE_' + str(day).encode())
        sha = scheduled.digest(path)
        path.with_suffix('.gpg.sha256').write_text(sha + '\n')
        if own:
            scheduled.mark_verified(path, sha)
        return path

    def test_retention_off_by_default_keep_two_never_deletes_foreign_or_current(self):
        first, second, current = [self.archive(day) for day in [1, 2, 3]]
        foreign = self.archive(4, own=False)
        other = self.directory / 'foreign.gpg'
        other.write_bytes(b'SYNTHETIC_FOREIGN')
        before = set(self.directory.iterdir())
        self.assertEqual(scheduled.retain(self.directory, current, None), 0)
        self.assertEqual(set(self.directory.iterdir()), before)
        self.assertEqual(scheduled.retain(self.directory, current, 2), 1)
        self.assertFalse(first.exists())
        self.assertFalse(first.with_suffix('.gpg.sha256').exists())
        self.assertFalse(scheduled.marker(first).exists())
        for file in [second, current, foreign, other]:
            self.assertTrue(file.exists())
        self.assertEqual(scheduled.retain(self.directory, current, 2), 0)
        with self.assertRaises(ValueError):
            scheduled.retain(self.directory, current, 1)

    def test_retention_protects_latest_verified_even_if_future_names_exist(self):
        current, future, newest = [self.archive(day) for day in [1, 2, 3]]
        self.assertEqual(scheduled.retain(self.directory, current, 2), 1)
        self.assertTrue(current.exists())
        self.assertTrue(newest.exists())
        self.assertFalse(future.exists())

    def test_retention_ignores_links_invalid_sidecars_and_unverified_current(self):
        first, second, current = [self.archive(day) for day in [1, 2, 3]]
        first.with_suffix('.gpg.sha256').write_text('0' * 64)
        scheduled.marker(second).unlink()
        scheduled.marker(second).symlink_to(scheduled.marker(current))
        with self.assertRaises(ValueError):
            scheduled.retain(self.directory, first, 2)
        self.assertEqual(scheduled.retain(self.directory, current, 2), 0)
        self.assertTrue(first.exists())
        self.assertTrue(second.exists())

    def run_scheduled(self, fail=None, changed_cas=False, retention_fails=False):
        root = self.base / 'origin'
        root.mkdir()
        key = self.base / 'key'
        key.write_bytes(b'SYNTHETIC_ONLY' * 4)
        key.chmod(0o600)
        events = []
        prior = {'aceptarNuevos': True, 'recuperar': False, 'revision': 7}
        class FakeProductor:
            def __init__(self, path):
                self.root = Path(path)
                self.meta = {'project': 'ddn-formulario-produccion'}
            def request(self, method, path, kind, data=None, headers=None):
                events.append((method, data, headers))
                if method == 'GET':
                    return 200, {'etag': '"7"'}, json.dumps(prior).encode()
                if data['aceptarNuevos'] and changed_cas:
                    return 412, {}, b''
                return 200, {}, json.dumps(dict(data, revision=8)).encode()
            def compose(self, *args):
                events.append(('compose', args))
        def fake_run(command, **kwargs):
            mode = command[2]
            events.append((mode,))
            if mode == fail:
                return types.SimpleNamespace(returncode=1)
            if mode == 'backup':
                archive = Path(command[command.index('--output') + 1])
                archive.write_bytes(b'SYNTHETIC_NEW_CIPHERTEXT')
                archive.with_suffix('.gpg.sha256').write_text(scheduled.digest(archive) + '\n')
            return types.SimpleNamespace(returncode=0)
        args = ['--root', str(root), '--directory', str(self.directory), '--key-file', str(key), '--keep', '2']
        with patch.object(scheduled, 'Productor', FakeProductor), patch.object(scheduled.os, 'geteuid', return_value=0), \
                patch.object(scheduled.subprocess, 'run', side_effect=fake_run), \
                patch.object(Path, 'exists', fixture_exists), contextlib.redirect_stdout(io.StringIO()):
            if fail or changed_cas:
                with self.assertRaises(ValueError):
                    scheduled.main(args)
            elif retention_fails:
                with patch.object(scheduled, 'retain', side_effect=ValueError('/ruta/sensible')), \
                        self.assertRaises(scheduled.RetentionFailed):
                    scheduled.main(args)
            else:
                scheduled.main(args)
        return root, events

    def test_scheduled_verifies_before_retention_and_restores_exact_cas(self):
        first, second = [self.archive(day) for day in [1, 2]]
        root, events = self.run_scheduled()
        record = json.loads((root / 'backup-last-success.json').read_text())
        self.assertEqual(record['retentionDeletesPerformed'], 1)
        self.assertIs(type(record['retentionDeletesPerformed']), int)
        self.assertTrue(record['fileManifestVerified'])
        self.assertFalse(first.exists())
        self.assertTrue(second.exists())
        puts = [e for e in events if e[0] == 'PUT']
        self.assertFalse(puts[0][1]['aceptarNuevos'])
        self.assertEqual(puts[-1][2], {'If-Match': '"8"'})
        self.assertTrue(puts[-1][1]['aceptarNuevos'])
        self.assertLess(events.index(('verify',)), next(i for i, e in enumerate(events) if e[0] == 'compose'))

    def test_failed_capacity_or_verification_never_retains_or_reopens(self):
        for mode in ['backup', 'verify']:
            with self.subTest(mode=mode), tempfile.TemporaryDirectory(prefix='qa-u8a-fail-') as temp:
                original = self.base
                self.base = Path(temp)
                for file in self.directory.iterdir():
                    file.unlink()
                first, second = [self.archive(day) for day in [1, 2]]
                root, events = self.run_scheduled(fail=mode)
                self.assertFalse((root / 'backup-last-success.json').exists())
                self.assertTrue(first.exists())
                self.assertTrue(second.exists())
                self.assertEqual(len([e for e in events if e[0] == 'PUT']), 1)
                self.base = original

    def test_retention_failure_after_verified_backup_still_restores_cas_and_records(self):
        first, second = [self.archive(day) for day in [1, 2]]
        root, events = self.run_scheduled(retention_fails=True)
        record = json.loads((root / 'backup-last-success.json').read_text())
        self.assertTrue(record['fileManifestVerified'])
        self.assertTrue(record['previousControlRestored'])
        self.assertEqual(record['retentionDeletesPerformed'], 0)
        self.assertEqual(record['retentionError'], 'ValueError')
        self.assertNotIn('/ruta/sensible', json.dumps(record))
        puts = [e for e in events if e[0] == 'PUT']
        self.assertTrue(puts[-1][1]['aceptarNuevos'])
        self.assertTrue(first.exists() and second.exists())

    def test_scheduled_never_overwrites_changed_operator_cas(self):
        root, events = self.run_scheduled(changed_cas=True)
        self.assertFalse((root / 'backup-last-success.json').exists())
        self.assertEqual([e for e in events if e[0] == 'PUT'][-1][2], {'If-Match': '"8"'})

    def test_systemd_stays_opt_in_no_keep_or_install_changes(self):
        service = (ROOT / 'infra/ubuntu/ddn-formulario-produccion-backup.service').read_text()
        timer = (ROOT / 'infra/ubuntu/ddn-formulario-produccion-backup.timer').read_text()
        self.assertNotIn('--keep', next(line for line in service.splitlines() if line.startswith('ExecStart=')))
        self.assertEqual(timer.split('[Install]\n')[1], 'WantedBy=timers.target\n')


if __name__ == '__main__':
    unittest.main()
