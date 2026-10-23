# Comunicación NNA provisional · Sobre 2.0 / entrega 2.1

Variante compatible por lectura con sobres 1.0 y 1.1 y entregas 2.0. Se transporta mediante **HTTP V3** existente: SIGER inicia HTTPS, reclama reserva, verifica custodia pública, persiste su registro y devuelve ACK firmado. No existe una ruta V2 de reserva utilizable.

## Representación

`SobreComunicacion`: `version="2.0"`, UUID técnico `id`, `recibidaEn`, `identidad=null`, `declaracionNna`, `mensaje`, `avisoPrivacidadVersion`, `sha256`.

`declaracionNna` contiene `primerNombre`, `segundoNombre`, `primerApellido`, `segundoApellido`, `runNumero`, `runDv`, `direccion`, `comuna`, `region`. Primer nombre, primer apellido y región son obligatorios (1–100 caracteres no vacíos); segundos componentes y comuna opcionales (hasta 100), dirección opcional (hasta 300). Los opcionales ausentes son null; una cadena vacía no sustituye null. Se preservan espacios, tildes, capitalización y componentes compuestos. Se rechazan controles y Unicode mal formado. Relato obligatorio, máximo 2000 unidades UTF-16, preservado íntegramente, incluyendo saltos válidos. El límite de admisión HTTP adicional es 8192 bytes por solicitud, incluidos JSON y campos.

RUN opcional, **nunca inventado**: número de 1–8 dígitos, sin cero inicial ni puntuación; DV separado de un carácter `0`–`9`, `K` o `k`. Ambos ausentes o ambos presentes. Se calcula módulo 11 (pesos 2–7 desde la derecha; resultado 11→0, 10→K). Su validez matemática **no verifica identidad ni existencia**. Región y comuna son texto declarado provisional: no se inventó un catálogo institucional ni se valida pertenencia territorial.

`EntregaVerificada.version="2.1"` exige sobre 2.0. La procedencia incluye únicamente los campos presentes y `mensaje`, todos con `fuente="CIUDADANO"`, `nivel="DECLARADO_NO_CONTRASTADO"`, fecha de recepción pública y evidenciaRef igual al UUID técnico. No puede elevarse a ClaveÚnica mediante el cliente. La entrega 2.0 adulta conserva sus requisitos `CLAVE_UNICA_USERINFO / IDENTIDAD_AUTENTICADA`.

## Huellas y firmas

No cambia ningún algoritmo histórico. Sobre 2.0 tiene una nueva huella SHA-256: campos UTF-8 precedidos por longitud int32 big-endian, en orden `version,id,fecha`, luego los nueve campos declarados en el orden anterior, luego mensaje y aviso. Cada opcional y obligatorio de declaración se codifica como `"0"` si null o `"1"+valor` si presente. No se concatenan arbitrariamente componentes.

La huella de procedencia NNA añade el prefijo `PROCEDENCIA_NNA_1` mediante `DataOutputStream.writeUTF` (longitud unsigned int16 y texto ASCII), seguido de campos ordenados lexicográficamente. Para cada uno se codifican con longitud int32 UTF-8: nombre de campo, fuente, nivel, instante y UUID de evidencia. La procedencia adulta conserva su orden y bytes anteriores.

El sello Ed25519 de custodia pública 1.0 liga contenido, procedencia, fecha, UUID y aviso; permanece interno al transporte. **No acredita registro SIGER ni se ofrece como comprobante definitivo.**

Tras COMMIT institucional, se genera `RegistroInstitucional` con comprobante de contenido **1.1**, canal `FORMULARIO_WEB_NNA`, UUID propio, folio, fecha, UUID público y ambas huellas. Los comprobantes adultos 1.0 mantienen `FORMULARIO_WEB_PUBLICO`. No cambia Ed25519 ni se regeneran comprobantes históricos. El validador exige que el canal firmado corresponda a la variante de la entrega.

## Propiedad e idempotencia

La solicitud NNA no contiene principal autenticado, procedencia, fecha, folio ni propietario. El servidor asigna un secreto de propiedad de 256 bits dentro de la sesión y lo mantiene fuera de la API. `public_delivery.owner_session` vincula la entrega a esta sesión; `owner_run` permanece null para NNA incluso cuando se declaró RUN. Los nuevos ingresos adultos HTTP también quedan vinculados a su sesión, conservando `owner_run` como antecedente de identidad.

La huella de idempotencia NNA incorpora dominio `NNA_1`, propietario de sesión, declaración completa, relato y versión del aviso en JSON de orden fijo. Misma clave y contenido recuperan el mismo resultado; otro cuerpo o propietario devuelve 409. No se usan nombres ni RUN para deduplicar personas o Comunicaciones distintas.

`GET /api/nna/comunicaciones/{id}` y `/{id}/comprobante` requieren la sesión propietaria; conocer el UUID no basta. No hay listado ciudadano, reconstrucción de históricos ni recuperación entre sesiones. La pérdida de sesión no elimina custodia ni registro. Una nueva pestaña puede compartir cookie del navegador, pero comienza con estado React vacío: no se ofrece búsqueda/restauración por UUID.

`POST /api/nna/comunicaciones` exige JSON, `Idempotency-Key`, CSRF y `avisoPrivacidadLeido=true`, `avisoVersion`. Devuelve 202 con `PENDIENTE_REGISTRO_SIGER` hasta recibir ACK verificable, o el mismo registro ya confirmado en un reintento. El comprobante devuelve 409 antes de confirmación; 404 para una sesión ajena, sin indicar existencia. Validación 400, falta CSRF/HTTPS 403, cuota 429, recepción operacional cerrada 503. El prototipo cerrado devuelve capacidad false y no admite mutaciones.

Fixtures sintéticos firmados: `fixtures/entrega-nna-v2.1.json`, `fixtures/public-nna-test.json`. Solo contienen clave pública. Se verifican por el receptor institucional; el build compara ambas copias. No deben sobrescribirse para ocultar una incompatibilidad.
