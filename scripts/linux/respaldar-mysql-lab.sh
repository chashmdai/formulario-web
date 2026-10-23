#!/usr/bin/env bash
# Respaldar únicamente el laboratorio desechable indicado. No elimina ni restaura el origen.
set -euo pipefail
[[ $# == 2 && "$2" == --autorizar-laboratorio ]] || { echo 'Uso: respaldar-mysql-lab.sh directorio-privado --autorizar-laboratorio' >&2; exit 2; }
private="$(realpath "$1")"
umask 077
mkdir -p "$private/respaldos"
backup="$private/respaldos/publico-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
docker exec siger-mysql-lab-mysql-1 mysqldump --defaults-extra-file=/run/secrets/root.cnf \
  --single-transaction --quick --routines --triggers --events --no-tablespaces --set-gtid-purged=OFF robust_public_test | gzip > "$backup"
gzip -t "$backup"
sha256sum "$backup" > "$backup.sha256"
printf '%s\n' "$backup"
