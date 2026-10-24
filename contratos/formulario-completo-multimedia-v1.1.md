# Extensión multimedia del formulario completo · documento 1.1

Estado: implementado en rama de revisión; no publicado en main ni habilitado en el baseline integrado. Complementa [custodia completa](../docs/CUSTODIA_FORMULARIO_COMPLETO.md) y [baseline](../docs/BASELINE_FORMULARIO_PUBLICO_2026-10-24.md). SIGER institucional aún debe adaptar su consumidor.

## Compatibilidad y negociación

La ruta continúa siendo `/integracion/v3/formulario-completo/entregas`. No cambia el contrato histórico `/integracion/v3/entregas`, sus fixtures, UUID, canonicalización, firmas, dominios de hash, reservas ni ACK. No se reescribe ningún documento histórico.

Una captura con al menos un adjunto del nuevo almacenamiento FILE produce `comunicacion.version = "1.1"`. Capturas del camino histórico conservan `1.0`. La ampliación de cantidad, tamaños, tipos y lectura por streaming requiere adaptación explícita del consumidor aunque el descriptor tenga las mismas claves.

`POST /reclamar`, cuerpo `{"limite":1}`, sin cabecera adicional: reclama únicamente documentos **1.0**. Para aceptar la ampliación, el consumidor envía `X-Formulario-Versiones: 1.0,1.1` (también puede pedir solo `1.1`). Versiones desconocidas, espacios dentro de los valores o una lista vacía se rechazan con 400. Un consumidor antiguo no recibe silenciosamente un documento ampliado. La consulta filtra por versión persistida y comprueba que coincide con el documento firmado.

## Documento e integridad

Continúa el sobre `entrega = {comunicacion,sha256,huellaProcedencia,custodia}`. `comunicacion` conserva `version,id,recorridoId,tipo,recibidaEn,avisoVersion,aceptaCanalDigital,datos,personas,adjuntos`.

- `datos` contiene campos/motivos, referencias de personas e instituciones e IDs de adjuntos. Identidad, procedencia individual y distinción autenticada/registral/declarada provienen del recorrido del servidor.
- Cada descriptor conserva `id,nombre,tamanio,sha256,tipo,estado`. `tamanio` son bytes y `sha256` es hexadecimal de los bytes originales; `estado` es `VALIDADO`.
- No se agregan rutas, claves de almacenamiento, URLs públicas, miniaturas, capacidades de sesión o etiquetas elegidas por el navegador al contenido firmado.
- Cambiar `version` cambia la huella canónica y la firma de **la nueva captura**. No se vuelve a firmar ni reinterpretar una Comunicación recibida anteriormente.
- MySQL V8 agrega `document_version` con valor histórico por defecto `1.0`; V1–V7 no se editan.

## Recuperación de originales

Se reutiliza `GET /{comunicacionId}/adjuntos/{adjuntoId}` con autenticación técnica existente y `X-Reserva` vigente. Se verifica pertenencia al documento firmado, reserva, tamaño y SHA-256 antes de abrir el flujo. Ahora admite `Range: bytes=inicio-fin`, rango abierto o sufijo único, `If-Range` con ETag SHA-256, respuestas 206/416 y HEAD sin cuerpo. Rangos múltiples no están habilitados. HEAD describe el recurso completo; Range solo aplica a GET.

No hay un protocolo de transferencia institucional nuevo. El original sale como descarga privada `application/octet-stream`, `Content-Disposition: attachment`, `Cache-Control: no-store`, `nosniff` y CSP sandbox. La lectura FILE usa buffers de 64 KiB; el histórico MySQL permanece acotado a su límite original de 10 MiB. Se limitan a cuatro respuestas concurrentes en esta instancia.

El consumidor debe transmitir a su archivo privado sin acumular el binario completo, verificar bytes y hash y asociar el mismo objeto documental a los Casos pertinentes mediante referencias. Una reserva de extracción no es una autorización permanente de visualización funcionaria. Debe copiarse y comprobarse la evidencia institucional antes del ACK según sus reglas de recepción.

La confirmación institucional y el ACK mantienen su contrato, correspondencia criptográfica y canal. No se elimina ningún original al confirmar el ACK. No se declara registro SIGER desde esta rama; las pruebas de ACK usan dobles institucionales existentes.

## Dependencias del consumidor

1. Admitir explícitamente 1.1 y los límites/tipos del [perfil multimedia](../docs/POLITICA_MULTIMEDIA_2026-10-24.md).
2. Implementar recuperación de flujo, reintentos de reserva y comprobación de SHA-256 antes de publicar el archivo en su dominio.
3. Separar origen ciudadano inmutable de documentos de gestión y autorizar cada lectura según el objeto institucional.
4. Adoptar el [contrato de visor](../docs/VISOR_INSTITUCIONAL_MULTIMEDIA.md); no ejecutar Office ni insertar originales PDF activos en la interfaz.
5. Acordar retención, transferencia definitiva y manejo de versiones. Un cambio visual del formulario no altera por sí mismo este contrato. Cambios de significado o validación deben coordinarse entre ambos repositorios.

No se modificó `sistema-de-requerimientos`.
