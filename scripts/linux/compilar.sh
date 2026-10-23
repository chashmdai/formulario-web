#!/usr/bin/env bash
set -euo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
node -e 'if(Number(process.versions.node.split(".")[0])!==24)process.exit(1)'
java -version
cd "$root/formulario-publico/front"
npm ci
npm run lint
npm test
npm run build
cd ../backend
mvn -B -ntp clean verify
