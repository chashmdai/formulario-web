# Certificación ClaveÚnica · DDN-UBUNTU · 03/11/2026

**Resultado: versión de certificación operativa localmente; recepción ciudadana bloqueada.** No se habilitó Cloudflare/DNS, no se autenticó una persona en producción y no se obtuvo aprobación oficial de certificación. Fecha y operación: America/Santiago.

## Fuente e imagen

Repositorio `chashmdai/formulario-web`, checkout `/home/admin-ti/formulario-ubuntu-h1-20261102`.

- `main`: fast-forward publicado desde `bfb950690e85381b69e6dcfac167f0a237c80071` hasta `7189a95cd449d1a5c6117116f1a360b4fb6b9f63`, sin force-push.
- Rama publicada: `deploy/certificacion-claveunica-2026-11-03`.
- Implementación: `edc90515cf1626ba42a2792dd3a660933900bd0a`.
- Entrega del informe y verificador: `2fcc199625822c9fc3fd16b31dae66279d99c7c2`; revisiones documentales posteriores no alteran la imagen.
- Fuente compilada/desplegada: `14b122cb41f48cca1118e55880381638583183ae`; corrige las aserciones de los nuevos tests de interfaz. Los commits posteriores de entrega/documentación no cambian el binario.
- Imagen local: `ddn-formulario:certificacion-20261103-14b122cb41f4`.
- ID local de imagen: `sha256:3e54e7de1c8b8ff15f3ff7331278ee80fa47d71b1a224e236d4533b67a78839c`. No se publicó en un registry externo.
- JAR SHA-256: `92ea9cc8ddf481ff062aad2e9ba155b443fa3ce8c940498e71fd5101d37b6fb0`.
- Exportación privada de imagen SHA-256: `9bb53a4646d91f9ba73a609d880c0d8112c54142df802bb9e423c9ae4812be74`.

Git CLI carecía de credenciales de escritura; publicación realizada con el conector GitHub, comprobando árboles equivalentes y actualizaciones sin forzar. El checkout anterior `/home/admin-ti/proyectos/formulario-web` se conservó.

## Comportamiento y configuración efectiva

`/srv/ddn-formulario-institucional`, proyecto Compose `ddn-formulario-institucional`. React se sirve desde el JAR; imagen runtime sin Maven ni servidor de desarrollo. Se sustituyeron solamente aplicación y proxy; MySQL, antivirus e inspector conservan sus contenedores e instantes de inicio.

| Parámetro | Valor |
|---|---|
| `siger.certificacion-claveunica` | `true` en este despliegue; `false` por defecto en código |
| Entorno CU | `PRODUCTION`, habilitada |
| Origin | `https://formulario.defensorianinez.gob.cl` |
| Redirect | `https://formulario.defensorianinez.gob.cl/redirect` |
| Retorno de logout | `https://formulario.defensorianinez.gob.cl/logout` |
| Algoritmo ID token | HS256 explícito; asignación específica al cliente pendiente de confirmar con proveedor/token real |
| Cookie/sesión | `SIGERSESSION`; Secure, HttpOnly, SameSite=Lax; 20 minutos; seguimiento solo por cookie |
| Forwarded headers en Spring | `none`; no se usa un encabezado de entrada para fingir HTTPS |
| Recepción adulta, NNA y formulario | `false` |
| Integración/PULL HTTP | `publico.integracion.habilitada=false` |
| PISEE/SRCeI, mantenimiento y retirada | deshabilitados |
| Control persistido | revisión 4; `accept_new=0`, `pull_enabled=0`, `SOLO_CONSULTA` |
| Publicación del proxy | exclusivamente `127.0.0.1:24443 → 8443/TLS` |

Portada institucional con aviso de suspensión y botón oficial que inicia OAuth directamente, sin crear recorrido. Todas las vistas antiguas pasan por la selección del modo del servidor. El mensaje «Autenticación realizada correctamente» depende de una identidad que el backend haya autenticado; no existe acceso por query para producir ese resultado.

El filtro de certificación aplica una lista cerrada de rutas/métodos antes de controladores y procesamiento multipart. Bloquea comunicaciones históricas/completas, recorridos, identificación alternativa, cargas/bloques, adjuntos, operación e integración. Caddy bloquea adicionalmente rutas operativas, administrativas e integración y rechaza Host ajeno con 421. La información personal y el identificador del ingreso se omiten de `/api/session` en este modo.

## Transporte y seguridad

Trayecto preparado: Cloudflare Tunnel en el host → HTTPS loopback Caddy → HTTPS Spring → JDBC MySQL `sslMode=VERIFY_IDENTITY`. No se modificó Cloudflare ni se abrieron puertos. Solo el tramo posterior al túnel se validó localmente.

Caddy verifica la CA privada y el nombre `app` del upstream. Spring requiere TLS propio, no una cabecera del proxy. El truststore Java conserva CA públicas y la CA privada; con ese mismo archivo se verificó HTTPS hacia los metadatos oficiales de CU. No se usaron opciones que omitan TLS/SAN.

Certificado Caddy: SAN canónico; válido del 02/11/2026 al 04/12/2027. PKI, claves de firma/confianza, secretos previos y volumen MySQL se conservaron. Aplicación/proxy/workers ejecutan UID 1000, filesystem de solo lectura y sin capabilities; MySQL conserva su configuración previa. Solo el proxy publica un puerto de esta pila.

Se mantienen CSRF para logout, protección de fijación de sesión de Spring, state/nonce dinámicos, validación de firma/algoritmo/issuer/audiencia/nonce/vigencia y concordancia de UserInfo. Tokens OAuth no se conservan en el cliente autorizado. Logout local usa POST con CSRF y navegación al endpoint oficial; GET `/logout` es el retorno. No se agregaron autenticaciones sustitutas.

Respuestas de certificación: `Cache-Control: no-store`, exclusión de indexación, HSTS, CSP, nosniff, bloqueo de frames, Referrer-Policy y Permissions-Policy. Caddy no tiene access log habilitado; no registra query OAuth. Respuestas de error no incluyen mensajes internos ni stacktraces.

## Credenciales productivas

Se encontraron el adjunto local y dos paquetes privados coincidentes. Contienen un par rotulado PRODUCCIÓN. Se incorporó el par original a `cu/siger.claveunica.client-id` y `cu/siger.claveunica.client-secret`, fuera de Git y de la imagen, permisos `0400`, propietario UID/GID `1000:1000`. El arranque los lee mediante el configtree existente. No se crearon credenciales ni se regeneraron claves.

Verificaciones reales sin autenticar una persona:

- Autorizador oficial: HTTPS verificado, respuesta 200 con formulario de acceso; no mostró error de cliente ni Redirect URI Error.
- Logout oficial sin sesión: HTTPS verificado y 302 al retorno canónico exacto.
- Metadatos oficiales: anuncian HS256 y RS256. El paquete privado no declara algoritmo ni caducidad del cliente. La aplicación fija HS256, sin autodetección ni aceptación de otro algoritmo.

Esto acredita únicamente el preflight. Falta comprobar el secreto mediante intercambio legítimo de código, el algoritmo emitido para este cliente, su vigencia/activación y la matriz de URI autorizadas. `callback-approved=true` permite ejecutar la prueba solicitada; no representa aprobación del proveedor.

**Incidente de esta sesión:** la primera lectura del adjunto mostró indebidamente sus valores en una salida de herramientas del chat. No se reprodujeron posteriormente. Se comprobó ausencia de esos valores en el árbol Git publicado, JAR, configuración de imagen y logs de los contenedores app/proxy. Corresponde gestionar la rotación del secreto con el proveedor y sustituirlo en el configtree antes de utilizarlo externamente. No se puede retirar desde este despliegue el contenido ya mostrado en el registro del chat.

## Pruebas y resultados

| Comprobación | Resultado |
|---|---|
| Docker multietapa: lint, React, build y JAR | correcto; 214 pruebas React |
| `npm audit --omit=dev` | 0 vulnerabilidades reportadas para dependencias frontend de ejecución |
| Backend H2/Maven | 379 pruebas: 377 correctas, 2 omisiones opt-in existentes, 0 fallos/errores |
| Python con JDK 25 privado | 46 pruebas correctas, sin omisiones |
| Verificador del despliegue | 30/30 comprobaciones finales correctas |
| HTTPS local | CA/SAN válidos; sin confianza o con SAN erróneo, conexión rechazada |
| Caddy → Spring | TLS verificado en configuración activa y respuestas reales |
| Spring → MySQL | conexiones runtime cifradas comprobadas; VERIFY_IDENTITY configurado |
| Sesión/logout | atributos de cookie, CSRF obligatorio, cookie expirada y antiguo par sesión/CSRF rechazado |
| OAuth | endpoint oficial, redirect exacto, state/nonce y state distinto entre inicios; callback no solicitado no autentica |
| Aislamiento | Host ajeno rechazado; Forwarded falsificado no altera origen; rutas administrativas cerradas |
| APIs | 503 por proxy y directamente contra Spring, incluso CSRF/sesión y tokens técnicos válidos |
| Interfaz real | escritorio 1440 y móvil 390, sin desborde, sin formularios alternativos ni falso éxito |
| Persistencia | mismos conteos y controles; 27 archivos de PKI/claves/almacén con hashes idénticos |
| Backup/restore | restauración hacia base/directorio nuevos, 2 archivos; original, derivada y 1 comunicación verificados |
| Recuperación | aplicación detenida y recreada desde checkpoint de certificación; comprobaciones repetidas correctas |
| Arranque futuro | unidad systemd activa/habilitada; Compose referencia imagen y Caddyfile de certificación |

Las pruebas unitarias de OAuth/identidad utilizan fixtures aislados y no acreditan login productivo. La inspección visual renderizó respuestas reales obtenidas con un cliente Node que verificó CA y SAN; no deshabilitó TLS, no sustituyó respuestas de autenticación y no acredita la confianza del navegador externo.

Conteos antes/después: 0 entregas históricas; 1 recorrido, 1 comunicación, 1 adjunto, 1 carga, 1 bloque y 4 auditorías operacionales. Son datos previos, conservados; esta tarea no creó comunicaciones ni abrió controles. Flyway permanece en V12 con 12 migraciones exitosas, sin modificar/aplicar migraciones ni checksums.

El ensayo de recuperación retorna al **checkpoint de certificación**, no ejecuta la antigua imagen anterior a certificación. Esa imagen carece del nuevo cierre y no es un rollback seguro con el túnel publicado. No se hizo rollback de esquema ni restauración sobre la base activa. El backup sigue en el mismo servidor y no acredita independencia física.

## Evidencias y repetición local

Directorio privado: `/home/admin-ti/operacion-formulario/2026-11-03-certificacion` (acceso administrativo). Contiene build, imagen exportada, JAR, metadatos de despliegue, respaldo coherente, archivos de configuración anteriores y de certificación, verificaciones TLS, escaneo de credenciales, hashes y resultados de recuperación. No adjuntar ese directorio a correos, Git o tickets: incluye copias privadas de configuración/secretos y datos preservados.

Restore aislado: base `formulario_restore_cert_20261103`; directorio `/srv/ddn-formulario-restauracion-certificacion-20261103`. No conecta a la aplicación activa.

Capturas y CA pública para TI: `/home/admin-ti/operacion-formulario/2026-11-03-browser`. `origin-ca.crt` contiene solamente el certificado público de la CA.

Repetir verificación, con nombre de resultado nuevo:

```bash
sudo python3 /home/admin-ti/formulario-ubuntu-h1-20261102/scripts/ubuntu/verificar-certificacion.py \
  --output /home/admin-ti/operacion-formulario/2026-11-03-certificacion/verificacion-adicional.json
```

El script no imprime cookies, state, nonce, tokens ni identidades. Ejecuta peticiones negativas que deben rechazarse y un inicio OAuth sin seguirlo; no autentica personas ni envía comunicaciones.

Retorno al checkpoint probado, únicamente para esta instalación/release:

```bash
sudo python3 /home/admin-ti/operacion-formulario/restore-certificacion-checkpoint-20261103.py
```

Restaura `compose.env` y configuración del checkpoint de certificación y recrea exclusivamente app con la imagen preservada. Requiere conservar el release activo de certificación y su Caddyfile; después repetir el verificador. No restaura bases, claves ni almacenes. Ante un problema del proxy, mantener el túnel cerrado y recuperar su Caddyfile de certificación; no publicar un backend anterior sin el filtro.

## Verificación externa cuando TI habilite Cloudflare

1. TI configura su túnel sin cambiar este despliegue: servicio `https://127.0.0.1:24443`, `originServerName` y `httpHostHeader` iguales a `formulario.defensorianinez.gob.cl`; `caPool` debe apuntar a una copia protegida de la **CA pública** de origen. Mantener verificación TLS habilitada. No copiar claves privadas al túnel ni publicar puertos entrantes.
2. Aplicar bypass de caché para todo este host de certificación, respetar `no-store`/cookies y excluir callbacks/OAuth/logout de transformaciones o desafíos que rompan el flujo. Comprobar que un origen/SAN/CA incorrectos fallen, sin activar opciones de TLS inseguro. La configuración Cloudflare la realiza TI; no fue modificada por esta tarea.
3. Tras gestionar la rotación indicada y confirmar con Gobierno Digital ambiente, algoritmo y URI exactas, incorporar el par productivo actualizado en el mismo configtree, `0400`/UID1000. Recrear solo app; no reconstruir la imagen ni cambiar flags de recepción.
4. Desde una red externa abrir `https://formulario.defensorianinez.gob.cl`. Verificar certificado público, portada suspendida y botón oficial. `/api/session` debe indicar PRODUCTION, modo de certificación y ausencia de autenticación; no debe mostrar información personal. Confirmar ausencia de cache HIT y atributos de cookies.
5. Comprobar que POST a `/api/comunicaciones` y `/api/formulario/recorridos`, así como acceso a `/operacion/v1/control`, `/integracion/…` y `/actuator/health`, siguen rechazados. Nunca abrir controles operacionales para esta prueba.
6. Una persona autorizada hace login real en el proveedor oficial, desde el botón. Confirmar retorno por `/redirect`, mensaje exacto «Autenticación realizada correctamente», sesión auténtica y ausencia de recorrido/comunicación/adjunto nuevos. No guardar contraseñas, code/state/nonce, tokens o RUN en capturas, HAR, logs o tickets.
7. Usar «Cerrar sesión»: comprobar POST local con CSRF, navegación al logout oficial, retorno `/logout` y `/api/session` no autenticada. Verificar expiración y rechazo de callbacks repetidos según la prueba institucional autorizada. Repetir verificador local y comparación de conteos.
8. Entregar evidencias no sensibles a Gobierno Digital mediante el canal institucional y esperar su aprobación. El preflight y el despliegue local no constituyen certificación oficial.

Pendientes externos: rotación por el incidente del chat; túnel/DNS/certificado público y políticas Cloudflare; confirmación del cliente/algoritmo/vigencia/URI; autenticación y logout reales; aprobación oficial. La recepción ciudadana, PISEE y SIGER/PULL permanecen cerrados independientemente de esos hitos.
