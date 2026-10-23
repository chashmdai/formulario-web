# Callback y certificación ClaveÚnica

La iteración de robustez volvió a completar Sandbox con `userinfo`, identidad bloqueada, recepción durable, comprobante y logout; [evidencia nueva](evidencias/robustez-2026-10-14/sandbox-recepcion-real.json). Esta verificación local conserva el botón, Authorization Code, state, identidad backend, sesión Secure y cierre de sesión del código anterior. No constituye certificación emitida por Gobierno Digital ni habilitación productiva; siguen pendientes publicación autorizada, configuración/secretos de cada ambiente y aceptación institucional/SGD.

**Actualización del 14 de octubre, posterior a la comprobación inicial:** el usuario confirmó como nombre registrado `claveunica.formularioadulto.defensorianinez.gob.cl`. Se implementó un perfil local HTTPS con hosts, preservando `/redirect` y `/logout`, y se completaron dos ingresos reales Sandbox, vista previa y logout. [Evidencia](evidencias/SANDBOX_REAL_2026-10-14.md). Las observaciones `.ddn.gob.cl` siguientes se conservan como antecedentes de la primera iteración. La publicación y certificación institucional siguen pendientes.

## Comprobación del callback — 14 de octubre de 2026

URI informada por la institución para los tres ambientes:

- Redirect: `https://claveunica.formularioadulto.ddn.gob.cl/redirect`
- Logout: `https://claveunica.formularioadulto.ddn.gob.cl/logout`

`Resolve-DnsName` local y con `-Server 1.1.1.1` y `-Server 8.8.8.8` respondieron «El nombre DNS no existe». GET a `/`, `/redirect` y `/logout` no pudo conectar: host desconocido. No hay evidencia de que el dominio atienda SIGER ni una aplicación existente. La ausencia de DNS público **no demuestra** que el dominio no tenga propietario, configuración interna o uso institucional.

No se cambiaron DNS, servicios, proxy, certificados ni la aplicación institucional. No se inició autorización real, porque el código retornaría a un destino no atendible. No se usaron las credenciales QA/productivas. El TXT institucional solo se lee por el script Sandbox y queda fuera del repositorio.

## Gestión concreta pendiente

La Defensoría debe confirmar responsable y uso actual del subdominio, registro efectivo de URIs y autorización para atenderlas. Elegir una de estas vías:

1. Habilitar un host Sandbox dedicado, HTTPS/TLS >= 1.2, y registrar su `/redirect` y `/logout`. Informar el origen público y encaminar esas rutas **al mismo backend que inició la sesión**. Mantener cookies/origen y sesión; un simple redirect del callback a localhost perdería la vinculación.
2. Para prueba local, solicitar al contacto administrativo registrado, mediante [Actualización de URIs en Cerofilas](https://gobdigital.cerofilas.gob.cl/), **solo para el client_id Sandbox**, el cambio de Redirect URI a `http://localhost:5173/redirect` y Logout URI a `http://localhost:5173/logout`. La versión 6.5 elimina la restricción localhost, pero la aceptación concreta del esquema HTTP debe confirmarse; preferir HTTPS local si Gobierno Digital lo exige. Nombre/siglas de institución en dominio, IPs prohibidas y restricciones de URI también deben ser validados por la institución. No ejecutar el flujo hasta confirmación del registro efectivo. Alternativa HTTPS local: registrar el origen y configurar certificado confiable y proxy HTTPS.

No cambiar las URIs QA/productivas ni reutilizar subdominios activos sin autorización. La gestión la realiza el contacto administrativo de credenciales, con su cuenta institucional; no se envió solicitud en nombre del usuario.

Después de confirmar exactamente las URIs registradas para Sandbox:

```powershell
# Ejemplo condicionado a aceptación y registro previo; no ejecutado contra CU.
.\scripts\Start-Backend.ps1 -RedirectUri 'http://localhost:5173/redirect' `
  -LogoutUri 'http://localhost:5173/logout' -CallbackApproved
```

## Recorrido real por ejecutar

Usar solo los RUN oficiales del manual: `44.444.444-4`, `55.555.555-5`, `88.888.888-8`, `99.999.999-9`; contraseña de prueba publicada en el manual: `testing`. Ingresarlos exclusivamente en Gobierno Digital. Comprobar pantalla completa, state cambiante, retorno al backend, POST token, POST userinfo e identidad visible. Cerrar sesión y volver a iniciar comprobando que CU solicita autenticación. No guardar HAR completos, códigos, tokens, secretos ni cookies como evidencia. Usar capturas de código y resultados redactados.

## Matriz del manual §4.1

| Requisito | Implementación / evidencia disponible | Pendiente para certificación |
|---|---|---|
| Botón oficial | SVG original, Roboto local, tamaño 24px, alto mínimo 48px, padding 8/14px, separación 4px, colores y foco oficiales. | Revisión de UI final por Gobierno Digital. |
| HTTPS aplicación | Cookie Secure por defecto; guardas de HTTPS/.gob.cl en producción; redirección HTTPS; HSTS y CSP. | Host productivo, certificado, TLS >=1.2 y configuración de proxy confiable. |
| Formulario a pantalla completa | Enlace en misma ventana a ruta backend que redirige al authorize oficial. | Evidencia de navegación real. |
| State dinámico | Generación criptográfica y almacenamiento en sesión por Spring; validación antes del token; pruebas de state falso/sin sesión/replay. | Evidencia en Sandbox accesible. |
| Secuencia/scopes/endpoints | Authorization Code, `openid run name`, URLs `accounts.claveunica.gob.cl`, firma/claims ID token y POST userinfo. | Recorrido real completo; confirmar comportamiento de issuer, RS256 y nonce del Sandbox. |
| Token y userinfo desde backend | `SecurityConfiguration.tokenClient()` y `ClaveUnicaOidcUserService`, pruebas HTTP de cuerpo token y POST Bearer userinfo. | Capturas de estos bloques con URLs para expediente institucional. |
| Credenciales ocultas | Variables externas, plantillas vacías, lector Sandbox, sin secretos en frontend/repositorio/logs. | Evidencia de configuración productiva redactada y gestión institucional de acceso. |
| Logout | POST local CSRF, invalidación, GET oficial en ventana principal, `/logout`, limpieza tras fallo y expiración detectada. | Comprobar cierre CU y nuevo login con sesión real. |

Estos estados describen cobertura del código; **no equivalen a certificación concedida**. El manual exige una aplicación productiva completa y accesible para revisión, o coordinación de videollamada cuando no sea accesible, con contacto administrativo presente. La Comunicación definitiva aún está fuera de esta iteración; esta interfaz mínima no demuestra la finalización del futuro SIGER ni garantiza que se acepte como sitio completo a certificar.

Producción pendiente: URIs/origen dedicados y habilitados, separación operativa Sandbox/QA/Producción, TLS, secreto del ambiente aprovisionado de forma segura, despliegue autorizado, duración de sesión acordada, ejecución real Sandbox y QA, evidencia visual redactada, solicitud institucional de certificación y activación de credenciales. Cambiar ambiente no requiere cambiar lógica Java/React; cambia configuración externa y habilitación institucional.
