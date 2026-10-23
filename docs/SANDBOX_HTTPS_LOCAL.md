> Rutas actualizadas tras separar las aplicaciones. Esta guía inicia solo el Formulario Público. Consulta también MIGRACION_APLICACIONES.md.

# Sandbox real con HTTPS y resolución local

Perfil exclusivo de pruebas, 14 de octubre de 2026. Dominio confirmado por el usuario para esta prueba: `claveunica.formularioadulto.defensorianinez.gob.cl`. Es distinto del `.ddn.gob.cl` documentado en la iteración inicial. No se cambia el registro en ClaveÚnica; el proveedor comprobará que corresponda realmente al par Sandbox.

**Estado comprobado:** dos ingresos reales completados contra Sandbox, identidad visible de solo lectura, vista previa y logout con nuevo ingreso. [Evidencia de la ejecución](evidencias/SANDBOX_REAL_2026-10-14.md).

**Uso habitual y después de reiniciar:** ejecutar `FORMULARIO.cmd` desde la raíz. El [script madre](ARRANQUE_LOCAL.md) automatiza preparación, arranque bloqueado, comprobaciones y habilitación. Los comandos auxiliares siguientes se conservan para diagnóstico y reversión; no es necesario ejecutarlos uno a uno.

## Componentes y alcance

- JAR SIGER con React incluido, en `127.0.0.1:8080`; sin Vite.
- Caddy 2.11.7 portable en `127.0.0.1:443`, TLS 1.2/1.3, sin puerto 80, servicio de Windows ni API administrativa. Sin ACME, DNS público ni Cloudflare.
- mkcert 1.4.4, CA propia de SIGER dentro de `.local/https/ca`; certificado para el nombre exacto, sin wildcard. Confianza únicamente en `Cert:\CurrentUser\Root`. No se modifica el almacén Java: Spring consume los endpoints públicos oficiales con su confianza normal.
- `hosts`: `127.0.0.1 claveunica.formularioadulto.defensorianinez.gob.cl`, dentro de un bloque delimitado. Se preservan las demás entradas y bytes; respaldo protegido antes de agregarlo.
- Claves, binarios, credenciales de proceso y logs locales quedan fuera de Git. `.local` tiene permisos para el usuario actual, SYSTEM y administradores. Nunca compartir `rootCA-key.pem` ni `site-key.pem`.

URLs conservadas exactamente:

```text
https://claveunica.formularioadulto.defensorianinez.gob.cl/redirect
https://claveunica.formularioadulto.defensorianinez.gob.cl/logout
```

El navegador llega a Caddy por hosts; Caddy entrega TLS con certificado confiable y reenvía a Spring. Tomcat reconoce HTTPS exclusivamente mediante cabeceras del proxy en loopback. Cookie `SIGERSESSION`: Secure/HttpOnly/SameSite=Lax; la solicitud de autorización y callback utilizan la misma sesión/origen. El código, token y userinfo se procesan por el backend. El mismo código sigue siendo válido para despliegue institucional.

## Preparación

PowerShell normal, en la raíz de SIGER:

```powershell
.\formulario-publico\scripts\Prepare-LocalSandboxTools.ps1
.\formulario-publico\scripts\Trust-LocalSandboxCertificate.ps1
```

Descargas: versiones fijas desde [Caddy oficial](https://github.com/caddyserver/caddy/releases/tag/v2.11.7) y [mkcert oficial](https://github.com/FiloSottile/mkcert/releases/tag/v1.4.4). SHA256 Caddy cotejado con metadatos GitHub y SHA512 con el archivo de checksums publicado. SHA256 mkcert calculado y fijado tras descarga HTTPS oficial; ese release no publica digest GitHub para el EXE.

Única operación que requiere administrador: ejecutar el script siguiente mediante sudo y aprobar UAC si aparece. Agrega solo la entrada indicada, respalda hosts y vacía caché DNS. No cambia firewall ni otros servicios.

```powershell
sudo --inline pwsh.exe -NoProfile -File .\formulario-publico\scripts\Set-LocalSandboxHosts.ps1
```

Comprobar con el resolvedor del sistema — `nslookup` consulta DNS y no demuestra la entrada hosts:

```powershell
[System.Net.Dns]::GetHostAddresses('claveunica.formularioadulto.defensorianinez.gob.cl')
```

Debe devolver exclusivamente `127.0.0.1`. No continuar si devuelve NXDOMAIN u otra dirección. Un navegador con configuración particular de resolución/confianza debe comprobarse también; nunca atravesar una advertencia de certificado.

## Arranque y comprobación antes de habilitar

Detener primero cualquier instancia previa de SIGER en 8080. El script no detiene servicios ajenos. Para construir el JAR si fuera necesario, ejecutar `Build-Formulario.ps1` con el JAR detenido.

```powershell
.\formulario-publico\scripts\Start-LocalSandbox.ps1
.\formulario-publico\scripts\Test-LocalSandbox.ps1 -RequireLoginBlocked
```

Los procesos arrancan ocultos, sin servicios persistentes. Se registra su PID y hora de inicio en `.local/https/processes.json`; no hay secretos en los argumentos. El script lee exclusivamente Client ID/Secret SANDBOX del TXT institucional externo. No utiliza QA ni Producción. Aplica `config/local-sandbox/application-local-sandbox-https.yml` únicamente en este proceso.

La comprobación exige resolución del sistema, confianza Windows/hostname/vigencia sin omitir validación TLS, HTTP 200 del React empaquetado, HSTS del backend detrás del proxy, cookie segura, retorno `/logout` y callback inválido rechazado por Spring sin intercambiar token. El inicio debe seguir devolviendo 503. Resultado redactado en `.local/https/preflight.json`; no almacena tokens ni cookies.

Solo tras comprobación exitosa:

```powershell
.\formulario-publico\scripts\Start-LocalSandbox.ps1 -EnableLogin
```

Este comando vuelve a comprobar el entorno bloqueado inmediatamente antes de reiniciarlo con `CU_CALLBACK_APPROVED=true`, exclusivamente en este perfil Sandbox. No altera configuraciones generales ni credenciales de otros ambientes. La sesión de preflight se descarta; el flujo real crea un nuevo state.

Abrir [SIGER Sandbox local](https://claveunica.formularioadulto.defensorianinez.gob.cl/). Comprobar certificado sin advertencias y pulsar el botón oficial. La autorización debe mostrar el sitio `accounts.claveunica.gob.cl` en la misma ventana.

## Autenticación real y evidencia

Utilizar solamente RUN oficiales del manual: `44.444.444-4`, `55.555.555-5`, `88.888.888-8` o `99.999.999-9`; clave de prueba oficial `testing`. Ingresarlos únicamente en Gobierno Digital.

Éxito significa autorización real, callback al dominio HTTPS local, intercambio token y consulta userinfo reales, seguida de RUN/nombres/apellidos en campos bloqueados de SIGER. El log seguro `CU_AUTHENTICATION_COMPLETED environment=SANDBOX source=userinfo` indica finalización del backend; no incluye identidad, tokens ni secreto. Verificar además cierre de sesión y nuevo ingreso.

Si el proveedor rechaza el client_id, URI o mecanismo, registrar el texto visible y/o código de error real y detener el intento. `CU_AUTHENTICATION_FAILED` registra solo código sanitizado; no exportar HAR, códigos, encabezados Authorization ni cookies. El callback sintético del preflight produce intencionadamente `authorization_request_not_found`; no confundirlo con un rechazo real de ClaveÚnica. No cambiar URIs, algoritmo, controles del proveedor o certificado para ocultar un rechazo.

Un preflight exitoso **no** demuestra autenticación real ni aceptación del mecanismo por Gobierno Digital. HTTPS local es confiable en este usuario, no un certificado productivo ni una publicación institucional.

## Detener y revertir

```powershell
# Solo procesos registrados de este perfil, sin tocar otros Java/Caddy.
.\formulario-publico\scripts\Stop-LocalSandbox.ps1

# Administrador: retira únicamente el bloque SIGER y vacía caché DNS.
sudo --inline pwsh.exe -NoProfile -File .\formulario-publico\scripts\Set-LocalSandboxHosts.ps1 -Remove

# Usuario actual: retirar solo nuestra CA.
.\formulario-publico\scripts\Trust-LocalSandboxCertificate.ps1 -Remove

# Borrar claves/certificados/logs/respaldos de esta prueba, dentro del workspace.
.\formulario-publico\scripts\Remove-LocalSandboxCertificateFiles.ps1
```

No restaurar indiscriminadamente el respaldo completo de hosts si hubo cambios posteriores: el script elimina solo su bloque. Si ese bloque fue editado, se detiene para revisión. Si la entrada ya existía sin nuestro marcador, no se elimina automáticamente.

No se crearon servicios, reglas de firewall ni tareas programadas. Los binarios portables pueden conservarse; sus archivos están en `.local/tools`. Reiniciar el navegador si conserva sesión o caché DNS después de retirar hosts. La configuración QA/productiva e infraestructura institucional siguen intactas.
