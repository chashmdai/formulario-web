#!/usr/bin/env python3
"""Evidencia reducida de la consulta autorizada. Ejecutar con acceso Docker local; no imprime atributos."""
import base64
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import uuid

root = Path(__file__).resolve().parents[2]
app, node = (Path(arg).resolve(strict=True) for arg in sys.argv[1:3])
output = root / ".local/evidencias/run-srcei-dev"
result = json.loads((output / "resultado.json").read_text())
assert result.get("etapa") == "completado"
journey, communication = str(uuid.UUID(result["recorrido"])), str(uuid.UUID(result["comunicacion"]))
authorized = json.loads((node / "run-autorizado.json").read_text())

def sql(query):
    process = subprocess.run(["docker", "exec", "-i", "siger-mysql-lab-mysql-1", "mysql", "--defaults-extra-file=/run/secrets/root.cnf", "--batch", "--raw", "--skip-column-names", "--default-character-set=utf8mb4"], input=query, text=True, capture_output=True)
    if process.returncode:
        raise RuntimeError("No se pudo verificar MySQL; no se imprimen sus resultados privados")
    return process.stdout.strip().splitlines()

rows = sql(f"SELECT body FROM robust_public_local.citizen_person WHERE journey_id='{journey}' ORDER BY person_id;")
people = {p["id"]: p for p in map(json.loads, rows)}
presenter, related = people["presentador-adulto"], people["relacionado-1"]
assert presenter["runNumero"] == str(authorized["numero"]) and presenter["runDv"] == str(authorized["dv"]).upper()
assert not presenter["autenticada"] and presenter["modulo11"]
assert presenter["estadoConsulta"] in {"COMPLETO", "PARCIAL"} and presenter["consultaRef"] and presenter["consultadaEn"]
assert sum(bool(p["consultaRef"]) for p in people.values()) == 1
assert related["estadoConsulta"] == "NO_HABILITADO" and not related["consultaRef"] and not related["autenticada"]
assert related["atributos"]["primerNombre"]["valor"] == "PROPIO"
assert related["atributos"]["primerNombre"]["fuente"] == "DECLARACION_CIUDADANA"
assert not any(a["fuente"] == "SRCEI" for a in related["atributos"].values())
assert presenter["atributos"]["runNumero"]["fuente"] == "DECLARACION_CIUDADANA"
registry = [a for a in presenter["atributos"].values() if a["fuente"] == "SRCEI"]
assert registry and all(a["evidencia"] == presenter["consultaRef"] and a["observadoEn"] == presenter["consultadaEn"] for a in registry)
assert sql(f"SELECT query_count FROM robust_public_local.citizen_journey WHERE id='{journey}';") == ["2"]
state, raw = sql(f"SELECT state,body FROM robust_public_local.citizen_submission WHERE id='{communication}';")[0].split("\t", 1)
delivery = json.loads(raw)
content = delivery["comunicacion"]
assert state == "PENDIENTE" and content["recorridoId"] == journey
assert content["tipo"] == "ADULTO_DECLARADO" and content["aceptaCanalDigital"]
assert content["personas"] == [presenter] and content["datos"]["personas"] == []
assert set(content["datos"]) == {"campos", "personas", "instituciones", "adjuntos"}

def digest(domain, value):
    encoded = json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode()
    return hashlib.sha256(b"SIGER_FORMULARIO_COMPLETO_1\0" + domain.encode() + b"\0" + encoded).hexdigest()

assert delivery["sha256"] == digest("CONTENIDO", content)
assert delivery["huellaProcedencia"] == digest("PROCEDENCIA", {presenter["id"]: [presenter["atributos"], presenter["discrepancias"]]})
proof = delivery["custodia"]
decode = lambda value: base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))
payload, signature = decode(proof["payload"]), decode(proof["firma"])
claims = json.loads(payload)
assert claims["huellaContenido"] == delivery["sha256"] and claims["id"] == communication
(output / "payload-verificacion.bin").write_bytes(payload)
(output / "firma-verificacion.bin").write_bytes(signature)
verified = subprocess.run(["openssl", "pkeyutl", "-verify", "-pubin", "-inkey", str(app / "publico.pem"), "-rawin", "-in", str(output / "payload-verificacion.bin"), "-sigfile", str(output / "firma-verificacion.bin")], capture_output=True)
assert verified.returncode == 0
summary = {"recorrido": journey, "comunicacion": communication, "estadoDev": presenter["estadoConsulta"], "atributosRegistralesInternos": len(registry), "consultasConEvidencia": 1,
           "runSigueDeclarado": True, "srceiNoAutentica": True, "terceroSinConsulta": True, "declaracionesConservadas": True,
           "custodiaIntegra": True, "firmaEd25519": True, "sinTerceroInactivoEnCustodia": True, "idempotenciaVerificadaEnHttp": result["idempotencia"]}
(output / "mysql.json").write_text(json.dumps(summary, indent=2))
if (output / "nna.json").is_file():
    nna = json.loads((output / "nna.json").read_text())
    assert nna["etapa"] == "completado"
    nna_journey, nna_communication = str(uuid.UUID(nna["recorrido"])), str(uuid.UUID(nna["comunicacion"]))
    person = json.loads(sql(f"SELECT body FROM robust_public_local.citizen_person WHERE journey_id='{nna_journey}';")[0])
    assert person["estadoConsulta"] == "NO_HABILITADO" and not person["consultaRef"] and not person["autenticada"]
    assert person["atributos"]["primerNombre"]["valor"] == "NOMBRE" and person["atributos"]["primerApellido"]["valor"] == "APELLIDO"
    assert all(a["fuente"] == "DECLARACION_CIUDADANA" for a in person["atributos"].values())
    nna_delivery = json.loads(sql(f"SELECT body FROM robust_public_local.citizen_submission WHERE id='{nna_communication}';")[0])
    assert not nna_delivery["comunicacion"]["aceptaCanalDigital"]
    assert nna_delivery["comunicacion"]["personas"] == [person]
    assert nna_delivery["sha256"] == digest("CONTENIDO", nna_delivery["comunicacion"])
    summary_nna = {"recorrido": nna_journey, "comunicacion": nna_communication, "sinConsultaExterna": True,
                   "runVoluntarioDeclarado": True, "sinExigirCanalAdulto": True, "declaracionesPersistidas": True,
                   "restauracionSinPost": nna["restauracionSinPost"], "custodiaIntegra": True}
    (output / "mysql-nna.json").write_text(json.dumps(summary_nna, indent=2))
print("MySQL DEV: consulta única con evidencia, fuentes separadas, tercero sin SRCeI, declaración conservada y custodia/firma íntegras.")
