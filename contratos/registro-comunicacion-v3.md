# Registro de Comunicación y comprobante institucional — v3

**Identidad estructurada:** el sobre comunicado admite 1.1 con componentes originales y 1.0 histórico sin reconstrucción. [Contrato y compatibilidad](identidad-componentes-v1.1.md). Registro, folio y ACK v3 se conservan.

Se conserva el sobre v2 y su sello de custodia público. Se versiona la confirmación: un ACK técnico v2 no acredita un registro formal ni un folio. Las dos aplicaciones mantienen artefactos y configuración independientes.

## Flujo

1. CU autentica al ciudadano en el backend público. POST `/api/comunicaciones` conserva sesión/CSRF, aviso e Idempotency-Key. La identidad y procedencia se obtienen del servidor.
2. COMMIT de custodia pública. Respuesta 202: `PENDIENTE_REGISTRO_SIGER`, sin registro institucional, folio o comprobante definitivo. UUID `id` identifica la operación técnica y permite repetirla o consultarla.
3. SIGER inicia POST HTTPS `/integracion/v3/entregas/reclamar` con `{"limite":20}`. Respuesta y reservas tienen la misma forma v2: `reserva`, `venceEnEpochMs`, `intento`, `entrega`. El campo `entrega.comprobante` mantiene su nombre histórico: es un sello de custodia exclusivo del transporte backend.
4. SIGER verifica esa entrega y, en una transacción, guarda su cuerpo íntegro, crea la Comunicación, asigna número/folio, fecha UTC y canal, firma y persiste el comprobante y agrega trazabilidad. Solo devuelve el registro después de COMMIT. Un rollback no publica resultado alguno.
5. SIGER inicia POST `/integracion/v3/entregas/{operacionId}/confirmacion` con `{"reserva":"UUID","recepcion":{"registro":{...},"comprobante":{...}}}`. El público valida firma con claves institucionales configuradas, vinculación a operación/huellas y correspondencia exacta de metadatos. Confirma el ACK en su propia transacción.
6. GET `/api/comunicaciones/{operacionId}` devuelve `REGISTRADA_SIGER`, `registroInstitucional` y `comprobante`. GET `/{operacionId}/comprobante` permite al propietario descargar el documento firmado. Mientras esté pendiente responde 409, sin descargar el sello público como si fuese definitivo.

## Registro firmado

`registro` contiene:

```json
{
  "version":"1.0",
  "emisor":"SIGER_DDN",
  "hecho":"COMUNICACION_REGISTRADA",
  "operacionPublicaId":"UUID técnico del público",
  "comunicacionId":"UUID propio de SIGER",
  "numeroFolio":1,
  "folio":"COM-000001",
  "registradaEn":"2026-10-14T23:00:00Z",
  "canalOrigen":"FORMULARIO_WEB_PUBLICO",
  "sha256":"huella del sobre original",
  "huellaProcedencia":"huella de las afirmaciones por atributo"
}
```

El comprobante Ed25519 tiene `{algoritmo,keyId,payload,firma}`. Payload/firma usan base64url sin padding; payload es la representación JSON canónica de `registro`. Verificación independiente con clave pública institucional confiada; no aceptar claves adjuntas enviadas por un cliente. El formato no pretende FEA ni sellado de tiempo de un tercero. El documento acredita el registro formal de la Comunicación, no una decisión sobre admisibilidad, distribución o gestión.

`numeroFolio` es un entero de base de datos. Consumidores visuales deben mostrar `folio` como cadena, sin reconstruirlo ni transformarlo a un folio a partir del UUID técnico. Los números no se reutilizan ni se promete continuidad sin huecos; el formato de cada Comunicación queda fijado al registrarla.

## Reintentos y desconexión

La unicidad de `(source,operacionId)` impide otro registro, incluso con múltiples consumidores. Repetir entrega o cambiar parámetros de formato devuelve el resultado persistido original. Otro contenido con el mismo ID produce conflicto; no consume una segunda Comunicación válida.

Las reservas mantienen fencing y vencimiento. GET de estado por RUN propietario conserva acceso al mismo resultado tras un reintento. ACK repetido idéntico devuelve 204, incluso después del cierre de nuevos ingresos; firma/contexto distintos se rechazan. El estado pendiente significa que el público **aún no tiene confirmación verificable**, incluso si SIGER pudo registrar antes de perder una respuesta.

SIGER guarda el token de reserva antes de intentar confirmar y concilia sus ACK pendientes por HTTPS saliente. Esto cubre tanto un ACK que no llegó al público como una respuesta perdida después del COMMIT público. No necesita que la entrega vuelva a aparecer en pendientes para repetir una confirmación ya aceptada. Una reserva reemplazada se rechaza y una nueva entrega permite actualizar el token sin cambiar folio o documento.

Fallo por entrega: POST `/integracion/v3/entregas/{id}/fallo`, mismo contrato de códigos/backoff/cuarentena v2. Una caída de SIGER deja custodia pública durable; no se crea folio provisional. El navegador nunca llama a la VM privada.

## Compatibilidad

Flyway V3 conserva los ACK previos en `public_delivery.legacy_ack` y `institutional_inbox.legacy_receipt`. Reabre las entregas públicas confirmadas únicamente con ACK antiguo para registrarlas formalmente. La fecha formal es la del nuevo registro; no se inventa un folio retrospectivo para el ACK técnico. Los sellos y cuerpos originales no se reescriben.

La publicación debe ser coordinada: pausar recuperación, preparar claves institucionales independientes y su confianza pública, migrar ambos esquemas, desplegar ambos JAR y reanudar. El consumidor actualizado usa v3. El endpoint v2 de ACK técnico devuelve 409 para impedir una confirmación formal sin comprobante. V1 continúa como demostración histórica de archivos, sin acreditar registros formales.

Los códigos HTTP, autenticación máquina, límites, CSRF ciudadano y políticas de cuarentena anteriores se conservan. No hay notificaciones externas ni llamadas SRCeI nuevas en este protocolo.
