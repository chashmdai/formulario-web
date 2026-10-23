#!/usr/bin/env python3
"""Solo bootstrap de revisión: CU real configurada pero bloqueada, recepción cerrada."""
import argparse
import os
from pathlib import Path
import re

p = argparse.ArgumentParser()
p.add_argument("credenciales_sandbox", type=Path)
p.add_argument("destino", type=Path)
p.add_argument("--puerto", type=int, default=18080)
args = p.parse_args()
if not 1024 <= args.puerto <= 65535:
    raise SystemExit("Elegir un puerto de revisión no privilegiado")
target = args.destino.expanduser().resolve()
if target.exists() or any((d / ".git").exists() for d in [target, *target.parents]):
    raise SystemExit("Elegir un directorio privado NUEVO fuera de Git")
source = args.credenciales_sandbox.read_text(encoding="utf-8-sig")
values = []
for label in ["Client ID SANDBOX", "Client Secret SANDBOX"]:
    match = re.search(r"(?im)^\s*" + re.escape(label) + r"\s*:\s*([^\r\n]+)", source)
    if match is None or not re.fullmatch(r"[A-Za-z0-9._~+-]{10,256}", match[1].strip()):
        raise SystemExit("No se pudo leer el par Sandbox; no se seleccionan QA/producción")
    values.append(match[1].strip())
os.umask(0o077)
target.mkdir(parents=True, mode=0o700)
props = "\n".join([
    "siger.claveunica.client-id=" + values[0], "siger.claveunica.client-secret=" + values[1],
    "siger.claveunica.environment=SANDBOX", "siger.claveunica.callback-approved=false",
    "siger.claveunica.redirect-uri=https://claveunica.formularioadulto.defensorianinez.gob.cl/redirect",
    "siger.claveunica.logout-uri=https://claveunica.formularioadulto.defensorianinez.gob.cl/logout",
    f"siger.claveunica.public-origin=http://localhost:{args.puerto}", "server.address=0.0.0.0", "publico.nna.habilitado=false",
    "publico.recepcion.habilitada=false", "publico.recepcion.aviso-validado=false", "PISEE_SRCEI_ENABLED=false", ""
])
(target / "application.properties").write_text(props, encoding="utf-8")
print("Bootstrap Sandbox preparado con callback bloqueado y recepción cerrada. Sin identidad simulada.")
