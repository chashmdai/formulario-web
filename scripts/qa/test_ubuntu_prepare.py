#!/usr/bin/env python3
"""Guardas de preparación; no Docker, datos ciudadanos ni proveedores."""
import importlib.util,os,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('ubuntu_prepare',ROOT/'scripts/ubuntu/preparar.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
class PrepareTest(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.base=Path(self.temp.name)
 def tearDown(self):self.temp.cleanup()
 def test_destination_must_be_new(self):
  with self.assertRaises(ValueError):module.new_destination(self.base)
  self.assertEqual(module.new_destination(self.base/'new'),self.base/'new')
 def test_never_prepare_inside_checkout(self):
  (self.base/'.git').mkdir()
  with self.assertRaises(ValueError):module.new_destination(self.base/'new')
 def test_symlink_parent_is_rejected(self):
  (self.base/'real').mkdir();(self.base/'link').symlink_to(self.base/'real',target_is_directory=True)
  with self.assertRaises(ValueError):module.new_destination(self.base/'link/new')
 def test_credential_must_be_private_regular_and_bounded(self):
  file=self.base/'secret';file.write_text('synthetic');file.chmod(0o600)
  self.assertEqual(module.private_source(file),file)
  file.chmod(0o644)
  with self.assertRaises(ValueError):module.private_source(file)
  file.chmod(0o600);file.write_bytes(b'x'*65537)
  with self.assertRaises(ValueError):module.private_source(file)
 def test_secret_link_is_rejected(self):
  file=self.base/'secret';file.write_text('synthetic');file.chmod(0o600)
  link=self.base/'link';link.symlink_to(file)
  with self.assertRaises(ValueError):module.private_source(link)
 def test_default_publication_does_not_enable_retirement(self):
  props=(ROOT/'infra/ubuntu/application.properties.example').read_text()
  self.assertIn('ciclo-vida.online-habilitado=false',props)
  self.assertIn('ciclo-vida.eliminacion.habilitada=false',props)
  self.assertIn('PISEE_SRCEI_ENABLED=false',props)
  self.assertIn('formulario.recepcion.habilitada=false',props)
if __name__=='__main__':unittest.main()
