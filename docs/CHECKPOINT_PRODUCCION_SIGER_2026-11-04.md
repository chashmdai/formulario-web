# Checkpoint operativo — RC1 público y continuidad con SIGER

> **Actualización posterior del 04/11:** [favicon desplegado y nueva imagen de aplicación](ACTUALIZACION_FAVICON_PRODUCCION_2026-11-04.md). Las referencias de artefactos del corte inicial que siguen se conservan como evidencia de publicación; para la imagen activa consultar esa actualización. Recepción habilitada, PULL cerrado y pendientes externos sin cambios.

Estado ejecutado el 04/11/2026, America/Santiago. **Comenzar aquí al retomar este mismo chat. No repetir el despliegue ni crear otro entorno.** Repositorio `chashmdai/formulario-web`, rama `release/formulario-produccion-2026-11`, referencia anterior `6d47760c`. Main y la rama histórica de certificación no se modificaron.

## Estado vigente

**Publicado:** `https://formulario.defensorianinez.gob.cl`. **Recepción habilitada**, protegida por contraseña temporal. Adultos con/sin CU, NNA definitivo y multimedia disponibles. SQL/custodia propios de `/srv/ddn-formulario-produccion`; no se usan las QA históricas ni U4.

**SIGER pendiente:** API PULL/ACK 1.0/1.1 publicada y Bearer operativo, sin contraseña ciudadana. Reservas/recuperación permanecen cerradas porque Windows aún no entregó una identidad ACK real. La recepción no depende de ese requisito. Las Comunicaciones aceptadas se guardan como `PENDIENTE` / `PENDIENTE_REGISTRO_SIGER`, con comprobante de custodia firmado, sin COM ni confirmación inventados.

Se registró la autorización institucional expresamente confirmada por el operador en este chat para publicar y recibir. Documento privado `authorization/open.approved.json`, esquema `RC1_PUBLICATION_AUTHORIZATION_V2`. Autoriza publicación, recepción y uso de la identidad/avisos/callback existentes. **`windowsReady=false`, `physicalE2eVerified=false`, autenticación CU completa no acreditada.** No se convirtió esa autorización en una certificación externa ficticia.

## Instalación y artefactos

| Elemento | Valor efectivo |
|---|---|
| Raíz/proyecto | `/srv/ddn-formulario-produccion` / `ddn-formulario-produccion` |
| Origen de Cloudflare | HTTPS `127.0.0.1:24443`, mismo túnel/ruta/dominio anteriores, sin cambios de DNS/firewall |
| Fuentes compiladas | `b6f639a5ef14f81dd393295b9ea75e37ffe76746` |
| Imagen Formulario | `sha256:8bf75004aa68c3ebdb5fe3f22b18296f85a66c3e5a1653a40ae9745d9ce5ce08` |
| JAR SHA-256 | `830de16985eb776381eb6ee13f619043ccf07c57fb0021c2cc7745d06a7bb4a5` |
| Archivo de seis imágenes | `software/images-publicacion-20261104.tar`, SHA-256 `427d9e9a90cad66484763703151bff8531dd68997e5283411d44fe3ec179c823` |
| Datos | Volumen MySQL propio, esquema `formulario_publico`, V1–V12 conservadas; `store` privado persistente |
| Configuración | `compose.env`, `config/application.properties`, `installation.json`, `compose.origin-ca.yml`, fuera de Git |
| Compose instalado | `release/infra/ubuntu/compose.yml` + `compose.production.yml` + overlay privado de CA de origen |
| Red PISEE | `10.203.74.0/29`, backend `.3`, nodo `.2`, autorización IP exacta |
| Configuración PISEE montada | `pisee/config-produccion.json` → `/var/lib/pisee/config.json`, RO; la configuración original de `pisee/runtime` permanece conservada |

Durabilidad MySQL verificada: `innodb_flush_log_at_trx_commit=1`, `sync_binlog=1`, `innodb_doublewrite=ON`, `log_bin=ON`.

Seis servicios saludables: app, MySQL, Caddy, ClamAV, Inspector y PISEE. Imágenes restantes/versiones/SHAs, evidencia pública y comparación antes/después en [manifiesto](PUBLICACION_RC1_MANIFIESTO_2026-11-04.json). No se construye al arrancar. El código operativo posterior al commit compilado no cambia el JAR.

La URL privada anterior `localhost:30443` dejó de ser el origen activo. Administración local ahora usa loopback24443 con CA/hostname verificados; operación y ciudadanía utilizan la misma instalación. No hay puertos host de backend, MySQL, AV/Inspector, 8084 ni8489.

## Propiedades y controles actuales

```properties
siger.certificacion-claveunica=false
formulario.habilitado=true
formulario.adultos.habilitado=true
formulario.recepcion.habilitada=true
publico.recepcion.habilitada=true
publico.recepcion.aviso-validado=true
publico.recepcion.custodia-durable-autorizada=true
publico.integracion.preparada=true
publico.integracion.habilitada=false
formulario.multimedia.habilitada=true
formulario.srcei.envio-autorizado=true
publico.nna.habilitado=false
ciclo-vida.online-habilitado=false
ciclo-vida.eliminacion.habilitada=false
capacidad.minimo-libre=16GB
formulario.multimedia.minimo-libre=16GB
formulario.multimedia.cuota-global=5GB
```

CAS persistido: **`aceptarNuevos=true`, `recuperar=false`, `contingencia=NORMAL`**. `publico.integracion.habilitada=false` significa que aún no se habilitó confianza/recuperación institucional; `preparada=true` mantiene rutas y verificación Bearer. No significa recepción cerrada.

`publico.nna.habilitado` corresponde exclusivamente al prototipo `/api/nna`, restringido a Sandbox por su guardia original. Se mantiene apagado. **NNA productivo usa `/api/formulario`**, está habilitado y fue comprobado desde la interfaz pública. No se relajó la guardia Sandbox para publicar un prototipo.

La recepción y la recuperación tienen permisos CAS separados. Sin clave ACK, incluso una solicitud operacional de abrir recuperación se rechaza. No hay retirada automática; la ausencia de Windows no elimina ni confirma datos. Los límites de capacidad impiden nuevos ingresos antes de agotar el disco. Retención diaria de respaldos sin borrados automáticos; vigilar espacio hasta definir custodia externa/retención operativa.

## Contraseña y continuidad al vencer

Se conserva la contraseña existente en `secrets/acceso-temporal-password`, 0400/root, únicamente en el anfitrión. El backend recibe solo el hash montado RO. Rotación mediante `release/scripts/ubuntu/acceso-temporal.py --root /srv/ddn-formulario-produccion rotate`, sin recompilar/reiniciar y revocando permisos temporales anteriores. No imprimir el valor en chat ni logs.

**Vence el 15/12/2026 a las 00:00 America/Santiago (03:00 UTC).** El mismo servidor deja de exigirla automáticamente, sin otro despliegue. La autorización V2 ya permite continuar recepción aunque el E2E Windows siga pendiente. Se conservan sesión ciudadana, CSRF, validaciones, PISEE, documentos y estados; el reloj no modifica Comunicaciones ni inventa registros SIGER. Un cierre operativo, falta de capacidad o autorización revocada sigue cerrando la recepción. Las verificaciones CLI también contemplan el período posterior al vencimiento.

## API institucional lista para Windows

Base: **`https://formulario.defensorianinez.gob.cl/integracion/v3/formulario-completo/entregas`**.

| Operación existente | Método/ruta relativa |
|---|---|
| Reserva PULL | `POST /reclamar`, cuerpo `{"limite":1}` y `X-Formulario-Versiones: 1.0,1.1` |
| Original | `GET` / `HEAD /{id}/adjuntos/{adjunto}`, cabecera `X-Reserva` |
| ACK | `POST /{id}/confirmacion`, reserva + `RegistroInstitucional` firmado |
| Reintento/fallo | `POST /{id}/fallo`, reserva + código contractual |

Bearer nuevo ya provisionado anteriormente: `secrets/integration-token`, copia de entrega `delivery-private/integration-token`, custodia root. **No cambió.** Entregar luego mediante canal seguro; no está en el paquete público y el consumidor no necesita la contraseña temporal.

Firmante Formulario conservado: **`ddn-formulario-prod-20261103-v1`**. PEM público SHA-256 `712fd54ba0676c6457901a9cc6f8eea7b30c4ff1eea1bba63ee055ba64948f92`. Su uso para recepción protegida quedó autorizado por la orden actual; el estado de aprobación formal anterior se conserva y no se declara una firma cualificada nueva.

Material público preparado: `/home/admin-ti/operacion-formulario/intercambio-siger-publico-20261104.zip`, SHA-256 **`1456fdf2c1c6be2e74cb71733c92ad33bf9376b4aabd7c962a100c604d0977ab`**. Contiene clave pública/mapa de confianza Formulario, perfil, CA pública y checksums; no contiene Bearer, contraseñas ni privadas.

La conexión pública usa el certificado de borde Cloudflare, verificado con nombre y cadena: **GTS Root R4**, PEM SHA-256 `7e8b80d078d3dd77d3ed2108dd2b33412c12d7d72cb0965741c70708691776a2`. `public-edge-root-ca.crt` es la confianza preparada para el dominio público. `origin-ca.crt` sirve solo para conexión directa al origen/SSH; no confundirlas. Revalidar cadena/hostname al instalar Windows. Host, CA, credencial y endpoints se configuran externamente, sin recompilar.

**Único material criptográfico pendiente que debe proporcionar Windows:** clave pública ACK Ed25519 productiva y su identificador. La clave privada se genera/custodia en Windows y no se entrega a Ubuntu. `keys/institutional-trust.json` continúa vacío. No se instaló una identidad ACK sintética.

## Siguiente intervención en este chat — no repetir publicación

1. Leer este checkpoint, Git/AGENTS y `installation.json`; inventariar cola/controles/servicios sin cuerpos ni RUN. Conservar comunicaciones recibidas, claves e imágenes. No reutilizar U4/WSL/QA Windows ni el puerto25443.
2. Con VM SIGER institucional preparada y destino de datos autorizado, intercambiar el paquete público y verificar huellas por canal independiente. Configurar consumidor existente, almacenamiento definitivo, Bearer, URL HTTPS y confianza pública del productor.
3. Recibir el mapa JSON `ID_ACK: PEM_PUBLICO_ED25519` y verificar SHA-256 acordado. Cerrar admisión/PULL durante la importación/reinicio controlado. No reclamar para probar autenticación.

```sh
sudo python3 /srv/ddn-formulario-produccion/release/scripts/ubuntu/productor.py --root /srv/ddn-formulario-produccion close
sudo python3 /srv/ddn-formulario-produccion/release/scripts/ubuntu/productor.py --root /srv/ddn-formulario-produccion install-ack --trust-file /custodia/ack-publico-verificado.json --sha256 HUELLA_ACORDADA
```

4. La importación valida Ed25519, ID y huella; rechaza privadas y reutilización de la identidad del Formulario incluso con otro PEM. Activa configuración de confianza sin recompilar. Reabrir recepción con `open --accept-new`; la recuperación continúa cerrada hasta iniciar la ventana coordinada.
5. Verificar HTTPS y Bearer: ausente401, falso401 y válido404 sobre recurso inexistente. Iniciar consumidor Windows con cola/destino autorizados y después habilitar recuperación mediante `open --accept-new --pull`. No exigir que el E2E ya esté terminado para poder ejecutar su primer ciclo.
6. Verificar físicamente documentos 1.0/1.1, identidad/procedencias, hashes canónicos, originales SHA-256, firma Formulario, COMMIT institucional previo al ACK Ed25519, folio COM, ACK ambiguo/reintentos, idempotencia, cero duplicados y persistencia después de reiniciar Windows. **Hasta hacerlo, `physicalE2eVerified` sigue false.** No confundir fixtures automáticos con una recepción institucional real.

Todo el código productor está instalado. No hace falta otro despliegue para importar la clave ACK/configurar Windows. Sí corresponde una breve ventana operativa para releer confianza externa y verificar el circuito real.

## Respaldo, arranque y reversión

`ddn-formulario-produccion.service` habilitado; Docker `unless-stopped`, healthchecks y límites conservados. `ddn-formulario-produccion-backup.timer` habilitado: 03:15 America/Santiago con hasta cinco minutos aleatorios. Su primera ejecución se probó con admisión activa **en la instalación definitiva antes del cambio público**: cierre CAS → SQL/documentos/PKI/software cifrados → GPG/integridad y SHA verificados → servicios saludables → restauración CAS exacta del estado anterior. No sobreescribe un cambio de revisión del operador.

La tarea detiene brevemente solo escritores de este proyecto para una copia consistente; durante la ventana puede interrumpirse navegación. Las sesiones/borradores HTTP no sobreviven al reinicio del backend; las Comunicaciones guardadas duraderamente en SQL y sus originales sí permanecen. Si falla la copia o hay capacidad insuficiente, conserva cierre de admisión y datos para intervención. No elimina respaldos ni volúmenes. Clave GPG separada en `/srv/ddn-formulario-custodia/backup-encryption-key`; logs privados sin SQL/cuerpos. Copia fuera del anfitrión: todavía no hay destino institucional suministrado; no se declara protección ante pérdida física del host solo por conservar copias locales.

Última copia probada: `/srv/ddn-formulario-respaldos/productivo-diario/formulario-20261104T131015Z.gpg`, SHA-256 `37ae114e64ea8185a590a9d1aa923fdc4f2d02d3ec5dafb400617ce4e5ad8192`. También están `rc1-antes-publicacion-20261104.gpg` y `certificacion-previa-publicacion-20261104.gpg`; SHAs en manifiesto. El procedimiento de restauración previamente probado valida cifrado, hashes, SQL, roles/permisos y firma en un destino nuevo. Nunca restaurar sobre la base productiva existente.

La transición conservó íntegros los archivos/configuración/volúmenes de certificación. Solo detuvo su Caddy al pasar el nuevo origen de30443 a24443. Se deshabilitó el autoarranque de la unidad anterior para evitar que reapropie24443 tras reboot; el contenedor anterior usa `unless-stopped`. No se cambió ni reinició cloudflared.

Plan aplicado en `transition/plan.json`, autorización ligada a su SHA en `authorization/transition-approved-20261104.json`. Reversión normal: cerrar controles y ejecutar `transicion.py --root /srv/ddn-formulario-produccion rollback`. **Emergencia con backend caído:** `emergency-rollback` congela el escritor, deshabilita recepción en su configuración, repone el Caddy anterior y su autoarranque sin esperar a que el backend privado esté saludable. Conserva MySQL/custodia/nuevas Comunicaciones; no restaura una SQL antigua ni borra volúmenes. Antes de volver a publicar, conciliar y verificar el estado persistido.

## Evidencia y límites de lo acreditado

Dominio público real, TLS del sistema y Cloudflare verificados por cliente HTTPS técnico y Firefox. 401 anónimo; contraseña incorrecta401; correcta permite acceder. Cookies Secure/HttpOnly/Lax; adulto declarado sigue sin autenticación CU. Capacidades reales `nna=true, adultos=true, multimedia=true`; NNA/adulto visibles y móvil sin overflow. Bearer público sin cookies/contraseña temporal: **401/401/404**. No se hizo PULL real ni se conectó Windows.

CU: redirección oficial, state/nonce y callback del dominio correcto, formulario del proveedor observado, sin rechazo institucional observado en esa comprobación. No se ingresaron credenciales personales ni se acreditó un login completo. PISEE: TCP Java→nodo real y GESCOD JSON no personal correctos; conserva consulta SRCeI real acreditada anteriormente y XML `entradaSRCel` U+006C. No se repitió una consulta personal. ClamAV detectó EICAR técnico por stream, sin adjunto/Comunicación de prueba.

Pruebas de software: 423 backend, cero fallos/errores, dos opt-in existentes omitidos; 38 Python, una omisión opt-in; frontend sin cambios, 219 tests/lint/TypeScript/Vite del empaquetado validado. Casos nuevos prueban custodia firmada/idempotente sin ACK, lectura posterior, recuperación solo al importar confianza y continuidad al caducar sin exigir un Windows ficticiamente listo. Fixtures solo en tests, nunca simulador en producción.

Al verificar: **cero Comunicaciones creadas por Codex**, cola productiva vacía; solo navegación y borradores sin identidad para probar las páginas. 597 archivos institucionales idénticos, 34 contenedores históricos intactos excluyendo el Caddy anterior detenido autorizadamente, cloudflared sin cambio, claves/certificados/CU/Bearer existentes idénticos. Main `7189a95` y rama de certificación `76d0b97` conservadas. Los contratos y firmas históricas no se reescribieron. Graph/correo siguen siendo responsabilidad futura de SIGER.
