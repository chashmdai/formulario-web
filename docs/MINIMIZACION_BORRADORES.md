# Minimización de identidad de borradores

`formulario.minimizacion-borradores.habilitada=false` conserva el comportamiento cerrado por defecto.
Activarla requiere **autorización institucional expresa y un plazo aprobado**; preparar o desplegar
este código no autoriza purgas. La decisión, su responsable y el plazo deben quedar registrados
fuera del repositorio, en la configuración/evidencia operacional correspondiente.

El interruptor es independiente de `ciclo-vida.online-habilitado` y
`ciclo-vida.eliminacion.habilitada`. El plazo ISO-8601 es `PT48H` por defecto, validado entre
`PT1H` y `P90D`, contado desde la caducidad `expires_ms` del recorrido (actualmente cuatro horas
después de iniciarlo). El intervalo propio es `PT5M`, entre `PT1M` y `P1D`; primera pasada al minuto.
La cancelación explícita intenta minimizar inmediatamente, después de confirmar su transacción,
sin esperar ese plazo. Un bloqueo o incidencia deja el recorrido para una pasada posterior,
cuando alcance `expires_ms + plazo` y se libere el impedimento. Los bytes pendientes no impiden
retirar la identidad, tampoco al cancelar.

Sólo se minimizan `BORRADOR`/`CANCELADO` sin `citizen_submission`, bajo gate de mantenimiento del
almacén, guardia de respaldo y bloqueo SQL del recorrido con revalidación transaccional. Se retiran
`citizen_person` y `citizen_identity_history` aunque queden cargas sin liberar o adjuntos con bytes.
En ese caso **sólo se borran esas dos tablas**: estado, sesión propietaria, nombres, errores,
reservas, marcadores, contenido BLOB y archivos del almacén permanecen intactos. Así los
mantenimientos de bytes siguen encontrando `BORRADOR`/`CANCELADO` con sus estados actuales.
Cuando no quedan pendientes, se neutralizan nombres de recursos ya retirados y la sesión
propietaria, y se marca `MINIMIZADO`, incluso si la identidad se retiró en una pasada anterior.
Las pasadas son acotadas, con cursor para evitar
que los primeros recorridos bloqueados impidan revisar los siguientes. El ciclo de vida ya no
inicia esta minimización: ambos servicios comparten el borrado común y el almacén/gates.

No afecta recorridos recibidos ni posteriores, documentos, ACK, recibos mínimos, originales
custodiados ni la eliminación de custodia. No libera reservas ni retira bytes. Tras retirar la
identidad, el recorrido con `released=false` o `bytes_retired=false` sale de los candidatos hasta
que otro mecanismo autorizado libere ambos tipos de pendientes. La recuperación de cargas abandonadas
de U1 libera reservas recuperables sin depender del ciclo de vida. Los adjuntos publicados o
clásicos con bytes todavía presentes dependen de su retirada autorizada; este mecanismo no la activa.
La depuración posterior de metadatos técnicos continúa bajo los controles existentes del ciclo de vida.

La retirada parcial registra `MINIMIZACION_BORRADORES / IDENTIDAD_BORRADOR_RETIRADA` sólo si
se borró al menos una fila de identidad; incluye identidad vigente e historial, aunque sólo
quede una de las tablas. Repetir la pasada o el hook de cancelación sin filas no genera otro
evento ni incrementa `minimizadas`. La finalización completa conserva el evento
`MINIMIZACION_BORRADORES / BORRADOR_PERSONAL_RETIRADO`, una vez por transición a `MINIMIZADO`.
Ambos eventos llevan UUID técnico y ningún antecedente personal.
`GET /operacion/v1/custodia` conserva su protección
TLS/token y `no-store`; incorpora `minimizacionBorradores` incluso con el ciclo de vida apagado.
Expone contadores desde el arranque: `minimizadas` cuenta recorridos en los que se borró identidad;
`completadas` cuenta transiciones a `MINIMIZADO`. Una retirada completa con identidad incrementa
ambos; completar después de retirar sólo identidad incrementa únicamente `completadas`, evitando
contar dos veces la retirada de identidad. `conservadas` cuenta intentos sin cambios e
`incidencias` intentos fallidos. El resultado de cada pasada usa esos mismos contadores.
También expone última pasada y plazo. No son conteos de personas ni un inventario persistente; los eventos confirmados
permiten verificar el trabajo realizado. Apagar el interruptor impide trabajo futuro, sin restaurar
datos ya minimizados. La gestión de copias de respaldo sigue su política institucional separada.

## Flujos posteriores y vigencia

`RecorridosJdbc.fila` rechaza con 404 recorridos vencidos, cancelados o minimizados antes de
consultar identidad: cubre consultas, vista de persona, identificación, declaraciones,
reinicios, agregado interno y revalidación/derivación NNA. La cancelación explícita también
cierra el recorrido inmediatamente, aunque aún no haya vencido.
`RecepcionFormularioJdbc.recibir` exige vigencia antes de preparar adjuntos y vuelve a
bloquear/revalidar el borrador dentro de la transacción antes de leer personas y custodiar.
Un borrador vencido no puede recibirse. El reintento de una recepción ya confirmada se resuelve
por su documento/recibo existente; esos recorridos están excluidos de este minimizador.

El saneamiento de `CargasMultimedia` y el abandono de `CicloVidaCustodia` consultan recorrido,
cargas, adjuntos y ausencia de recepción; no requieren `citizen_person`. `CustodiaTransitoria`
opera con documentos/recibos firmados para lo recibido y delega los bytes abandonados al ciclo
de vida; su depuración técnica espera `MINIMIZADO` y ausencia de pendientes. Borrar sólo identidad
preserva esas condiciones y permite su continuación cuando se autorice cada mantenimiento.

Las regresiones incluyen borradores/cancelados con adjuntos validados y cargas no liberadas,
integridad de BLOB/original/derivada y metadatos, cada tipo de pendiente por separado,
cancelación inmediata, repetición, finalización posterior y rechazo de recepción vencida.
`MinimizacionBorradoresSqlTest` comprueba la transacción con gates simulados; las pruebas con
almacén real verifican adicionalmente archivos y exclusión. No se equiparan ambas evidencias.
