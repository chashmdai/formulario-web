# Unidad RUN → PISEE/SRCeI automático — 24 de octubre de 2026

**Cerrada en el laboratorio integrado.** Se continuó desde `3b398d5`, sobre `integracion-siger-linux-2026`, con Git limpio antes de modificar. Se reutilizaron el formulario, la API de identificación, MySQL, el Nodo Linux y el cliente Java existentes. No hay rediseño, instalaciones, nuevas migraciones, cambios en SIGER institucional, push ni campaña de carga.

Implementación y evidencia documentada confirmadas localmente en **`4191804`**. El checkpoint incorpora esta referencia en el commit documental siguiente.

## Comportamiento implementado

`IdentificationControls.tsx` programa la revisión del presentador tras **650 ms** con número y DV válidos y estables. No envía una solicitud por tecla. El botón original se conserva como alternativa; ya no es necesario pulsarlo. Una promesa en curso evita duplicar entre disparador, botón y guardado, y una proyección concluida se reutiliza.

Antes de consultar, se restaura la persona por su recorrido/sesión. Una consulta vigente se sigue con GET; una concluida no vuelve a SRCeI. Se conservan las declaraciones propias. Al corregir el RUN se ocultan y retiran los datos anteriores, se confirma una única invalidación persistente antes de la nueva revisión y se conservan historial/cuotas. Incluso un nuevo RUN inválido retira el anterior. Durante la petición en curso las casillas quedan bloqueadas; el backend conserva su protección de reserva/lease para peticiones concurrentes de otras pestañas o clientes.

Se rechazan respuestas tardías tras desmontar el panel o cambiar su generación. Una restauración tardía no sustituye el RUN que comenzó a corregirse. Si falla restaurar, no se consulta automáticamente sobre un estado desconocido: el reintento confirma primero la invalidación. Un 409 de otra pestaña no se adopta ciegamente como respuesta del RUN escrito; se informa que debe recargarse. Un fallo HTTP no dispara un bucle de reintentos automáticos.

- **Adulto CU:** continúa la identificación automática existente al montar/restaurar. El backend deriva el RUN de la identidad OIDC y exige evidencia de sesión; el navegador no puede escogerlo ni autoafirmar autenticación. CU conserva prioridad y las discrepancias SRCeI permanecen privadas. La consulta solo ocurre si ese RUN válido está autorizado en DEV.
- **Adulto declarado:** aceptación/versionado del aviso antes de iniciar el recorrido; número/DV separados y módulo 11 antes del disparador. Resultado completo: no solicitar ni mostrar atributos disponibles. Parcial: pedir solo faltantes. Vacío, errores e indisponibilidad del proveedor: declaración manual con mínimos y procedencia conservados. La consulta no autentica al ciudadano.
- **NNA:** el RUN sigue siendo voluntario. Sin RUN no se consulta al proveedor. Con RUN válido se usa el disparador, sin exigir aceptación del **canal adulto**: el recorrido NNA conserva deliberadamente `aceptaCanal=false`. No se cambió su política de ingreso ni sus mínimos de primer nombre, primer apellido y región; comuna opcional. La provisión voluntaria del RUN, la sesión, el aviso vigente y la allowlist DEV acotan su consulta.
- **Terceros:** la allowlist de RUN no constituye autorización para buscar antecedentes de otra persona. `RecorridosJdbc` restringe el proveedor a `PRESENTADOR`; NNA afectados, adultos relacionados y señalados conservan RUN opcional y declaraciones. Las tarjetas no tienen disparador automático y tampoco consultan SRCeI al pulsar su botón. El texto aclara que se completan los antecedentes conocidos. No existe habilitación de búsqueda de terceros en esta unidad.

La proyección HTTP y el contrato firmado no cambian: campos faltantes/obligatorios, estados públicos, banderas y declaraciones propias; nunca un agregado de atributos registrales. Se mantienen propietario, CSRF, límites de tamaño, cuotas, anti-enumeración, fuentes individuales, discrepancias y timestamps/evidencias. No se cambió `IdentificacionCiudadana`, el parser XML ni los DTO/migraciones V1–V7 ya aplicados.

## DEV real, acotado y separado de los dobles

Se ejecutó **una consulta real autorizada desde el recorrido adulto declarado**, con el RUN leído exclusivamente de la allowlist privada. El resultado fue **COMPLETO**, con **10 atributos registrales conservados internamente**. El navegador recibió cero campos personales faltantes, ninguna declaración ni valor registral y un mensaje genérico. No hubo clic en «Revisar RUN». Al recargar, se restauró por GET sin otro POST de identificación.

En el mismo recorrido se probó, por la API protegida, que un tercero con un RUN de la allowlist **no** consultara al proveedor: quedó `NO_HABILITADO`, sin referencia de consulta ni atributos SRCeI. Se guardó y recuperó una declaración sintética propia de esa tarjeta. La comunicación de consulta se recibió realmente; la tarjeta inactiva quedó fuera de su custodia. Repetir el mismo cuerpo/clave conservó la referencia; cambiarlo con la misma clave devolvió 409. Sigue pendiente de registro SIGER, sin folio inventado.

Verificación independiente en MySQL/OpenSSL: RUN del presentador sigue declarado, no autenticado; atributos SRCeI con su referencia/fecha; una sola persona con evidencia de consulta; tercero solo declarado; captura persistida íntegra; huellas canónicas de contenido/procedencia y firma Ed25519 correctas. Los dos intentos válidos de RUN del recorrido consumen su contador (presentador y tercero), pero solo el presentador tiene consulta externa/evidencia.

La regresión móvil NNA usó el backend real y un RUN **sintético excluido de la allowlist**: primero sin RUN, después entrega voluntaria con disparador automático, guardado, recarga sin otro POST, declaraciones conservadas y recepción real. MySQL confirmó `NO_HABILITADO`, ninguna consulta externa, fuentes declaradas y `aceptaCanal=false` en la custodia. No se consultó dos veces al proveedor para comprobar NNA.

La primera prueba móvil encontró una condición añadida indebidamente que exigía el canal adulto a NNA. Se corrigió, se añadió regresión expresa con `aceptaCanal=false` y se volvió a verificar el recorrido. El borrador anterior se conserva; no consultó al proveedor. Una comprobación arrancada antes de que el JAR estuviera listo también se conserva como fallo de arranque de QA. No se presentan esos intentos como verificaciones satisfactorias.

No se repitió ClaveÚnica Sandbox: el enriquecimiento mixto CU/SRCeI, su prioridad y la derivación del RUN desde sesión se verificaron con dobles. La identidad Sandbox disponible no corresponde al RUN autorizado de esta allowlist; no se suplantó esa identidad ni se amplió la lista para forzar una consulta real. Los estados parcial/vacío/error/timeout y las carreras se probaron sintéticamente, no mediante consultas repetidas a Registro Civil.

## Regresiones

| Comprobación | Resultado final |
|---|---|
| Frontend, 14 archivos, dos workers como máximo | **99 correctas**, lint y build correctos |
| Backend H2, sin `Carga*Test` | **170 descubiertas, 169 correctas, 1 opcional omitida** |
| Backend MySQL, mismo conjunto | **170 descubiertas, 169 correctas, 1 opcional omitida** |
| Nuevos casos automáticos de frontend | Debounce, validación, deduplicación, completo/parcial/fallback, privacidad adulta, RUN NNA voluntario sin canal adulto, restauración pendiente/concluida, desmontaje, cambios, restauración tardía/fallida y conflicto de pestañas |
| Nuevos casos JDBC | Tres roles de tercero denegados aun con RUN permitido; NNA voluntario sin canal adulto; respuesta del RUN anterior no sobrescribe la declaración del nuevo |
| Estados registrales preexistentes | Completo/parcial/vacío/negativa/red/timeout/autorización/indisponibilidad/XML inválido/excesivo/saturación/RUN discrepante y persistencia, en H2/MySQL |
| Navegador real | Adulto con una consulta DEV, recarga sin reconsulta, recepción/idempotencia; comprobante sin desborde en 1440×900 y 390×844. NNA móvil sin RUN y con RUN sintético voluntario, restauración y recepción |
| HTTP V3 histórico | Fixtures byte a byte iguales al repositorio institucional de referencia; regresiones conservadas |

Las esperas de los tests adultos se corrigieron para esperar el paso visible y sus controles habilitados antes de escribir: los fallos de QA intentaban llenar un campo mientras seguía deshabilitado por la transición. No se relajaron validaciones ni se reemplazaron servicios reales por simuladores de ejecución.

Evidencias privadas excluidas de Git: `.local/evidencias/run-auto-{front-final,front-regresion,backend-h2,backend-mysql,empaquetado}.log` y `.local/evidencias/run-srcei-dev/{resultado,mysql,nna,mysql-nna,nna-fallo-canal-adulto}.json`, firmas de comprobación y PNG de custodia. Los resultados reducidos no contienen RUN, valores registrales, respuestas XML, certificados ni secretos. La sesión temporal de QA permanece fuera de Git, en el directorio privado del Nodo; no copiarla a informes ni logs.

## Configuración vigente y reversión

Se reutiliza `siger-pisee-dev-pisee-1`, la imagen Linux 2.4.0 existente y sus mismos montajes privados `runtime`/`state`. Se añadió `infra/compose.pisee-loopback.yml` porque el JAR integrado está en WSL nativo y no resuelve el alias privado Docker. El override publica únicamente **127.0.0.1:18084 → 8084**; el compose base continúa sin puertos y 8489 no se publica. El servicio se recreó para aplicar ese puerto conservando íntegramente su estado, configuración externa, certificados y credencial local. No se modificaron endpoints gubernamentales.

`scripts/linux/configurar-srcei-dev-formulario.py` comprueba Sandbox, TLS/loopback 18443, ausencia de perfiles QA/productivos, allowlist acotada con módulo 11 y autorización local existente. Crea una copia privada `application-before-srcei-automatico.properties` sin sobrescribirla y configura:

- `PISEE_SRCEI_ENABLED=true`, `PISEE_ENVIRONMENT=DEV`, `PISEE_DEV_AUTHORIZED=true`.
- `PISEE_BASE_URL=http://127.0.0.1:18084`, timeout **15000 ms**, concurrencia **1**.
- Rutas privadas existentes de `local-authorization` y `run-autorizado.json`, fuera de Git.

El origen local está autenticado y limitado a loopback; la conexión externa del Nodo conserva los controles TLS originales. HTTPS ciudadano, callbacks oficiales y Caddy no cambiaron en esta unidad. MySQL conserva V1–V7 exitosas con los checksums previos; no se recrearon bases, cuentas ni volúmenes. JAR final: SHA-256 `2e3e644570493d29d71e9ffb8cc99f502d1f8d780fa9cf337822e23b4fcf8f06`; PID observado **27172**, siempre sujeto a comprobar `app.pid`/puerto/proceso antes de operar.

Configuración WSL reproducible, desde el repositorio, solo si hace falta recuperar el laboratorio:

```bash
PISEE_PRIVATE_DIR=/home/nexbyteproveedor/.local/share/siger/pisee-dev docker compose -f infra/compose.pisee.yml -f infra/compose.pisee-loopback.yml up -d --no-build pisee
python3 scripts/linux/configurar-srcei-dev-formulario.py /home/nexbyteproveedor/.local/share/siger/nna-local-20261023 /home/nexbyteproveedor/.local/share/siger/pisee-dev --autorizar-dev
python3 scripts/linux/arrancar-nna-local.py /home/nexbyteproveedor/.local/share/siger/nna-local-20261023 --permitir-cu-sandbox
```

Usar los permisos Docker ordinarios ya disponibles. El arranque exige que 18443 esté libre; no iniciar una segunda instancia ni migrar de nuevo. Tras arrancar, comprobar salud/TLS antes del navegador. Si hay una instancia propia anterior que debe actualizarse, detener únicamente esa instancia con el mismo script y `--detener --permitir-cu-sandbox`, que valida PID/JAR/configuración antes de actuar.

Para revertir solo SRCeI: ejecutar el configurador con `--deshabilitar` en vez de `--autorizar-dev` y reiniciar únicamente el JAR. El resto del formulario sigue disponible con fallback declarado. Para retirar además el puerto local: aplicar solamente `infra/compose.pisee.yml` con `up -d --no-build pisee`, omitiendo el override. No borrar estado, certificados, allowlist, historial, comunicaciones, adjuntos ni volúmenes. No restaurar toda la configuración antigua sobre ajustes posteriores; el respaldo privado permite comparar las claves PISEE.

## Repetición de pruebas y límites

H2: `mvn -B -ntp '-Dtest=*,!Carga*Test' test` en backend. MySQL: `MAVEN_ARGS='-Dtest=*,!Carga*Test' bash scripts/linux/probar-mysql.sh /home/nexbyteproveedor/.local/share/siger/mysql-lab --autorizar-laboratorio`. Frontend: `npm test -- --run --maxWorkers=2`, `npm run lint`, `npm run build`.

Los scripts de navegador se ejecutan con Node Windows `--use-system-ca`, Edge instalado y TLS verificado. `RUN_DEV_AUTHORIZATION` debe apuntar a la allowlist privada existente. **No repetir `scripts/qa/run-srcei-dev.mjs` sin revisar primero la necesidad y el alcance autorizado**: crea una consulta real y una comunicación. Guarda la sesión fuera de Git para poder revisar una interrupción antes de consultar otra vez. `RUN_DEV_RESUME=1` solo sirve para un borrador con sesión todavía vigente, no para una comunicación ya recibida ni después de reiniciar el JAR. `scripts/qa/run-nna-sintetico.mjs` comprueba expresamente que su RUN esté excluido y no invoca al proveedor. `scripts/qa/verificar-run-srcei-mysql.py <directorio_app_privado> <directorio_nodo_privado>` solo lee/verifica MySQL y escribe evidencia reducida; requiere acceso Docker local.

Esta unidad no habilita búsquedas de terceros, QA/producción ni una consulta libre. El uso real combinado CU/SRCeI queda limitado por la identidad y la allowlist ya indicadas; su lógica está cubierta sintéticamente. No se cambió el consumidor SIGER ni se inició hardening productivo. Las nuevas comunicaciones permanecen en custodia pública y pendientes del ACK institucional correspondiente.
