# Plan diferido de hardening de producción · Formulario Público y SIGER

Estado: **documentado, no ejecutado**. Forma parte del cierre del ciclo del 24/10/2026. Su ejecución requiere una unidad posterior explícita, después de integrar y probar de extremo a extremo ambos aplicativos. No autoriza despliegue, exposición pública, activación multimedia fuera del laboratorio, consultas nuevas a proveedores ni cambios actuales en SIGER.

## Base y responsabilidades

El código del Formulario Público se consolida en main con los documentos completos 1.0/1.1, custodia, archivos privados y controles existentes. La estimación operativa aproximada del 96 % no es cobertura calculada, certificación, aceptación final ni autorización productiva.

La validación funcional institucional pendiente se concentra principalmente en redacciones, opciones de selección y eventuales eliminaciones de campos. El baseline permanece evolutivo. Estas observaciones no bloquean el comienzo de la integración SIGER ni exigen congelar el formulario. Solo un cambio que altere IDs, semántica, tipos, validaciones contractuales o firmas exige coordinación/versionamiento. Ningún ajuste posterior reinterpreta datos históricos.

Las decisiones de tecnología, arquitectura, seguridad e implementación corresponden al equipo de desarrollo. El equipo del Formulario Público conserva la frontera ciudadana/custodia; el de SIGER implementa consumo, registro y autorización institucional. La revisión conjunta debe resolver interfaces y pruebas. No se auditó ni modificó el código de SIGER en este cierre: sus controles siguientes son dependencias a verificar, no vulnerabilidades declaradas.

## Bloqueos reales y condiciones obligatorias antes de producción

La columna de evidencia distingue hechos comprobados de controles aún no calificados. Estos puntos impiden habilitar producción con la evidencia actual; **no impiden la integración de laboratorio sobre el contrato conocido**.

| ID | Evidencia actual / riesgo | Condición de salida para la unidad posterior | Responsabilidad técnica |
|---|---|---|---|
| B1 · Ambientes e integraciones | FormularioConfiguration restringe recorridos a Sandbox y rechaza perfiles prod/production; PISEE es DEV autorizado. No existe verificación productiva de proveedores. | Separar ambientes, revisar configuración y guardas mediante cambio autorizado, obtener habilitaciones/certificados/callbacks correspondientes y verificar OIDC, logout y SRCeI del ambiente autorizado. Nunca reutilizar credenciales DEV ni desactivar TLS/OIDC para habilitar. | Público + operación y responsables de integración; SIGER preserva procedencia recibida. |
| B2 · Consumo íntegro y ACK | El consumidor institucional del formulario completo sigue pendiente; no hay E2E real que acredite registro íntegro. Un consumidor parcial podría truncar datos o emitir ACK prematuro. | Probar adultos/NNA, 1.0/1.1, originales, procedencia, duplicados, COMMIT antes de ACK, ACK perdido, reservas vencidas, firma inválida y conciliación. Unicidad institucional por operación pública y preservación de históricos. | Ambos equipos; SIGER es dueño del COMMIT/folio/ACK institucional. |
| B3 · Identidad, acceso y secretos | HTTPS, sesión/CSRF y credencial de máquina están probados en laboratorio. No están calificados la red de producción, la operación de credenciales ni la autorización del futuro visor institucional. | Validar fronteras públicas/privadas, autenticación técnica revocable/rotable, mínimos privilegios, credenciales y claves separadas por ambiente/propósito. Autorizar cada archivo y Range/HEAD por recurso y relación al Caso; comprobar negativas entre usuarios/Casos. No tratar UUID, RUN o reserva como permiso permanente. | Público: propietario/sesión y API técnica. SIGER: actor funcionario, Caso/actuación y visor. Operación: red y secretos. |
| B4 · Recuperación y custodia criptográfica | Respaldo/restauración local SQL+archivos probados; no hay prueba de pérdida física del anfitrión ni recuperación de claves en otro equipo. Sin clave/confianza histórica no basta con restaurar binarios. | Acordar RPO/RTO, custodiar copias protegidas fuera del anfitrión, restaurar ambos aplicativos y archivos más claves/confianza en entorno aislado y comprobar hashes, firmas, históricos, referencias y continuidad de ACK. Definir rotación sin invalidar evidencia anterior. | Ambos equipos + operación. |
| B5 · Evidencia multimedia segura | ClamAV completo, inspector aislado y perfil limitado están probados; máximos nominales 150/250 MiB y operación prolongada no están calificados. DOC/HEIC/HEIF permanecen deshabilitados. | Mantener fallo cerrado ante scanner/inspector caído, límites insuficientes o aislamiento ausente. Verificar actualización de firmas/motores, artefactos/dependencias y permisos/volúmenes. Calificar límites reales y comportamiento ante capacidad agotada/reinicio antes de anunciarlos públicamente; reducir el perfil de publicación si la capacidad no se demuestra. SIGER no debe ejecutar originales activos ni confiar en extensiones. | Público y operación; SIGER valida integridad al ingresar y aísla cualquier futura conversión. |
| B6 · Transporte y proxy efectivo | TLS nativo local y fragmento Caddy comprobados; no hay prueba del trayecto publicado mediante proxy/Cloudflare ni VM institucional. | Verificar TLS y confianza de ambos extremos, cabeceras de proxy de origen confiable, CSRF/origen, límites/buffering/timeouts, Range, cancelación y reservas. No exponer MySQL, IPC, directorios privados o paneles operacionales. Cloudflare es dependencia solo si se elige ese trayecto. | Ambos equipos + operación de red. |
| B7 · Retención y tratamiento | No hay eliminación automática tras ACK; custodia pública y archivo definitivo se distinguen. La política definitiva de retención/transferencia debe quedar acordada. | Formalizar conservación, respaldo, acceso, trazabilidad y eventual eliminación, incluidos originales/derivadas y evidencia NNA. Comprobar avisos/configuración del ambiente publicado. No ejecutar borrado por ACK o por ausencia de una relación a Caso sin política aprobada. | Desarrollo implementa; responsables institucionales/operación establecen reglas y su aplicación. |
| B8 · Detección y respuesta | Existen auditoría de solicitudes y métricas agregadas; no está comprobada una operación productiva conjunta de alertas/incidentes. | Definir alertas mínimas de AV no disponible, capacidad/cuota, custodia/firma inválida, cola atascada y ACK ambiguo; probar atención, revocación y recuperación. Redactar logs sin contenido, RUN, cookies o secretos; auditar accesos institucionales. | Ambos equipos + operación. |

No se afirma que un control pendiente sea una vulnerabilidad explotable ya demostrada. B1 y B2 son impedimentos concretos de habilitación actual; el resto exige evidencia productiva conjunta que el laboratorio no proporciona. No se modifican guardas ni configuraciones en este cierre.

## Limitaciones que no son por sí mismas un bloqueo de seguridad

- **Sesión perdida tras reiniciar:** el acceso ciudadano falla cerrado con 404 y no revela archivos ajenos. Archivos/BD y recuperación institucional sobreviven. La recuperación de borradores entre sesiones es un pendiente funcional/operativo, no evidencia de bypass ni impedimento para comenzar SIGER. Si el servicio publicado promete esa continuidad, deberá resolverse previamente con diseño de identidad explícito; no serializar tokens OIDC ni añadir una capacidad improvisada.
- **Redacciones, opciones y eliminación de campos:** se incorporan cuando exista nuevo encargo, manteniendo compatibilidad y datos históricos. No se exige aceptación visual definitiva para desarrollar el consumidor conocido.
- **Word sin conversión y PDF con primera página:** la descarga privada controlada es la alternativa vigente. Un visor más completo es ampliación opcional, no condición para recibir evidencia original correctamente.
- **Formatos deshabilitados/codecs fuera de perfil:** mantenerlos cerrados es una decisión segura. Habilitarlos no forma parte del cierre ni se exige para integrar 1.0/1.1.

## Mejoras opcionales, sujetas a necesidad demostrada

| Mejora | Criterio para evaluarla después |
|---|---|
| HA, múltiples escritores o almacenamiento distribuido | Solo si demanda/SLA/RPO/RTO lo requieren. El diseño actual es un escritor por almacén; no abrir otra instancia sobre él sin coordinación. No se impone una sustitución de almacenamiento para integrar SIGER. |
| mTLS u otro mecanismo técnico adicional | La autenticación técnica segura, separada y rotable es obligatoria; la tecnología concreta la decide desarrollo según infraestructura y políticas. No se prescribe mTLS como única solución. |
| Conversión Office/PDF multipágina, transcodificación, nuevos formatos | Requiere valor funcional, aislamiento verificable, límites y revisión independiente. Los derivados nunca sustituyen originales o firmas. |
| Dashboards avanzados, tracing distribuido o plataformas externas | No sustituyen las alertas mínimas y la atención de incidentes. No se introducen proveedores que procesen antecedentes sin autorización. |
| Afinación general de rendimiento, interfaz o caching | Solo con mediciones y nuevo alcance. No cachear evidencia privada en infraestructura compartida ni alterar garantías para aumentar métricas. |

La eliminación automática tras ACK **no** es una mejora opcional habilitable por criterio técnico: requiere previamente la política institucional de retención correspondiente.

## Secuencia propuesta, sin ejecutar

1. **Integración y E2E de ambos aplicativos:** adaptar SIGER al contrato completo, preservar 1.0, optar explícitamente por 1.1 y generar evidencia sintética de registro/ACK/archivos. Mantener las observaciones funcionales menores como backlog evolutivo compatible.
2. **Revisión conjunta de seguridad y operación:** inventario de superficies, dependencias/artefactos, límites, cuentas, claves, RBAC y flujo de datos. Validar los riesgos B1–B8, registrar hallazgos comprobados y priorizar correcciones en una unidad autorizada.
3. **Recuperación e incidentes:** restaurar ambos dominios y material criptográfico fuera del anfitrión, ensayar fallos de scanner, almacenamiento, red y confirmaciones ambiguas con dobles/controlados. Acordar RPO/RTO y respuesta.
4. **Capacidad y resistencia:** después de resolver la estabilidad del entorno, ejecutar campaña limitada y progresiva en ambiente autorizado, sin carga contra proveedores gubernamentales. Calificar límites de petición, archivo, sesión, almacenamiento y consumidor.
5. **Cierre de hallazgos y habilitación separada:** demostrar ausencia de bloqueos abiertos, verificar cambios de contrato/históricos y evidencias; revisar permisos/avisos/políticas del despliegue. La activación productiva requiere su autorización específica; no se deriva del push, los tests ni del porcentaje de avance.

Criterio de salida futuro: evidencia E2E compartida, controles de acceso y fallos negativos correctos, originales/huellas/firmas históricos conservados, restauración conjunta demostrada, límites publicados calificados, operación y responsabilidad de incidentes definidas, y habilitaciones institucionales/proveedores aplicables. No se promete que estas condiciones estén satisfechas hoy.

## Estado al cerrar este ciclo

Únicamente se consolida y publica código/documentación de laboratorio. No se ejecuta este plan ni se crean automatizaciones. No se cambia SIGER, infraestructura pública, flags o secretos. Tras comprobar el push, se detiene la implementación del Formulario Público hasta recibir un nuevo encargo explícito.
