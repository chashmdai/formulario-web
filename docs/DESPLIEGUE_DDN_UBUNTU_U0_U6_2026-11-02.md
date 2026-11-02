# Instalación Formulario Público en DDN-UBUNTU — 02/11/2026

Instalación nueva operativa y persistente en Docker, en modo cerrado. No habilitada para ciudadanía. No se migraron datos/adjuntos/registros de WSL ni de los laboratorios anteriores.

| GATE | Estado | Evidencia/resultado |
|---|---|---|
| U0 | GO | Inventario real SO/CPU/RAM/disco/LVM/red/Docker/systemd y clasificación exclusiva del laboratorio. |
| U1 | GO | Orquestador experimental deshabilitado; ocho contenedores detenidos; diez conservados apagados con cuatro volúmenes/redes/imágenes. Respaldos lógicos, físicos y configuración con hashes. |
| U2 | GO | Cinco servicios nuevos, volumen limpio, Flyway V1–V12, claves/PKI propias, CU y ACK deshabilitados explícitamente. |
| U3 | GO local cerrado | HTTPS/JDBC, recursos JAR, controles negativos, workers reales, reinicio de contenedores y unidad systemd, backup/restore a destinos nuevos. |
| U4 | BLOQUEADO por dependencia externa | Ubuntu preparado. Faltan confianza ACK auténtica y configuración/ruta/validación desde SIGER Windows; E2E COMMIT/ACK no ejecutado. |
| U5 | PENDIENTE | Procedimiento ZIP/nodo PISEE preparado; no paquete solicitado/recibido, servicio/consultas no activados. |
| U6 | PENDIENTE | CU/dominio/túnel y habilitación institucional pendientes; todavía no se solicitan credenciales CU. |

## Fuente y despliegue

Partida exacta H1 `bfb950690e85381b69e6dcfac167f0a237c80071`. Checkout nuevo `/home/admin-ti/formulario-ubuntu-h1-20261102`; AGENTS y documentación H1 leídos antes de actuar. Código desplegado y cuatro imágenes construidos desde `099c3b16e71d57b335496c3a112daccfa38831db`, rama `gate-u2-arranque-cerrado-20261102`. Publicación verificada mediante árbol Git; sin cambios de contratos ni migraciones V1–V12. Una eventual revisión documental posterior no cambia este SHA de despliegue.

Instalación `/srv/ddn-formulario-institucional`, release exportado del commit y 550 archivos comprobados por hash Git. Proyecto Compose `ddn-formulario-institucional`, volumen nuevo `ddn-formulario-institucional_mysql-data`. No comparte almacenamiento, secretos ni claves con los laboratorios.

| Servicio activo | Imagen | ID instalado |
|---|---|---|
| ddn-formulario-institucional-proxy-1 | `ddn-caddy:099c3b16e71d57b335496c3a112daccfa38831db` | `sha256:3ef54a5f6607fc7f550aa1a2ac17099382dfc3032776d477453bcddf5592cb5b` |
| ddn-formulario-institucional-app-1 | `ddn-formulario:099c3b16e71d57b335496c3a112daccfa38831db` | `sha256:8fe846f5af6b37ef5c8e61e8e2bdeec8c4cabeab290d3ca65204850283b68329` |
| ddn-formulario-institucional-antivirus-1 | `ddn-clamav:099c3b16e71d57b335496c3a112daccfa38831db` | `sha256:2513c005f21f3602f0172372ef8ea23f0e55cf6207c9c3785b72f2803f5994cb` |
| ddn-formulario-institucional-inspector-1 | `ddn-inspector:099c3b16e71d57b335496c3a112daccfa38831db` | `sha256:3a24d996d75003959df9b7f7733472cf9b4ef9b1fd1edfa87246613098f8a305` |
| ddn-formulario-institucional-mysql-1 | `mysql:8.4.11` | `sha256:0f51e63e537a1f176423fa03ef559aabe6fa4372a53160a44bc7e75da7d15364` |

Unidad `ddn-formulario-institucional.service` habilitada, active/exited, ExecMainStatus=0; ciclo completo systemctl restart verificado. Docker habilitado y restart=unless-stopped en los cinco servicios. MySQL/app/antivirus/inspector healthy; Caddy no tiene healthcheck propio en H1 y se verificó por HTTPS funcional. App/workers/proxy uid1000, filesystem RO, cap_drop ALL, no-new-privileges; inspector sin red con Landlock3/seccomp. Límites memoria: app/MySQL/AV1.5GiB cada uno, inspector768MiB, proxy128MiB. Uso observado total aproximado1.6GiB; no es prueba de carga. Raíz98GiB,34GiB usados,60GiB libres; LVM no ampliado.

## HTTPS y estado cerrado

URL de prueba `https://formulario.defensorianinez.gob.cl:24443`, exclusivamente por loopback del Ubuntu. No se creó resolución DNS pública ni publicación LAN/Internet. El nombre debe resolverse hacia127.0.0.1 para la prueba local y confiar explícitamente en la CA privada de origen:

```bash
sudo curl --fail --cacert /srv/ddn-formulario-institucional/pki/proxy/ca.crt \
  --resolve formulario.defensorianinez.gob.cl:24443:127.0.0.1 \
  https://formulario.defensorianinez.gob.cl:24443/api/session
```

Sin `-k` ni confianza global modificada. HTTPS verifica cadena/SAN hasta Caddy y Caddy verifica TLS hacia Spring. JDBC VERIFY_IDENTITY hacia MySQL8.4.11; sesión app TLS_AES_256_GCM_SHA384 y cliente TLSv1.3. Acceso sinTLS rechazado1045, SAN incorrecto2026; HTTPS sinCA o SAN incorrecto falla curl60. Runtime MySQL solo SELECT/INSERT/UPDATE/DELETE sobre formulario_publico; migrador separado. Doce migraciones exitosas y repetición sin nuevas migraciones.

Solo24443/127.0.0.1 se publica por esta pila. No puertos MySQL/Spring/AV publicados; antiguos13306/18080 cerrados. SSH, firewall, red institucional y demás servicios SO sin cambios.

Controles persistidos revision0, aceptarNuevos=false, recuperar=false, SOLO_CONSULTA. Capacidades adultos=false,NNA=false,multimedia=true. Cero Comunicaciones. CU sin archivos de credenciales, sin registro OAuth, login/callback503 sin identidad, logout local conserva CSRF. Sin mapa ACK institucional ni claves ficticias; integración/ACK/PULL503 incluso token válido y apertura operacional409. PISEE, mantenimiento y retiradafalse. Tokens/keys/config privados fuera de Git y contexto Docker.

## Pruebas y recuperación

- React: lint,212 casos y build correctos. Recursos JS/CSS/index servidos idénticos al JAR; catálogos idénticos a fuentes.
- Backend H2:376 pruebas,374 correctas,2 omisiones opt-in existentes (workers reales/recuperación de proceso),0 fallos. Incluye modo CU habilitado con proveedor controlado y nuevo modo cerrado. Ningún proveedor gubernamental consultado.
- Python:46 correctas con JDK25 extraído de imagen oficial a directorio privado; no se instaló JDK global. La primera pasada host omitía clase sin JDK; pasada final completa correcta.
- Dependencias frontend: auditoría npm de ejecución0 vulnerabilidades;10 moderadas del árbol de desarrollo H1 pendientes de hardening antes de apertura. No se aplicaron actualizaciones automáticas de dependencias.
- Inspector real:17 formatos sintéticos aceptados y13 activos/corruptos rechazados; aislamiento LANDLOCK3_SECCOMP.
- ClamAV real: descarga HTTPS oficial y comprobación interna main63/daily28172/bytecode339; PNG limpio y EICAR detectado, incluso al final de ZIP12583210bytes. No pruebas de carga ni máximos150/250MiB.
- Persistencia: parada/arranque de cinco contenedores y después ciclo de unidad systemd; archivo sintético conservado con SHA256, controles/certificados/recursos intactos. No se reinició físicamente el host.
- Backup consistente con app detenida: BD+almacén, un archivo sintético sin datos personales, manifest/hashes/conteos verificados. Restore en base NUEVA formulario_restore_u3_20261102 y directorio NUEVO /srv/ddn-formulario-restauracion-20261102;12 migraciones y0 Comunicaciones, archivo idéntico. Fixture retirado del almacén activo tras comparar su hash; base activa conserva0 Comunicaciones.
- Config/secretos/PKI respaldados separadamente y extraídos en destino nuevo; comparación íntegra y certificado restaurado verificado. Nunca publicados.
- Imágenes finales conservadas por IDs y archivo exportado privado con SHA256. Backup de laboratorio y nuevo ambiente en el mismo SSD: no acredita independencia física, RPO/RTO institucional ni recuperación frente a pérdida del servidor. Medio externo y restauración física siguen pendientes antes de apertura.

JAR SHA256: `e7decf6e37ded80dd30e9e60a53cc028994346b82a493aa68b6277c0dd1f427c`.
CA origen pública SHA256 de archivo: `9ce8ccfc0045660c4f46b88ccee91deca5e88890af7f6dec26c2ef7b7d31557e`. Certificado de origen válido hasta04/12/2027; programar renovación antes de vencer. No publicar claves CA/privadas.

## Saneamiento y reversión

`ddn-formulario-lab.service` detenido/deshabilitado. Caddy de namespace compartido, tres Spring/preview, dos MySQL, ClamAV e inspector experimentales retirados del servicio. Diez contenedores, cuatro volúmenes, sus redes, imágenes, claves, certificados y configuración conservados apagados para rollback. Dumps de dos MySQL, copia física de cuatro volúmenes detenidos y tar de configuración/estado/almacén (~260MiB comprimidos). No rm/prune global/down-v. cloudflared instalado conservado sin servicio/túnel activado. No se intervino SIGER Windows/WSL ni futura VM.

Reversión: detener unidad institucional; conservar nuevos datos/claves; iniciar/habilitar orquestador experimental original preservado, sin abrir sus controles. No ejecutar dos escritores/consumidores sobre un almacén. Restauración siempre hacia destinos nuevos; nunca Flyway repair/downgrade ni regenerar claves históricas.

## Pendientes institucionales

SIGER: IP/nombre/responsable y trayecto privado autorizado desde Windows, trust TLS, clave pública ACK Ed25519 auténtica y key-id, configuración privada del consumidor/token/trust de recibos, coordinación para E2E sintético PULL→originales→COMMIT/folio→ACK→CONFIRMADA. H2 SIGER de referencia35d9e6853d756706f8c34973408ccc60323afc5e no modificado. Bundle público Ubuntu preparado con CA origen y mapa de recibos; tokens permanecen privados. Propuesta SSH de reenvío local usando acceso existente documentada; no conexión Windows acreditada.

PISEE: en su etapa, ZIP productivo auténtico/manual/certificados/autorización y conectividad. Inspeccionar antes de extraer, verificar Linux/dependencias/PKI y usar directorio/estado nuevo y servicio independiente del JAR. No pedir paquete ahora ni consultar sin autorización.

CU U6: cuando corresponda, ClientID/Secret por archivos privados, algoritmo, URI /redirect y retorno /logout registrados y recorrido autorizado token/userinfo/sesión/logout. Configtree externa permite incorporarlos recreando app sin recompilar ni regenerar claves. Todavía no se solicitan.

Cloudflare/DNS: cuenta/roles/tunnelID/credencial, ruta/originServerName/CA, política de acceso y autenticación técnica compatible con SIGER, privacidad/tratamiento y autorización institucional. No túnel/DNS ni publicación realizados.

Apertura separada: requiere dependencias/integraciones aceptadas, respaldo externo y restore físico, operación/monitorización, actualización AV, renovación de certificados, capacidad/carga y decisión institucional explícita. La instalación cerrada está lista para continuar esas integraciones; no está habilitada para recepción ciudadana.
