# Observaciones institucionales aprobadas · 31/10/2026

Base publicada: 5a4f4ff600fbbbf353e40043cf42f4a578c3c9fe. Rama de trabajo: validacion-institucional-formulario-2026-10-31.

Esta unidad implementa las diez observaciones aprobadas. No cambia la configuración de los laboratorios, no despliega el código, no habilita recepción/retirada ni publica acceso ciudadano. Las versiones ejecutadas permanecen anteriores a esta unidad.

## Resultado por observación

| N.º | Resultado |
|---|---|
| 1 | Orientación 03, selector y pregunta actualizados para estudios/publicaciones/estadísticas. Se mantienen Observatorio e Informe Anual y se incorpora Estudios con descripción y enlace aprobados. Una sola descripción de la información solicitada; se excluye el relato adicional de la captura estadística, conservando contacto y antecedentes pertinentes. |
| 2 | El backend ya rechazaba empleo como Comunicación. Se conserva y prueba esa regla; la interfaz deriva a Empleos Públicos, deshabilita Continuar y no ofrece revisión/envío para postulaciones. |
| 3 | Promoción exige región y comuna válidas. La fecha tentativa sigue siendo opcional, pero no puede preceder a la fecha de solicitud en Chile. Se preselecciona la región de residencia declarada cuando existe, respetando una elección posterior distinta. |
| 4 | Región de los hechos obligatoria; comuna opcional, validada contra el catálogo cuando se informa. Se elimina momento de los controles, captura, validación y revisión nuevas. Riesgo inminente y su orientación se conservan. |
| 5 | Afectados individuales, grupos o combinación. Los grupos registran contexto sin crear personas, RUN ni identidades ficticias. Nombre/referencia y descripción obligatorios; cantidad aproximada, edades, región y comuna opcionales. |
| 6 | Personas extranjeras sin RUN pueden declarar nombres, nacionalidad, tipo y número de documento. No se consulta SRCeI ni se autentica o verifica esa identidad. No se solicita situación migratoria ni se bloquea por ella. Se conservan RUN y ClaveÚnica. |
| 7 | Nombre preferido y tratamiento adulto opcionales, con las opciones utilizadas en NNA. Se almacenan por separado de los atributos identificatorios/registrales. |
| 8 | La normalización existente ya admitía x@x.x; se añadieron regresiones explícitas. El móvil mantiene +56 9 fijo y ocho dígitos editables, pegado normalizado, corrección/selección y errores accesibles. El correo muestra error de formato al salir del campo y conserva alias/mayúsculas de la parte local. |
| 9 | Ajustes de ancho, espaciado, obligatoriedad y distribución móvil. Los selectores siguen siendo nativos y muestran completa una selección larga. Se corrige el contraste de Orientación revisada en oscuro y se retiran Preguntas por validar y la referencia provisional del contacto. |
| 10 | Advertencia nativa de cierre/recarga/salida cuando hay cambios sin enviar; se conserva la confirmación de salida al inicio. No se implementa persistencia ni recuperación de borradores. |

La nueva regla territorial sustituye la decisión GATE 4A.14.2 del 26/10 para ingresos nuevos. No se reescriben datos ni firmas históricos. NNA independiente conserva primer nombre, primer apellido y región obligatorios, con comuna opcional; los datos territoriales del NNA afectado mantienen su opcionalidad.

## Identificación extranjera y procedencia

Nuevo endpoint del mismo dominio ciudadano:

```text
POST /api/formulario/recorridos/{id}/identificacion-extranjera
```

Requiere HTTPS, sesión propietaria, CSRF, recorrido editable ADULTO_DECLARADO y canal/privacidad aceptados. La operación es idempotente; cambiar entre RUN y documento exige el reinicio explícito ya existente. No es una búsqueda registral y no puede aplicarse a CU, NNA o terceros.

La Persona mantiene su estructura y rol PRESENTADOR. runNumero/runDv son null, modulo11=false, autenticada=false y no hay consulta/ref/fecha SRCeI. Se añade el atributo interno tipoIdentificacion=DOCUMENTO_EXTRANJERO, emitido en servidor con fuente DECLARACION_CIUDADANA. Nacionalidad, tipoDocumento y numeroDocumento tienen esa misma procedencia y nivel DECLARADO_NO_CONTRASTADO.

Tipos: PASAPORTE, IDENTIDAD_EXTRANJERA y OTRO. Nacionalidad y número tienen límite de 120 caracteres; el número es texto, conserva letras, caso y separadores, sin módulo 11 ni un patrón nacional universal. El número no acredita existencia, validez o titularidad del documento.

La proyección ciudadana conserva las mismas claves de control y solo devuelve declaraciones propias, campos faltantes y obligatorios. Los atributos registrales siguen protegidos. Las preferencias adult-preferred, adult-treatment y adult-treatment-text se conservan en datos.campos, sin sustituir nombres o sexo registrales.

## Grupos de NNA y contrato

No se agrega un tipo de Persona grupal ni un nuevo tipo de Comunicación. Se utilizan campos extensibles existentes:

```text
afectados-modalidad = individual | grupal | mixta
grupo-nna-{1..5}-nombre
grupo-nna-{1..5}-descripcion
grupo-nna-{1..5}-cantidad
grupo-nna-{1..5}-edades
grupo-nna-{1..5}-region
grupo-nna-{1..5}-comuna
```

La cuota técnica es cinco grupos, conservando el límite existente de 80 campos. Cada valor grupal admite hasta 500 caracteres; la cantidad opcional es un entero positivo de hasta seis dígitos. El grupo no se expande a filas individuales. El modo mixto exige al menos un registro individual y un grupo; el colectivo excluye de la captura las tarjetas individuales no seleccionadas.

Se conservan el sobre, los records documentales, los tipos existentes, las versiones 1.0/1.1, UUID, canonicalización, dominios de firma, ACK e idempotencia. No se agrega una migración Flyway ni se modifican V1–V12. Los campos nuevos y atributos declarados caben en sus mapas extensibles; los documentos anteriores continúan verificándose sin reinterpretarlos.

Se inspeccionó solo en lectura EntregaCompleta.java y la presentación institucional de C:/req/repo, referencia 48dd236368ca7e2229b1342c5a5a86933d9bff5b. El consumidor conserva campos/atributos desconocidos y no exige RUN para ADULTO_DECLARADO ni marca como autenticados sus atributos. No se modificó, compiló, ejecutó ni migró SIGER. Puede mejorar posteriormente las etiquetas y la agrupación visual de estos campos; esa presentación no bloquea su custodia íntegra. No se presenta esta lectura como una nueva prueba E2E de SIGER.

## Fecha de solicitud y reintentos

La comparación utiliza America/Santiago, con fechas ISO reales. El backend toma un único instante para validar y firmar recibidaEn, evitando discrepancias al cambiar el día. El frontend utiliza el mismo huso y actualiza la fecha antes del primer envío; una revisión abierta hasta el día siguiente puede corregirse antes de generar la clave de envío. Los reintentos inciertos conservan exactamente su clave/cuerpo y no se reinterpretan por una fecha posterior.

## Advertencia de pérdida sin borradores

AdultPage y NnaPage activan beforeunload tras cambios y carga/arrastre de archivos, lo desactivan tras una recepción y respetan la salida ya confirmada. No se guardan relatos, grupos, contactos ni capturas en localStorage/sessionStorage/IndexedDB. Se reutiliza únicamente la identificación por sesión que existía antes de esta unidad.

La advertencia nativa depende del navegador y de una interacción previa; no puede garantizarse ante cierre forzado del proceso o del sistema, especialmente en móviles. No hay recuperación nueva de esos datos porque los borradores quedaron fuera de alcance.

## Pruebas ejecutadas

| Batería local | Resultado final |
|---|---|
| Backend H2, sin Carga*Test | 360 seleccionadas: 358 correctas, dos omisiones opcionales, cero fallos/errores |
| Backend MySQL aislado robust_public_test | 360 seleccionadas: 358 correctas, dos omisiones opcionales, cero fallos/errores |
| Frontend Vitest | 191 correctas, 21 archivos |
| ESLint y build TypeScript/Vite | Correctos |
| Python de respaldo, inventario y lanzadores | 9 + 7 + 19 = 35 correctas |
| Edge/Chromium headless Windows | 30 recorridos controlados, 60 estados capturados y 60 capturas adicionales de viewport |
| Accesibilidad funcional | Cero fallos de contraste medidos, cero desborde horizontal, controles etiquetados, foco del diálogo contenido y acciones visibles |
| Salida nativa | Recarga advertida y cancelada; contenido mantenido en memoria |
| Contratos/seguridad | Persistencia, firmas y ACK sintéticos, idempotencia, propiedad de sesión, CSRF, CU/RUN, SRCeI con dobles, multimedia y retención incluidos en las regresiones |

Omisiones existentes: workers reales opt-in y reinicio destructivo MySQL. No se ejecutaron cargas, login gubernamental, consultas SRCeI reales o una nueva CI remota.

La matriz visual cubre escritorio 1440×1000 y móvil 390×844; temas claro/oscuro/alto contraste, texto 100 %/130 % y movimiento reducido. Incluye identificación extranjera con grupo, consulta estadística con CU controlada, promoción con error/corrección de fecha, derivación laboral y NNA sin RUN. La vista por pasos se prueba en claro y la completa en oscuro/contraste. Se inspeccionaron capturas representativas; no equivale a una auditoría WCAG formal ni a pruebas en teléfonos físicos.

Durante las verificaciones se corrigieron un contraste insuficiente existente, alineación de marcas, conteo colectivo y textos de ayuda. Un fixture ACK nuevo utilizaba un folio sintético fuera del formato contractual; se corrigió el fixture conservando intacta la validación criptográfica. También se corrigieron esperas/selección de elementos en el test visual. Las baterías finales indicadas están correctas.

## Reproducción y evidencia

```bash
npm --prefix formulario-publico/front run lint
npm --prefix formulario-publico/front test -- --maxWorkers=2
npm --prefix formulario-publico/front run build
mvn -B -ntp -f formulario-publico/backend/pom.xml '-Dtest=*Test,!Carga*Test' verify
python3 scripts/qa/test_media_archive.py
python3 scripts/qa/test_media_storage_report.py
python3 scripts/qa/test_runtime_jar.py
```

Para MySQL repetir Maven con ROBUST_DB_URL, ROBUST_DB_USER, ROBUST_DB_PASSWORD y credenciales migradoras privadas del esquema QA autorizado; nunca usar los esquemas ciudadanos ni institucionales.

La prueba visual está en [validacion-institucional.mjs](../scripts/qa/validacion-institucional.mjs), con [dobles](../scripts/qa/ui-fixtures.mjs) exclusivos de QA. Solo admite un origen HTTP loopback. Se ejecutó con Node Windows y Edge ya instalados, sobre un preview privado del dist, interceptando todas las API. No se transmitieron datos al backend ni al proveedor. No se realizaron POST de Comunicaciones, ni siquiera en el doble visual.

Trazas locales: .local/validacion-institucional-20261031/. Capturas y métricas sintéticas: .local/evidencias/validacion-institucional-20261031/. Permanecen fuera de Git.

No queda una definición institucional pendiente para estas observaciones aprobadas. Quedan fuera de esta unidad el despliegue, la aceptación productiva de la infraestructura y las habilitaciones externas de CU/PISEE ya documentadas. El código no se instala en los JAR activos ni cambia sus banderas.
