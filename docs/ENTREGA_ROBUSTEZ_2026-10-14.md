# Entrega verificable — robustez e investigación PISEE

**Entrega histórica de robustez.** La iteración siguiente corrige el alcance del comprobante y agrega registro formal con folio: [entrega vigente](ENTREGA_REGISTRO_COMUNICACION_2026-10-14.md). Las mediciones históricas conservan su alcance original; no prueban el nuevo registro.

## Resultado

Se implementaron las bases técnicas de las cinco mejoras, con recepción MySQL transaccional, idempotencia, procedencia firmada por atributo, conciliación durable, controles auditados y comprobante digital descargable. No se implementaron el formulario definitivo, procesos institucionales o envíos de notificaciones. El alojamiento público y la habilitación institucional siguen pendientes.

**Prueba vertical real completada:** CU Sandbox → callback del backend público → token/userinfo → identidad readonly → Comunicación sintética → COMMIT público y comprobante firmado → recuperación HTTPS saliente por SIGER → COMMIT institucional → ACK → pantalla confirmada. Se descargó y verificó la firma con Node.js y se comprobó logout local/proveedor. Evidencia `evidencias/robustez-2026-10-14/sandbox-recepcion-real.json` y captura `sandbox-recepcion-confirmada.png`. No es un ingreso en infraestructura institucional.

**PISEE sí se ejecutó:** nodo Windows 2.2.6 en copia aislada, puertos escuchando, HTTP local funcional y lecturas del servicio DEMO getProcedure/getSignature 200, repetidas con resultado estable. Siete procedimientos y tres firmas. La identidad del paquete es del Ejército, no de la Defensoría. No se enviaron notificaciones ni se verificó SRCeI.

## Verificaciones realizadas

| Verificación | Resultado |
|---|---|
| Build backend público Java 25 / Boot 4.1.1 | JAR compilado y empaquetado con React |
| Build institucional independiente | JAR compilado, sin React/OAuth2 ciudadano |
| Suite público con MySQL nativo | 38 pruebas exitosas; 3 escenarios especiales omitidos por defecto |
| Suite institucional con MySQL nativo | 17 pruebas exitosas |
| React / TypeScript / Vite | 5 pruebas exitosas y build exitoso |
| Escenarios especiales ejecutados expresamente | Benchmark anterior, carga HTTP real y caída/reinicio real de MySQL: exitosos |
| Seguridad HTTP | JSON duplicado 400, contenido cambiado 409, CSRF inválido 403, cuerpo excesivo 413; APIs técnicas sin credencial 401 |
| Concurrencia e integridad | Ingreso duplicado, reserva concurrente, lease vencido, firma/cuerpo/fila sustituida, cuarentena y rollback verificados |
| Recuperación | ACK perdido estable y caída real de MySQL sin pérdida de comprobante confirmado ni aceptación falsa |
| Independencia | Público arrancó sin SIGER; SIGER respondió con público y MySQL apagados; CU/sesión pública respondió con MySQL apagado |
| Secretos | Inspección de archivos fuente candidatos y coincidencias con secretos conocidos: ninguna. `.local`, claves y credenciales excluidas de Git |

La suite ordinaria suma **60 pruebas exitosas** incluyendo React, además de tres pruebas especiales ejecutadas por separado. Los stubs son exclusivos de tests. La prueba CU y las dos lecturas PISEE son evidencia externa real y están identificadas separadamente.

## Métricas finales

1000 ingresos HTTP / 64 clientes: 1000 respuestas 202, cero reintentos, **178,36 ingresos/s**, p95 **889,07 ms**. Ráfaga 256: 176 respuestas 202 y 824 respuestas 429 controladas; todos los ingresos pendientes se completaron al reintentar con las mismas claves. No confundir solicitudes rechazadas por segundo con ingresos recibidos. Datos crudos y metodología en `CARGA_Y_RECUPERACION_2026-10-14.md`.

## Cambios principales

- Ambos `backend/pom.xml`: JDBC, Hikari, Flyway, driver MySQL; H2 únicamente de test.
- Ambos paquetes `robustez`: persistencia, contratos v2, firma/verificación, controles, journal, operaciones externas, límites, JSON estricto, migrador explícito. Migraciones V1/V2 propias por aplicación y motor de test.
- Público: evidencia CU generada por backend; recepción/consulta/comprobante autenticados; React envía versión de aviso, conserva clave de reintento por sesión y ofrece descarga.
- Institucional: inbox durable, consumidor HTTPS saliente con límites y CA configurable, recuperación/backoff; adaptador PISEE de lectura no habilitado automáticamente.
- `contratos/fixtures`: vector interoperable firmado y clave **pública** de prueba; `contratos/recepcion-v2.md` define protocolo y fallos.
- `scripts/Robustez.ps1`, `Recepcion-Local.ps1` y auxiliares: preparación Windows, cuentas limitadas, pruebas/carga/caída, migración y arranque; `Auditar-PISEE.ps1` e `Inspeccionar-PISEE.mjs`: laboratorio reproducible y lista blanca de evidencias.
- Scripts existentes CU y SIGER: opciones JDBC sin cambiar el arranque normal, las URIs o QA/Producción.

No se creó un repositorio paralelo, no se alteraron DNS/Cloudflare/Cerofilas o infraestructura institucional y no se tocó el servidor MySQL remoto. El MySQL portable no instala servicios Windows ni abre firewall.

## Ejecutar

Arranque normal independiente: `FORMULARIO.cmd iniciar` y `SIGER.cmd iniciar`. No se necesitan Vite, MySQL o PISEE para el ingreso CU normal o la estructura institucional básica.

Pruebas reproducibles:

```powershell
.\scripts\Robustez.ps1 pruebas
.\scripts\Robustez.ps1 carga
.\scripts\Robustez.ps1 recuperacion
.\scripts\Robustez.ps1 pisee
```

Demostración durable con datos de prueba:

```powershell
.\scripts\Recepcion-Local.ps1 preparar
.\scripts\Recepcion-Local.ps1 publico
.\scripts\Recepcion-Local.ps1 siger
.\scripts\Recepcion-Local.ps1 habilitar
```

Abrir el mismo dominio HTTPS local registrado y usar exclusivamente el RUN oficial Sandbox. Para apagar: `Recepcion-Local.ps1 detener`, luego `Robustez.ps1 detener-db`. Antes de compilar, detener las aplicaciones para liberar los JAR de Windows.

Responsabilidades, avisos, configuración, seguridad y dependencias productivas: `ROBUSTEZ_Y_OPERACION.md`. Solicitud exacta de información a TI y límites de permisos reales: `AUDITORIA_PISEE_2026-10-14.md`. La certificación CU/productiva, el alojamiento, los contratos SRCeI y la identidad PISEE Defensoría no quedan resueltos por esta entrega.
