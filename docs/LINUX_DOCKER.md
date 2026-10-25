# Laboratorio Linux y despliegue pendiente

> **Guía de preparación histórica del 23/10.** Para el código actual, comenzar por [README](../README.md), [arquitectura V10](ARQUITECTURA_RECEPTOR_TRANSITORIO.md) y [operación multimedia](OPERACION_MULTIMEDIA.md). No repetir instalaciones en laboratorios preparados. Sus versiones y verificaciones de infraestructura corresponden a esa fecha.

Esta guía añade Linux sin sustituir las instrucciones Windows. Fecha de verificación: 23-10-2026. Ubuntu 24.04.5 WSL2 x86-64 y systemd. Git/SSH existentes se reutilizaron sin cambios. No se publicó WSL ni se alteró DNS público. Tras autorización expresa posterior se configuraron hosts y confianza de desarrollo del usuario Windows: [HTTPS Sandbox real y reversión](SANDBOX_HTTPS_WSL_2026-10-23.md).

## Herramientas

Verificadas: Temurin 25.0.5+8, Maven 3.9.15, Node 24.22.1, npm 11.19.1, Docker 29.9.2, Compose 5.6.1, OpenSSL, Python 3.12, ripgrep y Poppler. Antes de instalar, usar `command -v`, `java -version`, `mvn -version`, `node --version`, `docker info`, `docker compose version` y comprobar systemd/disco/permisos. Un `mvn` o `npm` ubicado bajo `/mnt/c` no acredita un runtime Linux.

Se usaron repositorios oficiales con claves limitadas a `/etc/apt/keyrings`, sin ejecutar descargas mediante `curl | bash`: [Temurin Linux](https://adoptium.net/installation/linux), [Docker Ubuntu](https://docs.docker.com/engine/install/ubuntu/). No instalar otro motor si Docker Desktop/WSL ya funciona. Los archivos binarios Node 24 y Maven 3.9.15 se descargaron de distribuciones oficiales y comprobaron con SHA-256/SHA-512 respectivamente. Node/Maven se ubicaron en `~/.local/opt` con enlaces en `~/.local/bin`. No se modificó identidad Git, SSH ni truststores Java. La actualización de certificados del paquete Ubuntu corresponde al mantenimiento normal de sus paquetes, no a una CA institucional nueva.

## Compilación

```bash
cd /home/nexbyteproveedor/proyectos/formulario-web
bash scripts/linux/compilar.sh
# Genera formulario-publico/backend/target/formulario-publico-0.1.0-SNAPSHOT.jar
docker build -t siger-formulario:linux-20261023 .
```

El build ejecuta lint, pruebas y TypeScript/Vite antes de Maven. React se empaqueta dentro del JAR. Docker usa Node 24 y Maven/JDK 25 solo en etapas de construcción; el runtime contiene Java 25 JRE, JAR y curl de healthcheck. UID/GID 1000, sin Node/Vite permanente. Se corrigió una colisión con el UID 1000 ya presente en la imagen Temurin; no se necesita recrear ese usuario. `.dockerignore` excluye secretos, material reservado y estado local.

## Bootstrap cerrado para revisar el JAR

```bash
python3 scripts/linux/preparar-app-cerrada.py /ruta/institucional/DefensoriadelaNinez.txt /ruta/privada/app-cerrada
export APP_PRIVATE_DIR=/ruta/privada/app-cerrada
docker compose -f infra/compose.app-lab.yml config --quiet
docker compose -f infra/compose.app-lab.yml up -d --wait
# Revisar http://localhost:18080/?vista=inicio
docker compose -f infra/compose.app-lab.yml stop
```

Este bootstrap selecciona únicamente el par Sandbox real, conservando redirect/logout registrados. Callback **bloqueado**, NNA/recepción cerrados y SRCeI deshabilitado en la aplicación; no genera identidades. Configuración privada 0600/directorio 0700, montada de solo lectura y nunca incorporada a imagen. Puerto host **127.0.0.1:18080** (`APP_LAB_PORT`, debe coincidir con `--puerto` del preparador). HTTP permite revisar recursos públicos; no acredita un flujo ciudadano HTTPS ni envío. El healthcheck verifica `/api/session`, no persistencia o disponibilidad de proveedores. Se eligió 18080 tras detectar otro servicio Windows en 8080; no se detuvo ni modificó ese servicio.

El bootstrap anterior sigue siendo cerrado por defecto. La variante HTTPS autorizada resolvió después el hostname y comprobó dos ingresos Sandbox reales con sesión y logout. Su configurador exige preflight antes de habilitar; no sustituye callbacks por localhost. QA y producción no se habilitaron ni se copiaron sus secretos. El Compose monta únicamente `application.properties`, sin exponer sus respaldos privados al contenedor.

## MySQL 8.4 aislado de pruebas

```bash
python3 scripts/linux/preparar-mysql-lab.py /ruta/privada/mysql-lab
export MYSQL_PRIVATE_DIR=/ruta/privada/mysql-lab
docker compose -f infra/compose.mysql-lab.yml up -d --wait mysql
bash scripts/linux/probar-mysql.sh "$MYSQL_PRIVATE_DIR" --autorizar-laboratorio
```

Solo crea `robust_public_test`, cuenta DML y migrador separado. La batería elimina datos exclusivamente de ese esquema autorizado; no apuntarla a bases con valor. El puerto **127.0.0.1:13306** reproduce la excepción local de TLS ya existente, sin ampliar sus condiciones. Fuera de ese laboratorio, JDBC continúa exigiendo `sslMode=VERIFY_IDENTITY`.

Secretos aleatorios separados en archivos privados. El SQL de inicialización contiene esos secretos: permanece fuera de Git, dentro de un directorio host 0700, y se monta individualmente de solo lectura para el usuario MySQL 999. Es 0444 porque el bind mount de Compose no remapea UID; el directorio padre impide lectura a otros usuarios host. No publicar ni adjuntar ese SQL.

El primer intento mostró que un directorio 0700 del usuario host impedía al UID 999 leer las claves desde un script `.sh`. Se corrigió el montaje y se completó la inicialización del volumen sin borrarlo ni recrearlo. El preparador final genera directamente el SQL individual. Se verificó además una inicialización desde un volumen nuevo del proyecto separado `siger-mysql-initcheck`, sin publicar puertos: esquema y dos cuentas creados correctamente; quedó detenido conservando su volumen de prueba. Nunca solucionar estos fallos con `down -v`.

## Respaldo y restauración realmente ejecutados

```bash
bash scripts/linux/respaldar-mysql-lab.sh "$MYSQL_PRIVATE_DIR" --autorizar-laboratorio
docker compose -f infra/compose.mysql-lab.yml --profile restore up -d --wait mysql-restore
bash scripts/linux/restaurar-mysql-lab.sh /ruta/al/respaldo.sql.gz --autorizar-laboratorio
# Reiniciar conserva el volumen:
docker compose -f infra/compose.mysql-lab.yml restart mysql
docker compose -f infra/compose.mysql-lab.yml up -d --wait mysql
```

El respaldo usa `--single-transaction --quick`, incluye rutinas/triggers/eventos, comprueba gzip y genera SHA-256. El restaurador exige un destino sin tablas y rehúsa reemplazar datos. Solo restaura en `mysql-restore`, sin puertos publicados, con volumen separado. Se compararon cuerpos, ACK, estados, huellas de solicitud y el historial Flyway con el origen; además se verificó la conservación tras reiniciar el MySQL origen. La instantánea contenía una entrega sintética durable. Prueba **de base de laboratorio**, no restauración integral del sistema institucional ni simulación de pérdida eléctrica.

Para una nueva repetición no limpiar un destino que ya contiene datos: crear otro proyecto/volumen de laboratorio identificado expresamente y conservar el anterior según necesidad. `stop`/`start` y `down` sin `-v` conservan volúmenes; no borrar secretos ni regenerar claves asociadas a registros existentes.

## Servicios y recursos

| Servicio | Puerto host | Persistencia | Límite |
|---|---|---|---|
| app cerrado | 127.0.0.1:18080 | Configuración privada externa | 2 CPU / 1024 MiB |
| mysql laboratorio | 127.0.0.1:13306 | Volumen `siger-mysql-lab_mysql-lab` | 2 CPU / 1536 MiB |
| mysql restauración | ninguno | Volumen `siger-mysql-lab_mysql-restore` | 1 CPU / 1024 MiB |
| PISEE DEV | ninguno | Estado Linux en directorio privado | 1 CPU / 512 MiB |
| Caddy Sandbox | 127.0.0.1:443 | Solo hoja TLS y clave privadas readonly | 0,5 CPU / 128 MiB |

Son Compose separados de laboratorio, **no** un Compose productivo integrado. El app cerrado no consume el MySQL ni consulta al nodo. PISEE usa red con salida y sin puertos host. Cada archivo tiene healthcheck; app/MySQL rotan logs, PISEE no los persiste para evitar cuerpos del proveedor. Las dependencias/readiness de un despliegue integrado siguen pendientes.

## Traslado a Ubuntu Server y límites actuales

No basta copiar contenedores. Trasladar con custodia autorizada: instantánea consistente de base, estado necesario del nodo, certificados activos y confianza, configuración por ambiente, claves privadas de firma y sus claves públicas históricas, y adjuntos cuando existan. Respaldos y secretos deben cifrarse y mantenerse fuera del host; la copia local aquí usada **no** satisface ese requisito institucional.

Pendientes antes de producción: Compose integrado app/MySQL/PISEE con readiness, TLS de MySQL verificado dentro de Docker, entrada HTTPS institucional (la local Sandbox ya está probada con confianza de proxy acotada), habilitación funcional de NNA/adulto declarado, protección antiabuso, almacenamiento/escaneo de adjuntos, prueba integral con consumidor y nueva representación de campos, restauración conjunta de datos/claves/adjuntos, monitoreo y runbooks institucionales. Aún no hay paquete Cloudflare Tunnel listo; no se creó túnel ni DNS.

Para actualizar, respaldar primero y construir una etiqueta nueva; conservar imagen/configuración anterior. Ejecutar Flyway con cuenta migradora separada antes del proceso DML, revisando compatibilidad de cada migración. Rollback de JAR no implica rollback SQL: no deshacer migraciones ni reescribir sobres firmados. No introducir datos de nuevos contratos hasta que su lector compatible esté disponible. Los Compose actuales se detienen con `docker compose -f ARCHIVO stop`, preservando datos.
