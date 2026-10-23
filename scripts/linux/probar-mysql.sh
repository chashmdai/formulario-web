#!/usr/bin/env bash
set -euo pipefail
[[ $# == 2 && "$2" == --autorizar-laboratorio ]] || { echo 'Uso: probar-mysql.sh directorio-privado --autorizar-laboratorio' >&2; exit 2; }
private="$(realpath "$1")"
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
export ROBUST_DB_URL='jdbc:mysql://127.0.0.1:13306/robust_public_test?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=3000&socketTimeout=5000'
export ROBUST_DB_USER=public_test_app
export ROBUST_DB_PASSWORD="$(cat "$private/app-password")"
export ROBUST_DB_MIGRATION_USER=public_test_migrator
export ROBUST_DB_MIGRATION_PASSWORD="$(cat "$private/migration-password")"
cd "$root/formulario-publico/backend"
mvn -B -ntp test
