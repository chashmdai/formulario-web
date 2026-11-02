# Integraciones del Formulario en Ubuntu

**Actualización 02/11/2026:** [modo de instalación cerrado sin CU ni confianza ACK](ARRANQUE_CERRADO_UBUNTU_2026-11-02.md). Los requisitos originales siguen vigentes al habilitar cada integración.


Complementa [instalación](INSTALACION_UBUNTU_DOCKER.md) y [traspaso/GATES](TRASPASO_DDN_UBUNTU_GATE_H1_2026-11-01.md). Configuración privada por ambiente. H1 no conecta proveedores ni infraestructura institucional.

## 1. HTTPS de origen y Cloudflare

Origen canónico previsto `https://formulario.defensorianinez.gob.cl`; no confundir con .cl sin gob. Callbacks exactos `/redirect` y `/logout`. Puerto24443 es transporte privado, no parte de URI pública.

Compose: host127.0.0.1:24443→Caddy8443→Spring8443TLS. Certificados privados verifican SAN; JDBC verifica mysql. Caddy no utiliza tls_insecure_skip_verify, Spring no confía en X-Forwarded para fingir TLS. Host canónico se fija en proxy, no desde entrada arbitraria. No hay puertos DB/AV/Spring publicados.

[cloudflared.example.yml](../infra/ubuntu/cloudflared.example.yml) muestra un túnel **no creado/activado**. Se instala posteriormente como proceso del host, con origen HTTPS, originServerName/httpHostHeader canónicos y caPool que contiene solo certificado público CA de origen. Nunca copiar la clave CA al túnel ni usar noTLSVerify. Si cloudflared se conteneriza, loopback cambia de namespace: requiere diseño de red explícito, no sustituir por HTTP inseguro.

TI entrega tunnelID/credencial privada, DNS/tenant/roles, política de acceso para revisión, confianza/renovación y autorización de tratamiento/ruta. Validar egreso7844 y fallback HTTP2, sin abrir NAT80/443 ni asumir UDP disponible. No cachear /api,/auth,/oauth2,/redirect,/logout,/integracion,/operacion; respetar no-store/cookies/Authorization. No aplicar desafíos interactivos de navegador al consumidor SIGER/ACK; acordar autenticación técnica Cloudflare adicional compatible si se exige. Esto no modifica firmas ni reemplaza el token de aplicación.

Cloudflare puede procesar antecedentes personales: TI debe validar ese trayecto, ubicación/condiciones y privacidad antes de publicar. Pruebas de transporte no son esa aprobación.

Referencias: [parámetros TLS de origen Cloudflare](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/configure-tunnels/origin-parameters/), [transporte TLS Caddy](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy), [TLS MySQL8.4](https://dev.mysql.com/doc/refman/8.4/en/using-encrypted-connections.html).

## 2. ClaveÚnica completa

Implementación: [SecurityConfiguration](../formulario-publico/backend/src/main/java/cl/nexbyte/siger/identidad/SecurityConfiguration.java), [propiedades](../formulario-publico/backend/src/main/java/cl/nexbyte/siger/identidad/ClaveUnicaProperties.java), [sesión/logout](../formulario-publico/backend/src/main/java/cl/nexbyte/siger/identidad/SessionController.java), [expediente de certificación](EXPEDIENTE_CLAVEUNICA_PISEE_CERTIFICACION_2026-10-28.md).

| Propiedad externa | Uso |
|---|---|
| siger.claveunica.environment | SANDBOX,QA,PRODUCTION; elegir paquete correspondiente, no mezclar credenciales |
| siger.claveunica.client-id/client-secret | Ficheros configtree, no JavaScript/URL/log |
| siger.claveunica.public-origin | https://formulario.defensorianinez.gob.cl |
| siger.claveunica.redirect-uri | https://formulario.defensorianinez.gob.cl/redirect, registrada por Gobierno Digital |
| siger.claveunica.logout-uri | https://formulario.defensorianinez.gob.cl/logout, retorno autorizado |
| siger.claveunica.id-token-algorithm | HS256 o RS256 según paquete; no autodetectar ni aceptar otro algoritmo |
| siger.claveunica.callback-approved | Declaración privada de habilitación; no altera aceptación del proveedor |
| server.servlet.session.cookie.secure | true; HTTPS/gob.cl exigidos en PRODUCTION |

Botón inicia `GET /oauth2/authorization/claveunica`→autorizador oficial `https://accounts.claveunica.gob.cl/openid/authorize/`, scopes openid/run/name, state/nonce/PKCE según implementación Spring. Callback `/redirect` valida solicitud/sesión/state; código se intercambia en servidor con `/openid/token/`; valida IDtoken/firma/issuer/audience/nonce/exp y userinfo `/openid/userinfo/`. Identidad autenticada se mantiene en sesión; no se confunde con RUN declarado/SRCeI.

Sesión `GET /api/session`, CSRF `GET /api/csrf`. Cierre `POST /auth/logout` con CSRF destruye sesión y redirige al logout oficial; retorno /logout conserva controles. No cambiar OAuth/canonicalización para instalar.

Estado QA: GET alcanzó autorizador y mostró Redirect URI Error; login exitoso productivo no se ha acreditado mientras se registra URI. Proveedor controlado prueba callback/token/userinfo/sesión/logout; evidencia no sustituye certificación Gobierno Digital. El arranque no consulta CU; smoke no sigue redirecciones externas. Prueba real de certificación será un GATE autorizado, sin contraseñas personales en logs/capturas.

## 3. PISEE productivo desde ZIP futuro

**ZIP productivo aún no recibido.** No incluirlo en Git, contextos Docker, registry o informes; tampoco certificados/credenciales. [PISEE_LINUX.md](PISEE_LINUX.md) es evidencia DEV histórica; código actual admite PRODUCTION, sin afirmar nodo productivo instalado. `preparar-pisee.py` está especializado en DEV y **no debe aplicarse automáticamente al ZIP productivo** porque ajusta rutas/configuración/servicios.

Procedimiento al recibirlo:
1. Registrar procedencia autorizada, nombre/versión/fecha y SHA256 del ZIP en evidencia técnica sin secretos. Inspeccionar listado antes de extraer: rechazar rutas absolutas/.., enlaces y archivos inesperados. Extraer solo a staging privado nuevo fuera del repositorio; no reutilizar SQLite/estado Windows o DEV.
2. Leer manual real del proveedor. Inventariar ELF64 Linuxamd64/NodoV2, librerías, rutas/puerto y esquema config. Comprobar wrapper Debian bookworm/xmlsec1 compatible; si el paquete cambia requisitos, documentar/adaptar **según evidencia**, no anunciar compatibilidad inexistente.
3. Verificar vigencia/cadena/SAN/uso y coincidencia clave-certificado PISEE/SRCeI sin imprimir claves. Conservar certificados originales; no regenerar identidades institucionales. Confirmar endpoint productivo, consumidor/ruta/autorización y egreso con TI/proveedor.
4. Preparar `PISEE_PRIVATE_DIR/runtime` RO(uid1000) y `state` RW0700. Secretos/certificados permanecen en runtime privado. Adaptar únicamente paths necesarios al manual, preservar configuración funcional; no filtrar/cambiar proveedores o contraseñas institucionales a ciegas.
5. Construir wrapper y validar Compose adicional, sin iniciarlo por defecto:

```bash
sudo docker build -t ddn-pisee:VERSION_REVISADA -f infra/pisee/Dockerfile infra/pisee
# Añadir previamente al compose.env privado: PISEE_IMAGE=ddn-pisee:VERSION_REVISADA
# y PISEE_PRIVATE_DIR=/srv/ruta-pisee-productivo-revisada
sudo docker compose --env-file /ruta/privada/compose.env -f infra/ubuntu/compose.yml -f infra/ubuntu/compose.pisee.yml --profile pisee config --quiet
```

6. Tras aprobar GATE, iniciar solo pisee, verificar salud/logs técnicos sin cuerpos/datos y conectividad privada. No publicar8084. El Node y app comparten red salida; el servicio no es accesible directamente desde ciudadanía.
7. Montar autorización local del consumidor en app como archivo RO y declarar fuera de Git:

```properties
PISEE_SRCEI_ENABLED=true
PISEE_ENVIRONMENT=PRODUCTION
PISEE_BASE_URL=http://pisee:8084
PISEE_LOCAL_AUTHORIZATION_PATH=/run/pisee/local-authorization
PISEE_TIMEOUT_MS=15000
PISEE_CONCURRENCY=2
```

Añadir el bind privado `/ruta/.../local-authorization:/run/pisee/local-authorization:ro` mediante override revisado. Mantener Node privado: HTTP solo admite pisee/localhost/127.0.0.1 por diseño; un origen remoto exige HTTPS verificado. No copiar autorización al navegador ni activar DEV_AUTHORIZED/allowlist DEV en producción.

8. Una consulta real autorizada y acotada desde el formulario, con RUN privado permitido y finalidad, verifica estados/procedencia/persistencia; no carga ni RUN reales en informe. NNA sin RUN no consulta, terceros no habilitan búsquedas indiscriminadas. SRCeI no autentica; datos registrales no se muestran para RUN declarado. Mantener fallback manual.
9. Reversión: deshabilitar integración y volver al paquete/config privada anteriores preservados; no tocar DB/claves CU/firmas. Registrar diferencia y esperar corrección del proveedor si falla, sin reemplazar por simulador productivo.

## 4. SIGER PULL/ACK independiente del agente

SIGER inicia conexiones HTTPS hacia Formulario; no abrir SIGER a internet ni montar su archivo en Ubuntu. La futura VM Windows/identidades/rutas se completará cuando termine su desarrollo; no es una dependencia para empaquetar este repositorio.

Base contractual: [custodia completa](CUSTODIA_FORMULARIO_COMPLETO.md), [contratos](../contratos/README.md), [multimedia1.1](../contratos/formulario-completo-multimedia-v1.1.md), [receptor transitorio](ARQUITECTURA_RECEPTOR_TRANSITORIO.md). Conservar bytes/canonicalización/firmas históricas.

| Operación bajo /integracion/v3/formulario-completo/entregas | Garantía |
|---|---|
| POST /reclamar, {"limite":1} | Bearer técnico HTTPS; reserva/lease, reintentos y control PULL |
| X-Formulario-Versiones: 1.0,1.1 | Opt-in1.1; sin cabecera solo1.0. No espacios dentro de lista |
| GET/HEAD /{id}/adjuntos/{adjunto}, X-Reserva | Pertenencia/reserva vigente, bytes originales íntegros; Range único/If-Range/ETag/206/416 |
| POST /{id}/confirmacion | reserva + recepcion(ACK institucional firmado); verificar documento/huella/folio |
| POST /{id}/fallo | reserva + código técnico; backoff/lease/cuarentena |

Documento completo incluye identidad/procedencia, presentador/afectados/grupos/personas relacionadas, campos/motivos, declaraciones, adjuntos(id,tipo,tamaño,SHA256) y firma. SIGER verifica firma confiada y manifiesto, transmite archivos a su almacén definitivo por buffers, verifica tamaño/SHA256, deduplica, registra COM/COMMIT y recién después firma ACK. No ACK por “descarga iniciada”; no alterar claves al migrar.

Intercambio privado: Form entrega su mapa público de firma al consumidor; SIGER entrega mapa público ACK a Form. Token PULL largo/aleatorio solo técnico, distinto de operación. No se entregan claves privadas de un sistema al otro. TLS es adicional a firmas, no las reemplaza.

QA automática ya verificó COM/ACK/originales. En el destino definitivo repetir E2E sintético autorizado, incluidos reinicio/reintento/indisponibilidad; no presentarlo como ejecutado por H1. Coordinar pausa del consumidor en cambios de endpoint/leases/certificados, conservar evidencia y reanudar con la misma identidad/config compatible.

## 5. Retirada, respaldo y agente

Recepción/PULL/COM/ACK no dependen del agente. Originales públicos no se borran al ACK. Mantenimiento online y retirada quedanfalse en el candidato.

- GARANTIA_INDEPENDIENTE: garantía Ed25519 independiente vigente+restauración acreditada, retención/holds/integridad.
- ACK_RETENCION_LIMITADA_APROBADA: opt-in/riesgo explícito, mínimo72h desde primer ACK persistido; duplicados no reinician. No fabrica garantía independiente.
- Ambas requieren activación operacional/configuración/restore/evidencia. Pendientes/cuarentenas/discrepancias/holds conservan; recuperación RETIRANDO e idempotencia preservadas.

Agente en entorno institucional Windows/Linux, SELECT controlado y medio independiente pendiente. No instalarlo en Ubuntu público ni tocar su emisión para conseguir una recepción exitosa. No basta otra letra de unidad en el mismo disco. [Agente Windows](../agente-custodia/README.md), [retención72h](RETENCION_ACK_72H_2026-10-26.md).

## 6. Dependencias externas resumidas

CU: URI/credenciales/algoritmo/certificación. PISEE: ZIP/certificados/nodo/servicio. SIGER: versión final/VM/trust/tokens/PULL/archivo. Cloudflare: cuenta/túnel/DNS/ruta/privacidad. TI: respaldo externo/restore/renovaciones/monitorización/energía/acceso. H1 prepara configuración y evidencia; no ejecuta esas aprobaciones ni habilita ciudadanía.
