# Antecedentes y reaperturas · iteración de laboratorio 20-10-2026

## Autoridad y alcance

El encargo nuevo confirma F01–F09. En particular **F04 reemplaza la reserva de la entrega del 19-10**: origen abierto mientras espera; aceptación por jefatura destino crea otro Caso bajo el mismo Requerimiento y cierra origen por DERIVACION. Las notas anteriores se conservan como historia, con esta actualización explícita.

| Tratamiento | Reglas de esta entrega |
|---|---|
| Confirmado por Benjamín | F01 jefatura destino acepta derivación; F02 profesional responsable cierra BAJA con fundamento; F03 complejidad por Caso; F04 secuencia de aceptación; F05 Distribución vincula a Requerimiento o Caso; F06 profesional evalúa admisibilidad; F07 no admisión conserva original/trazabilidad; F08 profesional solicita y jefatura decide reapertura de cerrado; F09 BAJA no autoriza auto reapertura. |
| Implementación técnica / provisional | Matriz sintética restringida existente; jefatura de unidad/sede asigna responsable, también en cerrado para encaminar antecedentes sin reabrir ni reclasificarlo; un vínculo por Comunicación hija como límite previo; una evaluación explícita e inmutable por vínculo/contexto; una solicitud pendiente por Caso/cierre, serializada por bloqueo de raíz; reanudación del destino afectado RESUELTO al aprobar, conservando su decisión anterior e historial; nuevo período explícito de gestión. |
| Pendiente, deshabilitado | Encaminamiento de vínculos solo a Requerimiento; rechazo/cancelación/escalamiento de DERIVACIONES (P01–P04); reapertura ordinaria de cierres DERIVACION; competencias entre unidades, bitácoras sectoriales, catálogo completo de complejidad y significado final de evaluación de destinos. |

No se implementan personas maestras, casos automáticos, avisos externos, catálogos ciudadanos ni autenticación institucional definitiva. El formulario público no recibe cambios funcionales.

## Protocolo de decisiones

1. Distribución selecciona por folio un registro que ya tiene Requerimiento y, opcionalmente, un Caso de ese agrupador. Se preservan los controles de duplicación y ciclos; el backend valida pertenencia. El original firmado no cambia.
2. El vínculo queda sin evaluación. Históricos sin referencia nueva se muestran `SIN_EVALUACION_HISTORICA`; no se migran a admitidos. Caso con profesional vigente: él evalúa; jefatura consulta en su alcance pero no decide admisibilidad por él. Vínculo solo a Requerimiento: pendiente de asignar evaluación, sin difundir a profesionales arbitrarios.
3. El profesional registra `ADMITIDA` o `NO_ADMITIDA`, fundamento, actor y fecha del servidor. La decisión pertenece al contexto del vínculo. Admitir en abierto solo referencia el original, incluso si el Caso estaba en revisión de término: no lo devuelve, cierra o reasigna. Leer aviso no evalúa.
4. En cerrado ordinario, el profesional vigente selecciona hasta 20 antecedentes admitidos y solicita reapertura. Se guarda cierre anterior, responsable, revisión y conjunto invocado. El Caso permanece CERRADO. La jefatura competente aprueba o rechaza con fundamento; Distribución y profesional no aprueban.
5. Aprobar cambia el mismo Caso a EN_GESTION, registra autorización/período y reevalúa ACTIVO. Conserva revisiones de cierre, fechas originales, decisiones y asignaciones. Si el destino estaba RESUELTO, solo él vuelve explícitamente a EVALUANDO; no cambia los hermanos ni se borra su decisión anterior. Esta reanudación del destino es la propuesta provisional autorizada para laboratorio.
6. Rechazar conserva el Caso cerrado y la admisión intacta; registra decisión y aviso interno al profesional. Una nueva solicitud sobre ese cierre debe incluir al menos un antecedente admitido que no se haya invocado en sus denegaciones anteriores. Es una restricción técnica reversible contra ciclos idénticos, sin atribuir nuevas competencias ni reconsideración automática.

Una vinculación/evaluación incrementa la revisión del Caso sin cambiar su estado; una reasignación también. Si cualquiera ocurre mientras hay solicitud pendiente, decidir esa solicitud da conflicto, aunque el navegador envíe la revisión actual. El profesional vigente puede **sustituir explícitamente una solicitud obsoleta**: la anterior queda SUPERADA, preservada con sus fundamentos y evidencias; la nueva referencia a la anterior y toma instantánea vigente. No puede sustituir una solicitud aún vigente. No existe cancelación implícita ni sobreescritura de evidencia.

## Integridad, acceso y persistencia

V8 MySQL/H2 agrega referencia opcional de Requerimiento y revisión a `core_link`, `core_link_evaluation`, `core_reopen`, `core_reopen_evidence` y `core_management_period`. No modifica V1–V7, textos antiguos, firmas, huellas ni comprobantes. Unicidad histórica V7 sobre origen se conserva: mientras P01–P03 no estén aprobadas, sigue el límite de una solicitud de derivación por origen. Evolucionarla requerirá otra migración, estados finales, exclusión de una pendiente bajo bloqueo y carrera aceptación/cancelación con un solo ganador; no se anticipa esa habilitación aquí.

Comandos reutilizan clave UUID + actor + acción + huella del cuerpo, transacción y bloqueo de Comunicación raíz. Misma clave/cuerpo recupera resultado; contenido distinto da 409. Solicitud, decisión, período, cambio de estado/destino, auditoría y resultado confirman o revierten juntos. Decisiones concurrentes serializadas: un resultado durable; repetición idéntica recuperable.

Lectura de antecedente está acotada al Caso destinatario: profesional responsable vigente y jefatura unidad/sede; Distribución conserva su alcance documental previo. Conocer UUID no habilita consultar el resto de Comunicaciones/Casos/actuaciones. Solo al Requerimiento: contenido documental por Distribución; jefaturas con destino ya autorizado consultan solo metadatos del vínculo sin recibir el relato por compartir agrupador, sin nuevo evaluador. Avisos revocan visibilidad al cambiar responsabilidad; no otorgan acceso al contenido.

Listados paginados (20 por defecto, máximo 50) y conteos SQL por alcance; búsqueda por folio en el servidor. Los datos de bitácora se consultan al abrir un Caso, no para poblar la bandeja. Catálogo sintético existente, sin sedes/personas institucionales inventadas. No hay nueva infraestructura, dependencia frontend ni fuente remota: fallback tipográfico del sistema y assets empaquetados en JAR.

## Operación y reversión

Inicio habitual: `scripts/Nucleo-Local.ps1 iniciar`. SIGER: `https://localhost:8443/`. Formulario: origen HTTPS actual, sin cambio de CU/callback/logout. Acceso sintético desde archivo privado existente; no se incluyen contraseñas en esta guía.

Actualizar: detener únicamente SIGER con `scripts/Nucleo-Local.ps1 detener-siger`, compilar interfaz y JAR, iniciar `scripts/Nucleo-Local.ps1 siger`. El script migra con su cuenta DDL local existente. No borrar bases. La aplicación pública y su base permanecen independientes.

Reversión compatible: detener SIGER y conservar V8 y sus datos. El JAR V7 sin adaptación no es una reversión segura: sus INSERT posicionales de vínculos desconocen las columnas nuevas. Preferir corrección hacia adelante o un artefacto de reversión probado que mantenga V8, lecturas y escritor de vínculos con columnas explícitas y deshabilite solo las acciones nuevas. Restauración de respaldo completo implica conciliar primero cualquier ingreso recibido desde el respaldo; no ejecutar descenso de esquema ni borrar autorizaciones. Copias locales previas en `.local/hijas-baseline` sirven de comparación, no son un downgrade automático.

Las reuniones deben definir encaminamiento de antecedentes solo al agrupador, excepciones de derivación, destino final de evaluación, matriz de datos sensibles y responsables activos, bitácoras y catálogo completo de complejidad. No presentar este laboratorio como SIGER terminado o habilitado productivamente.
