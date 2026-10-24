# Identificación persistente por recorrido — continuación 23-10-2026

Unidad backend cerrada con pruebas **SIMULADO_VALIDADO** en H2 y MySQL 8.4.11. Reutiliza el cliente `ClientePisee` real ya comprobado, pero esta batería utiliza dobles controlados: **ninguna prueba de esta unidad consulta a Gobierno Digital o SRCeI**. La conexión de sus pantallas React y la recepción del formulario completo continúan pendientes; no se confunde este recorrido de identificación con una Comunicación ya recibida.

## Modelo y controles

- `IdentificacionCiudadana`: separa identidad CU autenticada, RUN declarado con módulo 11, atributos registrales y declaración ciudadana. Guarda fuente, nivel, instante y evidencia por atributo. La procedencia CU referencia la evidencia de autenticación de la sesión, no una referencia inventada por el frontend. Conserva discrepancias con SRCeI y códigos registrales desconocidos.
- Los nombres agregados de SRCeI no se dividen por espacios. Si existen, no se solicita al ciudadano inventar sus componentes. Las fechas truncadas no sustituyen una fecha completa y los códigos desconocidos permiten declaración conservando el original internamente.
- `RecorridosJdbc`: propiedad mediante capacidad aleatoria interna de sesión, SQL parametrizado, transacciones cortas y cuotas persistidas. No hay almacenamiento alternativo en memoria. Error de base no se interpreta como consulta completa ni recepción exitosa.
- `FormularioController`: HTTPS obligatorio, CSRF activo en mutaciones, respuestas `no-store`, propiedad de sesión en cada operación. La identidad CU procede únicamente del principal y su evidencia autenticada; no se acepta sustituir su RUN desde el cuerpo.
- `FormularioConfiguration`: requiere `jdbc-reception` y habilitación explícita de laboratorio Sandbox. Por defecto no hay servicio ni recorridos: capacidades devuelve `disponible=false`, y mutaciones con CSRF válido devuelven 503. No habilita QA/producción.

La vista pública contiene `personaId`, estado genérico `CONSULTANDO`/`REVISADO`, indicador de autenticación real, RUN informado, campos faltantes/obligatorios y valores previamente declarados por el mismo ciudadano. **No contiene** valores SRCeI, el RUN consultado, códigos internos del proveedor, resultado completo/parcial, respuestas XML ni el historial privado. Para CU, la presentación de su propia identidad sigue correspondiendo al endpoint de sesión ya existente.

Una consulta en curso no habilita campos manuales. La reserva expira en 30 segundos y una recuperación posterior ofrece fallback TIMEOUT sin repetir el proveedor; una respuesta tardía no puede sobrescribir ese fallback. Repetir una identificación con el mismo RUN recupera la consulta persistida, sin volver a consultar.

El cambio de RUN exige reinicio explícito: se conserva la versión anterior en historial privado, se retiran sus atributos del recorrido activo y no se reinician las cuotas. El lease impide que una respuesta anterior se aplique a un RUN nuevo.

NNA sin RUN no consulta SRCeI y puede declarar primer nombre/apellido. La región obligatoria y el resto de campos se validarán en el agregado del formulario; esta unidad no afirma que baste completar identificación para enviar. Adulto declarado exige RUN/DV válidos; SRCeI no lo autentica.

## Antiabuso y autorizaciones

En esta implementación de laboratorio: 12 inicios por hora y 12 consultas autorizadas por hora por origen, 12 identificadores por recorrido y 30 personas por recorrido. Son límites técnicos de protección; no son una política productiva certificada. El origen se obtiene de la conexión validada por la configuración del proxy, no de un encabezado recibido libremente. El identificador de cuota usa HMAC-SHA256 con clave privada persistente, sin guardar dirección IP en claro ni RUN en logs de cuota. Los registros de cuota vencidos se depuran; el recorrido expira a las cuatro horas y no alarga la sesión HTTP de 20 minutos existente.

Solo se consulta si el RUN figura en la lista privada DEV autorizada. Sin autorización se ofrece declaración sin contactar al proveedor ni divulgar la lista. Los cambios visuales de campos pueden revelar disponibilidad de antecedentes: por eso no constituyen una defensa suficiente por sí solos; se mantienen autorización acotada, vínculo a sesión, cuotas y ausencia de valores. No se ha habilitado este patrón para consultas productivas anónimas.

## API nueva; V3 intacto

Todas las rutas pertenecen a `/api/formulario` y no sustituyen las APIs históricas:

| Método/ruta | Propósito |
|---|---|
| GET `/capacidades` | Disponibilidad real y versiones de aviso |
| POST `/recorridos` | Crear `ADULTO_CU`, `ADULTO_DECLARADO` o `NNA`, con versión y elección de canal |
| GET `/actual` | Recuperar el recorrido actual de esta sesión |
| DELETE `/recorridos/{id}` | Cancelar el borrador propio |
| POST `/recorridos/{id}/personas/{persona}/identificacion` | Identificar por CU de sesión, RUN declarado o sin RUN permitido |
| GET `/recorridos/{id}/personas/{persona}` | Recuperar solo la proyección pública |
| POST `/recorridos/{id}/personas/{persona}/declaracion` | Guardar exclusivamente campos manuales permitidos |
| DELETE `/recorridos/{id}/personas/{persona}/identificacion` | Reiniciar al cambiar RUN, preservando historial y cuotas |

Roles: presentador, NNA afectado, adulto relacionado y persona señalada. La sesión NNA no admite agregar personas adultas como si fueran su presentador. El servidor valida nombres de persona y roles; no usa un identificador enviado por el cliente como prueba de propiedad.

Migraciones nuevas: V5 crea `citizen_journey`, `citizen_person`, `citizen_abuse_bucket`; V6 crea `citizen_identity_history`. No modifican tablas/sobres históricos ni reescriben V1–V4. V5 ya fue aplicada en el laboratorio antes de introducir V6: se conservó su checksum. Los fixtures en ambos repositorios siguen idénticos y la regresión incluye firmas, custodia, idempotencia, ACK y comprobantes existentes.

El contrato HTTP V3 sigue exactamente igual. Actualmente sus entregas adultas requieren procedencia `CLAVE_UNICA_USERINFO/IDENTIDAD_AUTENTICADA`; la variante declarada existente corresponde a NNA. **No** se enviará un adulto declarado como adulto CU o como NNA para superar esa restricción. El formulario completo también admite relatos de 5000 caracteres y campos/adjuntos que el sobre histórico de 2000 caracteres no representa. La futura custodia completa debe conservarlos íntegros y documentar el acompañamiento/versionado compatible requerido al consumidor; esta unidad no altera esa frontera ni modifica `sistema-de-requerimientos`.

## Configuración privada para su conexión posterior

Además de JDBC real y migraciones aplicadas, se requieren:

```properties
formulario.habilitado=true
formulario.aviso-adulto=VERSION_ADULTA_LABORATORIO
formulario.aviso-nna=VERSION_NNA_LABORATORIO
formulario.abuso-key-path=/ruta/privada/abuso-key
PISEE_ALLOWED_RUNS_PATH=/ruta/privada/run-autorizado.json
```

Clave antiabuso aleatoria de 32–128 bytes fuera de Git. La lista DEV acepta un objeto `numero`/`dv` o una lista de hasta diez objetos, bajo custodia privada; no se incluyen aquí sus valores. No basta habilitar esta lista para activar el cliente: siguen vigentes las condiciones `PISEE_SRCEI_ENABLED`, autorización DEV, autenticación local y ambiente Sandbox de la [guía PISEE](PISEE_LINUX.md).

La aplicación HTTPS ya verificada no se reconfiguró durante esta unidad ni se preparó de nuevo su infraestructura. La habilitación conjunta se realizará al conectar las pantallas y la recepción real; no activar flags para ocultar que aún faltan esas partes.

## Pruebas y reproducción

Baseline de continuación: 17 pruebas frontend y 81 backend correctas/4 omitidas. Tras esta unidad: **118 descubiertas, 114 correctas y 4 opcionales omitidas**, tanto H2 como MySQL 8.4.11. Son 33 pruebas nuevas entre `RecorridosJdbcTest`, `FormularioHttpTest` y `FormularioDeshabilitadoTest`.

Cobertura: completo/parcial/vacío/negativa/red/timeout/autorización/indisponibilidad/XML/quotas; no exposición registral; nombres agregados; declaración/tampering; CU y evidencia de sesión; cambio de RUN/historial; NNA sin RUN; ausencia de autorización DEV; propiedad/CSRF/HTTPS; JSON duplicado/tamaño; concurrencia sin lock SQL durante proveedor; respuestas tardías; expiración y reapertura de conexiones conservando los resultados.

```bash
cd /home/nexbyteproveedor/proyectos/formulario-web/formulario-publico/backend
mvn -B -ntp test
cd ../..
bash scripts/linux/probar-mysql.sh /home/nexbyteproveedor/.local/share/siger/mysql-lab --autorizar-laboratorio
diff -qr contratos/fixtures ../sistema-de-requerimientos/contratos/fixtures
```

La batería MySQL opera solo sobre el esquema sintético autorizado `robust_public_test`; no usarla sobre datos con valor. Logs de esta ejecución excluidos de Git: `.local/evidencias/cierre-identificacion-h2-final.log` y `cierre-identificacion-mysql-final.log`. La reapertura probada corresponde al almacén JDBC; no afirma reanudar una sesión HTTP después de reiniciar Tomcat.
