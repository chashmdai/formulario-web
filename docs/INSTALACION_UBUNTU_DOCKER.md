# Instalación y operación Docker en Ubuntu

**Procedimiento para GATES futuros; H1 solo lo prepara.** Destino nuevo, aislado de GATE4A. Leer [traspaso/topología](TRASPASO_DDN_UBUNTU_GATE_H1_2026-11-01.md). No ejecutar todos los pasos indiscriminadamente ni hacer `down -v`. Sin publicación de80/443, MySQL o Spring en host.

## 1. Inventario y dependencias

Git, Python3 estándar, OpenSSL, curl, Docker Engine/Compose v2+/Buildx; Linux amd64 con POSIX, locks/rename/fsync, Landlock ABI≥3 y seccomp. No requiere Java/Maven/Node del host. Inspector Ubuntu24.04 en contenedor no exige cambiar Ubuntu26.04 del host.

```bash
hostnamectl
df -hT / /var/lib/docker /srv
free -h
sudo docker version
sudo docker compose version
sudo docker ps -a
sudo docker volume ls
sudo docker system df
sudo ss -ltnp
systemctl status ddn-formulario-lab.service --no-pager
```

Inventariar sin secretos. Acreditar fingerprint SSH con TI, acceso por clave y backup GATE4A. Si faltan dependencias, TI autoriza instalarlas; no reinstalar Docker/cambiar firewall o SSH. Docker group equivale a administración.

Elegir loopback libre24443, directorio/proyecto nuevos y respaldo externo. Límites de contenedores suman~5.4GiB más SO/cache/scratch; no son uso real medido. Recomendar≥8GiB disponibles para pila/cargas. Compilar serialmente o en otro host estable, nunca al borde de OOM.

## 2. Fuente y build

```bash
git clone git@github.com:chashmdai/formulario-web.git
cd formulario-web
git fetch origin
git switch main
git status --short --branch
REV=$(git rev-parse HEAD)
sudo docker build --build-arg SOURCE_REVISION="$REV" -t "ddn-formulario:$REV" .
sudo docker build -t "ddn-inspector:$REV" -f infra/media/Dockerfile infra/media
sudo docker build -t "ddn-clamav:$REV" -f infra/media/Dockerfile.clamav infra/media
sudo docker build -t "ddn-caddy:$REV" -f infra/sandbox/Dockerfile infra/sandbox
sudo docker image inspect "ddn-formulario:$REV" --format '{{.Id}} {{index .Config.Labels "org.opencontainers.image.revision"}}'
```

Usar SHA candidato revisado H1 o uno posteriormente aprobado, no cualquier main futuro. Build ejecuta npm ci/lint/pruebas/build React y regresión H2 sin carga. Recuentos: [evidencia](VERIFICACION_GATE_H1_2026-11-01.md). Imagen final JRE/curl/JAR, sin fixtures/tests/secretos de etapas anteriores.

Node/Maven/JRE por digest; MySQL8.4.11/Caddy2.11.7 por versión. Se reutiliza el wrapper sin file capabilities de infra/sandbox/Dockerfile para ejecutar8443 con cap_drop ALL. Registrar IDs/digests. apt/firmas AV evolucionan: proceso/fuente reproducibles no significa bytes idénticos en otras fechas. Retener imágenes aprobadas para rollback. docker save/load permite transporte con SHA256 e IDs verificados.

## 3. Preparación privada nueva

Responsables entregan por canal privado:
- Client ID y Client Secret CU del ambiente elegido en archivos0400/0600. Confirmar algoritmo; plantilla HS256 requiere≥32bytes.
- Mapa público **ACK SIGER** `{"key-id":"-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----\n"}`. Ed25519 e identificador exacto institucional; nunca inventar confianza.
- Avisos/versiones/autorizaciones y respaldo de configuración/claves. Nada privado por Git/chat.

```bash
sudo docker pull eclipse-temurin:25-jre@sha256:ecdc0adb37bde1a281c4d245a3cd535004b1fc8322d560f5b09463b248c81df0
sudo python3 scripts/ubuntu/preparar.py /srv/ddn-formulario-candidato --commit "$REV" --cu-client-id /ruta/privada/cu-client-id --cu-secret /ruta/privada/cu-secret --institutional-trust /ruta/privada/ack-trust.json
sudo docker compose --env-file /srv/ddn-formulario-candidato/compose.env -f infra/ubuntu/compose.yml config --quiet
```

Exige root para bind mounts uid1000/app,uid999/MySQL; comprobar UID de imagen antes. No altera cuentas host. Rechaza destino existente, Git/enlaces. **No sirve para actualizar ni trasladar históricos.** Fallo conserva destino parcial privado.

| Ruta | Contenido |
|---|---|
| compose.env | Rutas, SHA/imagen, puerto; sin contraseñas |
| secrets | Password raíz/migrador/app, CU, tokens operacional/integración, contraseña PKCS12 |
| config/application.properties | Configuración externa |
| keys | Ed25519 nueva de recibos, confianza propia/ACK recibida, clave antiabuso |
| pki/authority | CA privada origen, solo root, nunca montada |
| pki/app | PKCS12 SAN app/localhost/127.0.0.1; truststore **CA públicas JRE + CA privada** |
| pki/mysql | Certificado SAN mysql/localhost/127.0.0.1, clave uid999 |
| pki/proxy | Certificado formulario.defensorianinez.gob.cl, clave/CA |
| mysql-init | DDL/contraseñas solo para primer inicio de volumen vacío |
| store,ipc | POSIX0700 uid1000; binarios privados |
| clamav | Vacío; completar firmas reales |

`changeit` protege almacén de CA públicas, no claves privadas. CA/certificados397días: gestionar renovación. No instala CA global host/Java. Material institucional aprobado puede sustituirlos en un GATE; no reutilizar claves Sandbox.

### Antivirus

Aprovisionar clamav con firmas confiables actuales (main/daily/bytecode .cvd/.cld), uid1000/permisos lectura. Directorio RO para clamd. Nunca archivos vacíos/mocks.
Actualizar con freshclam oficial en entorno/contenedor autorizado sin documentos; validar firma/fecha, preparar lote nuevo y conservar anterior. Coordinar reinicio solo antivirus/app si corresponde, nunca MySQL. Política de actualización/alerta por firmas vencidas requerida antes de recepción. TCP3310 solo red interna.

Inicialización reproducible de firmas, antes del primer arranque, con freshclam incluido en la imagen construida:

```bash
sudo docker run --rm --user 1000:1000 --read-only --cap-drop ALL --security-opt no-new-privileges:true --memory 1g --tmpfs /tmp:uid=1000,gid=1000,mode=0700 --mount type=bind,src=/srv/ddn-formulario-candidato/clamav,dst=/var/lib/clamav --mount type=bind,src="$PWD/infra/ubuntu/freshclam.conf.example",dst=/etc/freshclam.conf,readonly --entrypoint /usr/bin/freshclam "ddn-clamav:$REV" --config-file=/etc/freshclam.conf --stdout
```

Requiere egreso HTTPS/DNS al servicio oficial de definiciones; no procesa ni transmite documentos. Si hay fallo/cooldown, conservar diagnóstico y reintentar según proveedor, no desactivar inspección. Para actualizaciones posteriores usar staging nuevo, validar y coordinar cambio/recreación; no ejecutar este comando sobre las firmas usadas por clamd sin procedimiento.

## 4. MySQL y migración explícita

```bash
sudo docker compose --env-file /srv/ddn-formulario-candidato/compose.env -f infra/ubuntu/compose.yml up -d mysql
sudo docker compose --env-file /srv/ddn-formulario-candidato/compose.env -f infra/ubuntu/compose.yml ps
sudo docker compose --env-file /srv/ddn-formulario-candidato/compose.env -f infra/ubuntu/compose.yml --profile migracion run --rm migrar
```

Salud SELECT1 con app y TLS VERIFY_IDENTITY. Migrador `java -jar /app/formulario.jar --migrate`/cuenta formulario_migrador/`MIGRATION_DB_PASSWORD_FILE`; sin secretos en argumentos. Runtime solo SELECT/INSERT/UPDATE/DELETE sobre formulario_publico; DDL migrador solo allí. Root no es runtime.

Comprobar12 filas Flyway exitosas/controles false,false,SOLO_CONSULTA. No editar V1–V12, usar repair o probar sobre esquemas existentes. App no migra automáticamente; repetir migrador valida checksums.

## 5. Arranque cerrado

```bash
sudo docker compose --env-file /srv/ddn-formulario-candidato/compose.env -f infra/ubuntu/compose.yml up -d inspector antivirus app proxy
sudo docker compose --env-file /srv/ddn-formulario-candidato/compose.env -f infra/ubuntu/compose.yml ps
sudo python3 scripts/ubuntu/estado.py /srv/ddn-formulario-candidato
sudo curl --fail --cacert /srv/ddn-formulario-candidato/pki/proxy/ca.crt --resolve formulario.defensorianinez.gob.cl:24443:127.0.0.1 https://formulario.defensorianinez.gob.cl:24443/api/session
```

Navegador remoto requiere GATE de acceso/trust; loopback de otro equipo no es accesible. No cambiar hosts/firewall ni usar -k para declarar TLS correcto.
ProxyHTTPS→SpringHTTPS/JDBC VERIFY_IDENTITY. Spring ve TLS real; forward-headers-strategy=none no simula seguridad con encabezados. Caddy verifica app/CA, MySQL mysql/CA. Cookies Secure/HttpOnly/SameSiteLax. Solo proxy127.0.0.1; inspector sin red, MySQL/AV internos.

PUT `.../cargas/{id}/bloques/{index}` limitado8MiB; demás26MiB. No POST150/250MiB. Canal clásico10MiB/archivo conservado; con multimedia habilitada nuevos multipart clásicos409. Sin ampliar límites.
Inspector debe acreditar LANDLOCK3_SECCOMP; AV firmas reales/salud. Si fallan no arranca app; no sustituir por dobles/abrir recepción.

## 6. Configuración y activación posterior

Archivo externo prevalece sobre defaults; configtree /run/secrets. No passwords en JDBC/env/Dockerfile. Tokens PULL/operación y claves recibos/ACK/garantía separados.

Candidato: formulario.habilitado=true carga componentes; recepción adulta/NNA, avisos/control BD cerrados. Multimedia=true prepara perfil inspeccionado **sin presentación**. PISEEfalse; Graph no incorporado. `ciclo-vida.online-habilitado=false`/`ciclo-vida.eliminacion.habilitada=false`, GARANTIA_INDEPENDIENTE/riesgo ACKfalse.

CU real del ambiente elegido; callback-approved=true es declaración operacional a contrastar con registro proveedor, no certificación. Smoke no consulta proveedores.
Futuros GATES validan avisos/capacidad/restore/proveedores/booleanos. Activación mediante PUT /operacion/v1/control autenticado, If-Match vigente y aceptarNuevos/recuperar/contingencia/motivo. No UPDATE manual. Coordinar SIGER/sintéticos autorizados. [Integraciones](INTEGRACIONES_UBUNTU.md).

## 7. Actualización/rollback

Registrar SHA/IDs/digests, huella/config privada, volumen, controles/conteos y backup. Coordinar PULL SIGER; cerrar recepción/PULL si ventana requiere, drenar leases, detener solo app candidata. No tocar WSL/GATE4A.
Construir imágenes inmutables conservando anteriores/env fuera de Git. Revisar migraciones/compatibilidad/restore antes del migrador. Cambiar tags y recrear app; workers/proxy solo si cambian. Verificar salud/TLS/JS/controles/conteos antes de reabrir conforme GATE.
Rollback: env/config anterior e imagen compatible. **No revierte Flyway ni contenido minimizado.** No regenerar claves/sobrescribir datos o bajar migraciones. Esquema incompatible: conservar y recuperar destinos nuevos/corte coordinado; jamás dos escritores/consumidores en mismo almacén.

## 8. Respaldo y restauración

media-archive.py verifica BD+objetos/derivadas/bloques/temporales, hashes, referencias FILE/BLOB. Requiere app detenida/lock exclusivo; mantenimiento online no sustituye backup. Destino externo protegido.

```bash
MYSQL_ID=$(sudo docker compose --env-file /srv/ddn-formulario-candidato/compose.env -f infra/ubuntu/compose.yml ps -q mysql)
sudo docker compose --env-file /srv/ddn-formulario-candidato/compose.env -f infra/ubuntu/compose.yml stop app
sudo python3 scripts/linux/media-archive.py --container "$MYSQL_ID" backup --store /srv/ddn-formulario-candidato/store --database formulario_publico --destination /respaldo-externo/formulario/ingreso-NUEVO
sudo docker compose --env-file /srv/ddn-formulario-candidato/compose.env -f infra/ubuntu/compose.yml up -d app
sudo python3 scripts/linux/media-archive.py --container "$MYSQL_ID" restore --source /respaldo-externo/formulario/ingreso-NUEVO --destination /srv/restauracion-NUEVA --database formulario_restore_nuevo
```

Restore exige BD/directorio nuevos, sin tráfico/PULL. Root.cnf restringido solo administra backup/restore. Manifiesto **excluye claves**: copia separada cifrada/autorizada firma/confianza histórica/antiabuso/config. Verificar recibos/hashes/accesoSIGER en otro host/medio. Otra carpeta SSD no acredita independencia.

## 9. Capacidad y diagnóstico

MySQL en volumen, FILE en store; scratch/tmpfs usa RAM. Logs3×10MiB/servicio, binlogs3días/segmento64MiB; días no es techo fijo de bytes. No borrar binlogs manualmente ni guardar builds/backups ilimitados.
Spring DataSize10GB=10GiB. Reservas contables, bytes lógicos y libre físico son distintos. Guardia4GiB/mínimo multimedia2GiB son pisos, no partición. Umbrales propuestos advertencia20%libre/70%cuota, crítica10%libre/85%cuota o atraso ACK/cola; medir/ajustar.

estado.py consulta control/custodia agregado, token stdin. Con mantenimiento online deshabilitado, custodia responde503 y el script lo informa sin activar nada. Complementar df/du/docker system df y [medición](../scripts/linux/media-storage-report.py). Sin notificaciones externas.
25GB exige builds/backups externos, SO/imágenes/DB/binlogs medidos y retención acotada. No prometer crecimiento estable si SIGER cae o retiradas están cerradas. DDN-UBUNTU reportado~98GiB raíz/~65GB libre; distinguir escenario e inventario. Calificación física de máximos multimedia pendiente.
