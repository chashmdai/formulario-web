#!/usr/bin/env bash
set -euo pipefail
[[ $# == 5 && "$5" == --autorizar-consulta-productiva-acotada ]] || {
  echo 'Uso: sonda-adaptador-pisee.sh JAR SHA256 CONFIG_PRIVADA DESTINO_NUEVO --autorizar-consulta-productiva-acotada' >&2
  exit 2
}
umask 077
artifact=$(realpath "$1")
[[ "$2" =~ ^[a-f0-9]{64}$ ]] || exit 2
printf '%s  %s\n' "$2" "$artifact" | sha256sum --check --status || {
  echo 'JAR distinto del candidato autorizado' >&2; exit 2;
}
destination=$(realpath -m "$4")
[[ ! -e "$destination" && ! -L "$destination" ]] || {
  echo 'Destino nuevo requerido' >&2; exit 2;
}
for parent in "$destination" "$(dirname "$destination")"; do
  cursor=$parent
  while [[ "$cursor" != / ]]; do
    [[ ! -e "$cursor/.git" && ! -L "$cursor" ]] || exit 2
    cursor=$(dirname "$cursor")
  done
done
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
mkdir -m 700 "$destination"
(cd "$destination" && jar xf "$artifact")
classpath="$destination/BOOT-INF/classes:$destination/BOOT-INF/lib/*"
javac -cp "$classpath" -d "$destination/probe" "$script_dir/SondaAdaptadorPisee.java"
exec java -cp "$destination/probe:$classpath" cl.nexbyte.siger.srcei.SondaAdaptadorPisee "$3"
