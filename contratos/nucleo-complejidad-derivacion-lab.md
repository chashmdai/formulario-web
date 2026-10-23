# Complejidad y derivación: extensión de la API funcionaria de laboratorio

Misma sesión HTTPS/CSRF del núcleo, sin token técnico en el navegador. Los POST habilitados requieren `Idempotency-Key` UUID y cabecera CSRF. No modifica el transporte público V3 ni comprobantes.

| Operación | Contrato |
|---|---|
| POST `/nucleo/casos/{id}/clasificar` | `{revision,clasificacion:"BAJA"|null,fundamento}`. Jefatura del alcance; Caso no cerrado. Descripción anterior independiente. |
| POST `/nucleo/casos/{id}/cerrar` | Sigue `{revision,texto}`. Profesional responsable: EN_GESTION + BAJA vigente + fundamento y sin derivación pendiente. Otro contenido no concede facultades. Jefatura conserva ruta provisional anterior. |
| POST `/nucleo/casos/{id}/derivar` | **Solicitar**, no trasladar: `{revision,unidad,sede,motivo,antecedentesEvaluacion}`. Jefatura origen; EN_GESTION; destino diferente y presencia de laboratorio válida. |
| GET `/nucleo/derivaciones/entrantes` | Jefatura destino, alcance propio, últimos 100. Incluye decisiones aceptadas para trazabilidad. |
| GET `/nucleo/derivaciones/{id}` | Solo jefatura origen o destino de esa solicitud. No otorga lectura del Caso ni original ajenos. |
| GET `/nucleo/casos/{id}/derivaciones` | Saliente, solo jefatura origen del Caso. |
| POST `/nucleo/derivaciones/{id}/aceptar` | `{revision,revisionOrigen,destinoId:null|UUID,revisionDestino:null|number,fundamento}`. Jefatura destino correcto, PENDIENTE y origen EN_GESTION. Destino null crea uno nuevo; existente requiere revisión, mismo origen/unidad/sede y EVALUANDO. |
| POST `/nucleo/derivaciones/{id}/rechazar`, `/cancelar`, `/escalar` | **409 sin efecto después de comprobar alcance**; pendientes DDN, no resoluciones operativas. |
| POST `/nucleo/casos/{id}/reabrir` | Permanece 409 después de autorizar. |

Resultado de clasificar: `{id:caso,revision:nueva}`; solicitar: `{id:solicitud,revision:0}`; aceptar: `{id:casoNuevo,revision:0}`. Recuperación idempotente devuelve el resultado original, no una revisión artificialmente actualizada. Cambiar contenido con la misma clave genera 409; refrescar no autoriza a sustituir silenciosamente el cuerpo de un envío ambiguo.

GET Caso agrega `clasificacion`, `clasificaciones:[{clasificacion,fundamento,autor,fecha}]`, `modalidadCierre`, `casoOrigen`, `derivacionPendiente`; `responsable` puede ser null hasta asignación explícita de destino. Históricos sin clasificación/modalidad no se completan por inferencia. Modos nuevos: PROFESIONAL_BAJA, JEFATURA y DERIVACION, sin reinterpretar cierres previos.

GET solicitud contiene IDs origen/Requerimiento, unidad/sede origen y destino, solicitante/fecha, revisión de origen al solicitar, motivo, antecedentes explícitos, estado/revisión, revisión/estado actuales de origen, decisión/actor/fecha/Caso creado y destinos compatibles EVALUANDO con sus revisiones. No incluye actuaciones ni contenido ciudadano original.

401 sin sesión; 403 CSRF o rol sin facultad; 404 recurso fuera de alcance; 400 contrato inválido/propiedades desconocidas; 409 versión obsoleta/transición incompatible o pendiente; 503 persistencia. No aceptar un error como confirmación. Backend valida nuevamente dentro de transacción. Aceptación no hereda responsable/complejidad ni resuelve destinos. [Reglas confirmadas, propuestas y límites](../docs/COMPLEJIDAD_Y_DERIVACION_LAB.md).
