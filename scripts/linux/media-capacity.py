#!/usr/bin/env python3
"""Sondeo explícito de capacidad. Solo metadatos agregados; código 2 si requiere atención."""
import argparse,json,ssl,urllib.request
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--config',type=Path,required=True);p.add_argument('--ca',required=True);p.add_argument('--origin',required=True);a=p.parse_args()
if not a.origin.startswith('https://'):p.error('Origen HTTPS requerido')
props=dict(l.split('=',1) for l in a.config.read_text().splitlines() if '=' in l and not l.startswith('#'));context=ssl.create_default_context(cafile=a.ca)
request=urllib.request.Request(a.origin.rstrip('/')+'/operacion/multimedia',headers={'Authorization':'Bearer '+props['operacion.token']})
with urllib.request.urlopen(request,context=context,timeout=10) as response:result=json.load(response)
print(json.dumps(result));raise SystemExit(2 if result.get('alertaCuota') or result.get('alertaEspacio') else 0)
