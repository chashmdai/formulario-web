# Dominio del Formulario · 28/10/2026

## Alcance

URL canónica solicitada: https://formulario.defensorianinez.cl/?vista=inicio.

El cambio se realiza en el WSL actual y en la resolución local del equipo Windows autorizado. No crea DNS público, túneles, servicios abiertos a Internet ni una certificación de ClaveÚnica. PISEE sigue en DEV; se conservan sus secretos, allowlist y autorización. No cambian contratos, firmas, UUID, datos, esquema V12 ni políticas de retirada.

## Código y configuración

- ClaveUnicaProperties admite el hostname exacto formulario.defensorianinez.cl en PRODUCTION, además de los dominios .gob.cl históricos. No habilita cualquier subdominio .cl. Continúan HTTPS, rutas exactas /redirect y /logout, URI sin usuario/query/fragmento, cookie Secure y callback habilitado.
- El lanzador integrado acepta el nuevo origen y exige que ambas URI correspondan exactamente con él.
- La plantilla productiva usa las nuevas URL. Los identificadores y secretos permanecen externos a Git.
- React y el botón CU ya usan rutas relativas; no requieren sustituciones ni ampliar CORS.
- Caddy atiende el nuevo dominio y redirige el alias anterior al origen canónico antes de iniciar sesiones. Se evita mezclar cookies OIDC de dominios diferentes.
- El salto interno Caddy → 18443 conserva el certificado, la CA y el SNI anteriores. El certificado adicional del proxy cubre ambos nombres y utiliza la misma clave/CA local; no sustituye los originales.

La configuración privada efectiva conserva todos los valores salvo estas tres propiedades:

```properties
siger.claveunica.public-origin=https://formulario.defensorianinez.cl
siger.claveunica.redirect-uri=https://formulario.defensorianinez.cl/redirect
siger.claveunica.logout-uri=https://formulario.defensorianinez.cl/logout
```

El cambio de dominio y el reinicio controlado de la JVM integrada invalidan sesiones HTTP en memoria. No trasladar cookies o códigos de autorización entre dominios ni redirigir callbacks como sustituto de registrar la URI correcta. Iniciar un recorrido nuevo desde el origen canónico.

## ClaveÚnica y publicación externa

La autorización local puede construir el redirect_uri nuevo. Completar la autenticación exige que Gobierno Digital registre para el Client ID vigente:

- https://formulario.defensorianinez.cl/redirect
- https://formulario.defensorianinez.cl/logout
- Sitio de revisión: https://formulario.defensorianinez.cl/

No se ejecuta autenticación gubernamental, no se modifica el registro del proveedor y no se afirma que acepte las nuevas URI. La bandera local callback-approved no demuestra esa aceptación. La guía publicada de CU mantiene requisitos de dominio: confirmar expresamente con SGD la aceptación del dominio .cl solicitado durante su registro/certificación.

Para acceso desde otros equipos o la VM definitiva, TI debe provisionar DNS y certificado público del hostname exacto, además de la conectividad autorizada. La entrada hosts de este equipo no habilita ese acceso externo.

El consumidor SIGER y, cuando se utilice, el agente deben apuntar al nuevo origen por configuración. Esta unidad no modifica esos aplicativos ni sus servicios.

## Operación y reversión

El proxy utiliza la hoja indicada por FORMULARIO_TLS_CERT_FILE; si no se configura, conserva la hoja original de SANDBOX_TLS_DIR. Para el nuevo dominio, esa hoja debe incluir su SAN. El SNI interno no debe cambiarse mientras el backend utilice su certificado anterior.

Las copias previas de configuración, selección del JAR y Caddyfile se conservan en un directorio privado dominio-formulario-previo-* fuera del repositorio. Windows conserva un respaldo íntegro de hosts y su SHA-256 antes de añadir exclusivamente la nueva resolución; WSL tiene un respaldo separado.

Reversión: detener solo la JVM integrada mediante su lanzador, restaurar su configuración/selección respaldadas y arrancar el JAR anterior inmutable. Restablecer el Caddyfile previo y usar la hoja original del proxy mediante Compose, recreando únicamente Caddy si es necesario. Retirar exclusivamente la nueva entrada hosts y limpiar DNS; restaurar el archivo completo solo si no recibió modificaciones posteriores. No restaurar ni degradar bases y no tocar el Nodo, certificados originales, claves o almacenes.

## Evidencia

| Verificación realizada | Resultado |
|---|---|
| Backend H2, sin Carga*Test | 348 seleccionadas: 346 correctas, dos omisiones opcionales, cero fallos/errores |
| Backend MySQL QA aislado, misma selección | 348 seleccionadas: 346 correctas, dos omisiones opcionales, cero fallos/errores |
| Casos nuevos del dominio | 10 correctos por motor: hostname exacto/imitaciones, HTTPS, cookie, callback, state/nonce y retornos |
| Python lanzadores | 19 correctas; incluye nuevo origen y rechazo de callbacks incoherentes |
| Caddy | Configuración validada antes de recrear exclusivamente el proxy existente |
| Windows HTTPS normal | HTTP 200, TLS verificado, sin omitir confianza o comprobaciones globales |
| Nombre anterior | HTTP 308 al nuevo origen; certificado válido para ambos nombres |
| HTML, JavaScript y CSS por HTTPS | HTTP 200; recursos del JAR idénticos a los de la versión integrada anterior |
| Sesión/capacidades/CSRF | CU PRODUCTION, login disponible, anónimo; adultos/NNA/multimedia habilitados; CSRF y no-store correctos |
| Inicio OIDC local | HTTP 302 al proveedor, mismo Client ID productivo, callback nuevo correcto, state y nonce presentes; no se siguió al proveedor |
| Configuración privada | Solo cambiaron public-origin, redirect-uri y logout-uri; PISEE y banderas de retirada idénticas |
| Servicios conservados | MySQL, Nodo PISEE, antivirus, inspector y laboratorio 19443 no se reiniciaron |
| Navegador Chrome | No verificado visualmente: la automatización se detuvo porque no pudo acreditar la URL de la ventana; no se eludió el control |

Las dos omisiones existentes corresponden a workers reales opt-in y reinicio destructivo de MySQL. Los casos de OIDC del dominio son sintéticos/locales; no acreditan que SGD haya registrado las URI. La batería general conserva las pruebas OIDC con proveedor controlado.

La primera ejecución falló en dos aserciones nuevas porque MockMvc tenía scheme=http pese a secure=true; se corrigió exclusivamente el fixture para usar URI HTTPS completa. Las dos regresiones completas finales están correctas. No hubo correcciones en los flujos ciudadanos.

JAR integrado instalado: 176ada4a0644052827bac8e85ae075bc19b957f67062d528652a7ab9625665d6, copia inmutable fuera de target. Se reinició únicamente esa JVM para cargar el código/configuración nuevos y se recreó Caddy. No se ejecutaron migraciones sobre los laboratorios existentes, consultas gubernamentales ni creación de Comunicaciones.

Mantenimiento online=false, eliminación=false, aceptación de riesgo ACK=false y modalidad GARANTIA_INDEPENDIENTE conservados. PISEE continúa DEV. Las credenciales CU y el TLS interno permanecen iguales. La hoja externa adicional vence el 27/11/2026; el certificado interno anterior mantiene su vencimiento del 22/11/2026: renovar ambos antes de esas fechas según el ambiente.

Archivo privado persistente para el proxy: /home/nexbyteproveedor/.local/share/siger/sandbox-https/formulario-proxy.env (0600). Al operar Compose debe usarse este archivo para seleccionar la hoja adicional; omitirlo selecciona la hoja histórica, que no cubre el nuevo dominio.

```bash
docker compose --env-file /home/nexbyteproveedor/.local/share/siger/sandbox-https/formulario-proxy.env -f infra/compose.sandbox-https.yml -f infra/compose.formulario-https.yml up -d --no-deps --no-build caddy
python3 scripts/linux/arrancar-nna-local.py /home/nexbyteproveedor/.local/share/siger/nna-local-20261023 --estado
```

El comando Compose solo es un procedimiento de recuperación del proxy; no inicia otra JVM ni migra bases. La instancia Java se recupera con su lanzador y selección inmutables existentes. Tras reiniciar WSL, revisar resolución local porque WSL puede regenerar /etc/hosts.

Las trazas privadas y el plan de reversión están en .local/dominio-formulario-20261028/; no se versionan secretos, cookies ni códigos. La comprobación visual manual y el registro/habilitación de las URI nuevas por SGD permanecen pendientes. No hubo push ni modificación del DNS público.

### Comprobación del laboratorio aislado anterior

El puerto directo 19443 se mantuvo sin modificar. Su URL anterior responde HTTP 200 desde WSL con la CA local y validación de hostname/TLS. curl.exe de Windows sobre ese puerto directo informa CRYPT_E_NO_REVOCATION_CHECK (Schannel, certificado anterior sin comprobación de revocación disponible). No se deshabilitó revocación ni se cambió ese certificado para resolver un problema ajeno al nuevo origen. El acceso canónico nuevo por 443 sí devuelve HTTP 200 y TLS verificado desde Windows sin excepciones.
