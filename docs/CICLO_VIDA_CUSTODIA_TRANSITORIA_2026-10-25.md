# Ciclo de vida de custodia transitoria y operación con 25 GB

> **Informe histórico V9.** La [arquitectura definitiva V10](ARQUITECTURA_RECEPTOR_TRANSITORIO.md) lo amplía: mantenimiento online, agente institucional saliente y minimización de MySQL, con retención candidata de 24 h. No usar la ventana offline, los montajes SIGER ni los plazos de este informe como operación normal vigente. Los resultados originales se conservan como evidencia.

**25 de octubre de 2026.** Continúa `4b148bc` en la rama `ciclo-vida-custodia-2026-10-25`. Implementación y pruebas locales; **eliminación real cerrada**. No hay push, despliegue, tarea programada ni cambios en SIGER.

## Resultado y decisión

El Formulario Público puede actuar como **buffer transitorio** dentro de 25 GB; SIGER conserva la custodia definitiva. La solución deja de acumular originales confirmados mediante un mantenimiento explícito que verifica firmas, originales institucionales y una restauración independiente, espera una ventana técnica y retira únicamente la copia pública. No se elimina por recibir un ACK.

El código está implementado, no solo propuesto: verifica, registra observación/retención, simula, retira, reanuda operaciones interrumpidas, libera reservas una sola vez, trata borradores abandonados y recupera originales desde la restauración. La activación real exige garantías técnicas comprobables y permanece deshabilitada en ambos laboratorios existentes. No se está esperando una decisión genérica de retención para terminar el mecanismo.

Con 300 ingresos web/mes de 20 MiB —hipótesis conservadora de que todos los canales migraran al formulario— el modelo diario de un año da un pico de **0,957 GiB físicos y 1,133 GiB reservados**. Sin liberación acumularía 71,289 GiB de originales en ese año. Es un modelo de capacidad, **no una prueba de rendimiento ni medición de una VM que aún no se ha entregado**.

## Componentes implementados

| Componente | Responsabilidad |
|---|---|
| `V9__ciclo_vida_custodia.sql` H2/MySQL | Marca `citizen_attachment.bytes_retired`; tablas `custody_lifecycle` y `storage_retirement`. Sin cambiar sobre, algoritmo, firma, UUID o comprobante |
| `ArchivoCustodiaIndependiente` | Lee por buffers los originales institucionales y restaurados, verifica tamaño/SHA-256; rechaza rutas compartidas, enlaces y hardlinks que simulen independencia; exporta recuperación a destino nuevo |
| `CicloVidaCustodia` | ACK/custodia validados, ventana temporal, bloqueo de conservación, prueba independiente, intención durable, retirada por recurso y contabilidad idempotente |
| `MantenimientoCustodia` | CLI privado, offline, explícito. Sin endpoint público, scheduler ni bean Spring que pueda borrar por sí solo |
| `AdjuntosFormulario` y representaciones | Distinguen retirada registrada de archivo perdido. Devuelven 410 para bytes retirados; mantienen metadatos y comprobantes. Una ausencia no registrada sigue siendo error de integridad |
| `media-archive.py` | Verifica originales vigentes; acepta ausencia de bytes solo con marca V9 e intención finalizada concordante; una reserva no liberada la bloquea. Conserva compatibilidad con respaldos V8 |
| `infra/capacidad-25gb.properties.example` | Perfil candidato: cuota 6 GiB, mínimo libre 4 GiB; retirada cerrada, ventana técnica 72 h y abandono 48 h adicionales al vencimiento |

La firma y el documento 1.0/1.1 permanecen intactos: `bytes_retired` y las tablas nuevas son estado operativo **fuera** del agregado firmado. No se reescriben valores históricos ni se borra `citizen_attachment`. Los BLOB pasan a NULL únicamente cuando la retirada validada concluye; sus nombres, tamaños, SHA-256, referencias y evidencia quedan conservados.

## Garantías antes de retirar una Comunicación

1. El registro público debe estar CONFIRMADA, contener ACK y superar validación de la custodia pública y del comprobante institucional, con confianza separada. Se comprueban id, huellas y canal mediante las funciones existentes. Una firma inválida o identidad de archivo distinta bloquea.
2. La primera observación válida se registra con hora propia; un documento histórico **no se considera vencido inmediatamente** por su fecha antigua. El plazo por defecto es 72 h desde esa observación. Cambiar la configuración a una ventana más larga la respeta; acortarla no adelanta el vencimiento ya registrado.
3. Se leen **todos** los originales del árbol institucional y del árbol restaurado: `objects/<UUID público>/<UUID adjunto>.bin`, igual que `ArchivoInstitucional`. Sus hashes y tamaños deben coincidir con los descriptores firmados; no basta un HEAD, contar documentos o un campo «transferido».
4. También se valida la copia pública antes de empezar, incluidos BLOB por streaming. Solo una intención previa registrada permite reanudar cuando la copia pública ya no existe. Una ausencia inesperada bloquea.
5. No puede existir bloqueo de conservación. La comparación de referencias/nombres/MIME/tamaños debe ser exacta. Se exige que `media_capacity` coincida con la suma de reservas no liberadas antes de tocar bytes.
6. El CLI real exige archivos institucionales y restaurados en **montajes de solo lectura**, distintos del filesystem público y entre sí. No modifica esos montajes ni requiere acceder a la base SIGER. Las credenciales de máquina, los archivos y las claves institucionales privadas no pasan al navegador ni al Formulario.
7. Además deben estar `ciclo-vida.eliminacion.habilitada=true` en configuración privada **y** `--aplicar` en la invocación. Ninguno está activo en los laboratorios. La aplicación receptora debe estar detenida y recepción/recuperación cerradas; el lock de instancia rechaza solapamiento con el receptor multimedia.

Las lecturas del árbol/restauración complementan el ACK, no sustituyen la custodia institucional. El despliegue debe montar el archivo **real y confiable** de SIGER y una restauración institucional verificada, no carpetas arbitrarias pobladas desde el Formulario. La prueba con clases existentes de SIGER, descrita abajo, comprueba COMMIT, registro y recuperación autónoma. El CLI no inventa un API de verificación en SIGER ni presupone un servicio nuevo allí.

## Retirada recuperable e idempotente

Estados de Comunicación: OBSERVADA → VERIFICADA → RETIRANDO → LIBERADA, con bloqueo de conservación y errores separados. Cada recurso tiene una intención INTENCION → FINALIZADA.

- Se confirma primero la intención en MySQL y su evento; después se retiran bloques, original y derivada de ese UUID y se sincronizan directorios. No se construyen rutas con nombres ciudadanos.
- Después, en una transacción, se bloquean contador y operación, se libera el saldo aún no liberado, se marca la carga `released`, se retiran metadatos de bloques y se marca `bytes_retired=true`, conservando el descriptor. Un BLOB se pone a NULL en esta misma transacción. Se registra finalización.
- Una caída antes del borrado deja el original y reserva. Una caída después del borrado pero antes del COMMIT deja la intención y conserva reserva; el siguiente intento **vuelve a verificar las copias externas** y termina. Una caída después del COMMIT permite repetir sin descontar dos veces.
- Ante error IO, discrepancia o falta de prueba, no se fuerza el contador ni se declara éxito. El mantenimiento no modifica firmas para hacer desaparecer inconsistencias.
- El borrado es retirada lógica durable, no una promesa de borrado forense de celdas SSD, snapshots o respaldos. Estos últimos mantienen sus propias reglas.

La consulta ciudadana de estado/comprobantes sigue funcionando después de la retirada. Los bytes ya transferidos dejan de servirse desde la copia pública; el acceso institucional definitivo corresponde al SIGER autenticado. No se expone un proxy público de lectura ni se habilita recuperación de identidades/archivos entre sesiones.

## Recuperación independiente

`--accion recuperar --id UUID --salida DIRECTORIO_NUEVO` revalida custodia, ACK y ambos árboles externos y exporta originales de la restauración, con nombres UUID, permisos privados, hashes, fsync y destino nuevo. No sobrescribe ni rehidrata automáticamente el SSD público; puede dirigirse a un volumen de recuperación separado. Un archivo corrupto, una restauración incompleta o un destino existente bloquean.

Si una retirada quedó en curso, debe terminarse antes de declarar consistente un nuevo respaldo público. Un respaldo durante una intención incompleta puede detectar que falta un original aún no marcado y **rechazar la verificación**; no lo trata como pérdida aceptable. No restaurar una BD anterior sobre nuevas recepciones ni reutilizar binarios antiguos que desconozcan V9 después de aplicar retiradas. Preservar claves de firma/confianza históricas por separado.

## Abandonados, temporales, derivados y reservas

- `abandonados`: inventaría por defecto. Con aplicación explícita y bandera, cancela recorridos vencidos **más 48 h**, sin Comunicación; incluye validados, BLOB de borrador, cargas rechazadas/reintentables y ANALIZANDO interrumpido. El mantenimiento es offline: no compite con un análisis ni una sesión que siga vigente. Mantiene metadatos y bitácora; elimina bytes y libera reservas mediante la misma intención recuperable.
- Una carga no incluida en el lote puede tratarse tras el margen aunque otro archivo del recorrido ya tenga Comunicación. Exige ausencia de referencia en `citizen_attachment` y también en el documento público firmado. Una referencia perdida no autoriza borrar.
- Ningún adjunto incluido en una Comunicación pendiente, en cuarentena o sin ACK pasa por el camino de abandono.
- `compactar`: primero verifica original y derivada durables; después quita bloques redundantes y ajusta la reserva de VALIDADO a `original + derivada`. La admisión inicial conserva `2 × archivo + margen`: el pico de ensamblado sigue protegido. Repetir compactación no vuelve a liberar el mismo saldo.
- `incoming` huérfano continúa bajo `media-archive.py clean-incoming`, offline, >24 h, simulación por defecto. No se habilita un `rm` por antigüedad.
- Las derivadas de una Comunicación se retiran junto con su copia pública, después de verificar la custodia definitiva; no se prometen previsualizaciones locales después de la retirada. Las fuentes originales institucionales quedan intactas.
- Objetos sin dueño conocido y archivos inesperados se conservan para incidencia; no se decide su borrado por ausencia de una fila. La contabilidad inconsistente bloquea en vez de corregirse con un UPDATE improvisado.

## Operación reproducible, sin habilitación real implícita

Compilar en salida aislada, sin sobrescribir el JAR en ejecución:

```bash
mvn -B -ntp -f formulario-publico/backend/pom.xml \
  -Dsiger.build.directory="$PWD/.local/ciclo-vida-target" '-Dtest=*Test,!Carga*Test' verify
```

V9 se aplica mediante el procedimiento de migración existente, con cuenta de migración, respaldo y prueba previa. **El CLI no migra automáticamente** ni pausa servicios. En esta unidad se aplicó únicamente a H2 sintético y `robust_public_test`; no a los esquemas de los laboratorios existentes.

Después de drenar sesiones y cerrar recepción/recuperación por los controles existentes, detener solo ese receptor. Conservar binario, claves, bases y volúmenes. La sesión Tomcat en memoria se pierde al detener; planificar una ventana anunciada para no cortar borradores activos. El ejemplo no instala un cron que interrumpa ciudadanos.

```bash
java -Dloader.main=cl.nexbyte.siger.multimedia.MantenimientoCustodia \
  -cp /ruta/privada/formulario-publico.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher \
  --config /ruta/privada/application.properties --accion simular
```

Acciones: `simular`, `ejecutar`, `abandonados`, `compactar`, `retener`, `levantar`, `recuperar`. Las tres acciones destructivas requieren `--aplicar` además de la bandera. `ejecutar` sin esa opción solo verifica; el lote está acotado a 20 Comunicaciones. Los bloqueos conservan el documento y aparecen en resultados/eventos; no interpretar `liberadas:0` como autorización pendiente de borrado. `simular` registra observaciones/evidencia/plazo, pero no elimina ni libera reservas. `retener/levantar --id UUID` modifican únicamente bloqueo operativo auditable de una Comunicación previamente observada.

El archivo privado necesita `publico.db.*`, rutas de confianza pública/institucional y `formulario.multimedia.directorio`, además del ejemplo de capacidad. El CLI lee propiedades literales UTF-8: no interpreta expresiones `${...}` de Spring. No pasar claves por argumentos. Por defecto solo hay lectura/verificación; no se suministra una configuración activa de eliminación.

Mantenimiento diario propuesto: verificar/conciliar; revisar bloqueados; compactar reservas validadas; sanear abandonados; retirar confirmadas cuyo plazo y pruebas se cumplen; verificar contador y espacio; respaldo externo de metadatos y pendientes; reabrir. La frecuencia diaria es un parámetro del dimensionamiento, **no una tarea instalada**. La ventana offline y pérdida de sesiones son una limitación operativa concreta; si la disponibilidad exige cero ventanas, será necesario integrar el mismo motor con un coordinador de mantenimiento online y locks por recurso, en otra unidad, antes de automatizarlo.

## Dimensionamiento de 25 GB: solución condicionada a una carga definida

25 GB decimales ≈23,28 GiB. Perfil candidato sin cambios efectivos: **6 GiB de reserva multimedia, mínimo libre 4 GiB**, con 72 h postconfirmación y margen de abandono 48 h. Presupuesto a comprobar en la VM: SO 6 GiB, runtime/imágenes/firmas 3 GiB, MySQL/metadatos/binlogs 3 GiB. Sin compiladores/cachés de build ni respaldos completos en ese mismo SSD. El laboratorio de WSL tiene ~1 TB lógico, no sirve para afirmar que se instaló y probó una VM de 25 GB.

Modelo ejecutable: `python3 scripts/qa/modelar-capacidad-transitoria.py`. Supone llegadas uniformes, un día hasta entrega/restauración verificable, retención 3 días, mantenimiento diario, dos archivos por ingreso salvo lote extremo (12), margen conservador de 2 MiB de derivada por archivo y staging de un lote. No modela rendimiento, ataques, abuso o concurrencia máxima: dejar margen adicional para cargas simultáneas y borradores. Incluye una fase con reservas sin compactar hasta el mantenimiento siguiente.

| Web/mes | MiB de originales/ingreso | Pico físico binario | Pico de reserva | Sin retirar, originales al año | ¿Reserva ≤6 GiB? |
|---:|---:|---:|---:|---:|---|
| 90 | 20 | 0,301 GiB | 0,340 GiB | 21,387 GiB | Sí |
| 300 | 20 | 0,957 GiB | 1,133 GiB | 71,289 GiB | Sí |
| 90 | 100 | 1,316 GiB | 1,512 GiB | 106,934 GiB | Sí |
| 300 | 100 | 4,160 GiB | 5,039 GiB | 356,445 GiB | Sí, margen menor |
| 300 | 250 y 12 archivos | 10,947 GiB | 13,145 GiB | 891,113 GiB | **No** |

En 300×20 MiB, ocho días hasta entrega/restauración más tres de retención elevan el pico a **2,598 GiB físicos / 2,773 GiB reservados**. Ante una indisponibilidad sin límite no existe almacenamiento finito que garantice admisión ilimitada: la cuota rechaza nuevos adjuntos con 507 antes de perder custodia. La alerta debe resolverse recuperando entrega/mantenimiento; no borrando pendientes.

**Conclusión:** 25 GB son técnicamente razonables para el buffer de los escenarios moderados, incluido 300×20 MiB, siempre que se ejecuten transferencia, restauración y mantenimiento dentro de los tiempos presupuestados y el resto del sistema respete su presupuesto. No basta cambiar la cuota: sin mantenimiento, sin montajes externos o con flag cerrada, el consumo no converge. No se certifica el extremo de 250 MiB en todos los ingresos.

Corrección concreta si aparece ese extremo: mantener admisión acotada, medir tasa/tamaño y reducir el tiempo real de entrega/validación de respaldo y de mantenimiento. Una ventana técnica post-ACK de 24 h está soportada, pero exige volver a modelar y verificar recuperación independiente con esa periodicidad; no se aplicó automáticamente para forzar el número. Si aun así el backlog supera el presupuesto, hay que limitar volumen admitido temporalmente o aportar almacenamiento transitorio externo. No prometer absorber datos arbitrarios dentro de 25 GB.

### MySQL y metadatos: límites que el borrado de archivos no resuelve

Se verificó `log_bin=ON`, `binlog_row_image=FULL`, segmento máximo 1 GiB y vencimiento de binlogs de 30 días en laboratorio; seis binlogs sumaban 135 107 226 bytes después de las pruebas. Con BLOB históricos, `binlog_row_image=FULL` puede retener los bytes antiguos al ponerlos a NULL. Las páginas InnoDB liberadas se reutilizan, pero no garantizan reducir el archivo `.ibd`. No se ejecutó OPTIMIZE, PURGE ni se deshabilitó binlog.

Corrección para la VM: mantener las cargas nuevas en FILE, presupuestar y vigilar binlogs, respaldar fuera de la VM y definir una ventana PITR acotada coherente con esos respaldos. Parámetros concretos a probar al configurar la VM: `binlog_row_image=MINIMAL` para no volver a escribir un BLOB completo al retirar, `binlog_expire_logs_seconds` acorde al PITR acordado y `max_binlog_size` para rotación. El tamaño de segmento no limita el total. No aplicar expiración sin respaldos restaurables externos. Para un histórico BLOB ya grande, retirar lógicamente solo después de la prueba de custodia; si el `.ibd` ya excede el presupuesto, trasladar/restaurar metadatos a un volumen nuevo con espacio externo, no reconstruir la tabla sobre el SSD casi lleno.

Los metadatos, hashes y bitácoras **sí siguen creciendo**, porque deben conservarse. No se afirma consumo matemáticamente constante de toda la BD durante infinitos años. En la prueba pequeña, dos Comunicaciones sumaron 6 674 bytes de body+ACK; no extrapolar ese ejemplo mínimo a textos/personas máximos. Presupuestando 128 KiB por ingreso, 300/mes añadirían ~0,44 GiB/año antes de índices/auditoría; medir distribución real y proyección anualmente. La estabilización demostrada es del volumen dominante de binarios y reservas; el presupuesto MySQL limita el horizonte de metadatos y dispara alertas para archivado/migración futura sin perder trazabilidad.

## Evidencia separada

**Motor sintético, H2 y MySQL:** 17 pruebas específicas de ciclo de vida; incluyen flag cerrada, firma ajena, copia ausente/corrupta, alias/hardlink, ventana/hold, interrupciones antes/después de borrar y después del COMMIT, repetición, contador inconsistente, compactación, abandono, BLOB y referencia recibida protegida. Seis ciclos sucesivos dejan cero bytes en objects/chunks/incoming/derived y reserva cero, manteniendo seis Comunicaciones y descriptores retirados.

**Código SIGER existente, sin modificarlo:** `scripts/qa/verificar-custodia-siger.py` lee un JAR ya compilado y ejecuta las clases reales `ArchivoInstitucional`, `RecepcionInstitucionalJdbc` y sus migraciones en H2 nuevo. Prueba 1.0 con BLOB y 1.1 con FILE, transfiere el original, confirma COMMIT/ACK, retira copia pública, cierra/vacía la BD pública sintética, reinicia SIGER y comprueba COM/ACK/original desde su propia BD/archivo. Dos recorridos correctos. Es integración de clases reales con datos sintéticos; **no es una prueba HTTP del servicio SIGER desplegado ni una prueba de desastre sobre medios físicos**. El JAR institucional quedó idéntico, SHA-256 `2ed038c567b672e7c7aaab6ac04f83efd22101f0b0ea1f5d4e0a33333be4ca97`.

Reproducción opt-in, después de ejecutar las pruebas públicas (necesita el classpath de Surefire). Requiere JAR local confiable; no clona ni compila SIGER:

```bash
python3 scripts/qa/verificar-custodia-siger.py \
  --siger-jar /ruta/al/JAR_SIGER_EXISTENTE.jar \
  --public-build "$PWD/.local/ciclo-vida-target" \
  --work /ruta/privada/directorio-nuevo-fuera-de-git
```

**Respaldo/restauración MySQL V9:** se creó un conjunto exclusivamente sintético en `robust_public_test`, con una Comunicación FILE y otra BLOB retiradas. Se exportó y restauró en el esquema nuevo `robust_public_retention_restore_20261025`, sin sobrescribir bases. Dos Comunicaciones, dos metadatos marcados, cero contenidos locales; CHECKSUM de submission, attachment y retirement coinciden origen/restaurado. El origen de prueba se limpió después, conservando la restauración y el respaldo privados. No se retiró ningún archivo de los laboratorios existentes.

**Regresiones:** 216 pruebas backend descubiertas por motor, **214 correctas y 2 omitidas** en H2 y en MySQL. Las omisiones opcionales siguen siendo `RecuperacionProcesoTest` y `MultimediaWorkersRealTest`; esta unidad no repite AV real ni consultas gubernamentales. Frontend: **160 correctas**, lint y build correctos. Python: **8 de respaldo/limpieza y 7 de inventario**, correctas. CLI empaquetado comprobado: `--aplicar` con flag cerrada termina con código 1 antes de conectar a una base.

Evidencias privadas: `.local/evidencias/ciclo-vida-{h2,mysql}.log`, `ciclo-custodia-siger-real-clases.log`, `ciclo-capacidad-modelo.json`, `ciclo-banderas.json`. Archivos sintéticos, respaldos y claves de prueba fuera de Git. No se incorpora evidencia ciudadana a commits.

## Estado de activación y próximo paso exacto

- Laboratorio integrado: multimedia false; `ciclo-vida.eliminacion.habilitada` ausente/false. Conserva 13 custodias, 13 adjuntos y 7 migraciones.
- Laboratorio multimedia: multimedia true como antes, retirada ausente/false. No se migró ni reinició; conserva sus originales y V8.
- V9 únicamente en bases de pruebas/restore, sin producción y sin consultas a proveedores.
- Árbol SIGER sin cambios; no se modificó un contrato documental ni se publicó código.

Para activar de verdad no falta implementar el motor. Falta comprobar sobre la VM y sus montajes reales: presupuesto libre, archivo institucional confiable, restauración independiente completa/actualizada, controles de conservación y ventana operativa. Después: respaldo, migración V9, simulación/revisión de resultados, habilitación privada explícita y primera retirada acotada con recuperación comprobada. Hasta ese punto, **no se elimina información real**. No se sustituye ninguna garantía faltante por una casilla de «ACK recibido».