# Paridad visual y funcional del mockup

Referencia congelada: `ddn-formulario` commit `13f9cf6`. La migración está **PARCIAL**. La entrada principal adulta y NNA conservan su comportamiento anterior; la nueva pantalla se revisa con `/?vista=inicio`. No se declara réplica integral.

| Pantalla/estado | Mockup | React | Diferencia | Corregido/Pendiente |
|---|---|---|---|---|
| Inicio 1440×900 | Cabecera, logo, Cabin, tarjetas, decoración y pie | Componentes con SVG/CSS originales, Cabin local | PNG completos 1440×1079 idénticos por SHA-256 en primera comparación | CORREGIDO en estado inicial claro/100% |
| Inicio 390×844 | Cabecera apilada y tarjetas verticales | Mismos elementos y cotas | PNG completos 390×1256 idénticos por SHA-256 en primera comparación | CORREGIDO en estado inicial claro/100% |
| Diálogo adulto escritorio | CU simulada; identidad inventada | Enlace OIDC al backend real | Textos de simulación sustituidos por descripción real; botón SVG oficial. Diferencia >16/255 en 3,4426% de píxeles de página | PARCIAL, diferencia intencional y ajustes pendientes |
| Diálogo adulto móvil | Misma simulación | CU real/entrada manual cerrada | Diferencia >16/255 en 8,5528% de píxeles | PARCIAL |
| Apariencia/escala | Claro, oscuro, contraste; 100–130%; preferencias locales | Controles React acotados, solo preferencias no personales en localStorage | Prueba de interacción pasa; capturas de todos los temas pendientes | PARCIAL |
| Aviso privacidad | Aceptación/rechazo y alternativas | Todavía no migrado | No se debe confundir el diálogo de acceso con aceptación del aviso | PENDIENTE |
| Adulto CU: tres pasos | Identificación, motivo y antecedentes | Identidad/mensaje del baseline | Amplia diferencia de campos y navegación | PENDIENTE |
| Adulto sin CU | RUN/DV y fallback registral/manual | Entrada aún no disponible | El botón muestra indisponibilidad, sin inventar una identidad | PENDIENTE |
| NNA: dos pasos | Identificación flexible, relato/contacto seguro | Baseline nombres/región/relato | Preguntas/contacto/adjuntos aún sin migrar | PENDIENTE |
| Personas/condicionales | Múltiples NNA, adultos e instituciones | No migrados | No eliminados de la referencia ni reinterpretados | PENDIENTE |
| Revisión/envío/comprobante | Envía y folia de forma simulada | Custodia/ACK V3 existentes | Integración con formulario completo pendiente | PENDIENTE |

Comparación ejecutada con Edge headless, viewport 1440×900 y 390×844, DPR 1, fuentes cargadas y contextos nuevos. Capturas de página completa para ambos estados y tamaños; inspección visual y comparación de geometría. Se conservaron ocho PNG y JSON de geometría en `.local/evidencias/paridad`, fuera de Git. Se repitió contra React empaquetado en el JAR Docker (`http://localhost:18080/?vista=inicio`), nuevamente con PNG iniciales idénticos y sin errores JavaScript; evidencia separada en `.local/evidencias/paridad-jar`. Las equivalencias iniciales son evidencia de esas capturas, no de todos los flujos ni navegadores.

Reproducir: iniciar HTML original en `127.0.0.1:8181`, Vite en `localhost:5173`, instalar Chromium de Playwright para QA y ejecutar `node scripts/qa/comparar-inicio.mjs`. `MOCKUP_URL`, `REACT_URL` y `BROWSER_CHANNEL=msedge` permiten usar otros servidores o Edge instalado. No prueba autenticación ni llama a SRCeI.

No se importan scripts del HTML, iframes, fixtures de identidad ni contadores de folio. Componentes: `InstitutionalHeader`, `AudienceSelection`, `AdultAccess`, `InstitutionalFooter`, `StartPage`. El CSS mantiene la fuente original para continuar la migración sin rediseño. Solo Cabin se añadió como dependencia visual; Playwright es una herramienta de QA.
