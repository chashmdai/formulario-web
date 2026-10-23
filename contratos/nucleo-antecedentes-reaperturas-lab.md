# Núcleo V8 · contrato de laboratorio

Sesión funcionaria HTTPS, CSRF y `Idempotency-Key` UUID en todos los POST; actor/rol se obtienen de sesión. Se mantienen rutas y contratos históricos, sin cambios a recepción pública ni comprobantes.

| Método / ruta bajo `/nucleo` | Lectura o cuerpo |
|---|---|
| GET `/trabajo` | Conteos durables de alcance; no entidad Task. |
| GET `/comunicaciones/buscar?q=&requerimientos=false&pagina=0&limite=20` | `{items,total,pagina,limite}`; búsqueda por folio; máximo 50. |
| GET `/casos?estado=&pagina=0&limite=20` | Proyección de metadatos, sin bitácoras de todos los Casos. |
| POST `/comunicaciones/{hija}/vincular` | Cuerpo compatible `{padre,casoReferencia?,motivo}`. `padre` es la Comunicación raíz de un Requerimiento existente; se conserva como identificador histórico del contrato. Caso opcional debe pertenecer a ese mismo Requerimiento. |
| GET `/antecedentes?caso=&pendientes=false&pagina=0&limite=20` | Proyección paginada por contexto, sin contenido original. |
| GET `/antecedentes/{hija}` | Vínculo, folio, contexto, evaluación y original de solo lectura, solo con alcance vigente. |
| POST `/antecedentes/{hija}/evaluar` | `{revision,revisionCaso,decision:ADMITIDA\|NO_ADMITIDA,fundamento}`. Solo profesional responsable vigente. |
| POST `/casos/{id}/solicitar-reapertura` | `{revision,antecedentes:[hijaUUID],fundamento,sustituye:null,revisionSustituida:null}`. De 1 a 20 antecedentes admitidos de ese Caso. Sustitución explícita solo de pendiente obsoleta con ID/revisión de anterior; no copia sobre ella. |
| GET `/reaperturas?caso=&pendientes=false&pagina=0&limite=20` | Solicitudes del alcance vigente, sin acceso por responsabilidad histórica. |
| GET `/reaperturas/{id}` | Instantánea solicitada, revisión actual y decisión durable. |
| POST `/reaperturas/{id}/aprobar` o `/rechazar` | `{revision,revisionCaso,fundamento}`; jefatura competente, instantánea vigente y pendiente. |
| GET `/casos/{id}/periodos` | Períodos de gestión autorizados por reapertura, no aperturas originales ficticias. |

Respuestas de comando `{id,revision}`. Duplicado idéntico devuelve el resultado previo; otro contenido/actor/acción con esa clave: 409. UUID fuera de alcance: 404; rol no habilitado para listado: 403; sesión ausente: 401; sin CSRF: 403. Validación inválida: 400; error SQL: 503 sin datos sensibles. 409 puede incluir `code` seguro: `VERSION_OBSOLETA`, `SOLICITUD_OBSOLETA`, `SOLICITUD_YA_DECIDIDA`, `CASO_NO_CERRADO`, `REAPERTURA_DERIVACION_PENDIENTE`. Actualizar lectura no reejecuta comandos.

Estados separados: vínculo histórico/sin evaluación, pendiente, pendiente de asignar, ADMITIDA, NO_ADMITIDA; solicitud PENDIENTE/APROBADA/RECHAZADA/SUPERADA; Caso conserva sus estados originales. Estado de solicitud no sustituye estado de Caso. Cerrar BAJA no autoriza reapertura. Ruta directa `/casos/{id}/reabrir` sigue 409; solo una aprobación válida reabre. Rechazo/cancelación de DERIVACIÓN siguen 409, independientemente del rechazo de REAPERTURA.
