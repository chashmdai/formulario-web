#!/usr/bin/env bash
# Restauración exclusivamente en el contenedor aislado mysql-restore, sin puertos.
set -euo pipefail
[[ $# == 2 && "$2" == --autorizar-laboratorio ]] || { echo 'Uso: restaurar-mysql-lab.sh respaldo.sql.gz --autorizar-laboratorio' >&2; exit 2; }
backup="$(realpath "$1")"
sha256sum --check --status "$backup.sha256"
tables="$(docker exec siger-mysql-lab-mysql-restore-1 mysql --defaults-extra-file=/run/secrets/root.cnf -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='robust_public_test'")"
[[ "$tables" == 0 ]] || { echo 'El destino ya contiene tablas; no se reemplaza ni limpia' >&2; exit 1; }
gzip -dc "$backup" | docker exec -i siger-mysql-lab-mysql-restore-1 mysql --defaults-extra-file=/run/secrets/root.cnf robust_public_test
query="SELECT id,SHA2(CONCAT_WS('|',body,COALESCE(ack,''),state,request_hash),256) FROM robust_public_test.public_delivery ORDER BY id; SELECT installed_rank,version,checksum,success FROM robust_public_test.flyway_schema_history ORDER BY installed_rank;"
origin="$(docker exec siger-mysql-lab-mysql-1 mysql --defaults-extra-file=/run/secrets/root.cnf -N -e "$query")"
restored="$(docker exec siger-mysql-lab-mysql-restore-1 mysql --defaults-extra-file=/run/secrets/root.cnf -N -e "$query")"
[[ "$origin" == "$restored" ]] || { echo 'Discrepancia entre origen y restauración' >&2; exit 1; }
echo 'Restauración real validada: cuerpos, ACK, estados, idempotencia y migraciones coinciden con el origen.'
