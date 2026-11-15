# Procedencia SRCeI del RUN del presentador NNA

Nota aditiva del 15/11/2026, aplicable a nuevas capturas del documento completo 1.0 y 1.1. No altera sus esquemas, algoritmos, contratos firmados, fixtures existentes ni firmas históricas.

`runNumero.fuente=SRCEI` significa **contrastado en fuente registral, no autenticado**. El RUN fue declarado por quien presenta y coincide con el de la respuesta registral. No demuestra que esa persona sea su titular. Se conserva `autenticada=false` y `nivel=CONTRASTADO_EN_FUENTE`.

Hoy la promoción de procedencia del RUN/DV declarado solo aplica a `presentador-nna`, rol `PRESENTADOR`, sin ClaveÚnica, con fecha de nacimiento registral y contraste explícito del RUN en la respuesta. Permite demostrar la correspondencia RUN/DV/fecha para la decisión de edad del recorrido NNA. Los tres atributos deben compartir la referencia de evidencia de la consulta; la decisión de mayoría de edad exige además una consulta exitosa y valores consistentes. Los adultos declarados mantienen `DECLARACION_CIUDADANA`; ClaveÚnica conserva su procedencia de autenticación propia. No se generaliza esta regla a otras personas del formulario.

**SIGER debe confirmar su consumidor antes de desplegar**: admitir esta procedencia ya representable en 1.0/1.1, preservarla sin elevarla a autenticación, comprobar huellas y firmas y mantener COMMIT previo al ACK. La aceptación por el núcleo del agente de este repositorio no acredita la instalación ni el consumidor definitivo de SIGER.

## Vectores y regeneración

Los seis archivos nuevos están enumerados en el [índice](README.md#vectores-aditivos-nna-con-procedencia-srcei-15112026). Contienen exclusivamente identidad ficticia, DV matemáticamente válido, evidencia/UUID y fechas fijos. El documento 1.0 no tiene adjuntos; el 1.1 describe un TXT cuyos bytes están definidos por `NnaSrceiFixtures.original()`. Cada versión tiene su ACK sintético correspondiente.

El [generador Java](../formulario-publico/backend/src/test/java/cl/nexbyte/siger/robustez/NnaSrceiFixtures.java) sigue el mecanismo del generador histórico `RecepcionJdbcTest.nnaProcedenciaNoPuedeElevarseYExportaVectorInteroperable`: pares Ed25519 efímeros en memoria, firma con las clases de producción y exportación exclusiva de documentos y claves públicas. Usa un par nuevo del Formulario y otro institucional sintético, independientes. Nunca escribe las privadas. Estructura, IDs y metadatos son reproducibles; las claves y firmas cambian en cada ejecución. Solo crea un directorio nuevo y usa `CREATE_NEW`; regenerar no autoriza reemplazar fixtures existentes.

Desde la raíz, después de `test-compile`, obtener el classpath de pruebas y ejecutar el generador (rutas de ejemplo en `/tmp`, fuera de Git):

```bash
mkdir -p /tmp/codex-jtmp-u6 formulario-publico/backend/target
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=/tmp/codex-jtmp-u6"
mvn -B -ntp -q -f formulario-publico/backend/pom.xml \
  -Dsiger.build.directory=/tmp/codex-target-u6 -DskipTests test-compile \
  dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=/tmp/codex-u6-classpath
java -cp "/tmp/codex-target-u6/test-classes:/tmp/codex-target-u6/classes:$(cat /tmp/codex-u6-classpath)" \
  cl.nexbyte.siger.robustez.NnaSrceiFixtures /tmp/nna-srcei-fixtures-nuevos
```

## Regresiones y alcance por plataforma

`NnaSrceiContratoTest` comprueba firmas, huellas, evidencia compartida, ausencia de autenticación, alteraciones de fuente/evidencia y recepción real 1.0/1.1 con proveedor registral y scanner controlados. La recepción 1.1 incluye carga FILE y negociación opt-in. También conserva las excepciones adulto declarado y NNA sin fecha registral.

`NnaSrceiAgenteCoreTest` comprueba en Linux `GarantiaCustodia.contexto`, el validador que usa el agente, para ambos documentos con personas y sus ACK; rechaza confianza institucional ausente y ACK de otra operación. Además ejecuta `RespaldoIngresoVerificado.ensayar`, el mismo núcleo usado por `AgenteCustodiaNativo`, sobre ambos fixtures con ACK y originales sintéticos. Comprueba aceptación/restauración, preservación de procedencia y rechazo de manipulación antes de publicar el paquete. Usa SELECT simulados, no una BD institucional, y no emite garantías. Puede ejecutarse junto al perfil `-Pagente-core` del backend. La restauración requiere un directorio temporal POSIX fuera de cualquier árbol con `.git`, incluidos sus ancestros; cambiar `java.io.tmpdir` a un hijo no sirve si el sandbox marca `/tmp` con `.git`.

La CI Windows conserva las comprobaciones exclusivas de NTFS/ACL, funciones nativas, interrupciones y recuperación en `PruebaAgenteNativo`; su escenario existente con lista de personas vacía no demuestra por sí solo esta procedencia. La nueva regresión con personas corre en el job Linux, no reemplaza las pruebas físicas Windows ni confirma el consumidor de SIGER. El frontend no lee estos fixtures nuevos.
