#!/usr/bin/env python3
"""Opt-in, H2 y ficheros sintéticos. Solo LEE un JAR SIGER existente; sin modificar ese repositorio."""
import argparse, hashlib, os, subprocess, zipfile, xml.etree.ElementTree as ET
from pathlib import Path

def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--siger-jar',required=True,type=Path)
 p.add_argument('--public-build',required=True,type=Path)
 p.add_argument('--work',required=True,type=Path,help='Directorio NUEVO privado, fuera de Git')
 a=p.parse_args();root=Path(__file__).resolve().parents[2];work=a.work.absolute()
 for part in [work,*work.parents]:
  if part.is_symlink() or (part/'.git').exists():p.error('Directorio fuera de Git y sin enlaces requerido')
 if work.exists():p.error('Directorio nuevo requerido')
 os.umask(0o077);work.mkdir();classes=work/'siger-classes';classes.mkdir();libs=work/'lib';libs.mkdir();out=work/'qa';out.mkdir()
 def digest(path):
  h=hashlib.sha256()
  with path.open('rb') as f:
   while b:=f.read(65536):h.update(b)
  return h.hexdigest()
 before=digest(a.siger_jar)
 with zipfile.ZipFile(a.siger_jar) as z:
  for name in z.namelist():
   if name.startswith('BOOT-INF/classes/') and not name.endswith('/'):
    rel=Path(name[len('BOOT-INF/classes/'):])
    if rel.is_absolute() or '..' in rel.parts:raise ValueError('JAR inválido')
    dest=classes/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(z.read(name))
 public_jar=a.public_build/'formulario-publico-0.1.0-SNAPSHOT.jar'
 with zipfile.ZipFile(public_jar) as z:
  for name in z.namelist():
   if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):(libs/Path(name).name).write_bytes(z.read(name))
 report=ET.parse(a.public_build/'surefire-reports/TEST-cl.nexbyte.siger.robustez.CicloVidaCustodiaTest.xml')
 test_cp=next(v.attrib['value'] for v in report.findall('.//property') if v.attrib.get('name')=='java.class.path')
 cp=os.pathsep.join(map(str,[a.public_build/'classes',a.public_build/'test-classes',classes,libs/'*',test_cp]))
 env={k:v for k,v in os.environ.items() if not k.startswith('ROBUST_DB_')}
 subprocess.run(['javac','-cp',cp,'-d',str(out),str(root/'scripts/qa/CustodiaSigerIndependiente.java')],check=True,env=env)
 subprocess.run(['java','-Xmx512m','-cp',str(out)+os.pathsep+cp,'cl.nexbyte.siger.robustez.CustodiaSigerIndependiente'],check=True,env=env)
 assert before==digest(a.siger_jar),'El JAR institucional no debe cambiar'
 print('JAR_SIGER_SIN_MODIFICACIONES sha256='+before)
if __name__=='__main__':main()