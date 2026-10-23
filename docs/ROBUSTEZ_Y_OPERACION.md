# Recepción robusta — 14 de octubre de 2026

**Actualización posterior de esta fecha:** [registro de Comunicación y comprobante definitivo](REGISTRO_COMUNICACION.md). La descripción del comprobante inmediato de esta entrega corresponde al sello de custodia pública original; ahora queda interno. Solo el registro transaccional en SIGER habilita el comprobante definitivo institucional. Las demás cinco capacidades se conservan.

Esta iteración implementa infraestructura técnica verificable. No define el formulario final, admisibilidad, bandejas, permisos funcionarios ni procesos institucionales. Las dos aplicaciones mantienen compilación, configuración y ejecución independientes. ClaveÚnica sigue en el público y no requiere MySQL en el perfil habitual.

## Cinco mejoras

| Mejora | Implementación y evidencia | Definición institucional pendiente |
|---|---|---|
| Idempotencia de ingreso | UUID por intento lógico, índice único transaccional; huella de RUN de sesión + texto canónico + versión de aviso. El mismo contenido conserva ID, fecha y comprobante; cambios con la misma clave producen 409. 72 llamadas concurrentes por almacén y 200 duplicados HTTP. SIGER deduplica por origen + ID. | Retención de claves y reglas para distinguir dos comunicaciones legítimas de texto idéntico. No se deduplica por contenido entre claves diferentes. |
| Procedencia por atributo | RUN/nombres/apellidos: `CLAVE_UNICA_USERINFO / IDENTIDAD_AUTENTICADA`, vinculados al evento de autenticación del servidor. Mensaje: `CIUDADANO / DECLARADO_NO_CONTRASTADO`. Huellas y firma cubren contenido y procedencia. El cliente no puede enviar identidad o elevar verificaciones. | Atributos definitivos y niveles para futuras fuentes. No se atribuye verificación SRCeI a ningún dato. |
| Conciliación | Journal durable por entrega, reservas con vencimiento y token de exclusión, backoff con jitter, ACK después de COMMIT institucional, recuperación de ACK perdido, cuarentena y reintento operacional explícito que vuelve a verificar integridad. Registro de operaciones externas con CAS; un timeout de notificación queda `DESCONOCIDA` y bloquea reenvío ciego. Adaptador PISEE de lectura acotada, sin envíos. | Canal, eventos notificables, conciliación contra referencias reales, responsables, alertas y plazos. El registro técnico no acredita una notificación enviada. |
| Habilitación y contingencia | Recepción opt-in, aviso versionado y habilitado externamente, controles persistentes CAS (`If-Match`), ingreso y recuperación independientes, modo `SOLO_CONSULTA`, historial antes/después/motivo, credencial operacional distinta de integración y ciudadanía. Consultas, comprobantes y ACK de entregas existentes siguen disponibles al cerrar ingreso. | Validación del aviso, autorizadores, turnos operacionales, SSO/roles del operador y contingencia institucional. El actor actual es una credencial técnica, no identifica un funcionario. |
| Comprobante inmediato | Comprobante Ed25519 generado y almacenado junto al ingreso y evento en una transacción. Solo se responde después de COMMIT; descarga JSON autenticada por propietario. Incluye ID, UTC, emisor, versión del aviso y huellas, sin RUN/nombres ni mensaje en el comprobante. SIGER verifica con claves públicas confiadas externamente. | Redacción, presentación oficial/PDF, eventual firma avanzada, sellado de tiempo y efectos institucionales. La firma técnica no es FEA ni certificación de Gobierno Digital. |

**Custodia pública, incorporación en SIGER y notificación Casilla Única son hitos diferentes.** El estado ciudadano no convierte un mensaje pendiente en ingreso institucional. No hay envíos de notificaciones implementados o realizados.

## Persistencia y fallos

`jdbc-reception` utiliza MySQL 8.4/InnoDB, Spring JDBC, Hikari y Flyway. Cada aplicación tiene esquema, cuenta DML y cuenta de migración propios. El almacén público transitorio es necesario: sin almacenamiento durable no se puede confirmar recepción mientras SIGER está apagado. Se reutiliza un motor; no se incorporan broker, Redis ni servicios adicionales. Los adaptadores de archivos quedan exclusivamente en el perfil histórico `local-integration-demo`, incompatible con JDBC.

Migraciones explícitas `java -jar <jar> --migrate` mediante `MIGRATION_DB_URL`, `MIGRATION_DB_USER`, `MIGRATION_DB_PASSWORD`. Las aplicaciones usan cuentas sin DDL y no migran automáticamente al arrancar. Fuera de los esquemas loopback de laboratorio se exige `sslMode=VERIFY_IDENTITY`. Configurar CA/hostname reales y límites de conexión/lectura JDBC en despliegue. Nunca poner usuario/contraseña en la URL.

Datos, procedencia, comprobante y journal se confirman juntos. La operación de red ocurre fuera de la transacción. SIGER solo confirma después de guardar. Si se pierde el ACK, la nueva reserva devuelve el recibo institucional original. Una reserva antigua no puede confirmar una reserva posterior. Las reclamaciones concurrentes usan actualización condicional; no se garantiza orden FIFO estricto.

Errores transitorios dejan datos recuperables con backoff. Tras diez intentos, un contrato inválido o una alteración de integridad se aísla la entrega en cuarentena. El operador puede reponer solo datos íntegros mediante el endpoint técnico; no existe un botón para ignorar la firma o editar el cuerpo. La integridad alterada exige investigar/restaurar desde un respaldo verificado. No se borra automáticamente una entrega confirmada: retención y depuración quedan pendientes.

La base indisponible impide emitir una recepción nueva. Si el COMMIT ocurrió y se perdió la respuesta HTTP, el navegador debe repetir **la misma clave**, nunca inferir rechazo definitivo. React conserva UUID/huella en `sessionStorage`, por sesión de autenticación; no almacena mensaje o identidad. El usuario debe volver a preparar el mismo contenido tras recargar. Otra sesión necesita conservar/consultar su identificador; no se promete recuperar un texto no almacenado en el navegador.

## Operación técnica

API `/operacion/v1/**`: Bearer operacional externo, HTTPS; HTTP únicamente con bind loopback, perfil JDBC y habilitación explícita. Una sesión ciudadana no concede acceso. No publicar esta API en Internet sin restricción de red/reverse proxy y gestión institucional de operadores.

- `GET /control` y `PUT /control`, este último con `If-Match: "revision"`. Ejemplo de cuerpo: `{"aceptarNuevos":false,"recuperar":false,"contingencia":"SOLO_CONSULTA","motivo":"INCIDENTE"}`. Una revisión obsoleta devuelve 412.
- `GET /controles-historial`, `/eventos?despues=0&limite=100`, `/entregas`, `/entregas-pendientes`, `/externas`, `/resumen`. Metadatos técnicos acotados, sin nombres, RUN ni mensajes. Los eventos usan cursor y límite máximo 200; las listas de pendientes/externas muestran hasta 100 registros.
- Público: `POST /entregas/{id}/reintentar` con `{"intentosEsperados":10,"motivo":"RECEPTOR_RECUPERADO"}`; solo cuarentena íntegra, guarda evento. El motivo alternativo es `CORRECCION_ADAPTADOR`.
- `POST /externas` con `{"id":"UUID","tipo":"NOTIFICACION"}` prepara un registro técnico. `PUT /externas/{id}` recibe `revision`, `estado`, `referenciaProveedor`, `codigo`. No dispara el proveedor. `DESCONOCIDA` solo pasa a `CONFIRMADA` por consulta o a `REQUIERE_REVISION`; no vuelve a `SOLICITADA`.

Límites iniciales medidos: 64 operaciones simultáneas, JSON 8 KiB, profundidad 16, cadenas 4096, nombres duplicados rechazados, texto 2000 con normalización NFC y saltos de línea, rechazo de Unicode inválido y controles. Pool 12, adquisición 3 s, SQL 4 s, transacción 5 s; Tomcat 80 hilos/512 conexiones/cola 256. Son límites iniciales configurables, no dimensionamiento productivo. Ver resultados de carga.

El consumidor usa HTTPS saliente, sin redirects, timeout y cuerpo máximo 512 KiB. Para la CA mkcert local se configura una CA específica de esa conexión Java; no se desactiva verificación ni se modifica el truststore global del JDK. La sesión ciudadana conserva cookies Secure/HttpOnly/SameSite, CSRF y control del callback anterior.

## Windows: repetir sin depender de Codex

Desde la raíz del repositorio, PowerShell 7.4, Java 25, Maven y Node disponibles:

```powershell
.\scripts\Robustez.ps1 preparar      # MySQL portable, solo loopback:13306
.\scripts\Robustez.ps1 pruebas       # MySQL real, esquemas de pruebas aislados
.\scripts\Robustez.ps1 carga         # HTTP real local + datos sintéticos; nunca servicios gubernamentales
.\scripts\Robustez.ps1 recuperacion  # caída abrupta SOLO del MySQL portable propio y verificación posterior
.\scripts\Robustez.ps1 pisee         # dos lecturas del taller, sin envíos; detiene el nodo en finally
```

Demostración interactiva con ClaveÚnica real, una vez construidos ambos JAR:

```powershell
.\FORMULARIO.cmd detener
.\SIGER.cmd detener
.\scripts\Build-Todo.ps1
.\scripts\Recepcion-Local.ps1 preparar   # esquemas locales distintos de tests, claves y migraciones
.\scripts\Recepcion-Local.ps1 publico   # independiente; mismo HTTPS/URIs, preflight antes de CU
.\scripts\Recepcion-Local.ps1 siger     # independiente; sin React o credenciales CU
.\scripts\Recepcion-Local.ps1 habilitar # habilita ingreso/recuperación SOLO del laboratorio sintético
.\scripts\Recepcion-Local.ps1 estado
.\scripts\Recepcion-Local.ps1 detener
.\scripts\Robustez.ps1 detener-db
```

Usar únicamente RUN de prueba oficial `44.444.444-4` / `testing` en Gobierno Digital. Abrir `https://claveunica.formularioadulto.defensorianinez.gob.cl`, no Vite ni localhost. Revisar mensaje, aceptar el aviso **de laboratorio**, enviar, descargar comprobante y consultar incorporación. El script no valida un aviso institucional: `PUBLIC_PRIVACY_APPROVED` se activa solo con versión `laboratorio-v1`, datos sintéticos y configuración local. Los controles SQL pueden conservar un estado previo; `habilitar` aplica un cambio auditado.

**Demostración efectivamente completada:** nueva autenticación CU Sandbox, campos readonly con datos de userinfo, Comunicación sintética `433229a4-ac49-473d-ab33-3bb9de288e13`, comprobante descargado y verificado independientemente con Node.js, recuperación HTTPS por SIGER y ACK confirmado, seguido de cierre de sesión CU. Ver `evidencias/robustez-2026-10-14/sandbox-recepcion-real.json` y captura. El ingreso reside solo en esquemas locales de laboratorio; no es una Comunicación institucional real.

Los secretos locales se cifran con DPAPI del usuario Windows; la privada Ed25519 está en directorio con ACL restringida `.local/robustez/recepcion`, ignorado por Git. No regenerarla si ya hay comprobantes. Respaldar clave privada, conjunto histórico de claves públicas, bases y configuración juntos, mediante procedimiento seguro. Probar restauración integral y rotación antes de producción. DPAPI depende de usuario/equipo: la recuperación institucional de secretos requiere un mecanismo aprobado, no copiar el XML a otro servidor esperando descifrarlo.

Los scripts no instalan servicios Windows, abren firewall, alteran DNS público o usan el servidor MySQL remoto. El MySQL portable tiene esquemas `robust_*_test` para pruebas y `robust_*_local` para demostración. Las pruebas limpian únicamente sus tablas de testing. Detener la demo antes de probar/compilar. La reversión de hosts/CA se conserva en `SANDBOX_HTTPS_LOCAL.md`.

## Pendiente antes de habilitación pública

Alojamiento público, TLS/DNS y callbacks autorizados; MySQL institucional y público con respaldo/restauración, cifrado y controles de acceso; secretos y rotación; aviso/finalidad/conservación; monitoreo y responsables; autenticación de máquinas y operadores con identidad individual; protección perimetral y límites por origen/sesión; pruebas de seguridad, volumen sostenido y recuperación de infraestructura definitiva. El inbox institucional implementa la frontera durable, no el modelo definitivo de Comunicaciones ni sus procesos. La sesión CU actual es de una instancia: reiniciar requiere reautenticación; alta disponibilidad de sesión no está resuelta.
