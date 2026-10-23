> Estado inicial del 19-10 conservado como historia. La actualización del 20-10 confirma F04 y habilita evaluación de hijas/reapertura ordinaria según [V8 vigente](ANTECEDENTES_Y_REAPERTURAS_LAB.md). No habilita rechazo/cancelación de derivaciones ni reapertura de DERIVACION.

# Núcleo institucional de laboratorio · 19-10-2026

**Actualización:** [complejidad estructurada y solicitud/aceptación de derivación](COMPLEJIDAD_Y_DERIVACION_LAB.md). Esa extensión prevalece sobre las limitaciones de la primera iteración descritas abajo.

Iteración funcional sobre [el flujo propuesto](flujo.consolidado-sinformsinbit.md). No constituye habilitación productiva ni aprobación de reglas DDN. Adulto CU y NNA mantienen sus contratos, identidad, custodia, ACK y comprobantes. Solo se agrega gestión separada en SIGER.

## Iniciar y recorrer

Desde `C:\Users\Benja\Desktop\pisee-claveunica\SIGER`, PowerShell 7.4+, con las herramientas y HTTPS local ya preparados:

```powershell
# Si hay instancias anteriores gestionadas por Nna-Local/Nucleo-Local:
.\scripts\Nucleo-Local.ps1 detener
# Builds independientes; el público y SIGER empaquetan su propio React.
.\scripts\Build-Todo.ps1
# Arranque normal conserva CU Sandbox. No necesita Vite.
.\scripts\Nucleo-Local.ps1 iniciar
# Obtener exclusivamente la contraseña sintética local (no copiarla a documentación):
.\scripts\Nucleo-Local.ps1 acceso
.\scripts\Nucleo-Local.ps1 estado
```

Abrir [SIGER institucional HTTPS](https://localhost:8443/). El [público adulto](https://claveunica.formularioadulto.defensorianinez.gob.cl/) y el [NNA](https://claveunica.formularioadulto.defensorianinez.gob.cl/?formulario=nna) siguen en 443. Son dos aplicaciones/sesiones independientes. Se reutiliza el proxy y la CA confiable existentes, emitiendo un certificado funcionario separado; no se alteran hosts, DNS, CA ni URIs CU. El puerto 8443 se liga a loopback, sin publicación externa. Credencial sintética nueva cifrada con DPAPI en `.local/nna/nucleo-password.clixml`, fuera de Git; los secretos institucionales no se sustituyen.

1. Ingresar como `demo.distribucion`: buzón con registros web ya incorporados; abrir `COM-000009` para el primer recorrido ejecutado o una Comunicación nueva de demostración. Hay paginación por secuencia técnica y filtro de la página actual.
2. Registrar otra Comunicación manual con canal y fecha declarados. Marcar «ya respondida» para comprobar que no crea Requerimiento ni Caso. Operador y fecha técnica se obtienen del servidor; no se genera identidad/comprobante ciudadano ficticio.
3. En una Comunicación abierta, añadir destinos UPR/A y UPD/A y confirmar. No se usa región o materia para decidirlos.
4. Cerrar sesión e ingresar como `demo.jefatura.upr.a`. Abrir dos Casos en el mismo destino, para `demo.profesional.upr.a` y `demo.profesional2.upr.a`. Ambos comparten el Requerimiento técnico.
5. Con cada profesional, registrar actuaciones y solicitar término. Jefatura puede devolver con observaciones y después cerrar cada Caso. El historial permanece. Jefatura también puede asumir un Caso y registrar actuación antes de cerrarlo personalmente.
6. Con jefatura UPR finalizar explícitamente la evaluación del destino cuando sus Casos estén cerrados. El Requerimiento sigue activo mientras UPD esté pendiente. Con `demo.jefatura.upd.a`, responder ese destino sin Caso: ahora termina el agrupador.
7. Con distribución, vincular una Comunicación hija al registro, indicando el Caso de referencia. Con su responsable, revisar y marcar leído el aviso interno. Ningún Caso se reabre.

Los usuarios y las sedes `DEMO_SEDE_A/B` son inequívocamente sintéticos. No representan personas, presencia territorial ni matriz DDN aprobadas.

## Demostración y pruebas reproducibles

```powershell
# Pruebas aisladas sin llamadas CU ni otros servicios gubernamentales:
.\scripts\Nucleo-Local.ps1 detener
.\scripts\Nucleo-Local.ps1 pruebas
.\scripts\Nucleo-Local.ps1 iniciar -SinClaveUnica
.\scripts\Nucleo-Local.ps1 demostrar
# Cerrar y volver al arranque habitual con CU habilitada solo tras preflight local:
.\scripts\Nucleo-Local.ps1 detener
.\scripts\Nucleo-Local.ps1 iniciar
```

`demostrar` crea datos sintéticos durables nuevos. No limpia el laboratorio. Primero ejecuta NNA real por HTTPS → MySQL público → pull HTTPS de SIGER → MySQL institucional → ACK → verificación Ed25519. Después ejecuta las decisiones institucionales por sesiones HTTPS con CSRF real y coteja de nuevo las huellas anteriores. Evidencias privadas en `.local/nna/evidencias/<fecha>/`, resumen sin secretos en [la entrega](ENTREGA_NUCLEO_2026-10-19.md).

`pruebas` usa exclusivamente `robust_public_test` y `robust_inst_test`, cuentas DML y migrador separadas. El perfil JDBC local emplea MySQL portable existente en 13306; no usa el servidor remoto. No ejecutar baterías de caída/carga sobre procesos con datos reales. No se han ejecutado esas baterías optativas en esta iteración.

Independencia: `detener-publico` conserva SIGER y proxy; `detener-siger` conserva público y proxy. `publico` inicia solo ese backend y el proxy cuando están detenidos; `siger` inicia solo SIGER. Ambos comandos de laboratorio aprovisionan/verifican los dos esquemas locales con los JAR ya compilados, pero no requieren que el otro backend esté encendido. El proxy compartido es una comodidad local. Cada aplicación tiene build/configuración/despliegue propios. `detener` conserva MySQL y todos los datos; no instala servicios Windows.

## Matriz mínima restrictiva, provisional

| Actor sintético | Lectura | Operaciones |
|---|---|---|
| Distribución central | Comunicaciones, destinos, metadatos de Casos e historial; sin textos de actuaciones/revisiones | Manual, respuesta/descarte antes de distribuir, distribución explícita, vínculo documental |
| Jefatura unidad+sede | Original de Comunicaciones con destino en su alcance; destinos/Casos de su unidad+sede | Abrir/asignar, actuaciones, revisar término, resolver evaluación de sus destinos |
| Profesional | Original de Comunicaciones donde tiene un Caso asignado; sus Casos y actuaciones | Actuar y solicitar término en sus Casos en gestión; leer sus avisos |

Presencias sintéticas limitadas: UPR/A, UPD/A, UEP/B y UGI/B. No se presume presencia de todas las unidades en todas las sedes. No hay coordinador regional habilitado: sus competencias y visibilidad requieren definición. Un UUID conocido no otorga acceso; no se toman rol, autor o unidad desde el cuerpo HTTP. Los resúmenes globales incluyen cantidades/estado del agrupador, no actuaciones de terceros. Esa visibilidad resumida también queda sujeta a aprobación DDN.

Sesión `SIGERSTAFFSESSION`: Secure, HttpOnly, SameSite=Strict, 20 minutos, fijación renovada al login, CSRF activo también en login/logout, no CORS abierto. Claves de comando en memoria del componente; no restauración del formulario tras recarga. Un reintento con misma clave/actor/acción/huella devuelve el resultado confirmado; otro contenido produce conflicto. Revisiones comprueban concurrencia obsoleta. Login limitado a 30 intentos/minuto por IP; tamaño HTTP 8 KiB, concurrencia del filtro y pool JDBC existentes. No son un diseño de acceso institucional definitivo.

El gate aborta identidades sintéticas fuera de perfiles `nucleo-lab,jdbc-reception`, entorno `LABORATORIO`, MySQL loopback `robust_inst_local|test`, cookie Secure y bind 127.0.0.1. Por defecto está cerrado. En producción falta autenticación funcionaria institucional y matriz aprobada; no trasladar cuentas de demostración. El puerto backend queda en loopback, confiando forwarding solo del proxy loopback que sobrescribe cabeceras. Un usuario local con control del equipo no forma parte de esa frontera de confianza.

## Modelo, atomicidad y límites

La V5 agrega tablas `core_*` (catálogo mínimo sintético, atención, destino, Requerimiento, Caso, asignaciones, actuaciones, revisiones, vínculos, avisos, eventos y comandos). Comunicación → 0/1 Requerimiento, 0/N destinos; Requerimiento → N Casos; destino → 0/N Casos. No existe unicidad por Requerimiento+unidad. UUID estables; Requerimiento/Caso muestran identificadores técnicos, sin inventar folios oficiales. No hay entidad Solicitud ni maestro de personas.

Las consultas compuestas (buzón, detalle y Caso) usan una instantánea transaccional de lectura REPEATABLE_READ para no mezclar resúmenes y decisiones de momentos diferentes. Todas las mutaciones bloquean la fila original de Comunicación y confirman cambios, comando idempotente y auditoría en una transacción. El primer Caso crea el Requerimiento bajo ese bloqueo y restricción única. FK compuestas impiden Caso asociado a otro origen. La reevaluación ocurre tanto al modificar Casos como al resolver destinos. Un fallo posterior a reevaluar revierte todo. Los enlaces toman un guard de base para impedir ciclos concurrentes; máximo 100 ancestros, conflicto explícito al excederlo. No hay procesamiento concurrente ilimitado.

La única alteración del registro anterior es permitir `operacion_id=NULL` para el ingreso manual `REGISTRO_FUNCIONARIO`, conservando el FK de los ingresos web. Manual usa el mismo generador COM auto_increment, con fecha técnica, procedencia de operador y huella separadas; la fecha/canal declarados y contenido quedan en `core_manual`. Su respuesta es una decisión auditable separada. No se inventa operación de transporte ni comprobante ciudadano. `ConsultaComunicaciones` admite ese NULL. Los algoritmos de huella, cuerpos, recibos y migraciones históricas V1–V4 no cambian. `entregaRegistrada` valida firma/cuerpo almacenados antes de mostrar el original web.

Destino `EVALUANDO` admite varios Casos. No se resuelve por cerrar uno. Jefatura puede responder sin Caso si no abrió ninguno, o finalizar evaluación explícitamente si todos sus Casos están cerrados. Destino `RESUELTO` no admite más Casos en este laboratorio. El Requerimiento termina exclusivamente con todos sus Casos cerrados y destinos resueltos. Esto es una regla **provisional y reversible mediante evolución explícita**, pendiente validación del significado de fin de evaluación. No se habilita reapertura automática para compensar esa definición pendiente.

Actuaciones/revisiones/asignaciones son registros append-only desde API; actor/fecha técnicos automáticos. Sin eliminación ni edición destructiva. Historial de detalle muestra últimos 200 eventos; avisos últimos 100. Textos limitados a 2.000 caracteres, motivo de vínculo 1.000, descripción de complejidad 300, canal 100; también rige el máximo HTTP. No se define retención ni se borran antecedentes automáticamente. No hay auditoría criptográfica adicional de gestión: la firma original acredita registro, no decisiones futuras. Las garantías frente a administrador de BD, backups, retención y visibilidad sensible necesitan diseño institucional.

## Aportar en reuniones DDN

- Catálogos y formularios/bitácoras sectoriales, complejidad y causales; contenido mínimo de ingreso manual.
- Competencia por unidad y sede, coordinaciones, subrogancias y matriz de lectura/modificación de antecedentes sensibles.
- Significado definitivo de destino resuelto, posibilidad de nuevos Casos y redistribución después de esa decisión.
- Autoridad, aceptación, momento de cierre de origen y efectos de derivación; criterios y autorización de reapertura.
- Destinatario e incorporación de antecedentes al vincular hijas; varios Casos/responsables posibles y avisos pendientes.
- Relación final Caso/Requerimiento, folios visibles, excepciones de cierre, privacidad, retención y documentos/adjuntos.

La solicitud/aceptación de derivación se habilita según la [iteración acotada](COMPLEJIDAD_Y_DERIVACION_LAB.md); rechazo/cancelación/escalamiento y reapertura siguen deshabilitados (API 409 tras autorización); sin referencia explícita a un Caso, el destinatario del aviso queda pendiente auditable. No se envían notificaciones externas ni se integra PISEE/SRCeI.



