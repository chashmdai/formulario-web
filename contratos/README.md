# Referencia vigente para integrar el Formulario Público completo

El consumidor SIGER debe tomar como base `/integracion/v3/formulario-completo/entregas`, el [baseline evolutivo](../docs/BASELINE_FORMULARIO_PUBLICO_2026-10-24.md), la [custodia completa](../docs/CUSTODIA_FORMULARIO_COMPLETO.md) y la [extensión documental 1.0/1.1](formulario-completo-multimedia-v1.1.md). Sin opt-in se reclama solo 1.0; 1.1 requiere `X-Formulario-Versiones: 1.0,1.1`. No confundir versión documental, versión del comprobante institucional y número de ruta HTTP.

El [cierre de main](../docs/CIERRE_CICLO_MAIN_2026-10-24.md) contiene resultados y banderas; el [visor](../docs/VISOR_INSTITUCIONAL_MULTIMEDIA.md) define separación entre documentos del ingreso y de gestión. SIGER aún debe adaptar y probar su consumidor. Redacciones, opciones y eventuales eliminaciones de campos no congelan este baseline ni impiden iniciar la integración; coordinar solo los efectos contractuales reales.

Las referencias V1/V2 siguientes son históricas. El transporte V3 previo se conserva y no contiene por sí solo todo el formulario completo. El [plan de hardening diferido](../docs/PLAN_HARDENING_PRODUCCION_DIFERIDO.md) no se ejecuta en este cierre ni autoriza producción.

---

# Actualización 19-10-2026

Contrato vigente: transporte [V3](registro-comunicacion-v3.md) y variante [NNA declarada](comunicacion-nna-v2.0.md). HTTP `/integracion/v2` está retirado (410, sin reservas); la representación de entrega adulta 2.0 sigue siendo válida dentro de HTTP V3. Nuevos ingresos HTTP requieren la sesión originadora para estado/comprobante. No existe restauración automática ni recuperación entre sesiones. Las descripciones V1 siguientes son históricas.


# Contrato de entrega v1

**Contrato vigente: [registro institucional v3](registro-comunicacion-v3.md).** Se conserva el sobre firmado de custodia v2 y se añade una confirmación institucional firmada con UUID, folio y fecha propios. El comprobante definitivo solo se publica tras el COMMIT institucional. Esta página y OpenAPI v1 describen el perfil histórico de archivos, sin folio formal; no son el protocolo vigente.

**Etapa histórica v2:** [recepción v2](recepcion-v2.md), implementada con JDBC en ambas aplicaciones. V1 se conserva únicamente para la demostración histórica de archivos; no degrada ni exporta las entregas v2 sin su evidencia firmada.

La API vive en el **backend público**. SIGER inicia las conexiones HTTPS salientes; React solo utiliza rutas relativas de su propio origen. No existe llamada desde el ciudadano a la VM privada. [OpenAPI](comunicaciones-v1.openapi.yml) describe rutas, cuerpos y errores; [ejemplo](ejemplo-comunicacion-v1.json) contiene exclusivamente identidad sintética.

## Secuencia y garantías

1. El ciudadano obtiene identidad de su sesión ClaveÚnica, revisa mensaje y aviso. POST `/api/comunicaciones` requiere sesión, CSRF e `Idempotency-Key` UUID. El navegador no envía identidad: atributos adicionales se rechazan. Se recortan espacios exteriores del mensaje.
2. El backend público guarda el sobre antes de devolver `202` y `Location`. Una misma clave, identidad y mensaje retorna el mismo registro; cambiar contenido con la misma clave produce `409`. La fecha y la huella se asignan una sola vez. La clave identifica una entrega, no un RUN ni una sesión.
3. SIGER consulta GET `/integracion/v1/comunicaciones/pendientes?limite=20` con una credencial máquina externa. La consulta no elimina ni marca registros. Los sobres pendientes pueden repetirse: entrega **al menos una vez**.
4. SIGER valida esquema y huella, guarda el registro y su recibo institucional **en una transacción**, con unicidad por `(origen, id público)`. Después del COMMIT envía POST `/{id}/confirmacion` con el recibo y la misma huella. El adaptador local guarda ambos juntos en un archivo atómico. El puerto productivo deberá hacerlo con persistencia institucional definitiva.
5. La confirmación pública también se guarda antes del `204`. Una repetición idéntica devuelve `204`; recibo o huella distintos producen `409`. El registro deja de aparecer en pendientes. La copia pública se conserva en la demo; no hay eliminación automática.

Si SIGER está apagado, no puede confirmar: el ciudadano ve **recibido por el componente público, pendiente de incorporación en SIGER**. Solo tras el recibo institucional ve `CONFIRMADA_SIGER`. Ninguno de estos estados implica admisibilidad, asignación o resolución institucional. Un `202` expresa aceptación para procesamiento, no finalización ([HTTP RFC 9110](https://www.rfc-editor.org/rfc/rfc9110.html#name-202-accepted)).

Si falla la escritura pública, no hay `202`; se devuelve `503`. Si se pierde la respuesta HTTP, el cliente reintenta la misma clave. La interfaz conserva esa clave en memoria durante los reintentos del mismo panel; recargar o editar inicia otra interacción. La recuperación del borrador/clave tras recargar queda pendiente de diseño de producto: no se guarda PII en localStorage. El identificador recibido permite consultar el estado con una sesión del mismo RUN. No se inventa un folio institucional antes del recibo.

Si SIGER falla antes de guardar, no hay confirmación. Si se guarda y se pierde el ACK, la siguiente recuperación encuentra el mismo identificador y retorna el recibo original, sin duplicar. Los errores de lote y de registro se reintentan con espera exponencial de 5 a 300 segundos más 1–5 segundos aleatorios en el planificador local; cada consulta pide 20. Los conflictos conservan el pendiente y requieren revisión. Un error de registro no cancela los otros del mismo lote. No hay borrado por número de intentos.

## Huella y compatibilidad

SHA-256 sobre campos en este orden: `version`, `id` UUID en minúsculas, `recibidaEn` normalizado a UTC mediante `Instant.toString()` de Java, `identidad.run`, `identidad.nombres`, `identidad.apellidos`, `mensaje`, `avisoPrivacidadVersion`. Cada campo se codifica UTF-8 sin normalización Unicode adicional y se precede de su longitud en bytes como entero de 32 bits big endian. La huella es hexadecimal en minúsculas y no incluye `sha256`. En v1 los espacios del mensaje ya se recortaron al recibirlo.

Las dos aplicaciones tienen DTOs independientes y pruebas del mismo vector de contrato, con huella `f3726415c9fd5a12875c2e2a7772451e8c1ad1f95bbcbecef50c4e5209fa1671`. El build raíz comprueba que los fixtures no diverjan. No se comparte lógica institucional ni un artefacto Maven entre aplicaciones. Cambios incompatibles requieren otra versión del contrato/ruta.

## Seguridad y límites actuales

La sesión ciudadana no autoriza `/integracion/**`. Esta frontera es stateless, no usa tokens ClaveÚnica ni cookies y exige credencial Bearer independiente. No tiene CORS. HTTPS es obligatorio salvo loopback del perfil `local-integration-demo`; el cliente no sigue redirecciones y limita conexión/lectura a 5/10 segundos. Las respuestas no se cachean. Secretos, cuerpos, RUN y mensajes no se escriben en logs.

El hash detecta alteraciones y conflictos; no sustituye TLS ni autentica al emisor. La demo genera una credencial aleatoria efímera compartida solo entre los dos procesos. En producción faltan acordar autenticación máquina (por ejemplo credenciales rotables o mTLS), gestión/rotación de secretos, autorización de la cuenta institucional y observabilidad segura.

El adaptador de archivos es **solo una simulación de persistencia local**: un escritor por directorio, `force(true)` del archivo temporal y publicación mediante `ATOMIC_MOVE` sin degradación silenciosa. Se probó reinicio del proceso, no garantías frente a pérdida eléctrica, fallo de disco o múltiples nodos. No es una cola productiva. No hay base de datos nueva, broker ni dependencia de MySQL para ClaveÚnica.

Para producción faltan alojamiento y almacenamiento público transitorio autorizado, backups/recuperación, cuotas y protección contra abuso, aviso legal y retención/eliminación de la copia pública, persistencia definitiva de SIGER y su migración Flyway, credenciales/transporte y prueba de red desde la VM. La paginación justa, aislamiento de registros corruptos, alertas, conciliación de huellas y gestión de conflictos también deben completarse: la ordenación UUID y límite de la demo pueden dejar otros registros detrás de un lote de errores. No se promete un SLA ni entrega productiva resuelta.
