#!/usr/bin/env python3
from pathlib import Path
import socket,json,hashlib,struct,time
import os
r=Path(__file__).resolve().parents[2];files=Path(os.environ['MULTIMEDIA_SYNTHETIC_DIR']);socketpath=os.environ['MULTIMEDIA_INSPECTOR_SOCKET'];result=[]
def inspect(path):
 with socket.socket(socket.AF_UNIX) as s:
  s.settimeout(60);s.connect(socketpath);s.sendall(json.dumps({'extension':path.suffix[1:],'tamanio':path.stat().st_size}).encode()+b'\n')
  with path.open('rb') as f:
   while b:=f.read(65536):s.sendall(b)
  data=bytearray()
  while not data.endswith(b'\n'):
   b=s.recv(65536)
   if not b:break
   data.extend(b)
   assert len(data)<3*1024*1024
  return json.loads(data)
valid=['imagen.png','imagen.jpg','imagen.jpeg','imagen.webp','documento.pdf','texto.txt','oficina.docx','oficina.odt','oficina.xlsx','oficina.pptx','audio.mp3','audio.m4a','audio.wav','audio.ogg','video.mp4','video.mov','video.webm']
invalid=['formula.xlsx','objeto.odt','enlace-falso.docx','externo.odt','activo.pdf','corrupto.pdf','macro.docx','externo.docx','traversal.docx','falso.png','corrupto.mp4','corrupto.mp3','falso.docx']
for name in valid+invalid:
 p=files/name;data=inspect(p);passed='error' not in data;result.append({'archivoSintetico':name,'aceptado':passed,'esperado':name in valid,'tipo':data.get('tipo'),'vista':data.get('vista'),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'tamanio':p.stat().st_size});print(name,':', 'ACEPTADO' if passed else 'RECHAZADO')
(r/'.local/evidencias/multimedia-inspector-real.json').write_text(json.dumps(result,indent=2));assert all(v['aceptado']==v['esperado'] for v in result),'Revisar formatos antes de habilitarlos'
print('Inspector real: todos los casos coinciden con su política.')