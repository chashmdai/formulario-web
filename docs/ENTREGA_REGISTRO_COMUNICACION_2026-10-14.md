# Nota posterior · 19-10-2026

La restauración de operaciones descrita en esta entrega histórica fue retirada por decisión funcional. Los formularios actuales comienzan vacíos al recargar y conservan claves únicamente en memoria del componente activo. [Entrega vigente y NNA provisional](ENTREGA_NNA_2026-10-19.md).


# Entrega: Comunicación institucional y comprobante definitivo

14-10-2026, Nexbyte. Evolución del repositorio vigente, conservando ambas aplicaciones y ClaveÚnica. No se modificó infraestructura institucional ni se inventaron procesos funcionales.

## Resultado demostrado

Se completó nuevamente el ingreso real de Sandbox con su RUN oficial de prueba y el backend público obtuvo userinfo. RUN/nombres/apellidos permanecieron de solo lectura. Con SIGER detenido se envió un mensaje sintético: el público confirmó únicamente custodia técnica, sin folio ni descarga definitiva.

Al iniciar SIGER se registró COM-000002, UUID d7365daf-f7e9-4722-914c-dd487663d9a2, el 15-10-2026 a las 00:03:44.218 UTC (14-10, 21:03:44 en Santiago). Su operación técnica pública es 23bdef01-7301-4c1f-9c51-58af9907fb5d. COM-000001 corresponde a la promoción del registro técnico previo conservado en la demo; no se borró ni reutilizó su operación.

Se descargó el comprobante desde la sesión propietaria, verificó su firma Ed25519 con Node independientemente de Java y volvió a descargar con SIGER detenido: bytes idénticos. Una firma alterada fue rechazada. Se cerraron las sesiones local y de CU. Es un registro formal de prueba en SIGER local, no un ingreso productivo.

- [Pantalla pendiente](evidencias/registro-comunicacion-2026-10-14/recepcion-pendiente.png) y [pantalla registrada](evidencias/registro-comunicacion-2026-10-14/comunicacion-registrada.png).
- [Documento firmado](evidencias/registro-comunicacion-2026-10-14/comprobante-COM-000002.json), [verificación](evidencias/registro-comunicacion-2026-10-14/verificacion-comprobante-real.json) y [recuperación idéntica](evidencias/registro-comunicacion-2026-10-14/recuperacion-comprobante-real.json).

## Flujo y cinco mejoras

Sesión CU → custodia pública idempotente → pendiente sin folio → recuperación HTTPS iniciada por SIGER → transacción institucional (Comunicación, folio, fecha, cuerpo, comprobante y evento) → COMMIT → ACK firmado → comprobante definitivo del propietario.

| Mejora | Estado |
|---|---|
| Idempotencia | Custodia y registro únicos; reintento conserva UUID, folio, fecha y firma originales. |
| Procedencia individual | Evidencia CU del servidor y mensaje declarado cubiertos por huellas. Proyección extensible; SRCeI NO_HABILITADO. |
| Conciliación | ACK durable, recuperación de respuesta perdida después del COMMIT público, aislamiento de confirmaciones corruptas y métricas sin PII. Registro operacional de notificaciones conservado, sin envíos nuevos. |
| Habilitación/contingencia | CAS/auditoría, ingreso y recuperación independientes, backoff y cuarentena conservados. Consultas y ACK existentes continúan al cerrar ingresos. |
| Comprobante | Definitivo únicamente después del registro SIGER. La custodia pendiente no crea un folio o comprobante institucional. |

La privada institucional es distinta de la pública y reside únicamente en SIGER. El público verifica y conserva el documento; su clave de custodia no permite emitirlo. El sello público anterior queda interno al transporte. El comprobante JSON no contiene RUN, nombres o mensaje y no pretende FEA.

La disponibilidad del comprobante definitivo depende del registro y su confirmación verificable; no puede prometerse inmediatez institucional mientras SIGER está desconectado. Plazos y redacción requieren validación en Hito 1.

## Modificaciones

- Institucional: modelo mínimo en institucional/comunicaciones; registro transaccional en RecepcionInstitucionalJdbc; folio de base de datos con formato congelado; consumidor saliente acotado, conciliación, consulta técnica paginada y métricas. Sin flujo de negocio.
- Público: ACK v3 firmado y vinculado a operación/huellas; estado pendiente distinto de registro formal; descarga por propietario; GET autenticado /api/identidad/atributos. No hay adaptador SRCeI activo.
- Ambos: ComprobanteInstitucional/RegistroInstitucional, Flyway V3 para MySQL y H2 de pruebas, fixture firmado común verificado en cada artefacto. Sin dependencia Maven entre aplicaciones.
- React: folio/UUID/fecha/canal y descarga solo al confirmarse registro, restauración de operación y consulta automática acotada/manual. Sin formulario definitivo.
- Scripts: claves institucionales independientes, Recepcion-Local actualizado, prueba de acumulación con dos JVM reales y verificador independiente.

Ambos JAR y React compilados. V3 aplicada a los esquemas locales preservando cuerpos y ACK anteriores. El despliegue requiere pausar recuperación y actualizar ambos extremos coordinadamente, según [contrato v3](../contratos/registro-comunicacion-v3.md).

## Pruebas y métricas

Ejecución final contra MySQL 8.4.11 real: 43 pruebas públicas y 25 institucionales exitosas, cero fallos/errores. Seis pruebas React exitosas. Cuatro pruebas especiales se omiten en la batería ordinaria por requerir coordinador: CargaBaseTest, CargaAcumuladaTest, CargaHttpTest y RecuperacionProcesoTest; no se cuentan como éxitos de esa batería.

CargaAcumuladaTest se ejecutó aparte con piloto de 100 y 5.000 en serie y en paralelo. Se probaron 200 folios distintos concurrentes, 72 reingresos de una operación, rollback, migración, ACK perdido antes/después de COMMIT, reapertura, firmas, propietario y estados ciudadanos.

| SIGER apagado durante acumulación | Serie | 4 trabajadores |
|---|---:|---:|
| Comunicaciones | 5.000 | 5.000 |
| Recuperación + registro + confirmación | 220,53 s | 134,34 s |
| Promedio confirmado | 22,67/s | 37,22/s |
| Folios únicos / ACK pendientes finales | 5.000 / 0 | 5.000 / 0 |

Mejora observada: 39 % menos tiempo, ~1,64 veces el rendimiento; mediciones locales individuales, no SLA. [Método, límites, JFR y comandos](PRUEBAS_REGISTRO_COMUNICACION.md); [resumen de pruebas](evidencias/registro-comunicacion-2026-10-14/pruebas-finales.json). No se repitió una caída física de MySQL ni se afirma restauración completa del nuevo registro desde backup.

## Repetir sin asistencia

Desde la raíz, PowerShell 7.4, Java/Maven/Node disponibles:

~~~powershell
.\scripts\Recepcion-Local.ps1 detener
.\scripts\Build-Todo.ps1
.\scripts\Robustez.ps1 pruebas
.\scripts\Recepcion-Local.ps1 preparar
.\scripts\Recepcion-Local.ps1 publico
.\scripts\Recepcion-Local.ps1 siger
.\scripts\Recepcion-Local.ps1 habilitar
~~~

Abrir https://claveunica.formularioadulto.defensorianinez.gob.cl, nunca localhost/Vite para este recorrido. RUN oficial 44.444.444-4, clave testing exclusivamente en Gobierno Digital. Para desconexión: SIGER.cmd detener; enviar un nuevo mensaje sintético; después Recepcion-Local.ps1 siger. Para carga: detener la demo y ejecutar scripts/Probar-Comunicaciones-Acumuladas.ps1 -Cantidad 5000 -Paralelismo 4. Las pruebas limpian solo *_test, nunca los esquemas *_local.

Verificación independiente de la evidencia (clave exclusiva del laboratorio, no raíz productiva):

~~~powershell
node .\scripts\Verificar-Comprobante-Institucional.mjs .\docs\evidencias\registro-comunicacion-2026-10-14\comprobante-COM-000002.json .\docs\evidencias\registro-comunicacion-2026-10-14\claves-publicas-laboratorio.json
~~~

Detener demo con Recepcion-Local.ps1 detener y MySQL con Local-MySQL.ps1 detener. FORMULARIO.cmd iniciar recupera el modo habitual CU sin base ni envío. Ambas aplicaciones siguen arrancando individualmente.

Se verificó el arranque institucional con público/MySQL detenidos y después el público con SIGER/MySQL detenidos: [evidencia](evidencias/registro-comunicacion-2026-10-14/independencia-aplicaciones.json). Al cerrar la entrega queda el público habitual iniciado, sin sesión ciudadana; SIGER y MySQL detenidos, datos conservados.

## Pendientes Hito 1

Formato/numeración y canales; campos, formulario, validación, privacidad y retención; distribución/clasificación, procesos/roles/transiciones y relación Caso/Requerimiento; presentación oficial y plazos del comprobante, eventual PDF/FEA/sellado de tiempo; alojamiento, credenciales máquina/operadores, backup/restauración, alertas y sincronización horaria; contrato, atributos autorizados y conectividad SRCeI.

No se utilizó el taller Notificador ni su identidad. La distinción Comunicación/Requerimiento conserva las obligaciones contractuales. El despliegue productivo continúa pendiente de autorización institucional.
