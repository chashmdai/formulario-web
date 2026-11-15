# Riesgo 410 y C-03: inferencias sobre antecedentes registrales

Nota de decisión pendiente, 15/11/2026. Describe el comportamiento existente; no autoriza publicación ni cambia validaciones, respuestas HTTP o proveedores. Referencias: `RecorridosJdbc`, `IdentificacionCiudadana` y `AccesoTemporal`.

## Riesgo 410: inferencia de mayoría de edad

El recorrido NNA devuelve HTTP **410** (`DERIVADO_ADULTO`) cuando acredita mayoría de edad registral del presentador. Se exige fecha completa SRCeI consistente con RUN/DV de la misma evidencia y consulta exitosa; se cierra el recorrido y se deriva a adulto. No equivale a autenticar a quien consulta.

Escenario: una persona que conoce o construye RUN/DV válidos inicia recorridos NNA y observa la diferencia entre derivación 410 y continuación del recorrido. Esa diferencia puede revelar que se obtuvo información registral suficiente para acreditar mayoría de edad. Una continuación no prueba minoría de edad ni inexistencia: también ocurre ante datos ausentes, fallos o falta de correspondencia.

Impacto: canal de inferencia sobre edad y disponibilidad de antecedentes de terceros, aun sin exponer nombres, fecha ni respuesta registral. No debe confundirse con los 410 de rutas V2 retiradas o de archivos minimizados, que tienen otra finalidad.

## C-03: oráculo de existencia/completitud

`IdentificacionCiudadana.vista()` calcula `camposFaltantes` y `camposObligatorios` a partir de los atributos útiles disponibles. Los valores registrales permanecen en backend, pero esas listas cambian según la completitud de la consulta. `declarar()` rechaza sustituir campos de otra fuente; la API traduce esa entrada inválida a HTTP **400**. Comparar listas y aceptación/rechazo permite inferir qué campos ya están cubiertos.

Escenario: sobre RUN/DV de terceros, un solicitante compara los campos pedidos en distintos recorridos o intenta declarar un campo que el servidor ya conserva. Un campo ausente de `camposFaltantes`, o el rechazo 400 de esa declaración, puede indicar disponibilidad de antecedentes. Es un oráculo indirecto de existencia/completitud, no una prueba concluyente de existencia: fallos, datos parciales, ClaveÚnica y declaraciones previas influyen en la respuesta.

Impacto: inferencia sobre existencia o completitud de registros y presencia de atributos, sin conocer su valor literal. El requisito de no sobrescribir antecedentes registrales conserva integridad, pero su respuesta observable aporta esta señal lateral. Ocultar únicamente el estado de consulta o el texto del error no elimina las listas ni la diferencia de aceptación.

## Mitigación actual y decisión institucional

Se mantienen cuotas persistentes **por origen** en `citizen_abuse_bucket`: hasta 12 inicios y 12 consultas por ventana horaria, con origen seudonimizado mediante HMAC. La cuota de consulta agotada evita llamar al proveedor y utiliza el fallback. Propiedad de recorrido/sesión, CSRF y acceso temporal reducen exposición; no eliminan la inferencia ni impiden que un atacante distribuya orígenes. La allowlist de pruebas DEV tampoco es una mitigación general de una apertura ciudadana.

Ambos riesgos requieren una **DECISIÓN INSTITUCIONAL pendiente antes de la apertura del 15/12/2026**, fecha de expiración prevista en `AccesoTemporal` (zona `America/Santiago`; la apertura sigue sujeta a sus guardas y autorización). La institución debe decidir y registrar si acepta el riesgo residual con las cuotas actuales o requiere rediseñar respuestas, flujo o controles antiabuso, indicando responsable y criterio de revisión. Esta nota no registra aceptación ni habilita apertura automática. No se modifica el comportamiento de C-03 ni del 410.
