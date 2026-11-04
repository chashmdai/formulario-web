# RC1 — protección temporal instalada para pruebas institucionales

**Actualización 04/11/2026:** [publicación y recepción autorizadas](CHECKPOINT_PRODUCCION_SIGER_2026-11-04.md). La política de apertura V2 permite que, al vencer la contraseña, continúe el mismo RC1 con la recepción autorizada sin requerir Windows/E2E terminados. Siguen vigentes los cierres por incidencia y capacidad. Las referencias siguientes al estado inicialmente cerrado son históricas.

Encargo del 03/11/2026; cierre de ejecución 04/11/2026, America/Santiago. Rama `release/formulario-produccion-2026-11`. Instalación privada `/srv/ddn-formulario-produccion`, URL `https://localhost:30443/?vista=inicio`. No se sustituyó certificación ni se modificó Cloudflare. Recepción y PULL continúan cerrados.

## Comportamiento instalado

La pantalla institucional se sirve desde Spring Boot, antes de entregar React o las API ciudadanas. Es responsiva, utiliza el logo existente, etiquetas accesibles y formulario HTML sin dependencia de JavaScript. El servidor verifica la contraseña con PBKDF2-HMAC-SHA256, sal aleatoria de 16 bytes, 310.000 iteraciones y derivación de 256 bits. La contraseña generada tiene 256 bits de aleatoriedad; nunca se incorpora al JAR, imagen, Git, documentación, URL o logs.

Acceso no autorizado: interfaz redirigida a `/acceso-temporal`; API ciudadanas y assets React rechazados con 401. El formulario exige HTTPS y CSRF. Una contraseña incorrecta responde 401; límite alcanzado, 429. Mensajes genéricos sin reflejar el valor ingresado.

Se reutiliza la sesión HTTP existente, con cookie Secure, HttpOnly y SameSite=Lax, compatible con el retorno OAuth por GET. Al conceder acceso se cambia el identificador de sesión y se descarta el CSRF anterior. El permiso temporal dura como máximo ocho horas, sujeto además a la caducidad por inactividad de la sesión existente y a la fecha final de campaña. No crea principal ciudadano, rol ClaveÚnica ni una identidad autenticada; el adulto declarado permanece `autenticada=false`.

Los contadores de fallos sobreviven al reinicio: cinco por origen observado por el backend y cien globales en quince minutos. No confían en `X-Forwarded-For` enviado por el cliente. Tras el proxy, varios usuarios pueden compartir ese origen y su límite conservador. Los accesos correctos no consumen el cupo de fallos. Se guardan únicamente contadores y HMAC del origen, sin IP en claro, contraseña ni cuerpos HTTP. Archivo privado y reemplazo atómico; corrupción falla cerrada.

## Caducidad y apertura independiente

**15/12/2026 a las 00:00 America/Santiago = 15/12/2026 a las 03:00 UTC.** La política evalúa un `Clock` del servidor en cada solicitud. No requiere cron, temporizador del navegador, reinicio, recompilación ni cambio manual de flags al llegar esa fecha. La configuración con zona/fecha permanece en el archivo privado y se respalda.

Desde ese instante:

- Sin GO institucional válido: desaparece la exigencia de contraseña, se muestra página institucional de servicio no disponible y se rechazan las API ciudadanas con 503, incluso si existía una sesión temporal autorizada.
- Con GO válido: deja de exigirse la contraseña; continúan todos los controles independientes del Formulario, recepción, SIGER, sesión y CSRF.

No existe apertura automática de recepción ni de PULL. La decisión posterior a la caducidad exige el documento persistente de apertura y las cuatro habilitaciones existentes de integración/recepción/aviso, además del control CAS `aceptarNuevos` autorizado. Falta, corrupción, revocación o error de lectura/consulta produce cierre. Las pruebas usan un reloj inyectado exclusivamente en tests; no existe endpoint ni propiedad de producción para falsificar la fecha.

Documento `keys/acceso-temporal-apertura.json`: instalado con `authorized=false`. `productor.py open` solo puede escribir la aprobación después de verificar el documento institucional preexistente `authorization/open.approved.json`, con firma institucional aprobada, Windows preparado, E2E físico y aviso aprobado. Ninguno de esos documentos fue autorizado ficticiamente. Un JSON de aprobación por sí solo no sustituye los controles CAS/configuración. No editarlo a `true` para eludir la apertura coordinada.

## Custodia y rotación

| Ruta privada bajo `/srv/ddn-formulario-produccion` | Uso |
|---|---|
| `secrets/acceso-temporal-password` | Contraseña recuperable por el operador; 0400/root. Abrir únicamente mediante permisos administrativos en una terminal/editor privado, sin pegar su contenido en este chat, informes o tickets. |
| `keys/acceso-temporal-password.hash` | Hash salado para verificación; 0400/uid1000, montaje de claves solo lectura. |
| `keys/acceso-temporal-apertura.json` | Decisión institucional persistente; actualmente no autorizada. |
| `acceso-temporal/limites.json` | Estado durable de límites, separado de la custodia de Comunicaciones. Directorio 0700/uid1000, archivo 0600. |
| `config/application.properties` | Activación explícita, fecha/zona y rutas externas. |

Rotar **sin recompilar ni reiniciar**:

```sh
sudo python3 /srv/ddn-formulario-produccion/release/scripts/ubuntu/acceso-temporal.py --root /srv/ddn-formulario-produccion rotate
```

Genera y custodia una contraseña nueva y su hash sin mostrarlos. El backend relee el hash y revoca los permisos de campaña anteriores en la siguiente solicitud; conserva la distinción con la identidad CU. `configure` reaplica rutas/configuración conservando la credencial y requiere reiniciar app si cambian propiedades. `provision` rechaza sobrescribir una credencial existente. No cambiar solo el archivo de contraseña: su hash debe corresponder, mediante esta herramienta.

El estado tiene un bind mount propio `/var/lib/acceso-temporal`. No se coloca dentro del almacén de adjuntos, que rechaza directorios ajenos como defensa de integridad. El primer ensayo detectó esa incompatibilidad y se corrigió moviendo exclusivamente el contador recién creado, conservando la guardia del almacén y añadiendo regresión de reapertura. No se modificaron datos ni contratos para obtener pruebas verdes.

El respaldo cifrado y la restauración incluyen credencial, hash, decisión, configuración y estado con sus propietarios. La sesión HTTP activa no se conserva al reiniciar: se vuelve a exigir acceso; la política, contraseña, fecha y contadores sí sobreviven.

## SIGER, PISEE, ClaveÚnica y salud

El filtro se instala únicamente en la cadena ciudadana de Spring Security, después de CSRF y antes de OAuth/logout. Las cadenas independientes `/integracion/**` y `/operacion/**` conservan Bearer, HTTPS, roles, firmas, confianza y CAS. El consumidor SIGER no recibe ni necesita la contraseña temporal.

PISEE/SRCeI sigue detrás de sesión y recorrido autorizado. No se modificaron certificado institucional, PKI, cliente XML, finalidad ni política de RUN variables. No se realizaron nuevas consultas personales para esta implementación.

ClaveÚnica conserva Authorization Code, state/nonce, callback público `/redirect`, validación criptográfica, Token/UserInfo y logout. El permiso temporal se conserva al cambiar el ID de sesión en el callback. La cookie Lax permite el retorno HTTPS del proveedor. Las credenciales CU y su configuración institucional no cambiaron. En pruebas automáticas se completó el flujo con proveedor controlado exclusivamente de test; en Firefox se verificó la redirección oficial con callback/state/nonce. No se presenta un login gubernamental completo como acreditado.

Salud mínima `/acceso-temporal/salud`: solo `{"status":"UP"}`, sin sesión, datos, configuración ni comprobaciones de identidad. Compose usa `FORM_HEALTH_PATH=/acceso-temporal/salud`, porque `/api/session` está protegido. El estado de servicio institucional cerrado tras caducidad no inutiliza el transporte técnico. Las rutas administrativas continúan bloqueadas en Caddy para el acceso frontal.

## Validaciones y evidencias

Resultados, imagen/JAR y hashes en [manifiesto](ACCESO_TEMPORAL_RC1_MANIFIESTO_2026-11-03.json). Se verificaron rechazo anónimo/incorrecto, acceso correcto, cookies, rotación de ID, aislamiento, ausencia de autenticación CU por contraseña, límites persistentes, revocación al rotar, caducidad inclusiva y página/API cerradas sin GO. Regresión completa del backend, frontend durante empaquetado y herramientas Python; omisiones opt-in identificadas en el manifiesto.

Ensayo PULL/ACK sobre MySQL real en esquema/custodia sintéticos nuevos: contratos 1.0/1.1, adjunto FILE, AV/Inspector, SHA-256, firmas Ed25519, reserva/descarga/Range/HEAD, COMMIT de fixture local antes de ACK, ACK repetido e idempotencia. **Todas las solicitudes institucionales se ejecutan sin cookies ni contraseña temporal.** Al finalizar se restituyen base/configuración/confianza originales. No hubo SIGER Windows ni reclamación de campañas conservadas.

Firefox gráfico con confianza CA del perfil, sin bypass TLS: pantalla inicial, contraseña incorrecta, correcta, API, cookie y vista móvil; evidencias sin contraseña ni datos personales. Perfil aislado `/home/admin-ti/snap/firefox/common/formulario-acceso-temporal-20261103`. Ventana final en pantalla de acceso, sin contraseña rellenada. La prueba de reinicio confirma contador/política persistentes y autorización nuevamente disponible con la credencial privada.

## Reversión conservada y límite de alcance

Imagen previa `sha256:3a6482156acad872ac9830c78e16ca23f7d977dd7b9d173e1b4c89db34ae987d` conservada, junto al JAR y archivo de imágenes anterior. Instantánea privada `rollback/acceso-temporal-20261103`: release, configuración, Compose, metadatos, JAR y hashes criptográficos anteriores. Respaldo previo cifrado `/srv/ddn-formulario-respaldos/rc1-antes-acceso-temporal-20261103.gpg`, con su SHA externo. No hubo migraciones de base de datos.

La reversión privada conserva base/custodia/certificados; debe restaurar conjuntamente esa configuración/release e imagen, incluyendo la ruta de salud anterior `/api/session`. Nunca aplicar solamente una imagen antigua con el healthcheck nuevo. Tras devolver la configuración, recrear exclusivamente app/proxy y verificar controles cerrados. No realizar una reversión que quite esta barrera en un origen público sin autorización: la instalación actual continúa aislada en loopback30443.

Este trabajo no autoriza cutover, recepción pública ni una transferencia física SIGER. El [procedimiento de transición anterior](INSTALACION_PRODUCTIVA_EJECUTADA_2026-11-03.md) permanece sujeto a sus aprobaciones. La caducidad de la contraseña no sustituye ninguna de ellas. Graph/correo siguen fuera del alcance del Formulario.
