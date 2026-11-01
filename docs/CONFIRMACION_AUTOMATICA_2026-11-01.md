# Confirmación ciudadana automática · 01/11/2026

## Alcance

Mejora de presentación y consulta de estado para adultos con/sin ClaveÚnica y NNA, después de acreditar en laboratorio el ingreso automático COM-002499. El backend, las clases Java, los contratos 1.0/1.1, las claves y las migraciones V1–V12 permanecen sin cambios. No es una habilitación productiva ni un cierre del desarrollo visual.

## Comportamiento

- Al disponer de un comprobante público pendiente, la pantalla consulta únicamente `GET /api/formulario/comunicaciones/{id}`, con la misma sesión y sin caché.
- Espera cuatro segundos entre una consulta terminada y la siguiente. No consulta mientras otro envío o consulta esté en curso. StrictMode no duplica temporizadores.
- Una petición sin respuesta se aborta a los doce segundos y permite el siguiente intento. Un fallo de consulta no provoca POST ni altera la clave idempotente.
- Muestra un indicador discreto y explica que el folio aparecerá automáticamente. Pasado un minuto, explica la demora sin declarar que la recepción falló.
- Conserva el comprobante de recepción descargable durante consultas, demoras y errores. El mensaje de error no invita a reenviar una Comunicación ya recibida.
- Con `REGISTRADA_SIGER` y su registro institucional, muestra el folio y habilita el comprobante institucional; se detienen los sondeos.
- Al salir de la pantalla se cancelan los temporizadores. Una petición GET ya iniciada puede terminar, o abortarse por su límite, pero no genera otro sondeo.
- Se mantiene «Actualizar estado» como alternativa manual. Su uso no es necesario para recibir el folio.
- No guarda borradores, cookies ni contenido ciudadano en almacenamiento nuevo.

La consulta sigue requiriendo la sesión propietaria. No se introdujo un mecanismo para recuperar una sesión perdida, ni acceso público por conocer un UUID. Una sesión cerrada puede impedir actualizar el estado, aunque la Comunicación y su ACK permanezcan íntegros en sus sistemas de custodia.

## Archivos

- `formulario-publico/front/src/ui/ReceiptPanel.tsx`: espera, temporizadores, indicador y confirmación.
- `formulario-publico/front/src/formulario/AdultPage.tsx` y `NnaPage.tsx`: reutilización del GET existente, exclusión de operaciones concurrentes y mensajes compatibles con el comprobante ya recibido.
- `formulario-publico/front/src/formulario/submission.ts`: timeout exclusivo de la consulta de estado.
- `formulario-publico/front/src/ui/experience.css`: disposición y estilos de espera, con colores institucionales. El spinner reutiliza las reglas existentes de reducción de movimiento.

## Verificación ejecutada

| Verificación local | Resultado |
|---|---|
| Frontend completo, un trabajador | **209 pruebas correctas**, 24 archivos, sin errores no manejados |
| Lint | Correcto |
| TypeScript y Vite build | Correcto |
| Maven package con frontend nuevo | Correcto; pruebas backend omitidas explícitamente en este empaquetado |
| Comparación de JAR anterior/nuevo | **217/217 clases backend idénticas** y **24/24 archivos SQL H2/MySQL V1–V12 idénticos**; recursos frontend coinciden con `dist` |
| Navegador nativo Edge Windows | **4 variantes correctas**, 12 capturas: escritorio/móvil, claro/oscuro/alto contraste y movimiento reducido |
| Casos de navegador | Pendiente → error GET503 → confirmación automática; folio/comprobante; cero POST y cese de sondeo tras ACK |

Las nueve pruebas nuevas cubren cuatro segundos, StrictMode, exclusión durante una petición lenta, ACK, demora, errores, descarga pública, desmontaje, timeout y recorridos adultos/NNA restaurados. Los fallos encontrados en la primera ejecución (importación de `act` y compatibilidad del callback de una prueba existente) se corrigieron antes de la batería final.

Las pruebas de navegador interceptan **todos** los endpoints API con dobles locales. No generan Comunicaciones en MySQL, no usan cookies del ciudadano ni contactan proveedores. No sustituyen una nueva prueba E2E institucional. El resultado de CI remota debe consultarse para el commit publicado; no se confunde con estas ejecuciones locales.

### Comandos reproducibles

Desde `formulario-publico/front`, con Node y dependencias ya instalados:

```sh
npm test -- --maxWorkers=1
npm run lint
npm run build
npm run preview -- --host 127.0.0.1 --port 5173 --strictPort
```

En otra consola, con navegador Playwright disponible (en Windows usa Edge instalado):

```sh
npm run test:receipt-browser
```

La comprobación exige origen local. `RECEIPT_BROWSER_URL` permite indicar otro puerto local y `RECEIPT_BROWSER_OUTPUT` el directorio de capturas. Por defecto utiliza el directorio temporal del sistema. Cerrar el preview al terminar; no constituye otro despliegue permanente.

## Laboratorio y continuidad

QA utilizada: origen local `https://formulario.defensorianinez.gob.cl`, aplicación 18443, MySQL 13306, antivirus 13310 y Caddy 443. Consumidor automático en SIGER Windows, independiente de los sondeos del navegador. No se cambia el origen ni se modifica SIGER desde este repositorio.

Antes de la intervención se comprobaron dos Comunicaciones confirmadas (COM-002498/COM-002499), cuatro originales y ninguna entrega pendiente o reservada. Las instancias históricas 18080/19443 y los contenedores de QA anteriores ya estaban detenidos; se conservaron sus volúmenes y evidencias. MySQL de la QA actual, Caddy y clamd son necesarios y no se detienen. El preview visual temporal también quedó detenido.

La sustitución de la JVM fue autorizada expresamente por el humano, con aviso de posible pérdida de la sesión del comprobante. Se coordinó pausa/drenaje del consumidor SIGER y control auditado del Formulario, revisión 7 cerrado → revisión 8 aceptarNuevos=true/recuperar=true/NORMAL.

- JAR nuevo inmutable: SHA-256 `40c02f3c0ec6e6cdf2b6c9a5f0cecd888318f630b7fed4de635d20e9f0c4cef3`.
- JAR anterior conservado: SHA-256 `11038950760953e166ed49d3bb15d9900a147ee84f4b0e80263b3de0773d41df`.
- Runtime actualizado: PID 5376, misma configuración privada y mismo volumen. Las rutas detalladas están en el checkpoint privado del laboratorio.
- HTTPS desde Windows: 200, validación TLS correcta. JS de entrada `index-CPJuX1xF.js`, módulo `submission-DJOa-pIq.js` y CSS `experience-ZpNdI_1n.css` servidos con SHA-256 idénticos a la compilación nueva.
- Verificación MySQL antes/después: cuerpos y ACK de ambas Comunicaciones sin cambios, un intento por Comunicación, cuatro BLOB íntegros (1.876.701 bytes), cero retiradas. Flyway conserva sus doce migraciones; no se ejecutó ninguna nueva.
- Respaldo privado de selección/runtime/configuración: `receipt-update-backup-20261101T144818Z` en la raíz de esta QA, fuera de Git.

El responsable SIGER confirmó la reanudación auditada de su consumidor (revisión 7), sondeos HTTPS posteriores correctos, estado ESPERANDO_ENTREGAS, ningún fallo ni ACK pendiente y la misma JVM institucional. Es evidencia coordinada de continuidad; no se creó otro ingreso para probar esta mejora.

Reversión: pausar y drenar primero el consumidor institucional; cerrar los controles públicos mediante la API operacional auditada; con el lanzador `scripts/linux/jar_laboratorio.py` detener únicamente esta JVM, seleccionar el SHA anterior y arrancar con la configuración y opciones originales. Verificar HTTPS y hashes antes de restaurar los controles previos y reanudar SIGER. No copiar desde `target/` sobre un JAR activo ni modificar claves o datos.

Retirada, mantenimiento online, modalidad alternativa de 72 h, emisión de garantías y proveedores reales permanecen deshabilitados. El entorno es exclusivamente de laboratorio; se conserva encendido para continuar ajustes y pruebas manuales antes del traslado a DDN-UBUNTU.
