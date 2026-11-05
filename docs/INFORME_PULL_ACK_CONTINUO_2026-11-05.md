# Candidato Ubuntu — PULL/ACK continuo, 05/11/2026

**Preparado; no instalado ni activado.** El encargo autoriza preparación y pruebas sintéticas. PULL productivo permanece cerrado. No se cambia contraseña, caducidad, sesiones, autorizaciones ciudadanas, contratos, credenciales, claves, certificados, Caddy, Cloudflare, ClaveÚnica ni PISEE.

## Base e inventario real

Rama candidata: `feature/formulario-pull-continuo-20261105`, worktree `/home/admin-ti/formulario-pull-continuo-20261105`. Nace directamente del código compilado **`c42bc4fb913c85001e01c6ba4c7b332a530ca448`**, constatado en `/srv/ddn-formulario-produccion`. La referencia `release/formulario-produccion-2026-11` conserva `7603448`; no se sobrescribe ni se usa como si fuera el último binario. Main y SIGER no se fusionan/modifican.

Inventario de solo lectura: **05/11/2026 15:23:26 UTC (12:23:26 America/Santiago)**:

| Elemento | Estado observado |
|---|---|
| Proyecto | `ddn-formulario-produccion`, instalación `/srv/ddn-formulario-produccion` |
| Imagen app reutilizada | `sha256:6fa0f5d53cc72e98fd9789537bf3e98269826be7e4ea9fbbd40158651ffaba6b` |
| JAR ejecutado/reutilizado | SHA-256 `5f778552618fd734af4d04c8cf511d67b0d80a21756c57d9e8ab81abf81fbaaa` |
| Servicios | App, MySQL, Caddy, PISEE, ClamAV e Inspector saludables, `unless-stopped` |
| systemd | Docker, unidad productiva y timer de respaldo activos y habilitados |
| Control persistido | Revisión 18: aceptarNuevos=true, recuperar=false, contingencia=NORMAL |
| Cola | 2 CONFIRMADA con ACK; sin pendientes ni reservas activas. No se leyeron cuerpos |
| Confianza ACK | `smr-windows-20261104-v1`; integración configurada, recuperación CAS cerrada |
| Contraseña | Obligatoria hasta **15/12/2026 00:00 America/Santiago**, política existente intacta |
| Eliminación | `ciclo-vida.eliminacion.habilitada=false`; ninguna retención nueva |
| Capacidad inicial | 31,81 GiB libres; piso de admisión 16 GB, cuota multimedia 5 GB |
| Respaldo reciente | `formulario-20261105T061945Z.gpg`, registro verificado, controles previos restaurados |

SHA-256 del respaldo registrado: `cf73b7420aba1c81de0c8503c3e30ce59f654c3869480c53e305f13279f41ee4`. No se restauró ni volvió a respaldar producción durante este trabajo. La copia externa del host sigue pendiente de destino institucional aprobado.

El operador acredita el primer E2E físico; los dos ACK/estados confirmados observados son coherentes con ello. No se repitió el E2E ni se inspeccionó Windows. Su informe declara candidato **02d4184**, paquete **smr-continua-rc1-20261105-r2**, no desplegado, sin SCM calificado y con autorización continua aún pendiente. No se presenta ese candidato como receptor continuo ya activo.

## Qué ya permite el productor

`operation_control` en MySQL guarda recepción, recuperación, modo y revisión. No tiene fecha de vencimiento ni ventana horaria. Reiniciar conexiones/app no transforma pausa en apertura. La autenticación técnica está en otra cadena de seguridad; la contraseña y su caducidad ciudadana no limitan el Bearer institucional. No hay razón para cambiar Java, React ni generar otro certificado o identidad.

PULL/ACK continuo significa mantener la intención autorizada en SQL y atender solicitudes durante el día y la noche, **no disponibilidad ininterrumpida ni un SLA**. Red, mantenimiento, capacidad y fallos pueden interrumpirlo. Las pausas/incidentes explícitos prevalecen, sin un supervisor que vuelva a activar PULL.

## Cambios del candidato

- Nueva herramienta [continuidad.py](../scripts/ubuntu/continuidad.py): `status`, `activate` y `pause`. Usa exclusivamente GET/PUT del control operacional privado con su token existente. Activar exige revisión recién leída, recepción ya abierta, modo NORMAL, integración configurada, identidad ACK presente y hash actual del mapa. Una revisión obsoleta, conflicto o resultado incierto no provoca reintentos de escritura. No modifica archivos de configuración, autorizaciones ni sesiones, no reinicia y no invoca `productor.py open`.
- Pausar PULL conserva recepción/modo y usa CAS. Repetir una decisión ya efectiva no escribe otra revisión. No hay daemon, calendario ni tarea de reapertura; la referencia identifica la intervención del operador, no inventa una aprobación institucional.
- [respaldo-programado.py](../scripts/ubuntu/respaldo-programado.py) conserva el procedimiento existente: cierre CAS, copia consistente cifrada, verificación, salud y restauración del estado previo **solo con la revisión exacta del cierre**. Un cambio concurrente del operador devuelve 412: la copia verificada se registra con `operatorDecisionPreserved=true` y no se reabre. Un fallo distinto/ambiguo exige revisión, sin reintentar ni anular la pausa.
- [respaldo.py](../scripts/ubuntu/respaldo.py) cierra explícitamente los lectores usados para hash. No cambia formato de copia, cifrado, restauración, custodias ni retención.
- Nuevas pruebas de continuidad, restauración e integridad. **Ningún cambio en clases de aplicación de producción, SQL/Flyway, unidades, Compose o configuración del acceso ciudadano.**

El paquete operacional se instala en una intervención futura aprobada. Su JAR RC1 y la imagen reutilizada son exactamente los existentes: no hay motivo para desplegar otra imagen o recompilar React/Java para habilitar continuidad.

## Reinicios, pausas y fallos

- Docker arranca por systemd; `ddn-formulario-produccion.service` ejecuta Compose inmutable y los servicios usan `unless-stopped`. Son configuraciones observadas, no una prueba de reboot del servidor productivo. No se reinició el host ni sus componentes.
- La unidad de arranque puede volver a iniciar contenedores: una parada de proceso no sustituye una pausa persistente. Usar CAS para suspender recuperación; el estado SQL se conserva tras reinicio. Si MySQL no está disponible, no hay apertura implícita.
- PULL cerrado bloquea **nuevas reservas**. El contrato conserva la conciliación de ACK válidos en vuelo; no deshace COMMIT institucional ni convierte una pausa en borrado. Para mantenimiento, pausar/drenar también el consumidor Windows y atender ACK pendientes.
- Windows desconectado: el productor conserva la Comunicación. Una reserva vence a cinco minutos y puede reasignarse con token nuevo. Fallos temporales tienen backoff; límites/contrato inválido llevan a cuarentena. No hay renovación de lease. No retirar cuarentena por SQL para aparentar recuperación.
- Windows que confirmó COMMIT antes de perder red conserva ACK pendiente y registro/folio. Al volver, repite ACK firmado idéntico o actualiza reserva al recibir el mismo documento, sin duplicar el registro. La disponibilidad de esa reconciliación depende del receptor real, no de Ubuntu solamente.
- Capacidad insuficiente cierra admisión por la guarda existente y no borra originales. No se promete una reserva ilimitada de espacio. El backup mantiene su piso y falla cerrado si no hay capacidad. Vigilar disco, tamaño/edad de cola, cuarentena, salud, backups/alertas y ACK pendientes del receptor. No se agregan políticas de borrado.
- El respaldo diario actual detiene brevemente escritores de **este** stack para una copia consistente y reinicia servicios; interrumpe navegación/sesiones según el comportamiento existente. El candidato no cambia esa política ni promete backup en caliente. Restaura la intención anterior solo si no hubo otra decisión CAS. Si falló o el operador intervino, queda el cierre/decisión vigente; nunca se fuerza reapertura diaria. No es una nueva ventana de autorización de PULL.

## Artefactos y evidencia

El [manifiesto versionado](MANIFIESTO_PULL_ACK_CONTINUO_2026-11-05.json) y el manifiesto externo del paquete fijan commit de herramientas y pruebas, SHA por archivo, JAR e imágenes reutilizadas, resultados y límites. Los logs brutos de pruebas son privados; el paquete incluye resúmenes saneados. No contiene configuraciones institucionales, claves, tokens ni SQL/originales reales. Los fixtures se generan exclusivamente en tests y no se conectan a proveedores.

Ver [procedimiento de instalación, activación única y reversión](PROCEDIMIENTO_PULL_ACK_CONTINUO_2026-11-05.md). No instalar mientras solo esté autorizada la preparación.

## Resultado final de pruebas y preservación

| Batería final | Reportadas | Fallos / errores | Omisiones |
|---|---:|---:|---:|
| Backend/H2, build Maven del candidato | 429 | 0 / 0 | 2 opt-in existentes |
| Formulario completo sobre MySQL sintético | 33 | 0 / 0 | 1 ensayo SQL específico de H2 |
| Python: continuidad, backup y regresiones de herramientas | 51 | 0 / 0 | 0 |

No sumar baterías ni repeticiones para presentar cobertura. Frontend no cambió; el stage idéntico previamente validado fue reutilizado desde caché. No se atribuye una ejecución React nueva ni CI remota verde a esta preparación.

Se probaron JDBC reabierto, pausa conservada y ACK en vuelo, lease vencida después de avanzar 100 días el reloj sintético, receptor temporalmente no disponible, firma/hash/original, ACK repetido sin duplicado, y capacidad insuficiente sin borrar ni cambiar intención CAS. Se restauró un snapshot SQL H2 con documento/original/ACK firmado y control. En MySQL nuevo sin puertos host se ejecutó la batería, dump/importación a otro esquema, reinicio real de ese MySQL y verificación de control, firma Ed25519 y hash del original sintético. No es un ensayo de NTFS ni del SMR Windows.

La prueba GPG/TAR/restore usa cifrado y archivos reales sintéticos; el dump Docker de esa prueba se sustituye por SQL de prueba. Se distingue de la prueba nativa MySQL anterior. Las pruebas de carrera CAS usan dobles de la API, no PUT productivos. Dos iteraciones previas de compilación de los tests se corrigieron (visibilidad y excepción del helper); solo la batería final se reporta aprobada. No se relajaron controles del código productivo.

Al cierre se compararon **45 archivos protegidos** por hash, control/cola y los seis contenedores por ID, imagen y hora de arranque: idénticos. Hash del respaldo productivo comprobado contra el archivo real. Continúa revisión18, recepción activa y PULL cerrado; dos confirmadas y cero reservas activas. Espacio libre al cierre de tests: **31,13 GiB**. Solo se detuvo el MySQL sintético creado para este trabajo; sus datos de prueba se conservaron. HTTPS público: 200/UP con CA del sistema y Cloudflare observado.

**Estado:** candidato Ubuntu listo para revisión; **activación no autorizada/ejecutada por esta preparación**. Aprobación de custodia continua, configuración/release/SCM/DPAPI y calificación de Windows siguen pendientes según el informe recibido. No se afirma disponibilidad 24/7 acreditada por superar pruebas aisladas.

Artefacto de entrega: `/home/admin-ti/operacion-formulario/candidato-pull-continuo-20261105.tar.gz`, con SHA-256 contiguo y manifiesto externo. Incluye herramientas, informe/procedimiento, evidencia saneada y JAR RC1 reutilizado; no incluye configuración institucional ni datos. Los tests y su código reproducible están en la rama candidata.
