# Agente de custodia nativo · Windows Server 2022

**Unidad de laboratorio del 25/10/2026.** Baseline `25560065a73e6f42d6ab9419ac02351ebc66cc5e`; desarrollado en `agente-windows-nativo-2026-10-25` y consolidado en main por autorización posterior. Publicar este código no activa el agente ni la tarea programada. El agente es auxiliar: SIGER mantiene sus operaciones y su custodia sin depender de él. No se modificó, compiló, migró ni desplegó SIGER.

La ejecución nativa está comprobada para el mecanismo sintético. **La emisión hacia un Formulario en servicio está bloqueada.** No hay medio institucional independiente y el certificado MySQL no identifica a localhost. Esto no se resuelve cambiando letras de unidad o desactivando TLS.

## Qué se entrega

| Componente | Función |
|---|---|
| `ArchivoAgente` | Abstracción acotada: POSIX existente o NTFS/NIO/Kernel32; rutas, ACL, reparse/hardlinks, archivos privados, sincronización y publicación atómica |
| `RespaldoIngresoVerificado` | Conserva lógica compartida de COM/documentos/ACK, verificación y restauración; añade `ensayar`, que devuelve un resultado **sin firma ni envío** |
| `AgenteCustodiaNativo` | CLI sin arranque de aplicación Spring ni servidor web, configuración externa, stdin anónimo para secreto, lock entre procesos, inspección/ensayo/TLS/emisión protegida |
| `EmisorGarantiaCustodia` | Mantiene Linux; parser JDBC exige VERIFY_IDENTITY exacto y rechaza duplicados/alias inseguros. El entrypoint legado también bloquea envío Windows antes de cualquier conexión pública |
| `windows/Invoke-AgenteCustodia.ps1` | DPAPI → pipe anónimo → Java 25, logs acotados por ejecución, timeout/kill y códigos de salida |
| `windows/Preparar-TareaAgente.ps1` | Genera XML deshabilitado con identidad original; **no registra ni inicia tareas** |
| `qa/` | Harness separado, sonda TLS sin autenticación, comprobación de identidad y DDL sintético revisable |

Se reutilizan las clases compiladas de contratos y criptografía del backend mediante el clasificador `agente-core`; no se copia ni reimplementa su lógica. El JAR independiente contiene dependencias JDBC/Jackson/Hikari y código común, sin recursos web, controlador público ejecutable, Tomcat, Spring Boot runtime, migraciones ni H2. Algunas clases comunes no utilizadas permanecen en el clasificador; no es una instalación del Formulario ni tiene su clase de arranque. H2 y las clases QA se instalan solo bajo testdata.

## Artefacto instalado

- Versión: **`1.0.0-windows-lab`**, Java 25.
- JAR: **`C:\agente\bin\agente-custodia-1.0.0-windows-lab-d3ab5a3179ee.jar`**.
- Tamaño: **11 983 069 bytes**.
- SHA-256: **`d3ab5a3179ee6673a5c21cf1951109a4b8af2f0724e390e4912aef823d5623c0`**.
- Recompilaciones consecutivas produjeron bytes idénticos. `project.build.outputTimestamp` está fijado; el JAR de proyecto se recrea antes de sombrear para no reutilizar clases de una distribución previa.
- Los candidatos anteriores se conservaron con nombres que incluyen su hash. El archivo señalado arriba es el artefacto de entrega; no usar un candidato antiguo por su fecha.

Compilación con herramientas Linux/WSL existentes, desde la raíz del repositorio:

```bash
bash agente-custodia/build.sh
```

El script compila/instala el clasificador en Maven local y ensambla `.local/agente-dist/agente-custodia-1.0.0-windows-lab.jar`. Usa salidas aisladas, no despliega ni ejecuta migraciones. `-DskipTests` es solo construcción; la regresión se ejecutó separadamente. No modifica Java/Maven global de Windows ni construye SIGER. No necesita npm para obtener un agente sin UI.

## NTFS, permisos y durabilidad

No se reemplazaron ACL de `C:\agente` ni de sus subdirectorios preparados. Se inspeccionan antes de operar. Los nuevos temporales y archivos se crean con ACL privada mediante Java NIO, desde su creación. La política de laboratorio admite al usuario ejecutor, SYSTEM, Administradores y la plantilla CREATOR OWNER solo heredable; rechaza otros accesos ALLOW. También valida NTFS local y lectura efectiva.

Las rutas admitidas son absolutas locales, sin ADS, componentes ambiguos, nombres reservados o redirecciones. Se rechazan UNC, enlaces, junctions y otros reparse points en cada antecesor; `GetFileAttributesW` y `GetFileInformationByHandle` detectan reparse y número de hardlinks. No se habilitan privilegios de backup ni se abre un volumen. Las ACL privadas limitan carreras de modificación de rutas por usuarios ajenos; no se afirma resistencia frente a un administrador comprometido.

Para sincronización Windows se emplean Java 25 FFM y Kernel32, sin DLL ni librería adicional: `CreateFileW` con handle de directorio de escritura, `FILE_FLAG_BACKUP_SEMANTICS` y `FILE_FLAG_OPEN_REPARSE_POINT`, comprobación del handle y `FlushFileBuffers`. En el laboratorio:

- Handle de lectura: abre, pero Flush devuelve error 5.
- Handle de escritura: abre y Flush devuelve éxito.
- El flujo del agente usa el segundo; un error no se convierte silenciosamente en sincronización satisfecha.

Se fuerza el contenido por `FileChannel.force(true)`, se verifican hashes y metadatos, se publica mediante rename atómico y se sincroniza el directorio padre. Un paquete existente no se sobrescribe; discrepancias/corrupción bloquean, sin reconstruirlo desde el original. [Microsoft documenta los handles de directorio](https://learn.microsoft.com/en-us/windows/win32/fileio/obtaining-a-handle-to-a-directory) y la [detección de reparse points](https://learn.microsoft.com/en-us/windows/win32/fileio/reparse-point-operations).

**`durabilidadAcreditada=true` significa que se completó la secuencia de sincronización de la API.** No certifica resistencia del NVMe/caché ante pérdida de energía. Esa calificación física sigue pendiente, como la restauración en un equipo/medio independiente. En POSIX se conserva fsync de directorios y archivos; las regresiones Linux verificaron el camino original.

## Independencia y emisión: bloqueo explícito

C: y D: se comprobaron como particiones del **mismo DiskNumber 0**. Scratch es transitorio; el USB no fue seleccionado como archivo institucional. Otra letra, otro volumen o una copia en este host no acreditan independencia.

La implementación Windows devuelve **independencia no acreditada**; no existe una propiedad para convertirla en verdadera. El envío devuelve código **42 antes de contactar el Formulario**. Los métodos de emisión compartidos tampoco producen una garantía Windows. El ensayo no firma; las firmas sintéticas de QA usan claves efímeras no confiadas por ningún Formulario y no se transmiten.

Habilitar emisión requerirá una unidad revisada con medio independiente real, evidencia de topología/recuperación, calificación de la sincronización y controles específicos. No sustituir esa prueba por una excepción de configuración. La firma/domain/canonicalización de `GarantiaCustodia`, `FirmaOperativa`, documentos 1.0/1.1 y ACK **no se modificaron**.

## Configuración y secretos

Configuración instalada: `C:\agente\config\custodia-nativa-lab.properties`, sin contraseña; directorios de origen/respaldo sintéticos y scratch propios. No contiene origen HTTPS público ni claves de emisión. La [plantilla](windows/agent-lab.properties.example) orienta la configuración; el entorno MySQL sigue bloqueado.

El launcher nativo usa `Import-Clixml` **bajo `DEFENSORIANINEZ\nexbyte.proveedor`**, recupera la credencial de aplicación y entrega un JSON de tamaño acotado por el stdin anónimo del hijo. Java lee ese canal en memoria. No se usa contraseña en argumentos, variables de entorno, archivos de propiedades, logs o Git. No se importa ni exporta la credencial root. No se supone que Java entienda un PSCredential serializado. Las cadenas temporales del CLR/JVM no son almacenamiento persistente ni una garantía de borrado forense de RAM.

PowerShell instalado y usado por los scripts: **7.4.6**. La herramienta de desarrollo estaba hospedada en 7.6.5; no se confunden sus versiones. Java predeterminado y JAVA_HOME global permanecieron en **21.0.3**. El agente y el compilador de QA se invocaron mediante la ruta absoluta de **Java 25.0.4.1**. La política de ejecución no se modificó: los scripts se instalaron localmente en scripts; no se usó bypass para ejecutar desde UNC.

## MySQL: contrato y bloqueo TLS demostrado

El agente lee `institutional_inbox`, `communication_registry` e `institution_document`: body/ACK completos, registro COM/folio/canal/huellas y descriptores/objetos con origen `INGRESO_CIUDADANO`. No escribe esas tablas ni introduce reglas de negocio. Una cuenta de aplicación de laboratorio con permisos amplios no sustituye una cuenta SELECT de producción.

El servidor observado en localhost:3306 presentó TLS 1.3 y certificado:

- CN `MySQL_Server_8.0.46_Auto_Generated_Server_Certificate`.
- SAN ausente.
- SHA-256 público `5a3afab25b9b6ecf50abd789f3d2d3f8c792dda41e6cadf70a873f91a7b2ef2e`.

Una sonda **sin usuario/contraseña ni SQL** obtuvo ese certificado público; otra cargó la confianza observada y exigió identidad localhost: el handshake rechazó la identidad. El launcher con DPAPI y JDBC VERIFY_IDENTITY devolvió **40**, sin una sesión SQL utilizable. No se deshabilitó TLS, alteró MySQL80, cambió su certificado/configuración ni reinició servicios. [Connector/J exige comprobar confianza y nombre del servidor con VERIFY_IDENTITY](https://dev.mysql.com/doc/connector-j/en/connector-j-server-authentication.html).

`mysql-lab-observado.cer/.p12` son material público de diagnóstico privado, no una CA institucional aprobada; no se instaló confianza global. La sonda que observa certificados sin autenticar está en QA, **excluida del JAR operativo**, y nunca devuelve una conexión autenticada al agente.

[DDL sintético mínimo](qa/schema-mysql-lab.sql), copiado a `C:\agente\testdata\qa-native\schema-mysql-lab.sql`: tres tablas, solo en `siger_custodia_lab`, sin DROP/TRUNCATE ni migraciones institucionales. **No se aplicó a MySQL80** por el bloqueo TLS; tampoco se afirma que se hayan inspeccionado sus tablas. El harness positivo usa esas referencias contractuales sobre H2 **en memoria, exclusivamente QA**, no una nueva base del diseño productivo ni otra instancia MySQL.

La corrección pendiente es un certificado/CA de confianza con identidad local válida, mediante una intervención autorizada que evalúe los clientes antiguos de MySQL80. No usar REQUIRED/VERIFY_CA como sustitución ni cambiar hosts para disfrazar la identidad.

## Automatización preparada y códigos

Instalados en `C:\agente\scripts`: launcher, preparador de tarea, helper de enlaces de QA y probe de durabilidad. Las revisiones anteriores del launcher se preservaron como `.previous-HASH`.

Definición final: `C:\agente\config\tarea-custodia-nativa-20261025-d3ab5a3179ee.xml`. Settings y trigger están **disabled**. No se registró ni inició una tarea. Contiene:

- Periodicidad 15 minutos, IgnoreNew y lock real por archivo entre procesos.
- Timeout de tarea 10 minutos y del launcher 540 s; tres reintentos cada cinco minutos.
- Identidad Windows original, token interactivo y run level limitado para laboratorio; no almacena contraseña de inicio de sesión.
- Acción inicial Inspeccionar. La identidad no interactiva definitiva y su DPAPI deben calificarse aparte.

| Código | Significado |
|---:|---|
| 0 | Inspección/ensayo o verificación solicitada completados |
| 12 | Otra ejecución posee el lock |
| 20 | Fallo seguro de configuración, permisos o integridad |
| 30 | Timeout; proceso/hijos terminados, sin borrar paquetes |
| 40 | Identidad TLS MySQL no verificada |
| 42 | Custodia no acreditada; sin conexión pública ni emisión |

Los logs JSON contienen fecha/modo/código y mensajes genéricos, sin argumentos, excepciones JDBC, cuerpos o secretos. El reporte de salida se acota a 8 KiB; la retención/rotación de logs y su monitor definitivo deben incorporarse al procedimiento operativo aprobado. La configuración no captura dumps de memoria con secretos.

El janitor se limita a staging/restauraciones propios de más de 24 h, lote 20 y bajo exclusión, nunca origen ni paquetes publicados. El harness prueba terminación abrupta, liberación del lock por el sistema y limpieza del temporal vencido. Cancelar no autoriza eliminar evidencia institucional. No se promete limpieza inmediata tras un kill que impida finally.

## Pruebas realizadas y alcance

| Plataforma | Evidencia |
|---|---|
| Windows nativo, documento 1.0 | **33 verificaciones correctas** |
| Windows nativo, documento 1.1 | **33 verificaciones correctas** |
| Windows / launcher | Inspección 0; bloqueo de envío 42; TLS 40; timeout real 30 seguido de inspección 0 |
| Windows / Scheduler | XML generado y validado disabled; ninguna tarea registrada por esta unidad |
| Linux / backend H2 | **266 descubiertas, 264 correctas, dos omisiones opcionales**, cero fallos/errores |
| Linux / MySQL Formulario aislado | Mismo resultado, exclusivamente `robust_public_test`; no es MySQL80 de Windows |
| Python existentes | Nueve + siete correctas |
| Artefacto | Builds reproducibles y auditoría de contenido, sin UI/web/H2/migraciones/sonda |

Las verificaciones nativas abarcan ACL privadas/amplias, rutas/ADS/UNC, hardlinks/junctions, hashes, firmas válidas/falsas, corrupción, paquete no reescrito, repetición, restauración en otro proceso con claves públicas persistidas, concurrencia de procesos, halt y recuperación de temporales. Las claves privadas de QA permanecen efímeras; no se instalaron claves institucionales ni se enviaron garantías.

La restauración entre procesos reconstruye la BD H2 del harness desde el paquete de prueba; **no equivale a una recuperación MySQL institucional**. No se prueba un corte físico de energía, hardware independiente, volúmenes de 150/250 MiB ni instalación productiva. Los siete tests nuevos de portabilidad/secreto/TLS están incluidos en las 266 regresiones, no sumados dos veces. Las dos omisiones heredadas son workers multimedia reales y recuperación de proceso opt-in. No se deshabilitaron pruebas existentes. No se ejecutó CI remota de esta rama local.

## Comandos reproducibles de laboratorio

Todos los ejemplos PowerShell son líneas independientes, sin continuaciones. Ejecutarlos bajo la identidad DPAPI original; no copiar contraseñas al prompt.

```powershell
& 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe' '--enable-native-access=ALL-UNNAMED' -jar 'C:\agente\bin\agente-custodia-1.0.0-windows-lab-d3ab5a3179ee.jar' --version
```

```powershell
& 'C:\Program Files\PowerShell\7\pwsh.exe' -NoProfile -File 'C:\agente\scripts\Invoke-AgenteCustodia.ps1' -Jar 'C:\agente\bin\agente-custodia-1.0.0-windows-lab-d3ab5a3179ee.jar' -Config 'C:\agente\config\custodia-nativa-lab.properties' -Modo Inspeccionar
```

```powershell
& 'C:\Program Files\PowerShell\7\pwsh.exe' -NoProfile -File 'C:\agente\scripts\Invoke-AgenteCustodia.ps1' -Jar 'C:\agente\bin\agente-custodia-1.0.0-windows-lab-d3ab5a3179ee.jar' -Config 'C:\agente\config\custodia-nativa-lab.properties' -Modo VerificarTLS
```

```powershell
& 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe' '--enable-native-access=ALL-UNNAMED' -cp 'C:\agente\bin\agente-custodia-1.0.0-windows-lab-d3ab5a3179ee.jar;C:\agente\testdata\qa-native\h2-2.4.240.jar;C:\agente\testdata\qa-native\classes' cl.nexbyte.siger.custodia.PruebaAgenteNativo --pruebas 'C:\agente\testdata\custodia-nativa-20261025' 1.1
```

El harness se compila con javac.exe 25 por ruta absoluta y `-encoding UTF-8`, usando el mismo classpath JAR/H2 y la fuente `qa/PruebaAgenteNativo.java`, con salida en `C:\agente\testdata\qa-native\classes`. No se incluye en el JAR operativo. Usar 1.0 en la última línea para repetir esa versión. El harness crea un directorio nuevo por ejecución y conserva los anteriores.

Para preparar otra definición revisable, usar un destino NUEVO; esto no registra ni activa nada:

```powershell
& 'C:\Program Files\PowerShell\7\pwsh.exe' -NoProfile -File 'C:\agente\scripts\Preparar-TareaAgente.ps1' -Jar 'C:\agente\bin\agente-custodia-1.0.0-windows-lab-d3ab5a3179ee.jar' -Config 'C:\agente\config\custodia-nativa-lab.properties' -Destination 'C:\agente\config\tarea-custodia-revision-nueva.xml'
```

Regresión backend, con salida aislada; MySQL requiere las variables privadas de prueba habituales y el esquema autorizado, según el [README principal](../README.md):

```bash
mvn -B -ntp -f formulario-publico/backend/pom.xml -Dsiger.build.directory="$(pwd)/.local/agente-regresion-target" '-Dtest=*Test,!Carga*Test' verify
```

## Rollback y condiciones definitivas

No hay tarea activa ni esquema Windows migrado que deshacer. Para retirar la instalación de uso, dejar de invocar el launcher y conservar JAR/hash, XML, logs y testdata como evidencia. Puede marcarse la configuración como fuera de uso mediante un rename local revisado, sin tocar originales ni credenciales:

```powershell
Rename-Item -LiteralPath 'C:\agente\config\custodia-nativa-lab.properties' -NewName 'custodia-nativa-lab.properties.disabled'
```

No borrar paquetes, cambiar ACL preparadas, regenerar claves, eliminar DPAPI, reiniciar MySQL/otros JAR ni modificar JAVA_HOME/PATH. No registrar la tarea como parte de esta revisión. Si más adelante se instala una tarea, su rollback debe identificarla exactamente y detener solo esa tarea bajo autorización de operación.

Pendientes concretos: medio independiente aprobado y evidencia de recuperación física; certificado/CA MySQL con identidad correcta; cuenta SELECT y principal/ACL productores institucionales definidos sin adaptar SIGER al agente; calificación de hardware, sincronización y un timeout con tamaños reales; identidad no interactiva/DPAPI y política de logs; revisión específica antes de habilitar emisión Windows. La política ACL actual es cerrada para el laboratorio; no admite automáticamente SIDs productores adicionales ni todos los idiomas de Administradores.

Los laboratorios Linux del Formulario, sus bases V7/V8 y banderas de retirada no se alteraron. Las migraciones V1–V11 y contratos firmados permanecen intactos. El historial de la rama de desarrollo se conserva en main. **La publicación no implica despliegue institucional, registro de tarea ni habilitación de emisión**.
