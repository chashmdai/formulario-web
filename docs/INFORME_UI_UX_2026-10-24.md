# Pulido visual y UX del Formulario Público · 24/10/2026

## Resultado y punto de partida

Intervención integral de presentación sobre el checkpoint `e46b905`, en la rama local `pulido-ui-formulario-2026-10-24`. El repositorio estaba limpio antes de crearla. Se conservaron todos los commits anteriores. Implementación: **`512614d`**. No se hizo push.

La autorización del 24/10 permite mejorar el diseño respecto del mockup: las diferencias visuales de esta unidad son deliberadas. Se mantienen los recorridos, datos, condiciones y límites institucionales. No se reconstruyó el formulario ni se modificaron fuentes backend, migraciones o contratos.

## Cambios de presentación y experiencia

| Área | Problema observado antes | Resultado |
|---|---|---|
| Portada | Referencia incorrecta a una demostración que no enviaba; jerarquía y tarjetas poco diferenciadas | Mensaje institucional claro, énfasis ciruela, tarjetas de públicos reconocibles, instrucciones de tres momentos y recordatorio de privacidad |
| Identidad visual | Densidad y jerarquías desiguales | Logo SVG original, Cabin, franja y paleta institucional conservados; superficies, espaciados, tipografía y componentes coherentes |
| Navegación | Progreso lateral desaparecía en móvil | Indicador visible en ambos tamaños, etapa actual anunciada y navegación existente reutilizada; vista por pasos/completa conserva datos |
| Formularios | Tres columnas densas y ayudas pequeñas | Dos columnas legibles en escritorio, una en móvil, etiquetas y ayudas consistentes, controles táctiles y foco visible |
| Identificación | Explicaciones técnicas y botón de revisión con excesiva prioridad | Lenguaje comprensible; disparador automático intacto y revisión manual secundaria; CU sigue bloqueada y RUN declarado sigue sin autenticar |
| Condicionales | Jerarquías confusas, tarjeta de recursos aún sin destino | Paneles y tarjetas relacionados más claros; se retiró solo el recurso sin enlace, manteniendo los canales oficiales y condiciones |
| NNA | Casilla deshabilitada de excepción institucional pendiente | Explicación de ayuda y de los mínimos vigentes, sin ofrecer una excepción que no existe; validación obligatoria conservada |
| Adjuntos | Lista de nombres sin jerarquía y tamaños crudos | Nombre, tamaño legible, estado y progreso; un reemplazo rechazado informa que los archivos anteriores se mantienen |
| Revisión | En móvil, acciones podían quedar fuera del área visible | Cuerpo desplazable y pie de acciones visible, tamaños legibles, error situado en el diálogo; bloqueos de envío ambiguo conservados |
| Validación | Enlaces podían apuntar a campos de pasos ocultos | Se muestra el paso correspondiente antes de enfocar el campo; una identificación incompleta lleva a la sección de datos |
| Salida | Inicio podía abandonar cambios sin orientación | Confirmación al usar Inicio con cambios; seguir completando conserva el recorrido |
| Recepción | Texto técnico y confirmación fuera del área visible después del envío | Panel enfocado al aparecer, referencia copiable, comprobantes y dos estados claramente distintos: recibido y registro pendiente/confirmado |
| Diálogos | Tab podía salir del recorrido de controles | Manejo común de Tab/Mayús+Tab, manteniendo el diálogo nativo y los controles de Escape/ocupado/bloqueado existentes |
| Sesión sin recorrido | La raíz podía mostrar el formulario histórico simplificado después de un callback sin recorrido vigente | Vuelve a la portada institucional; un recorrido CU vigente sigue restaurándose y las rutas históricas explícitas se conservan |
| Redacción | Notas de laboratorio, demostración y aprobación interna | Retiradas de las pantallas ciudadanas verificadas; avisos legales, privacidad y estados reales de cierre/error/registro pendiente permanecen |

El CSS de presentación se concentra en `src/ui/experience.css`; los componentes compartidos están en `src/ui/`. Se reutilizan los activos locales y la paleta documentada en el prototipo institucional. No se sustituyó el logotipo ni se modificó el componente oficial de ClaveÚnica. Esta revisión no constituye una certificación contra un manual de marca completo ni de accesibilidad.

Las transiciones de tarjetas, etapas, progreso y diálogos son breves. `prefers-reduced-motion` suprime animaciones y transiciones; se contempla también `forced-colors`. Modo claro, oscuro y alto contraste comparten variables de presentación. Las preferencias siguen almacenando exclusivamente tema y escala.

## Evidencias antes/después

- [Comparativa navegable, 22 pares](/home/nexbyteproveedor/proyectos/formulario-web/.local/evidencias/pulido-ui/comparativa.html), disponible localmente en [la galería](http://localhost:5175/comparativa.html) mientras su proceso permanezca activo.
- [Portada antes](/home/nexbyteproveedor/proyectos/formulario-web/.local/evidencias/pulido-ui/antes/inicio-escritorio-light.png) y [después](/home/nexbyteproveedor/proyectos/formulario-web/.local/evidencias/pulido-ui/despues/inicio-escritorio-light.png).
- [Revisión móvil antes](/home/nexbyteproveedor/proyectos/formulario-web/.local/evidencias/pulido-ui/antes/consulta-movil-revision-pantalla.png) y [después](/home/nexbyteproveedor/proyectos/formulario-web/.local/evidencias/pulido-ui/despues/consulta-movil-revision-pantalla.png).
- [Portada móvil servida por HTTPS local](/home/nexbyteproveedor/proyectos/formulario-web/.local/evidencias/pulido-ui/https-local-movil.png).

Las capturas se tomaron antes de editar y después del build. Baseline: **76 estados / 152 PNG**. Matriz final compilada: **110 estados / 220 PNG**, a 1440×1000 y 390×844, con comprobación adicional a 320 px. Cada estado incluye imagen de página completa e imagen del área visible. Las evidencias permanecen en `.local/evidencias/pulido-ui/`, fuera de Git. Contienen únicamente datos sintéticos; no son comprobantes de recepciones institucionales reales.

| Grupo de recorridos | Comprobación en escritorio y móvil |
|---|---|
| Acceso y privacidad | Portada en tres temas, selección adulta, acceso CU/manual, privacidad, foco con teclado; aceptación/rechazo y alternativas conservados en regresiones |
| Adulto declarado | Tres pasos, consulta general, orientación, estadísticas, empleo, promoción/organización, vulneración, NNA afectado, adulto relacionado e institución señalada |
| Adulto CU | Identidad de sesión de solo lectura, entrevista, revisión, recepción; cierre de sesión mantiene POST y CSRF |
| Identificación automática | Carga, faltantes completos/parciales, cambio de RUN y restauración sin repetir identificación; restantes estados y concurrencia cubiertos por la batería de identificación |
| NNA | Dos pasos, sin RUN, contacto seguro/inseguro, contacto adicional social/otra red, relato, validación, retorno a identificación incompleta, revisión y recepción; RUN voluntario conservado en regresiones |
| Archivos y envío | Rechazo/recepción de archivo, revisión, fallo de respuesta, reintento, custodia pendiente y registro confirmado mediante doble controlado |
| Accesibilidad y presentación | Claro/oscuro/alto contraste, escala 100–130 %, reflujo a 320 px, vista completa, Tab/Mayús+Tab, Escape y reducción de movimiento |

Los scripts responden **todas** las rutas `/api/**` desde Playwright y cierran cualquier ruta de API no prevista. La autenticación de esta auditoría es un doble de sesión, no un inicio real de CU. Los dobles están exclusivamente en scripts de QA y pruebas; no se agregaron simuladores a la aplicación.

En la matriz compilada se comprobaron **6.171 muestras de contraste de texto**, incluyendo estados repetidos, con cero resultados por debajo del umbral aplicado de 4,5:1 para texto normal y 3:1 para texto grande. Cero desbordes horizontales y cero pies de diálogo fuera de pantalla. Son mediciones automatizadas del contenido renderizado y revisión visual de capturas; no sustituyen una evaluación completa con tecnologías de asistencia o usuarios.

## Regresiones y garantías conservadas

| Verificación | Resultado | Evidencia local |
|---|---|---|
| Vitest | **107 correctas**, 15 archivos | `pulido-ui-front-final.log` |
| ESLint | Correcto, sin advertencias | `pulido-ui-lint.log` |
| TypeScript + Vite | Correcto | `pulido-ui-build.log` |
| Backend H2, sin `Carga*Test` | **169 correctas y 1 omisión opcional**, 170 descubiertas | `pulido-ui-backend-h2.log` |
| Empaquetado Spring Boot | Correcto; mismos fuentes backend | `pulido-ui-package.log` |
| Fixtures V3 | Idénticos entre repositorio público, recursos de test y referencia institucional | Comparación binaria de ambos fixtures V3 |
| MySQL existente | Conteos y CHECKSUM conservados en **12 tablas**, **7 migraciones**, **13 custodias** | `pulido-ui/mysql-sin-escrituras.json` |
| HTTPS local | HTTP 200 y TLS validado, portada sin llamadas API; matriz completa de 110 estados repetida con API controlada | `pulido-ui/https-local.json` y `pulido-ui/https-integrado/auditoria.json` |
| Secretos | Sin coincidencias en los valores privados contrastados | Escáneres locales de RUN/Nodo y configuración integrada |

La matriz completa se repitió sobre el HTTPS oficial local y sus políticas de contenido: **110 estados / 220 capturas adicionales**, 6,171 mediciones de texto sin fallos y sin desbordes. Todas las API siguieron interceptadas.

Los logs de esta unidad se encuentran bajo `.local/evidencias/`. Se conservaron las pruebas de debounce, deduplicación, respuestas tardías, procedencia individual, CU/RUN declarado, mínimos NNA, sesión, CSRF, idempotencia, adjuntos privados y custodia. No se modificaron DTO, llamadas de captura, contratos, firmas ni almacenamiento. Los nuevos tests comprueban navegación, diálogo, comprobante pendiente/confirmado, descarga del objeto firmado sin alterarlo, adjuntos conservados y retorno a portada cuando no hay recorrido vigente.

MySQL se verificó con consultas de solo lectura. No hubo recreación de bases o volúmenes, nuevas migraciones, consultas gubernamentales, autenticación real ni comunicaciones reales durante esta unidad. No se repitieron los ensayos reales de antivirus, CU o PISEE previamente aprobados; sus evidencias permanecen en los informes anteriores. El consumidor SIGER y el hardening preproductivo siguen fuera del alcance de esta intervención.

## Servicio local y reversión

La interfaz está disponible en [el HTTPS local oficial](https://claveunica.formularioadulto.defensorianinez.gob.cl/?vista=inicio). Caddy 443 sigue encaminando al JAR en 127.0.0.1:18443. Se actualizó únicamente el artefacto local con los estáticos compilados, sin modificar configuración privada, endpoints, certificados, banderas ni servicios Docker.

- PID observado al cierre: **75047**, comprobar siempre `app.pid` antes de operar.
- SHA-256 del JAR: `baf036b32c51cf03a15e8b1d6f6c5b7254de803389a808667f6f6d335353faec`.
- Respaldo previo: `.local/evidencias/pulido-ui/rollback-e46b905.jar`, SHA-256 `2e3e644570493d29d71e9ffb8cc99f502d1f8d780fa9cf337822e23b4fcf8f06`.
- Configuración privada existente: `/home/nexbyteproveedor/.local/share/siger/nna-local-20261023/`; no copiarla a Git o informes.

Para revertir solo el servicio local: verificar el hash del respaldo, detener el proceso mediante `scripts/linux/arrancar-nna-local.py <directorio_privado> --permitir-cu-sandbox --detener`, copiar el JAR respaldado sobre `formulario-publico/backend/target/formulario-publico-0.1.0-SNAPSHOT.jar`, y arrancar con el mismo helper y `--permitir-cu-sandbox`, sin `--migrar`. El helper comprueba PID, JAR, configuración y puerto antes de actuar. No se requiere restaurar ni borrar datos. El cambio de código puede revertirse con un commit inverso de `512614d`, conservando la historia.

## Reproducción de QA

En WSL, dentro de `formulario-publico/front`: `npm test -- --run --maxWorkers=2`, `npm run lint`, `npm run build` y `npm run preview -- --host 127.0.0.1 --port 5174`. Backend: `mvn -B -ntp '-Dtest=*Test,!Carga*Test' test`.

Ejecutar los scripts de navegador con Node Windows/Edge, conservando las dependencias existentes. `scripts/qa/pulido-ui.mjs` usa `UI_PHASE=despues` y `UI_ORIGIN=http://localhost:5174`; también admite el HTTPS local con todas las API interceptadas. No sobrescribir la carpeta `antes`: corresponde al estado previo. El script protege una evidencia anterior existente. `generar-comparativa-ui.py` regenera la galería desde las evidencias preservadas; `verificar-ui-https.mjs` comprueba la portada HTTPS contra el asset del build actual sin acceder a API. No ejecutar los scripts históricos de integración DEV para repetir una auditoría visual.

Para servir únicamente las evidencias sintéticas: `python3 -m http.server 5175 --bind 127.0.0.1 --directory /home/nexbyteproveedor/proyectos/formulario-web/.local/evidencias/pulido-ui`. La galería no forma parte del build ciudadano.
