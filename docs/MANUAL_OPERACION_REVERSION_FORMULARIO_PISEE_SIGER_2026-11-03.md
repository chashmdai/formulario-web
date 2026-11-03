# Operación y reversión del candidato privado — 03-11-2026

Este procedimiento opera únicamente los laboratorios nuevos. El sitio público permanece en certificación. Inventario/resultados: [informe](INFORME_INTEGRACION_FORMULARIO_PISEE_SIGER_2026-11-03.md).

## DDN-UBUNTU: acceso y estado

```bash
REPO=/home/admin-ti/formulario-release-20261103
LAB=/home/admin-ti/operacion-formulario/release-20261103/formulario-lab-a
git -C "$REPO" status --short --branch
git -C "$REPO" log -6 --oneline
ss -lntp
sudo docker compose --env-file "$LAB/compose.env" -p ddn-release-pisee-lab-a \
  -f "$REPO/infra/ubuntu/compose.yml" \
  -f "$REPO/infra/ubuntu/compose.srcei-stub-lab.yml" config --quiet
sudo docker compose --env-file "$LAB/compose.env" -p ddn-release-pisee-lab-a \
  -f "$REPO/infra/ubuntu/compose.yml" \
  -f "$REPO/infra/ubuntu/compose.srcei-stub-lab.yml" ps -a
```

Al cierre están detenidos. Para revisión privada se pueden arrancar esos mismos recursos conservados mediante `up -d --wait` con los argumentos anteriores. Eso no abre el control: revisión 4, aceptarNuevos=false, recuperar=false, SOLO_CONSULTA. Mantener el digest final de `compose.env`; no usar build/imagen latest ni migrar de nuevo una base conservada por rutina.

Acceso HTTPS privado comprobable sin cambiar hosts/DNS:

```bash
curl --fail --silent --show-error \
  --cacert "$LAB/pki/proxy/ca.crt" \
  --resolve formulario-pisee-lab.defensorianinez.gob.cl:26443:127.0.0.1 \
  https://formulario-pisee-lab.defensorianinez.gob.cl:26443/api/session
```

No imprimir la respuesta si existe una sesión real del operador. El laboratorio actual solo tiene datos sintéticos. La CA de laboratorio no se instala en confianza global. Para navegador, el puente privado ya usado está en la raíz de evidencias; verifica CA/SAN en Node y pasa respuestas reales. No equivale a instalar confianza nativa en Chromium.

La Comunicación sintética ya existe: **no ejecutar nuevamente `adulto-ui.mjs`, no borrar su marcador de envío único y no abrir recepción para “completar” el mismo envío**. Sigue PENDIENTE con attempts=0; debe ser la única entrega de una eventual ventana Windows. Su UUID/hashes están en el informe.

El helper privado `control-lab.py estado` consulta el control. `cerrar` aplica CAS/If-Match, recupera=false y SOLO_CONSULTA, sin forzar revisiones. `admitir` fue exclusivo del envío sintético ya cerrado; no usarlo ahora. Ningún comando anterior habilita PULL. No introducir actualizaciones SQL manuales de controles, leases o ACK.

## Crear otro laboratorio sin reutilizar estado

Usar `scripts/ubuntu/preparar.py` con destino/proyecto/puerto NUEVOS, SHA completo, `--sin-claveunica` y `--sin-confianza-ack`. Aprovisionar las claves/trust de la nueva campaña fuera de Git; no copiar privadas del ensayo ni de U4. Las plantillas del directorio `infra/ubuntu` arrancan con todos los gates de PISEE/envío cerrados.

El compose integrado sigue siendo la base; elegir **un solo** overlay:

- Carril A: `compose.srcei-stub-lab.yml` + `pisee-controlled-test.properties.example`. El stub solo admite RUN sintético 1-9, no tiene red de egreso y no existe fallback stub en el adaptador productivo.
- Carril B: `compose.pisee-production-lab.yml` + `pisee-production.properties.example`. No combinar overlays ni asignar el alias `pisee` al stub en un perfil PRODUCTION.

Regresiones sin secretos/proveedores, con Java25 y Node24:

```bash
umask 077
# En este host, runtime de pruebas copiado de imágenes previas, sin instalar globalmente:
. /home/admin-ti/formulario-release-tools-20261103/env.sh
cd "$REPO/formulario-publico/front"
node /home/admin-ti/formulario-release-tools-20261103/npm/bin/npm-cli.js run lint
node /home/admin-ti/formulario-release-tools-20261103/npm/bin/npm-cli.js test -- --maxWorkers=2
node /home/admin-ti/formulario-release-tools-20261103/npm/bin/npm-cli.js run build
cd "$REPO/formulario-publico/backend"
mvn -B -ntp -Dmaven.repo.local=/home/admin-ti/formulario-release-tools-20261103/m2/repository \
  -Dtest='*Test,!Carga*Test' -DargLine=-Xmx384m verify
```

MySQL se ejecutó con el preparador existente y proyecto `ddn-release-regression-mysql`, nunca sobre QA. `run-mysql.py` privado lee contraseñas de archivo hacia el entorno del subproceso; no las pone en argv/salida. Reutilizar ese runner solo para repetir las pruebas de este esquema descartable conservado; verificar primero proyecto, volumen y puerto 13306. No apuntarlo a `formulario_publico`, U4, QA ni WSL. Las pruebas de carga y opt-in no forman parte del gate ejecutado; no ocultar sus omisiones.

## Carril B: nodo y prueba real del adaptador

La copia actual es `/home/admin-ti/operacion-formulario/release-20261103/pisee`. El compose privado independiente es `compose.pisee-b.yml` en esa raíz. No requiere cambiar el nodo original/histórico. Su imagen conservada contiene Ubuntu26.04 y xmlsec1 1.3.9; binario, certificados y configuración se montan RO, estado propio RW. No publicar 8084/8489 ni conectar ciudadanía al nodo.

Para otra copia, con destino inexistente y fuera de Git:

```bash
python3 "$REPO/scripts/linux/preparar-pisee.py" \
  /home/admin-ti/nodo-pisee/LAB_PROD_2026-11-03 \
  /ruta/privada/NUEVA/pisee --produccion-validada
```

El preparador conserva catálogo, PKI, MPGA, consumidores/proveedores y seguridad interna; adapta únicamente rutas/estado/log. Exige el certificado institucional cotejado y verifica pares sin imprimirlos. No reutiliza el par X509 de Gobierno Digital del paquete. Destino existente, certificado distinto o par no verificable detienen la preparación. No sobrescribir un destino rechazado: custodiarlo separadamente sin subirlo a Git.

Antes de una consulta personal se requieren **identidad de prueba autorizada para esta campaña, base legal/finalidad y custodia/retención aprobadas**. No reutilizar el RUN del manual ni adivinarlo. Su archivo `allowed-runs.json` vive fuera de Git, máximo diez entradas; una lista vacía impide toda consulta del recorrido. Mantener `formulario.srcei.envio-autorizado=false`, integración/PULL y aceptación cerrados. No probar contra el stub presentándolo como respuesta real.

Arrancar el adaptador Java aislado en la misma red del nodo con el fragmento productivo revisado; el certificado público se monta en `/run/pisee/public.pem`, la privada exclusivamente en el nodo. Autorizar explícitamente los gates del fragmento solamente con evidencia institucional; faltas de material, certificado, ambiente o finalidad deben fallar cerrado.

Para esa ventana futura, crear un directorio de campaña nuevo y privado con `config.properties` y `allowed-runs.json`, ambos 0400/0600 para UID1000. La configuración usa las rutas del fragmento productivo y exactamente un RUN autorizado en el archivo. No activar `formulario.srcei.envio-autorizado`. Una vez acreditados esos requisitos:

```bash
ROOT=/home/admin-ti/operacion-formulario/release-20261103
CAMPAIGN=/ruta/privada/NUEVA/campana-autorizada
# JDK_IMAGE debe ser un digest JDK25 verificado; nunca una imagen con secretos.
sudo docker compose -f "$ROOT/compose.pisee-b.yml" up -d
sudo docker run --rm --user 1000:1000 --read-only --cap-drop ALL \
  --security-opt no-new-privileges:true --network ddn-release-pisee-lab-b_privada \
  --tmpfs /work:size=192m,uid=1000,gid=1000,mode=0700 \
  --tmpfs /tmp:size=32m,uid=1000,gid=1000,mode=0700 \
  --mount type=bind,src="$REPO/scripts/linux",dst=/probe/bin,readonly \
  --mount type=bind,src="$ROOT/formulario-candidato-final.jar",dst=/probe/formulario.jar,readonly \
  --mount type=bind,src="$CAMPAIGN",dst=/run/pisee-campaign,readonly \
  --mount type=bind,src="$ROOT/pisee/runtime/certsX509-institucional/public.pem",dst=/run/pisee/public.pem,readonly \
  --entrypoint bash "${JDK_IMAGE:?Seleccionar JDK25 por digest}" \
  /probe/bin/sonda-adaptador-pisee.sh /probe/formulario.jar \
  8ff36499e0b6e270a4528a3281028642e68a89a9bc75f74a2a4e83a20aac031e \
  /run/pisee-campaign/config.properties /work/sonda --autorizar-consulta-productiva-acotada
sudo docker compose -f "$ROOT/compose.pisee-b.yml" stop
```

La sonda usa las clases/dependencias del JAR verificado, nunca `target` ni Maven como runtime. No arranca Spring, sesión, base ni cola. Solo informa estado, motivo y cantidad de atributos normalizados; esta cantidad no es el número de elementos XML principales. Su código de salida 0 exige COMPLETO; parcial/vacío/fallo no son éxito completo. No guarda valores, RUN, XML o SOAP. Un 200 con `datosPersona` vacío es fallo funcional. Ejecutar una sola vez dentro de la autorización y custodiar únicamente el resultado saneado.

Si se requiere inspección UI con datos reales, acceso exclusivo del operador, sin crear Comunicación ni enviar a SIGER de desarrollo. No persistir el cuerpo SOAP en bases QA, descargas, capturas o reportes. Al terminar, cerrar el proceso/nodo privado y comprobar listeners; no cambiar Cloudflare para acceder al laboratorio.

## Windows: frontera pendiente y preparación exacta

No se ejecutó PowerShell, SIGER, aprovisionamiento, migración, túnel o ciclo nuevo en Windows. El [cierre U4.4](https://github.com/chashmdai/sistema-de-requerimientos/blob/main/docs/INFORME_FINAL_U4_4_WINDOWS_UBUNTU_2026-11-02.md) permanece histórico. El responsable Windows debe leer sus AGENTS y traspaso antes de operar.

**Bloqueo comprobado en código:** `scripts/Windows-Ensayo-Formulario-Ubuntu.ps1` rechaza todo perfil distinto de U4, exige :25443 y usa privadas/credenciales U4. `windows/EnsayoFormularioUbuntu.java` fija ORIGIN a :25443, SCHEMA a `siger_u4_ddn_ubuntu` y cuentas/custodia U4. `ExclusionEnsayoUbuntu` / `VinculoOrigenEnsayo` pertenecen a ese ámbito. **No invocar esos helpers para este ensayo, no cambiar sus literales sobre el JAR conservado y no reabrir su base.**

Primero, solo lectura en Windows:

```powershell
git -C C:\req\repo status --short --branch
git -C C:\req\repo worktree list
Get-CimInstance Win32_Process |
  Where-Object Name -in @('java.exe','ssh.exe') |
  Select-Object ProcessId,ParentProcessId,Name
Get-NetTCPConnection -State Listen |
  Where-Object LocalPort -in @(443,8081,25443,27443) |
  Select-Object LocalAddress,LocalPort,OwningProcess
```

Continuación asignable a Codex/operador Windows:

1. Inventariar hashes/ACL/estado QA y U4 por lectura, sin argumentos/secretos de procesos; preservar `C:\agente`, `C:\smid-ops`, MySQL global y WSL. Crear worktree, esquema, cuentas, custodia y perfil NUEVOS. Propuesta de nombres, que deben comprobarse inexistentes: esquema `siger_pisee_adulto_20261103`, perfil `C:\req\config\formulario-pisee-adulto-20261103`, custodia nueva bajo `C:\req\testdata`. No ejecutar fixtures sobre esquemas existentes.
2. Preparar un entrypoint/perfil del ensayo nuevo que **reutilice** `FuenteFormulario`, `RecuperarFormulario`, `RecepcionInstitucionalJdbc`, `ArchivoInstitucional`, controles CAS y COMMIT→ACK existentes. Separar ámbito de exclusión/vinculación del origen y marca de ciclo único; no construir otro protocolo. Mantener U4 y su código/fixtures intactos. Testear rechazo de esquema/perfil/origen QA/U4, credenciales/TLS inválidos y ciclo sin autorización.
3. Generar clave ACK nueva en Windows, proteger privada por DPAPI CurrentUser/ACL y entregar solo su pública al laboratorio Ubuntu cerrado. El trust `siger-simulado-test` de Linux **no es una identidad Windows**; no transferir su privada. Intercambiar por canal privado aprobado token de integración nuevo/CA pública/trust del recibo y verificar hashes. No copiar DPAPI entre máquinas ni mostrar valores.
4. Configurar nuevo origen HTTPS `https://formulario-pisee-lab.defensorianinez.gob.cl:27443`. Para evitar cambios de hosts/DNS globales, el proceso Java del ejecutor nuevo puede usar un archivo privado propio con `127.0.0.1 formulario-pisee-lab.defensorianinez.gob.cl` y `127.0.0.1 localhost`, mediante `-Djdk.net.hosts.file=RUTA_DEL_PERFIL\hosts`. Aplicarlo únicamente a esa JVM. Este mecanismo de prueba está documentado por [Java25](https://docs.oracle.com/en/java/javase/25/core/java-networking.html); nunca se usa para sustituir validación TLS/SAN ni como DNS de producción.
5. Confirmar consentimiento/ventana del operador, fingerprint SSH ya acreditado y puerto 27443 libre. Solo entonces, en consola propia, abrir el reenvío temporal siguiente; **no fue ejecutado por esta sesión**:

```powershell
ssh.exe -N -T -o ExitOnForwardFailure=yes -o StrictHostKeyChecking=yes -L 127.0.0.1:27443:127.0.0.1:26443 admin-ti@192.168.100.14
```

6. Verificar TLS con CA nueva/SAN exacto. Para sonda sin resolver globalmente se puede usar `curl.exe --resolve formulario-pisee-lab.defensorianinez.gob.cl:27443:127.0.0.1 --cacert RUTA_CA_NUEVA https://formulario-pisee-lab.defensorianinez.gob.cl:27443/api/session`. El token técnico se lee desde DPAPI por stdin/config privado, no en ese comando. Sonda contractual HEAD sobre UUID/adjunto/reserva inexistentes: sin Bearer 401, falso 401, válido 404. Cero claims/ACK. No invocar MIGRAR/CICLO de U4.
7. Acordar cuál cola/campaña nueva se consumirá. El ensayo Linux conservado contiene exactamente la Comunicación sintética del informe, PENDIENTE/attempts0. Verificar UUID, 1.1, ADULTO_DECLARADO, canal público adulto, campos/discrepancias y adjunto antes de habilitar PULL. Si se decide otra captura, preparar **otra campaña**, sin borrar/repetir esta. El consumidor no filtra por UUID: la seguridad depende de la cola/base aisladas y del conteo previo.
8. Con recepción ya cerrada, habilitar PULL mediante API CAS del laboratorio nuevo y ejecutar **un único ciclo** Windows con exclusión y marca durable. Validar SHA canónico, procedencia, firma Formulario y original; persistir COM/documento/atributos con `autenticada=false`, hacer COMMIT y recuperar/verificar registro antes de ACK Ed25519 adulto 1.0. Mantener scheduler QA ajeno al ensayo.
9. Observar la UI SIGER aislada con rol autorizado: RUN declarado, nombres declarados, enriquecimiento simulado, origen/fecha/evidencia y discrepancias; no elevarlo a CU. Verificar un COM, cero duplicados, `ack_pending=0`, Formulario CONFIRMADA y persistencia tras reinicio del ensayo. COMMIT→ACK necesita evidencia del código/eventos/transacción Windows; no se infiere de relojes entre equipos.
10. Cerrar aceptación/PULL de ambos laboratorios por CAS, conservar datos/recibos, cerrar únicamente el SSH identificado mediante Ctrl+C y comparar QA/U4 antes/después. Devolver informe saneado y hashes. Sin esa evidencia, dejar el gate físico PENDIENTE.

Si el ACK se pierde tras COMMIT, conservar COM/folio/original/recibo/origen/clave y reconciliar con el protocolo existente. No ejecutar automáticamente otro ciclo, reenviar el formulario ni borrar leases. Las campañas de caída deliberada/ACK perdido deben planificarse aparte del único ciclo físico, aunque las pruebas automatizadas de esos casos estén verdes.

## Detención y reversión

Reversión ordinaria del candidato: cerrar primero aceptación/PULL por la API del laboratorio, comprobar que no hay transferencia en vuelo y detener **solo sus proyectos**. No se ejecuta rollback sobre el sitio activo porque nunca fue cambiado.

```bash
sudo docker compose --env-file "$LAB/compose.env" -p ddn-release-pisee-lab-a \
  -f "$REPO/infra/ubuntu/compose.yml" \
  -f "$REPO/infra/ubuntu/compose.srcei-stub-lab.yml" stop
sudo docker compose \
  -f /home/admin-ti/operacion-formulario/release-20261103/compose.pisee-b.yml stop
sudo env MYSQL_PRIVATE_DIR=/home/admin-ti/operacion-formulario/release-20261103/mysql-regresion \
  docker compose -p ddn-release-regression-mysql \
  -f "$REPO/infra/compose.mysql-lab.yml" stop
ss -lntp
```

Se conservan bases, volúmenes, cola, claves, certificados, configuración, artefactos y evidencia. Nunca `down -v`, reset/clean destructivos, restauración sobre originales ni regeneración de claves con pendientes. La imagen inicial del candidato también se conserva; cualquier selección posterior debe mantener las barreras de envío registral actuales y los controles cerrados. No es necesario volver a una imagen menos restrictiva para detener el ensayo.

Comprobación independiente del sitio institucional, con lectura/rechazos controlados, sin login real:

```bash
sudo python3 "$REPO/scripts/ubuntu/verificar-certificacion.py" \
  --output /home/admin-ti/operacion-formulario/release-20261103/verificacion-adicional-NUEVA.json
```

El inventario privado compara hashes/servicios sin imprimir secretos. Revisar el informe y `comparacion.json`; si aparece una diferencia inesperada, detener la labor del candidato e investigar por lectura. No intentar “reparar” Cloudflare, flags, Caddy, base o contenedores institucionales desde este procedimiento.
