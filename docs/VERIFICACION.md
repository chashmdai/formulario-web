# Verificación de esta iteración

14 de octubre de 2026, Windows 11 x64, Java 25.0.3/Maven 3.9.15/Node 24.15.0/npm 11.12.1.

**Actualización posterior, mismo día:** Sandbox real verificado a través de HTTPS y hosts locales para el nombre confirmado por el usuario `claveunica.formularioadulto.defensorianinez.gob.cl`. [Registro y capturas](evidencias/SANDBOX_REAL_2026-10-14.md). La tabla siguiente conserva las comprobaciones de la iteración inicial; su bloqueo DNS fue resuelto para este equipo mediante el perfil local, sin publicación institucional.

| Comprobación | Resultado efectivo |
|---|---|
| `scripts/Build-Siger.ps1` | npm ci, lint, Vitest, TypeScript/Vite y Maven clean verify ejecutados; JAR con React construido. |
| Backend automatizado | 18 pruebas exitosas. Flujo OIDC por HTTP contra servidor de pruebas exclusivo: POST token con state/secreto/URI; POST userinfo Bearer; nombres estructurados y RUN independiente de sub. State aleatorio, falsificado, sin sesión y replay; firma, issuer, audience, expiración y nonce inválidos; errores token/userinfo; identidad incompleta; rotación y minimización de sesión; callback falso no cierra sesión activa; CSRF real de formulario; logout local/proveedor; guardas Sandbox/QA/Producción y URIs; HS256 configurable validado. |
| Frontend automatizado | 3 pruebas: botón en la misma ventana/sin contraseña; datos de identidad de solo lectura, mensaje aislado y logout POST/CSRF; fallo de conexión sin inventar identidad. |
| Auditoría npm | `npm install`/`npm ci` auditó el árbol: 0 vulnerabilidades; `npm audit --omit=dev` también 0. No se ejecutó auditoría CVE del árbol Maven; queda como control antes de publicación. |
| JAR local | `Start-Backend.ps1 -Packaged`: arranque real con par Sandbox leído desde TXT externo, callback deshabilitado. Página y SVG HTTP 200. Sesión anónima sin identidad; CSRF emitido; cookie HttpOnly/SameSite=Lax (HTTP local deliberado, Secure false). Inicio 503 por bloqueo; POST sin CSRF 403; POST anónimo con CSRF válido 401. Vite proxy `/api/session` 200. [Resultado redactado](evidencias/smoke-local.json). |
| Navegador | UI empaquetada verificada; sin errores/warnings de consola. Botón 48px, SVG cargado 24×24, Roboto 700, gap 4px, padding 8/14px y #0F69C4; sin target popup. Mobile a 390px sin desborde horizontal. [Escritorio](evidencias/inicio-local.jpg), [móvil](evidencias/inicio-mobile.jpg). |
| Secretos | Comparación en memoria de los valores institucionales contra archivos fuente: ninguna coincidencia. No se imprimieron los valores. `.gitignore` excluye entorno real, claves, logs y artefactos de build. |
| Proveedor real | Discovery oficial consultado; GET de las dos variantes documentadas logout sin sesión respondió 204. Esto no comprueba cierre de una sesión CU. |
| Sandbox E2E | **NO ejecutado**: callback registrado no resuelve por DNS local/1.1.1.1/8.8.8.8. No se completó authorize → callback → token → userinfo contra ClaveÚnica real. No hay identidad institucional real verificada en la evidencia. |

Capturas y pruebas locales no son evidencias de autenticación real ni una certificación. Infraestructura institucional, QA/Producción, MySQL/SSH y PISEE no fueron modificados.
# Verificación posterior a la separación

Consultar [ejecución de la refactorización](evidencias/REFACTOR_APLICACIONES_2026-10-14.md): 24 pruebas del backend público, 5 institucionales, 5 React y recorrido Sandbox real seguido de recuperación/confirmación local. Las notas anteriores corresponden a la iteración inicial.

