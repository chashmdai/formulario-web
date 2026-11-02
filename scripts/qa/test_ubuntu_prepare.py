#!/usr/bin/env python3
"""Guardas de preparación; no Docker, datos ciudadanos ni proveedores."""
import argparse,importlib.util,json,os,tempfile,unittest
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
 def inputs(self,**overrides):
  values=dict(sin_claveunica=True,sin_confianza_ack=True,cu_client_id=None,cu_secret=None,institutional_trust=None)
  values.update(overrides);return argparse.Namespace(**values)
 def test_explicit_closed_modes_need_no_credentials_or_invented_trust(self):
  self.assertEqual(module.installation_inputs(self.inputs()),([],None))
 def test_enabled_modes_require_real_input_files(self):
  for values in [dict(sin_claveunica=False),dict(sin_confianza_ack=False)]:
   with self.assertRaises(ValueError):module.installation_inputs(self.inputs(**values))
 def test_conflicting_modes_rejected(self):
  for values in [dict(cu_client_id='anything'),dict(cu_secret='anything'),dict(institutional_trust='anything')]:
   with self.assertRaises(ValueError):module.installation_inputs(self.inputs(**values))
 def test_empty_ack_map_is_not_authentic_trust(self):
  file=self.base/'trust';file.write_text('{}');file.chmod(0o600)
  with self.assertRaises(ValueError):module.installation_inputs(self.inputs(sin_confianza_ack=False,institutional_trust=str(file)))
 def test_mysql_configuration_is_present_and_requires_verified_transport(self):
  config=(ROOT/'infra/ubuntu/mysql.cnf').read_text()
  self.assertIn('require_secure_transport=ON',config)
  for name in ['ca.crt','server.crt','server.key']:self.assertIn('/run/pki/'+name,config)
if __name__=='__main__':unittest.main()
