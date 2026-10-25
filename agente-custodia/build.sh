#!/usr/bin/env bash
set -euo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
# Construcción aislada. No despliega, configura servicios, migra o toca SIGER.
mvn -B -ntp -f "$root/formulario-publico/backend/pom.xml" -Pagente-core -DskipTests -Dsiger.build.directory="$root/.local/agente-windows-target" install
mvn -B -ntp -f "$root/agente-custodia/pom.xml" -Dagente.build.directory="$root/.local/agente-dist" package
sha256sum "$root/.local/agente-dist/agente-custodia-1.0.0-windows-lab.jar"