# GATE 4A.14.2 · Región y comuna obligatorias en vulneración adulta

Fecha: 26/10/2026. Base: `e94225d1fa0d0d62f2be0b5ca9375e9f4a7fa9e3`.
Rama local: `fix-territorio-vulneracion-gate4a14-2`. Sin push ni despliegue.

## Corrección acotada

Para el flujo adulto, con o sin ClaveÚnica, cuando `motivo=vulneracion`:

- `hechos-region` y `hechos-commune` son obligatorios: se rechazan ausencia, cadena vacía y espacios.
- La región debe existir en el catálogo y la comuna debe pertenecer a esa región.
- Se conservan los identificadores. La validación del paso de selección del motivo sigue permitiendo avanzar antes de completar los hechos; la revisión y recepción completas exigen ambos campos.
- Los controles existentes reciben `required`, marcas de obligatoriedad y errores accesibles desde las reglas React. Se reemplaza la indicación contradictoria «si lo conoces» del bloque de los hechos.

No se modifica el catálogo común ni se endurece su método de comuna opcional: la obligatoriedad nueva se aplica exclusivamente en la rama adulta de vulneración. NNA independiente mantiene región obligatoria y comuna opcional; los datos territoriales del NNA afectado conservan su contrato y opcionalidad.

No hay cambios a ClaveÚnica, PISEE/SRCeI, custodia, firmas, formatos 1.0/1.1, migraciones o configuración de recepción/retirada. La recepción continúa resolviendo las repeticiones ya persistidas antes de validar un ingreso nuevo; no se reescriben Comunicaciones históricas.

## Archivos

| Archivo | Ajuste |
|---|---|
| [ValidacionAdultos.java](../formulario-publico/backend/src/main/java/cl/nexbyte/siger/formulario/ValidacionAdultos.java) | Exige los dos campos antes de validar el catálogo |
| [adultRules.ts](../formulario-publico/front/src/formulario/adultRules.ts) | Obligatoriedad condicional y etiquetas de error |
| [AdultStepThree.tsx](../formulario-publico/front/src/formulario/AdultStepThree.tsx) | Instrucción coherente con los campos obligatorios |
| [AdultosFormularioJdbcTest.java](../formulario-publico/backend/src/test/java/cl/nexbyte/siger/robustez/AdultosFormularioJdbcTest.java) | Fixture válido; matriz territorial, custodia firmada/idempotente con y sin CU y comuna opcional del NNA afectado |
| [FormularioCompletoJdbcTest.java](../formulario-publico/backend/src/test/java/cl/nexbyte/siger/robustez/FormularioCompletoJdbcTest.java) | Regresión explícita del territorio del NNA independiente |
| [adultRules.test.ts](../formulario-publico/front/src/formulario/adultRules.test.ts) | Fixture y regresiones territoriales |
| [AdultFlow.test.tsx](../formulario-publico/front/src/formulario/AdultFlow.test.tsx) | Revisión bloqueada, errores accesibles y corrección hasta captura válida |
| [pulido-ui.mjs](../scripts/qa/pulido-ui.mjs) | El recorrido visual de vulneración selecciona región/comuna válidas |

## Evidencia ejecutada

Antes de corregir las reglas, las regresiones nuevas reprodujeron **seis fallos esperados por motor de validación**: seis en frontend y seis en backend H2. Tras la corrección no quedan fallos.

La matriz incluye región ausente, comuna ausente, ambas ausentes; valores vacíos y espacios, individualmente y en ambos campos; región inválida; comuna ajena a región; pares válidos del catálogo. Los casos válidos verifican captura, persistencia, firma e idempotencia. Se comprueba que los rechazos no generan custodia ni sacan el recorrido de borrador. Las regresiones existentes y nuevas conservan la distinción de identidad y las reglas NNA.

| Verificación | Resultado local |
|---|---|
| Frontend completo | **176 correctas**, 18 archivos, cero fallos |
| ESLint | Correcto |
| TypeScript y build Vite | Correctos |
| Backend H2 completo sin carga | **324 seleccionadas: 322 correctas, dos omisiones, cero fallos/errores**, 45 clases |
| Sintaxis del fixture visual | `node --check scripts/qa/pulido-ui.mjs` correcto |
| MySQL QA | **No ejecutado**: no hay servicio disponible en `127.0.0.1:13306`; no se arrancó ni reconfiguró MySQL |
| Revisión de alcance y `git diff --check` | Correctas |

Omisiones opcionales existentes: `MultimediaWorkersRealTest#transporteJavaInspeccionaRepresentacionesYAntivirusReales` y `RecuperacionProcesoTest#comprobanteConfirmadoSobreviveCaidaRealDeMySQLYUnaFallaNoSeAcepta`. No se habilitaron workers reales ni reinicio de MySQL. Se excluye `Carga*Test`; no se realizan pruebas de estrés.

Herramientas ya instaladas: Node 24.22.1, Maven 3.9.15 y Java 25.0.5. Se utiliza `~/.local/bin` en PATH solo para estos comandos, sin instalaciones o cambios globales. El build backend se guarda en `.local/qa-gate4a14-2/backend-full-h2`, separado de los JAR ejecutables. Los logs locales ignorados quedan en `.local/qa-gate4a14-2/`.

Comandos reproducibles desde la raíz, con esas herramientas en PATH y sin variables `ROBUST_DB_*` para H2:

```bash
npm --prefix formulario-publico/front test -- --maxWorkers=2
npm --prefix formulario-publico/front run lint
npm --prefix formulario-publico/front run build
MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2' mvn -B -ntp -f formulario-publico/backend/pom.xml '-Dtest=*Test,!Carga*Test' "-Dsiger.build.directory=$PWD/.local/qa-gate4a14-2/backend-full-h2" verify
node --check scripts/qa/pulido-ui.mjs
git diff --check
```

La matriz JDBC utiliza `TestDatabase` y puede repetirse en el esquema QA autorizado con las variables privadas `ROBUST_DB_*`, cuando esté disponible, conforme al [README](../README.md). No usar bases ciudadanas ni MySQL institucional.

## Límites y continuidad

- Pendiente repetir la regresión en MySQL QA cuando esté disponible.
- El recorrido visual Playwright se actualizó y verificó sintácticamente; **no se ejecutó esa campaña visual**. El comportamiento de revisión/corrección se ejecutó en las pruebas React con DOM controlado.
- No hay CI remota nueva: el cambio queda exclusivamente en un commit local.
- No se arrancaron los laboratorios HTTPS, no se cambiaron banderas y no se desplegó el build. No hubo consultas a proveedores gubernamentales ni uso de datos reales.
- SIGER institucional no se modifica.
