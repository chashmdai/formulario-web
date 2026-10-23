# Registro institucional: pruebas y mediciones del 14-10-2026

Las pruebas utilizan el repositorio vigente y el MySQL 8.4.11 portable ya preparado, InnoDB con fsync al confirmar (`innodb_flush_log_at_trx_commit=1`). Los esquemas `robust_public_test` y `robust_inst_test` están separados de la demostración `_local`. Windows 11, Java 25.0.3, 8 procesadores lógicos. No se hizo carga contra servicios gubernamentales.

## Acumulación con SIGER apagado

El productor es Tomcat/Spring del público, con configuración de test y una identidad sintética generada únicamente en test. Primero persiste el volumen completo y comprueba que no hay comprobante institucional. El coordinador exige SIGER apagado, inicia después su JAR real en un JVM separado y mide hasta que el público tiene todas las confirmaciones verificadas. Verifica además las filas institucionales y ausencia final de ACK pendientes.

Se pierde deliberadamente **una respuesta después de que el público confirmó su transacción de ACK**. Esa entrega ya no reaparece al reclamar; la conciliación institucional recupera su resultado. Se comprueban todas las firmas recibidas por el público, folios e IDs distintos, acceso al mismo comprobante y reintento idempotente de una operación.

| Medición | Secuencial | 4 trabajadores acotados |
|---|---:|---:|
| Volumen acumulado | 5.000 | 5.000 |
| Generación pública | 8,98 s | 7,01 s |
| Primera confirmación, incluido arranque SIGER | 4,05 s | 4,09 s |
| Recuperación, registro y confirmación completos | 220,53 s | 134,34 s |
| Promedio de registros confirmados | 22,67/s | 37,22/s |
| Folios / UUID de Comunicación únicos | 5.000 / 5.000 | 5.000 / 5.000 |
| ACK pendientes finales | 0 | 0 |

La reducción observada del tiempo fue de aproximadamente 39 %. Son dos ejecuciones locales, con cachés/JIT y otras actividades de desarrollo posibles; no un estudio estadístico o SLA. El tiempo incluye arranque y recuperación del ACK perdido. La comprobación final exhaustiva de documentos ocurre después del punto de drenaje medido. La generación directa usa el servicio real de custodia con evidencia sintética de test; no mide un ingreso HTTP ciudadano autenticado contra CU.

## Diagnóstico y ajuste

JFR sobre el proceso SIGER de la primera ejecución, ventana de 30 s: CPU JVM promedio ~3,14 % de la capacidad total del equipo; 2.053 eventos SocketRead hacia MySQL, acumulando ~8,78 s. Los tiempos agregados de ThreadPark incluyen hilos ociosos y **no** se utilizan como medida de trabajo perdido. Se observó margen de CPU y un procesamiento secuencial con varias confirmaciones durables por registro.

Se implementó paralelismo limitado de registros independientes, con un lote activo máximo de 20 y 1–8 trabajadores configurables (4 por defecto), conservando el pool de 12 conexiones y todas las transacciones, firmas, fsync y comprobaciones de unicidad. No se sustituyó persistencia por memoria ni se relajó durabilidad. No se promete FIFO. El sondeo/drenaje sigue acotado para que puedan aplicarse controles y backoff.

En la ejecución con cuatro trabajadores, métricas internas medias por intento procesado: registro + preparación durable de ACK ~26,78 ms; confirmación pública ~16,49 ms; cierre local de ACK ~11,01 ms. Estas métricas pueden superponerse por concurrencia y no se suman como tiempo total del lote.

Datos crudos: `evidencias/registro-comunicacion-2026-10-14/acumuladas-5000-serie.json`, `acumuladas-5000-paralelo.json` y resúmenes JFR sin datos ciudadanos. La grabación JFR completa queda privada en `.local`.

## Casos automatizados

- 72 reingresos simultáneos con una clave: una Comunicación y un resultado estable.
- 200 Comunicaciones diferentes bajo concurrencia: folios, números e IDs únicos; cambio de formato solo para nuevos ingresos.
- Rollback antes de COMMIT: no queda Comunicación, inbox parcial o comprobante publicable; reintento posterior registra una sola vez.
- Reapertura del repositorio con configuración de formato distinta: mismo folio, fecha, ID y firma.
- Firma falsa con clave del público, firmante desconocido, registro de otra operación y ACK técnico antiguo: rechazados.
- SIGER ausente: recepción pendiente con `registroInstitucional` y `comprobante` nulos; descarga definitiva 409.
- ACK perdido antes de llegar y respuesta perdida después del COMMIT público: conciliación sin cambiar documento o necesitar una nueva reclamación.
- Reserva vencida/reemplazada: no permite confirmar otra reserva; ACK ya aceptado idéntico es repetible.
- Confirmación local corrupta: se aísla su reserva conciliable y conserva los datos; las demás continúan. Requiere restauración/atención operacional, no se ignora la firma.
- Migración v2 → v3: ACK antiguo conservado, entrega reabierta y sin folio inventado o fecha retroactiva.
- Contrato firmado v3 interoperable en ambos artefactos sin versionar la privada.
- UI pendiente sin folio/enlace definitivo; UI registrada con metadatos y descarga; una confirmación técnica antigua no se presenta como formal.
- Proyección CU real de sesión; SRCeI no habilitado. Prueba de extensión exclusivamente sintética con lista de atributos autorizados.

Se conservan las pruebas anteriores de OIDC/state, token/userinfo backend, RUN desde RolUnico, cookies/CSRF, integridad, normalización, límites y controles operacionales. La batería usa H2 solo en tests rápidos y MySQL real en la ejecución nativa. La prueba especial de caída MySQL anterior cubre custodia técnica; no debe reinterpretarse como restauración integral del nuevo registro institucional.

## Repetir

Última batería ordinaria con MySQL real: 43 pruebas públicas y 25 institucionales exitosas, cero fallos/errores; cuatro pruebas especiales omitidas por activación explícita. Frontend: seis pruebas exitosas. La acumulación se ejecutó aparte y sus resultados están arriba. También se arrancó cada aplicación con la otra y MySQL detenidos: [evidencia](evidencias/registro-comunicacion-2026-10-14/independencia-aplicaciones.json).

```powershell
.\scripts\Recepcion-Local.ps1 detener
.\scripts\Build-Todo.ps1
.\scripts\Robustez.ps1 pruebas
.\scripts\Probar-Comunicaciones-Acumuladas.ps1 -Cantidad 5000 -Paralelismo 4
# Mismo script con -Paralelismo 1 permite una comparación controlada.
# Acceso desde el script madre:
.\scripts\Robustez.ps1 acumuladas
```

El coordinador valida PID/puertos y esquemas aislados, usa credenciales DPAPI de testing, no imprime tokens y detiene el JAR que creó. No borra datos institucionales o de demo. El volumen/plazo está acotado; una falla deja logs privados y no permite declarar drenaje exitoso. Antes de producción se necesitan carga prolongada, dimensiones institucionales, infraestructura definida, pruebas de respaldo/restauración completa y sincronización horaria.
