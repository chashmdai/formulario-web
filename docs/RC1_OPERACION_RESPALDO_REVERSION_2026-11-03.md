# RC1 — operación, respaldo, actualización y reversión

**Instalación definitiva ya ejecutada:** [rutas, herramientas y recuperación comprobada](INSTALACION_PRODUCTIVA_EJECUTADA_2026-11-03.md). Se restauró también un respaldo real de la instalación preparada: veinte tablas idénticas y persistencia después del reinicio de un MySQL independiente. Las pruebas sintéticas históricas indicadas más abajo se mantienen como evidencia complementaria.

Complementa la guía TI. El alcance actual es privado; no ejecutar estas instrucciones sobre certificación ni campañas históricas. Los comandos de ejemplo requieren las rutas definitivas revisadas por TI. No copiar valores secretos a terminal, informes o tickets.

## Comando de operación

Definir un wrapper privado con `docker compose --env-file RUTA_ENV -p PROYECTO -f RUTA_REPO/infra/ubuntu/compose.yml -f RUTA_REPO/infra/ubuntu/compose.production.yml`. El laboratorio real añade exclusivamente su overlay privado de campaña. No mezclarlo con stub ni usar el entorno de U4. Verificar el proyecto antes de cualquier mutación; nunca `down -v`, podas de volúmenes, reset o limpiezas destructivas.

Antes/después: inventario de IDs/digests, start/restarts, montajes y hashes de configuración; puertos efectivos y redes. No imprimir `docker inspect` completo, `compose config` completo, propiedades, logs HTTP o SQL con datos. El preflight RC1 produce solo resultado agregado. Verificar `/api/session` con CA y hostname, capacidades y controles persistidos; no usar `-k`.

Los seis servicios persistentes tienen `unless-stopped`, límites y healthchecks. Para recuperación automática tras arranque, Docker debe estar habilitado en la VM definitiva y los bind mounts/discos disponibles antes del daemon. Un servicio detenido explícitamente por mantenimiento permanece detenido hasta `compose up -d --wait`; documentar esa ventana. Un estado unhealthy requiere alerta/intervención, no un bucle de reinicio que oculte errores. El reboot del anfitrión NO se ensaya en DDN-UBUNTU porque aloja la certificación activa.

## Respaldo consistente y cifrado

`scripts/ubuntu/respaldo.py` exige salida NUEVA fuera del origen y clave externa 0400/0600 >=32 bytes, custodiada separadamente. GPG AES256 con protección de integridad; SHA-256 externo y manifiesto interno por archivo. Temporales privados 0700 y permisos 0600. No confiar solo en un hash como autenticación de un archivo sin cifrado.

```sh
sudo python3 scripts/ubuntu/respaldo.py backup \
  --private-dir DIRECTORIO_PRIVADO --project PROYECTO \
  --pisee-private-dir DIRECTORIO_NODO --pisee-node-config CONFIG_NODO \
  --pisee-state-dir ESTADO_PERSISTENTE_NODO \
  --key-file ARCHIVO_CLAVE_RESPALDO --output SALIDA_NUEVA.gpg --maintenance
```

La ventana de mantenimiento detiene solo app/nodo/proxy del proyecto elegido para una instantánea consistente entre SQL, documentos y estado del nodo. MySQL permanece operativo para el dump transaccional; los escritores previamente activos se reanudan incluso al fallar. Los proyectos históricos conocidos están protegidos explícitamente. El argumento de estado debe coincidir con PISEE_STATE_DIR; se incluye incluso cuando está fuera del runtime. No hacer un backup de archivos con escritores activos.

Incluye SQL de formulario_publico, documentos/custodia, claves y confianza históricas, config, secretos, PKI, CA, truststores, inicialización MySQL y runtime/config/estado PISEE. La política actual de contenido regenerable y capacidad se detalla a continuación. No incluye logs ni IPC efímeros. Las imágenes se conservan separadamente por digest y archivo SHA-256. No se guardan datos de otras campañas. Guardar dos copias cifradas fuera del host, verificar descifrado periódico y documentar custodios, RPO/RTO y rotación. RC1 acredita restauración local sintética, no pérdida física del centro de datos ni SLA.

### Actualización U8a — capacidad y retención Linux (15/11/2026)

**Estado informado por el operador:** en la partición productiva de 98 GiB, las
copias diarias agotaron espacio; el timer está **DESHABILITADO** y las copias
acumuladas se retiraron manualmente. No asumir un respaldo reciente. Este cambio
prepara código y pruebas sintéticas: no habilita el timer ni acredita un respaldo
productivo nuevo, una copia externa o un restore físico. La referencia al timer
habilitado en el checkpoint del 04/11 describe aquel corte histórico.

El formato nuevo es **TAR → gzip → GPG simétrico AES256**, directamente al archivo
cifrado 0600. No se copia el árbol ni se crea un TAR en claro. Sólo el dump SQL
utiliza un temporal 0600 dentro de un directorio 0700, eliminado al salir tanto por
éxito como por fallo; también se elimina una salida cifrada incompleta propia.
El manifiesto SHA-256 por archivo se calcula sobre los bytes enviados al TAR y va
dentro del cifrado (manifiesto V2; lectura compatible con V1 histórico). `verify` comprueba el SHA externo, descifra hasta EOF (incluida
la integridad GPG) y coteja **todos los archivos** con el manifiesto, sin extraerlos
al disco. El programado exige esta verificación antes de reabrir por CAS y de retener.
`restore` acepta tanto este gzip como el TAR antiguo sin comprimir dentro de GPG;
conserva destino nuevo, rechazo de enlaces/dispositivos/traversal y saneamiento de
modos/propietarios. No importa SQL ni inicia servicios.

Política de categorías (sólo se omite lo identificado explícitamente):

| Categoría | Contenido y criterio |
| --- | --- |
| Obligatoria: identidad | `secrets`, `cu`, `keys`, `pki`, `intercambio-publico`, `acceso-temporal/`. Incluye la contraseña temporal, su hash, apertura, claves de firma y confianza histórica, CA y certificados. |
| Obligatoria: configuración | `config`, `compose.env`, `installation.json`, `compose.origin-ca.yml`, `authorization`, `transition`, `mysql-init`, credenciales y configuración en `mysql/`. |
| Obligatoria: documentos y base | Todo `store/` y dump transaccional de `formulario_publico`, con rutinas, triggers y eventos. Nunca confundir adjuntos MySQL con contenido regenerable. |
| Obligatoria: PISEE | Runtime completo, identidades y criptografía, configuración y estado persistente explícitos; se omiten únicamente sockets IPC recreables. No incluye otras campañas. |
| Excluible: ClamAV | Firmas regenerables `main/daily/bytecode.cvd` o `.cld`. Otra configuración/contenido se conserva. Reponer y validar firmas antes de arrancar una restauración. |
| Excluible: datadir | Sólo un bind mount identificado por Docker como `/var/lib/mysql` **subordinado a `mysql/`**; nunca `mysql/` entero ni sus `.cnf`. Si no se identifica con seguridad, se conserva. Los volúmenes externos no forman parte del árbol. Recuperar MySQL nuevo desde init/config/secretos y `database.sql`, no desde una copia en caliente del datadir. |
| Excluible: release/software | Sólo archivos cuyo SHA-256 coincide con `installation.json.backupReproducibleFiles`. Sin mapa, entrada o coincidencia, se incluyen. Un digest de imagen por sí solo no identifica los archivos. |

`backupReproducibleFiles` es un mapa opcional de ruta relativa exacta bajo
`release/` o `software/` a SHA-256 hexadecimal minúsculo de 64 caracteres. El
operador sólo debe declararlo después de asegurar la disponibilidad externa del
artefacto reproducible por esa huella. Las claves, documentos, configuración y
PISEE nunca se excluyen por ese mapa. El mapa se conserva dentro del respaldo.

Antes de una ventana autorizada, consultar capacidad con los mismos argumentos
de origen/PISEE y una salida en el filesystem que contendrá la copia:

```sh
python3 scripts/ubuntu/respaldo.py estimate \
  --private-dir DIRECTORIO_PRIVADO --project PROYECTO \
  --pisee-private-dir DIRECTORIO_NODO --pisee-node-config CONFIG_NODO \
  --pisee-state-dir ESTADO_PERSISTENTE_NODO --output SALIDA_NUEVA.gpg
```

`estimate` sólo lee archivos/metadatos locales y metadatos SQL mediante Docker:
no hace dump, no detiene servicios y no modifica controles ni escribe una copia.
Su JSON muestra bytes por categoría, bytes excluidos, estimación del archivo,
temporal SQL, espacio libre, factor y reserva; no muestra rutas, nombres de
archivos ciudadanos, filas ni secretos. `reproducible` cuenta los binarios/AV
**conservados**, `excludedRegenerable` cuenta los omitidos. La estimación no
presupone compresión: suma TAR/manifiesto/cabeceras y margen, con SQL estimado a
partir de `data_length + index_length` × 6 + 16 MiB (hex/escapes/DDL). Es una
estimación conservadora, no un tamaño exacto del dump ni una garantía de RPO.
El temporal SQL tiene como límite ese presupuesto: si el dump lo excede, se
cancela, se elimina el temporal y se conserva el cierre para revisión de capacidad.

`backup` comprueba capacidad antes de detener escritores y de nuevo con el tamaño
SQL real. Exige `libre >= estimatedArchiveBytes × factor + reserva + temporalSQL`.
El factor es `--capacity-factor` (por defecto **1.25**, mínimo 1); la reserva es
`--reserve-bytes` (por defecto **max(10 GiB, 10% del filesystem)**, mínimo 0).
La reserva corresponde al filesystem de salida, no a un umbral fijo en el origen.
Cambiar estos márgenes requiere evaluar capacidad; una reserva 0 sólo se usa en
fixtures. El programado propaga ambas opciones y cierra controles previamente:
si falta capacidad/falla copia o verificación, mantiene recepción/PULL cerrados;
tras éxito y salud, restaura únicamente con el ETag exacto del cierre.

La retención está **apagada por defecto**. Sin `--keep`, no se elimina ningún
respaldo. `respaldo-programado.py --keep N` exige N ≥ 2 y sólo retiene después de
verificar el nuevo respaldo, reanudar salud, restaurar el CAS previo y registrar
`backup-last-success.json`: un fallo de retención se informa como `retentionError`
(solo la clase del error) con código de salida 3, sin dejar la recepción cerrada. Protege siempre el recién verificado
(incluso ante nombres con fecha futura), más las N−1 copias propias más nuevas.
Sólo considera archivos regulares del mismo directorio con nombre
`formulario-YYYYMMDDTHHMMSSZ.gpg`, `.gpg.sha256` coincidente y marca propia
`.gpg.verified.json` emitida tras verificación completa. No sigue enlaces ni adopta
copias antiguas sin marca o archivos ajenos. El contador entero
`retentionDeletesPerformed` registra **respaldos** eliminados, junto a sus dos
acompañantes; no cuenta cada archivo auxiliar. El registro local
`backup-last-success.json` indica además `fileManifestVerified`.

El `.service` distribuido conserva su ExecStart **sin `--keep`** y el `[Install]`
del timer no cambia. Para una futura decisión operacional de retener, preparar un
drop-in del servicio con el ExecStart completo actual más `--keep N` (systemd
requiere primero una línea `ExecStart=` vacía). Revisar custodios, capacidad,
RPO/RTO y copia externa antes de aplicar esa decisión. Esto no autoriza habilitar
el timer; su activación sigue pendiente de instrucción del operador. Las opciones
`--directory` y `--key-file` del programado permiten custodias externas distintas;
sus valores por defecto conservan las rutas operacionales existentes.

Para una comprobación manual independiente, sin arrancar ni restaurar archivos:

```sh
python3 scripts/ubuntu/respaldo.py verify --archive RESPALDO.gpg --key-file CLAVE_SEPARADA
```

## Restauración sin sobrescribir el origen

```sh
sudo python3 scripts/ubuntu/respaldo.py restore \
  --archive RESPALDO.gpg --key-file CLAVE_SEPARADA \
  --destination DIRECTORIO_NUEVO
```

Valida cifrado, hash externo, rutas TAR (sin enlaces, dispositivos, absolutos ni traversal), todos los hashes internos y propietarios del runtime. No arranca servicios ni importa SQL automáticamente. El destino debe no existir; nunca restaurar encima de producción.

1. Revisar la copia de `compose.env`: remapear rutas externas al destino nuevo, subred/IPs/hostname/puertos independientes; generar una config NUEVA del nodo con esas IPs, conservando criptografía. Crear IPC privados nuevos uid1000; conservar MySQL TLS uid999 y clave root para inicialización. No duplicar una identidad productiva activa en una red externa: la prueba aislada no arranca el nodo restaurado ni contacta PISEE/CU/SIGER.
2. Crear MySQL con volumen NUEVO usando inicialización y secretos recuperados. Comprobar que no contiene tablas de comunicaciones. Importar `database.sql` mediante stdin a `mysql --defaults-extra-file=... formulario_publico`, sin contraseñas en argumentos. No ejecutar DROP ni sustituir tablas existentes. No volver a migrar históricos con scripts diferentes.
3. Comprobar todas las tablas, versiones/checksums Flyway, estados, firma/hashes de documentos y originales, confianza de claves anteriores y controles CAS. Mantener recepción/PULL cerrados; ninguna cola se reclama para probar una restauración.
4. Reiniciar exclusivamente los servicios restaurados y repetir hashes/estados. Borradores de sesión HTTP no sobreviven al reinicio: caducan de forma segura; custodia SQL/archivos y entregas durables sí deben sobrevivir. No prometer continuidad de sesión ni guardar tokens OAuth para conseguirla.
5. Solo tras aprobación y plan de corte independiente, seleccionar el origen definitivo. No hacer activos simultáneamente dos escritores sobre el mismo almacén ni dos consumidores de la misma cola.

Prueba RC1 efectuada con SQL, archivo y claves exclusivamente sintéticos: backup cifrado → restauración a directorio nuevo → importación a MySQL nuevo sin puertos → comparación SQL/archivo → reinicio y persistencia. Proyectos de prueba nuevos, detenidos al cierre; volúmenes conservados. No se empleó la base del laboratorio con identidades reales.

## Actualización y rollback

1. Aprobar versión/digests y compatibilidad de esquema; inventariar origen. Cerrar admisión/PULL mediante CAS con revisión esperada, esperar leases y resolver operaciones ambiguas antes del mantenimiento. No interpretar timeout como ausencia de COMMIT institucional.
2. Respaldo consistente verificado, incluyendo claves/confianza históricas. Registrar digest anterior y configuración protegida. Importar imágenes nuevas; preflight sin imprimir secretos. Nunca construir imágenes en la VM productiva durante el arranque.
3. Ejecutar migrador inmutable con credencial separada si hay migraciones nuevas, después de revisar compatibilidad. V1–V12 no se editan. Crear app/nodo/proxy con `up -d --wait`, conservar volúmenes y claves. Healthchecks, TLS/SAN, MySQL, AV/Inspector, firmas, estado CAS y pruebas negativas antes de abrir controles.
4. Sin cambio incompatible de esquema, restaurar digest/configuración anterior y recrear solo servicios necesarios. Si hay esquema incompatible, NO degradar a ciegas: restaurar respaldo en otro volumen, conciliar COMMIT/ACK e idempotencia y aprobar la selección del origen. No perder comunicaciones aceptadas después del backup.
5. En el laboratorio actual, rollback a `compose.pre-rc1.env` + overlay productivo lab anterior reusa exactamente la red/IP anterior y configuración histórica privada; no elimina la nueva red ni datos. Solo app/nodo/proxy, nunca MySQL ni sitio público. Evitar que queden ambas instancias de nodo escribiendo el mismo estado.

## Certificados, alertas y mantenimiento

Inventario externo de SAN, emisor, huella, ruta y vencimiento (sin privados). Validar con `openssl x509 -checkend SEGUNDOS -noout -in CERTIFICADO_PUBLICO` y comprobación de cadena/hostname. Alertar a 60/30/7 días según TI; PKI PISEE/X509, CU/credenciales, Caddy, MySQL y app tienen ciclos independientes. La renovación utiliza custodia institucional; preservar confianza histórica de firmas Ed25519. No auto-renovar certificados PISEE ni reemplazar sus claves por las de Gobierno Digital.

ClamAV necesita firmas actualizadas por tarea TI con salida revisada y reemplazo atómico de definiciones, permisos uid1000, revalidación EICAR y reinicio controlado. No descargar firmas dentro del contenedor del sitio público. AV/Inspector no saludables, espacio libre insuficiente, firma inválida, cola atascada, consultas externas rechazadas, vencimientos y ACK ambiguo requieren alertas agregadas sin PII. RPO/RTO, guardias, capacidad y retención definitivos requieren aprobación y calificación institucional.
