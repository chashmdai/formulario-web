#!/usr/bin/env python3
"""Configura solo el perfil privado Sandbox; habilitar exige preflight bloqueado reciente."""
import argparse
import datetime as dt
import ipaddress
import json
import os
from pathlib import Path
import re
import subprocess

DOMAIN = "claveunica.formularioadulto.defensorianinez.gob.cl"
p = argparse.ArgumentParser()
p.add_argument("config", type=Path, help="application.properties privado existente")
p.add_argument("--habilitar-con-preflight", type=Path, metavar="JSON")
a = p.parse_args()
config = a.config.expanduser().resolve()
if not config.is_file() or any((d / ".git").exists() for d in config.parents):
    p.error("Se requiere configuración privada existente fuera de Git")
data = config.read_text()
if not re.search(r"^siger.claveunica.environment=SANDBOX$", data, re.M):
    p.error("Solo se admite el perfil SANDBOX")
for key, path in [("redirect-uri", "/redirect"), ("logout-uri", "/logout")]:
    if not re.search(r"^siger.claveunica\." + key + r"=https://" + re.escape(DOMAIN) + path + r"$", data, re.M):
        p.error("No coincide el callback oficial; no se sustituye")
container = json.loads(subprocess.check_output(["docker", "inspect", "siger-sandbox-https-caddy-1"], text=True))[0]
network = container["NetworkSettings"]["Networks"].get("siger-app-lab_default")
if not network or not container["State"]["Running"]:
    p.error("El proxy debe estar ejecutándose en la red privada de la aplicación")
proxy_ip = str(ipaddress.IPv4Address(network["IPAddress"]))
if not ipaddress.ip_address(proxy_ip).is_private:
    p.error("Dirección del proxy no privada")
enabled = bool(a.habilitar_con_preflight)
if enabled:
    evidence = json.loads(a.habilitar_con_preflight.read_text(encoding="utf-8-sig"))
    required = ["SystemResolution", "HttpsTrustAndHostname", "ForwardedHttps", "SecureHttpOnlySameSiteLax",
                "CallbackReachesBackend", "LogoutReachesBackend", "LoginBlocked"]
    when = dt.datetime.fromisoformat(evidence["ValidatedAtUtc"].replace("Z", "+00:00"))
    age = (dt.datetime.now(dt.timezone.utc) - when).total_seconds()
    # Pequeño desfase Windows/WSL al copiar inmediatamente la evidencia.
    if evidence.get("Domain") != DOMAIN or evidence.get("Environment") != "SANDBOX" or not all(evidence.get(k) is True for k in required) or not -5 <= age <= 900:
        p.error("Se requiere preflight bloqueado satisfactorio de los últimos 15 minutos")
changes = {
    "siger.claveunica.public-origin": "https://" + DOMAIN,
    "siger.claveunica.callback-approved": str(enabled).lower(),
    "server.forward-headers-strategy": "native",
    "server.tomcat.remoteip.internal-proxies": "127[.]0[.]0[.]1|" + proxy_ip.replace(".", "[.]"),
    "server.tomcat.remoteip.protocol-header": "x-forwarded-proto",
    "server.tomcat.remoteip.remote-ip-header": "x-forwarded-for",
    "server.servlet.session.cookie.secure": "true",
}
os.umask(0o077)
backup = config.with_name("application-before-https.properties")
if not backup.exists():
    backup.write_text(data)
    backup.chmod(0o600)
for key, value in changes.items():
    pattern = r"^" + re.escape(key) + r"=.*$"
    replacement = key + "=" + value
    data = re.sub(pattern, lambda _: replacement, data, flags=re.M) if re.search(pattern, data, re.M) else data + "\n" + replacement + "\n"
config.write_text(data)
config.chmod(0o600)
print("Perfil HTTPS SANDBOX configurado; proxy exacto", proxy_ip, "; inicio", "habilitado" if enabled else "bloqueado", "; reiniciar solo la aplicación")
