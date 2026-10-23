#!/usr/bin/env python3
"""Una consulta DEV autorizada. Salida de metadatos; jamás atributos personales."""
import argparse
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument("directorio_privado", type=Path)
p.add_argument("--contenedor", default="siger-pisee-dev-pisee-1")
p.add_argument("--autorizar-dev", action="store_true", required=True)
p.add_argument("--guardar-respuesta-privada", action="store_true", help="Retención explícita excepcional del XML protegido")
args = p.parse_args()
private = args.directorio_privado.expanduser().resolve(strict=True)
run = json.loads((private / "run-autorizado.json").read_text())
number, dv = str(run["numero"]), str(run["dv"]).upper()
if not number.isdigit() or not 1 <= len(number) <= 8 or dv not in "0123456789K" or len(dv) != 1:
    raise SystemExit("Formato del identificador autorizado inválido")
xml = f"<documento><entradaSRCel><run><numero>{number}</numero><dv>{dv}</dv></run></entradaSRCel></documento>"
auth = (private / "local-authorization").read_text().strip()
config = f'header = "X-PISEE-LocalAuthorization: {auth}"\nheader = "Content-Type: application/xml; charset=utf-8"\ndata = "{xml}"\n'
result = subprocess.run(["docker", "exec", "-i", args.contenedor, "curl", "--silent", "--show-error", "--max-time", "30",
                         "--max-filesize", "131072", "--config", "-", "--write-out", "\n%{http_code}",
                         "http://127.0.0.1:8084/srcei/informacionPersonalBasico"],
                        input=config.encode(), stdout=subprocess.PIPE, stderr=subprocess.PIPE)
if result.returncode:
    print(json.dumps({"estado": "FALLO_TRANSPORTE", "codigo_curl": result.returncode}))
    raise SystemExit(1)
body, _, status = result.stdout.rpartition(b"\n")
if b"<!DOCTYPE" in body.upper() or b"<!ENTITY" in body.upper():
    raise SystemExit("XML no permitido")
try:
    tree = ET.fromstring(body)
    data = [x for x in tree.iter() if x.tag.split("}")[-1] == "datosPersona"]
    names = sorted(x.tag.split("}")[-1] for x in list(data[0])) if len(data) == 1 else []
    report = {"http": status.decode(), "xml_valido": True, "cantidad_atributos": len(names), "atributos": names,
              "entrada_xml": "entradaSRCel", "resultado": "CON_ATRIBUTOS" if names else "SIN_ATRIBUTOS"}
except ET.ParseError:
    report = {"http": status.decode(), "xml_valido": False, "resultado": "XML_INVALIDO"}
# Por defecto no se conserva el XML personal. Retención opcional solo en custodia privada.
if args.guardar_respuesta_privada:
    (private / "ultima-respuesta.xml").write_bytes(body)
    (private / "ultima-respuesta.xml").chmod(0o600)
(private / "evidencia-sanitizada.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
print(json.dumps(report, ensure_ascii=False))
