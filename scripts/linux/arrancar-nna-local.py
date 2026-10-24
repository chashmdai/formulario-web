#!/usr/bin/env python3
"""Arranca únicamente el laboratorio NNA nativo con TLS verificado; conserva todos los servicios previos."""
import argparse
import json
import os
from pathlib import Path
import socket
import subprocess
import signal

p = argparse.ArgumentParser()
p.add_argument("directorio_privado", type=Path)
p.add_argument("--migrar", action="store_true")
p.add_argument("--detener", action="store_true")
p.add_argument("--permitir-cu-sandbox", action="store_true", help="Opt-in explícito para el formulario integrado, conservando callbacks oficiales")
a = p.parse_args()
private = a.directorio_privado.expanduser().resolve()
root = Path(__file__).resolve().parents[2]
jar = root / "formulario-publico/backend/target/formulario-publico-0.1.0-SNAPSHOT.jar"
if not private.is_dir() or any((d / ".git").exists() for d in [private, *private.parents]) or not jar.is_file():
    p.error("Usar configuración privada existente fuera de Git y un JAR ya compilado")
if not (private / "application.properties").is_file():
    p.error("Falta la configuración privada")
props = dict(line.split("=", 1) for line in (private / "application.properties").read_text().splitlines() if "=" in line and not line.startswith("#"))
if props.get("server.address") != "127.0.0.1" or props.get("server.port") != "18443" or props.get("server.ssl.enabled") != "true" or props.get("siger.claveunica.environment") != "SANDBOX":
    p.error("Solo admite el laboratorio NNA loopback/TLS con callback bloqueado")
if props.get("siger.claveunica.callback-approved") != "false" and not a.permitir_cu_sandbox:
    p.error("El callback requiere el opt-in explícito --permitir-cu-sandbox")
if a.permitir_cu_sandbox:
    domain="https://claveunica.formularioadulto.defensorianinez.gob.cl"
    if props.get("siger.claveunica.redirect-uri") != domain+"/redirect" or props.get("siger.claveunica.logout-uri") != domain+"/logout" or props.get("siger.claveunica.public-origin") != domain:
        p.error("Conservar exactamente los callbacks y origen oficiales del Sandbox")
if a.detener:
    pid = int((private / "app.pid").read_text())
    process_dir = Path(f"/proc/{pid}")
    if not process_dir.exists():
        raise SystemExit("El proceso de laboratorio ya no está ejecutándose")
    if str(jar).encode() not in (process_dir / "cmdline").read_bytes() or ("SPRING_CONFIG_ADDITIONAL_LOCATION=file:" + str(private / "application.properties")).encode() not in (process_dir / "environ").read_bytes():
        p.error("El PID no corresponde a este JAR y configuración privada; no se detiene")
    os.kill(pid, signal.SIGTERM)
    print("Se solicitó detener exclusivamente el proceso NNA de este laboratorio:", pid)
    raise SystemExit(0)
with socket.socket() as check:
    if check.connect_ex(("127.0.0.1", 18443)) == 0:
        p.error("18443 ya está ocupado; no se detiene ningún proceso")
os.umask(0o077)
if a.migrar:
    env = dict(os.environ, **json.loads((private / "migration.json").read_text()))
    with (private / "migration.log").open("ab") as log:
        result = subprocess.run(["java", "-Xmx256m", "-jar", str(jar), "--migrate"], env=env, cwd=root, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        raise SystemExit("Migración no completada; revisar el log privado sin publicar su contenido")
with (private / "app.log").open("ab") as log:
    process = subprocess.Popen(["java", "-Djava.net.preferIPv4Stack=true", "-Xms64m", "-Xmx512m", "-jar", str(jar)], env=dict(os.environ, SPRING_CONFIG_ADDITIONAL_LOCATION="file:" + str(private / "application.properties")), cwd=root, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
(private / "app.pid").write_text(str(process.pid))
print("Proceso NNA iniciado, PID", process.pid, "en HTTPS loopback 18443. Comprobar salud/TLS antes de usarlo.")
