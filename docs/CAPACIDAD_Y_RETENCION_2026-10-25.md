# Capacidad y retención del Formulario Público

> **Actualización del encargo:** el [ciclo de vida de custodia transitoria](CICLO_VIDA_CUSTODIA_TRANSITORIA_2026-10-25.md) implementa la solución para operar con 25 GB. Sustituye la ampliación de disco como salida principal y los apartados de este informe que describían el mecanismo como futuro. Esta auditoría inicial se conserva como evidencia del punto de partida; no describe las capacidades nuevas de V9.

Fecha: **25 de octubre de 2026**. Auditoría sobre `a84d2e7`, sin modificar SIGER, documentos firmados, contratos 1.0/1.1 ni comprobantes. Las mediciones son del laboratorio WSL, no de la futura VM. Esta unidad no habilita producción ni cambia banderas, cuotas o retención efectivas.

## Decisión técnica

**25 GB no sostienen la conservación indefinida de originales.** Pueden servir como un buffer transitorio acotado, con cuotas, alertas atendidas, respaldo externo y una política aprobada de transferencia/retención; esa política todavía no está implementada. Hoy el ACK no libera disco ni cuota. Limpiar temporales ayuda, pero no resuelve el crecimiento de documentos recibidos.

Recomendación: pedir **64 GiB o más como punto inicial de dimensionamiento**, con respaldo externo, y revisar consumo mensual; tampoco es capacidad indefinida ni capacidad certificada. Si solo hay 25 GB, no anunciar recepción multimedia amplia hasta demostrar el presupuesto real del sistema y resolver retención o ampliación. No reducir tamaños aceptados ni cambiar políticas silenciosamente. El formulario sin multimedia también crece: los BLOB históricos, datos y binlogs consumen disco.

El responsable informa que SIGER ya conserva originales íntegros 1.0/1.1, registra COM y entrega ACK firmado después del COMMIT. Se toma como antecedente de esta decisión; **no se inspeccionó ni modificó SIGER**. Las referencias anteriores que aún describen un consumidor pendiente son evidencia histórica, no una auditoría del estado actual de ese aplicativo.

## Qué hace el código realmente

| Elemento | Consumo y liberación efectivos | Referencia |
|---|---|---|
| Inicio de carga | Reserva `2 × tamaño + min(2 MiB, máximo del lote)` en `media_capacity`, dentro de transacción con bloqueo. Con perfil vigente son 2 MiB por archivo | `CargasMultimedia.iniciar` |
| Reserva global | Límite predeterminado 10 GiB; incluye recibidos, validados y pendientes no liberados. No equivale al uso físico ni es una cuota del filesystem | `PoliticaMultimedia`, `media_upload.reserved_bytes` |
| Admisión | Comprueba cuota contable y espacio disponible del filesystem frente a nueva reserva + mínimo libre; cada bloque vuelve a comprobar su tamaño + mínimo | `iniciar`, `bloque` |
| Validación | Ensambla original, verifica SHA-256, AV e inspector; crea derivada si corresponde. Intenta borrar bloques, **sin reducir reserva** | `analizar` |
| Fallo | Rechazado/reintentable conserva lo escrito y reserva hasta cancelación/saneamiento elegible; un fallo de borrado de bloques tras validación conserva bloques | `analizar` |
| Cancelación | Solo borrador sin referencia de adjunto; espera análisis local si sigue activo, borra archivos no incorporados y luego libera reserva una vez | `cancelar`, `liberarCancelado` |
| Confirmación de lote | Cambia referencias del borrador con control de revisión. Archivos FILE retirados no se borran físicamente; validados y reservas permanecen | `confirmar` |
| Recepción de Comunicación | Congela recorrido y referencias, persiste contenido/firmas. No libera cuota | `RecepcionFormularioJdbc.recibir` |
| ACK | Verifica firma con confianza institucional, id, huellas, canal y reserva vigente; repetición del mismo ACK idempotente. Guarda ACK, estado CONFIRMADA y evento; no borra archivos ni libera `media_capacity` | `EntregaFormulario.validarRegistro`, `RecepcionFormularioJdbc.confirmar` |
| Saneamiento explícito | Vencimiento inicial 24 h, no deslizante por bloque; solo RECIBIENDO/REINTENTABLE/RECHAZADO/CANCELADO/EXPIRADO, sin adjunto y sin Comunicación en su recorrido. Marca terminal, borra, libera reserva y registra evento | `sanear(false/true)` |
| Incoming huérfano | Herramienta offline, aplicación detenida y locks exclusivos: solo `incoming/carga-*.part` de más de 24 h; simulación por defecto | `media-archive.py clean-incoming` |

La reserva conservadora absorbe original + bloques + derivada, pero **no preasigna espacio físico** ni cubre otros escritores, MySQL, copias de respaldo o volúmenes distintos. `getUsableSpace` es disponibilidad para la aplicación; no confundirla con `free` del administrador. El mínimo actual de 512 MiB protege solo admisiones multimedia, no todo el servicio. Llegar a 507 puede impedir nuevos adjuntos aunque aún haya espacio físico.

Las reservas de entrega SIGER duran cinco minutos y se recuperan por reclamación vencida; no contienen binarios ni consumen `media_capacity`. Conservar su historia no equivale a conservar una copia adicional del archivo.

## Dónde están los bytes

- **MySQL:** Compose monta `mysql-lab` en `/var/lib/mysql`; en este anfitrión se verificó `/var/lib/docker/volumes/siger-mysql-lab_mysql-lab/_data`. Es persistente e independiente de `objects`. Contiene datos, índices, BLOB históricos, redo/undo y binlogs. `tmpdir=/tmp`; se verificó `log_bin=ON`, expiración de binlogs 2 592 000 segundos (30 días). No se cambió ni purgó: primero debe acordarse recuperación a punto en el tiempo. `information_schema.tables` no mide todo este volumen.
- **Originales multimedia:** ruta privada configurable `formulario.multimedia.directorio`, fuera de Git. Subdirectorios `objects/<UUID>` (original), `chunks/<UUID>/<índice>` (bloques), `incoming/carga-*.part` (recepción/ensamblado) y `derived/<UUID>` (PNG). Escritura durable y renombrado atómico; permisos privados. En laboratorio están bajo el home Linux, **no en el volumen MySQL**. Una VM requiere montaje persistente explícito de ese directorio.
- **Multipart histórico:** `file-size-threshold=0B` usa temporales del contenedor/proceso, hasta 10/26 MiB; no hay `spring.servlet.multipart.location` explícito en las configuraciones auditadas. Compose de app tiene `/tmp` tmpfs de 128 MiB. Se debe comprobar la ruta efectiva del proceso nativo al trasladarlo, sin asumir que es el almacén multimedia.
- **Inspector y AV:** copias de trabajo bajo `/scratch` en tmpfs (192/320 MiB) y `/tmp` 8 MiB por worker. El inspector usa `TemporaryDirectory`; no genera un archivo permanente adicional por cada aceptación. Tmpfs consume RAM y puede usar swap: si la VM usa swap en SSD también compite con disco. Bases de firmas AV son un montaje privado persistente aparte.
- **Docker, SO, JAR, logs y respaldos:** fuera de la cuota multimedia. Los Compose limitan rotación de sus logs; no significa que journal, logs nativos, binlogs o caché de compilación estén acotados por esa política. No conviene compilar y retener cachés en una VM de 25 GB. No se ejecutó prune ni ninguna limpieza del anfitrión.
- **Respaldo actual:** exporta SQL y copia los cuatro directorios. `--hex-blob` puede representar BLOB aproximadamente al doble antes de compresión; no presupuestar una copia SQL como si tuviera el tamaño exacto de InnoDB. Exige destino nuevo y app detenida. Guardar el respaldo en el mismo SSD duplica consumo y no protege contra pérdida de la VM. Restaurar también exige espacio adicional; usar otro volumen/equipo.

## Medición de laboratorio, solo lectura

Se consultaron agregados y tamaños, sin leer cuerpos ciudadanos ni imprimir nombres/identificadores de archivos, RUN, claves o ACK. No se validan firmas mediante SQL.

| Medida | Resultado |
|---|---:|
| Filesystem WSL total | 1 081 101 176 832 bytes; **no representa una VM de 25 GB** |
| Uso global observado (`df`) | 12 742 234 112 bytes, todos los proyectos/servicios del anfitrión |
| Almacén multimedia: originales | 72 archivos; 16 862 482 bytes lógicos |
| Bloques | 11 archivos; 8 389 932 bytes |
| Incoming | 0 archivos |
| Derivadas | 22 archivos; 109 296 bytes |
| Reservado contable | 220 371 506 bytes (~210,2 MiB) |
| Suma de reservas no liberadas | 220 371 506 bytes, **coincide** |
| Referencias FILE / MYSQL en esquema multimedia | 62 / 1 |
| Adjuntos en Comunicaciones con ACK almacenado | 16; 16 796 876 bytes, **continúan conservados** |
| Elegibles para saneamiento según consulta | 0; no se ejecutó saneamiento |
| Validados sin referencia | 0 en esta medición |
| Volumen completo MySQL de laboratorio (`du`) | 376 414 208 bytes, incluye múltiples esquemas |
| Docker: imágenes / caché de build | 3,545 GB / 4,396 GB reportados por Docker |

Docker puede compartir capas: **no sumar ciegamente** estas cifras a `df`. Los contadores de archivos y referencias no tienen por qué coincidir: hay cargas fallidas/pedientes con objetos. La medición es en línea, no una instantánea atómica de BD y disco, y no demuestra integridad criptográfica de todos los originales. El reporte no recorrió respaldos privados ni atribuye todos los bytes del anfitrión a este formulario.

## Dimensionamiento de 25 GB

25 GB decimales = **23,28 GiB**, antes de reservas del filesystem. Si el proveedor ofrece 25 GiB, son 26,84 GB decimales: confirmar unidad y espacio realmente utilizable. El dato de 300 Comunicaciones mensuales es **de todos los canales**, por lo que no debe tratarse como 300 ingresos web observados.

Hipótesis explícita para comparar: promedio de adjuntos por ingreso web **incluye ingresos sin adjuntos**; se presuponen dos archivos por ingreso a efectos de sobrecarga de reserva. Ignora temporales abandonados y crecimiento de BD; los plazos de cuota son optimistas y no predicen cuándo se llenará el SSD.

| Ingresos web/mes | MiB adjuntos/ingreso | Originales/mes | Reserva/mes (2× + 4 MiB/ingreso) | Meses hasta 10 GiB reservados |
|---:|---:|---:|---:|---:|
| 30 (10 % de todos los canales) | 5 | 0,15 GiB | 0,41 GiB | 24,4 |
| 90 (30 %) | 5 | 0,44 GiB | 1,23 GiB | 8,1 |
| 90 (30 %) | 20 | 1,76 GiB | 3,87 GiB | 2,6 |
| 300 (100 %, escenario conservador de participación) | 20 | 5,86 GiB | 12,89 GiB | 0,78 |
| 90 (30 %) | 100 | 8,79 GiB | 17,93 GiB | 0,56 |

Con 12 archivos y lote máximo de 250 MiB, una Comunicación puede reservar 524 MiB. La cuota actual admite **19 lotes máximos completos simultáneos/acumulados**, sin otras cargas. Conservar sin límite temporal agotará cualquier cuota finita aunque SIGER entregue ACK inmediato.

Presupuesto orientativo para una VM de 25 GB, **a comprobar tras instalar**: SO/actualizaciones 6 GiB, imágenes/runtime/firmas 3 GiB, MySQL/binlogs/metadatos 3 GiB y margen libre operativo 4 GiB. Deja ~7,28 GiB para multimedia/otros usos. Estos presupuestos son hipótesis, no límites impuestos ni medición de una VM inexistente; si se exceden, no habilitar ese perfil. La cuota 10 GiB no cabe con holgura en este presupuesto. Backups fuera de ese SSD; sin caché de build ni múltiples laboratorios en la VM.

Un perfil técnico candidato de **6 GiB reservados y mínimo libre 4 GiB** encaja mejor, sujeto al inventario efectivo. Reduce capacidad disponible, no resuelve retención ni está aplicado. Con 90×20 MiB por mes ofrece ~1,55 meses de reserva; con 300×20 ofrece ~0,47. No prometer 30 o 90 días sin medir volumen web real y tiempo hasta ACK. Solicitar ampliación antes del agotamiento, sin borrar recibidos para recuperar servicio.

## Acumulación: qué puede retirarse y qué debe conservarse

| Situación | Regla actual / acción |
|---|---|
| Incompletos vencidos sin referencias ni Comunicación | Saneamiento existente, explícito y auditable. Primero simulación; aplicación revalida estado bajo bloqueo. No se ejecutó en esta unidad porque el inventario arrojó cero candidatos |
| Incoming huérfano >24 h | Herramienta offline ya autorizada; no detener un servicio solo para liberar cero bytes. No ejecutar `rm` por antigüedad |
| Cancelado mientras analizaba | Liberación diferida por worker. Si falla, retiene reserva hasta saneamiento permitido; revisar edad/estado |
| Bloques conservados tras validar | Normalmente se borran; un fallo de IO puede dejarlos. No ampliar borrado ahora: una reconciliación futura debe verificar original durable/íntegro y serializar análisis, backup y recuperación |
| ANALIZANDO con lease vencido tras caída | No elegible en `sanear`. Puede reintentarse desde sesión válida; pérdida de sesión deja pendiente operativo. No resetear estado/reserva manualmente |
| VALIDADO abandonado o retirado del lote | No elegible para saneamiento actual; conserva original y reserva. Requiere regla explícita de retención de borradores y coordinación transaccional antes de automatizar |
| Recorrido enviado con cargas extra rechazadas/no seleccionadas | Exclusión conservadora por existencia de Comunicación protege el recorrido completo; incluso cargas no incluidas pueden permanecer. No inferir permiso de borrado por ausencia de referencia individual |
| Derivada PNG de documento recibido | Regenerable en principio, pero la implementación/backup/visor esperan su presencia y hash. No borrarla sin soporte de regeneración controlada; jamás sustituye original |
| Original con ACK | Recibido, protegido. **ACK no es autorización de eliminación** |
| Filas, auditoría, binlogs y BLOB históricos | Sin purga por esta unidad. Borrar una fila InnoDB tampoco garantiza devolver bytes al filesystem; no ejecutar OPTIMIZE ni reescribir históricos |

## Ciclo de vida posterior al ACK: diseño pendiente de aprobación

1. **Custodia pública pendiente:** mantener originales, hashes y referencias. Solo ACK validado por la aplicación puede marcar recepción institucional; SQL o estado del navegador no son prueba suficiente.
2. **Recepción institucional confirmada:** conservar ACK firmado y contenido original 1.0/1.1 intactos. El ACK acredita lo que firma, no comprueba por sí solo restauración física futura en SIGER. Según el antecedente entregado, el consumidor comprueba originales y confirma después de COMMIT; el equipo debe conservar evidencia E2E de esa garantía.
3. **Ventana de seguridad:** una política aprobada define plazo desde un evento fiable de ACK, margen por conciliación/restauración, prohibiciones y retenciones excepcionales. **30 y 90 días son alternativas de simulación, no plazos adoptados.** No deducir automáticamente vencimiento desde `created_ms` o el vencimiento de carga. No existe un reloj dedicado de purga post-ACK; definirlo sería una implementación posterior.
4. **Candidato a liberación:** exigir firma/huellas verificadas con claves históricas confiables, asociación exacta a COM, conciliación de manifest con todos los hashes/tamaños, custodia institucional y respaldo restaurable comprobados, ausencia de bloqueo de conservación. Un operador debería revisar una simulación y autorizar lote acotado según política. Ante ACK ambiguo, incidente o ausencia de prueba, conservar.
5. **Liberación futura:** estado y bitácora separados del contenido firmado; intención durable antes de retirar bytes, operaciones idempotentes, permisos por recurso y reconciliación después de caída. Liberar reserva solo después de éxito verificado; nunca ajustar el contador global a cero ni reescribir documento/comprobante para ocultar la retirada. No implementado.
6. **Recuperación:** conservar metadatos/hashes/ACK y ruta administrativa para restaurar original íntegro desde archivo institucional o respaldo. **La API actual de bytes exige estado RESERVADA y reserva vigente; después de CONFIRMADA no proporciona un canal general de rehidratación.** No cambiar ese control ni fingir que ya existe recuperación post-purga. La política debe resolver primero esa dependencia y el efecto en la consulta ciudadana.
7. **Retención de metadatos y respaldos:** definir por separado, incluyendo datos declarados/registrales, auditoría y copias históricas. No introducir eliminación de esas evidencias al liberar espacio de binarios. No duplicar físicamente un adjunto por cada Caso institucional.

Una eventual expiración de la copia pública es una **nueva capacidad** con requisitos de recuperación y política aprobada. No se implementa mediante un cron que elimine `objects` ni mediante un callback de ACK. Las decisiones técnicas corresponden al equipo de desarrollo; plazos, alcance, excepciones y autoridad de disposición requieren aprobación institucional. Esta separación no congela los campos/UI ni bloquea el trabajo de integración.

## Cuotas, alertas y operación propuesta

Configuraciones existentes, sin cambiar valores del laboratorio:

```properties
# Ejemplo candidato SOLO si se confirma VM de 25 GB y su presupuesto; NO aplicado.
formulario.multimedia.cuota-global=6144MB
formulario.multimedia.minimo-libre=4096MB
```

Spring `DataSize` interpreta MB en múltiplos de 1024²; estos valores representan 6/4 GiB. Los techos por archivo, lote, bloque y cantidad ya son configurables. El constructor exige cuota al menos dos lotes. El vencimiento de 24 h actualmente es fijo en configuración Java; **no anunciar una propiedad de TTL inexistente**. No modificar firmas/contratos para ajustar límites operativos.

| Indicador | Propuesta | Acción |
|---|---|---|
| Reserva global | Preventivo 80 %, crítico 95 %, admisión rechaza al exceder 100 % | Inventariar pendientes, resolver entrega/ACK, solicitar ampliación; saneamiento solo permitido |
| Disco libre para aplicación | Preventivo <5 GiB, crítico <4 GiB para candidato 25 GB | Investigar todos los usos del filesystem. No borrar originales. Configurar mínimo libre solo tras revisión |
| Tendencia | Margen <30 días al ritmo medido de reserva | Revisar con responsable; si crece sin política de liberación, ampliar antes de agotar |
| Contabilidad | `media_capacity` distinto de suma no liberada o negativo | Incidencia, conservar evidencia; no corregir con SQL manual |
| Pendientes/ACK | Medir edad y bytes, además de número de Comunicaciones | Identificar atraso de entrega sin confundir ACK con espacio liberado |
| Auditoría y BD | Crecimiento de datos, índices, binlogs y volumen completo | Revisar presupuesto/PITR; política propia, sin purga improvisada |

La alerta backend existente es reserva ≥80 % o libre <mínimo+dos videos máximos (con perfil actual ~812 MiB). **No se cambió.** El nuevo inventario usa umbrales operativos más tempranos, configurables, sin afectar admisión. Puede ejecutarse manualmente o integrarse después en el monitor institucional; no se instaló cron, correo ni notificación automática.

### Comandos de medición

```bash
# Sin BD: bytes físicos/lógicos por carpeta y filesystem. Código 2 avisa reservas no medidas.
python3 scripts/linux/media-storage-report.py --store /ruta/privada/almacen

# Con BD V8: resumen consistente de contadores/agregados + disco, sin exponer archivos.
# Ejecutar con acceso local autorizado a Docker y el secreto ya montado; sin claves por CLI.
python3 scripts/linux/media-storage-report.py --store /ruta/privada/almacen \
  --database esquema_v8 --container contenedor_mysql \
  --quota-gib 10 --min-free-gib 4

# API operativa existente, sin acceder al contenedor; usa el token privado y TLS validado.
python3 scripts/linux/media-capacity.py --config /ruta/privada/application.properties \
  --ca /ruta/privada/ca.pem --origin https://origen-local-autorizado
```

El inventario devuelve JSON, código **0** sin alertas, **2** atención y **1** error/inventario incompleto. No abre puertos, no hace llamadas a proveedores y no tiene modo de eliminación. `--quota-gib` es el límite contra el que se compara, **no cambia configuración**: debe coincidir con el de la instancia; para esta auditoría se usaron 10 GiB. MySQL requiere V8; sin él, usar la medición de carpetas solo donde exista almacén multimedia y medir el volumen MySQL por separado. No se habilita multimedia para medirla.

En JSON, los agregados de carga son `[estado,cantidad,bytesEsperados,reservaNoLiberada]`; los de tipo `[MYSQL|FILE,cantidad,bytesOriginales]`; RECIBIDOS/CON_ACK_ALMACENADO `[etiqueta,cantidadAdjuntos,bytesOriginales]`; SANEABLES/VALIDADOS_SIN_REFERENCIA `[etiqueta,cantidadCargas,reserva]`. Son conjuntos superpuestos, **no sumarlos entre sí**. `MYSQL_TABLAS_INDICES` es una estimación del esquema, no el volumen completo. No leer CON_ACK_ALMACENADO como una revalidación criptográfica.

`du -sx --block-size=1 RUTA` y `df -B1 RUTA` complementan el inventario sobre montajes autorizados. No sumar el libre del mismo filesystem varias veces. Contabilizar aparte la base de firmas, logs y respaldos. El inventario recorre metadatos, sin hashear/leer contenido; no es una prueba de restore ni una cuota dura contra otros procesos.

## Entrega y límites de esta unidad

Implementado ahora: inventario agregado de solo lectura, contabilidad de reservas, alertas configurables y esta evaluación/plan. No hubo migraciones, cambio de backend/React, banderas, tamaños máximos, retención, contratos o despliegue. No se eliminaron archivos del laboratorio ni se liberaron reservas; solo las pruebas unitarias usan y eliminan sus temporales sintéticos.

Pruebas: **7 pruebas nuevas de inventario/alertas y 5 regresiones de respaldo/limpieza**, todas correctas; ejecución real agregada sobre laboratorio V8 correcta. La batería completa Java/React del cierre anterior se conserva como evidencia histórica, **no se afirma haberla repetido en esta unidad Python/documental**. No se hicieron consultas gubernamentales, nuevas Comunicaciones, cargas intensivas ni cambios en SIGER. Sin push en esta unidad.

Pendientes institucionales: confirmar unidad/capacidad del SSD, proporción web y distribución de tamaños, plazo y excepciones de retención pública, responsabilidad de restauración institucional y respaldo externo. Pendientes técnicos posteriores a esas decisiones: reconciliación de bloques/leases, política segura para borradores abandonados, reserva ajustada por fase si se justifica, marca de ciclo de vida y recuperación antes de una eventual retirada post-ACK. No ejecutar esas ampliaciones como parte implícita de este informe.