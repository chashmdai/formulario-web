"""Pruebas sintéticas: no usa configuración, Docker ni conexiones productivas."""
import contextlib
import copy
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import patch
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT/'scripts/ubuntu'))
from continuidad import cambiar_pull
spec = importlib.util.spec_from_file_location('backup_continuidad', ROOT/'scripts/ubuntu/respaldo-programado.py')
backup = importlib.util.module_from_spec(spec); spec.loader.exec_module(backup)

class Op:
    def __init__(self, root, accept=True, pull=False):
        self.root=Path(root); self.meta={'project':'ddn-formulario-produccion'}
        self.state={'revision':7,'aceptarNuevos':accept,'recuperar':pull,'contingencia':'NORMAL'}
        self.puts=[]; self.conflict=False; self.unavailable=False; self.composes=[]
    def request(self, method, path, kind=None, data=None, headers=None):
        assert path=='/operacion/v1/control' and kind=='operations-token'
        if method=='GET':return 200,{'etag':'"'+str(self.state['revision'])+'"'},json.dumps(self.state).encode()
        self.puts.append((copy.deepcopy(data),copy.deepcopy(headers)))
        if self.unavailable:return 503,{},b''
        if self.conflict or headers['If-Match']!='"'+str(self.state['revision'])+'"':return 412,{},b''
        self.state={k:data[k] for k in ['aceptarNuevos','recuperar','contingencia']}|{'revision':self.state['revision']+1}
        return 200,{},json.dumps(self.state).encode()
    def compose(self,*args):self.composes.append(args)

class ContinuidadTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
        (self.root/'config').mkdir();(self.root/'keys').mkdir()
        (self.root/'config/application.properties').write_text('publico.integracion.preparada=true\npublico.integracion.habilitada=true\nformulario.acceso-temporal.expira=2026-12-15T00:00:00-03:00[America/Santiago]\n')
        raw=b'{"ACK_TEST":"PUBLIC SYNTHETIC FIXTURE"}';(self.root/'keys/institutional-trust.json').write_bytes(raw)
        self.sha=hashlib.sha256(raw).hexdigest();self.op=Op(self.root)
    def activate(self,**kw):
        return cambiar_pull(self.op,True,kw.get('revision',7),'CONTINUA-SYNTHETIC-01',kw.get('ack','ACK_TEST'),kw.get('sha',self.sha))
    def test_activacion_cas_solo_modifica_pull_y_no_archivos(self):
        before={p:p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        result=self.activate();self.assertTrue(result['control']['recuperar']);self.assertTrue(result['control']['aceptarNuevos'])
        self.assertEqual(before,{p:p.read_bytes() for p in before});self.assertFalse(self.op.composes)
    def test_repeticion_explicita_no_modifica_revision(self):
        self.activate();r=self.activate(revision=8);self.assertFalse(r['changed']);self.assertEqual(len(self.op.puts),1)
    def test_pausa_preserva_recepcion(self):
        self.activate();r=cambiar_pull(self.op,False,8,'INCIDENTE-SYNTHETIC-01');self.assertFalse(r['control']['recuperar']);self.assertTrue(r['control']['aceptarNuevos'])
    def test_cierre_ciudadano_no_se_sobre_escribe(self):
        self.op.state['aceptarNuevos']=False
        with self.assertRaises(ValueError):self.activate()
        self.assertFalse(self.op.puts)
    def test_contingencia_no_se_sobre_escribe(self):
        self.op.state['contingencia']='SOLO_CONSULTA'
        with self.assertRaises(ValueError):self.activate()
        self.assertFalse(self.op.puts)
    def test_revision_obsoleta_y_conflicto_concurrente(self):
        with self.assertRaises(ValueError):self.activate(revision=6)
        self.op.conflict=True
        with self.assertRaises(ValueError):self.activate()
        self.assertEqual(len(self.op.puts),1);self.assertFalse(self.op.state['recuperar'])
    def test_hash_o_identidad_incorrecta(self):
        for args in [{'sha':'0'*64},{'ack':'OTHER'}]:
            with self.assertRaises(ValueError):self.activate(**args)
        self.assertFalse(self.op.puts)
    def test_configuracion_cerrada_impide_activacion_pero_no_pausa(self):
        (self.root/'config/application.properties').write_text('publico.integracion.habilitada=false\n')
        self.op.state['recuperar']=True
        with self.assertRaises(ValueError):self.activate()
        self.assertFalse(cambiar_pull(self.op,False,7,'INCIDENTE-SYNTHETIC-01')['control']['recuperar'])
    def test_fallo_de_respuesta_no_reintenta_escritura(self):
        self.op.unavailable=True
        with self.assertRaises(ValueError):self.activate()
        self.assertEqual(len(self.op.puts),1)

class BackupContinuidadTest(unittest.TestCase):
    def scenario(self,*,pull=True,operator_change=False,failure=False,closed=False):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);op=Op(root,not closed,False if closed else pull)
            def run(args,**kw):
                if args[0]=='python3':
                    if operator_change:op.state.update(revision=op.state['revision']+1,recuperar=False)
                    if failure:return types.SimpleNamespace(returncode=1)
                    p=Path(args[args.index('--output')+1]);p.write_bytes(b'SYNTHETIC ENCRYPTED DOUBLE')
                    p.with_suffix('.gpg.sha256').write_text(hashlib.sha256(p.read_bytes()).hexdigest())
                return types.SimpleNamespace(returncode=0)
            with patch.object(backup.subprocess,'run',side_effect=run),patch.object(backup.shutil,'disk_usage',return_value=types.SimpleNamespace(free=32*2**30)),contextlib.redirect_stdout(io.StringIO()):
                if failure:
                    with self.assertRaises(ValueError):backup.run_backup(op,root/'copies',root/'key')
                    self.assertFalse(op.state['recuperar']);self.assertEqual(len(op.puts),1);return
                result=backup.run_backup(op,root/'copies',root/'key')
            self.assertEqual(op.state['recuperar'],False if operator_change or closed else pull)
            self.assertEqual(result['previousControlRestored'],not operator_change and not closed)
            self.assertEqual(result['operatorDecisionPreserved'],operator_change)
            if operator_change:self.assertEqual(op.state['revision'],9)
            if closed:self.assertFalse(op.puts)
            return result
    def test_respaldo_reanuda_ambos_controles_solo_si_cas_no_cambia(self):self.scenario()
    def test_respaldo_no_abre_pull_si_estaba_pausado(self):self.scenario(pull=False)
    def test_pausa_concurrente_prevalece_y_copia_se_registra(self):self.scenario(operator_change=True)
    def test_fallo_conserva_cierre(self):self.scenario(failure=True)
    def test_respaldo_de_instancia_cerrada_no_reabre(self):self.scenario(closed=True)
    def test_capacidad_insuficiente_conserva_cierre_y_no_inicia_copia(self):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);op=Op(root,True,True)
            with patch.object(backup.shutil,'disk_usage',return_value=types.SimpleNamespace(free=16*2**30-1)),patch.object(backup.subprocess,'run',side_effect=AssertionError('No copiar sin capacidad')):
                with self.assertRaises(ValueError):backup.run_backup(op,root/'copies',root/'key')
            self.assertFalse(op.state['aceptarNuevos']);self.assertFalse(op.state['recuperar'])
            self.assertFalse((root/'copies').exists())

if __name__=='__main__':unittest.main()
