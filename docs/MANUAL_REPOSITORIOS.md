# Ejecución desde los dos repositorios

Guía del 23-10-2026. Código de laboratorio; no habilita publicación ciudadana ni operación institucional productiva. Java 25, Spring Boot 4.1.1, React/TypeScript, MySQL 8.4 y Flyway. Cada aplicación compila su React dentro de su propio JAR y mantiene su base independiente.

## Qué contiene cada repositorio

- `chashmdai/formulario-web`: `formulario-publico/backend`, `front`, configuración externa de ejemplo y scripts propios. Autenticación ClaveÚnica, sesiones ciudadanas, NNA provisional, recepción durable y custodia.
- `chashmdai/sistema-de-requerimientos`: `siger-institucional/backend`, `front`, configuración y scripts propios. Registro institucional, folio, comprobante Ed25519, recuperación saliente y núcleo funcionario V8.
- Ambos conservan `contratos/`, documentación técnica y utilidades compartidas. Los scripts integrados necesitan las dos carpetas; compilar una aplicación no necesita la otra. Las utilidades PISEE no se publican: requieren materiales e identidades institucionales externos y no pertenecen al arranque.

No se suben `.local`, contraseñas, TXT institucional, claves privadas, certificados de máquina, bases, logs, capturas de autenticación, JAR compilados ni `node_modules`. Las claves **públicas** y firmas de fixtures sintéticos se conservan para comprobar compatibilidad. Los informes históricos describen pruebas anteriores; no constituyen nuevas pruebas ejecutadas al clonar. Las evidencias locales referidas en esos informes no se distribuyen.

## Requisitos Windows

Instalar Git, PowerShell 7.4+, Eclipse Temurin JDK 25, Maven 3.9+ y Node.js 24 con npm. Comprobar desde `pwsh`:

```powershell
git --version
java -version
mvn -version
node --version
npm --version
```

No se requiere WSL ni Docker. El helper descarga MySQL portable 8.4.11 fijado por hash; no utiliza ni modifica un servidor institucional. Maven/npm necesitan acceso a sus repositorios de dependencias. No instalar dependencias con una cuenta de producción.

## Clonar y compilar por separado

```powershell
mkdir C:\Dev\SIGER2026
cd C:\Dev\SIGER2026
git clone https://github.com/chashmdai/formulario-web.git
git clone https://github.com/chashmdai/sistema-de-requerimientos.git

cd C:\Dev\SIGER2026\formulario-web
pwsh -NoProfile -File .\formulario-publico\scripts\Build-Formulario.ps1

cd C:\Dev\SIGER2026\sistema-de-requerimientos
pwsh -NoProfile -File .\siger-institucional\scripts\Build-Siger.ps1
```

Cada build ejecuta `npm ci`, lint, pruebas y build React, después `mvn clean verify`. Los JAR quedan en `APP/backend/target/APP-0.1.0-SNAPSHOT.jar`. Detener el backend correspondiente antes de recompilar: Windows mantiene abierto su JAR.

Para probar el bootstrap institucional independiente, cerrado y sin público:

```powershell
cd C:\Dev\SIGER2026\sistema-de-requerimientos
java -jar .\siger-institucional\backend\target\siger-institucional-0.1.0-SNAPSHOT.jar
# En otra consola: Invoke-RestMethod http://127.0.0.1:8081/api/estado
# Ctrl+C detiene este bootstrap. No habilita la sesión funcionaria de laboratorio.
```

El formulario requiere configuración externa de OIDC para arrancar. No utilizar valores inventados para hacer pasar una autenticación. El modo integrado `-SinClaveUnica` permite demostrar NNA con OIDC cerrado.

## Conectar los clones para el laboratorio completo

Elegir **una sola raíz operativa**. En este ejemplo será `sistema-de-requerimientos`. El siguiente comando crea una junction local hacia la carpeta del formulario del otro clon; no copia secretos ni código y la ruta enlazada está ignorada por Git.

```powershell
cd C:\Dev\SIGER2026\sistema-de-requerimientos
.\scripts\Conectar-Repositorios.ps1 -OtroRepositorio ..\formulario-web -Comprobar
.\scripts\Conectar-Repositorios.ps1 -OtroRepositorio ..\formulario-web
```

Ejecutar los comandos integrados siempre desde esta raíz: `.local` contendrá el estado, bases, claves, credenciales DPAPI y procesos de **este** laboratorio. No iniciar dos laboratorios contra los mismos puertos. Actualizar el formulario con Git en su clon real y SIGER en el suyo. La junction no es una dependencia de despliegue ni una llamada entre backends.

En el equipo original ya existe la raíz `C:\Users\Benja\Desktop\pisee-claveunica\SIGER` con ambas carpetas y datos. **No necesita junction, clon nuevo, regeneración de claves ni migración de sus datos.** Usar allí directamente el script madre. Los clones nuevos empiezan con bases vacías; no incluyen los registros del equipo original. DPAPI no es portable entre usuarios/equipos.

## Preparación HTTPS en un equipo nuevo

No repetir estos pasos en el equipo ya preparado. No cambiar DNS público, certificados institucionales ni firewall.

```powershell
# Descarga herramientas fijadas por hash y genera certificado local. No instala confianza.
.\formulario-publico\scripts\Prepare-LocalSandboxTools.ps1
```

Revisar y ejecutar **expresamente** las dos operaciones locales siguientes cuando el dueño del equipo las autorice:

```powershell
# Confianza de la CA de desarrollo solo para CurrentUser/Root.
.\formulario-publico\scripts\Trust-LocalSandboxCertificate.ps1
# Modifica hosts, crea respaldo y vacía caché DNS. Requiere elevación/UAC.
# Ejecutar en PowerShell elevado (o sudo disponible) desde la misma raíz:
.\formulario-publico\scripts\Set-LocalSandboxHosts.ps1
```

Hosts debe resolver `claveunica.formularioadulto.defensorianinez.gob.cl` exclusivamente a loopback. HTTPS público sigue en 443 y funcionario en `https://localhost:8443`. El certificado funcionario es separado y se genera con la CA local ya confiada; no reemplaza el certificado público. No usar el dominio público con `:8443`: los hosts, no los puertos, aíslan cookies. No usar `-SkipCertificateCheck` ni desactivar CSRF/Secure/HttpOnly.

## Iniciar, comprobar y detener

```powershell
# Primera compilación conjunta, después de conectar ambos clones:
.\scripts\Nucleo-Local.ps1 compilar

# Laboratorio NNA y funcionario, sin habilitar OIDC ni llamar a Gobierno Digital:
.\scripts\Nucleo-Local.ps1 iniciar -SinClaveUnica
.\scripts\Nucleo-Local.ps1 estado
.\scripts\Test-Sesiones-Separadas.ps1

# Mostrar exclusivamente en tu consola las cuentas sintéticas y contraseña local:
.\scripts\Nucleo-Local.ps1 acceso

# Detiene backends/proxy propios y conserva MySQL y todos los datos:
.\scripts\Nucleo-Local.ps1 detener
# Opcional, después de detener las aplicaciones:
.\scripts\Local-MySQL.ps1 detener
```

El arranque crea esquemas `robust_public_local` y `robust_inst_local`, cuentas restringidas y cuentas DDL separadas, aplica Flyway pendiente y genera material criptográfico local si falta. No borra las bases existentes. MySQL escucha solo `127.0.0.1:13306`; backends solo `127.0.0.1:8080/8081`; Caddy en loopback 443 y 8443. No abrir puertos de firewall. Todas las contraseñas/tokens se generan fuera de Git y se protegen mediante ACL/DPAPI.

Direcciones:

| Uso | Dirección |
|---|---|
| Formulario adulto | `https://claveunica.formularioadulto.defensorianinez.gob.cl/` |
| Formulario NNA provisional | `https://claveunica.formularioadulto.defensorianinez.gob.cl/?formulario=nna` |
| SIGER funcionario | `https://localhost:8443/` |

React ya está en los JAR. `npm run dev` es opcional para desarrollo y **no** reemplaza el origen HTTPS/callback registrado.

Para iniciar individualmente dentro del laboratorio preparado:

```powershell
.\scripts\Nucleo-Local.ps1 publico -SinClaveUnica
.\scripts\Nucleo-Local.ps1 siger
.\scripts\Nucleo-Local.ps1 detener-publico
.\scripts\Nucleo-Local.ps1 detener-siger
```

No ejecutar `iniciar` sobre puertos ocupados: consultar `estado` y detener únicamente los procesos propios con este script. No mezclar estos procesos con los lanzadores históricos `FORMULARIO.cmd`/`SIGER.cmd`.

## ClaveÚnica Sandbox en el equipo autorizado

Proporcionar el TXT Sandbox institucional mediante un canal privado, fuera de los clones. El script selecciona únicamente las líneas `Client ID SANDBOX:` y `Client Secret SANDBOX:`; nunca selecciona QA/producción.

```powershell
$env:CU_SANDBOX_CREDENTIALS_PATH='C:\CredencialesPrivadas\DefensoriadelaNinez.txt'
.\scripts\Nucleo-Local.ps1 iniciar
```

Si ya está iniciado, detener primero con el script madre. En el equipo original se conserva la ruta externa anterior como fallback. El arranque comprueba resolver Windows, HTTPS confiable, cookies y rutas antes de habilitar `CU_CALLBACK_APPROVED` **solo en los procesos locales**. No cambia las URI registradas: `/redirect` y `/logout` bajo el origen HTTPS público. Un 503 “Callback pendiente” indica que ese proceso no superó/habilitó esta comprobación; no editar YAML para forzarlo ni entrar por localhost:8080/5173. Consultar los logs privados `.local/nna`, sin publicarlos.

Iniciar sesión realmente en Sandbox es una acción aparte en el sitio del proveedor, solo con identidades oficiales admitidas. El preflight local no acredita autenticación real. No usar producción ni ejecutar llamadas gubernamentales en pruebas automatizadas.

## Recorrido de extremo a extremo

Con el laboratorio iniciado, abrir NNA, introducir datos inequívocamente sintéticos, región obligatoria, comuna opcional y RUN opcional. Enviar. Recepción técnica pendiente significa que el público custodió el ingreso; **no** es registro institucional ni folio confirmado.

SIGER inicia HTTPS saliente hacia el público, verifica custodia, registra una Comunicación en su MySQL y devuelve folio/metadatos firmados mediante ACK. El público permite recuperar el comprobante institucional de la misma operación y sesión. Entonces la Comunicación aparece en el buzón funcionario. Si SIGER está apagado, queda pendiente durable y se recupera al encenderlo. Cerrar/recargar el formulario no restaura campos ni operaciones anteriores; no implica borrar lo ya recibido. No hay recuperación entre sesiones.

Para una demostración automatizada sintética, que **sí crea registros de laboratorio**:

```powershell
.\scripts\Nucleo-Local.ps1 demostrar
.\scripts\Nucleo-Local.ps1 demostrar-derivacion
.\scripts\Antecedentes-Local.ps1 demostrar
```

La última demuestra no admisión, incorporación, reapertura rechazada/aprobada e integridad de originales. Roles sintéticos restringidos: distribución vincula; profesional responsable evalúa y solicita reapertura; jefatura decide. Un profesional solo cierra directamente su Caso BAJA con fundamento. Rechazo/cancelación de **derivación** siguen deshabilitados; no confundirlos con rechazo de reapertura implementado. Bitácora, matriz y encaminamiento de vínculos solo al Requerimiento siguen provisionales/pendientes. Véase `docs/ANTECEDENTES_Y_REAPERTURAS_LAB.md`.

## Pruebas reproducibles

```powershell
# Independientes, desde cada carpeta front: npm ci; npm run lint; npm test; npm run build
# Independientes, desde cada carpeta backend: mvn -B -ntp test (H2 por defecto)
.\scripts\Test-Antecedentes.ps1 -Base H2
.\scripts\Test-Antecedentes.ps1 -Base MySQL
.\scripts\Test-Sesiones-Separadas.ps1
.\formulario-publico\scripts\Test-LocalSandbox.ps1 -RequireLoginEnabled
```

Pruebas MySQL usan únicamente `robust_*_test`, que pueden limpiar; nunca apuntarlas a esquemas reales. `Robustez.ps1` exige detener la recepción antes de su batería. Pruebas opcionales de carga/caída tienen activación explícita y no forman parte del arranque. No ejecutar carga contra proveedores. Una prueba H2 no acredita MySQL, y ninguna prueba local acredita autenticación o habilitación gubernamental.

## Configuración y despliegue futuro

Plantillas sin secretos en `APP/config`; no se cargan automáticamente como dotenv. Configurar variables de entorno externas al proceso, certificados y trust stores reales, cuenta de aplicación limitada y cuenta de migración separada. Ejecutar migraciones mediante `java -jar APP.jar --migrate` con `MIGRATION_DB_URL/USER/PASSWORD` antes del servicio. Fuera del laboratorio se exige MySQL TLS `sslMode=VERIFY_IDENTITY`; no añadir parámetros contradictorios. No utilizar `LOCAL_DB_ALLOWED` ni perfiles `nna-lab`/`nucleo-lab` en producción.

`SIGER_PUBLIC_SOURCE` es el origen del que SIGER recupera entregas, distinto del origen de la interfaz funcionaria. El ciudadano nunca llama a una IP privada de SIGER. Se deben provisionar fuera de Git los tokens técnicos, claves Ed25519 y confianza de claves públicas compatibles de ambas partes. El formulario no administra usuarios funcionarios ni SIGER la sesión ClaveÚnica.

Faltan alojamiento público, autenticación/matriz institucional, aprobación de privacidad y formularios, operación/respaldos/retención, habilitación ClaveÚnica y definiciones DDN. No copiar perfiles locales como configuración productiva. No habilitar NNA real por el hecho de que funcione esta demostración.

## Recuperación, actualización y reversión

Guardar respaldos cifrados y coordinados de **cada base**, claves de firma y trust stores; no subirlos a Git. No regenerar claves para resolver un fallo de arranque: perdería la continuidad de firma. DPAPI solo sirve al usuario/equipo que lo creó; una reinstalación requiere un procedimiento privado de recuperación, no copiar XML a otro usuario.

Actualizar con `git pull --ff-only` en cada clon real, detener su JAR antes del build, recompilar y arrancar desde la raíz operativa elegida. No editar V1–V8 aplicadas ni borrar esquemas. Volver a un JAR anterior no revierte una migración: preferir corrección hacia adelante o un respaldo consistente validado fuera de los datos actuales. El JAR V7 no debe asumirse compatible con V8.

Para retirar el laboratorio, detener primero ambos backends/proxy y luego MySQL. No borrar `.local` sin respaldo. La retirada de hosts y de confianza es explícita y local:

```powershell
.\formulario-publico\scripts\Set-LocalSandboxHosts.ps1 -Remove
.\formulario-publico\scripts\Trust-LocalSandboxCertificate.ps1 -Remove
```

Revisar el respaldo de hosts; no restaurar ciegamente un archivo completo sobre cambios ajenos. No retirar otras CA. Para desconectar los clones puede eliminarse **solo la junction**, con Explorador o `cmd /c rmdir` sobre su ruta literal tras verificar que es un enlace; nunca usar borrado recursivo contra la aplicación enlazada. No eliminar la carpeta del otro clon.

## Publicación de código y secretos

Cada repositorio tiene su propio `.gitignore`. Revisar `git status`, `git diff --cached` y ejecutar un escáner de secretos antes de `git commit`/`git push`. No añadir `.local` con `git add -f`. No adjuntar capturas de sesión ni copiar TXT, bases o logs a issues. Los repositorios son públicos: toda publicación es visible sin iniciar sesión. Ningún secreto es necesario para compilar o ejecutar las pruebas H2.
