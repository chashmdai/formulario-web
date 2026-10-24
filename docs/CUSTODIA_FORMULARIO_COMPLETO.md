# Custodia del formulario completo — laboratorio

Fecha de cliente: 2026-10-23. Continúa la identificación persistente del commit `1e6af92` y los componentes NNA de `9e8eafe`. No sustituye el Encargo Maestro.

## Alcance efectivo

Implementado en Java/JDBC y probado con H2 y MySQL: recepción NNA con relato íntegro de 5000 caracteres; ubicación, nombre preferido, tratamiento, contacto primario/secundario y precauciones; identidad/procedencia tomada exclusivamente del recorrido de servidor; custodia técnica Ed25519; idempotencia por clave y propietario; congelación del recorrido; cola recuperable, reservas, reintentos, cuarentena y ACK institucional verificado. Las respuestas ciudadanas contienen estado y comprobantes, sin el agregado privado. Un comprobante de custodia no es un folio SIGER.

Actualización 24/10/2026: las reglas y los recorridos completos adultos ya están implementados y comprobados; su bandera adicional `formulario.adultos.habilitado` se habilitó en el laboratorio integrado después de los controles. Por defecto permanece cerrada. No se hace pasar un adulto declarado por una identidad ClaveÚnica ni por una declaración NNA. Ver `CIERRE_ADULTOS_2026-10-24.md` para evidencia, reversión y límites institucionales.

V7 agrega `citizen_submission` y `citizen_attachment`, con claves foráneas al recorrido. V1–V6 no se modifican. El catálogo `front/src/formulario/territorio.json` es la única fuente compartida para Java y React, congelada desde `ddn-formulario@13f9cf6`; no se presenta como actualización oficial de comunas.

## Adjuntos

Hasta 5 archivos, 10 MiB por archivo y 25 MiB por lote. Metadatos y bytes se guardan en MySQL privado; no existen rutas estáticas ni nombres de disco proporcionados por el ciudadano. Se validan extensión/firma de contenido, texto UTF-8 y estructura ZIP acotada para DOCX (sin travesías, entradas repetidas ni VBA). El antivirus inspecciona todos los bytes antes de guardar. Estos controles no garantizan la ausencia de toda amenaza.

Un archivo rechazado o un scanner indisponible conserva el lote previo y no genera una aceptación falsa. Por defecto no hay scanner configurado y las cargas fallan con 503. Configurar `formulario.adjuntos.clamav-port` conecta INSTREAM exclusivamente a loopback, con conexión/lectura acotadas. Las pruebas unitarias usan un doble TCP. La instalación real y las verificaciones de ClamAV en el laboratorio NNA se completaron el 2026-10-24; ver `CHECKPOINT_2026-10-24.md`. Otras instancias requieren su propia configuración y comprobación.

Reemplazar/borrar requiere HTTPS, CSRF, sesión propietaria y recorrido editable. Descargar exige propietario o reserva institucional activa, verifica tamaño/SHA-256 y entrega `application/octet-stream`, `attachment`, `no-store`, `nosniff` y CSP sandbox. Después del envío se bloquean cambios de identidad y archivos. Los temporales multipart del servidor deben mantenerse en almacenamiento privado del proceso; el límite es 26 MiB, dos cargas concurrentes. No se elimina información ya recibida ni se implementa aún una política automática de retención.

## API ciudadana nueva

- `GET /api/formulario/recepcion/capacidades`: habilitación efectiva por recorrido.
- `POST /api/formulario/recorridos/{id}/comunicaciones`, cabecera `Idempotency-Key: UUID`: `DatosFormulario {campos, personas, instituciones, adjuntos}`. En NNA, `campos` usa los IDs del mockup y excluye canales inactivos; `personas`/`instituciones` vacíos. `adjuntos` contiene UUID propios. No acepta identidad ni procedencias del navegador.
- `GET /api/formulario/comunicaciones/{id}`: estado/comprobantes del propietario.
- `GET/POST/DELETE /api/formulario/recorridos/{id}/adjuntos`; POST multipart con partes `archivos` y longitud conocida.
- `GET /api/formulario/recorridos/{id}/adjuntos/{adjunto}`: descarga privada.

La captura completa admite JSON de hasta 64 KiB y cadenas de 5000 caracteres. Las rutas históricas conservan su límite de cuerpo previo y sus validaciones de DTO; también la identificación conserva 8192 bytes. Esta ampliación no cambia el máximo de 2000 caracteres de los DTO históricos.

## Evolución necesaria del consumidor — no aplicada a SIGER

El sobre histórico no representa contactos NNA completos, relatos de 5000 caracteres, adjuntos, adultos declarados ni procedencia mixta. Truncarlos o etiquetarlos como ClaveÚnica sería incorrecto. Se conserva la cola y todas las rutas `/integracion/v3/entregas` existentes; el consumidor actual no recibe documentos desconocidos.

Se implementa una extensión **optativa y separada** bajo `/integracion/v3/formulario-completo/entregas`:

- `POST /reclamar {limite:1..10}` → `{entregas:[{reserva,venceEn,intento,entrega}]}`.
- `entrega`: `{comunicacion,sha256,huellaProcedencia,custodia}`. `comunicacion.version="1.0"`, id/recorrido/tipo/fecha/aviso/aceptación, captura completa, personas internas con procedencia y descriptores de adjuntos.
- Huellas SHA-256 de JSON con claves ordenadas recursivamente, dominio `SIGER_FORMULARIO_COMPLETO_1\0CONTENIDO\0` o `PROCEDENCIA`. No utiliza ni reemplaza la canonicalización histórica. La firma técnica existente liga ambas huellas, id, fecha y aviso.
- `GET /{id}/adjuntos/{adjunto}`, cabecera `X-Reserva: UUID`: bytes privados durante una reserva vigente de cinco minutos.
- `POST /{id}/confirmacion {reserva,recepcion:RegistroInstitucional}`: comprueba firma institucional confiada, id público, ambas huellas, canal, registro/folio y reserva. El sello público no sirve como ACK. Repetir el mismo ACK es idempotente.
- `POST /{id}/fallo {reserva,codigo}`: códigos históricos; espera progresiva y cuarentena por contrato inválido o diez intentos. Un ACK válido previo permanece consultable.

Estas rutas heredan TLS, token de máquina y separación de sesión ciudadana del filtro de integración existente. El consumidor debe incorporar explícitamente esta extensión, persistir todo el agregado y sus fuentes, verificar huellas/firmas, recuperar/verificar todos los adjuntos y emitir su ACK **después** de confirmar la transacción institucional. No se ha modificado `sistema-de-requerimientos` ni se afirma compatibilidad real de ese consumidor con el esquema nuevo. La verificación de ACK de esta extensión usa únicamente claves institucionales sintéticas de prueba.

## Habilitación y reversión

Además de `jdbc-reception` y la configuración privada de recorridos, requiere `formulario.recepcion.habilitada=true`, `publico.recepcion.aviso-validado=true` y controles operativos de recepción/recuperación en NORMAL. Por defecto queda cerrado. El formulario nuevo sigue limitado a Sandbox; no desplegar en producción.

Reversión funcional: cerrar recepción/recuperación mediante los controles existentes y desactivar la nueva bandera; conservar V7, filas, archivos y claves de verificación. No bajar el esquema, borrar volúmenes ni restaurar una base anterior sobre información recibida. El binario histórico ignora las tablas nuevas. Para retomar, habilitar tras validar configuración y pruebas.

## Evidencias

Suite ampliada: 148 pruebas backend descubiertas, 4 opcionales omitidas; H2 y MySQL se registran por separado en `.local/evidencias/formulario-completo-{h2,mysql}-final.log`. Incluye identidad parcial en sobre privado, texto de 5000 caracteres, idempotencia concurrente, reinicio lógico del servicio, propietario, congelación, cuotas de archivos, antivirus controlado, ZIP inválido, integridad, reserva vencida, ACK ajeno/falso, cuarentena y pruebas HTTP/CSRF/TLS. Los resultados finales se consignan en el checkpoint; no son pruebas reales contra proveedores gubernamentales.
