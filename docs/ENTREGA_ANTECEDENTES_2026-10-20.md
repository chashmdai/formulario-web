# Entrega V8 · antecedentes, reaperturas y trabajo funcionario

Iteración sobre el núcleo existente, exclusivamente de laboratorio. Las confirmaciones corresponden a Benjamín en este encargo; no se atribuye aprobación DDN al conjunto del sistema. [Matriz de reglas confirmadas, provisionales y pendientes](ANTECEDENTES_Y_REAPERTURAS_LAB.md). F04 queda confirmada; las entregas y el flujo anteriores conservan su texto histórico con una nota que lo actualiza.

## Cambios revisables

| Conjunto | Archivos / propósito |
|---|---|
| A. Decisiones vigentes | `README.md`, `docs/ANTECEDENTES_Y_REAPERTURAS_LAB.md`, actualización de `DECISIONES_TECNICAS.md`, `ARQUITECTURA_APLICACIONES.md` y notas en entregas/guías/flujo previos. [Contrato HTTP V8](../contratos/nucleo-antecedentes-reaperturas-lab.md). |
| B. Backend | `nucleo/{ContratosNucleo,NucleoController,NucleoInstitucional}.java`: evaluación por vínculo, solicitudes versionadas, aprobación/rechazo, períodos, consultas por alcance y paginación. `robustez/{FalloTecnico,ErroresTecnicos}.java`: código de conflicto seguro y opcional, compatible con errores previos. |
| B. Migración | **V8__antecedentes_y_reaperturas.sql**, MySQL y H2: `core_link` con referencia nullable de Requerimiento/revisión; `core_link_evaluation`, `core_reopen`, `core_reopen_evidence`, `core_management_period`. Históricos sin admisión inferida, sin reabrirlos por migración. |
| C. Interfaz institucional | `front/src/{App,Comunicacion,Caso,Antecedentes,Derivaciones,Manual,ui}.tsx`, `api.ts`, `styles.css`: navegación por rol/tarea, listados, detalle contextual, paneles de decisión, selección por folio/Caso y tokens SIGER. Sin dependencias nuevas ni cambios de manifiestos/lock. |
| D. Pruebas | `AntecedentesReaperturaTest.java`; ampliación de `NucleoHttpTest`, expectativas vigentes de `NucleoInstitucionalTest`, limpieza solo de tablas de prueba en `DatosNucleoTest`; `front/src/App.test.tsx`. |
| D. Operación | `scripts/{Antecedentes-Local,Test-Antecedentes}.ps1`, `Probar-Antecedentes.mjs`, `Verificar-Contraste.mjs`. Reutilizan los scripts madre, credenciales protegidas y MySQL existentes. |

Rutas Java bajo `siger-institucional/backend/src/{main,test}/java/cl/nexbyte/siger/institucional/`. [Inventario exacto del código comparado](evidencias/antecedentes-2026-10-20/archivos-codigo.json). Copias anteriores en `.local/hijas-baseline` para revisión local. No se hicieron commits masivos; el repositorio sigue sin HEAD y los archivos previos estaban sin seguimiento.

Se conservan las dos aplicaciones/JAR/bases, contratos adulto/NNA, folios, idempotencia, firmas y ACK. [14 archivos de migraciones V1–V7 cotejados](evidencias/antecedentes-2026-10-20/migraciones-v1-v7.json); sin modificaciones. El receptor institucional firmado permanece idéntico a la copia previa. No se cambió el código público ni se recompiló su JAR durante esta iteración; se comprobó su ejecución existente y sus regresiones. El JAR institucional sí se reconstruyó con React actualizado.

## Resultado funcional y pruebas por escenario

| Criterios | Evidencia ejecutada |
|---|---|
| 1–4: vínculo, alcance, no admisión e incorporación a abierto | Pruebas de vínculo sin admisión/reapertura, UUID ajeno, conservación de `communication_registry` y referencia única idempotente. Admisión en REVISION_TERMINO no devuelve la gestión ni crea otro Caso. Demo HTTPS y recorrido visual. |
| 5–8: solicitud, rechazo, aprobación | Pendiente conserva CERRADO; profesional BAJA y Distribución no aprueban. Rechazo conserva ADMITIDA y aviso; aprobación conserva Caso, Requerimiento, cierre y fecha original. Un período autorizado. HTTP real sintético y navegador con sesión de jefatura. |
| 9: destino | Aprobación reanuda explícitamente solo el destino afectado RESUELTO; Casos/destinos hermanos no se reactivan. Requerimiento vuelve ACTIVO. Propuesta de laboratorio, no regla final DDN. |
| 10–12: versiones, concurrencia y rollback | Nuevos antecedentes/reasignación hacen obsoleta la solicitud; sustitución explícita preserva la anterior. Dos decisiones concurrentes solo una confirma; misma clave concurrente recupera mismo resultado. Fallo SQL forzado después de autorizar revierte autorización, Caso y período; reintento válido. |
| 13: solo Requerimiento | Sin profesional arbitrario ni difusión a unidades. Jefaturas ya autorizadas al agrupador reciben metadatos del vínculo, **sin relato de la hija**. Profesional no puede leerlo/evaluarlo. Regresión backend y React. |
| 14–15: excepciones y regresiones | Cierre DERIVACION no admite reapertura ordinaria; rechazo/cancelación/escalamiento de derivaciones siguen bloqueados. BAJA, aceptación, hermanos, término global, ACK, TLS, V2 410, NNA, identidad/contratos/comprobantes históricos. |
| 16–19: tareas y navegación | Distribución vinculó COM-000032 mediante buscador y selector, sin escribir UUID. Profesional la encontró, admitió y solicitó reapertura; jefatura aprobó desde su decisión contextual. Recarga/atrás consultaron datos durables, sin repetir comando. El backend y la prueba React verifican reintento con misma clave tras respuesta perdida. |
| 20–23: presentación | Capturas de inicio, bandeja, Caso, evaluación y decisión; foco visible, Tab hasta confirmación, Escape y restitución del foco; 1366×768, 1920×1080 y 420×900. Reflujo 683×384, con diálogo desplazable y botón alcanzable. Assets del DOM locales, sin fuentes remotas en CSS. 16 pares de contraste, mínimo calculado **5,91:1**. |
| 24: público/sesiones | 66 pruebas públicas exitosas; 13 React públicas; pruebas HTTPS de cookies por host y logout en ambas direcciones. Público y SIGER funcionaron con el otro backend apagado. No autenticación gubernamental nueva. |

No se usa una cifra total como sustituto de escenarios: los resúmenes JUnit incluyen nombres de pruebas para reproducir y revisar.

| Batería | Resultado efectivo |
|---|---|
| Institucional / MySQL 8.4 | **87 exitosas**, sin fallos, errores u omitidas. [Escenarios](evidencias/antecedentes-2026-10-20/junit-mysql.json). |
| Institucional / H2 | **87 exitosas**, sin fallos, errores u omitidas. [Escenarios](evidencias/antecedentes-2026-10-20/junit-h2.json). |
| Público / MySQL | 70 descubiertas: **66 exitosas, 4 optativas omitidas**, sin fallos/errores. [Escenarios](evidencias/antecedentes-2026-10-20/junit-publico-mysql.json). |
| Frontend institucional | **14 exitosas**; lint, TypeScript y build exitosos. |
| Frontend público | **13 exitosas**. No se modificó su código ni se repitió su build. |
| Empaquetado | JAR institucional reconstruido y ejecutado con su interfaz propia. |

Omitidas: `CargaBaseTest`, `CargaAcumuladaTest`, `CargaHttpTest`, `RecuperacionProcesoTest`. No se ejecutaron carga nueva, caída abrupta, proveedor gubernamental real ni autenticación productiva. Público/H2 no se ejecutó en esta iteración. Las pruebas de independencia de backends sí incluyeron detención/reinicio controlados, sin detener MySQL ni perder entregas.

## Demostración reproducible

En PowerShell 7, desde la raíz de SIGER:

```powershell
.\scripts\Nucleo-Local.ps1 iniciar
.\scripts\Antecedentes-Local.ps1 demostrar
```

Si los servicios ya están encendidos, ejecutar solo la segunda línea. La demo crea seis ingresos NNA sintéticos reales, sin CU ni RUN; cada uno atraviesa HTTPS → MySQL público → pull de SIGER → registro/folio → ACK → comprobante Ed25519 verificado. Crea además registros manuales sintéticos, con identidad de operador, para evaluación pendiente y vinculación visual; no fabrica comprobantes ciudadanos para ellos.

La ejecución final dejó **COM-000033** como raíz; COM-000034 no incorporada al contexto, COM-000035 incorporada a abierto, COM-000036 admitida con reapertura rechazada y COM-000037 admitida con reapertura aprobada. COM-000038 está solo al Requerimiento, pendiente de encaminamiento. Dos Casos del mismo agrupador: primero CERRADO y segundo EN_GESTION después de autorización; destino reanudado explícitamente y Requerimiento ACTIVO. COM-000039 queda pendiente de evaluación por `demo.profesional2.upr.a`; COM-000040 queda preparado sin vínculo para repetir el selector visual.

[Resultado de gestión](evidencias/antecedentes-2026-10-20/resultado-antecedentes.json), [firma original](evidencias/antecedentes-2026-10-20/verificacion-firma.json) y [cotejo MySQL](evidencias/antecedentes-2026-10-20/originales-firmados.json). Las 44 huellas de custodia/inbox existentes antes de esa ejecución permanecieron intactas. Además se cotejaron las 32 huellas existentes antes de empezar esta iteración, sin cambios. No son cantidades de personas ni de Comunicaciones.

El recorrido visual adicional usó la raíz anterior COM-000025 y la hija manual COM-000032: vínculo → admisión → solicitud → aprobación por jefatura → mismo Caso reabierto y período visible. Esa gestión quedó separada del comprobante original. Las evidencias de ambas ejecuciones no se presentan como un único instante de datos.

SIGER: `https://localhost:8443/`. Público: su dominio HTTPS actual. Inicio y cuentas sintéticas se conservan en el mecanismo privado existente; esta guía no contiene contraseñas. Para ver vínculos/casos, usar la cuenta correspondiente a la unidad y responsabilidad de la demo. No hay selector ficticio de actor.

Pruebas repetibles:

```powershell
.\scripts\Test-Antecedentes.ps1 -Base MySQL
.\scripts\Test-Antecedentes.ps1 -Base H2
npm --prefix siger-institucional/front test
npm --prefix siger-institucional/front run lint
npm --prefix siger-institucional/front run build
npm --prefix formulario-publico/front test
node .\scripts\Verificar-Contraste.mjs
```

Los esquemas de pruebas son `robust_inst_test` / `robust_public_test`, distintos de los locales de demostración. No ejecutar dos baterías sobre el mismo esquema a la vez. El script obtiene la credencial DDL protegida para migrar y usa la cuenta de aplicación para operación; no imprime secretos.

## Inspección visual, fuentes y límites

[Capturas y registro de inspección](evidencias/antecedentes-2026-10-20/visual/). Antes/después del inicio y bandeja; Caso cerrado/reabierto; selección de vínculo, evaluación y decisión; anchuras y teclado. Se corrigieron durante la revisión el encabezado desplazado del diálogo y la confusión del rótulo PENDIENTE de reapertura con evaluación documental; se añadió acceso al Caso desde el listado de antecedentes.

Referencias encontradas: `tailwind.config.js`, `src/{index.css,theme.ts,App.css}`, `package.json`, `package-lock.json`, `vite.config.ts` y `eslint.config.js` en la ruta SMID indicada; `D:/SIGER_MOCKUP_INTERACTIVO.html`. **No está `D:/SIGER_MOCKUP_INTERACTIVO(2).html`**; se usó el archivo sin sufijo. No se recibieron copias con nombres `package(3).json`/`package-lock(1).json`; sí sus rutas equivalentes proporcionadas. El flujo local es la transcripción histórica existente, subordinada a F01–F09. Los manifiestos, configuraciones y reglas del mockup no se copiaron.

Límites expresos de la inspección:

- **Zoom real 200% no acreditado:** el navegador integrado no aplicó la combinación de zoom, mantuvo ancho/DPR. Se verificó reflujo a 683×384, sin afirmar que equivalga a ejecutar el zoom real. Falta comprobación humana en navegador al 200%.
- No se obtuvo una traza de red completa: la superficie de inspección no expone Performance. Se revisaron consola (sin errores de aplicación), assets del DOM y fuentes CSS/API locales; no se instaló instrumentación ni se alteró seguridad para sortearlo.
- La automatización no completó el control nativo `datetime-local` del registro manual. Ese registro se preparó por API con sesión/CSRF reales y el resto del circuito se ejecutó visualmente; el ingreso manual tiene pruebas React y HTTP, pero no se declara completada su entrada de fecha en navegador por esta herramienta.
- No se verificó lector de pantalla ni una auditoría WCAG completa. Historial de Comunicación mantiene el límite heredado de 200 eventos recientes; los avisos muestran hasta 100 recientes. La base conserva todos sus hechos.

Comprobación manual pendiente: poner zoom real 200%, recorrer sidebar/listado/Caso/diálogo con Tab y Escape; confirmar que texto y acciones se alcanzan sin recortes; completar fecha de recepción manual en el control del navegador. No hace falta contactar servicios externos para estos pasos.

## Riesgos y reversión

Persisten los vacíos institucionales de encaminamiento de vínculos solo al agrupador, matriz/actividad de responsables, bitácoras y catálogo completo, fin de evaluación del destino y excepciones DERIVACION. Rechazo de REAPERTURA implementado; rechazo/cancelación/escalamiento de DERIVACION no implementados. P01–P03 requerirán nueva migración de la unicidad V7 y exclusión de una sola pendiente; no se habilitaron anticipadamente.

[Reversión compatible con V8](ANTECEDENTES_Y_REAPERTURAS_LAB.md#operación-y-reversión): conservar esquema y hechos, preferir corrección hacia adelante o artefacto de reversión probado. No volver directamente a un JAR V7 que usa INSERT posicionales de vínculos, no borrar tablas ni regenerar comprobantes.

Quedan encendidos público, SIGER, Caddy y MySQL, con listeners de laboratorio limitados a loopback. [Estado final](evidencias/antecedentes-2026-10-20/estado-final.json). No se modificaron DNS, firewall, CA de confianza, CU/URI, VM, SIGER legado ni servicios gubernamentales. No se publica ni se declara habilitación productiva.
