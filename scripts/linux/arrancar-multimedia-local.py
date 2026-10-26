#!/usr/bin/env python3
"""Laboratorio multimedia 19443: copias privadas versionadas, sin tocar el baseline."""
import argparse
import json
import os
from pathlib import Path
import subprocess
from jar_laboratorio import RuntimeJar, opciones


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("private", type=Path)
    opciones(p)
    a = p.parse_args()
    try:
        runtime = RuntimeJar(a.private, 19443)
        private = runtime.private
        root = Path(__file__).resolve().parents[2]
        props = dict(line.split("=", 1) for line in runtime.config.read_text().splitlines() if "=" in line and not line.startswith("#"))
        if not (props.get("server.address") == "127.0.0.1" and props.get("server.port") == "19443" and props.get("server.ssl.enabled") == "true" and props.get("siger.claveunica.callback-approved") == "false" and props.get("PISEE_SRCEI_ENABLED") == "false" and props.get("formulario.multimedia.habilitada") == "true" and '/robust_public_media_lab?' in props.get('publico.db.url', '') and 'PISEE_ALLOWED_RUNS_PATH' not in props):
            p.error("Solo laboratorio multimedia aislado sin proveedores externos")
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
                result = runtime.start(["-Djava.net.preferIPv4Stack=true", "-Xms64m", "-Xmx384m"], root)
            print(json.dumps(result, ensure_ascii=True))
    except (OSError, ValueError, KeyError) as error:
        p.error(str(error))


if __name__ == "__main__":
    main()
