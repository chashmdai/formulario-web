# Comunicación: registro institucional mínimo

Esta iteración evoluciona la entrega de robustez del 14-10-2026. La distinción Comunicación/Requerimiento es nomenclatura del levantamiento y no elimina obligaciones contractuales. No define todavía estados, roles, asignaciones, bandejas ni la relación Caso/Requerimiento.

```mermaid
sequenceDiagram
    participant C as Ciudadano
    participant P as Formulario Público
    participant S as SIGER Institucional
    C->>P: Sesión CU y envío idempotente
    P->>P: COMMIT de custodia técnica
    P-->>C: UUID técnico, pendiente de confirmación
    S->>P: Recuperar por HTTPS saliente
    P-->>S: Entrega íntegra y reserva
    S->>S: COMMIT Comunicación + folio + comprobante + trazabilidad
    S->>P: Confirmación institucional firmada
    P->>P: Verificar y guardar ACK
    C->>P: Consultar / recuperar comprobante
    P-->>C: Folio, ID y fecha de SIGER; comprobante definitivo
```

## Modelo persistido

`communication_registry` contiene UUID de Comunicación propio, número de folio, formato conservado, fecha UTC de registro, canal de origen, origen técnico, ID de operación pública y huellas de contenido/procedencia. Restricción única por origen/operación y FK a `institutional_inbox`, donde reside el cuerpo íntegro y su comprobante. No se duplica el contenido ciudadano ni se crea un Requerimiento de forma automática.

El folio se genera con AUTO_INCREMENT de MySQL dentro de la transacción de registro; no se usa MAX+1 ni un contador de React. Configuración `SIGER_FOLIO_PREFIX=COM`, `SIGER_FOLIO_WIDTH=6`. Prefijo/ancho quedan persistidos por registro y los cambios afectan solo a nuevos ingresos. La identidad estable es el UUID de Comunicación; el folio es su referencia visible institucional. Se admiten huecos por rollback y concurrencia, conforme al comportamiento de [InnoDB](https://dev.mysql.com/doc/refman/8.4/en/innodb-auto-increment-handling.html). Si Hito 1 exige continuidad legal o numeración anual, debe definirse expresamente: esta iteración no inventa esas reglas.

El comprobante se calcula dentro de la transacción y se almacena junto al registro. No se publica hasta que esa transacción confirma. Su emisor es SIGER, con una privada diferente de la del público. El público solo tiene la clave pública institucional y no puede emitir un comprobante institucional con su firmante de custodia. Se comprueba esa separación al cargar configuración.

## Garantías conservadas

Idempotencia y huellas originales; procedencia por atributo; límites/validación server-side; controles CAS auditados y contingencia; cuarentena; reserva con vencimiento; backoff; recuperación de ACK y comprobante estable. Se agregó conciliación de ACK aceptado por el público cuya respuesta se perdió. El journal distingue `COMUNICACION_REGISTRADA` (ID institucional) y confirmación pública (operación técnica).

Consulta técnica paginada `/operacion/v1/comunicaciones?despues=0&limite=100`, protegida por el control operacional existente. Devuelve metadatos sin RUN/mensaje. No es una bandeja funcionaria ni una definición de permisos institucionales. Métricas sin identidad en `/operacion/v1/recuperacion/metricas`.

El consumidor tiene paralelismo acotado configurable `SIGER_REGISTRATION_PARALLELISM` (1–8, predeterminado 4), lotes de 20, presupuesto de drenaje de dos segundos y máximo diez lotes por tick; no se introduce broker. No hay garantía de orden FIFO ni correlación entre orden de llegada y número bajo concurrencia. Cada Comunicación guarda su propia fecha, canal y trazabilidad.

## Proyección CU / SRCeI

GET autenticado `/api/identidad/atributos` proyecta `run`, `nombres`, `apellidos` como listas de valores por fuente, con nivel, fecha y referencia de evidencia. En ejecución normal únicamente hay `CLAVE_UNICA_USERINFO / IDENTIDAD_AUTENTICADA`; `SRCEI` se declara `NO_HABILITADO`.

`ProyeccionAtributos.FuenteAutorizada` define el puerto backend futuro, sin un adaptador activo. La incorporación requiere una lista de atributos autorizados del servidor, conserva valores por fuente y no reemplaza la identidad CU ni resuelve discrepancias automáticamente. Los niveles futuros del contrato son decisiones técnicas que deben validarse con los contratos y el levantamiento. Las pruebas de proyección usan datos sintéticos explícitos; el ciudadano no ve una verificación SRCeI simulada.

No se accede al nodo Notificador ni se reutiliza su identidad de otro organismo. La ubicación de SRCeI, conectividad y permisos, campos autorizados, reglas de discrepancias y su incorporación futura al sobre versionado continúan pendientes.

## Compilar, migrar y demostrar en Windows

```powershell
# Antes de reconstruir: liberar los JAR de Windows.
.\scripts\Recepcion-Local.ps1 detener
.\scripts\Build-Todo.ps1
.\scripts\Recepcion-Local.ps1 preparar
.\scripts\Recepcion-Local.ps1 publico
.\scripts\Recepcion-Local.ps1 siger
.\scripts\Recepcion-Local.ps1 habilitar
```

El mismo dominio HTTPS local y las URIs registradas se conservan. Usar exclusivamente el RUN oficial Sandbox `44.444.444-4` / `testing` en Gobierno Digital. Con SIGER apagado se muestra recepción pendiente, sin enlace a comprobante definitivo. Después de recuperarse, consultar para ver folio, ID, fecha y descarga firmada.

`preparar` genera una clave institucional local independiente sin regenerar claves existentes y aplica V3 con cuentas de migración. Privadas y secretos permanecen en `.local`, con ACL/DPAPI, nunca en React o Git. Firmante institucional: `SIGER_RECEIPT_KEY_ID`, `SIGER_RECEIPT_PRIVATE_KEY_PATH`; confianza histórica en `SIGER_RECEIPT_TRUSTED_KEYS_PATH`. El público necesita solo esta última. La confianza de sellos públicos sigue siendo `RECEIPT_TRUSTED_KEYS_PATH`. Conservar ambos conjuntos históricos al rotar y respaldar junto con bases/configuración.

Para probar el acumulado, detener primero la demo. Se reutilizan los dos esquemas *_test y el MySQL portable existentes:

```powershell
.\scripts\Robustez.ps1 pruebas
.\scripts\Probar-Comunicaciones-Acumuladas.ps1 -Cantidad 5000 -Paralelismo 4
```

El script ejecuta Tomcat del público con configuración de test e identidad sintética exclusivamente en código de pruebas. Genera primero el volumen con SIGER detenido; inicia después el JAR institucional real en otro JVM, verifica folios/registro/firmas y conciliación y detiene sus propios procesos. Solo usa HTTP loopback autorizado en test; la demo interactiva utiliza HTTPS verificado. No contacta CU/PISEE/SRCeI ni modifica datos de la demo o infraestructura institucional. Las pruebas no usan un autenticador ficticio como modo normal de las aplicaciones.

## Pendientes Hito 1

Formato visible definitivo y política de numeración/huecos; canales adicionales y requisitos de ingreso; campos/formulario/privacidad y retención; distribución, clasificación, gestión, roles y transiciones; relación Caso/Requerimiento; redacción/presentación oficial del comprobante y eventual PDF/FEA/sellado de tiempo; contratos SRCeI y tratamiento de discrepancias; alojamiento, autenticación de máquinas/operadores, respaldo y operación productiva. Nada de ello queda artificialmente resuelto por el registro mínimo.
