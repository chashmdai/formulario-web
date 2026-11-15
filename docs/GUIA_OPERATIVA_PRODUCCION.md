# Guía operativa asistida — reinstalación de `/srv/formulario` en DDN-UBUNTU

Base: commit **`3d0b54c32f8638674efa9c0ccbe5f05615c439a3`** (`scripts/formulario/formulario.py`, `infra/formulario/compose.yml`) y [REINSTALACION_LIMPIA_FORMULARIO.md](REINSTALACION_LIMPIA_FORMULARIO.md). Esta guía **no autoriza** ninguna operación: cada etapa se ejecuta solo cuando el operador la confirma, y la transición solo con R1–R7 acreditados y autorización expresa (R7).

## 0. Reglas para quien asiste (ChatGPT u otra persona)

1. **Una etapa por vez.** Mostrar los comandos de la etapa, esperar que el operador pegue la salida, compararla con el *resultado esperado* y solo entonces proponer la siguiente.
2. **Detenerse** ante cualquier diferencia con lo esperado, error, salida vacía o duda. No improvisar comandos fuera de esta guía, no "arreglar" archivos, no editar código, no borrar nada que no esté indicado.
3. **Nunca** pedir, mostrar ni pegar valores secretos: contraseña temporal, tokens, claves, contenido de `secrets/`, `keys/`, `cu/`, `pki/*.key`, credenciales de cloudflared ni el token de un túnel. Las salidas de `formulario.py` contienen solo hashes, contadores, rutas operacionales e IDs de imagen: esas sí pueden compartirse.
4. **No asumir valores.** Todo valor marcado `‹COMPROBAR›` se obtiene en la etapa indicada y se anota en la bitácora (sección 14) antes de usarlo.
5. No ejecutar nunca: `docker compose down -v`, `docker system prune`, `docker volume rm` de volúmenes que no sean del ensayo, `rm` sobre `/srv/ddn-formulario-produccion`, cambios en cloudflared/DNS/Cloudflare, `preparar.py`, `acceso-temporal.py`, ni `formulario.py arrancar` **sin** `--validacion` sobre el ensayo. Las opciones `--sin-systemd`, `--ignorar-montaje` y `--overlay` son exclusivas del laboratorio: **nunca** en DDN-UBUNTU.

### Leyenda de operaciones

| Etiqueta | Significado |
|---|---|
| **[LECTURA]** | Solo lee. Sin efecto en el servicio. |
| **[AISLADO]** | Crea recursos nuevos propios (archivos, imagen, proyecto `formulario-ensayo`). No toca el servicio público. |
| **[INTERRUMPE]** | Detiene temporalmente el servicio público. Reversible. |
| **[SIN RETORNO SIMPLE]** | A partir de aquí, volver atrás exige un respaldo de la instalación nueva y conciliación (sección 12). |

## 1. Requisitos R1–R7

Hay dos grupos distintos. Los **requisitos previos** (1.a) bloquean la etapa 9: sin evidencia anotada en la bitácora para **cada** fila, la transición es NO-GO. La **aceptación posterior** (1.b) se comprueba en la etapa 10; si cualquier punto falla, se aplica la reversión (etapa 12) y no se continúa.

### 1.a Requisitos previos — bloquean la transición

| # | Requisito | Evidencia exigida **antes** de la etapa 9 | Etapa |
|---|---|---|---|
| R1 | Respaldo externo verificado | `respaldar` con `"verificado": true` escrito en un medio externo POSIX; `verificar-respaldo` OK leyendo **desde ese medio**; SHA-256 coincidente; confirmación de que una copia de la clave `K` está custodiada **fuera de la VM** | 5 |
| R2 | Espacio disponible | `plan` sin bloqueos con `--limite-gib 40`; `df` de `/srv`, `/var/lib/docker` y del medio externo anotados | 3, 4 |
| R3 | Identidad PISEE | exactamente un contenedor activo monta `/opt/pisee`; su `public.pem` coincide con `plan.piseeIdentidad` y con `PISEE_IDENTIDAD` del ensayo; nodos PISEE detenidos de otros proyectos identificados; TI acepta el cambio de la IP permitida del backend | 3, 4, 6 |
| R4 | Subredes | subredes nuevas y del ensayo **aprobadas por TI** sin colisión (redes Docker, rutas, VPN, GATE4A, orquestador); `verificar --validacion` del ensayo con `puertaSalida` = `FORM_TRUSTED_PROXIES` | 3, 6 |
| R5 | systemd (estado previo) | `ddn-formulario-produccion.service` `enabled`; `/etc/systemd/system/formulario.service` **no existe**. La herramienta vuelve a comprobarlo y aborta **antes de interrumpir** si no se cumple | 3, 8 |
| R6 | Componentes reales (ensayo) | puerto de cloudflared = puerto público del origen; `plan.montajes` sin bloqueos (incluye el `compose.origin-ca.yml` real); en el ensayo: ClamAV detecta EICAR, inspector sano, contraseña temporal operativa, ClaveÚnica redirige | 3, 4, 6 |
| R7 | Ventana y autorización | autorización expresa del operador, aviso a SIGER y responsable de reversión designado | 8 |

### 1.b Aceptación posterior — tras la transición

| # | Comprobación | Etapa |
|---|---|---|
| A1 (R3) | un único nodo PISEE activo, el de `/srv/formulario`, sano | 10 |
| A2 (R4) | `verificar` (modo producción) con `puertaSalida` = `FORM_TRUSTED_PROXIES`; acceso real por el dominio público | 10 |
| A3 (R5) | `ddn-formulario-produccion.service` `disabled`, `formulario.service` `enabled` y `systemctl start formulario.service` correcto | 10 |
| A4 (R6) | seis servicios sanos con los componentes reales, contraseña temporal y ClaveÚnica por el dominio público vía cloudflared **sin cambios** | 10 |
| A5 | CAS idéntico al registrado en 3.2; contenedores del origen conservados en `Exited` | 10 |

## 2. Valores a comprobar (no se asumen)

| Variable | Qué es | Dónde se obtiene |
|---|---|---|
| `PKG` | ruta del paquete en Ubuntu, fuera de cualquier repositorio Git | etapa 2 |
| `F` | `$PKG/fuente/scripts/formulario/formulario.py` | etapa 2 |
| `R` | raíz anterior (documentada `/srv/ddn-formulario-produccion`) | etapa 3 |
| `PROY` | proyecto Compose anterior (`installation.json` → `project`) | etapa 3 |
| `PUB` | puerto loopback público del origen (`compose.env` → `FORM_HTTPS_PORT`) | etapa 3 |
| `HOSTNAME` | `compose.env` → `FORM_HOSTNAME` | etapa 3 |
| `K` | clave de respaldo (documentada `/srv/ddn-formulario-custodia/backup-encryption-key`) | etapa 3 |
| `E` | punto de montaje del medio externo POSIX, en otro disco | etapa 3 |
| `IMG` | ID de la imagen cargada (`manifiesto.json` → `imagenId`) | etapa 2 |
| `SUB_SAL`, `SUB_PIS` | subredes nuevas aprobadas por TI (por defecto de la herramienta: `10.203.76.0/28`, `10.203.75.0/29`) | etapa 3 + TI |
| `ENS_SAL`, `ENS_PIS` | subredes del ensayo aprobadas por TI (sugeridas `10.203.77.0/28`, `10.203.78.0/29`) | etapa 3 + TI |
| `VAL` | puerto de validación de la transición (por defecto `30444`), libre | etapa 3 |
| `ENS_PUB`, `ENS_VAL` | puertos del ensayo (sugeridos `30445`, `30446`), libres | etapa 3 |
| `W` | bitácora privada de root, p. ej. `/root/reinstalacion-formulario` | etapa 2 |

---

## Etapa 1 — Preparar el release en CachyOS **[AISLADO]**

Equipo de desarrollo, repositorio `~/Proyectos/FormularioWeb`. No requiere Ubuntu.

```bash
cd ~/Proyectos/FormularioWeb
git fetch origin && git status --short --branch
git cat-file -t 3d0b54c32f8638674efa9c0ccbe5f05615c439a3
gh run list --commit 3d0b54c32f8638674efa9c0ccbe5f05615c439a3 --json conclusion,status
uname -m
docker version --format '{{.Server.Version}}'
mkdir -p ~/formulario-releases
python3 scripts/linux/empaquetar-release.py --commit 3d0b54c32f8638674efa9c0ccbe5f05615c439a3 \
  --destino ~/formulario-releases/release-3d0b54c
```

**Esperado:** `commit` existe; CI `success`; `uname -m` = `x86_64` (la instalación es `linux/amd64`); el empaquetado construye la imagen desde `git archive` (lint, pruebas React, regresión H2: tarda varios minutos) y termina con JSON que incluye `"commit": "3d0b54c3…"`, `"imagen": "ddn-formulario:3d0b54c32f86"`, `"imagenId": "sha256:…"`, `"flywayMaxima": 13`, `"contieneSecretos": false`.

```bash
cd ~/formulario-releases/release-3d0b54c && sha256sum -c --quiet SHA256SUMS && echo sums-ok
cat manifiesto.json
du -sh .
```

**Esperado:** `sums-ok`; tamaño aproximado de 150 MB. Anotar `imagenId` (= `IMG`). **Detenerse** si falla cualquier comprobación.

## Etapa 2 — Transferir y verificar en Ubuntu **[AISLADO]**

El medio de transferencia (scp/rsync/medio físico) y la cuenta de destino los define el operador: `‹COMPROBAR›`. Ejemplo con rsync desde CachyOS: `rsync -a ~/formulario-releases/release-3d0b54c/ ‹usuario›@‹ddn-ubuntu›:release-3d0b54c/`.

En DDN-UBUNTU:

```bash
W=/root/reinstalacion-formulario
sudo install -d -m 0700 "$W"
sudo install -d -m 0750 /srv/formulario-releases
sudo cp -a ~/release-3d0b54c /srv/formulario-releases/
PKG=/srv/formulario-releases/release-3d0b54c
F=$PKG/fuente/scripts/formulario/formulario.py
cd "$PKG" && sudo sha256sum -c --quiet SHA256SUMS && echo sums-ok
git -C "$PKG" rev-parse 2>&1 | head -1
python3 --version; sudo docker compose version; gpg --version | head -1; uname -m
```

**Esperado:** `sums-ok`; `git` responde «not a git repository» (el paquete no debe estar dentro de un repositorio); Python ≥ 3.11; Docker Compose ≥ 2.24.4 (necesario para `!reset`/`!override`); `gpg` presente; `x86_64`. **Detenerse** si algo difiere.

Cargar la imagen (añade una imagen; no cambia contenedores):

```bash
gunzip -c "$PKG/imagen-app.tar.gz" | sudo docker load
sudo docker image inspect ddn-formulario:3d0b54c32f86 --format '{{.Id}} {{index .Config.Labels "org.opencontainers.image.revision"}}'
```

**Esperado:** `Loaded image: ddn-formulario:3d0b54c32f86`; el ID coincide con `imagenId` del manifiesto y la revisión es `3d0b54c32f8638674efa9c0ccbe5f05615c439a3`. Anotar `IMG`.

## Etapa 3 — Inventario real **[LECTURA]**

### 3.1 Instalación anterior

```bash
R=/srv/ddn-formulario-produccion
sudo test -f "$R/installation.json" && echo instalacion-ok
sudo python3 -c 'import json,sys;d=json.load(open(sys.argv[1]));print(d.get("project"),d.get("hostname"),d.get("port"))' "$R/installation.json"
sudo grep -E '^(COMPOSE_PROJECT_NAME|FORM_HOSTNAME|FORM_HTTPS_PORT|FORM_HEALTH_PATH|FORM_TRUSTED_PROXIES|PISEE_PRIVATE_DIR|PISEE_SUBNET|PISEE_NODE_IPV4|PISEE_BACKEND_IPV4)=' "$R/compose.env"
```

Anotar `PROY`, `HOSTNAME`, `PUB` (`FORM_HTTPS_PORT`), `FORM_HEALTH_PATH` (se espera `/acceso-temporal/salud`), `FORM_TRUSTED_PROXIES` y `PISEE_PRIVATE_DIR`. **Detenerse** si falta `installation.json`, `FORM_HOSTNAME` o `FORM_HTTPS_PORT`.

```bash
PROY=‹COMPROBAR›; PUB=‹COMPROBAR›
sudo docker ps -a --filter label=com.docker.compose.project=$PROY --format '{{.Names}}\t{{.Status}}\t{{.Image}}'
```

**Esperado:** seis servicios (`mysql`, `app`, `proxy`, `antivirus`, `inspector`, `pisee`) en estado `Up … (healthy)`.

### 3.2 Estado CAS y base de datos (solo SELECT)

```bash
MYSQL=$(sudo docker ps -q --filter label=com.docker.compose.project=$PROY --filter label=com.docker.compose.service=mysql)
sudo docker exec "$MYSQL" mysql --defaults-extra-file=/run/secrets/root.cnf --batch formulario_publico \
  -e 'SELECT revision,accept_new,pull_enabled,contingency FROM operation_control; SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1;'
```

Anotar el estado CAS. **Si `accept_new` = 1**, la instalación nueva recibirá comunicaciones apenas pase a producción: ver el punto sin retorno simple de la etapa 9. Versión Flyway esperada: `12`.

### 3.3 Unidades systemd

```bash
systemctl list-unit-files 'ddn-*' 'formulario*' --no-pager
systemctl is-enabled ddn-formulario-produccion.service ddn-formulario-produccion-backup.timer
test -e /etc/systemd/system/formulario.service && echo EXISTE || echo no-existe
```

**Esperado:** `ddn-formulario-produccion.service` `enabled`; timer de respaldo `disabled` (no reactivarlo); `formulario.service` **no-existe**. Anotar también `ddn-formulario-lab.service` y otras unidades `ddn-*` (no se tocan). **Detenerse** si `formulario.service` ya existe (la transición lo sobrescribiría). → R5 (antes).

### 3.4 Puerto de cloudflared (R6)

No mostrar el token ni el archivo de credenciales del túnel.

```bash
systemctl list-units --all --type=service --no-pager | grep -i cloudflared
systemctl cat cloudflared 2>/dev/null | grep -c -- '--token'
sudo ls /etc/cloudflared/ 2>/dev/null
sudo grep -nE '^\s*(- )?(hostname|service):' /etc/cloudflared/config.yml 2>/dev/null
sudo ss -ltnp | grep -E "127\.0\.0\.1:$PUB\b"
```

**Esperado:** cloudflared corre en el anfitrión. Si hay `config.yml` local, la línea `service:` del hostname público apunta a `https://127.0.0.1:$PUB` o a `https://localhost:$PUB`. Si el túnel usa `--token` (configuración remota), TI debe confirmar en Cloudflare el *Service* del hostname público. `ss` muestra `docker-proxy` escuchando en `127.0.0.1:$PUB`. **Detenerse** si el puerto de cloudflared no es `PUB`: la herramienta reutiliza `PUB` y **no** se cambia cloudflared.

### 3.5 Redes Docker, rutas y puertos (R4)

```bash
sudo docker network ls -q | xargs sudo docker network inspect --format '{{.Name}} {{range .IPAM.Config}}{{.Subnet}} gw={{.Gateway}} {{end}}'
ip -4 route; ip -4 -br addr
sudo cat /etc/docker/daemon.json 2>/dev/null
sudo ss -ltnp | grep -E '127\.0\.0\.1:(30444|30445|30446)\b' || echo puertos-libres
```

Anotar todas las subredes y la puerta de enlace de `${PROY}_salida` (debería ser el valor de `FORM_TRUSTED_PROXIES` del origen). Llevar la lista a TI para aprobar `SUB_SAL`, `SUB_PIS`, `ENS_SAL` y `ENS_PIS` sin colisiones con estas redes, rutas, VPN, GATE4A ni el orquestador. No cambiar `daemon.json` (si `userland-proxy` está desactivado, informarlo: cambia cómo ve el proxy la IP de cloudflared). Confirmar `puertos-libres` o elegir otros con TI.

### 3.6 Identidad PISEE (R3)

```bash
sudo docker ps --format '{{.ID}}' | xargs -r sudo docker inspect --format '{{.Name}}|{{index .Config.Labels "com.docker.compose.project"}}|{{range .Mounts}}{{if eq .Destination "/opt/pisee"}}{{.Source}}{{end}}{{end}}' | grep -v '|$'
sudo docker ps -a --filter status=exited --format '{{.ID}}' | xargs -r sudo docker inspect --format '{{.Name}}|{{index .Config.Labels "com.docker.compose.project"}}|{{range .Mounts}}{{if eq .Destination "/opt/pisee"}}{{.Source}}{{end}}{{end}}' | grep -v '|$'
```

**Esperado:** primera lista con **exactamente un** contenedor activo (el `pisee` de `PROY`), con su ruta de runtime. La segunda lista muestra los nodos detenidos (p. ej. demostraciones previas): anotarlos y **no iniciarlos**. Con la ruta del runtime activo:

```bash
RUNTIME=‹ruta de la primera lista›
sudo sha256sum "$RUNTIME/certsX509-institucional/public.pem"
```

Anotar el hash: es la identidad PISEE.

### 3.7 Espacio y clave (R2, R1)

```bash
df -h /srv /var/lib/docker; sudo docker system df; free -h
K=/srv/ddn-formulario-custodia/backup-encryption-key
sudo stat -c '%a %U %s' "$K"
E=‹punto de montaje del medio externo›
findmnt -no SOURCE,FSTYPE,OPTIONS "$E"; findmnt -no SOURCE /srv; df -h "$E"
```

**Esperado:** `K` con modo `600` o `400`, dueño `root`, tamaño ≥ 32 bytes (la herramienta lo exige). `E` en **otro dispositivo** que `/srv`, sistema de archivos POSIX (`ext4`, `xfs` o `btrfs`; no `vfat`, `exfat` ni `ntfs`, porque la herramienta aplica permisos 0700/0600) y montado con escritura. El respaldo escribe un volcado SQL temporal 0600 en `E` mientras dura (luego lo borra): el medio debe estar bajo custodia institucional. Confirmar con el operador que una copia de `K` está custodiada fuera de la VM (**no** mostrarla). **Detenerse** si no hay un medio externo que cumpla.

## Etapa 4 — Plan **[LECTURA]**

```bash
sudo python3 "$F" plan --legado "$R" --destino /srv/formulario --limite-gib 40 | sudo tee "$W/plan.json" >/dev/null
sudo python3 -c 'import json;d=json.load(open("/root/reinstalacion-formulario/plan.json"));print("bloqueos",d["bloqueos"]);print("identidad",d["piseeIdentidad"]);print("flyway",d["baseDatos"]["flyway"]);print("GiB requeridos %.2f libres %.2f limite %.2f"%tuple(x/2**30 for x in (d["bytesRequeridos"],d["bytesLibres"],d["limiteBytes"])));print("no migrado",d["noMigradoPermaneceEnOrigen"]);print("proxyTls",d["proxyTls"]);print("arranqueValidacion",d["arranqueValidacion"]);[print(m) for m in d["montajes"] if m["clase"] in (None,"release","efimero")]'
```

**Esperado:**

- `bloqueos []`. Un bloqueo «Montaje no clasificado …» (p. ej. por el `compose.origin-ca.yml` real) **detiene el proceso**: informar al equipo de desarrollo; no usar `--ignorar-montaje` (es solo para laboratorio).
- `identidad` = hash de 3.6. Si es `None` o distinto (p. ej. `PISEE_PRIVATE_DIR` fuera de `$R/pisee`), **detenerse e informar**.
- `flyway 12`.
- `proxyTls` con `verificado: True` y `caSalud` = `pki/proxy/origin-ca.crt` si el origen tiene CA de origen (en DDN-UBUNTU sí), si no `pki/proxy/ca.crt`. Un bloqueo «… no verifica pki/proxy/server.crt …» **detiene el proceso**: el proxy nuevo no quedaría sano; no cambiar certificados para sortearlo, informar al equipo de desarrollo. → R6 parcial.
- `arranqueValidacion` sin bloqueo (muestra `aceptarNuevos`/`recuperar` del origen, que **no** se cambian). Un bloqueo «La validación no arrancaría…» **detiene el proceso**.
- GiB requeridos ≤ mín(libres, 40). → R2.
- `no migrado` lista lo que permanece solo en el origen (p. ej. `release`, `software`, `updates`, `ipc`).

## Etapa 5 — Respaldo externo verificado **[INTERRUMPE]** → R1

**El servicio público queda interrumpido durante toda la ejecución de la copia**: la herramienta detiene `proxy`, `app` y `pisee` del origen, vuelca MySQL, escribe y cifra el archivo completo en `E`, y solo entonces los reanuda y espera a que estén sanos; el público ve error de cloudflared en todo ese intervalo. La verificación final del archivo corre ya con el servicio reanudado. Planificar la ventana para el comando completo (la duración crece con el tamaño de la custodia y la velocidad de `E`). No cambia controles CAS ni datos.

```bash
A="$E/formulario-pre-reinstalacion-$(date -u +%Y%m%dT%H%M%SZ).gpg"; echo "$A"
sudo python3 "$F" respaldar --legado "$R" --salida "$A" --clave "$K" --mantenimiento | sudo tee "$W/respaldo-previo.json"
sudo docker ps --filter label=com.docker.compose.project=$PROY --format '{{.Names}}\t{{.Status}}'
```

**Esperado:** JSON con `"verificado": true`, `sha256`, `archivos`, `baseDatos.flyway` `12`, conteos y `piseeIdentidad` igual a 3.6. Los seis servicios del origen vuelven a `healthy`.

Verificación independiente **leyendo desde el medio externo**:

```bash
sudo python3 "$F" verificar-respaldo --archivo "$A" --clave "$K"
sudo sha256sum "$A"; sudo cat "$A.sha256"
```

**Esperado:** `"verificado": true` y los dos hashes iguales. Anotar ruta y SHA-256 → R1 (junto con la confirmación de la custodia de `K`). Si el comando falla o el origen no vuelve a `healthy`, **detenerse**.

## Etapa 6 — Ensayo aislado **[AISLADO]** → R3, R4, R6

Usa una raíz, un proyecto, puertos y subredes propios. El origen sigue en servicio. **Nunca** ejecutar `arrancar` sin `--validacion` sobre el ensayo: iniciaría un segundo nodo PISEE con la misma identidad si la detección fallara.

```bash
sudo test -e /srv/formulario-ensayo && echo EXISTE || echo no-existe
sudo python3 "$F" restaurar --archivo "$A" --clave "$K" --raiz /srv/formulario-ensayo --proyecto formulario-ensayo \
  --puerto ‹ENS_PUB› --puerto-validacion ‹ENS_VAL› --subred-salida ‹ENS_SAL› --subred-pisee ‹ENS_PIS› --imagen-app ‹IMG› \
  | sudo tee "$W/ensayo-restaurar.json"
```

**Esperado:** `no-existe` antes; luego `"flywayAntes": "12"`, `"flywayDespues": "13"`, `"conteosIguales": true`. Si una subred se superpone, la herramienta lo rechaza: volver a TI (R4).

```bash
sudo python3 "$F" arrancar --raiz /srv/formulario-ensayo --validacion
sudo python3 "$F" verificar --raiz /srv/formulario-ensayo --validacion | sudo tee "$W/ensayo-verificar.json"
sudo curl -sS --cacert "$R/pki/proxy/origin-ca.crt" --resolve "‹HOSTNAME›:‹ENS_VAL›:127.0.0.1" \
  "https://‹HOSTNAME›:‹ENS_VAL›/acceso-temporal/salud"; echo
```

`--resolve` conecta a `127.0.0.1` conservando el hostname del certificado (SNI y validación de nombre intactos). No usar `-k`/`--insecure` ni sustituir el hostname por una IP.

**Esperado:** `"modo": "validacion"`, `"pisee": "no iniciado"`; `verificar` con `"ok": true`, `"problemas": []`, `cierreValidacion` con `503` en cada ruta de `proxy` y de `app` (integración y operación cerradas sin enviar credenciales), `salud` `healthy` en `mysql`, `antivirus`, `inspector`, `app` y `proxy` (sin `pisee`) y `puertaSalida` = primera IP de `ENS_SAL` (→ R4 parcial); `curl` → `{"status":"UP"}`.

Componentes reales (R6), sin datos personales:

1. **Contraseña temporal:** en el Firefox institucional de DDN-UBUNTU (perfil que ya confía en la CA de origen), abrir `https://localhost:‹ENS_VAL›/acceso-temporal` e ingresar la contraseña vigente (el operador, sin compartirla). Esperado: acceso concedido **sin advertencia de certificado**; si aparece una advertencia, **no** aceptar la excepción: detenerse e informar.
2. **ClaveÚnica:** pulsar el acceso con ClaveÚnica y comprobar la redirección a `accounts.claveunica.gob.cl`. **No** ingresar credenciales.
3. **ClamAV (EICAR):**
   ```bash
   AV=$(sudo docker ps -q --filter label=com.docker.compose.project=formulario-ensayo --filter label=com.docker.compose.service=antivirus)
   sudo docker exec -i "$AV" bash -c 'exec 3<>/dev/tcp/127.0.0.1/3310; printf "zINSTREAM\0" >&3; printf "\0\0\0\104" >&3; printf "%s" "X5O!P%@AP[4\PZX54(P^)7CC)7}\$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!\$H+H*" >&3; printf "\0\0\0\0" >&3; timeout 10 head -c 200 <&3; echo'
   ```
   Esperado: una respuesta que termina en `FOUND` (firma EICAR). `OK` o error → **detenerse**: las firmas copiadas no sirven.
4. **Inspector:** `healthy` en `verificar` (exige `LANDLOCK3_SECCOMP`).
5. **Identidad PISEE (R3, solo lectura):**
   ```bash
   sudo grep -E '^(PISEE_IDENTIDAD|PISEE_NODE_IPV4|PISEE_BACKEND_IPV4|FORM_TRUSTED_PROXIES|FORM_HEALTH_PATH)=' /srv/formulario-ensayo/formulario.env
   ```
   Esperado: `PISEE_IDENTIDAD` = hash de 3.6. Como el nodo activo del origen monta ese mismo certificado, `arrancar` productivo quedaría rechazado (comportamiento probado en LAB y en `test_formulario.py`). Mostrar a TI que la configuración local del nodo autorizará `PISEE_BACKEND_IPV4` en lugar de la IP anterior.
6. **Origen intacto:** `sudo docker ps --filter label=com.docker.compose.project=$PROY` → seis servicios `healthy`.

## Etapa 7 — Retirar el ensayo **[AISLADO]**

Solo recursos del proyecto `formulario-ensayo`. Revisar la ruta antes de borrar.

```bash
sudo python3 "$F" detener --raiz /srv/formulario-ensayo
sudo docker compose --project-name formulario-ensayo --env-file /srv/formulario-ensayo/formulario.env -f /srv/formulario-ensayo/infra/compose.yml down
sudo docker volume rm formulario-ensayo_mysql-data
sudo rm -rf /srv/formulario-ensayo
sudo docker ps -a --filter label=com.docker.compose.project=formulario-ensayo --format '{{.Names}}' | wc -l
```

**Esperado:** `0`. El respaldo `A` se conserva en `E`.

## Etapa 8 — Decisión GO / NO-GO **[LECTURA]**

Revisar la bitácora: R1–R6 con evidencia. **R7:** el operador declara por escrito la autorización de la ventana, el aviso a SIGER (el PULL efectivo sigue cerrado sin clave ACK) y el responsable de la reversión. Comprobar además:

```bash
sudo test -e /srv/formulario && echo EXISTE || echo no-existe
sudo test -e /srv/.formulario.restaurando && echo EXISTE || echo no-existe
test -e /etc/systemd/system/formulario.service && echo EXISTE || echo no-existe
sudo ss -ltnp | grep -E '127\.0\.0\.1:‹VAL›\b' || echo puerto-validacion-libre
date; grep -E '^formulario.acceso-temporal.expira=' <(sudo cat "$R/config/application.properties")
```

**Esperado:** tres `no-existe` y el puerto libre. La fecha de vencimiento se conserva tal cual (2026-12-15 America/Santiago; al vencer con aprobación pasa a `OPEN`). **Sin R1–R7 completos: NO-GO.**

## Etapa 9 — Transición **[INTERRUMPE]** → contiene el punto **[SIN RETORNO SIMPLE]**

```bash
sudo python3 "$F" transicion --legado "$R" --clave "$K" --respaldos "$E" --raiz /srv/formulario \
  --puerto ‹PUB› --puerto-validacion ‹VAL› --subred-salida ‹SUB_SAL› --subred-pisee ‹SUB_PIS› --imagen-app ‹IMG› \
  2> >(sudo tee "$W/transicion-error.json" >&2) | sudo tee "$W/transicion.json"
```

Secuencia automática (sin intervención manual):

0. Comprobaciones sin interrumpir: plan sin bloqueos, `ddn-formulario-produccion.service` `enabled` y `formulario.service` inexistente; si fallan, la herramienta aborta **sin tocar el servicio**.
1. Plan; detiene `proxy`, `app` y `pisee` del origen (**comienza el corte**); respaldo final verificado en `E` (`formulario-transicion-‹UTC›.gpg`).
2. Detiene `mysql`, `antivirus` e `inspector` del origen (los contenedores **se conservan**) y **deshabilita** `ddn-formulario-produccion.service`.
3. Restaura en `/srv/formulario` (Flyway 12 → 13, conteos idénticos).
4. Validación cerrada en `VAL`, sin PISEE.
5. **[SIN RETORNO SIMPLE]** Producción en `PUB` con el nodo PISEE único y los controles CAS heredados del origen (3.2). Desde aquí, si el CAS estaba abierto, la instalación nueva puede recibir comunicaciones; revertir exige la sección 12.
6. `verificar` e instalación y habilitación de `/etc/systemd/system/formulario.service`.

**Esperado:** `"pasos": ["respaldo-final-verificado", "origen-detenido", "validacion-cerrada", "produccion"]`, `respaldo.verificado: true`, `restauracion.flywayDespues: "13"`, `unidadAnterior: "ok"`, `unidadNueva: "ok"`, `verificacion.ok: true` con `pisee` `healthy`. El corte termina al quedar sano el `proxy` nuevo en `PUB`; la duración depende del volumen (en LAB, minutos con datos mínimos): registrarla.

**Si falla antes del paso 5:** la herramienta detiene lo nuevo y reanuda el origen (`"reversion": {"origen": "reanudado", …}` en el error). **Si falla en el paso 5 o 6:** solo revierte si demuestra, con los escritores detenidos, que ninguna tabla protegida cambió (huellas `CHECKSUM TABLE` contra la línea base de la restauración); si no puede demostrarlo (MySQL caído, cambios, error), **no** reanuda el origen, vuelve a dejar la instalación nueva como estaba y lo informa como «Reversión automática bloqueada»: ir a la sección 12. Un fallo de systemd (deshabilitar la unidad anterior o habilitar la nueva) siempre es bloqueante. Comprobar los seis servicios del origen `healthy`, `systemctl is-enabled ddn-formulario-produccion.service` = `enabled` y el dominio público. **Detenerse** e informar. No reintentar: `/srv/formulario` queda para diagnóstico y la herramienta exige un destino nuevo. Si el error dice «Reversión automática bloqueada», el origen sigue detenido a propósito: no reanudarlo a mano; ir a la sección 12.

## Etapa 10 — Aceptación posterior **[LECTURA]**, salvo el arranque de la unidad → A1–A5

```bash
N=/srv/formulario
sudo python3 $N/bin/formulario.py verificar --raiz $N | sudo tee "$W/aceptacion-verificar.json"
sudo docker ps -a --filter label=com.docker.compose.project=$PROY --format '{{.Names}}\t{{.Status}}'
sudo docker ps --format '{{.ID}}' | xargs -r sudo docker inspect --format '{{.Name}}|{{range .Mounts}}{{if eq .Destination "/opt/pisee"}}{{.Source}}{{end}}{{end}}' | grep -v '|$'
MYSQLN=$(sudo docker ps -q --filter label=com.docker.compose.project=formulario --filter label=com.docker.compose.service=mysql)
sudo docker exec "$MYSQLN" mysql --defaults-extra-file=/run/secrets/root.cnf --batch formulario_publico -e 'SELECT revision,accept_new,pull_enabled,contingency FROM operation_control;'
systemctl is-enabled ddn-formulario-produccion.service formulario.service
sudo ss -ltnp | grep -E "127\.0\.0\.1:$PUB\b"
```

**Esperado:**

- `ok: true` y `puertaSalida` = primera IP de `SUB_SAL` (= `FORM_TRUSTED_PROXIES`).
- Los seis contenedores del origen en `Exited` (conservados).
- Un solo nodo PISEE activo, con la ruta `/srv/formulario/pisee/runtime` (→ R3).
- CAS idéntico a 3.2.
- Unidades: `disabled` (anterior) y `enabled` (nueva).
- `docker-proxy` en `PUB`.

Probar la unidad (idempotente: `arrancar` vuelve a comprobar la identidad PISEE y no recrea lo que ya está sano):

```bash
sudo systemctl start formulario.service; systemctl status formulario.service --no-pager | head -5
```

**Esperado:** `active (exited)`, sin errores → R5. Un reinicio del anfitrión para acreditar el arranque al boot **solo** con autorización expresa, porque afecta GATE4A y el orquestador.

Desde un equipo externo, por el dominio público (`https://‹HOSTNAME›`, vía cloudflared **sin cambios**):

1. `https://‹HOSTNAME›/acceso-temporal`: ingresar la contraseña vigente → acceso.
2. Recorridos visibles; el acceso con ClaveÚnica redirige a `accounts.claveunica.gob.cl` (sin ingresar).
3. No enviar comunicaciones de prueba con datos reales.

Con todo conforme: comunicar a SIGER el cambio de instalación (mismo dominio, firma, token y confianza). La instalación anterior **no se borra** y su timer de respaldo sigue deshabilitado.

## Etapa 11 — Primer respaldo de la instalación nueva **[INTERRUMPE]** (durante toda la copia, como en la etapa 5)

```bash
B="$E/formulario-post-reinstalacion-$(date -u +%Y%m%dT%H%M%SZ).gpg"
sudo python3 /srv/formulario/bin/formulario.py respaldar --raiz /srv/formulario --salida "$B" --clave "$K" --mantenimiento
sudo python3 /srv/formulario/bin/formulario.py verificar-respaldo --archivo "$B" --clave "$K"
```

**Esperado:** `"verificado": true` en ambos. Repetirlo con la periodicidad que defina la institución (los respaldos no se borran automáticamente).

## Etapa 12 — Reversión **[INTERRUMPE]**

`revertir` detiene primero `proxy`, `app` y `pisee` de la instalación nueva y, con MySQL nueva **activa**, compara las huellas (`CHECKSUM TABLE`) de las tablas protegidas —recorridos, personas, adjuntos, cargas, comunicaciones, entregas e integración, custodia, operaciones externas, controles CAS y su auditoría— con la línea base tomada al restaurar. Detecta inserciones, borrados **y modificaciones** de filas existentes (p. ej. estados de reserva o ACK). Si no puede demostrar que no hay cambios (MySQL detenida, sin línea base, error) o encuentra cambios no respaldados, **se bloquea** y vuelve a iniciar los escritores de la instalación nueva. Antes de reanudar el origen exige además que **ningún** contenedor de la instalación nueva siga activo y que ningún otro contenedor activo use la misma identidad PISEE; si no lo logra, se detiene con «No se pudo detener la instalación nueva» y **no** reanuda el origen (nada se borra). Lo mismo aplica a la reversión automática de la transición, que ahora cubre cualquier tipo de error.

**12.a Sin cambios:**

```bash
sudo python3 /srv/formulario/bin/formulario.py revertir --legado "$R" --raiz /srv/formulario | sudo tee "$W/reversion.json"
```

**Esperado:** `{"cambiosRespaldados": [], "unidadNueva": "ok", "nueva": "detenida", "unidadAnterior": "ok", "origen": "reanudado"}`; los seis servicios del origen `healthy`; `ddn-formulario-produccion.service` `enabled` y `formulario.service` `disabled`; el dominio público responde. `/srv/formulario` se conserva. Si MySQL nueva estuviera detenida, la herramienta se niega: iniciarla (`sudo docker start formulario-mysql-1`) y repetir.

**12.b Con cambios** **[SIN RETORNO SIMPLE]**: la herramienta rechaza 12.a e indica las tablas. **Detenerse** y escalar: esos datos existen solo en `/srv/formulario`. Solo con decisión institucional:

```bash
C="$E/formulario-antes-de-revertir-$(date -u +%Y%m%dT%H%M%SZ).gpg"
sudo python3 /srv/formulario/bin/formulario.py respaldar --raiz /srv/formulario --salida "$C" --clave "$K" --mantenimiento
sudo python3 /srv/formulario/bin/formulario.py revertir --legado "$R" --raiz /srv/formulario --clave "$K" --respaldo-nuevo "$C"
```

`revertir` exige que las huellas guardadas en `C` sean **idénticas** al estado actual (comprobado con los escritores detenidos): como `respaldar` reanuda el servicio al terminar, cualquier cambio entre ambos comandos bloquea la reversión y obliga a repetir el respaldo. El origen vuelve **sin** esos cambios: deben conciliarse con SIGER desde `C` o `/srv/formulario` antes de reabrir la recepción. Ninguna herramienta lo hace automáticamente.

## 13. Operación posterior (resumen)

| Tarea | Comando (desde `/srv/formulario/bin/formulario.py`, como root) |
|---|---|
| Verificar | `verificar --raiz /srv/formulario` (producción: exige los seis servicios sanos; `--validacion` solo para el modo de validación, que exige PISEE detenido) |
| Detener / arrancar | `detener --raiz /srv/formulario` / `arrancar --raiz /srv/formulario` |
| Respaldo | etapa 11 |
| Actualizar | desde un paquete nuevo verificado: `python3 ‹PKG nuevo›/fuente/scripts/formulario/formulario.py actualizar --raiz /srv/formulario --imagen-app ‹IMG nueva› --clave "$K" --respaldos "$E"` |
| Recuperar | `restaurar --archivo ‹respaldo› --clave "$K" --raiz ‹raíz NUEVA› --imagen-app ‹IMG›` desde el paquete, con la instalación dañada detenida, y luego `arrancar` |

## 14. Bitácora (completar sin secretos)

| Campo | Valor |
|---|---|
| Fecha y operador | |
| `PKG`, `IMG` (ID) | |
| `R`, `PROY`, `HOSTNAME`, `PUB` | |
| Puerto de cloudflared y método de comprobación | |
| CAS del origen (3.2) | |
| Unidades (3.3) | |
| Subredes existentes / aprobadas por TI (`SUB_*`, `ENS_*`) | |
| Identidad PISEE (3.6) / nodos detenidos identificados | |
| `df` de `/srv`, `/var/lib/docker` y `E`; plan (GiB) | |
| R1: archivo, SHA-256, custodia de `K` | |
| R2 | |
| R3 | |
| R4 | |
| R5 (antes / después) | |
| R6: EICAR, inspector, contraseña temporal, ClaveÚnica, `plan.montajes`, PISEE tras la transición | |
| R7: autorización, aviso a SIGER, responsable | |
| Transición: inicio, fin, corte medido, resultado | |
| Aceptación | |
