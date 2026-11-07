# Actualización productiva — formulario adulto y límite antiabuso por ciudadano

07/11/2026, America/Santiago. Misma instalación `/srv/ddn-formulario-produccion` y dominio oficial. Autorizada por el operador; sin migraciones ni cambios de contratos, credenciales, ClaveÚnica, PISEE, Cloudflare Tunnel, certificados, MySQL, custodia ni PULL/ACK.

- Fuente: `main`, commit `c16d135690dce07e1e3a52b12d83fd71fa9650fa` (CI #54 correcta: Linux, Windows y Docker).
- Imagen instalada: `sha256:5dd5c0110f8ba24fc8e0a6bb3558abac2c6bd907bb166f77bdf823fd4b48b819`, construida desde `git archive` del commit. JAR SHA-256 `b4c27ee9d1667ebff48523b38560e81e98a225c5d449d6880d71c2d35b3a6de2`.
- Imagen anterior conservada para reversión: `sha256:6fa0f5d53cc72e98fd9789537bf3e98269826be7e4ea9fbbd40158651ffaba6b`.
- Gates del build: lint, React 237 pruebas, TypeScript/Vite, backend 436 pruebas sin fallos (dos omisiones opt-in).

## Problema corregido

El backend solo veía la dirección del contenedor Caddy (cloudflared → `127.0.0.1:24443` → Caddy → app; `forward-headers-strategy=none`). La cuota de 12 inicios y 12 consultas RUN por hora y origen era compartida por toda la ciudadanía. Evidencia previa: un único origen registrado en 24 horas.

## Solución

- **Caddy** (`Caddyfile.production`): acepta `CF-Connecting-IP` únicamente si el par TCP pertenece a `FORM_TRUSTED_PROXIES`. En producción es `192.168.144.1/32`, puerta de enlace de la red `salida` por la que entra cloudflared desde el anfitrión (verificado empíricamente). Sin variable no confía en ningún par. Sobrescribe siempre la cabecera interna `X-Ddn-Client-Ip`.
- **Backend** (`OrigenCliente`): usa `X-Ddn-Client-Ip` solo desde el servicio `proxy` (`formulario.origen.proxies-confiables=proxy`), con literal IP estricto y valor único; nunca `X-Forwarded-For`. IPv6 se agrupa por /64. Sin configuración conserva la dirección TCP.
- Cuotas sin cambios. El límite de fallos de la contraseña temporal no se modificó (sigue usando su origen observado, más su tope global).
- El borde de Cloudflare rechaza con `error 1000` solicitudes que intentan enviar `CF-Connecting-IP`/`True-Client-IP` propias.

## Pruebas

- Aisladas (revisión sintética, Caddyfile productivo real): ciudadano A 12×200 y 13.º 429; ciudadano B independiente; falsificar `X-Ddn-Client-Ip`/`X-Forwarded-For` no cambia el contador; atacante directo a Caddy y atacante en red interna directo al backend limitados por su propia IP aunque roten cabeceras; IPv6 por /64. Verificación por HMAC: 7 contadores esperados, ninguno para direcciones falsificadas.
- Producción (dominio oficial, una sola solicitud de inicio borrador cancelada, sin comunicaciones): el contador corresponde a la IP pública real del cliente; cabeceras falsificadas y la dirección del proxy sin contador.

## Operación y reversión

CAS cerrado durante la ventana (revisión 24) y restaurado exactamente al estado previo (revisión 23 → 25: recepción y recuperación abiertas, NORMAL). Respaldo cifrado verificado previo: `formulario-20261107T153853Z.gpg`, SHA-256 `33fd8887d7e7a31d0df1a87dec4d14d2973ac09c1ec294a189354fd178c345a3`. Solo se recrearon `app` y `proxy`; los scripts de respaldo/continuidad instalados el 05/11 no se tocaron. Material protegido (47 archivos), contraseña temporal, su caducidad 15/12/2026 00:00 America/Santiago y comunicaciones (5) sin cambios. El reinicio de `app` invalidó sesiones HTTP abiertas.

Antes/después y manifiesto en `updates/antiabuso-20261107/` (privado). Reversión: reponer los `*.before` de esa carpeta, cerrar CAS, recrear solo `app` y `proxy`, reabrir CAS a `control.before.json`; no restaurar base ni volúmenes.

Si se recrea la red `salida` con otra subred, actualizar `FORM_TRUSTED_PROXIES` con su nueva puerta de enlace; mientras no coincida, Caddy no confía en ningún par y vuelve el contador compartido (falla en modo seguro, no falsificable).
