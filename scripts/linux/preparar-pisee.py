#!/usr/bin/env python3
"""Prepara una COPIA privada Linux; nunca modifica originales ni copia SQLite Windows."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("origen", type=Path, help="Directorio institucional nodo ya extraído")
parser.add_argument("destino", type=Path, help="Directorio privado nuevo, fuera de Git")
args = parser.parse_args()
source = args.origen.resolve(strict=True)
target = args.destino.expanduser().resolve()
if target.exists():
    raise SystemExit("El destino ya existe; no se sobrescribe configuración ni estado")
for parent in [target, *target.parents]:
    if (parent / ".git").exists():
        raise SystemExit("El destino debe estar fuera de cualquier repositorio")
os.umask(0o077)
binary = source / "NodoV2"
header = binary.read_bytes()[:20]
if header[:6] != b"\x7fELF\x02\x01" or header[18:20] != b"\x3e\x00":
    raise SystemExit("Se requiere ELF64 Linux x86-64")
config = json.loads((source / "config.json").read_text(encoding="utf-8-sig"))
runtime = target / "runtime"
runtime.mkdir(parents=True, mode=0o700)
(target / "state").mkdir(mode=0o700)
shutil.copyfile(binary, runtime / "NodoV2")
(runtime / "NodoV2").chmod(0o700)
for name in ("cert.pem", "key.pem", "cacert.pem", "catalogo-cert.pem", "catalogo-key.pem", "certServer.pem",
             "certsX509/private.pem", "certsX509/public.pem"):
    src = (source / name).resolve(strict=True)
    if not src.is_relative_to(source):
        raise SystemExit("Ruta institucional fuera del directorio autorizado")
    dst = runtime / name
    dst.parent.mkdir(exist_ok=True, mode=0o700)
    shutil.copyfile(src, dst)
    dst.chmod(0o600)

# Verificar pares sin mostrar claves, sujetos, números personales ni respuestas.
def output(command):
    return subprocess.check_output(command, stderr=subprocess.DEVNULL)

for key, cert in [("key.pem", "cert.pem"), ("certsX509/private.pem", "certsX509/public.pem")]:
    private_public = output(["openssl", "pkey", "-in", str(runtime / key), "-pubout", "-outform", "DER"])
    pem_public = output(["openssl", "x509", "-in", str(runtime / cert), "-pubkey", "-noout"])
    public = subprocess.run(["openssl", "pkey", "-pubin", "-outform", "DER"], input=pem_public,
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=True).stdout
    if private_public != public:
        raise SystemExit("Par criptográfico no coincidente; no iniciar el nodo")
    subprocess.run(["openssl", "x509", "-in", str(runtime / cert), "-checkend", "0", "-noout"],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)

# Cambios acotados: rutas Linux, estado nuevo, registro mínimo y autenticación interna.
config["database"]["path"] = "/var/lib/pisee/nodo"
for section in [config["identificacion"]["pki"], config["identificacion"]["SRCeI"]["X509"]]:
    for key in ("privado", "publico"):
        rel = Path(section[key].replace("\\", "/").removeprefix("./"))
        if rel.is_absolute() or ".." in rel.parts or not (runtime / rel).is_file():
            raise SystemExit("Revisar ruta de certificado institucional antes de adaptarla")
        section[key] = "/opt/pisee/" + rel.as_posix()
config["servidorCentral"]["certificadoPublico"] = "/opt/pisee/cacert.pem"
config["log"].update(nivel="ERROR", ruta="stdout", color=False)
security = config["servidores"]["interno"].setdefault("seguridad", {})
security.pop("cors", None)
password = secrets.token_hex(32)
security["passwordHTTPBasico"] = "formulario:" + password
(target / "local-authorization").write_text("formulario@" + password, encoding="utf-8")
# Solo el servicio necesario, sin proveedor ni scripts de otros servicios.
config["consumidor"] = [s for s in config["consumidor"] if s.get("rutaLocal") == "/srcei/informacionPersonalBasico"]
if len(config["consumidor"]) != 1:
    raise SystemExit("Servicio SRCeI único no encontrado")
config["proveedor"] = []
config["identificacion"].pop("custom", None)
(runtime / "config.json").write_text(json.dumps(config, ensure_ascii=False, indent=2), encoding="utf-8")
(target / "integridad.json").write_text(json.dumps({"NodoV2_sha256": hashlib.sha256(binary.read_bytes()).hexdigest(),
    "pares_criptograficos_coinciden": True, "estado_windows_copiado": False,
    "cambios": ["database.path", "log", "servidores.interno.seguridad", "consumidor", "proveedor", "identificacion.custom"]}, indent=2))
print("Copia Linux preparada; dos pares criptográficos coinciden y certificados vigentes. Sin estado Windows.")
