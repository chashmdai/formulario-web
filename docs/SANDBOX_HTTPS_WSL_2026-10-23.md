# ClaveÚnica Sandbox real en Docker/WSL — 23 octubre 2026

Autorización expresa del responsable en este chat: hosts Windows/WSL, CA de desarrollo en el usuario, proxy HTTPS local y flujo real Sandbox. Esta ejecución complementa [el procedimiento Windows anterior](SANDBOX_HTTPS_LOCAL.md); no requiere detener el servicio ajeno que ocupa 8080.

## Resultado comprobado

**FUNCIONAL/PROBADO:** autorización oficial → callback registrado → intercambio de código → userinfo → sesión autenticada → cierre e invalidación de sesión. Ejecutado en el navegador integrado sin advertencias TLS ni omitir validación de certificados. Se empleó exclusivamente la identidad sintética Sandbox autorizada; no se registra aquí su identidad ni contraseña.

- Windows y WSL resuelven exclusivamente `127.0.0.1`; el navegador cargó React desde el hostname HTTPS exacto.
- Preflight Windows con validación TLS normal: HTTP 200, HSTS, sesión anónima SANDBOX, cookie Secure/HttpOnly/SameSite=Lax, callback negativo rechazado, logout y redirección oficial correctos. Ejecutado primero bloqueado y después habilitado.
- Tras el login, `/api/session` indicó `authenticated=true`, `environment=SANDBOX` e identidad con RUN y componentes de nombres/apellidos. El backend emitió `CU_AUTHENTICATION_COMPLETED environment=SANDBOX source=userinfo`.
- Tras pulsar Cerrar sesión, `/api/session` indicó `authenticated=false` y ausencia de identidad. No se enviaron Comunicaciones durante esta prueba.
- Se repitió el recorrido real completo después de empaquetar `PrivacyDialog` en el JAR: elección explícita del canal → autorización oficial → sesión autenticada con identidad → cierre → sesión anónima sin identidad. Ambos ingresos concluyeron correctamente.
- No se guardaron HAR, tokens, cookies, códigos, capturas autenticadas ni respuestas de identidad. La captura de evidencia muestra solo el inicio tras cerrar sesión. Los JSON privados de preflight contienen únicamente estado técnico y huellas públicas.

Esto verifica este cliente y ambiente Sandbox. No certifica aceptación productiva, envío SIGER ni migración completa del mockup. SRCeI es una integración independiente.

## Componentes y cambios efectivos

| Elemento | Configuración |
|---|---|
| Hostname | `claveunica.formularioadulto.defensorianinez.gob.cl` |
| Callback / logout conservados | `https://claveunica.formularioadulto.defensorianinez.gob.cl/redirect` y `/logout` |
| Windows hosts | Bloque SIGER añadido por el script original `Set-LocalSandboxHosts.ps1`, mediante UAC normal; respaldo binario previo, otras entradas preservadas y caché DNS vaciada |
| WSL hosts | Mismo bloque exacto, respaldo previo; no se modificó `wsl.conf` ni se desactivó su generación automática |
| TLS | CA EC P-256 propia, hoja RSA 2048 SAN exacto, 30 días, TLS 1.2/1.3; OpenSSL verificó cadena y hostname |
| Confianza | Solo `Cert:\CurrentUser\Root`, mediante el script original; CA SHA-1 pública `374CC406A15182DE15E913285C496C1DA7BE6A64`; sin cambios Java o confianza global Linux |
| Proxy | Caddy 2.11.7 Alpine en `siger-sandbox-https-caddy-1`, Windows/WSL loopback 443 → contenedor 8443 → `app:8080` |
| Aplicación | `siger-app-lab-app-1`, diagnóstico loopback 18080, origen público HTTPS exacto |
| Forwarded | Tomcat confía solo loopback y la IP concreta del proxy (`172.21.0.3` en esta ejecución), no toda la red privada. Caddy sobrescribe cabeceras y elimina Forwarded recibido |
| Aislamiento | UID 1000, filesystem readonly, sin capabilities ni privilegios nuevos; CA privada nunca montada, solo hoja y su clave; sin logs de acceso Caddy |

La imagen oficial Caddy incorpora file capability para puertos bajos. Al quitar todas las capabilities, el kernel rechazaba ejecutar ese binario. El Dockerfile retira su file capability durante la construcción y usa 8443; no se conceden capabilities al contenedor.

Custodia Linux: `/home/nexbyteproveedor/.local/share/siger/sandbox-https` (0700, archivos 0600). La configuración con credenciales permanece en `app-cerrada/application.properties`, fuera de Git e imágenes. Runtime Windows: `%LOCALAPPDATA%\Nexbyte\SigerSandbox2026`, ACL limitada al usuario, SYSTEM y administradores. Solo se copiaron allí certificados públicos y los scripts originales de hosts, confianza y preflight. La clave privada de CA permanece en Linux y no se usa para ejecutar el proxy.

## Reproducir y reiniciar

Los comandos Docker requieren el grupo Docker disponible en la sesión o elevación Linux normal. En esta ejecución se empleó `wsl -d Ubuntu-24.04 -u root -- ...`. No regenerar una CA existente ni volver a importar otra innecesariamente.

```bash
cd /home/nexbyteproveedor/proyectos/formulario-web
# Solo la primera vez, elegir directorio privado NUEVO:
python3 scripts/linux/preparar-tls-sandbox.py "$HOME/.local/share/siger/sandbox-https"
# Con elevación, solo si falta la entrada WSL:
python3 scripts/linux/hosts-sandbox-wsl.py /home/nexbyteproveedor/.local/share/siger/sandbox-https
SANDBOX_TLS_DIR=/home/nexbyteproveedor/.local/share/siger/sandbox-https docker compose -f infra/compose.sandbox-https.yml up -d --build
# Bloquea inicio y actualiza la IP exacta del proxy. Repetir si se recrea el proxy/red.
python3 scripts/linux/configurar-sandbox-https.py /home/nexbyteproveedor/.local/share/siger/app-cerrada/application.properties
APP_PRIVATE_DIR=/home/nexbyteproveedor/.local/share/siger/app-cerrada docker compose -f infra/compose.app-lab.yml restart app
```

Esperar healthcheck de la aplicación. Windows: reutilizar `Test-LocalSandbox.ps1 -RequireLoginBlocked` desde el runtime privado; copiar su `.local\https\preflight.json` a un archivo privado Linux. Este preflight requiere CA pública en `.local\https\ca\rootCA.pem` y hoja pública `.local\https\site.pem`. Para la primera confianza/hosts, reutilizar los scripts originales copiados y elevación UAC de hosts.

```bash
python3 scripts/linux/configurar-sandbox-https.py /home/nexbyteproveedor/.local/share/siger/app-cerrada/application.properties --habilitar-con-preflight /home/nexbyteproveedor/.local/share/siger/sandbox-https/preflight-bloqueado.json
APP_PRIVATE_DIR=/home/nexbyteproveedor/.local/share/siger/app-cerrada docker compose -f infra/compose.app-lab.yml restart app
```

El configurador exige SANDBOX, callbacks exactos y preflight bloqueado satisfactorio de los últimos 15 minutos. Después ejecutar preflight Windows `-RequireLoginEnabled`. Abrir el hostname y usar el acceso oficial. La renovación de la hoja debe conservar el SAN exacto y verificarse antes de reemplazar los archivos montados. No usar `-k`, desactivar TLS ni atravesar interstitials.

## Reversión acotada

1. Configurador sin `--habilitar-con-preflight` y reiniciar únicamente esta aplicación para bloquear nuevos ingresos. Para restaurar también el origen anterior, usar el respaldo privado **`sandbox-https/application-before-https.properties`** de esta ejecución, revisando antes cambios posteriores. El configurador conserva además una copia anterior a su primera ejecución en el directorio de configuración; esta puede corresponder a un estado ya HTTPS.
2. Detener únicamente el proxy: `SANDBOX_TLS_DIR=... docker compose -f infra/compose.sandbox-https.yml stop`. No detener otros servicios ni eliminar volúmenes.
3. Windows, UAC normal: ejecutar el script copiado `%LOCALAPPDATA%\Nexbyte\SigerSandbox2026\formulario-publico\scripts\Set-LocalSandboxHosts.ps1 -Remove`. Retira solo su bloque intacto y vacía caché DNS. Respaldo original y metadatos están en `.local\https` de ese runtime.
4. Linux con elevación: `python3 scripts/linux/hosts-sandbox-wsl.py /home/nexbyteproveedor/.local/share/siger/sandbox-https --retirar`. El respaldo `wsl-hosts-before.bin` de esta ejecución permanece privado. No restaurar todo hosts si hubo cambios posteriores. WSL puede regenerarlo tras reiniciar; comprobar resolución nuevamente.
5. Usuario Windows normal: script copiado `Trust-LocalSandboxCertificate.ps1 -Remove`; elimina exclusivamente nuestra CA por la huella registrada en su metadata. No retirar certificados ajenos.
6. Reiniciar pestañas si conservan DNS/sesiones. Con servicios detenidos, conservar o retirar únicamente los archivos privados de esta prueba según la política institucional. La CA expira en un año y la hoja en 30 días; no se dejan fuera de custodia.

No se modificaron DNS público, firewall, políticas globales, URIs registradas, servicios ajenos ni credenciales productivas.
