# Aviso de versión histórica

Desde el 19-10-2026, HTTP `/integracion/v2/entregas` está retirado: sus mutaciones devuelven 410 y no reservan. El transporte vigente es [V3](registro-comunicacion-v3.md). Esto no invalida entregas firmadas 2.0 ni comprobantes históricos. Variante declarada adicional: [NNA](comunicacion-nna-v2.0.md).


# Contrato de entrega durable v2

**Histórico, sustituido por [registro v3](registro-comunicacion-v3.md).** El sobre v2 sigue siendo evidencia de custodia entre backends. Su sello público no es el comprobante definitivo de registro en SIGER. Los estados y rutas vigentes están en v3; el ACK técnico v2 ya no permite confirmar formalmente una entrega.

Implementado en ambos backends, perfil `jdbc-reception`. Es un contrato técnico versionado y probado localmente; no declara habilitado un transporte institucional productivo. V1 permanece como demostración de archivos y no exporta entregas JDBC.

## Ciudadanía, exclusivamente mismo origen

`POST /api/comunicaciones`: sesión autenticada, CSRF, `Idempotency-Key: UUID`. JSON estricto:

```json
{"mensaje":"Mensaje sintético","avisoPrivacidadLeido":true,"avisoVersion":"laboratorio-v1"}
```

RUN y nombres se obtienen de la sesión del backend. Se rechazan campos desconocidos, JSON ambiguo, aviso ausente/obsoleto y texto inválido. `202` se emite tras COMMIT y devuelve `{id,estado,confirmacion,comprobante}` y Location. `GET /api/comunicaciones/{id}` y `/{id}/comprobante` solo para el RUN propietario. El comprobante firmado se descarga JSON, `no-store`; nunca poner RUN o claves en URL.

Clave repetida y contenido canónico igual devuelve recepción original; distinta identidad/mensaje/versión: 409. El mismo mensaje con **otra clave** es un ingreso diferente. El cliente debe conservar la clave durante un resultado incierto. `PENDIENTE_SIGER` es custodia pública; `CONFIRMADA_SIGER` significa persistencia en la frontera institucional, no una clasificación o decisión funcional.

## SIGER inicia todas las conexiones

HTTPS y Bearer de integración externa, distinto del token de operación y de CU. Sin cookies, CSRF ciudadano ni CORS. El navegador no participa. `POST /integracion/v2/entregas/reclamar`:

```json
{"limite":20}
```

Respuesta 200, límite permitido 1–100 (consumidor actual pide 20):

```json
{"entregas":[{"reserva":"UUID","venceEnEpochMs":1792010000000,"intento":1,"entrega":{"version":"2.0","comunicacion":{},"procedencia":{},"comprobante":{}}}]}
```

La forma completa interoperable está en `fixtures/entrega-v2.json`, con claves públicas de prueba en `fixtures/receipt-public-test.json`. No contiene clave privada. El `comunicacion` conserva el sobre v1: versión, ID, UTC, identidad, mensaje, versión de aviso y SHA-256 según campos UTF-8 prefijados por longitud. `procedencia` tiene exactamente run, nombres, apellidos, mensaje, con fuente/nivel/observadoEn/evidenciaRef. Se firma también su huella en un orden fijo. Cada receptor valida contrato, integridad y firma, sin elevar niveles por datos del cliente.

Reserva por defecto 300 s, configurable 1–900 s; una nueva reserva invalida el token anterior. No se ofrece exactamente una entrega de red: se admite entrega repetida y se garantiza una sola incorporación por origen + ID mediante transacción e índice único.

Tras guardar, SIGER llama `POST /integracion/v2/entregas/{id}/confirmacion`:

```json
{"reserva":"UUID","recepcion":{"recepcionInstitucionalId":"UUID","registradaEn":"2026-10-14T22:00:00Z","sha256":"64 caracteres hex minúsculos"}}
```

Respuesta 204; repetir exactamente el ACK conserva resultado. Hash diferente o reserva reemplazada: 409. No confirmar antes de COMMIT. Si la conexión se pierde después de persistir, mantener ACK pendiente y recuperar; el recibo institucional original se reutiliza.

Fallo de una entrega: `POST /integracion/v2/entregas/{id}/fallo`, `{"reserva":"UUID","codigo":"RECEPTOR_NO_DISPONIBLE"}`. Otros códigos: `CONFIRMACION_AMBIGUA`, `CONTRATO_INVALIDO`. 204 registra reintento diferido o cuarentena. Ningún error provoca borrar el registro. Una caída antes de notificar el error se recupera al vencer la reserva.

## Firma y claves

`comprobante`: `{algoritmo:"Ed25519",keyId,payload,firma}`. Payload y firma son base64url sin padding; la firma cubre los bytes JSON exactos del contenido. Verificar la firma con el keyId de la configuración pública confiada, no una clave incluida por el emisor en el mensaje. Contenido: versión 1.0, UUID, fecha UTC, `CUSTODIA_PUBLICA`, huellas de contenido/procedencia, emisor `FORMULARIO_PUBLICO_DDN` y versión de aviso. La verificación requiere representación canónica igual al serializador y reglas de correspondencia del sobre. El fixture fija ese contrato entre implementaciones Java independientes.

Conservar las claves públicas históricas al rotar el firmante; desplegar nueva confianza en SIGER antes de usar una nueva privada en el público. Respaldar claves y base. No hay endpoint público que cambie automáticamente confianza ni descarga automática de una clave remitida en un comprobante. Este formato no afirma FEA ni sellado de tiempo oficial.

## Errores y habilitación

400 contrato/validación, 401 autenticación, 403 CSRF/HTTPS o permisos, 404 objeto no accesible, 409 conflicto/integridad/reserva, 412 revisión operacional obsoleta, 413 cuerpo excesivo, 429 capacidad, 503 dependencia/ingreso suspendido. Respuestas técnicas no incluyen secretos, cuerpo de proveedor o trazas SQL. Reintentar 429/503 con espera y la misma clave; no reintentar ciegamente conflictos de contenido.

Controles operacionales, conciliación y comandos: `../docs/ROBUSTEZ_Y_OPERACION.md`. Timeout HTTP del consumidor 10 s y cuerpo máximo 512 KiB; el proveedor debe dimensionar lote según tamaño real. Para volumen mayor se debe paginar en lotes menores, no aumentar sin medida la memoria. Los intervalos, retención, SLA y autenticación definitiva entre instituciones requieren validación del despliegue.
