> Actualización 20-10-2026: el nuevo encargo de Benjamín confirma F04 (origen abierto hasta aceptación, nuevo Caso bajo el mismo Requerimiento y cierre por DERIVACION). Reemplaza su tratamiento anterior como propuesta. F05–F09 habilitan evaluación de hijas y solicitud/decisión de reapertura ordinaria; no habilitan rechazo/cancelación de derivaciones ni reapertura de DERIVACION. Esta entrega se conserva como evidencia histórica; reglas vigentes en [Antecedentes y reaperturas](ANTECEDENTES_Y_REAPERTURAS_LAB.md).

# Entrega acotada: acceso propio, complejidad y aceptación · 19-10-2026

Se partió del núcleo vigente V5, sin reconstruirlo. Se conservan dos aplicaciones/JAR/esquemas independientes, adulto/NNA, procedencia, custodia, Ed25519, ACK, idempotencia, permisos y auditoría. No se alteraron fuentes del formulario público, categorías ciudadanas, CU/URI, DNS, CA pública, VM, SIGER legado ni servicios externos. No hay habilitación productiva.

## Conjuntos de revisión y archivos

| Conjunto | Archivos y secciones principales |
|---|---|
| **1. Acceso local** | `scripts/{Origen-Funcionario,Test-Sesiones-Separadas,Nna-Local,Nucleo-Local}.ps1`; `scripts/Cliente-Funcionario.mjs` y adaptación de `Probar-Nucleo.mjs`. Host funcionario configurable y certificado separado, proxy reutilizable con SIGER primero. `SeguridadInstitucional.java` devuelve copia de usuario por login; regresión en `NucleoHttpTest`. |
| **2. Complejidad/cierre** | V6 en `backend/src/main/resources/db/institucional/{mysql,h2}`. `nucleo/ContratosNucleo`, métodos `clasificar`, `revisarCaso`, `classificationHistory`/`caseView` de `NucleoInstitucional`, y rutas en `NucleoController`. `front/src/{api.ts,App.tsx,App.test.tsx}`. Código BAJA separado del texto, historial fundamentado y cierre profesional transaccional. |
| **3. Derivación** | V7 MySQL/H2; DTO de solicitud/aceptación; métodos `solicitarDerivacion`, `aceptarDerivacion`, consultas de alcance y guard de pendiente en el mismo núcleo; rutas explícitas. Nuevo `front/src/Derivaciones.tsx`, ampliaciones de App/API; `scripts/Probar-Derivacion.mjs`. `DerivacionComplejidadTest`, `NucleoHttpTest`, ajuste de expectativas vigentes en `NucleoInstitucionalTest`, limpieza de nuevas FK solo en `DatosNucleoTest`. |

Las rutas Java anteriores están bajo `siger-institucional/backend/src/main/java/cl/nexbyte/siger/institucional/`; frontend bajo `siger-institucional/front`. Son conjuntos conceptuales de revisión: los archivos compartidos contienen secciones de ambos cambios de negocio, no tres despliegues independientes. Copias/hashes previos: `.local/derivacion-baseline`. Comparación local y manifiesto de cambios: `.local/revision-derivacion`. No se agregaron dependencias ni commits masivos de este repositorio sin HEAD.

Migraciones nuevas **V6 y V7**, aplicadas a MySQL de pruebas/local y verificadas en H2. V1–V5 no se editan. V6 agrega clasificación nullable, modalidad e historial sin reclasificar textos o cierres. V7 admite Caso nuevo sin responsable y persiste solicitud/aceptación. No se modifican algoritmos de huella ni comprobantes. No eliminar esquemas para actualizar: detener aplicación, compilar y usar el script habitual, que migra con cuenta DDL separada antes del servicio.

## Qué quedó implementado

Según nota aportada: BAJA se clasifica por Caso y únicamente jefatura puede hacerlo/reclasificarlo con fundamento, autor y fecha. Profesional responsable actual puede cerrar EN_GESTION/BAJA con fundamento, sin aprobación ni actuaciones mínimas; el backend revalida todo y la revisión dentro de transacción. El cuerpo de cierre no contiene rol, responsable ni complejidad. Históricos permanecen no determinados; texto libre no habilita facultades. Modalidad PROFESIONAL_BAJA visible y auditada; no cierra hermanos/destinos. Para no BAJA continúa la ruta restrictiva anterior, expresamente provisional.

Aprobación de derivación por jefatura destino, con secuencia propuesta: solicitud mantiene origen abierto; aceptación atómica crea Caso vinculado al mismo Requerimiento y cierra origen por DERIVACION. El Caso nuevo no hereda responsable ni complejidad. Se asigna explícitamente después por destino. Caso destino existente no se reutiliza: se crea otro Caso; el **destino de atención** puede ser nuevo o compatible EVALUANDO. RESUELTO no se reabre.

Jefatura origen promueve provisionalmente; una pendiente, sin bifurcaciones. Mientras espera se permiten actuaciones pero no cierre ordinario ni solicitud de término, para conservar la secuencia propuesta. No hay pausas de plazos. Destino solo ve solicitud/antecedentes expresos, no bitácora/Caso original ajenos. Autorizaciones se comprueban por recurso y alcance. Rechazo/cancelación/escalamiento/reapertura permanecen bloqueados, sin efectos inventados. El ciclo completo de derivación **no** se declara terminado.

Una falla revierte aceptación, Caso/destino nuevo, cierre, auditoría y comando; origen sigue abierto. Misma clave y contenido recuperan resultado estable incluso concurrentemente; claves distintas concurrentes producen una aceptación y un 409. Versiones de solicitud, origen y destino existente se validan de nuevo bajo bloqueo. No se finaliza el Requerimiento intermedio ni se resuelven destinos automáticamente.

## Evidencia ejecutada

| Pruebas | Resultado real |
|---|---|
| Backend público/MySQL | 70 descubiertas: **66 exitosas**, 4 optativas omitidas, 0 fallos/errores. Adulto con proveedor de prueba, NNA, sesión/CSRF, controles, TLS/migrador, V2 410, contratos/firma/idempotencia. |
| Backend institucional/MySQL final | **68 exitosas**, sin omitidas/fallos. Incluye ACK >20, recuperación de respuestas perdidas, recibos históricos, folios, permisos y nuevos escenarios. |
| Institucional/H2 | Batería completa final de **68 exitosas**, incluyendo BAJA bloqueada durante espera y el evento de cierre inequívoco. Migraciones V6/V7 ejecutadas. |
| React/TypeScript | **13 públicas y 8 institucionales**, lint/build de ambas exitosos. Clasificación/permiso BAJA, cierre pendiente bloqueado con actuación disponible, aceptación sin responsable heredado y botones pendientes deshabilitados. |
| Acceso HTTPS | TLS real confiable, SAN localhost, seis orígenes inválidos rechazados; CookieContainer nativo prueba cookies por host, sin Domain y logout en ambas direcciones. Sesión ciudadana nueva/anónima, sin login CU real. |

Omitidas explícitamente: `CargaBaseTest`, `CargaAcumuladaTest`, `CargaHttpTest` y `RecuperacionProcesoTest` (activación optativa).

Las nuevas pruebas cubren cierre BAJA sin actuaciones previas; otro profesional/UUID; texto y cuerpo HTTP manipulados; reclasificación/reasignación obsoletas y reasignación concurrente; solicitud sin traslado; origen que no se autoacepta; otro alcance que no lee/acepta; nuevo Caso sin herencia; destino EVALUANDO/resuelto; aceptación repetida y dos concurrentes con misma/distinta clave; rollback después de actualizar aceptación; rechazo/reapertura bloqueados. El término global y sus regresiones previas permanecen.

Se detectó durante verificación real un defecto preexistente: reutilizar UserDetails hacía que Spring borrara la contraseña de la plantilla después del primer login. Se corrigió entregando una copia nueva por autenticación, conservando el borrado normal de credenciales. Pasan tres ciclos login/logout por la misma cuenta y los logins repetidos reales de demostración. No se relajaron seguridad ni controles para superar el error.

Logs privados: `.local/derivacion-regresiones-mysql.log` (público), `.local/derivacion-final-mysql.log` (institucional final), `.local/derivacion-final-h2.log`, `.local/derivacion-cierre-pendiente-h2.log`. [Resumen MySQL](evidencias/derivacion-2026-10-19/junit-mysql.json). No se ejecutaron carga, caída de procesos, autenticación gubernamental, PISEE/SRCeI ni notificaciones reales. No se afirma inspección visual completa: UI validada por pruebas React y recorrido HTTPS real.

## Demostración y estado final

`Nucleo-Local.ps1 demostrar-derivacion` completó:

1. NNA sin RUN → custodia pública MySQL → pull HTTPS desde SIGER → **COM-000023** → ACK/comprobante Ed25519 válido. [Resultado de recepción](evidencias/derivacion-2026-10-19/resultado-http.json), [firma](evidencias/derivacion-2026-10-19/verificacion-firma.json).
2. Dos Casos UPR del mismo Requerimiento: uno cerrado por su profesional BAJA, sin actuaciones previas; otro solicita UPD y conserva actuaciones antes/durante espera. Una aceptación con versión obsoleta fue rechazada; aceptación vigente y repetición misma clave devuelven un Caso vinculado. Origen cierra por DERIVACION, Caso nuevo sin clasificación/responsable heredados; asignación destino posterior explícita. [Evidencia de gestión separada](evidencias/derivacion-2026-10-19/resultado-derivacion.json).
3. Tres registros de Caso finales, Requerimiento ACTIVO, un Caso abierto y dos destinos pendientes: no cierre global falso. **30 huellas de filas de custodia/inbox anteriores permanecen iguales**, cuerpo/ACK coinciden entre bases y ACK no pendiente. [Originales firmados](evidencias/derivacion-2026-10-19/originales-firmados-resumen.json).
4. Se detuvieron ambos y se inició **solo SIGER**, incluido proxy, con 8080 apagado: interfaz, sesión y buzón autenticados respondieron por HTTPS. Después se inició público reutilizando el proxy y se verificaron logouts independientes. [Interfaz independiente](evidencias/derivacion-2026-10-19/interfaz-independiente.json), [sesiones](evidencias/derivacion-2026-10-19/sesiones-separadas.json), [guardas/SAN](evidencias/derivacion-2026-10-19/origen-guardas.json).

Quedan encendidos MySQL 127.0.0.1:13306, público 127.0.0.1:8080/HTTPS 443 y SIGER 127.0.0.1:8081/HTTPS 8443 en 127.0.0.1 y ::1. [Funcionario: https://localhost:8443](https://localhost:8443/). [Público: origen habitual](https://claveunica.formularioadulto.defensorianinez.gob.cl/). CU Sandbox volvió a habilitarse mediante preflight exclusivamente local, sin seguir redirecciones gubernamentales. No se instala nueva confianza: certificado funcionario separado, CA confiable existente; no se toca certificado público, DNS ni firewall.

[Comandos, recorrido y reglas confirmadas/propuestas](COMPLEJIDAD_Y_DERIVACION_LAB.md) · [contrato](../contratos/nucleo-complejidad-derivacion-lab.md). Pendiente DDN: promoción/cancelación/rechazo/escalamiento, catálogo completo, cierre no BAJA, bitácoras/matriz real, excepciones de espera, significado final de destinos y validación de la secuencia propuesta. Sin publicación ni habilitación productiva.


