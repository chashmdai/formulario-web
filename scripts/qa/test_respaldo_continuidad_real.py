"""GPG/TAR/restore reales sobre archivos/SQL sintéticos; dump Docker sustituido, sin servicios."""
import contextlib
import importlib.util
import io
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import types
import unittest
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('archive',ROOT/'scripts/ubuntu/respaldo.py');archive=importlib.util.module_from_spec(spec);spec.loader.exec_module(archive)

@unittest.skipUnless(shutil.which('gpg'),'GPG requerido para cifrado real')
class Roundtrip(unittest.TestCase):
    def test_cifrado_restauracion_y_rechazo_de_copia_alterada(self):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);origin=root/'synthetic';origin.mkdir();(origin/'store').mkdir()
            content=b'ORIGINAL EXCLUSIVAMENTE SINTETICO';(origin/'store/original.txt').write_bytes(content)
            (origin/'config').mkdir();(origin/'config/application.properties').write_text('SYNTHETIC_ONLY=true\n')
            runtime=root/'node';runtime.mkdir();state=root/'state';state.mkdir();node=root/'node-config.json';node.write_text('{}')
            key=root/'backup.key';key.write_bytes(os.urandom(40));key.chmod(0o600)
            sql=b'CREATE TABLE synthetic(id INT PRIMARY KEY); INSERT INTO synthetic VALUES(1);\n'
            original_run=subprocess.run
            def run(args,**kw):
                if args[:3]==['docker','exec','synthetic-mysql']:
                    kw['stdout'].write(sql);return types.SimpleNamespace(returncode=0)
                return original_run(args,**kw)
            args=types.SimpleNamespace(private_dir=str(origin),output=str(root/'backup.gpg'),key_file=str(key),project='synthetic-only',certification_readonly=False,maintenance=False,pisee_private_dir=str(runtime),pisee_node_config=str(node),pisee_state_dir=str(state))
            fake=[{'Id':'synthetic-mysql','Config':{'Labels':{'com.docker.compose.service':'mysql'}},'State':{'Running':True}}]
            with patch.object(archive,'containers',return_value=fake),patch.object(archive.subprocess,'run',side_effect=run),contextlib.redirect_stdout(io.StringIO()):archive.backup(args)
            self.assertNotIn(content,(root/'backup.gpg').read_bytes())
            restore=types.SimpleNamespace(archive=str(root/'backup.gpg'),key_file=str(key),destination=str(root/'restored'))
            with contextlib.redirect_stdout(io.StringIO()):archive.restore(restore)
            self.assertEqual((root/'restored/store/original.txt').read_bytes(),content)
            self.assertEqual((root/'restored/database.sql').read_bytes(),sql)
            with self.assertRaises(ValueError):archive.restore(restore)
            (root/'backup.gpg').write_bytes(b'CORRUPT SYNTHETIC COPY')
            restore.destination=str(root/'another')
            with self.assertRaises(ValueError):archive.restore(restore)
            self.assertFalse((root/'another').exists());self.assertEqual((origin/'store/original.txt').read_bytes(),content)

if __name__=='__main__':unittest.main()
