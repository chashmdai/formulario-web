#!/usr/bin/env python3
"""Operación expresamente autorizada: un único bloque en /etc/hosts WSL, con respaldo."""
import argparse
import datetime as dt
import os
from pathlib import Path

DOMAIN = b"claveunica.formularioadulto.defensorianinez.gob.cl"
BLOCK = b"# BEGIN SIGER LOCAL SANDBOX\n127.0.0.1 " + DOMAIN + b"\n# END SIGER LOCAL SANDBOX\n"
p = argparse.ArgumentParser()
p.add_argument("directorio_privado", type=Path)
p.add_argument("--retirar", action="store_true")
a = p.parse_args()
private = a.directorio_privado.expanduser().resolve()
if os.geteuid() != 0:
    p.error("Ejecutar con la elevación Linux normal autorizada")
if not private.is_dir() or private.stat().st_mode & 0o077 or any((d / ".git").exists() for d in [private, *private.parents]):
    p.error("Se requiere directorio existente 0700 fuera de Git")
hosts = Path("/etc/hosts")
data = hosts.read_bytes()
if a.retirar:
    if data.count(BLOCK) != 1:
        p.error("No hay un único bloque intacto; revisar sin restaurar todo el archivo")
    hosts.write_bytes(data.replace(BLOCK, b"", 1))
    print("Retirado únicamente el bloque SIGER de WSL; otras entradas preservadas")
elif BLOCK in data:
    if data.count(BLOCK) != 1:
        p.error("Bloque duplicado; revisar")
    print("Bloque SIGER ya presente; sin modificaciones")
elif DOMAIN in data:
    p.error("Entrada existente sin nuestro marcador; revisar antes de modificar")
else:
    os.umask(0o077)
    backup = private / ("wsl-hosts-before-" + dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ") + ".bin")
    with backup.open("xb") as f:
        f.write(data)
    backup.chmod(0o600)
    hosts.write_bytes(data + (b"" if not data or data.endswith(b"\n") else b"\n") + BLOCK)
    print("Respaldado y agregado solo el bloque SIGER en WSL")
