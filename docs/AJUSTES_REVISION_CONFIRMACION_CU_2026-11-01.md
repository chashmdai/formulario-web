# Revisión, confirmación y ClaveÚnica para revisión · 01/11/2026

## Cambios ciudadanos

El modal de revisión de adultos y NNA presenta secciones independientes y accesibles: Identificación, Contacto, Ubicación, Comunicación, Relato y Archivos adjuntos. Conserva personas, grupos de NNA e instituciones y todos los valores existentes de la captura. Los campos no clasificados siguen apareciendo en Comunicación. No cambia los datos enviados ni su procedencia.

Las preferencias de nombre/tratamiento se muestran junto a la identificación, manteniendo su naturaleza declarada. Los datos registrales protegidos siguen sin exponerse. Los textos largos conservan saltos de línea y se leen a ancho completo; las tarjetas separan encabezado, etiqueta y respuesta. El cuerpo se desplaza dentro del modal mientras encabezado y acciones permanecen disponibles. En móvil las filas se apilan; se preservan foco, Escape y bloqueo durante el envío.

La confirmación mantiene el GET existente cada cuatro segundos, sin reenvíos ni solicitudes solapadas. Al llegar el ACK muestra el folio y el comprobante institucional y detiene los sondeos. En estado confirmado ya no aparece «Actualizar estado». Ante un error pendiente, permanece el botón «Reintentar consulta».

«Guarda tus comprobantes» explica que el documento de recepción acredita la recepción pública y el institucional acredita el registro en SIGER. Tras la confirmación solicita anotar y guardar el folio para consultar el estado y dar seguimiento a la gestión.

## ClaveÚnica: flujo existente habilitado

Por instrucción expresa del responsable del proyecto, **se reutiliza la configuración de producción existente** para permitir la revisión de Gobierno Digital desde la QA local. No se incorpora una ruta alternativa, un perfil de diagnóstico ni una excepción al protocolo.

La configuración privada utiliza el Client ID/secret institucionales ya disponibles, `siger.claveunica.environment=PRODUCTION` y `siger.claveunica.callback-approved=true`. El origen y el retorno siguen siendo `https://formulario.defensorianinez.gob.cl`, `/redirect` y `/logout`. Las credenciales no se imprimen, no se incorporan al repositorio y no se envían desde React.

Esta habilitación permite al botón iniciar `/oauth2/authorization/claveunica` y obtener la redirección al autorizador oficial. El campo de configuración habilita el acceso local para revisión; la aceptación de la URI y la certificación corresponden al proveedor. No se simula una identidad ni se presenta el cambio como certificación obtenida.

Permanecen intactas las validaciones OAuth/OIDC existentes: state, nonce, tokens, firmas, issuer, audiencia, sesión, CSRF y callback. La respuesta `loginAvailable=true` indica que se puede iniciar el flujo; `authenticated` solo pasa a verdadero después de una autenticación válida. Los límites entre CU autenticada, declaración e información registral no cambian.

El despliegue intervenido sigue siendo la QA WSL de loopback, no un servidor institucional ni un sitio público. PISEE, Graph, mantenimiento y retiradas conservan sus valores anteriores deshabilitados. El consumidor SIGER asociado permanece destinado exclusivamente a Comunicaciones sintéticas.

## Pruebas ejecutadas

| Prueba local | Resultado |
|---|---|
| Frontend completo | **212 correctas**, 25 archivos |
| Lint, TypeScript y Vite | Correctos |
| Backend OAuth existente | **27 correctas**, sin fallos ni omisiones |
| Maven package | Correcto |
| Comparación de artefactos | **217 clases backend idénticas**, **24 SQL H2/MySQL V1–V12 idénticos**, frontend nuevo coincide con `dist` |
| Confirmación en Edge Windows | **4 variantes correctas**, 12 capturas |
| Modal de adultos/NNA en Edge Windows | **6 variantes correctas**, 12 capturas |

El spinner se verificó con tiempo real, comparando su transformación antes/después de 240 ms: gira en escritorio/móvil/contraste y queda sin animación cuando `prefers-reduced-motion: reduce`. Las pruebas visuales de confirmación esperan el intervalo real, simulan un GET 503 y luego un ACK, verifican folio/comprobante y ausencia de consultas adicionales. Las pruebas del modal comprueban ancho, desplazamiento, encabezado/acciones visibles y foco por teclado.

Estas pruebas visuales usan datos locales artificiales e interceptan las API; no crean ingresos en MySQL ni consultan proveedores. El fixture de revisión está fuera de `dist` y se comprobó que no se incluye en el JAR. La comprobación real de redirección CU se documenta separadamente abajo: no se confunde con una autenticación completada.

### Spinner y revisión mediante MSTSC

Se mantuvo artificialmente la recepción pendiente durante más de cinco segundos, usando exclusivamente fixtures GET locales, en **Edge Windows con una ventana visible y reloj real**. Se compararon transformaciones y PNG pintados; no bastó con leer el nombre CSS de la animación.

| Preferencia en Edge | Antes: 16 px | Ajuste final: 22 px |
|---|---|---|
| Sistema de la sesión examinada (`reduce=false`) | 5 fotogramas distintos, gira | 5 fotogramas distintos, gira |
| Movimiento permitido explícito | 5 fotogramas distintos, gira | 5 fotogramas distintos, gira |
| Movimiento reducido | 1 fotograma idéntico, sin giro | 1 fotograma idéntico, sin giro |

No se encontró una animación detenida o un `keyframes` ausente en la versión previa. Para que el indicador sea más perceptible se amplió a **22 px**, con un trazo de **3 px**, arco contrastado y una vuelta de **1,2 s**. Sus `@keyframes receipt-spin` se definen en la misma hoja del componente. La regla `prefers-reduced-motion: reduce` sigue anulando el giro y conserva texto de espera accesible; el icono decorativo permanece oculto a lectores de pantalla.

La medición corresponde al navegador del servidor. La cadencia visible en el cliente MSTSC también depende de la transmisión y compresión de esa sesión. No se modificaron preferencias de Windows, Edge ni MSTSC para forzar animaciones. Los fotogramas y sus hashes están en `spinner-report.json` y PNG privados fuera del repositorio.

La comprobación es reproducible con `npm run test:spinner-browser`. `RECEIPT_BROWSER_HEADED=1` abre una ventana de prueba; el primer escenario conserva la preferencia real del sistema, y los otros verifican explícitamente ambos modos. No requiere instalar FFmpeg ni escribir datos de ciudadanos.

### Confirmación del flujo OAuth original

No hay diferencias en el backend OAuth respecto del commit publicado anterior. Se conservan:

1. Redirección desde `/oauth2/authorization/claveunica` al autorizador oficial, con state y nonce.
2. Callback `/redirect` validado y de un solo uso.
3. Intercambio de código en el endpoint oficial de tokens.
4. Validación OIDC y consulta `userinfo`; una respuesta inválida no crea identidad autenticada.
5. Sesión ciudadana con identidad verificada, rotación de sesión y controles CSRF.
6. Logout local mediante POST protegido y salida/retorno del proveedor.

Las 27 pruebas existentes incluyen el flujo completo con proveedor **local controlado**, estados incorrectos, callbacks repetidos, tokens inválidos, sesión, CSRF y logout. La única comprobación contra Gobierno Digital fue la navegación de autorización ya documentada; no se completó una autenticación real ni se hizo un nuevo intercambio de tokens con ese proveedor. La aceptación definitiva de la URI sigue pendiente de Gobierno Digital.

Comandos desde `formulario-publico/front`:

```sh
npm test -- --maxWorkers=1
npm run lint
npm run build
npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

En otra consola, con navegador Playwright disponible (Windows usa Edge):

```sh
npm run test:receipt-browser
npm run test:review-browser
```

Los scripts exigen un origen local y aceptan `RECEIPT_BROWSER_URL` y `RECEIPT_BROWSER_OUTPUT`. Detener el servidor temporal después de verificar. Las capturas y logs privados se conservan fuera de Git.

## Actualización QA y continuidad

La actualización usa un JAR versionado inmutable, con respaldo previo del JAR seleccionado, runtime, configuración y manifiesto privados. El consumidor SIGER debe pausarse/drenarse antes de reemplazar únicamente la JVM del Formulario y reanudarse tras verificar HTTPS, recursos, configuración y datos.

Antes del cambio se acreditaron tres Comunicaciones confirmadas (COM-002498 a COM-002500), un intento por Comunicación y cinco originales íntegros (2.192.231 bytes). No se generó una nueva Comunicación para comprobar esta unidad, ni se ejecutaron migraciones o limpiezas.

### Resultado comprobado

- QA final actualizada y encendida: PID 10282; JAR inmutable SHA-256 `b7db05d3fa774c7c3959e7cdae7c80b6a2d3282f34f63cbd2ef4163e1ade38da`.
- Respaldo final privado: `spinner-final-backup-20261101T190006Z`, en la raíz del laboratorio; conserva selección, runtime, configuración y manifiesto. JAR anterior `a29944ae4bdd57c5cce174b2ac43696f32d777924350cb360af41b6ba6621efb` y respaldos anteriores intactos.
- Control del Formulario pausado mediante API auditada, revisión 11, y reabierto en revisión 12: recepción/PULL=true, NORMAL. MySQL, Caddy y ClamAV no se detuvieron.
- HTTPS Windows devuelve 200 con TLS validado; JS de entrada `index-ZpsGwimr.js` y recursos nuevos coinciden por SHA-256 con el build.
- `/api/session` devuelve configuración PRODUCTION, `loginAvailable=true` y `authenticated=false` para una sesión nueva.
- **Redirección real desde Edge Windows:** GET local de autorización devuelve 302 hacia `https://accounts.claveunica.gob.cl/openid/authorize/` con callback correcto, state y nonce. El proveedor devuelve una página HTTP 200 con **«Redirect URI Error»** y el mensaje «La solicitud falló debido a que la URI de redirección no es válida». No es una autenticación exitosa; no se introdujo contraseña, se simuló identidad ni se envió una Comunicación.
- Comparación MySQL final antes/después: mismas cuatro Comunicaciones confirmadas (COM-002498 a COM-002501), cuerpos/ACK sin cambios, un intento por Comunicación y seis originales íntegros (2.243.854 bytes). La nueva recepción humana automática COM-002501 apareció durante la iteración y también fue preservada. Doce migraciones conservadas y cero retiradas.
- La habilitación CU inicial modificó únicamente cuatro parámetros privados existentes: Client ID, secret, ambiente y acceso habilitado. Perfil JDBC, origen, credenciales de integración, claves de firmas, bases y políticas restantes permanecen intactos. El reemplazo final del spinner conserva además toda esa configuración sin cambios.
- El servidor Vite de pruebas quedó detenido. Las instancias históricas permanecen detenidas y sus datos/evidencias se conservan.


Para revertir, coordinar de nuevo pausa/drenaje; cerrar temporalmente recepción/PULL por API operacional auditada; detener únicamente esta JVM y seleccionar el JAR anterior con su configuración respaldada; verificar y restaurar los controles. No sobrescribir un ejecutable activo desde Maven.

Este ajuste no cierra el ciclo de desarrollo ni habilita el traslado a DDN-UBUNTU por sí mismo. Las interfaces siguen abiertas a ajustes y las decisiones de despliegue institucional pertenecen a su unidad correspondiente.
