#!/usr/bin/env python3
"""Estado agregado del candidato local HTTPS. Sin imprimir tokens o datos ciudadanos."""
import argparse,json,re,subprocess
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('private',type=Path);p.add_argument('--hostname',default='formulario.defensorianinez.gob.cl');p.add_argument('--port',type=int,default=24443)
a=p.parse_args()
if not re.fullmatch(r'[a-z0-9.-]+',a.hostname) or not 1024<=a.port<=65535:raise SystemExit('Destino inválido')
root=a.private.absolute()
token=(root/'secrets/operations-token').read_text().strip()
if not re.fullmatch(r'[a-f0-9]{64}',token):raise SystemExit('Token operacional inesperado; no se imprime')
result={}
for name in ['control','custodia']:
    response=subprocess.run(['curl','--silent','--show-error','--fail','--max-time','15','--write-out','\n%{http_code}','--cacert',str(root/'pki/proxy/ca.crt'),'--resolve',f'{a.hostname}:{a.port}:127.0.0.1','--config','-',f'https://{a.hostname}:{a.port}/operacion/v1/{name}'],input=('header = "Authorization: Bearer '+token+'"\n').encode(),capture_output=True)
    body,_,code=response.stdout.rpartition(b'\n')
    if name=='custodia' and code==b'503':
        result[name]={'disponible':False,'motivo':'Mantenimiento online deshabilitado; no se activa para consultar métricas'};continue
    if response.returncode or code!=b'200':raise SystemExit('Consulta operacional fallida; no se imprime la respuesta')
    result[name]=json.loads(body)
print(json.dumps(result,ensure_ascii=False,indent=2))
