# RC1 — operación, respaldo, actualización y reversión

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
  --key-file ARCHIVO_CLAVE_RESPALDO --output SALIDA_NUEVA.gpg --maintenance
```

La ventana de mantenimiento detiene solo app/nodo/proxy del proyecto elegido para una instantánea consistente entre SQL, documentos y estado del nodo. MySQL permanece operativo para el dump transaccional; los escritores previamente activos se reanudan incluso al fallar. Los proyectos históricos conocidos están protegidos explícitamente. Antes de usarlo TI debe verificar que PISEE_STATE_DIR está dentro del directorio respaldado del nodo; si el estado está fuera, incluirlo en la custodia del nodo o preparar una copia consistente adicional durante la misma ventana. No hacer un backup de archivos con escritores activos.

Incluye SQL de formulario_publico, documentos/custodia, claves y confianza históricas, config, secretos, PKI, CA, truststores, inicialización MySQL, definiciones AV y runtime/config/estado PISEE. No incluye logs ni IPC efímeros. Las imágenes se conservan separadamente por digest y archivo SHA-256. No se guardan datos de otras campañas. Guardar dos copias cifradas fuera del host, verificar descifrado periódico y documentar custodios, RPO/RTO y rotación. RC1 acredita restauración local sintética, no pérdida física del centro de datos ni SLA.

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
