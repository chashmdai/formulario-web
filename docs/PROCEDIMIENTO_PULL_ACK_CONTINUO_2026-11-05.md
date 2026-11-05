# Activación única y reversión — PULL/ACK continuo Ubuntu–SMR

**Procedimiento preparado, no ejecutado.** No es una orden de apertura. Informe: [candidato y estado](INFORME_PULL_ACK_CONTINUO_2026-11-05.md).

## Requisitos de la única activación inicial

1. Aprobación explícita de custodia continua en el Windows de desarrollo hasta migración, alcance sobre **toda la cola elegible**, responsable/aprobador y referencia común. Ni el primer E2E ni esta preparación convierten su autorización temporal vencida en continua. No hay filtro de campaña/UUID en reclamar.
2. Windows instala y califica el paquete r2 aprobado: identidad/DPAPI/cuenta real, MySQL/NTFS propios, servicio SCM y recuperación, respaldo/restore y entrega de control. El informe recibido dice que ese trabajo todavía no está desplegado ni calificado. No alterar SIGER QA, MySQL80 ni Java global; no fusionar ramas de SIGER desde Ubuntu.
3. Un único consumidor autorizado; detener/inhabilitar cualquier perfil anterior con ese origen/Bearer. Los locks de DB/archivo de un PC no prueban exclusividad entre máquinas.
4. Confirmar salud, HTTPS y Bearer 401/401/404 sin reclamar, confianza ACK/huellas existentes y conservación de los dos ingresos/folios/originales/ACK. No enviar datos ni hacer consultas personales para una sonda.
5. Acordar respaldo externo, alertas/capacidad, mantenimiento y recuperación de secretos. El respaldo local no acredita pérdida total del host. No inventar RPO/RTO ni una política de retención.

## Instalación posterior de las herramientas

Verificar SHA-256 del paquete y cada entrada del manifiesto por canal independiente. Confirmar que la imagen/JAR/base productiva coinciden con el manifiesto; si cambiaron, revisar diferencias antes de instalar. El JAR del paquete es referencia del mismo RC1, **no requiere reemplazo**.

En una intervención futura autorizada, adquirir en modo no bloqueante `backup-operation.lock` para no sustituir scripts mientras corre una copia. Guardar copia privada con SHA y propietario/permisos de `scripts/ubuntu/respaldo-programado.py`, `respaldo.py` y del nuevo `continuidad.py` si ya existiera. Instalar solo esos tres scripts en `/srv/ddn-formulario-produccion/release/scripts/ubuntu/`, con permisos de administrador y de lectura/ejecución apropiados, verificando sus hashes contra el paquete. `productor.py` debe coincidir con la dependencia incluida y no se sustituye por rutina. No copiar ejemplos, SQL, configuración o secretos.

No es necesario recrear app, ejecutar Compose, migrar SQL ni recargar systemd. Las unidades/timer existentes ya leen los scripts desde esa ruta. Registrar manifiesto/commit operacional en custodia separada; no reinterpretarlo como nuevo commit del binario Java. Conservar las herramientas anteriores para reversión.

## Lectura de estado y apertura CAS preparada

Comando de solo lectura tras instalar las herramientas:

```sh
sudo python3 /srv/ddn-formulario-produccion/release/scripts/ubuntu/continuidad.py \
  --root /srv/ddn-formulario-produccion status
```

La revisión **18** fue observada durante la preparación; **no** es un valor para copiar el día de activación. Leer la revisión actual inmediatamente antes de escribir. Confirmar que recepción está abierta, modo NORMAL y PULL cerrado. No reabrir un incidente o una pausa ajenos para conseguir ese estado.

La huella del mapa ACK se comprueba sobre bytes del JSON instalado. Revalidarla contra el intercambio público aprobado; no mostrar ni leer claves privadas. Con la misma referencia acordada con Windows y con autorización explícita de la activación:

```sh
sudo python3 /srv/ddn-formulario-produccion/release/scripts/ubuntu/continuidad.py \
  --root /srv/ddn-formulario-produccion activate \
  --if-match REVISION_RECIEN_LEIDA \
  --reference CONTINUA-FORM-SMR-20261105-01 \
  --ack-id smr-windows-20261104-v1 \
  --trust-sha256 HUELLA_ACTUAL_VERIFICADA_DEL_MAPA
```

Los marcadores no son comandos listos para ejecutar. La herramienta exige los valores reales. Solo cambia recuperar=true, preservando recepción y modo. No toca `authorization/open.approved.json`, archivos de contraseña/caducidad, sesiones, configuración, claves o certificados. **No utilizar `productor.py open` para esta activación:** su rutina general también escribe configuración/autorización ciudadana, fuera del alcance de esta tarea.

Después de verificar GET de estado y misma revisión resultante, Ubuntu comunica apertura a Windows. Windows completa únicamente las confirmaciones verdaderas de su autorización **CONTINUA** y activa el consumidor mediante su procedimiento aprobado. Su script debe conservar lectura/consulta mientras se califica transporte; si exige confirmar apertura Ubuntu antes de instalar autorización, mantener consumidor pausado durante ese intervalo y verificar exclusividad.

Desde entonces no se programan ventanas diarias ni se ejecuta `activate` por cron/systemd. Windows conserva su intención/autorización válida; Ubuntu conserva CAS en SQL. Monitorear ciclos, reintentos, registros/folios/ACK y espacio. Una pausa/revocación explícita exige diagnóstico y nueva decisión del responsable, nunca una reapertura automática. Los reinicios del servicio no renuevan aprobaciones revocadas.

## Pausa explícita sin cerrar recepción

Pausar/drenar Windows primero cuando sea posible; conservar sus ACK pendientes. Leer revisión actual y ejecutar únicamente si se autoriza la suspensión:

```sh
sudo python3 /srv/ddn-formulario-produccion/release/scripts/ubuntu/continuidad.py \
  --root /srv/ddn-formulario-produccion pause \
  --if-match REVISION_RECIEN_LEIDA \
  --reference INCIDENCIA-FORM-SMR-REFERENCIA_REAL
```

Conserva aceptarNuevos y contingencia, solo establece recuperar=false. No cancela un COMMIT/ACK ya en vuelo. Si la respuesta es incierta o hay 412, volver a inspeccionar y coordinar: no reintentar ciegamente. Para un incidente que exija cerrar también recepción, se usa el procedimiento operacional existente con autorización específica, no esta pausa parcial.

## Respaldo, reinicio y recuperación

El timer vigente sigue su calendario existente; no se crea otro. Un backup iniciado con PULL pausado nunca lo abre. Si empezó activo, solo restaura sus flags anteriores con el ETag del cierre. Si hubo pausa/cambio de revisión durante la copia, conserva esa decisión y registra el respaldo sin restaurar controles. Un fallo de copia, capacidad o resultado CAS incierto no fuerza apertura.

No probar reboot de DDN-UBUNTU durante preparación: tiene servicios históricos. La configuración de arranque fue inspeccionada y el comportamiento JDBC/MySQL se probó aisladamente. La calificación de reboot completo y de recuperación conjunta Windows–Ubuntu requiere TI y ventana propia. La copia programada se toma tras cerrar CAS; ese cierre se conserva en el dump, y no debe sustituirse automáticamente por la intención anterior del registro de backup. Si se restaura SQL en **otro destino** desde copia con pull_enabled=true, iniciar con recuperación cerrada por configuración, sin acceso del consumidor y sin dos productores/receptores activos. Cerrar CAS bajo control antes de entregar red/consumidor. Un estado activo en el dump no autoriza activar otra máquina.

## Reversión controlada

Pausar PULL mediante CAS y coordinar drenaje/conciliación Windows. Conservar admisión si no existe un incidente ciudadano. Bajo el mismo lock de backup, restaurar los scripts operacionales anteriores verificados; el nuevo CLI puede quedar inerte sin ejecutarse. No restaurar SQL anterior, no borrar volúmenes, no deshacer COM/ACK ni rotar identidades por volver de herramientas. No hay cambio de JAR o esquema que revertir.

En Windows, su reversión a RC1 temporal conserva datos pero la autorización previa ya venció; debe permanecer pausado hasta nueva decisión, sin inventar otra ventana. Mantener PULL Ubuntu cerrado. Registrar estado final de controles/servicios/respaldos y custodia. Una cola vacía no prueba que un ACK pendiente se haya conciliado: comprobar ambos lados.
