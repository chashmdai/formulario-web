import importlib.util,tempfile,unittest,json,types,os,time
from unittest.mock import patch
from pathlib import Path
spec=importlib.util.spec_from_file_location('media_archive',Path(__file__).resolve().parents[1]/'linux/media-archive.py');archive=importlib.util.module_from_spec(spec);spec.loader.exec_module(archive)
class BackupSafety(unittest.TestCase):
 def test_identificadores_no_aceptan_sql(self):
  for value in ['x; DROP DATABASE x','a/b','../x','a-b','']:
   with self.assertRaises(ValueError):archive.identifier(value)
 def test_ruta_git_y_enlace_se_rechazan(self):
  with tempfile.TemporaryDirectory() as root:
   p=Path(root);(p/'.git').mkdir()
   with self.assertRaises(ValueError):archive.safe(p/'store')
  with tempfile.TemporaryDirectory() as root:
   p=Path(root);(p/'link').symlink_to(p/'target')
   with self.assertRaises(ValueError):archive.safe(p/'link')
 def test_restore_no_sobrescribe_destino(self):
  with tempfile.TemporaryDirectory() as root:
   p=Path(root)
   with self.assertRaises(ValueError):archive.restore(types.SimpleNamespace(source=str(p),destination=str(p),database='nuevo'))
 def test_sql_corrupto_se_rechaza_antes_de_crear_base_o_destino(self):
  with tempfile.TemporaryDirectory() as root:
   p=Path(root);(p/'database.sql').write_text('contenido modificado');(p/'manifest.json').write_text(json.dumps({'version':1,'sqlSha256':'0'*64}))
   with self.assertRaises(ValueError):archive.restore(types.SimpleNamespace(source=str(p),destination=str(p/'nuevo'),database='nuevo'))
   self.assertFalse((p/'nuevo').exists())
 def test_sanea_solo_incoming_antiguo_con_aplicacion_detenida(self):
  with tempfile.TemporaryDirectory() as root:
   p=Path(root);(p/'incoming').mkdir();(p/'objects').mkdir();old=p/'incoming/carga-001.part';old.write_text('temporal');os.utime(old,(time.time()-90000,)*2);recent=p/'incoming/carga-002.part';recent.write_text('actual');original=p/'objects/original';original.write_text('conservar')
   args=types.SimpleNamespace(store=str(p),hours=24,apply=False);archive.clean_incoming(args);self.assertTrue(old.exists());args.apply=True;archive.clean_incoming(args);self.assertFalse(old.exists());self.assertTrue(recent.exists());self.assertEqual(original.read_text(),'conservar')
class LifecycleBackupSafety(unittest.TestCase):
 def test_v9_distingue_retirados_de_ausentes_y_v8_sigue_verificable(self):
  for version in [8,9]:
   queries=[]
   def fake(args,sql):
    queries.append(sql)
    if 'information_schema.columns' in sql:return ['1' if version==9 else '0']
    if 'SELECT COUNT(*)' in sql:return ['0']
    return []
   with tempfile.TemporaryDirectory() as root,patch.object(archive,'command',side_effect=fake):
    result=archive.verify_database(types.SimpleNamespace(), 'sintetica',Path(root))
   self.assertEqual(result['comunicaciones'],0)
   selects=[q for q in queries if 'storage_kind' in q]
   self.assertTrue(all(('bytes_retired=FALSE' in q)==(version==9) for q in selects if 'storage_kind' in q and ('storage_key' in q or 'content IS NULL' in q)))
   self.assertTrue(any("released=FALSE" in q for q in queries))
 def test_original_vigente_ausente_sigue_bloqueando_respaldo(self):
  def fake(args,sql):
   if 'information_schema.columns' in sql:return ['1']
   if 'SELECT id,expected_size' in sql:return ['00000000-0000-0000-0000-000000000001\t10\t'+'0'*64]
   return []
  with tempfile.TemporaryDirectory() as root,patch.object(archive,'command',side_effect=fake):
   with self.assertRaises(ValueError):archive.verify_database(types.SimpleNamespace(),'sintetica',Path(root))
 def test_marca_de_retirada_sin_intencion_no_oculta_un_original_perdido(self):
  def fake(args,sql):
   if 'information_schema.columns' in sql:return ['1']
   if 'LEFT JOIN' in sql and 'storage_retirement' in sql:return ['1']
   return ['0']
  with tempfile.TemporaryDirectory() as root,patch.object(archive,'command',side_effect=fake):
   with self.assertRaises(ValueError):archive.verify_database(types.SimpleNamespace(),'sintetica',Path(root))
if __name__=='__main__':unittest.main()
