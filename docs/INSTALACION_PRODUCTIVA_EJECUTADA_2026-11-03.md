# RC1 — instalación definitiva preparada y verificada

Fecha: 03/11/2026, America/Santiago. Repositorio `chashmdai/formulario-web`, rama conservada `release/formulario-produccion-2026-11`. Este informe actualiza el cierre RC1 anterior; sus artefactos históricos permanecen identificados, sin sustituir firmas ni contratos históricos.

| Estado | Resultado ejecutado |
|---|---|
| RC1 CÓDIGO | Implementado. Imagen definitiva con productor preparado, privacidad SRCeI, política productiva de RUN variables, CU/OIDC y contratos 1.0/1.1. Resultados y SHAs en el manifiesto adjunto. |
| RC1 INFRAESTRUCTURA | Instalada en `/srv/ddn-formulario-produccion`; seis componentes saludables, almacenamiento propio, TLS verificado, arranque automático, respaldo cifrado y restauración comprobada. |
| GO PRODUCTIVO | **NO-GO para abrir ciudadanía o sustituir certificación.** Faltan la identidad pública ACK/VM SIGER definitiva, E2E físico coordinado, aprobación de la firma nueva y habilitaciones institucionales indicadas al final. |

## Instalación y acceso existentes

URL privada en Firefox de DDN-UBUNTU: **`https://localhost:30443/?vista=inicio`**. La ventana quedó abierta; perfil independiente `/home/admin-ti/snap/firefox/common/formulario-produccion-definitivo-20261103`, permisos 0700. Confianza CA instalada solamente en ese perfil; no se desactivó TLS ni se modificó la confianza global.

Proyecto Compose `ddn-formulario-produccion`. Instalación autónoma, sin depender del checkout del desarrollador:

| Ruta | Contenido / custodia |
|---|---|
| `/srv/ddn-formulario-produccion/release` | Copia versionada del código y herramientas de operación; sin Git, dependencias de desarrollo ni secretos. |
| `installation.json`, `compose.env`, `compose.origin-ca.yml` | Proyecto, hostname, puerto, IP fija y manifiesto instalado; propiedades fuera del repositorio. |
| `config`, `secrets`, `cu`, `keys`, `pki` | Configuración, credenciales, identidades y certificados externos; acceso mínimo por rol. |
| `store`, volumen Compose MySQL | Custodia y base nuevas, persistentes, ajenas a QA/U4/campañas anteriores. |
| `pisee/runtime`, `pisee/config-produccion.json`, `pisee/state` | Runtime/PKI institucional conservados, configuración de rutas/IP propia y estado durable independiente. |
| `software/formulario.jar`, `software/images.tar`, `software/manifest.json` | JAR y las seis imágenes definitivas exportadas; SHA-256 y versiones. Archivo de imágenes cargado y comprobado localmente. |
| `/etc/systemd/system/ddn-formulario-produccion.service` | Unidad nueva habilitada y ejecutada correctamente; arranque `--no-build --wait`. No afecta la unidad de certificación. |

Compose instalado: `release/infra/ubuntu/compose.yml` + `compose.production.yml` + `compose.origin-ca.yml`. El último distingue la CA del certificado de origen de la CA nueva del backend; no introduce proveedores simulados. Imágenes fijadas por ID SHA-256, sin builds al arrancar. El archivo exportado permite importarlas en otro anfitrión; los IDs locales no se presentan como manifiestos publicados en un registro remoto.

Solo Caddy publica `127.0.0.1:30443`. Backend, MySQL, ClamAV e Inspector no tienen puertos de host. Red PISEE interna `10.203.74.0/29`, nodo `.2`, backend `.3`; autorización exacta a loopback y `.3`, conservada al recrear. No hay publicación 8084/8489. Nodo con salida necesaria a PISEE y sin logs de cuerpos; resto con rotación 10 MiB × 3. Healthchecks, límites y `unless-stopped` comprobados en los seis componentes. Docker habilitado al arranque; no se reinició el anfitrión compartido con certificación.

Base nueva migrada V1–V12, sin modificar scripts/checksums históricos. Al cierre: **cero Comunicaciones productivas**, recepción y PULL persistidos en `false`, contingencia `SOLO_CONSULTA`; sin tareas de eliminación activas. Navegación adulta/NNA y revisión disponibles. El envío ciudadano real permanece cerrado; no se fabrican comprobantes para eludirlo.

## Código y artefactos definitivos

Fuente del JAR/imagen: `a160f8b90f1358b24b8cbfca73598b6f1456c096`.

Implementación operativa instalada: `d3ca44bf4f15a94dbe1d65d8551d0974b651929d`, posterior a `034390f15fff9d9fe81acb73f56c0f0b0d6a77d0`. Los commits posteriores de cierre documentan evidencia y manifiestos; no requieren reconstruir Java ni React.

- JAR SHA-256: `485f2f644adbf4bd0ef966e8181da4d814d8d97f6113ba74c869c3fef1c5143d`.
- Imagen aplicación: `sha256:3a6482156acad872ac9830c78e16ca23f7d977dd7b9d173e1b4c89db34ae987d`.
- Archivo de seis imágenes SHA-256: `06452867abd026edaf8aaa8106b9f8d97dc0dc09f1f6436d9ab8dbb2fb8e80ff`.

El [manifiesto de la instalación](INSTALACION_PRODUCTIVA_MANIFIESTO_2026-11-03.json) identifica las seis imágenes, versiones, certificados, pruebas y respaldos sin incluir secretos ni datos personales. El manifiesto RC1 previo se conserva como referencia del artefacto anterior.

## PISEE y privacidad

Nodo real obtenido del runtime validado `/home/admin-ti/operacion-formulario/release-20261103/pisee`. Se conservaron PKI, X.509 institucional, MPGA y credenciales; no se regeneraron. La distribución original `/home/admin-ti/nodo-pisee/DDN_PROD/DDN` permanece intacta. Para evitar dos nodos activos con la misma identidad, se detuvo solamente el nodo de la demostración privada anterior `ddn-funcional-real-pisee-1`; su contenedor, estado y certificados se conservaron. Su función está asumida por el nodo definitivo. No se detuvieron las QA históricas ni certificación.

`PRODUCTION`, autorización institucional, base legal y finalidad `REGISTRO DE USUARIOS` habilitadas. `PISEE_RESTRICTED_CAMPAIGN=false`; sin `allowed-runs.json` obligatorio ni montaje de listas. Cliente Java → `http://pisee:8084` por red interna, endpoint `/srcei/informacionPersonalBasico`, servicio `InformacionPersonalBasicoDDN`, XML exacto `entradaSRCel` (U+006C final). Pin X.509: `3dc48ca30f6894d3321da10173af47960d5fe9afdbbd769696f1e6de678a64e0`; vigente hasta 28/09/2029.

Se verificó GESCOD real HTTP 200 y conectividad productiva. Se conserva la acreditación previa Firefox → React → Java → nodo → SRCeI con la identidad autorizada, normalización y ocultación de antecedentes. **No se repitió una consulta personal para este cierre.** No se presenta el ensayo sintético de intercambio como consulta SRCeI real.

El recorrido legítimo sigue exigiendo sesión propietaria, aviso/finalidad, CSRF, módulo 11, límites por sesión/IP, deduplicación, cancelación/respuestas tardías y timeout. Solo consulta el presentador; no hay consulta libre de terceros. La proyección ciudadana excluye valores registrales, solicita únicamente faltantes y conserva correcciones/procedencias/discrepancias en backend. Adulto declarado mantiene `autenticada=false`. Envío de datos registrales a recepción institucional cerrado.

## TLS y ClaveÚnica

Certificado de origen nuevo para **`formulario.defensorianinez.gob.cl` y `localhost`**, firmado por la CA de origen institucional ya existente, sin modificar los archivos del sitio público. Vigente hasta 03/11/2027. La autoridad copiada al destino definitivo permanece solo bajo root. Backend y MySQL usan su propia CA nueva, SAN y verificación de cadena/hostname; no se confunden ambas CA. Se comprobaron rechazo de CA no confiable y hostname incorrecto.

Credenciales CU productivas existentes instaladas en configtree privado. Botón oficial visible y Authorization Code/state/nonce apuntando a `accounts.claveunica.gob.cl`. Callback conservado **`https://formulario.defensorianinez.gob.cl/redirect`**, logout `/logout`. No se cambió el proveedor, callback público ni configuración de certificación.

Resultado real observado: **HTTP 200 y formulario oficial de acceso visible**, sin error de institución no habilitada en esa comprobación. No se ingresaron credenciales personales ni se acreditó Token/UserInfo real. El retorno público continúa llegando a certificación y no a la sesión privada: completar el login aquí necesita un URI independiente autorizado o el futuro cambio de origen aprobado. El código no requiere recompilar por esas habilitaciones. Las pruebas automatizadas de JWT/firma/issuer/audience/nonce, sesión, callback, replay, errores y logout son de proveedor controlado en tests, claramente diferenciadas del resultado real.

## Productor PULL/ACK e intercambio Windows

API real `/integracion/v3/formulario-completo/...`, versiones 1.0/1.1. `publico.integracion.preparada=true` verifica Bearer aun con recepción y recuperación cerradas; `habilitada=false` mantiene el intercambio cerrado hasta instalar confianza y autorización. El modo preparado conserva HTTPS obligatorio y no reserva ni confirma por sí mismo.

- Credencial técnica productiva nueva instalada en `secrets/integration-token`; copia de entrega en `delivery-private/integration-token` (0400/root). Independiente del token de operaciones y de campañas anteriores. Entregar ese archivo posteriormente mediante custodia/SSH autorizado, nunca en chat, URL, ticket o paquete público.
- Firmante Ed25519 independiente `ddn-formulario-prod-20261103-v1`, privada 0400 fuera de Git/imágenes. **Estado `PENDING_INSTITUTIONAL_APPROVAL`**: preparado y verificado criptográficamente, sin inventar aprobación institucional.
- Clave pública y confianza Formulario exportadas. Huella SHA-256 del PEM público `712fd54ba0676c6457901a9cc6f8eea7b30c4ff1eea1bba63ee055ba64948f92`.
- Almacén `keys/institutional-trust.json` vacío al cierre: no contiene la clave sintética utilizada en pruebas.
- Paquete público listo: `/home/admin-ti/operacion-formulario/intercambio-siger-produccion-20261103-v2`, con PEM, mapa de confianza, CA de origen, perfil 1.0/1.1 y `SHA256SUMS.json`; sin Bearer ni privadas.

Copia transportable del paquete: `/home/admin-ti/operacion-formulario/intercambio-siger-produccion-20261103-v2.zip`; SHA-256 en el manifiesto. Debe verificarse su huella por un canal institucional independiente antes de instalar confianza.

**Único material criptográfico que debe entregar Windows:** su clave pública ACK Ed25519 productiva y su identificador. La privada permanece en Windows. El consumidor debe instalarse en su VM definitiva, configurar almacenamiento/COM/endpoint/CA/Bearer/confianza del productor y verificar COMMIT antes del ACK. Ningún cambio de hostname/CA/credencial obliga a recompilar este Formulario.

Operación instalada (ejecutar con privilegios, sin imprimir propiedades):

```sh
sudo python3 /srv/ddn-formulario-produccion/release/scripts/ubuntu/productor.py --root /srv/ddn-formulario-produccion verify
sudo python3 /srv/ddn-formulario-produccion/release/scripts/ubuntu/productor.py --root /srv/ddn-formulario-produccion close
# Solo al recibir/verificar el mapa público ACK de Windows:
sudo python3 /srv/ddn-formulario-produccion/release/scripts/ubuntu/productor.py --root /srv/ddn-formulario-produccion install-ack --trust-file /custodia/ack-publico.json --sha256 HUELLA_VERIFICADA
```

El mapa JSON contiene `ID_ACK: PEM_PUBLICO`. La herramienta rechaza privadas, claves no Ed25519, huella incorrecta y reutilización de la identidad criptográfica del Formulario aunque se cambie el formato PEM. Instalar confianza deja admisión/PULL cerrados; su apertura es una acción separada.

Transporte privado futuro sin modificar DNS/hosts ni TLS global: SSH local autorizado `127.0.0.1:32443` Windows → `127.0.0.1:30443` Ubuntu, origen **`https://localhost:32443`**, SAN localhost y CA del paquete. No se abrió todavía un túnel Windows. Alternativa posterior: origen público institucional tras transición aprobada. En la ruta pública actual, integración responde **503 por el Caddy de certificación**, como debe: no se afirma transporte PULL/ACK a través de Cloudflare. La ruta HTTPS privada del productor sí transportó Bearer, versiones, JSON, binarios, Range/HEAD y ACK en el ensayo ejecutado. Prueba de borde externo pendiente del cambio de origen coordinado, sin alterar Cloudflare ahora.

### Próxima campaña física, ya implementada

No abrir primero recepción productiva para poder probar Windows. La herramienta siguiente crea una cola/esquema/custodia **exclusivamente sintéticos**, deshabilita PISEE durante esa campaña y exige confianza ACK real previamente instalada. No modifica la base primaria ni reutiliza U4. Debe ejecutarse en una ventana coordinada, usando un directorio NUEVO privado:

```sh
sudo python3 /srv/ddn-formulario-produccion/release/scripts/qa/probar-productor-instalado.py --root /srv/ddn-formulario-produccion --work /custodia/campana-windows-nueva --windows-handoff
# Deja dos Comunicaciones sintéticas, aceptación y PULL cerrados.
# Con Windows preparado y consentimiento de la ventana:
sudo python3 /srv/ddn-formulario-produccion/release/scripts/qa/probar-productor-instalado.py --root /srv/ddn-formulario-produccion --work /custodia/campana-windows-nueva --start-handoff-pull
# Comprobar físicamente COMMIT/ACK/COM, reinicio, duplicados y visualización en Windows.
sudo python3 /srv/ddn-formulario-produccion/release/scripts/qa/probar-productor-instalado.py --root /srv/ddn-formulario-produccion --work /custodia/campana-windows-nueva --finish-handoff
```

La preparación/inicio/cierre de campaña ya se ensayaron localmente con identidad ACK sintética temporal y posterior restitución del almacén vacío. Esto **no acredita E2E Windows**. `--finish-handoff` restaura configuración/base/custodia productivas y revoca permisos del esquema sintético; conservar evidencia y conciliar cualquier ACK ambiguo antes de cerrar una campaña real.

## Validaciones y recuperación ejecutadas

Resultados exactos en el manifiesto adjunto. Backend completo sin carga H2, contrato/seguridad/XML/privacidad; suite MySQL separada; React/lint/TypeScript/Vite y Python. Las omisiones opt-in existentes se identifican; no se reemplazan con afirmaciones de pruebas físicas.

| Suite | Resultado |
|---|---|
| React, lint y TypeScript/Vite | 219 tests; lint y build aprobados por el empaquetado. |
| Backend H2 | 401, cero fallos/errores, dos omisiones opt-in existentes. |
| Backend MySQL | 401, cero fallos/errores, mismas dos omisiones; MySQL nuevo, credenciales nuevas y TLS `VERIFY_IDENTITY`. Contenedor de la suite detenido al cierre, sin reutilizar QA histórica. |
| Python | 33, cero fallos, una omisión opt-in; incluye guardas de apertura/identidades/transición y sockets de respaldo. |
| Certificación preservada | 30/30, sin autenticación personal real. |

Pruebas Python: `python3 -m unittest discover -s scripts/qa -p 'test_*.py'`. El ensayo criptográfico requiere `python3-cryptography` (46.0.5 instalado/verificado en Ubuntu); las herramientas de operación ordinaria usan Python estándar, OpenSSL, Docker Compose y GPG. Estas dependencias de QA no incorporan claves a imágenes ni habilitan proveedores simulados en el runtime definitivo.

En el productor realmente instalado se ejecutó un ciclo controlado con dos Comunicaciones sintéticas, una 1.0 y otra 1.1 con adjunto FILE, en esquema y custodia independientes: idempotencia de envío, reserva, negociación, SHA canónico, Ed25519, AV/Inspector, descarga/SHA-256/HEAD/Range, COMMIT del consumidor local antes del ACK, repetición de ACK perdido y cero duplicados. Resultado: dos `CONFIRMADA`, `ack_pending=0`. Se restauró la configuración y confianza originales. No hubo SIGER Windows ni SRCeI personal en ese ensayo.

Respaldo cifrado AES256/GPG, SHA-256 externo y manifiesto interno de todos los archivos:

- `/srv/ddn-formulario-respaldos/productivo-preparado-completo-20261103.gpg`: SQL, custodia, PKI/claves/credenciales, runtime/estado PISEE, release, JAR e imágenes. Detuvo/reanudó solo escritores del proyecto nuevo. Descifrado completo a destino nuevo verificado, con ejecutable del nodo y propietarios MySQL/autoridad correctos.
- `/srv/ddn-formulario-respaldos/certificacion-previa-completa-20261103.gpg`: copia previa de certificación **en solo lectura**, sin detener/recrear/escribir sus servicios. Descifrado y hashes comprobados.
- Clave de cifrado separada en `/srv/ddn-formulario-custodia/backup-encryption-key` (0400/root). No forma parte de los archivos cifrados ni del intercambio Windows.
- Restauración SQL real en MySQL independiente, volumen nuevo, sin puertos: veinte tablas con checksums idénticos, firma recuperada igual y persistencia tras reinicio. Contenedor de restauración detenido al finalizar; evidencias privadas conservadas.
- Reinicio de los seis componentes definitivos: veinte tablas y firma sin cambios; todos saludables y recepción/PULL aún cerrados. Arranque systemd habilitado/probado; reboot del servidor compartido no ejecutado.

Correcciones surgidas de estas pruebas: excluir solamente sockets IPC efímeros del nodo del respaldo; conservar ejecución/modos seguros al extraer TAR; restaurar inicialización MySQL con uid999 y autoridades con root; separar CA de origen/backend. No se debilitó TLS ni se reutilizó una base histórica para conseguir el resultado.

Herramienta de respaldo/restauración instalada: `release/scripts/ubuntu/respaldo.py`; procedimiento ampliado en [operación RC1](RC1_OPERACION_RESPALDO_REVERSION_2026-11-03.md). Restaurar siempre a destino NUEVO; no iniciar otro nodo institucional simultáneo. Las copias externas al anfitrión, custodios, RPO/RTO y retención operativa requieren destino y aprobación institucional; no se afirma tolerancia a pérdida física del host por tener una copia local.

Firefox real verificó portada, acceso adulto declarado y NNA, escritorio/móvil sin desbordamiento ni errores JS, con TLS estricto. Sin consulta personal ni envío de Comunicación. Evidencia local privada: `definitivo-firefox-final.json`, `definitivo-portada.png`, `definitivo-movil.png` bajo `/home/admin-ti/operacion-formulario`.

## Transición y reversión listas, sin aplicar al origen público

Herramienta ejecutable `release/scripts/ubuntu/transicion.py`. Plan real preparado en `transition/plan.json`, ligado al hash del respaldo previo completo y a IDs/configuración/montajes/certificados del Caddy de certificación. `prepare` solo lee. Antes de una ventana futura, refrescar respaldo/plan si cambió el origen.

```sh
# Preparación de solo lectura; el respaldo indicado ya existe y fue verificado.
sudo python3 /srv/ddn-formulario-produccion/release/scripts/ubuntu/transicion.py --root /srv/ddn-formulario-produccion prepare --source-container ddn-formulario-institucional-proxy-1 --source-backup /srv/ddn-formulario-respaldos/certificacion-previa-completa-20261103.gpg
# SOLO tras autorización explícita de la transición y aprobación de sus requisitos:
sudo python3 /srv/ddn-formulario-produccion/release/scripts/ubuntu/transicion.py --root /srv/ddn-formulario-produccion apply --authorization /custodia/transicion-autorizada.json
# Si procede volver al origen anterior, cerrar antes admisión/PULL y conciliar:
sudo python3 /srv/ddn-formulario-produccion/release/scripts/ubuntu/transicion.py --root /srv/ddn-formulario-produccion rollback
```

El archivo de autorización exige `planSha256` exacto y `authorized`, `windowsPhysicalE2e`, `receiptSignerApproved`, `providerAndCallbackApproved` verdaderos. **No se creó una autorización aprobada.** `apply` detendría únicamente el Caddy anterior, trasladaría el Caddy nuevo de loopback30443 a24443 y comprobaría salud/TLS/Bearer; si falla revierte automáticamente. Mantiene base/app/volúmenes anteriores para volver; no toca túnel/DNS. Los controles siguen cerrados tras el cambio. La apertura posterior exige además `authorization/open.approved.json` con las aprobaciones definidas por `productor.py`; existe solo ejemplo con valores falsos.

Ensayo ya efectuado: origen HTTPS fixture en31443 → productor preparado → reversión explícita; después fallo de smoke inyectado solo en el arnés → reversión automática comprobada. `--test-only` rechaza siempre el proyecto de certificación y el puerto24443. No se aplicó el plan público. En la ventana futura verificar también callback/login real y transporte por el borde institucional; después la apertura CAS es una acción autorizada separada.

## Preservación y límites externos

Inventarios privados antes/después: `definitivo-antes-20261103.json` y `definitivo-despues-20261103.json`. **597 archivos institucionales idénticos; 25 contenedores históricos/certificación idénticos en ID, imagen, inicio, reinicios, montajes y configuración.** Comparación saneada `definitivo-preservacion-final.json`. Excepción explícita fuera de esos 25: nodo de la demostración privada anterior detenido para evitar duplicación de identidad PISEE.

Certificación 30/30 controles. Comprobación del dominio público con navegador: portada/API HTTP200, `certificationMode=true`, integración503; sin bypass TLS. La lectura Python del borde recibió rechazo HTTP, por lo que se verificó también con navegador real y TLS normal. No se modificaron Cloudflare, DNS, firewall, certificados/configuración públicos, main ni la rama de certificación.

Pendientes externos reales: VM SIGER y su identidad ACK pública, prueba física COMMIT/COM/ACK y persistencia/visualización; aprobación institucional del firmante nuevo; autenticación CU completa y aceptación/certificación del proveedor con retorno al destino definitivo; aprobación de avisos/seguridad/retención y apertura coordinada; custodia de respaldo fuera del host, responsables y RPO/RTO. El transporte del borde público se verificará tras autorizar el cambio de origen; hoy está cerrado intencionalmente. **Graph, correo y una cuenta de correo del Formulario no son requisitos ni bloqueos de este componente.** No queda una implementación paralela de API ni un simulador desplegado como solución productiva.
