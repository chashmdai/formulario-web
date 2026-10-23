# Ejecución desde los dos repositorios

Guía del 23-10-2026. Código de laboratorio; no habilita publicación ciudadana ni operación institucional productiva. Java 25, Spring Boot 4.1.1, React/TypeScript, MySQL 8.4 y Flyway. Cada aplicación compila su React dentro de su propio JAR y mantiene su base independiente.

## Qué contiene cada repositorio

- `chashmdai/formulario-web`: `formulario-publico/backend`, `front`, configuración externa de ejemplo y scripts propios. Autenticación ClaveÚnica, sesiones ciudadanas, NNA provisional, recepción durable y custodia.
- `chashmdai/sistema-de-requerimientos`: `siger-institucional/backend`, `front`, configuración y scripts propios. Registro institucional, folio, comprobante Ed25519, recuperación saliente y núcleo funcionario V8.
- Ambos conservan `contratos/`, documentación técnica y utilidades compartidas. Los scripts integrados necesitan las dos carpetas; compilar una aplicación no necesita la otra. Las utilidades PISEE no se publican: requieren materiales e identidades institucionales externos y no pertenecen al arranque.

No se suben `.local`, contraseñas, TXT institucional, claves privadas, certificados de máquina, bases, logs, capturas de autenticación, JAR compilados ni `node_modules`. Las claves **públicas** y firmas de fixtures sintéticos se conservan para comprobar compatibilidad. Los informes históricos describen pruebas anteriores; no constituyen nuevas pruebas ejecutadas al clonar. Las evidencias locales referidas en esos informes no se distribuyen.

## Requisitos Windows

Instalar en **Windows x64** Git, PowerShell 7.4+, Eclipse Temurin JDK 25, Maven 3.9.x y Node.js 24 con npm. Instalar también **Microsoft Visual C++ v14 Redistributable x64**, requerido por el MySQL portable. Instalar estas herramientas puede requerir UAC y autorización del dueño del equipo. Esta guía no cubre Windows ARM, Linux o macOS.

Descargas oficiales: [Git](https://git-scm.com/downloads/win), [PowerShell](https://learn.microsoft.com/powershell/scripting/install/installing-powershell-on-windows), [Temurin](https://adoptium.net/temurin/releases/?version=25), [Maven](https://maven.apache.org/download.cgi), [Node.js](https://nodejs.org/en/download), [runtime Visual C++ x64](https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist). Microsoft documenta la compatibilidad del runtime v14 con aplicaciones compiladas con las herramientas Visual Studio 2017/2019/2022.

Si Maven se instala desde ZIP, extraer su distribución **binaria**, por ejemplo a `C:\Dev\apache-maven-3.9.x`, y añadir su directorio `bin` al `PATH` de usuario. Configurar `JAVA_HOME` hacia la raíz del JDK 25 —no a `bin`— y añadir `%JAVA_HOME%\bin` al `PATH`. `mvn -version` debe indicar Java 25, aunque `java -version` ya lo indique. Véase [instalación oficial de Maven](https://maven.apache.org/install.html).

Cerrar y abrir la terminal después de instalar o cambiar variables. Abrir **PowerShell 7 (`pwsh`)**, no Windows PowerShell 5.1. Comprobar:

```powershell
git --version
java -version
javac --version
mvn -version
node --version
npm --version
```

No se requiere WSL ni Docker. El helper descarga MySQL portable 8.4.11 fijado por hash; no utiliza ni modifica un servidor institucional. Maven/npm necesitan acceso a sus repositorios de dependencias. No instalar dependencias con una cuenta de producción.

Los comandos normales se ejecutan **sin elevación**, siempre con el mismo usuario Windows. Solo la edición de hosts y los instaladores que lo requieran se ejecutan elevados. Usar otro usuario para confiar la CA o generar DPAPI haría que el usuario cotidiano no pudiera reutilizarlos.

Si Windows bloquea scripts, revisar primero `Get-ExecutionPolicy -List`. Cuando la política de la organización lo permita, el usuario puede establecer `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`; no usar `Bypass` ni cambiar políticas de equipo para sortear una política institucional. Si se descargó un ZIP, comprobar su procedencia y desbloquear ese ZIP antes de extraerlo; preferir `git clone`.

## Clonar y compilar por separado

```powershell
mkdir C:\Dev\SIGER2026
cd C:\Dev\SIGER2026
git clone https://github.com/chashmdai/formulario-web.git
git clone https://github.com/chashmdai/sistema-de-requerimientos.git

cd C:\Dev\SIGER2026\sistema-de-requerimientos
.\scripts\Comprobar-Instalacion.ps1 -SoloHerramientas

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

**Desde este punto ejecutar todos los comandos en `C:\Dev\SIGER2026\sistema-de-requerimientos`, con ambos clones conectados.** No volver a preparar HTTPS desde la raíz del otro clon: generaría otra `.local`/CA. Se recomienda Edge o Chrome con la confianza de Windows del usuario actual. Firefox puede requerir su propia gestión de confianza; no omitir alertas TLS.

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

Para el último paso, abrir PowerShell 7 **como administrador** con el mismo usuario y volver a la raíz operativa mediante `cd C:\Dev\SIGER2026\sistema-de-requerimientos`. Ejecutar únicamente el script de hosts y cerrar esa consola elevada. La confianza de CA, builds y arranque se ejecutan en la consola normal. No se necesita que `sudo` esté habilitado.

Antes de iniciar, en la consola normal:

```powershell
cd C:\Dev\SIGER2026\sistema-de-requerimientos
.\scripts\Comprobar-Instalacion.ps1
```

El comprobador es de solo lectura: revisa herramientas/versiones, runtime Visual C++, JAR, archivos HTTPS, confianza local, resolución Windows y separación del origen funcionario. No descarga ni instala nada, no inicia servicios y no realiza peticiones de autenticación. Las bases, claves de comprobante y certificado funcionario se preparan automáticamente al arrancar si aún no existen; no es necesario copiar los del equipo original.

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

El TXT puede contener **solo** las dos líneas Sandbox; no es necesario entregar QA ni Producción al equipo nuevo. No rellenar valores de ejemplo como si fueran credenciales válidas. Guardarlo en una carpeta privada del usuario, con ACL restringida, no en Descargas compartidas, OneDrive público ni dentro del repositorio. Puede comprobarse su formato sin imprimirlo mediante `Comprobar-Instalacion.ps1 -ConClaveUnica`.

## Después de apagar o reiniciar Windows

Los scripts inician procesos locales, no servicios Windows ni tareas de autoarranque. Hosts y confianza del usuario permanecen, pero los procesos deben iniciarse otra vez. No reinstalar herramientas, borrar `.local`, regenerar certificados ni recompilar si no cambió el código.

```powershell
cd C:\Dev\SIGER2026\sistema-de-requerimientos
# Esta variable de proceso debe indicarse de nuevo en cada consola nueva:
$env:CU_SANDBOX_CREDENTIALS_PATH='C:\CredencialesPrivadas\DefensoriadelaNinez.txt'
.\scripts\Comprobar-Instalacion.ps1 -ConClaveUnica
.\scripts\Nucleo-Local.ps1 iniciar
.\scripts\Nucleo-Local.ps1 estado
```

Para NNA/SIGER sin CU, omitir la variable y usar `iniciar -SinClaveUnica`. Abrir las direcciones HTTPS de la tabla anterior; `localhost:5173`, `localhost:8080` o el dominio público con puerto 8443 no son equivalentes.

## Diagnóstico de instalación

| Síntoma | Qué comprobar |
|---|---|
| `mvn`/`java`/`npm` no reconocido | PATH, terminal nueva y `Comprobar-Instalacion.ps1 -SoloHerramientas`. No sustituir Java 25 por otro JDK. |
| MySQL no inicia; faltan DLL `VCRUNTIME`/`MSVCP` | Instalar runtime Visual C++ x64 oficial. Revisar logs privados `.local/robustez/mysql`; no borrar datadir. |
| Descarga de MySQL/Caddy/mkcert falla o hash no coincide | Acceso a las URLs oficiales del script y posibles proxy/antivirus corporativos. No omitir el hash ni descargar un ejecutable alternativo sin revisión. |
| Puerto 443/8443/8080/8081/13306 ocupado | Consultar `Nucleo-Local.ps1 estado` y `Get-NetTCPConnection -State Listen`. Detener solo la instancia propia con el script. No matar procesos ajenos ni cambiar callback para sortearlo. |
| Advertencia HTTPS | CA de **esta** raíz operativa confiada por **este** usuario, fechas/nombre del certificado y reloj Windows correctos. No aceptar certificados inválidos. |
| NXDOMAIN o página institucional en lugar del formulario local | `([Net.Dns]::GetHostAddresses('claveunica.formularioadulto.defensorianinez.gob.cl')).IPAddressToString` debe mostrar solo loopback. Revisar hosts y caché local. No cambiar DNS público. |
| 503 de callback | Haber arrancado sin `-SinClaveUnica`, TXT privado válido y preflight local correcto. Detener y volver a iniciar con el script, no activar el gate manualmente. |
| Acceso funcionario no disponible | Usar `https://localhost:8443/`, núcleo iniciado con el script madre, contraseña generada en este PC consultada con `acceso`. No usar una contraseña copiada del equipo anterior. |
| Una Comunicación queda pendiente | SIGER encendido, controles habilitados por el arranque, confianza HTTPS y recuperación saliente. Conservar bases y revisar logs privados de ambos backends; no reenviar inventando otra identidad. |

Si aparece “Arranque incompleto”, ejecutar primero `Nucleo-Local.ps1 detener` para retirar únicamente los procesos propios que alcanzaron a iniciarse, corregir la causa y reintentar. No publicar logs completos ni pasarlos a Git.

**Alcance de la reproducción:** el manual permite instalar el código y crear un laboratorio nuevo con datos sintéticos. No traslada los datos del PC original ni otorga acceso institucional nuevo. La aceptación del login Sandbox depende de credenciales autorizadas y disponibilidad del proveedor. Todavía no se ha ejecutado esta guía completa en un segundo PC limpio; los pasos locales y builds desde clones separados se verificaron en el equipo original. Las dos baterías de GitHub Actions iniciales también terminaron correctamente en runners Windows independientes, pero no prueban hosts/CA ni el login Sandbox.

Comprobador añadido el 23-10-2026: ejecución positiva de herramientas desde ambos clones y comprobación completa con Sandbox en el workspace preparado; casos negativos de clon sin HTTPS y TXT Sandbox inexistente detectados correctamente. No se modificaron hosts, confianza, servicios ni credenciales durante estas comprobaciones.

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
