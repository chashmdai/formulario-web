# Decisiones técnicas · SIGER 2026

Fecha: 14 de octubre de 2026. Fuentes institucionales: manual ClaveÚnica 6.5 (mayo de 2026), guía del botón 2.0 y observaciones de fidelidad locales. `nexbytedevstack.md` es referencia de ingeniería, no definición funcional.

| Problema | Solución elegida y justificación |
|---|---|
| Inicio de repositorio | `backend` y `front` inspeccionados: vacíos. Git inicializado dentro de SIGER; sin remoto ni publicación. Monolito modular Java por capacidades `identidad` y `comunicaciones`; configuración transversal mínima. |
| Compatibilidad | Java 25; Spring Boot 4.1.1, Spring Security 7.1.1 mediante BOM; Maven 3.9.15. Boot publicado en Maven Central y [compatible con Java 25](https://docs.spring.io/spring-boot/system-requirements.html). React 19.3.0, TS 6.0.3, Vite 8.3.4; versiones exactas y lock npm. |
| Contrato particular CU | OAuth2 Client OIDC de Spring. `client_secret_post`; scopes `openid run name`; `state` añadido al POST token; POST userinfo con Bearer; conversión de `name` conservada como objeto. Adaptaciones requeridas por secciones 3.4 y 3.6 del manual. Spring conserva validación de firma RS256, issuer, audience, tiempo, nonce y correlación de sub entre ID token y userinfo. |
| Metadatos | [Discovery real](https://accounts.claveunica.gob.cl/openid/.well-known/openid-configuration) confirma issuer y JWKS. Endpoints de authorize/token/userinfo usan barras finales del manual. El logout utiliza el endpoint del paso 7, no el `end_session_endpoint` de discovery. GET de ambas variantes logout, sin credenciales ni cookies institucionales, respondió 204. Falta comprobar su efecto con una sesión real. |
| Algoritmo y límites HTTP | RS256 por defecto, acorde al ejemplo del manual; HS256 seleccionable externamente porque discovery lo anuncia, solo después de confirmar uso institucional. Nunca selección basada en `alg` recibido. Decoder Nimbus con validadores OIDC/tiempo de Spring; acceso JWKS, token y userinfo con 5s de conexión y 10s de lectura, sin redirecciones automáticas ni reintentos del código de un solo uso. |
| PKCE | Spring Security 7.1 lo agrega por defecto incluso a clientes confidenciales. Se desactiva explícitamente: el manual y discovery consultado no declaran soporte. Es un cliente confidencial con secreto exclusivamente en backend; se conservan state y nonce. No se asumió soporte no documentado. |
| Ambientes | Mismo código y artefacto para Sandbox/QA/Producción. Un único par externo por proceso; script de desarrollo selecciona exclusivamente Sandbox. El nombre de ambiente no demuestra que un par pertenezca a él: el operador debe suministrar el par correcto. Sin carga automática de QA o producción. |
| Callback | URI explícita, nunca derivada de Host o cabeceras de usuario. Inicio bloqueado por defecto hasta `CU_CALLBACK_APPROVED=true`. El dominio registrado no resuelve; no se alteró infraestructura. Cambiar a localhost requiere registro previo institucional, no basta cambiar la configuración local. |
| Sesión | Sesión HTTP del servidor, 20 minutos de inactividad (decisión técnica provisional), cookie HttpOnly/SameSite=Lax/Secure por defecto; rotación de ID al autenticar. Solo RUN, nombres y apellidos permanecen en sesión: se descartan tokens y autoridades OIDC que podrían contenerlos. No se refrescan ni se almacenan tokens. |
| CSRF y transporte | CSRF de Spring habilitado para POST de Comunicación y logout. Sin CORS abierto. Origen único: Vite proxy en desarrollo; React empaquetado dentro del JAR en despliegue. Producción exige HTTPS, cookie Secure y dominio .gob.cl; no confía en forwarded headers por defecto. Proxy TLS requiere configuración de confianza explícita de operación. |
| Callbacks falsos y repetidos | Un callback no puede cerrar una sesión ciudadana ya verificada: se rechaza con 409. Retorno sin solicitud o state inválido vuelve al portal con error fijo y no llama logout CU. Limpieza CU tras un flujo iniciado que falla al obtener/validar tokens o identidad. Evita logout forzado mediante navegación maliciosa a un callback. |
| Cierre de sesión | POST local con CSRF invalida sesión y cookie; navegación de página completa al GET oficial con Logout URI registrada. El retorno `/logout` también invalida sesión. Fallo OIDC limpia sesión local y navega al logout del proveedor. Tras expiración, la próxima interacción del navegador llama logout; un servidor no puede redirigir un navegador cerrado/inactivo. No iframe ni popup. |
| Identidad | Solo `userinfo.RolUnico.numero`, `DV`, `name.nombres`, `name.apellidos`. `sub` se usa exclusivamente para validación OIDC de correlación, nunca como RUN o llave de negocio. Validación estructural sin recalcular DV: los RUN sintéticos oficiales Sandbox no satisfacen necesariamente módulo 11. |
| Comunicación mínima | POST `/api/comunicaciones/preview` valida mensaje y muestra identidad tomada de sesión. DTO no acepta identidad remitida por cliente. No registra ni envía Comunicaciones; texto explícito en UI/API. Persistencia, retención, recepción definitiva y API pública quedan por validar. Sin MySQL/Flyway en esta fase, sin tocar servidor remoto. |
| UI | Botón HTML interactivo: SVG original descargado de los ejemplos oficiales del manual, Roboto local, cotas y colores guía 2.0. El CSS oficial contiene `48x` y una discrepancia `.text`/`.texto`: se priorizan las cotas del documento, 48px mínimo, separación 4px. No se reproduce una captura como botón. |
| Dependencias UI | No se agregan router, biblioteca de componentes ni gestor de estado remoto para una sola vista. Tipos TS estrictos y API separada; crecer conforme aparezcan rutas y necesidades reales. |

La implementación no define roles institucionales, bandejas, Requerimientos, Casos ni PISEE. La duración de sesión y la operación productiva deben ser revisadas por la Defensoría antes de despliegue.

## HTTPS local para completar Sandbox — 14 de octubre de 2026

Problema: callback sin DNS disponible mientras TI prepara publicación; el usuario confirmó el nombre exacto `claveunica.formularioadulto.defensorianinez.gob.cl` y autorizó su resolución hosts local.

Solución: mismo JAR con React, Caddy portable en loopback:443 y mkcert con CA propia confiada solo por el usuario actual. Tomcat acepta forwarded headers exclusivamente desde loopback en un archivo externo aplicado solo al perfil local Sandbox. Credenciales elegidas exclusivamente del par Sandbox; login bloqueado hasta resolver sistema, validar TLS, cookie Secure y rutas. Se habilitó después de esas verificaciones y se completó el flujo real dos veces.

Justificación: conserva URIs registradas y lógica OIDC, evita publicación o túneles y permite validar el backend real. No constituye publicación ni certificación productiva. Scripts registran procesos para detener solo los propios y retiran únicamente el bloque hosts y CA de esta prueba. Logs de autenticación guardan ambiente/resultado/código de error sanitizado, sin identidad, tokens ni descripciones del proveedor que puedan incluir datos sensibles.

## Dos aplicaciones independientes — 14 de octubre de 2026

Esta decisión solicitada por el usuario sustituye la topología inicial de un solo SIGER.

| Problema | Solución y justificación |
|---|---|
| Público e institucional tienen alojamiento y acceso distintos | Mover código ciudadano a `formulario-publico/backend` y `front`; crear `siger-institucional/backend` sin React/OAuth2 ni credenciales CU. POM y scripts propios, sin dependencia Maven entre aplicaciones. Se conserva lógica OIDC, cookie, configuración por ambiente y las 18 pruebas originales. |
| VM sin conexiones públicas entrantes | SIGER recupera y confirma por HTTPS saliente contra API pública; React solo usa su origen. No se presupone conectividad directa público → SIGER. |
| Evitar pérdida mientras SIGER está apagado | Puerto de custodia transitoria pública; aceptar solo después de guardar. Confirmación institucional únicamente después de COMMIT. Contrato v1, lectura no destructiva, identificadores estables y reintentos idempotentes. |
| Infraestructura/almacenamiento aún no autorizados | Adaptadores de archivos exclusivos del perfil local de demo, con bloqueo de escritor y publicación atómica. Recepción/exportación productiva deshabilitada; sin base adicional, broker ni conexión al MySQL remoto. Puerto institucional definitivo pendiente de implementar con su almacenamiento aprobado. |
| No confundir aceptación con ingreso institucional | UI y API distinguen PENDIENTE_SIGER de CONFIRMADA_SIGER; aviso provisional solo para pruebas, no texto legal definitivo. No se inventa folio o proceso institucional. |
| Autenticación máquina distinta de ciudadanía | Frontera stateless con credencial Bearer externa, HTTPS fuera de loopback demo, sin cookies ni tokens CU. El script demo genera secreto efímero; esquema y rotación productivos pendientes. |
| Conservar Sandbox Windows | Reutilizar `.local`, hosts y CA existentes; scripts ajustados a las nuevas rutas. `FORMULARIO.cmd` abre el público; `SIGER.cmd` el institucional. Mismas URIs registradas, sin cambios de QA/Producción ni infraestructura. |
| PISEE/SRCeI sin conectividad definida | Mantener decisión pendiente; no implementar cliente o dependencia ficticia. Se decidirá ubicación y contrato según conectividad/autorización. |

Ver `ARQUITECTURA_APLICACIONES.md`, `MIGRACION_APLICACIONES.md` y `../contratos/README.md` para garantías y límites de la demo. No se declara resuelto transporte productivo.

## Robustez, carga y PISEE — 14 de octubre de 2026

| Problema | Decisión y justificación |
|---|---|
| La demo de archivos no basta para concurrencia/recepción durable | Perfil opcional JDBC con MySQL 8.4, transacción para ingreso/comprobante/evento y unicidad. Dos esquemas/cuentas independientes, migraciones Flyway explícitas. Se conserva el perfil histórico y CU sin DB. Es necesaria custodia pública para aceptar cuando SIGER no está disponible; no se añade broker. |
| Reintentos de red y ACK perdido | Contrato v2 envolviendo v1, reservas con token/vencimiento, backoff, cuarentena y ACK pos-COMMIT idempotente. Entrega repetible e incorporación única; no se promete exactly-once en la red. |
| Procedencia manipulable y comprobantes reproducibles | Evidencia de autenticación del servidor, niveles por atributo, huellas con formato definido y firma Ed25519 por clave externa. Confianza pública independiente en SIGER. Firma técnica sin afirmar FEA. |
| Contingencia y conciliación necesitan estado durable | Controles CAS auditados y registro de operación externa; timeout ambiguo no reenvía. API técnica separada de sesión ciudadana. Identidad individual de operadores/SSO permanece pendiente. |
| Carga inicial de ráfaga rechazaba conexiones y admisión 48 rechazaba a 64 clientes | Cola Tomcat 256 y admisión 64, medidas con HTTP local real; pool 12, límites de cuerpo/JSON/tiempos y 429 explícito. Cifras no son SLA productivo. |
| Servidor remoto no accesible por SSH no interactivo | MySQL 8.4.11 portable oficial, sin servicio Windows, solo loopback:13306. Testing y demo separados; app DML y migrador DDL por esquema. No se tocó servidor remoto. TLS verificado exigido fuera del laboratorio. |
| Cliente Java no usa automáticamente la CA CurrentUser de Windows | CA explícita solo del cliente HTTPS institucional al origen local. Mantiene verificación de certificado/hostname; no altera confianza global. |
| Taller PISEE no acredita identidad Defensoría | Ejecutar copia aislada con identidad original y dos GET documentados, luego detener. Se verificaron 200/200 remotos del taller; no envíos ni SRCeI. Adaptador de lectura probado, no habilitado por defecto; ubicación final y permisos institucionales pendientes. Sustituye la restricción previa de no implementar cliente alguno en esta fase ampliada autorizada por el usuario. |

Garantías, métricas y pendientes: `ROBUSTEZ_Y_OPERACION.md`, `CARGA_Y_RECUPERACION_2026-10-14.md`, `AUDITORIA_PISEE_2026-10-14.md`.

## Comunicación formal y comprobante institucional — 14 de octubre de 2026

Estas decisiones evolucionan la entrega anterior sin cambiar su topología ni eliminar obligaciones contractuales.

| Problema | Solución y justificación |
|---|---|
| Operación técnica confundida con Comunicación institucional | UUID, fecha UTC, canal y referencia única al origen/operación; cuerpo, registro y trazabilidad en una transacción. Sin procesos ni estados de negocio pendientes de levantamiento. |
| Folios concurrentes y formato provisional | AUTO_INCREMENT MySQL y unicidad en base, prefijo/ancho externos congelados por registro. Sin MAX+1. Se admiten huecos y el formato cambia solo para nuevos ingresos; UUID como identidad estable. |
| Sello público anterior no prueba registro institucional | Conservarlo únicamente en transporte. SIGER firma/persiste comprobante definitivo junto a Comunicación, visible tras COMMIT. Privada distinta de la pública, confianza externa y separación comprobada. No se afirma FEA. |
| Respuesta de ACK perdida tras COMMIT público | Persistir reserva del ACK y repetir confirmaciones desde SIGER sin reclamar de nuevo una entrega ya confirmada. ACK idéntico aceptado repetible; fencing se mantiene para uno nuevo. |
| ACK histórico sin registro formal | V3 conserva cuerpos/ACK antiguos y reabre entregas para registro real. Fecha actual al promover, sin folio retrospectivo inventado. Actualización coordinada de ambos extremos. |
| Confirmación corrupta bloquea otras | Aislar su reserva conciliable con evento durable, conservar datos y continuar otras. Restauración desde evidencia íntegra requiere atención operacional; nunca omitir validación. |
| Acumulado lento con margen CPU | Cuatro trabajadores, configurables 1–8, lote activo máximo 20, pool DB conservado. 5.000 registros pasaron de 220,53 s a 134,34 s sin relajar fsync o transacciones. Sin garantía FIFO. |
| CU y futuros atributos SRCeI pueden discrepar | Proyección por atributo/fuente/nivel/evidencia y lista autorizada del servidor. Solo CU activa; SRCeI NO_HABILITADO. Sin adaptador real; pruebas sintéticas explícitas, sin resolución funcional automática. |

Flujo, comandos y evidencia: [registro](REGISTRO_COMUNICACION.md), [pruebas](PRUEBAS_REGISTRO_COMUNICACION.md), [entrega](ENTREGA_REGISTRO_COMUNICACION_2026-10-14.md).

## Componentes originales de identidad — 14 de octubre de 2026

Problema: concatenar userinfo.name borraba los límites entre nombres y apellidos. Solución: listas inmutables originales en principal, sesión y contratos; cadenas derivadas de compatibilidad, sin trim ni normalización. UI muestra cada elemento readonly y no deduce separaciones. Sobres nuevos 1.1 incluyen límites/orden/cantidades en la huella y la evidencia de sesión; 1.0 histórico conserva cuerpos y firmas sin reconstrucción. Ambas aplicaciones aceptan las dos versiones, sin migración SQL ni cambios OIDC/registro institucional. El despliegue de los dos lectores debe preceder a la recepción de 1.1. Se mantienen límites de seguridad y se rechaza entrada inválida en lugar de corregirla silenciosamente.

[Contrato](../contratos/identidad-componentes-v1.1.md) y [pruebas reales](IDENTIDAD_COMPONENTES_2026-10-14.md).

## Presentación RUN/DV e investigación — 15 de octubre de 2026

Problema: el formulario requiere número y DV independientes y dos nombres/apellidos visibles. Solución mínima: conservar directamente numero/DV del proveedor en la sesión y su proyección; verificar coherencia con RUN completo. UI limita solo la presentación, sin dividir cadenas ni modificar listas, sobre 1.1, huellas o datos históricos. No se crean migraciones ni personas. Para persistencia futura se recomienda mantener instantáneas y evaluar una referencia de persona institucional solo después de Hito 1; ninguna actualización/fusión automática autorizada.

[Entrega y pruebas](ENTREGA_RUN_DV_2026-10-15.md) y [diagnóstico con fuentes oficiales](IDENTIDAD_CLAVEUNICA_ATRIBUTOS_Y_PERSISTENCIA_2026-10-14.md).
# Iteración 19-10-2026 · decisiones adicionales

- **ACK bloqueantes:** conservar el lote de veinte y procesamiento secuencial de conciliación; añadir aplazamiento durable, orden por oportunidad, límites 3/8 para persistentes/transitorios y revisión auditable. Resolver un incidente no equivale a confirmarlo. [Detalle](CORRECCIONES_TECNICAS_2026-10-19.md).
- **URLs MySQL:** gramática y propiedades admitidas explícitamente en servicio y migrador. Una cadena que contiene VERIFY_IDENTITY no basta; rechazar duplicados/aliases/codificación ambigua antes de conectar. Se conserva solo la excepción loopback de laboratorio.
- **Protocolo retirado:** HTTP V2 devuelve 410 sin reservar; HTTP V3 y lectores de documentos históricos permanecen. No renombrar clases internas históricas si no aporta funcionalidad.
- **Identidad NNA:** nueva declaración de sobre 2.0 y entrega 2.1, sin principal CU ficticio ni RUN obligatorio. Procedencia por campo declarada; firma y huella propias sin tocar bytes históricos. Canal FORMULARIO_WEB_NNA y comprobante institucional 1.1 distinguen el origen sin crear categorías de negocio.
- **Propiedad y recarga:** secreto interno de sesión para nuevos ingresos HTTP adultos/NNA; owner_run nullable, owner_session adicional. Mantener custodia durable aunque termine la sesión. Sin recuperación intersesión ni sessionStorage, por decisión funcional expresa. Reintentos activos conservan clave en memoria.
- **Habilitación:** prototipo NNA opt-in, exclusivamente perfiles de laboratorio + Sandbox + loopback + HTTPS + cookie Secure; abortar activación en producción. Cuotas temporales acotadas por sesión/IP/global complementan tamaño y concurrencia, sin infraestructura adicional; protección productiva pendiente.
- **Campos y frontend:** pequeña separación fields/types, adaptador API, componente NNA y panel común de entrega. Región declarada como texto, sin catálogo inventado. Campos institucionales definitivos y aviso quedan pendientes DDN. [Guía](FORMULARIO_NNA_PROVISIONAL.md).


## Núcleo institucional provisional — 19 de octubre de 2026

- Gestión separada: V5 core_* referencia el original firmado, sin editarlo. Manual conserva operador, original, huellas y fechas propias; operación de transporte nula, sin identidad/comprobante ciudadano ficticios.
- Agrupador concurrente: bloqueo de Comunicación, FK compuestas y unicidad de Requerimiento por origen. Primer Caso, agrupador, auditoría y comando idempotente confirman juntos. Revisiones obsoletas producen conflicto; sin MAX()+1 ni unicidad por unidad.
- Término: cierre individual no finaliza evaluación. Provisionalmente, jefatura responde sin Caso si no abrió ninguno o finaliza evaluación cuando todos cerrados. Destino resuelto no admite nuevos Casos. Reevaluación global al cerrar Caso y resolver destino; derivación/reapertura/redistribución bloqueadas hasta DDN.
- Acceso: React en JAR institucional propio, sesión HTTPS/CSRF distinta de CU y tokens técnicos. Identidades sintéticas solo con perfiles y MySQL loopback explícitos; producción rechazada. Matriz por recurso/unidad/sede y asignación, pendiente aprobación. Proxy/certificado existentes en otro puerto loopback, sin DNS nuevo.
- Vínculo: no reabre. Caso explícito determina destinatario de aviso interno; de otro modo queda pendiente auditable. Guard transaccional impide ciclos concurrentes. Ninguna notificación externa.

[Guía, límites y decisiones pendientes](NUCLEO_INSTITUCIONAL_LAB.md) · [Pruebas ejecutadas](ENTREGA_NUCLEO_2026-10-19.md).
- Lectura consistente: una única instantánea REPEATABLE_READ por consulta compuesta de buzón/detalle/Caso; evita presentar detalle anterior con resumen posterior a un cierre concurrente. Probado con decisión confirmada en otro hilo durante la lectura sobre MySQL. Mutaciones conservan su bloqueo explícito y READ_COMMITTED existente.

## BAJA y derivación aceptada — 19-10-2026, siguiente iteración acotada

- Confirmado según nota aportada: clasificación por Caso; profesional responsable cierra BAJA con fundamento; jefatura destino aprueba derivación. Código nullable BAJA separado del texto, sin reclasificar históricos; solo jefatura clasifica con motivo y autor/fecha. V6 conserva texto y cierres anteriores.
- Propuesta: solicitud conserva origen EN_GESTION; aceptación crea Caso vinculado en mismo Requerimiento y cierra origen por DERIVACION, en una sola transacción. V7 agrega solicitud/decisión y permite responsable NULL hasta asignación explícita de destino; sin heredar complejidad. Destino existente debe ser compatible/EVALUANDO; jamás reabrir RESUELTO.
- Provisional: solo jefatura origen promueve; una pendiente por Caso; bloquear cierre ordinario y solicitud de término durante espera, pero permitir actuaciones. Rechazo/cancelación/escalamiento/reapertura siguen 409 sin efecto. No inventar catálogo mayor ni ruta definitiva no BAJA.
- Orígenes: funcionario HTTPS configurable, por defecto localhost:8443, certificado separado firmado por la CA local ya confiable; público conserva host/443, URI CU y SIGER_PUBLIC_SOURCE. Sin cambios hosts/DNS/confianza. Cookies host-only, no aislamiento por puerto. Proxy puede iniciarse con SIGER sin público.
- Login repetido: devolver copia nueva de UserDetails en cada autenticación, evitando que el borrado normal de credenciales de Spring invalide la plantilla de laboratorio. Mantener cifrado, CSRF, sesión y restricciones, no desactivar eraseCredentials.

[Guía y decisiones pendientes](COMPLEJIDAD_Y_DERIVACION_LAB.md) · [entrega y pruebas](ENTREGA_DERIVACION_2026-10-19.md).

## 20-10-2026 · Antecedentes, autorizaciones y espacio de trabajo V8

- Autoridad nueva: F04 está confirmada por Benjamín, reemplazando su carácter de propuesta de la nota anterior. F05–F09 separan vínculo documental, admisión profesional y autorización de reapertura. Rechazo/cancelación de DERIVACION siguen bloqueados; no equivalen al rechazo de una reapertura.
- V8 añade decisiones y períodos sin reinterpretar originales, vínculos históricos ni cierres. Una relación por hija conserva el límite anterior; no se generalizan múltiples contextos ni fusiones sin validación. Vincular a Requerimiento existente sin Caso deja encaminamiento pendiente.
- Responsable profesional vigente por Caso y alcance; jefatura unidad/sede decide reapertura ordinaria. La asignación de responsable a un cerrado reutiliza la facultad restringida de su jefatura y no lo reabre ni reclasifica. Referencia antigua de responsable fuera de unidad no devuelve visibilidad.
- Bloqueo de Comunicación raíz, revisiones y comandos idempotentes conservados. Una pendiente por Caso/cierre. Cambios de responsable o antecedentes invalidan la instantánea; sustitución explícita conserva solicitud anterior SUPERADA. Una denegación no genera otro ciclo con idénticos antecedentes. Son límites técnicos reversibles, pendientes de evaluación institucional de excepciones.
- Aprobar conserva cierre histórico y apertura original; registra autorización y período, reanuda solo el destino afectado RESUELTO (propuesta provisional del encargo) y activa agrupador. DERIVACION queda excluida para no deshacer continuidad en otro Caso.
- UX por consultas acotadas y paginadas del servidor; sin entidad Task, motores de workflow o persistencia local de relatos. Hash de navegación conserva contexto de lectura tras recarga, nunca comandos. Formularios de decisión mantienen fundamento solo en memoria; actualización de conflicto disponible dentro del panel.
- Tokens CSS SIGER, fallback local de tipografía y assets del JAR; sin Mantine, Tailwind u otras dependencias nuevas. Mockup HTML solo como referencia de navegación, nunca como autoridad funcional o sustituto del núcleo.