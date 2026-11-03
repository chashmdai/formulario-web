#!/usr/bin/env python3
"""Prepara una COPIA privada Linux; nunca modifica originales ni copia SQLite Windows."""
import argparse
import base64
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
parser.add_argument("--produccion-validada", action="store_true", help="Conservar configuración y certificados de la copia institucional ya validada; solo adaptar rutas/estado/log")
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
def relative(value):
    rel = Path(value.replace("\\", "/").removeprefix("./"))
    if rel.is_absolute() or ".." in rel.parts: raise SystemExit("Ruta institucional no relativa; requiere revisión")
    return rel.as_posix()

x509 = config["identificacion"]["SRCeI"]["X509"]
x509_files = [relative(x509[k]) for k in ("privado", "publico")] if args.produccion_validada else ["certsX509/private.pem", "certsX509/public.pem"]
if args.produccion_validada:
    # Rechazar el par de Gobierno Digital incluido en el paquete original.
    cert_der = subprocess.check_output(["openssl", "x509", "-in", str(source/x509_files[1]), "-outform", "DER"], stderr=subprocess.DEVNULL)
    if hashlib.sha256(cert_der).hexdigest() != "3dc48ca30f6894d3321da10173af47960d5fe9afdbbd769696f1e6de678a64e0":
        raise SystemExit("El certificado no coincide con el institucional validado el 03/11/2026")
for name in ("cert.pem", "key.pem", "cacert.pem", "catalogo-cert.pem", "catalogo-key.pem", "certServer.pem", *x509_files):
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

def public_from_key(path):
    # Algunos paquetes Go rotulan PKCS#1 como PRIVATE KEY. Validar DER en memoria,
    # sin reescribir la PKI que el nodo ya consume ni mostrar material privado.
    result = subprocess.run(["openssl", "pkey", "-in", str(path), "-pubout", "-outform", "DER"], capture_output=True)
    if result.returncode == 0: return result.stdout
    pem = path.read_bytes()
    try:
        der = base64.b64decode(b"".join(line for line in pem.splitlines() if not line.startswith(b"-----")), validate=True)
        result = subprocess.run(["openssl", "pkey", "-inform", "DER", "-pubout", "-outform", "DER"], input=der, capture_output=True)
        if result.returncode == 0: return result.stdout
    except ValueError: pass
    raise SystemExit("No se pudo verificar el par criptográfico; no iniciar el nodo")

for key, cert in [("key.pem", "cert.pem"), tuple(x509_files)]:
    private_public = public_from_key(runtime / key)
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
if args.produccion_validada:
    central = config["servidorCentral"]
    central["certificadoPublico"] = "/opt/pisee/" + relative(central["certificadoPublico"])
    for key in ("publico", "privado"):
        central["catalogo"]["tls"][key] = "/opt/pisee/" + relative(central["catalogo"]["tls"][key])
    for folder in ("assets", "conversiones"):
        shutil.copytree(source/folder, runtime/folder)
    # Preservar consumidor/proveedor, PKI, MPGA, catálogo y seguridad interna.
    config["log"].update(nivel="error", ruta="/dev/null", color=False)
    (runtime / "config.json").write_text(json.dumps(config, ensure_ascii=False, indent=2), encoding="utf-8")
    (target / "integridad.json").write_text(json.dumps({"NodoV2_sha256": hashlib.sha256(binary.read_bytes()).hexdigest(),
        "institucional_x509_validado": True, "pares_criptograficos_coinciden": True, "estado_historico_copiado": False,
        "cambios": ["rutas_pki_x509_catalogo", "database.path", "log"]}, indent=2))
    print("Copia productiva privada preparada; identidad, catálogo, MPGA y servicios preservados. Sin copiar estado ni iniciar procesos.")
    raise SystemExit(0)
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
