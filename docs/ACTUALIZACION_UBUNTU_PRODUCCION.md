# Actualización de producción Ubuntu — procedimiento vigente

15/11/2026, America/Santiago. Procedimiento para actualizar `/srv/ddn-formulario-produccion` a un commit nuevo de `main`. **Este documento no se ha ejecutado en producción** y no autoriza por sí mismo la ventana: la ejecución requiere la autorización del operador y una ventana coordinada con SIGER.

Principios: misma instalación y dominio; se conservan identidades, firmas y confianza histórica, certificados, credenciales, MySQL y su volumen, documentos en custodia, PISEE, configuración privada y la **contraseña temporal entregada a Gobierno Digital**. No se regeneran claves, no se baja Flyway, no se borran volúmenes ni respaldos, no se reactiva el timer de respaldo y no se abre nada que hoy esté cerrado.

## 0. Qué cambia en esta versión

| Área | Cambio | Efecto operativo |
|---|---|---|
| Esquema | **V13** `origen_reserva_multimedia` (H2/MySQL): columna `citizen_journey.media_origin_ref` con valor por defecto e índices. Aditiva. | Requiere ejecutar el migrador. Los recorridos anteriores a V13 comparten el grupo de origen `000…` (24 reservas, 25% de la cuota) hasta vencer (4 h). |
| Multimedia (AUD-01/03/05) | Cuota por origen, vencimiento sin progreso (15 min) e inactividad (30 min), recuperación de inspecciones interrumpidas, permiso de análisis único. | Libera reservas sin `sanear` manual ni ciclo de vida. |
| Transferencias (X-01) | Lectura/escritura no bloqueantes; cupos `formulario.transferencias.*` (6 cargas/1 por origen, 8 descargas/2 por origen, pool exclusivo de 2 para SIGER); plazos totales. Caddy: `read_body 5m`, `write 15m`. | Recrear `app` **y** `proxy` (Caddyfile cambió). |
| Acceso temporal (AUD-02) | Contador por origen (`OrigenCliente`) y tope global `max-fallos-globales` (500/15 min). | Contraseña, hash, propiedades, aprobación y vencimiento **sin cambios**. `limites.json` antiguo se acepta. |
| ACK SIGER (C-02/AUD-04) | ACK válido aceptado aunque venza el lease o esté en CUARENTENA; reposición `POST /operacion/v1/formulario-completo/entregas/{id}/reintentar` (CAS de intentos). | Sin cambio contractual; PULL sigue efectivamente cerrado sin clave ACK. |
| Minimización (C-01) | `formulario.minimizacion-borradores.*`, **apagada**. | Activarla es decisión institucional ([política](MINIMIZACION_BORRADORES.md)). |
| Respaldos | Streaming cifrado, estimación, preflight de capacidad, retención opt-in `--keep`. | El timer permanece **deshabilitado**. |
| Imagen | Sin `curl` en el runtime; healthcheck Java `SaludHttp`. | `verificar-certificacion.py` ejecuta `curl` dentro del contenedor histórico de certificación: no actualizar esa instancia con esta imagen sin adaptar el script. |
| Contratos | Fixtures y nota aditivos NNA/SRCeI; 1.0/1.1 sin cambios. | Confirmar consumidor SIGER antes de abrir PULL. |

Propiedades nuevas (todas con valor por defecto seguro; no es necesario añadirlas a la configuración privada): `formulario.acceso-temporal.max-fallos-globales`, `formulario.multimedia.origen-bytes|origen-reservas|sin-progreso-ms|inactividad-ms`, `formulario.transferencias.*`, `formulario.minimizacion-borradores.*`. Ver `infra/ubuntu/application.properties.example`.

## 1. Preparación fuera de la VM productiva

1. Commit aprobado con CI remota verde en los tres jobs (Linux, Windows, Docker). Registrar el SHA completo.
2. Construir y empaquetar en un equipo de build, nunca en la VM productiva durante la ventana:
   ```bash
   python3 scripts/linux/empaquetar-release.py --commit <SHA> --destino /ruta/externa/release-<sha12>
   ```
   Se obtiene `imagen-app.tar.gz`, `fuente/`, `manifiesto.json` y `SHA256SUMS`, sin secretos. Si cambian `infra/sandbox` o `infra/media`, construir y exportar también `ddn-caddy`, `ddn-inspector` y `ddn-clamav` como en [instalación](INSTALACION_UBUNTU_DOCKER.md#2-fuente-y-build).
3. **Ensayo obligatorio de recuperación y migración en un entorno aislado** (equipo de laboratorio o host aislado, nunca la VM productiva):
   ```bash
   python3 scripts/ubuntu/simulacro-restauracion.py origen-lab --trabajo <dir> --jar-anterior <jar-desplegado>
   python3 scripts/ubuntu/simulacro-restauracion.py restaurar --trabajo <dir> --archivo <dir>/respaldos/formulario-lab-*.gpg \
     --clave <dir>/clave-respaldo --imagen <imagen-candidata>
   ```
   Debe informar `appSaludable: true`, `flywayFailed: 0`, la versión Flyway nueva y `coincideConOrigen: true`. Resultado de referencia: [simulacro del 15/11/2026](#anexo-simulacro-local-15112026).

## 2. Ventana productiva (como root en DDN-UBUNTU)

Variables de ejemplo: `R=/srv/ddn-formulario-produccion`, `U=$R/updates/remediacion-20261115` (privado, 0700).

1. **Inventario sin secretos y espacio** (partición de 98 GiB):
   ```bash
   install -d -m 0700 "$U"
   python3 <release>/fuente/scripts/ubuntu/preflight-actualizacion.py --root "$R" --snapshot "$U/preflight-antes.json"
   df -h "$R" /var/lib/docker; docker system df
   ```
   Debe terminar sin `blockers`. `backupTimer` debe seguir `disabled`. Si el espacio libre no cubre el respaldo estimado (paso 3) más la imagen nueva, **detener**: no borrar respaldos, imágenes ni volúmenes para hacer sitio sin autorización.
2. Registrar ID de imagen actual (`docker inspect` del contenedor `app`), `compose.env`, `installation.json` y el estado CAS (`GET /operacion/v1/control`) en `$U` (`*.before`). Coordinar con SIGER: no hay PULL efectivo sin clave ACK, pero registrar el estado.
3. **Respaldo manual previo obligatorio** (no existe uno reciente recuperable; el timer no se reactiva):
   ```bash
   python3 $R/release/scripts/ubuntu/respaldo.py estimate --private-dir "$R" --project ddn-formulario-produccion \
     --pisee-private-dir "$R/pisee/runtime" --pisee-node-config "$R/pisee/config-produccion.json" --pisee-state-dir "$R/pisee/state" \
     --output /srv/ddn-formulario-respaldos/manual/formulario-pre-remediacion.gpg
   python3 $R/release/scripts/ubuntu/respaldo-programado.py --root "$R" --directory /srv/ddn-formulario-respaldos/manual
   ```
   El segundo comando cierra el CAS, respalda en streaming, verifica descifrado completo y manifiesto, y restaura el CAS exacto. Sin `--keep` no borra nada. Copiar el `.gpg` y su `.sha256` a un medio **externo** a la VM antes de continuar.
4. Cerrar recepción/PULL por CAS (`productor.py --root "$R" close`) y esperar a que no haya leases activos.
5. Importar la imagen nueva (sin build en la VM):
   ```bash
   cd <release> && sha256sum -c SHA256SUMS && gunzip -c imagen-app.tar.gz | docker load
   ```
   Conservar la imagen anterior para reversión. Actualizar `release/` con `fuente/` del paquete, **sin tocar** `config/`, `secrets/`, `keys/`, `pki/`, `cu/`, `acceso-temporal/`, `pisee/`, `store/` ni `mysql*`.
6. Actualizar únicamente `FORM_IMAGE` (y `FORM_SOURCE_REVISION`) en `compose.env`. No ejecutar `preparar.py` (solo inicializa destinos nuevos) ni `acceso-temporal.py provision|rotate|configure`.
7. Migrar V13 con el migrador inmutable: `<wrapper compose de installation.json> --profile migracion run --rm migrar`. Si falla, **no** continuar: la base queda en V12 y la imagen anterior sigue siendo compatible.
8. Recrear solo lo necesario: `... up -d --no-build --wait app proxy`. No recrear `mysql`, `pisee`, `antivirus` ni `inspector` salvo cambio de imagen.
9. Verificar: `productor.py --root "$R" verify`; salud de los seis servicios; `preflight-actualizacion.py --root "$R" --compare "$U/preflight-antes.json"` **sin blockers** (identidades, certificados, credenciales, contraseña temporal, configuración de acceso temporal y vencimiento idénticos; custodia y estado PISEE sin pérdida). Comprobar con la contraseña temporal vigente que el acceso funciona.
10. Restaurar el CAS exactamente al estado registrado en el paso 2 (revisión esperada), no a uno distinto.

## 3. Preservación explícita

| Material | Ruta bajo `$R` | Regla |
|---|---|---|
| Firma Ed25519 y confianza histórica | `keys/receipt-key.pem`, `keys/receipt-trust.json`, `keys/institutional-trust.json` | No regenerar ni reemplazar. |
| Antiabuso | `keys/abuso.key` | Cambiarla invalida contadores y huellas. |
| Certificados | `pki/` (app, mysql, proxy, origen), `compose.origin-ca.yml` | Renovación solo por custodia institucional. |
| Credenciales | `secrets/`, `mysql/*.cnf`, `cu/` | Sin rotación en esta ventana. |
| Contraseña temporal | `secrets/acceso-temporal-password`, `keys/acceso-temporal-password.hash`, `keys/acceso-temporal-apertura.json`, `acceso-temporal/limites.json`, propiedades `formulario.acceso-temporal.*` (incl. `expira=2026-12-15T00:00:00-03:00[America/Santiago]`) | Intactas. El formato `pbkdf2-v5_8:` y la política de vencimiento no cambian. |
| MySQL | volumen `ddn-formulario-produccion_mysql-data` | Nunca `down -v`. Flyway solo avanza. |
| Custodia | `store/` | Sin limpieza manual. |
| PISEE | `pisee/runtime`, `pisee/state`, `pisee/config-produccion.json` | Sin reinicio salvo cambio de imagen del nodo. |

## 4. Reversión

- **Antes de migrar** (pasos 1–6): reponer `compose.env.before` y recrear `app`/`proxy` con la imagen anterior.
- **Después de V13**: la imagen anterior es compatible con V13 (columna aditiva con valor por defecto; el código anterior no la lee). Revertir solo `compose.env`, `release/` y el Caddyfile anterior y recrear `app`/`proxy`. No bajar Flyway ni restaurar la base salvo corrupción comprobada; en ese caso, restaurar el respaldo del paso 3 en un **destino nuevo** (`respaldo.py restore`) y conciliar ACK/COMMIT con SIGER antes de elegir origen.
- Reabrir el CAS al estado previo registrado.

## 5. Decisiones institucionales pendientes (no se activan aquí)

- **15/12/2026**: al vencer el acceso temporal con aprobación vigente pasa a `OPEN` (`AccesoTemporal`). Decidir renovación o aceptación de exposición antes de esa fecha.
- Riesgo 410 y **C-03** (oráculo de existencia/completitud): [documentados](RIESGO_410_Y_C03_ENUMERACION.md), sin cambio de comportamiento.
- Minimización de borradores (plazo y activación), retención de respaldos (`--keep` y reactivación del timer), presupuesto PISEE diario.
- Defensa en profundidad de `/integracion/**` (Access/mTLS/allowlist) antes de abrir PULL; clave ACK institucional y confirmación del consumidor SIGER 1.0/1.1.

## Anexo: simulacro local 15/11/2026

Ejecutado en el equipo de laboratorio CachyOS (no en DDN-UBUNTU), sin secretos institucionales ni proveedores reales. Evidencia **local**, no acreditación productiva.

- Origen LAB nuevo con `preparar.py --sin-claveunica --sin-confianza-ack` (identidades y certificados sintéticos), base migrada con el JAR del commit desplegable anterior (`5b17003`, esquema **V12**), 3 recorridos y 5 eventos sintéticos, un objeto en `store/` y estado PISEE simulado. Respaldo con `respaldo.py backup` (streaming cifrado) y `verify` completo.
- Restauración con `simulacro-restauracion.py restaurar` en el proyecto `ddn-simulacro-20261115t191414`: destino nuevo, MySQL nueva, importación del volcado, migrador de la imagen candidata (árbol de esta remediación) → **V13** sin fallos, `app` saludable con recepción/PULL/PISEE/CU/multimedia/ciclo de vida cerrados, redes internas, **sin puertos publicados**, conteos idénticos al origen (`coincideConOrigen: true`), 1 objeto restaurado con manifiesto verificado. 37 s. Se eliminó solo el volumen propio (1) y el contenedor de laboratorio preexistente siguió en ejecución.
- Hallazgo corregido durante el ensayo: `respaldo.py` exigía `installation.json`; un origen preparado sin instalar ahora conserva `release/` y `software/` completos (regresión en `test_ubuntu_backup.py`).
- Pendiente de acreditar: ensayo con un respaldo productivo real en un host aislado (requiere las superposiciones `compose.production.yml` y el nodo PISEE institucional), y la ventana productiva.
