> Sesión y vista previa incluyen runNumero/runDv de origen cuando están disponibles; GET /api/identidad/atributos proyecta su procedencia. El RUN concatenado sigue compatible y las listas completas no se limitan por la cantidad de campos visibles.

> Identidad actualizada: [componentes originales y compatibilidad](../contratos/identidad-componentes-v1.1.md). Sesión y vista previa añaden nombresComponentes/apellidosComponentes; las cadenas anteriores se conservan como representación derivada.

> API del backend público. El contrato nuevo de entrega está en [contratos](../contratos/README.md); estas rutas originales se conservan.

# API inicial

Origen único. Sesión mediante cookie `SIGERSESSION`; sin tokens de CU entregados al cliente. Respuestas sin caché. Errores de validación `application/problem+json` sin eco de entrada sensible.

| Método/ruta | Autorización | Contrato |
|---|---|---|
| GET `/oauth2/authorization/claveunica` | Pública | 302 al authorize oficial; 503 mientras callback no esté aprobado. |
| GET `/redirect` | Callback | Spring recibe code/state, valida y autentica; no guarda ni expone parámetros. 409 si ya existe identidad autenticada; callbacks falsos no cierran una sesión activa. |
| GET `/api/session` | Pública | `{authenticated,identity:{run,nombres,apellidos}\|null,environment,loginAvailable}`. |
| GET `/api/csrf` | Pública | `{token,parameterName,headerName}`; token CSRF de aplicación, distinto de tokens CU. Renovar tras login/logout. |
| POST `/api/comunicaciones/preview` | Sesión + CSRF | Cuerpo `{mensaje}`: 1–2000 caracteres, no blanco; campos adicionales rechazados. Respuesta `{identidad:{run,nombres,apellidos},mensaje,almacenada:false}`. No recepción definitiva. |
| POST `/auth/logout` | CSRF | Invalida sesión/cookie local y 302 al logout CU con URI registrada. Formulario HTML de página completa. |
| GET `/logout` | Retorno CU | Invalida sesión residual anónima y 303 al origen público. 409 si existe identidad activa: cierre local requiere POST/CSRF y ya debe haber ocurrido antes de CU. |
| GET `/auth/session-expired` | Sin sesión activa | 303 al logout CU; 409 si existe identidad activa, para exigir POST/CSRF. |

CSRF puede enviarse como el header indicado por `/api/csrf` o como campo de formulario. POST sin CSRF: 403; sin autenticación pero con CSRF válido: 401; mensaje inválido/campos desconocidos: 400. Ningún campo RUN/nombre remitido por cliente se toma como identidad. DTO de Comunicación solo admite mensaje.
# Ampliación 19-10-2026 · NNA provisional

`GET /api/nna/capacidades` es público. `POST /api/nna/comunicaciones` requiere sesión anónima, HTTPS, CSRF, `Idempotency-Key`, campos declarados y aviso; no admite identidad CU aportada por el cliente. Estado y comprobante bajo `/api/nna/comunicaciones/{id}` requieren la sesión originadora. No hay listado ciudadano. [Contrato y errores](../contratos/comunicacion-nna-v2.0.md), [habilitación exclusivamente local](FORMULARIO_NNA_PROVISIONAL.md). El DTO adulto de la API inicial no se amplía con campos NNA. Nuevos ingresos adultos también vinculan estado/comprobante a su sesión. No se restaura ninguna entrega al recargar.

