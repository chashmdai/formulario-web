# Ejecución estable de JAR en los laboratorios HTTPS

## Diagnóstico y alcance

El lanzador integrado `arrancar-nna-local.py` ejecutaba directamente `formulario-publico/backend/target/formulario-publico-0.1.0-SNAPSHOT.jar`. `mvn clean/package` podía borrar o reemplazar el archivo mientras Java conservaba clases e índices de los JAR anidados en memoria. La incidencia del 26/10 mostró conexiones TLS válidas, errores de carga de clases y peticiones HTTP sin respuesta.

El lanzador multimedia ya copiaba el build a `runtime.jar` fuera de Maven, comprobando el puerto antes de reemplazarlo. Estaba aislado del build ordinario, pero la ruta era mutable y su registro solo guardaba el PID. La recuperación manual de `18443` había dejado una copia privada correcta que el lanzador antiguo no reconocía para detenerla.

Se modifica únicamente la gestión del ejecutable: dos lanzadores, un helper Python estándar compartido, pruebas y documentación. `compilar.sh` conserva su comportamiento de build sin despliegue. No se cambia Java de negocio, frontend, POM, contratos, firmas, migraciones, datos ni políticas.

## Separación entre construir, instalar y ejecutar

Cada directorio privado existente conserva su configuración y recibe:

```text
DIRECTORIO_PRIVADO/
  application.properties        # Configuración anterior, sin regenerar ni modificar
  jars/SHA256.jar                # Copias completas, sin vínculo con el inode de Maven, modo 0400
  selected-jar.json              # Versión seleccionada explícitamente para el próximo arranque
  active-runtime.json            # PID, inicio Linux, ejecutable Java, JAR/SHA y ruta/hash de configuración
  app.pid                       # PID en el formato anterior
  launcher.lock                 # Exclusión entre invocaciones de este laboratorio
  app.log                       # Registro privado anterior
```

`jar_laboratorio.py` copia por buffers a un temporal privado, verifica ZIP/manifiesto ejecutable, calcula SHA-256, sincroniza y publica sin reemplazar una versión existente. No usa hardlinks contra la entrada de Maven. La publicación usa un enlace atómico únicamente entre el temporal **ya copiado** y su nombre final; el temporal se retira y queda un solo enlace al inode privado. Reinstalar los mismos bytes reutiliza la copia verificada. Una copia diferente o dañada no se sobrescribe para ocultar el problema.

Las copias son inmutables para los lanzadores y de solo lectura para la operación ordinaria; esto no equivale a impedir que un administrador cambie permisos o archivos deliberadamente. El hash detecta desviaciones. Las compilaciones normales no conocen ni escriben este almacén, ubicado fuera de Git y `target`. No se agrega limpieza automática de versiones.

No hay selección del JAR más reciente, fallback a `target`, actualización automática ni reinicio al compilar. Un laboratorio nuevo necesita `--jar RUTA` explícito o una copia instalada y seleccionada. Las configuraciones y secretos nunca se incorporan al JAR ni a los registros JSON.

## Estado y control de procesos

Ambos lanzadores conservan las guardas de su ambiente y agregan las mismas operaciones. Para `18443`, conservar `--permitir-cu-sandbox` cuando corresponde a los callbacks ya aprobados; ese opt-in no consulta al proveedor.

```bash
LAB=/home/nexbyteproveedor/.local/share/siger/nna-local-20261023
python3 scripts/linux/arrancar-nna-local.py "$LAB" --estado --permitir-cu-sandbox
MEDIA=/home/nexbyteproveedor/.local/share/siger/multimedia-lab
python3 scripts/linux/arrancar-multimedia-local.py "$MEDIA" --estado
```

`--estado` no inicia Java. Informa PID, ticks de inicio en `/proc`, ejecutable Java, ruta/SHA del JAR, ruta/hash de configuración y concordancia con el registro de arranque. No imprime valores de propiedades, argumentos secretos, cookies o respuestas ciudadanas. Un cambio del archivo de configuración se distingue de lo que se registró al arrancar; editarlo no recarga el proceso.

Las invocaciones se serializan con `flock`. No se arranca ni selecciona otra versión si el proceso sigue activo o el puerto está ocupado. La parada comprueba el proceso propietario, argumentos completos, configuración, ticks y ejecutable; usa `pidfd` para enviar SIGTERM al proceso identificado sin señalizar otro PID reutilizado. Registros discordantes se rechazan para revisión, sin intentar matar procesos ajenos. La salida dice **detención solicitada**, no presume que terminó.

Se reconocen también procesos anteriores con JAR directamente en el directorio privado y configuración coincidente: se informa `registrado=false`. No se adopta automáticamente un proceso que ejecute desde `target`. Tras una recuperación autorizada, el siguiente arranque utiliza el almacén versionado. Un PID inexistente o zombie no se trata como una instancia activa.

## Preparar una actualización sin intervenir la instancia

Compilar por el procedimiento habitual. Con el JAR terminado, instalarlo para revisión:

```bash
python3 scripts/linux/arrancar-nna-local.py "$LAB" --instalar /ruta/al/build.jar --permitir-cu-sandbox
python3 scripts/linux/arrancar-multimedia-local.py "$MEDIA" --instalar /ruta/al/build-multimedia.jar
```

Cada comando devuelve ruta y SHA completos. Solo crea/reutiliza una copia privada; no cambia la selección, no migra y no ejecuta el artefacto. Puede preparar una versión mientras la anterior funciona. No instalar el último `main` en estos laboratorios por el solo hecho de haberlo compilado: comprobar compatibilidad de esquema, contratos y configuración antes de seleccionarlo.

## Actualizar y revertir con autorización

La actualización necesita una ventana y autorización para detener/arrancar. No hay comando de reinicio automático:

```bash
python3 scripts/linux/arrancar-nna-local.py "$LAB" --detener --permitir-cu-sandbox
python3 scripts/linux/arrancar-nna-local.py "$LAB" --estado --permitir-cu-sandbox
python3 scripts/linux/arrancar-nna-local.py "$LAB" --seleccionar SHA256_COMPLETO_INSTALADO --permitir-cu-sandbox
python3 scripts/linux/arrancar-nna-local.py "$LAB" --permitir-cu-sandbox
```

Esperar estado DETENIDO y puerto libre antes de seleccionar. Para multimedia, las mismas operaciones usan `arrancar-multimedia-local.py "$MEDIA"` y no llevan el opt-in ClaveÚnica. `--jar RUTA` es un atajo explícito de instalar/seleccionar/arrancar, permitido solo con el laboratorio detenido; no se utiliza en el proceso de build.

Reversión: detener con autorización, seleccionar el **SHA anterior conservado**, arrancar y verificar HTTPS/recursos/estado. No se reemplazan JAR históricos, claves, certificados ni configuración. La reversión binaria no revierte Flyway ni garantiza compatibilidad con un esquema posterior. No se utiliza `--migrar` durante una actualización/reversión ordinaria; la opción anterior se conserva solo para una intervención separada y explícita. Preparar o consultar nunca la ejecuta.

El registro de arranque indica `readinessVerificada=false`: crear un PID no prueba disponibilidad. Verificar la URL y los recursos estáticos con TLS/hostname correctos, y los controles funcionales necesarios antes de declarar la actualización sana.

## Recuperación autorizada de este laboratorio

Al comenzar esta unidad, `18443` y `19443` no escuchaban desde Windows ni WSL y los PID previos no existían. No se detuvieron servicios. El responsable autorizó expresamente recuperar ambas instancias con sus artefactos anteriores, sin migraciones ni cambios de configuración.

| Laboratorio | SHA-256 del artefacto anterior recuperado | PID observado |
|---|---|---:|
| Integrado `18443`, fuente histórica `c1e4ead` | `c3b544b1cddf30aef5086cb2a632ac7265040a57a75612a558332e04f4240be9` | 6545 |
| Multimedia `19443`, artefacto previo del laboratorio | `24ccc1674bc3be3168158997a3d4c8665493933a14febc96ff51945230381d27` | 6579 |

Los PID son evidencia puntual, no constantes operativas. Consultar siempre `--estado`. Ambos ejecutan ahora `jars/SHA256.jar` dentro de sus respectivos directorios privados, conservando las copias anteriores. Los SHA de configuración coinciden antes/después de arrancar y de compilar.

URLs conservadas:

- `https://claveunica.formularioadulto.defensorianinez.gob.cl/?vista=inicio` — Caddy existente `443` hacia `18443`.
- `https://claveunica.formularioadulto.defensorianinez.gob.cl:18443/?vista=inicio` — integrado directo.
- `https://claveunica.formularioadulto.defensorianinez.gob.cl:19443/?vista=inicio` — multimedia aislado.

No se migraron los esquemas V7/V8, no se desplegó el JAR nuevo de `main`, no se modificaron MySQL, SIGER, PISEE, ClaveÚnica, proxy, certificados, claves, ni banderas de mantenimiento/retirada o política de 72 h. Las peticiones de verificación fueron GET locales, sin Comunicaciones ni proveedores gubernamentales.

## Evidencia de pruebas

Pruebas reproducibles sin BD ni proveedores:

```bash
python3 scripts/qa/test_runtime_jar.py
python3 scripts/qa/test_media_archive.py
python3 scripts/qa/test_media_storage_report.py
```

El primer conjunto utiliza JAR diminutos compilados con el JDK existente y JVM propias en puertos loopback efímeros. Cubre copia independiente, overwrite/clean del build, solo lectura, reinstalación idempotente, corrupción/enlaces, ausencia de fallback, puerto ocupado, `flock` entre procesos, identificación de PID/configuración sin secretos, versiones simultáneas preparadas, actualización/reversión y reconocimiento legacy. Las pruebas nuevas se incorporan al job Linux de CI; no requieren infraestructura privada.

**Prueba Maven real con los dos laboratorios activos:** `mvn -B -ntp -DskipTests clean package`, con heap de build 512 MiB y dos procesadores lógicos como máximo para la JVM de Maven. Finalizó correctamente. Borró/reconstruyó `backend/target` y produjo un SHA distinto del build anterior; ese JAR nuevo quedó únicamente como salida de compilación. En 14 rondas se comprobaron las tres entradas HTTPS: **42 respuestas HTTP 200** antes/durante/después. Los dos PID, ticks, SHA de JAR y hashes de configuración permanecieron iguales.

Resultados finales locales: **17 pruebas nuevas de lanzadores, 9 de respaldo/restore y 7 de inventario**, todas correctas (**33 en total**). Sintaxis Python y shell, enlaces locales nuevos y `git diff --check` correctos. HTTPS oficial también comprobado desde Windows con `curl.exe` y validación TLS normal, HTTP 200. No se atribuye una inspección visual nueva de Chrome a estas peticiones HTTP.

No se presenta este build con tests omitidos como una nueva regresión Java completa. El cambio afecta lanzadores Python; las pruebas de backend/frontend históricas siguen documentadas en sus cierres. La nueva batería está preparada en CI Linux, pero no se ha ejecutado remotamente en esta rama porque no hubo push. Los logs y JSON completos de la verificación quedan en `.local/evidencias/runtime-jars-*`, ignorados por Git, sin incluirlos como artefactos públicos.

Pendientes operativos: cualquier actualización futura de versión o migración requiere su propia autorización y revisión de compatibilidad. No se activan tareas de arranque automático ni servicios nuevos. No hay push en este encargo.
