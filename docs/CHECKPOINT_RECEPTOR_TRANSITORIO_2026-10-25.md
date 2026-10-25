# Checkpoint · receptor transitorio integral · 25/10/2026

## Publicación posterior y vigencia

Este checkpoint conserva el cierre técnico de `a557245`/`c971c42`, realizado antes del push. La [consolidación en main](CONSOLIDACION_MAIN_2026-10-25.md) publica íntegramente ese trabajo y actualiza el README, sin ejecutar nuevas regresiones ni desplegar infraestructura. Las menciones a rama local/sin publicación más abajo son el registro de aquella ejecución. Para estado publicado, usar el documento de consolidación y las referencias Git.

## Estado para continuar

- Repositorio: `formulario-web`, exclusivamente. Rama local **`receptor-transitorio-integral-2026-10-25`**.
- Implementación: **`a5572455e804291155b980e4d0510bddfe796c39`**, `feat: consolidar receptor transitorio con minimizacion y mantenimiento online`.
- Base conservada: `4a621a2` → `2d5b2f3` (V9) → `4b148bc` → `a84d2e7` (baseline publicado). Sin reset, cambios descartados, push ni modificaciones de main.
- Documentación: commit posterior de esta misma rama; `git log -1 -- README.md docs/CHECKPOINT_RECEPTOR_TRANSITORIO_2026-10-25.md` identifica su revisión exacta.
- [Arquitectura vigente y procedimiento de activación](ARQUITECTURA_RECEPTOR_TRANSITORIO.md). Los informes V9/capacidad inicial son históricos; no gobiernan el mantenimiento normal nuevo.

**Decisión aplicada en software:** el Formulario conserva temporalmente; SIGER es el único registro y archivo institucional definitivo de la Comunicación íntegra. La copia pública no conserva indefinidamente relato, identidad, contactos, respuesta registral ni payload firmado completo después de acreditar la custodia y recuperación independientes y cumplir la retención técnica.

## Implementación cerrada

| Área | Resultado |
|---|---|
| Recepción transaccional | Agregado, personas, procedencia, adjuntos y payload íntegro mientras están pendientes; no se minimizan pendientes, cuarentenas o conservaciones bloqueadas |
| Entrega institucional | Contratos 1.0/1.1, firmas, canonicalización, UUID, reservas, COM y ACK existentes intactos |
| Garantía adicional | Firma operativa separada y caducable que vincula ID/COM, hashes de documento/manifiesto/ACK y restauración; ACK aislado no habilita retirar |
| Agente institucional | CLI privado SELECT; verifica COM/ACK/originales, publica respaldo independiente del ingreso, restaura y revalida automáticamente antes de enviar la garantía por HTTPS saliente |
| Período técnico | Perfil candidato: 24 h desde observación válida; no retrofecha históricos ni acorta plazos ya asignados. Sin prueba vigente se conserva incluso vencido el plazo |
| Mantenimiento | Programador Spring cada cinco minutos, lotes limitados, lease persistido y guardias por recorrido. No exige detener recepción ni montar SIGER en el público |
| Minimización V10 | Retira body MySQL, personas, historial registral/declarado y nombres ciudadanos; conserva recibo mínimo sellado, comprobantes originales, UUID, folio/COM, huellas y estado |
| Archivos y cuota | Reutiliza intención durable V9, elimina originales/derivadas/BLOB elegibles, libera reserva una vez, reanuda tras interrupción y compacta reservas validadas |
| Borradores | Vencimiento +48 h para identidad y archivos; siete días para metadatos abandonados sin Comunicación. Temporales incoming huérfanos >24 h, con exclusión de activos |
| Auditoría | Estado agregado operativo, holds separados de integración; auditoría técnica prescindible 30 d, conservando evidencias finales y comprobantes |
| Piso de disco | Admisión configurable para nuevas recepciones, identidades y cargas; la consulta y el reintento exacto de lo recibido siguen funcionando |

El formulario completo mantiene el mismo comprobante/ACK y respuesta idempotente tras minimizar. En el camino HTTP ciudadano histórico que devolvía el sobre personal, un POST repetido después de retirada devuelve **410 sin duplicar**; su GET autorizado mantiene registro/comprobante. No se reconstruyen datos personales ni se altera el contrato institucional documental.

## Pruebas finales

| Verificación | Resultado y alcance |
|---|---|
| Backend H2, Maven verify | **245 descubiertas, 243 correctas, 2 omisiones, 0 fallos/errores**; JAR generado en salida aislada |
| Backend MySQL real aislado | **245 descubiertas, 243 correctas, 2 omisiones, 0 fallos/errores**; exclusivamente `robust_public_test` |
| Frontend | **160 correctas**, 18 archivos; lint y build correctos. No se modificaron React ni recorridos ciudadanos en esta unidad |
| Backup/restore Python | **9 correctas**, incluidas coherencia de recibos V10 y rechazo de identidades residuales |
| Inventario Python | **7 correctas**; agregados, reservas y alertas |
| Agente de respaldo automático | Cuatro tests nuevos incluidos en las 245: publicación/restauración, reutilización, rechazo de corrupción y fuente ausente sin garantía |
| Programador + HTTP Spring | Temporizador real sobre motor real con datos sintéticos; garantía recibida mediante MockMvc, retirada y consulta posterior. TLS simulado en request: no se presenta como handshake de red real |
| Contraste SIGER real | Clases del JAR institucional existente, migraciones y JDBC reales sobre H2 en disco; versiones 1.0 BLOB y 1.1 FILE, COMMIT, ACK y custodia/recuperación completas |
| Restore público MySQL V10 | Backup consistente y restauración nueva: dos recibos, cero bodies e identidades; validación criptográfica de ambos recibos restaurados |
| Modelo numérico | Meseta binaria a uno/dos años, sin carga ni llamadas a servicios. Perfil uniforme hasta 300×250 MiB/mes: pico/reserva 5,596 GiB |
| Higiene | `git diff --check`, compilación Python, sintaxis systemd/Compose y revisión de archivos incorporados; sin claves privadas, RUN DEV autorizados, BD, dumps ni evidencias privadas en el commit |

Las dos omisiones opcionales son `MultimediaWorkersRealTest` (`MEDIA_REAL_CHECK`) y `RecuperacionProcesoTest` (`recuperacion.real`). No se lanzaron workers/cargas prolongadas ni se repitieron verificaciones gubernamentales. El informe multimedia anterior conserva su evidencia real de ClamAV/inspector; no se suma como prueba nueva de esta unidad.

### Qué demostró el ciclo con SIGER

`scripts/qa/verificar-custodia-siger.py` compila `CustodiaSigerIndependiente.java` fuera del repositorio institucional y emplea su artefacto existente:

1. Recibe una Comunicación completa sintética con relato de 5000 caracteres y original adjunto, en variantes 1.0/1.1.
2. SIGER registra COM y persiste documento/original/ACK con sus clases reales. Un registro restaurado con hash discrepante es rechazado.
3. Verifica restauración SQL; el agente automático publica el paquete en otro filesystem y restaura de él, sin consultar la copia pública para acreditar el respaldo.
4. La garantía firmada adicional habilita el ciclo público una vez vencido el plazo. Antes de esos requisitos se conserva.
5. El Formulario retira bytes, identidades y payload; mantiene el mismo comprobante y ACK.
6. Cierra y deja vacía la base pública de prueba; reinicia los componentes institucionales y recupera documento firmado íntegro, ACK y originales exactos desde SIGER.

Resultado: **`CUSTODIA_SIGER_INDEPENDIENTE_OK`**. SHA-256 del JAR institucional antes/después, sin cambios:

`2ed038c567b672e7c7aaab6ac04f83efd22101f0b0ea1f5d4e0a33333be4ca97`.

Se usaron dos filesystems sintéticos, incluido tmpfs para la prueba de independencia. **El CLI real rechaza tmpfs/ramfs/overlay como respaldo durable** y exige un medio institucional independiente. La prueba no acredita un desastre físico real, un trayecto TLS desplegado o la instalación del agente.

### Recuperación pública MySQL V10

Backup y restauración ejecutados con `media-archive.py` sobre datos creados exclusivamente para esta prueba:

- Origen sintético: `robust_public_test`, posteriormente limpiado por la batería de pruebas.
- Restore conservado: `robust_public_transient_restore_20261025`.
- Dos Comunicaciones minimizadas; `body IS NOT NULL = 0`, `citizen_person = 0`; recibos mínimos suman 6056 bytes, sin contar ACK separado.
- Checksums origen/restore concordantes: `citizen_submission=2761484430`, `custody_guarantee=2078382365`.
- Verificación Ed25519 posterior: **`RECIBOS_RESTAURADOS_CRIPTOGRAFICAMENTE_VALIDOS=2`**.
- Material de backup, claves públicas de validación y evidencias permanecen privados fuera de Git, bajo el directorio institucional de laboratorio del usuario. No se publican cuerpos, RUN ni claves privadas.

### Resiliencia y no acumulación

La batería cubre falta/caducidad/replay/falsificación de garantía; separación de claves y tokens; hold; cierre de banderas; carrera con recepción de otro recorrido; respuesta/idempotencia después de minimizar; reintento de ACK; fallo de transacción al minimizar y recuperación; fallo al liberar lease; bloqueo de lectura/retirada; limpieza de incoming activo/huérfano; cuotas y piso; vencimiento de borrador; alternancia de candidatos para evitar inanición; conservación de contratos.

Seis ciclos consecutivos verifican **cero payloads, personas, originales y reservas pendientes**, manteniendo seis recibos+ACK que totalizan 25 650 bytes en esa muestra. No se usa este número como tamaño exacto de todas las tablas o como promesa de trazabilidad infinita sin crecimiento.

## Presupuesto de 25 GB

Se mantienen 12 adjuntos y 250 MiB por Comunicación, con la política vigente por tipo. Perfil candidato: **6 GiB de cuota reservada**, piso de **4 GiB libres**, transferencia/garantía en 24 h y retención técnica de 24 h. La compactación online de reservas validadas forma parte de la hipótesis del modelo; una reserva es contable, no otro archivo físico.

Presupuesto: 6 GiB SO, 3 GiB runtime, 3 GiB MySQL, 6 GiB multimedia y 4 GiB margen = **22 GiB** frente a ~23,28 GiB disponibles en 25 GB decimales. En 300×20 MiB/mes el pico modelado es **0,488 GiB**; con ocho días de entrega/restauración, **2,129 GiB**. El extremo uniforme 300×250 MiB/mes queda en **5,596 GiB**, con poco margen de ráfaga.

**Es viable bajo esos presupuestos y plazos, no una capacidad física ya medida de la futura VM.** Los recibos mínimos e índices siguen creciendo lentamente (~0,11 GiB/año presupuestando 32 KiB por Comunicación típica y 300/mes). No hay almacenamiento finito que admita un bloqueo de SIGER o conservación de datos durante un tiempo ilimitado: se alerta y detiene admisión antes de comprometer lo recibido. No se eliminan pendientes para liberar cuota.

El overlay MySQL candidato establece imagen de binlog MINIMAL, vencimiento 72 h, segmentos de 64 MiB y rotación de logs Docker. **No fue aplicado.** Los BLOB a NULL liberan páginas para reutilización; no prometen reducir `.ibd` inmediatamente ni borrado forense. Respaldos públicos operativos y binlogs deben caducar coherentemente en infraestructura; los respaldos definitivos están en SIGER y fuera del SSD público. No almacenar backups completos ni cachés de compilación en ese presupuesto.

## Estado real de los laboratorios

| Elemento | Estado conservado |
|---|---|
| Integrado | `robust_public_local`: **13 custodias, 13 adjuntos, V7**; no se sustituyó JAR ni configuración |
| Multimedia | `robust_public_media_lab`: **V8**; multimedia sigue habilitada únicamente aquí |
| Contabilidad multimedia | **220 371 506 bytes** de reserva, igual a la suma no liberada; sin retirada de documentos existentes |
| Online nuevo | `ciclo-vida.online-habilitado`: ausente/false en ambos |
| Retirada | `ciclo-vida.eliminacion.habilitada`: ausente/false en ambos |
| V10 | Aplicada solo a esquemas sintéticos y restauración aislada |
| SIGER | Working tree sin cambios; JAR idéntico; ningún cambio de consumidor, esquema real o despliegue |
| Publicación | Ningún push; main/origin no modificados por esta unidad |

Los servicios, volúmenes y configuraciones existentes no fueron recreados. Las guardas previas de producción, ClaveÚnica y SRCeI no se relajaron. No se generaron Comunicaciones reales ni consultas gubernamentales.

## Activación pendiente en infraestructura, sin desarrollo conceptual bloqueado

1. Calificar el tamaño real SO/runtime/MySQL y presupuesto de binlogs/respaldos en la VM de 25 GB. Usar inventario agregado y alertas existentes (cuota 80/95 %, espacio preventivo a piso+1 GiB y crítico bajo piso).
2. En el entorno institucional compatible Linux/POSIX, instalar el agente desde el JAR del Formulario: cuenta SELECT local TLS, archivo de SIGER, respaldo durable independiente, scratch privado y clave exclusiva de garantía. No se instala el repositorio SIGER ni su archivo en la VM pública.
3. Comprobar respaldo/restore de un ingreso sintético con el agente y consulta de originales desde SIGER sin Formulario. El modo automático está implementado; no requiere exportación manual de cada lote. El backup general institucional sigue siendo una dependencia operacional separada.
4. Respaldar y comprobar restore del Formulario; aplicar V10 con cuenta de migración por el procedimiento habitual. Conservar confianza histórica y clave de propiedad HMAC. No modificar migraciones aplicadas ni restaurar un JAR antiguo sobre V10 minimizada.
5. Activar primero solo el programador, con retirada **false**. Verificar reloj, TLS, llegada/renovación de garantías, holds, estado operativo y alertas. Instalar el timer institucional de 15 min.
6. Tras comprobar esos requisitos, habilitar retirada en un conjunto controlado y verificar después cuerpo ausente, recibo conservado, reservas concordantes y custodia SIGER íntegra. El mantenimiento normal es online; el backup/restore offline de laboratorio no es el planificador.

No se pide otra decisión genérica sobre quién custodia: está resuelta y aplicada. Quedan instalación, configuración y calificación del ambiente real antes de permitir una retirada destructiva. No se declara el producto certificado, habilitado públicamente ni terminado su hardening general.

## Referencias para operación y desarrollo

- [Arquitectura, garantía, parámetros y procedimientos](ARQUITECTURA_RECEPTOR_TRANSITORIO.md).
- [Contrato documental multimedia 1.1 y compatibilidad 1.0](../contratos/formulario-completo-multimedia-v1.1.md).
- [Custodia completa y referencias históricas](CUSTODIA_FORMULARIO_COMPLETO.md).
- [Baseline funcional evolutivo; estimación operativa del 96 %](BASELINE_FORMULARIO_PUBLICO_2026-10-24.md).
- [Operación/respaldos multimedia](OPERACION_MULTIMEDIA.md).
- [Plan de hardening general diferido](PLAN_HARDENING_PRODUCCION_DIFERIDO.md).
- Perfiles: `infra/capacidad-25gb.properties.example`, `infra/compose.mysql-25gb.yml`, `infra/garantia-custodia.properties.example`, `infra/systemd/siger-garantia-custodia.{service,timer}`.

Evidencias privadas de esta ejecución en `.local/evidencias/transitorio-*`: baterías H2/MySQL/frontend/Python, modelo numérico, ciclo SIGER y verificación criptográfica de recibos restaurados. No incorporarlas a Git.

**Próximo paso operativo:** una vez publicada la consolidación, ejecutar la activación gradual anterior en el entorno de destino cuando esté autorizado. No rehacer formularios, infraestructura ni V9; no añadir otras funcionalidades sin nuevo encargo.
