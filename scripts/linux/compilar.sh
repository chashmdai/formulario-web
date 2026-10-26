#!/usr/bin/env bash
set -euo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
# Solo construye. Los lanzadores ejecutan copias privadas seleccionadas por SHA-256, nunca target/.
node -e 'if(Number(process.versions.node.split(".")[0])!==24)process.exit(1)'
java -version
cd "$root/formulario-publico/front"
npm ci
npm run lint
npm test
npm run build
cd ../backend
mvn -B -ntp clean verify
