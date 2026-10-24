#!/usr/bin/env python3
"""Configura exclusivamente DEV en el laboratorio TLS existente; no imprime datos ni reinicia servicios."""
import argparse
import json
import os
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument("aplicacion", type=Path)
p.add_argument("nodo", type=Path)
mode = p.add_mutually_exclusive_group(required=True)
mode.add_argument("--autorizar-dev", action="store_true")
mode.add_argument("--deshabilitar", action="store_true")
a = p.parse_args()
app, node = a.aplicacion.resolve(strict=True), a.nodo.resolve(strict=True)
for directory in [app, node]:
    if any((parent / ".git").exists() for parent in [directory, *directory.parents]):
        p.error("Usar solo directorios privados existentes fuera de Git")
config = app / "application.properties"
original = config.read_text()
props = dict(line.split("=", 1) for line in original.splitlines() if "=" in line and not line.startswith("#"))
if props.get("siger.claveunica.environment") != "SANDBOX" or props.get("server.address") != "127.0.0.1" or props.get("server.port") != "18443" or props.get("server.ssl.enabled") != "true":
    p.error("Solo admite el laboratorio Sandbox TLS en loopback 18443")
if any(profile.strip().lower() in {"prod", "production", "qa"} for profile in props.get("spring.profiles.active", "").split(",")):
    p.error("No alterar QA ni producción")
if a.autorizar_dev:
    allowed = node / "run-autorizado.json"
    if allowed.stat().st_size > 4096:
        p.error("Allowlist DEV excesiva")
    entries = json.loads(allowed.read_text())
    entries = entries if isinstance(entries, list) else [entries]
    if not 1 <= len(entries) <= 10:
        p.error("Reutilizar la allowlist privada acotada ya autorizada")
    for entry in entries:
        if not isinstance(entry, dict) or set(entry) != {"numero", "dv"} or not str(entry["numero"]).isdigit() or not 1 <= len(str(entry["numero"])) <= 8:
            p.error("Formato de autorización inválido")
        number = str(entry["numero"])
        checksum = 11 - sum(int(digit) * (2 + i % 6) for i, digit in enumerate(reversed(number))) % 11
        if str(entry["dv"]).upper() != ("0" if checksum == 11 else "K" if checksum == 10 else str(checksum)):
            p.error("La autorización privada no contiene RUN/DV válido")
    authorization = node / "local-authorization"
    if not authorization.is_file() or not 1 <= authorization.stat().st_size <= 256:
        p.error("Reutilizar la autorización local privada del Nodo existente")
os.umask(0o077)
backup = app / "application-before-srcei-automatico.properties"
if not backup.exists():
    with backup.open("x") as output:
        output.write(original)
    backup.chmod(0o600)
if a.autorizar_dev:
    props.update({"PISEE_SRCEI_ENABLED": "true", "PISEE_ENVIRONMENT": "DEV", "PISEE_DEV_AUTHORIZED": "true",
                  "PISEE_BASE_URL": "http://127.0.0.1:18084", "PISEE_LOCAL_AUTHORIZATION_PATH": str(authorization),
                  "PISEE_ALLOWED_RUNS_PATH": str(allowed), "PISEE_TIMEOUT_MS": "15000", "PISEE_CONCURRENCY": "1"})
else:
    props["PISEE_SRCEI_ENABLED"] = "false"
config.write_text("\n".join(key + "=" + value for key, value in props.items()) + "\n")
config.chmod(0o600)
print("DEV configurado con allowlist privada y respaldo conservado." if a.autorizar_dev else "SRCeI deshabilitado; se conservan los demás ajustes y datos.")
