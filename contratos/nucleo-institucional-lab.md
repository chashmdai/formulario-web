# API funcionaria de laboratorio, independiente del transporte V3

Mismo origen HTTPS institucional, sesión/CSRF. Propuesta provisional; no contrato público de integración externa. No se exponen tokens de operación al navegador. DTO concretos en `ContratosNucleo.java`; no hay JSON arbitrario de negocio.

GET `/nucleo/session` y `/nucleo/csrf` permiten iniciar sesión; POST `/nucleo/login` usa formulario URL encoded `username/password` y cabecera CSRF obtenida del segundo. POST `/nucleo/logout` exige CSRF. Después de login se obtiene un token CSRF nuevo antes de mutar.

| Ruta relativa a `/nucleo` | Método / cuerpo |
|---|---|
| `/catalogo` | GET: unidades/sedes/presencias sintéticas; operadores según alcance |
| `/comunicaciones?despues=0&limite=100` | GET: buzón paginado por `secuencia`, límite 1–100; no usar UUID como autorización |
| `/comunicaciones/{id}` | GET: original validado, destinos/Casos visibles, resumen, historial y vínculos autorizados |
| `/comunicaciones/manual` | POST: `texto,canalDeclarado,recibidaEnDeclarada` (ISO instant), `sede,respuestaYaEntregada` nullable |
| `/comunicaciones/{id}/distribuir` | POST: `revision,destinos:[{unidad,sede,evaluador}]` (1–10) |
| `/comunicaciones/{id}/responder` o `/descartar` | POST: `revision,texto` |
| `/comunicaciones/{id}/vincular` | POST: `padre,casoReferencia` nullable, `motivo` |
| `/destinos/{id}/abrir-caso` | POST: `revision,responsable,complejidadProvisional` nullable |
| `/destinos/{id}/responder` o `/finalizar-evaluacion` | POST: `revision,texto`; cero Casos o todos cerrados, respectivamente |
| `/casos/{id}` | GET: Caso autorizado y sus actuaciones/revisiones/asignaciones |
| `/casos/{id}/asignar` | POST: `revision,responsable,complejidadProvisional` |
| `/casos/{id}/actuaciones`, `/solicitar-termino`, `/observar`, `/cerrar` | POST: `revision,texto` |
| `/casos/{id}/reabrir` | POST: deshabilitada, 409; primero se comprueba alcance |
| `/avisos` y `/avisos/{id}/leer` | GET y POST, solo destinatario de sesión |

POST habilitados requieren `Idempotency-Key` UUID y CSRF. Resultado `{id,revision}` del recurso confirmado; recuperación de respuesta perdida devuelve exactamente ese resultado, aunque otra acción haya avanzado después. Clave reutilizada con otro actor/acción/contenido: 409. Revisiones obsoletas: 409; cuerpo inválido: 400; sin sesión: 401; CSRF/acción de rol no permitida: 403; recurso fuera de alcance: 404; persistencia no disponible: 503. No confundir conflicto con confirmación: refrescar el recurso y no cambiar contenido silenciosamente al reintentar.

No se modifica el sobre público, entrega 2.1 NNA, lector histórico, comprobante ni transporte V3. El ciudadano sigue viendo el registro original, no estados internos. La solicitud/aceptación usa ahora el [contrato explícito de laboratorio](nucleo-complejidad-derivacion-lab.md); rechazo/cancelación y reapertura siguen pendientes. Véase [guía y matriz](../docs/NUCLEO_INSTITUCIONAL_LAB.md).

