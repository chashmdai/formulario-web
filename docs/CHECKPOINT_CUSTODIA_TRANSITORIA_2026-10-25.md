# Checkpoint: custodia transitoria y VM de 25 GB

> **Checkpoint histórico V9.** Fue ampliado por el [receptor transitorio integral V10](ARQUITECTURA_RECEPTOR_TRANSITORIO.md), el [checkpoint técnico](CHECKPOINT_RECEPTOR_TRANSITORIO_2026-10-25.md) y la [consolidación publicada](CONSOLIDACION_MAIN_2026-10-25.md). Sus condiciones offline, plazos y ausencia de push describen aquella unidad, no el estado del código actual.

Fecha: 25/10/2026. Repositorio: `formulario-web`. Rama local: `ciclo-vida-custodia-2026-10-25`.

Motor implementado y probado: **`2d5b2f39fbd3837ee739e85d3c7d4fa7d62290c1`**. Conserva `4b148bc` y el baseline publicado `a84d2e7`. El commit documental posterior contiene este checkpoint; consultar `git log -2` para identificarlo. **Sin push ni despliegue.**

Referencia principal: [ciclo de vida, operación, garantías y dimensionamiento](CICLO_VIDA_CUSTODIA_TRANSITORIA_2026-10-25.md).

## Cerrado en esta unidad

- V9: marca de bytes retirados y bitácoras operativas, fuera del documento firmado.
- Verificación de firma pública, ACK institucional, referencias, originales en SIGER y restauración independiente.
- Primera observación conservadora y plazo configurable (72 h por defecto), hold y habilitación explícita cerrada.
- Intención durable antes de retirar; reintentos después de interrupción; liberación transaccional y única de reservas.
- Borradores vencidos con margen de 48 h, cargas no incluidas, análisis interrumpido offline, derivados y BLOB.
- No se interpreta una fila de Comunicación perdida ni una marca sin intención como autorización de borrado.
- Compactación de reservas después de comprobar bytes durables y retirar bloques redundantes.
- Respaldo/restauración V8/V9; recuperación a destino nuevo desde custodia independiente.
- CLI empaquetado y modelo de capacidad reproducible; no scheduler ni HTTP destructivo.

## Pruebas finales

| Verificación | Resultado |
|---|---|
| Backend H2 | 216 descubiertas, 214 correctas, 2 omitidas |
| Backend MySQL 8.4, solo robust_public_test | 216 descubiertas, 214 correctas, 2 omitidas |
| Motor de ciclo de vida | 17 pruebas específicas dentro de cada batería |
| Frontend | 160 correctas; lint/build correctos |
| Python respaldo/limpieza | 8 correctas |
| Python inventario | 7 correctas |
| JAR existente SIGER, H2 nuevo | 1.0 y 1.1 correctos: COMMIT/ACK, retirada pública, reinicio institucional y recuperación propia |
| Respaldo/restore MySQL V9 | 2 Comunicaciones sintéticas retiradas, sin bytes públicos, CHECKSUM coincidentes |
| CLI con eliminación cerrada | --aplicar rechazado, código 1 antes de conectar |

Omisiones: `RecuperacionProcesoTest` y `MultimediaWorkersRealTest`. No se hicieron consultas gubernamentales ni pruebas de carga. El ensayo con clases SIGER no sustituye la prueba HTTP del despliegue institucional ni una recuperación en medios físicos independientes.

Evidencia privada bajo `.local/evidencias/ciclo-*`; compilación en `.local/ciclo-vida-target`. Respaldo y restauración sintéticos preservados fuera de Git; el esquema fuente de prueba se limpió. No almacenar secretos ni copias ciudadanas en el repositorio.

## Datos existentes preservados

- Integrado: 13 custodias, 13 adjuntos, Flyway V7, multimedia cerrada.
- Multimedia: Flyway V8; mismo almacén y contador conservado, multimedia de laboratorio como antes.
- Ambos: `ciclo-vida.eliminacion.habilitada` ausente/false. Sin reinicio ni migración de sus bases.
- SIGER: árbol Git limpio y JAR sin cambios; no se utilizó su base real.

## Capacidad y siguiente paso

Con entrega/restauración en un día, retención 72 h y mantenimiento diario, el modelo 300×20 MiB/mes se estabiliza alrededor de 0,957 GiB de binarios y 1,133 GiB de reserva. El presupuesto de 25 GB es razonable para esa carga con cuota 6 GiB, mínimo libre 4 GiB y resto del sistema acotado. El extremo de 300×250 MiB/mes no cabe. Metadatos y auditoría crecen lentamente; no se promete constancia infinita de toda la base.

**No activar aún sobre información real.** Próximo paso técnico exacto: confirmar presupuesto en la VM, montar archivo SIGER y restauración confiable en medios de solo lectura independientes, comprobar recuperación real, planificar ventana offline/drain, respaldar y migrar V9, simular, revisar discrepancias y solo entonces decidir una primera retirada acotada con doble habilitación. No falta una propuesta teórica de motor; faltan las pruebas de esos montajes y despliegue concretos. No reemplazarlas por una autorización genérica ni por el mero ACK.