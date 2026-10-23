#!/usr/bin/env python3
"""Genera secretos de un laboratorio nuevo, sin reutilizar datos institucionales."""
import argparse
import os
from pathlib import Path
import secrets

p = argparse.ArgumentParser()
p.add_argument("destino", type=Path)
args = p.parse_args()
target = args.destino.expanduser().resolve()
if target.exists():
    raise SystemExit("El destino ya existe; conservar secretos y volumen asociados")
if any((parent / ".git").exists() for parent in [target, *target.parents]):
    raise SystemExit("Los secretos deben quedar fuera de Git")
os.umask(0o077)
target.mkdir(parents=True, mode=0o700)
for name in ["root-password", "app-password", "migration-password"]:
    (target / name).write_text(secrets.token_hex(32), encoding="ascii")
(target / "root.cnf").write_text("[client]\nuser=root\npassword=" + (target / "root-password").read_text() + "\n", encoding="ascii")
sql = "CREATE DATABASE IF NOT EXISTS robust_public_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n"
for user, secret, rights in [("public_test_app", "app-password", "SELECT,INSERT,UPDATE,DELETE"),
                            ("public_test_migrator", "migration-password", "ALL PRIVILEGES")]:
    sql += f"CREATE USER IF NOT EXISTS '{user}'@'%' IDENTIFIED BY '{(target / secret).read_text()}';\n"
    sql += f"GRANT {rights} ON robust_public_test.* TO '{user}'@'%';\n"
(target / "laboratorio.sql").write_text(sql, encoding="ascii")
# Montaje individual legible por mysql (UID 999); el directorio host 0700 protege el archivo.
(target / "laboratorio.sql").chmod(0o444)
print("Secretos nuevos generados fuera de Git; directorio 0700. SQL de inicialización para montaje individual.")
