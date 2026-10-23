# Conjunto A · ACK, TLS MySQL y retiro de V2

Cambios sobre la implementación vigente, sin cambiar firmas ni huellas históricas. El repositorio no tenía un commit base (todos los archivos estaban sin seguimiento); no se crearon commits ni se publicó nada. Este documento y el del [conjunto B](FORMULARIO_NNA_PROVISIONAL.md) delimitan la revisión.

## Defectos corregidos

1. **Inanición de ACK.** `RecepcionInstitucionalJdbc.confirmacionesPendientes()` seleccionaba siempre los primeros veinte registros. Un 409 reiterado impedía avanzar. Ahora filtra por vencimiento y bloqueo y ordena por próxima oportunidad, fecha e ID. Cada conciliación procesa como máximo veinte, secuencialmente y sin solapamiento dentro del consumidor. Un fallo se registra y aplaza; se continúa con el resto del lote. No hay red dentro de la transacción.
2. **TLS ambiguo.** La presencia textual de `sslMode=VERIFY_IDENTITY` no garantizaba su valor efectivo. Ambas aplicaciones ahora analizan la URL completa con una gramática cerrada. Rechazan duplicados, claves con otra capitalización, codificación porcentual, separadores ambiguos, aliases TLS, propiedades desconocidas y credenciales en URL. Solo admiten `sslMode`, `connectTimeout`, `socketTimeout` y, exclusivamente en laboratorio con TLS deshabilitado, `allowPublicKeyRetrieval=true`. Una propiedad adicional legítima deberá evaluarse y admitirse expresamente.
3. **HTTP V2 retirado.** `/integracion/v2/entregas/reclamar`, `/{id}/confirmacion` y `/{id}/fallo` devuelven **410** después de los controles de autenticación máquina, sin tocar entregas ni reservas. `/integracion/v3` sigue activo. Los nombres internos `FuenteHttpV2` y `RecuperarEntregasV2` se conservaron para evitar un renombrado transversal; el primero ya usa rutas HTTP V3. Leer contratos y comprobantes antiguos sigue permitido.

## Política de conciliación

Flyway institucional **V4__conciliacion_ack.sql**, MySQL y H2, añade:

- `ack_attempts`: intentos fallidos de conciliación.
- `ack_next_ms`: instante mínimo del siguiente intento.
- `ack_blocked`: revisión operacional requerida.
- `ack_last_error`: clasificación y código HTTP saneado.
- Índice de ACK pendientes, no bloqueados y vencidos.

4xx salvo 408/429: rechazo persistente; máximo **3** fallos de conciliación. 408, 429, 5xx y excepciones de transporte: transitorios; máximo **8**. Espera exponencial desde dos segundos hasta cinco minutos. Se conservan `ack_pending=TRUE`, cuerpo, Comunicación y comprobante. Un documento local inválido conserva el aislamiento previo mediante `ack_lease=NULL`; no se intenta arreglar o firmar automáticamente un documento corrupto.

Cada fallo deja evento con clasificación/código HTTP; el agotamiento añade `ACK_REVISION_REQUERIDA`. El listado técnico autenticado `/operacion/v1/entregas-pendientes` expone los metadatos de reintento, sin contenido ciudadano. `POST /operacion/v1/entregas/{id}/reintentar-ack`, con `{"intentosEsperados":3}` y credencial operacional, permite rearmar un ACK bloqueado con control de versión por contador. No confirma, no repara datos ni modifica firmas. Antes debe investigarse el incidente. Un rechazo por reserva vencida puede resolverse mediante una nueva reserva del protocolo existente.

Los límites de recuperación pública (reservas/reintentos y cuarentena) siguen vigentes por separado. Una respuesta del ACK perdida después del COMMIT público puede volver a confirmarse: la misma confirmación firmada es idempotente incluso con una reserva anterior. Solo una respuesta satisfactoria permite cerrar el ACK institucional.

El bloqueo evita repetición indefinida; no garantiza resolución automática de incompatibilidades de contrato, credenciales erróneas o corrupción. Debe existir revisión operacional. El control de solapamiento es por proceso, conforme al despliegue actual de una instancia; no se implementó coordinación de múltiples réplicas.

## TLS y migraciones

Fuera de la excepción local se exige un único `sslMode=VERIFY_IDENTITY`. La excepción requiere bandera local explícita, host literal `127.0.0.1`, puerto **13306** y uno de los cuatro esquemas `robust_public_test`, `robust_inst_test`, `robust_public_local`, `robust_inst_local`. El arranque también exige bind loopback para concederla. El migrador valida el destino antes de crear el pool o ejecutar Flyway; usa una cuenta distinta con DDL.

Las pruebas positivas de URL verifican la política, **no un handshake con una base productiva**. MySQL real de laboratorio usa la excepción explícita sin TLS sobre loopback. No se contactaron bases institucionales.

## Archivos y regresiones

- Institucional `robustez/RecepcionInstitucionalJdbc.java`, `RecuperarEntregasV2.java`, `OperacionController.java`: selección, aplazamiento, auditoría, revisión y reintento de ACK. El primero también contiene los cambios de canal NNA del conjunto B.
- Ambas `robustez/ConfiguracionDatos.java`, `MigrarDatos.java`, `GuardasDatosTest.java`: una misma política para servicio y migración, con pruebas negativas y positivas.
- Público `robustez/IntegracionV2Controller.java`: cierre de las tres mutaciones retiradas.
- Institucional `db/institucional/{mysql,h2}/V4__conciliacion_ack.sql`.
- `RecepcionInstitucionalJdbcTest`: **35 ACK**, 25 con 409 y 10 válidos posteriores; avanzan los diez, los 25 quedan pendientes y bloqueados después de tres fallos; se conservan los 35 registros. También aplazamiento transitorio, reintento operacional y recuperación del ACK perdido.
- `NnaHttpTest.rutasV2RetiradasDevuelven410SinReservarUnaEntrega`: regresión MVC con entrega pendiente y contador de reservas intacto. Los controles máquina de V3 se ejercitan además en la demostración HTTPS real.

Ejecución reproducible: `scripts/Build-Todo.ps1` y `scripts/Robustez.ps1 pruebas`. Resultados consolidados y límites en la [entrega](ENTREGA_NNA_2026-10-19.md).
