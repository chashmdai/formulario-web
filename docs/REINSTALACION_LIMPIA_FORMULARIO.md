# Reinstalación limpia del Formulario — procedimiento único

15/11/2026, America/Santiago. **`/srv/formulario` es la instalación definitiva del Formulario.** Este documento es el procedimiento vigente para crearla desde la instalación anterior y para operarla después (instalación, actualización, respaldo y recuperación). Sustituye, para la instalación nueva, a `scripts/ubuntu/*` y a los Compose `infra/ubuntu/compose*.yml` (que siguen sirviendo solo a la instalación anterior mientras exista). **No ejecutado en producción.** La ejecución requiere autorización del operador y una ventana coordinada con SIGER.

## 1. Qué cambia

| Antes (`/srv/ddn-formulario-produccion`) | Ahora (`/srv/formulario`) |
|---|---|
| 3 Compose (`compose.yml` + `compose.production.yml` + `compose.origin-ca.yml` generado fuera del repositorio) | **1 Compose** versionado, `infra/formulario/compose.yml`, copiado a `RAIZ/infra/` |
| `preparar.py`, `productor.py`, `respaldo*.py`, `transicion.py`, `acceso-temporal.py`, … | **1 herramienta**, `scripts/formulario/formulario.py` (solo biblioteca estándar), copiada a `RAIZ/bin/` |
| `installation.json`, `compose.env`, `release/`, `software/`, `updates/` | `instalacion.json`, `formulario.env`, `bin/`, `infra/` (generados) |
| Red `salida` con subred automática: `FORM_TRUSTED_PROXIES` se ajustaba a mano | Subred **fija**: la puerta de enlace por la que entra cloudflared se calcula y se verifica |
| Unidad systemd que hace `compose up` | Unidad que ejecuta `formulario.py arrancar`, que **rechaza** iniciar un segundo nodo PISEE con la misma identidad |

Se conservan sin cambios las rutas **dentro** de los contenedores (`/run/keys`, `/run/pki`, `/run/secrets/…`, `/var/lib/formulario`, …), por lo que `config/application.properties`, la firma Ed25519, la confianza histórica, los certificados, ClaveÚnica, PISEE, las credenciales y la **contraseña temporal entregada a Gobierno Digital** (archivo, hash `pbkdf2-v5_8:`, aprobación, `limites.json` y vencimiento) funcionan sin tocarse.

### Layout

```
/srv/formulario/
  instalacion.json formulario.env bin/formulario.py infra/{compose.yml,Caddyfile,mysql.cnf,clamd.conf,validacion.yml}
  config/ secrets/ keys/ pki/{app,mysql,proxy,authority,…} cu/ mysql/{init,root.cnf,health.cnf}
  store/ acceso-temporal/ pisee/{runtime,state,config.json} clamav/ ipc/ registro/{origen-manifiesto.json,legado/}
  volumen Docker formulario_mysql-data
```

`registro/legado/` guarda, sin uso operativo, `installation.json`, `compose.env`, `compose.origin-ca.yml`, `authorization/`, `transition/`, `intercambio-publico/` y `delivery-private/` de la instalación anterior. `release/`, `software/` y `updates/` no se migran: permanecen en la instalación anterior, que no se borra.

## 2. Cómo se migra sin depender de lo histórico

1. **Inventario de lo real.** `formulario.py plan` lee `installation.json` y `compose.env` del origen y los montajes efectivos de sus contenedores (`docker inspect`). Cada montaje se clasifica como *preservar* (ruta nueva), *release* (lo aporta el paquete) o *efímero*. **Un montaje no clasificado bloquea.** Un archivo montado dentro de un directorio clasificado (p. ej. la CA de origen del overlay) se conserva en su ruta anidada: la instalación nueva ve exactamente lo que veían los contenedores.
2. **Respaldo = migración.** El respaldo del origen se escribe ya con el layout nuevo (TAR → gzip → GPG AES256 en streaming, sin copia en claro), con manifiesto SHA-256/modo/propietario por archivo, volcado MySQL, conteos por tabla, versión Flyway e identidad PISEE (SHA-256 del certificado institucional). Se verifica completo (descifrado, integridad GPG, manifiesto) antes de darlo por bueno.
3. **Restaurar** crea la raíz nueva desde el respaldo (propietarios y modos del manifiesto), escribe `infra/`, `bin/` y `formulario.env`, ajusta **solo** `ipPermitidas` de la configuración PISEE a la nueva IP del backend, crea una MySQL nueva, importa el volcado, exige conteos idénticos y aplica las migraciones nuevas (V13).
4. **Validación cerrada**: la instalación nueva arranca en un puerto alternativo **sin nodo PISEE** y con recepción, integración, PISEE, ciclo de vida y minimización cerrados por variables de entorno. `verificar` comprueba hashes y propietarios del material preservado, custodia completa, conteos ≥ origen, Flyway sin fallos, salud de servicios y la puerta de enlace de `salida`.
5. **Producción**: se inicia el nodo PISEE solo si **ningún otro contenedor activo** monta el mismo certificado institucional ni declara la misma identidad. Los controles CAS de la base son los del origen al cierre: la instalación nueva queda abierta o cerrada exactamente como estaba la anterior.

## 3. Espacio (límite de 40 GiB)

`plan` estima: material preservado + 3× tamaño de la base (volumen MySQL y volcado) + imagen nueva + 2 GiB de margen, y si el respaldo queda en el mismo disco (`--respaldo-local`) suma su tamaño. **Bloquea** si no cabe en `min(libre, --limite-gib 40)`. El respaldo debe ir preferentemente a un medio externo (`--respaldos`). La instalación anterior no se borra para liberar espacio.

## 4. Procedimiento (como root en DDN-UBUNTU, desde el paquete verificado)

Paquete: `scripts/linux/empaquetar-release.py` en el equipo de build, `sha256sum -c SHA256SUMS` en la VM. `F=$PKG/fuente/scripts/formulario/formulario.py`, `K=/srv/ddn-formulario-custodia/backup-encryption-key`, `E=<medio externo>`.

1. **Plan (solo lectura):**
   ```bash
   python3 "$F" plan --legado /srv/ddn-formulario-produccion --destino /srv/formulario --limite-gib 40
   ```
   Sin `bloqueos`. Revisar `montajes`, `noMigradoPermaneceEnOrigen`, `baseDatos` y `piseeIdentidad`.
2. **Respaldo previo verificado y externo** (detiene unos segundos proxy, app y PISEE):
   ```bash
   python3 "$F" respaldar --legado /srv/ddn-formulario-produccion --salida "$E/formulario-pre-reinstalacion.gpg" --clave "$K" --mantenimiento
   ```
3. **Ensayo de restauración** (raíz y proyecto propios, puerto y subredes distintos; el origen sigue en servicio):
   ```bash
   python3 "$F" restaurar --archivo "$E/formulario-pre-reinstalacion.gpg" --clave "$K" --raiz /srv/formulario-ensayo \
     --proyecto formulario-ensayo --puerto 30445 --puerto-validacion 30446 \
     --subred-salida 10.203.77.0/28 --subred-pisee 10.203.78.0/29 --imagen-app <imagen nueva>
   python3 "$F" arrancar --raiz /srv/formulario-ensayo --validacion
   python3 "$F" verificar --raiz /srv/formulario-ensayo
   ```
   Con la contraseña temporal vigente debe abrirse `https://localhost:30446/acceso-temporal` (sin publicar nada). `arrancar` sin `--validacion` **debe rechazarse** mientras el nodo PISEE anterior esté activo. Después: `formulario.py detener`, `docker compose -p formulario-ensayo down` y `docker volume rm formulario-ensayo_mysql-data` (solo recursos de ese proyecto), `rm -rf /srv/formulario-ensayo`.
4. **Transición** (ventana; la interrupción dura el respaldo final, la restauración y dos arranques):
   ```bash
   python3 "$F" transicion --legado /srv/ddn-formulario-produccion --clave "$K" --respaldos "$E" \
     --raiz /srv/formulario --imagen-app <imagen nueva>
   ```
   Pasos que ejecuta: detiene proxy, app y PISEE del origen → respaldo final verificado → detiene el resto del origen (los contenedores se conservan) y **deshabilita** `ddn-formulario-produccion.service` → restaura en `/srv/formulario` → validación cerrada en el puerto alternativo → producción en el **mismo puerto público** (por defecto el del origen, 30443) con nodo PISEE único → `verificar` → instala y habilita `formulario.service`. **Cloudflare/cloudflared no cambian**: siguen apuntando a `127.0.0.1:30443`. Ante cualquier fallo revierte solo: detiene lo nuevo, reanuda los contenedores del origen y rehabilita su unidad.
5. **Aceptación**: `verificar` sin problemas, contraseña temporal operativa por el dominio público, recorridos y comprobantes visibles, SIGER informado. Hasta reabrir recepción ciudadana en la nueva, la reversión es sin pérdida.

## 5. Reversión

```bash
python3 /srv/formulario/bin/formulario.py revertir --legado /srv/ddn-formulario-produccion --raiz /srv/formulario
```
Detiene la instalación nueva, deshabilita su unidad, reanuda el origen intacto y rehabilita su unidad. **Se rechaza** si la nueva tiene comunicaciones posteriores a la transición: primero `respaldar --raiz /srv/formulario …` y pasar `--clave K --respaldo-nuevo <archivo>`; esas comunicaciones deben conciliarse antes de reabrir el origen (no se pierden: quedan en ese respaldo y en `/srv/formulario`, que no se borra).

## 6. Operación posterior (instalación nueva)

| Tarea | Comando |
|---|---|
| Arranque (systemd) | `formulario.py arrancar --raiz /srv/formulario` |
| Parada | `formulario.py detener --raiz /srv/formulario` (no borra nada) |
| Verificación | `formulario.py verificar --raiz /srv/formulario` |
| Respaldo | `formulario.py respaldar --raiz /srv/formulario --salida <externo>/f.gpg --clave K --mantenimiento` |
| Verificar respaldo | `formulario.py verificar-respaldo --archivo <f.gpg> --clave K` |
| Recuperación | `formulario.py restaurar --archivo <f.gpg> --clave K --raiz <raíz NUEVA> --imagen-app <imagen>` + `arrancar` (con el origen dañado detenido) |
| Actualización | desde el paquete nuevo: `formulario.py actualizar --raiz /srv/formulario --imagen-app <imagen> --clave K --respaldos <externo>` (respaldo verificado previo, migración, recreación de app/proxy, verificación; vuelve a la imagen anterior si falla) |

El respaldo de la instalación nueva usa el mismo formato y la misma restauración que la migración: la recuperación queda ensayada cada vez que se migra o se prueba un respaldo.

## 7. Invariantes y límites

- Un solo nodo PISEE activo por identidad (comprobado por certificado montado y por etiqueta, también en cada arranque por systemd).
- La instalación anterior nunca se borra: contenedores detenidos, unidad deshabilitada, árbol intacto (comprobado por hash en LAB).
- Sin pérdida de datos: el respaldo final se toma con escritores detenidos; la reversión se bloquea si hay datos nuevos sin respaldar.
- No cambia: hostname, puerto público, callback de ClaveÚnica, certificados, firma, confianza ACK, token de integración, contraseña temporal ni su vencimiento (15/12/2026).
- Cambia: subredes Docker (nuevas, verificadas sin superposición) y, en consecuencia, la IP permitida del backend en la configuración local del nodo PISEE. Si TI tuviera reglas de firewall atadas a las IP Docker antiguas, revisarlas.
- `--overlay` e `--ignorar-montaje` son solo para el laboratorio (stubs); nunca en producción.
- Lo validado en LAB no acredita producción: ver los requisitos pendientes de la sección 8.

## 8. Requisitos pendientes de producción (bloquean la transición)

Cada punto se comprueba en DDN-UBUNTU **antes** de `transicion` y se registra (hashes y contadores, sin secretos). Ninguno está acreditado todavía.

| # | Requisito | Cómo se acredita | Bloquea si |
|---|---|---|---|
| R1 | **Respaldo externo verificado** | `respaldar --legado … --salida <medio externo> --mantenimiento`, luego `verificar-respaldo` sobre la copia **en el medio externo** y comparación de su SHA-256 con el `.sha256`. La clave `K` se custodia aparte, nunca junto a la copia. | No existe copia externa verificable, o la clave no está disponible fuera de la VM |
| R2 | **Espacio disponible** | `plan --limite-gib 40` sin bloqueo; `df -h /srv /var/lib/docker` y `docker system df` registrados. El respaldo va al medio externo (sin `--respaldo-local`). | `bytesRequeridos` > mín(libre, 40 GiB); no se libera espacio borrando respaldos, imágenes ni la instalación anterior |
| R3 | **Identidad PISEE** | `plan.piseeIdentidad` = SHA-256 del certificado institucional en uso; `ensayo` con `arrancar` productivo **rechazado** mientras el nodo anterior está activo; tras la transición, un único contenedor PISEE activo. Confirmar con TI que la IP permitida del backend en la configuración local del nodo puede cambiar (red nueva) y que no hay registros externos atados a la IP Docker antigua. | Otro nodo con la misma identidad activo, o una dependencia externa de la IP antigua |
| R4 | **Subredes** | Elegir con TI `--subred-salida` (por defecto 10.203.76.0/28) y `--subred-pisee` (por defecto 10.203.75.0/29) sin colisión con redes Docker, rutas del anfitrión, VPN ni GATE4A/orquestador; la herramienta rechaza superposiciones con redes Docker existentes. `verificar` confirma que `FORM_TRUSTED_PROXIES` es la puerta de enlace real de `salida` (por la que entra cloudflared). | Colisión, o la IP real de la ciudadanía no llega al backend (comprobar con una solicitud real por el dominio) |
| R5 | **systemd** | Antes: `systemctl cat ddn-formulario-produccion.service` y estado registrados. `transicion` lo deshabilita (no lo borra) e instala `/etc/systemd/system/formulario.service`. Acreditar: `systemctl is-enabled` (anterior `disabled`, nueva `enabled`) y, en una ventana autorizada, que un reinicio del anfitrión levanta **solo** la nueva. | Ambas unidades habilitadas, o el arranque por systemd falla |
| R6 | **Componentes reales** | Ensayo (paso 3 del procedimiento) con las imágenes y el runtime institucionales: nodo PISEE real (salud local, sin consultas personales), ClamAV con firmas vigentes (prueba EICAR), inspector con aislamiento `LANDLOCK3_SECCOMP`, ClaveÚnica (botón y redirección a `accounts.claveunica.gob.cl` sin ingresar credenciales), contraseña temporal por el puerto alternativo, `compose.origin-ca.yml` real reflejado en `plan.montajes`. Tras la transición: acceso por el dominio público vía cloudflared **sin cambiar su configuración**. | Cualquier componente no saludable o un montaje no clasificado |
| R7 | **Ventana y SIGER** | Autorización del operador, aviso a SIGER (PULL efectivo cerrado sin clave ACK) y responsable de la reversión. | Sin autorización |

## 9. Pasos mínimos de instalación de `/srv/formulario`

1. Equipo de build: `python3 scripts/linux/empaquetar-release.py --commit <SHA con CI verde> --destino <externo>/release-<sha12>`; copiar el paquete a DDN-UBUNTU y `sha256sum -c SHA256SUMS`; `gunzip -c imagen-app.tar.gz | docker load`.
2. `F=<paquete>/fuente/scripts/formulario/formulario.py`; `python3 "$F" plan --legado /srv/ddn-formulario-produccion --destino /srv/formulario` → sin bloqueos (R2, R3, R6 parcial).
3. `python3 "$F" respaldar --legado /srv/ddn-formulario-produccion --salida <externo>/pre.gpg --clave "$K" --mantenimiento` y verificación de la copia externa (R1).
4. Ensayo en `/srv/formulario-ensayo` (sección 4, paso 3) con componentes reales (R3, R4, R6); eliminarlo después solo con recursos de su proyecto.
5. Ventana autorizada (R7): `python3 "$F" transicion --legado /srv/ddn-formulario-produccion --clave "$K" --respaldos <externo> --raiz /srv/formulario --imagen-app <ID de la imagen cargada>` (subredes acordadas en R4).
6. Aceptación: `python3 /srv/formulario/bin/formulario.py verificar --raiz /srv/formulario`, unidades (R5), dominio público y contraseña temporal. Ante cualquier duda: `revertir`.

## Anexo: validación en LAB (15/11/2026)

`scripts/qa/lab_migracion.py` en el equipo de laboratorio CachyOS, como root solo dentro de un contenedor de herramientas efímero; **evidencia local, no acreditación productiva**. Origen emulado con los Compose históricos reales (`infra/ubuntu/compose.yml` + `compose.production.yml`) más un overlay de CA de origen, la aplicación de la versión anterior (`5b17003`, esquema V12), identidad PISEE con certificado sintético, contraseña temporal activa (hash `pbkdf2-v5_8:`), credenciales CU sintéticas y datos sintéticos (3 recorridos, 1 entrega, 3 objetos en custodia). Nodo PISEE, ClamAV e inspector simulados por `infra/formulario/lab/stub.py`.

| Comprobación | Resultado |
|---|---|
| `plan` | 0 bloqueos; 56 archivos preservados; requeridos ≈ 2,8 GiB ≤ 40 GiB |
| Montaje no clasificado (stubs del LAB sin `--ignorar-montaje`) | **bloquea** (comprobado) |
| Respaldo del origen | verificado (descifrado, GPG, manifiesto) |
| Ensayo: restauración + validación cerrada en puerto alternativo | `verificar` sin problemas; contraseña temporal → 303 |
| Arranque productivo del ensayo con el nodo anterior activo | **rechazado**: «Otro nodo PISEE activo con la misma identidad» |
| Transición | respaldo final → origen detenido → restauración (Flyway 12 → 13, conteos iguales) → validación → producción en el mismo puerto |
| Instalación nueva | 6 servicios sanos; puerta de `salida` 10.203.76.1 = `FORM_TRUSTED_PROXIES`; CA de origen del overlay presente con el mismo SHA; PISEE `ipPermitidas` = backend nuevo, resto de la configuración intacto; contraseña temporal → 303 |
| Origen tras la transición | 6 contenedores conservados, 0 activos, árbol idéntico por hash |
| Reversión sin datos nuevos | origen reanudado en el mismo puerto; contraseña temporal → 303 |
| Reversión con una entrega nueva sin respaldar | **rechazada**; permitida tras `respaldar --raiz` y `--respaldo-nuevo` |
| `actualizar` | respaldo previo verificado, migración, recreación y `verificar` correctos |
| Final | origen operativo e intacto por hash; recursos del LAB eliminados por etiqueta de proyecto |

Defectos encontrados y corregidos durante el ensayo: la salud del Compose nuevo usaba `/api/session`, que no responde sin acceso cuando la contraseña temporal está activa; ahora usa `FORM_HEALTH_PATH` heredado del origen (`/acceso-temporal/salud`). Además, un error de lectura al verificar que los archivos no cambian durante el respaldo y el pipe de `stderr` de GPG sin leer.

No acreditado en LAB: unidades systemd (el contenedor de herramientas no tiene `systemctl`: la herramienta las informa como `omitido`), el nodo PISEE, ClamAV e inspector institucionales reales, cloudflared y el `compose.origin-ca.yml` real de producción. El `plan` en DDN-UBUNTU es el primer paso obligatorio para verificarlo.
