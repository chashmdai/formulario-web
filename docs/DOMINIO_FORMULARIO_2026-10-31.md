# Dominio corregido del Formulario · 31/10/2026

## URL vigente

**https://formulario.defensorianinez.gob.cl/?vista=inicio**, sin puerto explícito.

El responsable corrigió el nombre solicitado anteriormente: el dominio debe terminar en defensorianinez.gob.cl. El documento [del 28/10](DOMINIO_FORMULARIO_2026-10-28.md) conserva evidencia histórica del nombre equivocado; no describe la configuración vigente.

## Cambios acotados

- ClaveUnicaProperties vuelve a exigir HTTPS y dominio .gob.cl en PRODUCTION: se retira la excepción añadida para formulario.defensorianinez.cl. Continúan Secure, state, nonce, firma, sesión, CSRF y controles de callback.
- El lanzador integrado admite el origen corregido y el hostname histórico largo. Rechaza el origen equivocado .cl y exige callbacks /redirect y /logout coherentes.
- La plantilla productiva y la configuración privada utilizan:

```properties
siger.claveunica.public-origin=https://formulario.defensorianinez.gob.cl
siger.claveunica.redirect-uri=https://formulario.defensorianinez.gob.cl/redirect
siger.claveunica.logout-uri=https://formulario.defensorianinez.gob.cl/logout
```

- No cambian Client ID, Client Secret, ambiente CU PRODUCTION ni credenciales del Nodo. PISEE permanece DEV.
- Caddy atiende el nuevo nombre por 443 y redirige los dos alias anteriores al origen corregido. El salto interno a 18443 mantiene su SNI/certificado y validación TLS anteriores.
- Una hoja adicional firmada por la CA local existente cubre los tres nombres. Se reutiliza la clave existente; no se regeneran la CA, las claves ni los certificados anteriores.
- Windows y WSL agregan exclusivamente la resolución local del nuevo nombre a 127.0.0.1, con respaldos previos. Los alias anteriores se conservan para redirigir marcadores. No se cambia DNS público, firewall ni almacenes de confianza.
- React no requiere cambios: sus rutas y el botón CU son relativos al origen.
- No cambian schema V12, migraciones, datos, contratos 1.0/1.1, recibos, almacenes ni banderas de recepción/retirada.

## ClaveÚnica

La configuración local construye el callback correcto. El registro/habilitación de esas URI para las credenciales vigentes corresponde a Gobierno Digital y no se acredita por cambiar una propiedad. No se ha ejecutado un login gubernamental ni una consulta SRCeI en esta corrección.

Iniciar sesión desde el nuevo origen. Las cookies del dominio equivocado no se transfieren y un reinicio de Java invalida sesiones en memoria. No reutilizar pestañas antiguas con códigos/state previos.

## Verificación

| Comprobación ejecutada | Resultado |
|---|---|
| Backend H2 sin Carga*Test | 349 seleccionadas: 347 correctas, dos omisiones opcionales, cero fallos/errores |
| Backend MySQL QA aislado, misma selección | 349 seleccionadas: 347 correctas, dos omisiones opcionales, cero fallos/errores |
| Dominio en backend | 11 casos, incluido rechazo explícito del nombre .cl equivocado |
| Python lanzadores | 19 correctas, callbacks coherentes y origen corregido |
| Caddy | Configuración validada antes de recrear exclusivamente el proxy |
| Windows HTTPS normal | HTTP 200 y TLS verificado, sin excepciones de confianza/revocación |
| HTML y capacidades | Interfaz empaquetada; adultos/NNA/multimedia habilitados |
| Sesión y CSRF | Anónimo, CU PRODUCTION/login disponible, CSRF presente, no-store |
| Inicio local OIDC | 302 con callback /redirect del hostname .gob.cl, mismo Client ID, state y nonce; no se siguió al proveedor |
| Alias anteriores | Ambos responden 308 al origen corregido |
| Instancia aislada 19443 | Mismos PID/JAR/configuración; no se reinició |
| Frontend | Recursos empaquetados idénticos byte a byte al JAR anterior; sin cambios React ni nueva batería frontend |

Las omisiones existentes corresponden a workers reales opt-in y reinicio destructivo MySQL. No se ejecutaron cargas ni proveedores gubernamentales.

Solo cambiaron tres propiedades del integrado: public-origin, redirect-uri y logout-uri. Se conservan mantenimiento online=false, eliminación=false, aceptación ACK=false y GARANTIA_INDEPENDIENTE; PISEE sigue DEV. MySQL, Nodo, antivirus e inspector no se reiniciaron. El backend 18443 se reinició controladamente para cargar el JAR nuevo y Caddy se recreó con el certificado adicional.

JAR inmutable instalado: 5c402e7ae876b6829db03e9324d67b000f5840a2f367f4864172fd00312656dc. Se mantuvieron las claves/TLS internos y la CA existentes, sin migraciones de los laboratorios. Los respaldos y la evidencia completa permanecen privados en .local/dominio-formulario-gob-20261031/.

No se realizó verificación visual automatizada en Chrome ni una autenticación CU real. La aceptación del nuevo callback debe confirmarse con SGD o mediante una prueba institucional autorizada. Resultados locales, sin nueva CI remota. No se creó ninguna Comunicación.

## Operación y reversión

El proxy conserva su imagen existente y selecciona la nueva hoja mediante /home/nexbyteproveedor/.local/share/siger/sandbox-https/formulario-proxy.env (0600):

```bash
docker compose --env-file /home/nexbyteproveedor/.local/share/siger/sandbox-https/formulario-proxy.env -f infra/compose.sandbox-https.yml -f infra/compose.formulario-https.yml up -d --no-deps --no-build caddy
python3 scripts/linux/arrancar-nna-local.py /home/nexbyteproveedor/.local/share/siger/nna-local-20261023 --estado
```

El backend usa una copia JAR versionada e inmutable fuera de target. Una compilación posterior no la sobrescribe. No se añaden instancias duplicadas ni tareas de inicio automático.

Hay respaldo privado de application.properties, selección del JAR, registro de proceso, entorno del proxy y Caddyfile bajo dominio-gob-previo-*. Revertir solo esos componentes mediante sus lanzadores/Compose, sin cambiar ni degradar bases. Si hubiera cambios posteriores en hosts, retirar únicamente la entrada añadida; no sobrescribir otras entradas restaurando el archivo completo.

La hoja externa nueva es de laboratorio y necesita renovación antes de su vencimiento. El certificado interno anterior mantiene su vencimiento del 22/11/2026. El cambio local no provisiona HTTPS público ni acredita producción/certificación.

No se ejecutan push, operaciones sobre SIGER, reinicios de MySQL/PISEE, retiradas ni consultas gubernamentales como parte de esta corrección.
