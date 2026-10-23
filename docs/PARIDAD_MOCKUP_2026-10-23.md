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
| NNA: dos pasos | Identificación flexible, relato/contacto seguro | Baseline nombres/región/relato | Preguntas/contacto/adjuntos aún sin migrar | PENDIENTE |
| Personas/condicionales | Múltiples NNA, adultos e instituciones | No migrados | No eliminados de la referencia ni reinterpretados | PENDIENTE |
| Revisión/envío/comprobante | Envía y folia de forma simulada | Custodia/ACK V3 existentes | Integración con formulario completo pendiente | PENDIENTE |

Comparación ejecutada con Edge headless, viewport 1440×900 y 390×844, DPR 1, fuentes cargadas y contextos nuevos. Capturas de página completa para ambos estados y tamaños; inspección visual y comparación de geometría. Se conservaron ocho PNG y JSON de geometría en `.local/evidencias/paridad`, fuera de Git. Se repitió contra React empaquetado en el JAR Docker (`http://localhost:18080/?vista=inicio`), nuevamente con PNG iniciales idénticos y sin errores JavaScript; evidencia separada en `.local/evidencias/paridad-jar`. Las equivalencias iniciales son evidencia de esas capturas, no de todos los flujos ni navegadores.

Reproducir: iniciar HTML original en `127.0.0.1:8181`, Vite en `localhost:5173`, instalar Chromium de Playwright para QA y ejecutar `node scripts/qa/comparar-inicio.mjs`. `MOCKUP_URL`, `REACT_URL` y `BROWSER_CHANNEL=msedge` permiten usar otros servidores o Edge instalado. No prueba autenticación ni llama a SRCeI.

La segunda unidad añade `PrivacyDialog`, replicando el aviso adulto y sus canales alternativos. Comparación en navegador integrado con viewport verificado en DOM y capturas JPEG con encuadre explícito ajustado por scroll, en ambos tamaños. Se corrigieron espacios JSX perdidos junto a `strong` y el enlace legal. Ocho capturas y `privacidad-geometria.json` están en la carpeta privada de visualizaciones de esta sesión. No se afirma identidad binaria de JPEG ni cobertura de todos los temas. El script QA incluye aviso, alternativas y reelección para reproducirlos. Pruebas frontend: 17 pasan; lint, TypeScript y build pasan. El rechazo no inicia CU ni consulta SRCeI; la reelección exige seleccionar nuevamente y no se almacena la decisión en localStorage. Aceptar en ruta CU inicia la autorización real; la entrada manual sigue mostrando indisponibilidad expresa mientras se implementa su backend.

No se importan scripts del HTML, iframes, fixtures de identidad ni contadores de folio. Componentes: `InstitutionalHeader`, `AudienceSelection`, `AdultAccess`, `PrivacyDialog`, `InstitutionalFooter`, `StartPage`. El CSS mantiene la fuente original para continuar la migración sin rediseño. Solo Cabin se añadió como dependencia visual; Playwright es una herramienta de QA.
