#!/usr/bin/env python3
"""Laboratorio NNA separado, reutilizando MySQL/TLS existentes; nunca altera su volumen o Sandbox CU."""
import argparse
import json
import os
from pathlib import Path
import pwd
import secrets
import subprocess

p = argparse.ArgumentParser()
p.add_argument("destino", type=Path)
p.add_argument("--usuario", required=True)
p.add_argument("--autorizar-laboratorio", action="store_true", required=True)
a = p.parse_args()
if os.geteuid() != 0:
    p.error("Ejecutar con los permisos administrativos normales de WSL")
user = pwd.getpwnam(a.usuario)
private_root = Path(user.pw_dir) / ".local/share/siger"
target = a.destino.expanduser().absolute()
if target.exists() or target.is_symlink() or not target.is_relative_to(private_root) or target.parent.resolve() != private_root.resolve():
    p.error("Se requiere un directorio privado nuevo directamente bajo ~/.local/share/siger")
if not (private_root / "mysql-lab/root.cnf").is_file() or not (private_root / "sandbox-https/site.pem").is_file():
    p.error("Reutilizar el laboratorio MySQL y el certificado exacto ya preparados")

def mysql(sql):
    r = subprocess.run(["docker", "exec", "-i", "siger-mysql-lab-mysql-1", "mysql", "--defaults-extra-file=/run/secrets/root.cnf", "--batch", "--skip-column-names"], input=sql, text=True, capture_output=True)
    if r.returncode:
        raise SystemExit("No se pudo completar la operación MySQL privada; no se imprimen SQL ni credenciales")
    return r.stdout.strip()

if mysql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='robust_public_local';") != "0":
    p.error("La base robust_public_local ya existe: conservarla y revisar su configuración, sin reinicializar")
if mysql("SELECT COUNT(*) FROM mysql.user WHERE user IN ('public_local_app','public_local_migrator');") != "0":
    p.error("Los usuarios de laboratorio local ya existen; no reemplazar sus credenciales")

os.umask(0o077)
target.mkdir(mode=0o700)
def write(name, value):
    path = target / name
    path.write_bytes(value if isinstance(value, bytes) else value.encode("utf-8"))
    path.chmod(0o600)
    os.chown(path, user.pw_uid, user.pw_gid)

for name in ["app-password", "migration-password", "keystore-password"]:
    write(name, secrets.token_hex(32))
write("abuso.key", secrets.token_bytes(32))
for prefix in ["publico", "institucional"]:
    subprocess.run(["openssl", "genpkey", "-algorithm", "ED25519", "-out", str(target / f"{prefix}-key.pem")], check=True, stdout=subprocess.DEVNULL)
    subprocess.run(["openssl", "pkey", "-in", str(target / f"{prefix}-key.pem"), "-pubout", "-out", str(target / f"{prefix}.pem")], check=True, stdout=subprocess.DEVNULL)
    write(f"{prefix}-trust.json", json.dumps({f"{prefix}-nna-lab": (target / f"{prefix}.pem").read_text()}))

tls = private_root / "sandbox-https"
subprocess.run(["openssl", "pkcs12", "-export", "-name", "siger-nna-lab", "-inkey", str(tls / "site-key.pem"), "-in", str(tls / "site.pem"), "-certfile", str(tls / "rootCA.pem"), "-out", str(target / "servidor.p12"), "-passout", "file:" + str(target / "keystore-password")], check=True, stdout=subprocess.DEVNULL)
url = "jdbc:mysql://127.0.0.1:13306/robust_public_local?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=3000&socketTimeout=5000"
properties = {
    "spring.profiles.active": "jdbc-reception", "server.address": "127.0.0.1", "server.port": "18443",
    "server.forward-headers-strategy": "none", "server.servlet.session.cookie.secure": "true",
    "server.servlet.session.cookie.name": "SIGERNNA2026", "server.ssl.enabled": "true", "server.ssl.key-store-type": "PKCS12",
    "server.ssl.key-store": "file:" + str(target / "servidor.p12"), "server.ssl.key-store-password": (target / "keystore-password").read_text(),
    "siger.claveunica.environment": "SANDBOX", "siger.claveunica.callback-approved": "false",
    "siger.claveunica.client-id": "NNA_LAB_CALLBACK_BLOQUEADO", "siger.claveunica.client-secret": secrets.token_hex(32),
    "siger.claveunica.redirect-uri": "https://claveunica.formularioadulto.defensorianinez.gob.cl/redirect",
    "siger.claveunica.logout-uri": "https://claveunica.formularioadulto.defensorianinez.gob.cl/logout",
    "siger.claveunica.public-origin": "https://claveunica.formularioadulto.defensorianinez.gob.cl:18443",
    "publico.db.url": url, "publico.db.user": "public_local_app", "publico.db.password": (target / "app-password").read_text(),
    "datos.mysql-local": "true", "publico.nna.habilitado": "false", "PISEE_SRCEI_ENABLED": "false",
    "publico.recepcion.habilitada": "false", "publico.recepcion.aviso-validado": "true", "publico.recepcion.aviso-version": "NNA_LAB_20261023_1",
    "publico.comprobante.key-id": "publico-nna-lab", "publico.comprobante.private-key": str(target / "publico-key.pem"),
    "publico.comprobante.trusted-keys": str(target / "publico-trust.json"), "publico.comprobante.institutional-trusted-keys": str(target / "institucional-trust.json"),
    "formulario.habilitado": "true", "formulario.abuso-key-path": str(target / "abuso.key"),
    "formulario.aviso-adulto": "ADULTO_LAB_20261023_1", "formulario.aviso-nna": "NNA_LAB_20261023_1", "formulario.recepcion.habilitada": "true",
    "operacion.token": secrets.token_hex(32), "publico.integracion.token": secrets.token_hex(32),
}
# La bandera de aviso habilita exclusivamente captura sintética de laboratorio; no implica aprobación productiva.
write("application.properties", "\n".join(f"{k}={v}" for k, v in properties.items()) + "\n")
write("migration.json", json.dumps({"MIGRATION_DB_URL": url, "MIGRATION_DB_USER": "public_local_migrator", "MIGRATION_DB_PASSWORD": (target / "migration-password").read_text(), "LOCAL_DB_ALLOWED": "true"}))
mysql("CREATE DATABASE robust_public_local CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n" + "\n".join(
    f"CREATE USER '{name}'@'%' IDENTIFIED BY '{(target / secret).read_text()}'; GRANT {rights} ON robust_public_local.* TO '{name}'@'%';"
    for name, secret, rights in [("public_local_app", "app-password", "SELECT,INSERT,UPDATE,DELETE"), ("public_local_migrator", "migration-password", "ALL PRIVILEGES")]))
for path in target.iterdir():
    os.chown(path, user.pw_uid, user.pw_gid)
    path.chmod(0o600)
os.chown(target, user.pw_uid, user.pw_gid)
print("Laboratorio NNA creado: esquema nuevo robust_public_local; se conservaron volumen, cuentas y servicios existentes. TLS exacto reutilizado; callback CU bloqueado; adjuntos cerrados hasta configurar antivirus real.")
