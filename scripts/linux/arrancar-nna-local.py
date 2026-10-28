#!/usr/bin/env python3
"""Laboratorio integrado 18443: ejecución privada versionada, nunca desde target/."""
import argparse
import json
import os
from pathlib import Path
import subprocess
from jar_laboratorio import RuntimeJar, opciones


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("directorio_privado", type=Path)
    opciones(p)
    p.add_argument("--permitir-cu-sandbox", action="store_true", help="Compatibilidad: habilitación CU controlada por callback-approved en configuración privada")
    a = p.parse_args()
    try:
        runtime = RuntimeJar(a.directorio_privado, 18443)
        private = runtime.private
        root = Path(__file__).resolve().parents[2]
        props = dict(line.split("=", 1) for line in runtime.config.read_text().splitlines() if "=" in line and not line.startswith("#"))
        if props.get("server.address") != "127.0.0.1" or props.get("server.port") != "18443" or props.get("server.ssl.enabled") != "true":
            p.error("Solo laboratorio integrado loopback 18443/TLS")
        if props.get("siger.claveunica.environment") not in {"SANDBOX", "QA", "PRODUCTION"}:
            p.error("Ambiente CU inválido")
        if props.get("siger.claveunica.callback-approved") == "true":
            domain = props.get("siger.claveunica.public-origin")
            if domain not in {"https://claveunica.formularioadulto.defensorianinez.gob.cl", "https://formulario.defensorianinez.cl"}:
                p.error("Origen institucional no autorizado para el laboratorio")
            if props.get("siger.claveunica.redirect-uri") != domain + "/redirect" or props.get("siger.claveunica.logout-uri") != domain + "/logout" or props.get("siger.claveunica.public-origin") != domain:
                p.error("Conservar exactamente callbacks y origen registrados")
        os.umask(0o077)
        with runtime.lock():
            result = runtime.action(a)
            if result is None:
                runtime.stopped()
                if a.jar:
                    runtime.select(runtime.install(a.jar)["sha256"])
                jar = runtime.selected()
                if a.migrar:
                    env = dict(os.environ, **json.loads((private / "migration.json").read_text()))
                    with (private / "migration.log").open("ab") as log:
                        migration = subprocess.run(["java", "-Xmx256m", "-jar", str(jar), "--migrate"], env=env, cwd=root, stdout=log, stderr=subprocess.STDOUT)
                    if migration.returncode:
                        raise ValueError("Migración no completada; revisar log privado")
                result = runtime.start(["-Djava.net.preferIPv4Stack=true", "-Xms64m", "-Xmx512m"], root)
            print(json.dumps(result, ensure_ascii=True))
    except (OSError, ValueError, KeyError) as error:
        p.error(str(error))


if __name__ == "__main__":
    main()
