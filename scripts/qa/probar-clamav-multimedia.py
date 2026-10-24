#!/usr/bin/env python3
from pathlib import Path
import socket,struct,base64,io,json,tempfile,zipfile,os
import sys
assert '--autorizar-eicar' in sys.argv,'La prueba usa exclusivamente EICAR benigno y requiere el flag explícito.'
r=Path(__file__).resolve().parents[2];socketpath=os.environ['MULTIMEDIA_AV_SOCKET'];fixture=Path(os.environ['MULTIMEDIA_SYNTHETIC_DIR']);results=[]
def scan(file):
 with socket.socket(socket.AF_UNIX) as s:
  s.settimeout(90);s.connect(socketpath);s.sendall(b'zINSTREAM\0')
  while b:=file.read(65536):s.sendall(struct.pack('!I',len(b))+b)
  s.sendall(b'\0\0\0\0');response=bytearray()
  while not response.endswith(b'\0'):
   data=s.recv(512)
   if not data:break
   response.extend(data)
   assert len(response)<=512
  return response.rstrip(b'\0').decode()
eicar=base64.b64decode('WDVPIVAlQEFQWzRcUFpYNTQoUF4pN0NDKTd9JEVJQ0FSLVNUQU5EQVJELUFOVElWSVJVUy1URVNULUZJTEUhJEgrSCo=')
with (fixture/'imagen.png').open('rb') as f:clean=scan(f)
assert clean=='stream: OK';results.append({'caso':'PNG sintetico','resultado':'LIMPIO'})
response=scan(io.BytesIO(eicar));assert 'eicar' in response.lower() and response.endswith(' FOUND');results.append({'caso':'EICAR benigno','resultado':'DETECTADO'})
with tempfile.TemporaryDirectory(prefix='siger-av-prueba-') as private:
 path=Path(private)/'contenedor-sintetico.zip'
 with zipfile.ZipFile(path,'w',compression=zipfile.ZIP_STORED) as z:
  with z.open('relleno.txt','w') as f:
   for _ in range(12):f.write(b'A'*(1024*1024))
  z.writestr('control-final.com',eicar)
 with path.open('rb') as f:response=scan(f)
 assert 'eicar' in response.lower() and response.endswith(' FOUND');results.append({'caso':'EICAR al final de ZIP mayor de 12 MiB','tamanio':path.stat().st_size,'resultado':'DETECTADO'})
(r/'.local/evidencias/multimedia-clamav-real.json').write_text(json.dumps(results,indent=2));print(json.dumps(results))