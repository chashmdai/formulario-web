# Estado actualizado de paridad — 24 de octubre de 2026

La matriz siguiente actualiza el estado vigente; las secciones posteriores se conservan como evidencia histórica de la migración. Detalles y límites en [CIERRE_ADULTOS_2026-10-24.md](CIERRE_ADULTOS_2026-10-24.md).

| Recorrido/estado | Escritorio 1440×900 | Móvil 390×844 | Evidencia y límite |
|---|---|---|---|
| Portada y aviso adulto | Componentes originales reutilizados; aceptación persistida antes del recorrido | Mismo gate y alternativas | Pruebas frontend; paridad inicial histórica conservada |
| Adulto declarado, etapas 1/2/3 | RUN/DV, faltantes, consultas, promoción y vulneración | Mismos tres recorridos | Seis recepciones reales, capturas por pasos/completo/contraste/revisión/custodia/recarga |
| Consulta/entrevista/estadísticas/empleo | Orientación, perfil otro obligatorio, confirmación de fuentes, canal externo de empleo | Mismas condiciones | Preflight navegador real con recepción cerrada; todos los perfiles cubiertos sintéticamente |
| NNA afectados, adultos relacionados, señalados e instituciones | Tarjetas, campos condicionales, datos desconocidos y retiro confirmado | Mismo recorrido complejo | Backend/React y navegador de vulneración; custodia excluye la tarjeta retirada |
| Adulto CU | Identidad de sesión bloqueada y revisión completa | Revisión y envío real | Mismo recorrido Sandbox; una custodia, no dos envíos; logout real separado |
| Revisión/envío | Solo declaraciones propias y CU; datos SRCeI privados | Mismos controles | Custodia MySQL real; ningún folio simulado; ACK institucional real pendiente |
| Adjuntos rechazados/limpios | PDF inválido bloquea; TXT limpio inspeccionado por ClamAV | Mismo comportamiento | Descarga ajena 404; integridad verificada en MySQL |
| Claro/oscuro/contraste/130% | Sin desborde en estados capturados | Sin desborde en estados capturados | Presentación final con API controlada separada de los envíos reales |
| NNA completo | Verificado en `08f5031` | Verificado en `08f5031` | Regresiones actuales incluidas; no se rehízo su infraestructura |

Se cotejaron las tres etapas del HTML y React. Posición/ancho de primera tarjeta coinciden en las capturas reales de recepción: x348/y421.96875/ancho1028 escritorio; x14/y510.75/ancho362 móvil; fuente Cabin 16px. Se corrigieron título/caption de identificación manual, header, casillas RUN/DV y posición del estado, con inspección visual final explícitamente controlada. Se conserva la estructura y el CSS originales; cambian textos de simulación, tratamiento seguro de datos y recepción real. No se afirma igualdad total de píxeles ni certificación integral de accesibilidad.

---

## Historial de paridad anterior

# Paridad visual y funcional del mockup

Referencia congelada: `ddn-formulario` commit `13f9cf6`. La migración está **PARCIAL**. La entrada principal adulta y NNA conservan su comportamiento anterior; la nueva pantalla se revisa con `/?vista=inicio`. No se declara réplica integral.

| Pantalla/estado | Mockup | React | Diferencia | Corregido/Pendiente |
|---|---|---|---|---|
| Inicio 1440×900 | Cabecera, logo, Cabin, tarjetas, decoración y pie | Componentes con SVG/CSS originales, Cabin local | PNG completos 1440×1079 idénticos por SHA-256 en primera comparación | CORREGIDO en estado inicial claro/100% |
| Inicio 390×844 | Cabecera apilada y tarjetas verticales | Mismos elementos y cotas | PNG completos 390×1256 idénticos por SHA-256 en primera comparación | CORREGIDO en estado inicial claro/100% |
| Diálogo adulto escritorio | CU simulada; identidad inventada | Enlace OIDC al backend real | Textos de simulación sustituidos por descripción real; botón SVG oficial. Diferencia >16/255 en 3,4426% de píxeles de página | PARCIAL, diferencia intencional y ajustes pendientes |
| Diálogo adulto móvil | Misma simulación | CU real/entrada manual cerrada | Diferencia >16/255 en 8,5528% de píxeles | PARCIAL |
| Apariencia/escala | Claro, oscuro, contraste; 100–130%; preferencias locales | Controles React acotados, solo preferencias no personales en localStorage | Prueba de interacción pasa; capturas de todos los temas pendientes | PARCIAL |
| Aviso privacidad 1440×900 / 390×844 | Aceptación/rechazo, alternativas, reelección y vuelta al inicio | Componente React con texto y estructura originales; gate anterior al enlace CU | Texto visible, rectángulo y foco idénticos en aviso y alternativas. JPEG móvil y alternativas escritorio: 0% píxeles con diferencia >16; aviso escritorio: 0,00463% | CORREGIDO en estos estados; elección del canal solo en memoria, registro servidor pendiente |
| Adulto CU: tres pasos | Identificación, motivo y antecedentes | Identidad/mensaje del baseline | Amplia diferencia de campos y navegación | PENDIENTE |
| Adulto sin CU | RUN/DV y fallback registral/manual | Entrada aún no disponible | El botón muestra indisponibilidad, sin inventar una identidad | PENDIENTE |
| NNA: dos pasos | Identificación flexible, relato/contacto seguro | Nuevos componentes `NnaFlow`, `NnaStepOne/Two`, `GeoFields` con catálogo del mockup y controles persistentes | Captura por pasos/completa y condicionales probadas en componentes. Aún sin montar desde portada ni conectar recepción/almacén de adjuntos; comparación visual pendiente | IMPLEMENTADO_NO_VERIFICADO visual; envío completo PENDIENTE |
| Personas/condicionales | Múltiples NNA, adultos e instituciones | No migrados | No eliminados de la referencia ni reinterpretados | PENDIENTE |
| Revisión/envío/comprobante | Envía y folia de forma simulada | Custodia/ACK V3 existentes | Integración con formulario completo pendiente | PENDIENTE |

Comparación ejecutada con Edge headless, viewport 1440×900 y 390×844, DPR 1, fuentes cargadas y contextos nuevos. Capturas de página completa para ambos estados y tamaños; inspección visual y comparación de geometría. Se conservaron ocho PNG y JSON de geometría en `.local/evidencias/paridad`, fuera de Git. Se repitió contra React empaquetado en el JAR Docker (`http://localhost:18080/?vista=inicio`), nuevamente con PNG iniciales idénticos y sin errores JavaScript; evidencia separada en `.local/evidencias/paridad-jar`. Las equivalencias iniciales son evidencia de esas capturas, no de todos los flujos ni navegadores.

Reproducir: iniciar HTML original en `127.0.0.1:8181`, Vite en `localhost:5173`, instalar Chromium de Playwright para QA y ejecutar `node scripts/qa/comparar-inicio.mjs`. `MOCKUP_URL`, `REACT_URL` y `BROWSER_CHANNEL=msedge` permiten usar otros servidores o Edge instalado. No prueba autenticación ni llama a SRCeI.

La segunda unidad añade `PrivacyDialog`, replicando el aviso adulto y sus canales alternativos. Comparación en navegador integrado con viewport verificado en DOM y capturas JPEG con encuadre explícito ajustado por scroll, en ambos tamaños. Se corrigieron espacios JSX perdidos junto a `strong` y el enlace legal. Ocho capturas y `privacidad-geometria.json` están en la carpeta privada de visualizaciones de esta sesión. No se afirma identidad binaria de JPEG ni cobertura de todos los temas. El script QA incluye aviso, alternativas y reelección para reproducirlos. Pruebas frontend: 17 pasan; lint, TypeScript y build pasan. El rechazo no inicia CU ni consulta SRCeI; la reelección exige seleccionar nuevamente y no se almacena la decisión en localStorage. Aceptar en ruta CU inicia la autorización real; la entrada manual sigue mostrando indisponibilidad expresa mientras se implementa su backend.

No se importan scripts del HTML, iframes, fixtures de identidad ni contadores de folio. Componentes: `InstitutionalHeader`, `AudienceSelection`, `AdultAccess`, `PrivacyDialog`, `InstitutionalFooter`, `StartPage`. El CSS mantiene la fuente original para continuar la migración sin rediseño. Solo Cabin se añadió como dependencia visual; Playwright es una herramienta de QA.

## Continuación NNA

Se trasladaron las secciones estáticas del HTML a `NnaStepOne`/`NnaStepTwo`, con estado/validación separados en `NnaFlow`/`nnaRules`. Se conservaron preguntas, opciones, clases y jerarquía; los controles de simulación se retiraron. El catálogo `territorio.ts` procede del commit de referencia, sin presentarlo como actualización territorial oficial. Se verificaron región/comuna dependiente, contacto primario/alternativo/social/otra red, ausencia de contacto seguro, exclusión de datos de canales inactivos, relato y validación de selección de archivos.

Discrepancia institucional resuelta: la excepción del HTML que permite omitir nombre/apellido estaba expresamente pendiente; se conserva como opción deshabilitada con explicación y se mantienen primer nombre, primer apellido y región. Los atributos registrales siguen ocultos, incluso donde el mockup CU mostraba fecha/sexo simulados. Son cambios por reglas confirmadas, no un rediseño.

37 pruebas frontend correctas, lint y TypeScript/Vite correctos. El puerto de adjuntos de `NnaFlow` es una dependencia obligatoria para conectar almacenamiento real; sus dobles aparecen solo en tests. **No se declara implementado el almacenamiento ni envío/comprobante por existir ese puerto.** La portada y los recorridos previos no se sustituyeron por una pantalla incompleta. Capturas y validación integrada quedan para el montaje con la recepción completa.
