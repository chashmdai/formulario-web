"""Guardas negativas: nunca conectan servicios, proveedores ni identidades reales."""
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/ubuntu'))
from productor import Productor
import transicion

class ProductorGuardTest(unittest.TestCase):
    def test_ack_con_misma_clave_y_otro_pem_no_es_identidad_independiente(self):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);(root/'keys').mkdir()
            key=Ed25519PrivateKey.generate().public_key().public_bytes(serialization.Encoding.PEM,serialization.PublicFormat.SubjectPublicKeyInfo).decode()
            (root/'keys/receipt-trust.json').write_text(json.dumps({'formulario':key}))
            (root/'keys/institutional-trust.json').write_text('{}')
            source=root/'windows.json';source.write_text(json.dumps({'otra-identidad':key+'\n'}))
            op=object.__new__(Productor);op.root=root
            op.request=lambda *a,**k:(200,{},b'{"aceptarNuevos":false,"recuperar":false}')
            op.compose=lambda *a: self.fail('No debe reiniciar al rechazar una clave')
            with self.assertRaises(ValueError):op.install_ack(source,hashlib.sha256(source.read_bytes()).hexdigest())
            self.assertEqual((root/'keys/institutional-trust.json').read_text(),'{}')

    def test_apertura_sin_aprobacion_no_cambia_configuracion(self):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);(root/'authorization').mkdir()
            (root/'authorization/open.approved.json').write_text(json.dumps({'receiptSignerApproved':False}))
            op=object.__new__(Productor);op.root=root
            op.config=lambda *a:self.fail('No debe cambiar configuración')
            with self.assertRaises(ValueError):op.open(True,True)

    def test_ensayo_no_puede_detener_certificacion(self):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);(root/'transition').mkdir()
            (root/'transition/plan.json').write_text(json.dumps({'source':{'project':'ddn-formulario-institucional'},'publicPort':24443}))
            op=object.__new__(Productor);op.root=root
            with (patch.object(transicion,'Productor',return_value=op),
                  patch.object(transicion,'docker',side_effect=AssertionError('No tocar Docker')),
                  patch.object(sys,'argv',['transicion.py','--root',str(root),'apply','--test-only'])):
                with self.assertRaises(SystemExit):transicion.main()

if __name__=='__main__':unittest.main()
