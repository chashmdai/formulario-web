"""Fixtures sintéticos: no consulta proveedores, contenedores ni configuración institucional."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
def module(name):
    spec=importlib.util.spec_from_file_location(name,ROOT/'scripts/ubuntu'/name)
    m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

class Rc1OperationsTest(unittest.TestCase):
    def test_config_ip_exacta_no_modifica_credenciales_originales(self):
        m=module('preparar-config-pisee.py')
        with tempfile.TemporaryDirectory() as t:
            source=Path(t)/'original.json';target=Path(t)/'nuevo.json'
            original={'servidores':{'interno':{'seguridad':{'ipPermitidas':['127.0.0.1'],'cors':'SYNTHETIC'}}},'log':{},'credencial':'SYNTHETIC_ONLY'}
            source.write_text(json.dumps(original))
            m.preparar(source,target,'10.203.73.3','10.203.73.2','10.203.73.0/29')
            result=json.loads(target.read_text())
            self.assertEqual(original,json.loads(source.read_text()))
            self.assertEqual(['127.0.0.1','10.203.73.3'],result['servidores']['interno']['seguridad']['ipPermitidas'])
            self.assertEqual(original['credencial'],result['credencial'])
            self.assertEqual(0o400,target.stat().st_mode&0o777)
            with self.assertRaises(FileExistsError):m.preparar(source,target,'10.203.73.3','10.203.73.2','10.203.73.0/29')
            for bad in ['10.203.73.0','10.203.73.7','10.203.74.2','0.0.0.0/0']:
                with self.assertRaises(ValueError):m.preparar(source,Path(t)/'bad',bad,'10.203.73.2','10.203.73.0/29')

    def test_restore_refuse_destino_existente_y_clave_publica(self):
        m=module('respaldo.py')
        with tempfile.TemporaryDirectory() as t:
            p=Path(t)/'key';p.write_text('X'*64);p.chmod(0o644)
            with self.assertRaises(ValueError):m.key(p)
            p.chmod(0o400);self.assertEqual(p,m.key(p))
            (Path(t)/'link').symlink_to(p)
            with self.assertRaises(ValueError):m.key(Path(t)/'link')

if __name__=='__main__':unittest.main()
