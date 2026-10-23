# Medición local de carga y recuperación

Windows 11, Java 25.0.3, 8 procesadores lógicos visibles; MySQL **8.4.11** portable nativo, loopback:13306, InnoDB con `innodb_flush_log_at_trx_commit=1`, pool 12. Sin Docker/WSL. Datos sintéticos, una instancia y una ejecución por escenario. No se midieron SLA, red pública, sesiones distribuidas, antivirus ajeno, saturación de disco prolongada ni topología productiva. No hubo carga a ClaveÚnica, PISEE u otros servicios gubernamentales.

## Mediciones

| Escenario | Solicitudes / concurrencia | Resultado | p95 | Rendimiento observado |
|---|---:|---|---:|---:|
| Adaptador anterior de archivos | 1000 / 16 | Persistidas | 251,15 ms | 190,54 ingresos/s |
| JDBC + procedencia + firma | 1000 / 16 | 1000 persistidas | 140,84 ms | 256,24 ingresos/s |
| HTTP real, admisión inicial 48 | 1000 / 64 | 525 HTTP 202, 475 HTTP 429; todos recibidos tras repetir pendientes | 788,71 ms | 246,13 solicitudes/s, **incluye rechazos** |
| HTTP real, admisión final 64 | 1000 / 64 | **1000 HTTP 202, cero reintentos** | **889,07 ms** | **178,36 ingresos/s** |
| Misma clave HTTP repetida | 200 / 64 | 200 HTTP 202, **un registro** | 271,70 ms | 315,68 solicitudes/s |
| Ráfaga HTTP final | 1000 / 256 | 176 HTTP 202, 824 HTTP 429; las 824 se recibieron al reintentar con la misma clave | 885,62 ms | 781,10 solicitudes/s, **incluye rechazos** |

Los percentiles HTTP incluyen todas las respuestas de la primera pasada; las cifras de ráfaga no equivalen a ingresos persistidos por segundo. El tiempo final de reintentos no está incluido en ese rendimiento. Los reintentos se ejecutaron acotadamente con concurrencia 16 y se verificó en la base la presencia de todos los IDs. No se creó un registro por cada reintento.

Los escenarios de persistencia tienen el mismo número de entradas/hilos y distinto trabajo: el JDBC añade firma/procedencia/journal. Son observaciones de esta implementación, no un benchmark generalizable; calentamiento/JIT/caché y carga del equipo influyen. No comparar directamente persistencia con HTTP. Resultados crudos: `evidencias/robustez-2026-10-14/*.json`.

## Cuellos de botella y cambios aplicados

El adaptador anterior serializaba los ingresos y usaba varios archivos/sincronizaciones por recepción; su lectura de pendientes requería examinar archivos. Se incorporó MySQL con unicidad transaccional e índices de estado, vencimiento y propietario. Se retuvo la demo anterior, sin presentarla como persistencia productiva.

La primera ráfaga 256 detectó `ConnectException` con cola Tomcat 64. Se elevó esa cola acotadamente a 256, sin aumentar los 80 hilos/512 conexiones. La repetición final no registró errores de transporte: devolvió 429 controlados. Con admisión 48 se rechazaba prematuramente al probar 64 clientes; se midió y ajustó a 64, manteniendo pool 12 y espera acotada. Por encima de ese límite se aplica contrapresión explícita en lugar de acumular trabajo ilimitado. Los 64 clientes sostenidos son un ensayo local útil; la institución debe proporcionar volumen esperado para pruebas prolongadas y dimensionamiento.

La prueba HTTP usa Tomcat real y Spring Security: sesión y CSRF reales con identidad sintética instalada **solo por configuración de test**. La ruta de test no se empaqueta en el JAR. Se comprobaron 409 al alterar la misma clave, 400 ante claves JSON duplicadas, 413 por tamaño y 403 por CSRF inválido. No utiliza ClaveÚnica simulada como modo de ejecución de las aplicaciones.

## Fallos realmente ejecutados

- 72 ingresos simultáneos con una clave en público y 72 en SIGER: una fila y un evento de recepción por almacén; recibos estables.
- Fallo SQL forzado antes de COMMIT: ninguna recepción o comprobante devuelto; transacción completa revertida y reintento posterior exitoso.
- Reserva vencida y re-reclamada: token antiguo rechazado; ACK correcto repetible, incluso con ingreso suspendido.
- Cuerpo alterado: cuarentena; una entrega íntegra posterior continúa. Reposición no permite ignorar firma.
- ACK perdido tras persistencia institucional: queda pendiente, se recupera y conserva una única incorporación y recibo.
- Respuesta de origen excesiva, timeout y redirect HTTP: cancelación/rechazo acotados, sin seguir credenciales a otro origen; pruebas con servidor local.
- **Proceso MySQL terminado abruptamente** después de confirmar una recepción. Durante la caída, otro ingreso falló sin confirmarse. El motor reinició y ejecutó recuperación InnoDB/XA; comprobante anterior intacto y reintento idempotente. Evidencia `recuperacion-mysql.json`. Esta prueba no sustituye restaurar un respaldo en otro equipo.
- Notificación sintética con timeout ambiguo: estado durable `DESCONOCIDA`; reenvío ciego bloqueado y conciliación por referencia confirmada. Ningún envío externo realizado.

## Reproducción

```powershell
.\scripts\Robustez.ps1 pruebas
.\scripts\Robustez.ps1 carga
.\scripts\Robustez.ps1 recuperacion
```

Los esquemas de pruebas tienen nombres y host/puerto exigidos por los tests. No apuntar estas pruebas a datos institucionales. La simulación de caída verifica PID, fecha de inicio y ruta del MySQL privado antes de detenerlo. El cierre ordinario usa mysqladmin y conserva datos. Los scripts guardan secretos en DPAPI/ACL, nunca en argumentos o Markdown.
