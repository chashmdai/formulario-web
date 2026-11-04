# Evidencia del showcase RC1 — 04/11/2026

Este documento respalda los indicadores del [README](../README.md). Separa el corte de pruebas RC1, el estado observado de GitHub Actions y la implementación desplegada. No es un monitor de disponibilidad, un informe de cobertura ni una certificación externa.

## Resultados del corte RC1

Fuente primaria: [`PUBLICACION_RC1_MANIFIESTO_2026-11-04.json`](PUBLICACION_RC1_MANIFIESTO_2026-11-04.json), campos `tests`. Contexto y límites: [checkpoint productivo](CHECKPOINT_PRODUCCION_SIGER_2026-11-04.md#evidencia-y-límites-de-lo-acreditado).

| Familia | Casos reportados | Omisiones | Alcance documentado |
| :--- | ---: | ---: | :--- |
| Backend | 423 | 2 | Pruebas Java/Spring y contratos/persistencia; cero fallos y errores reportados. |
| Frontend | 219 | — | React; lint, TypeScript y Vite aprobados en el empaquetado validado. El manifiesto reutiliza la evidencia del frontend idéntico, sin nueva ejecución en ese corte. |
| Python | 38 | 1 | Pruebas de herramientas y controles operacionales; cero fallos reportados. |
| **Total** | **680** | **3** | **423 + 219 + 38**, sin sumar repeticiones ni jobs adicionales de CI. |

Las omisiones backend/Python son opt-in existentes, incluidas en los totales reportados. El cero corresponde a fallos reportados en este corte, no a una garantía sobre ejecuciones futuras. No se denominan «680 pruebas unitarias»: intervienen distintas categorías. Tampoco se incorporan las verificaciones Windows o los tests repetidos por Docker a esta suma.

## GitHub Actions

Workflow inspeccionado: [`.github/workflows/ci.yml`](../.github/workflows/ci.yml). Se ejecuta ante push, Pull Request o activación manual; usa runners GitHub y fixtures, sin despliegue automático en DDN-UBUNTU.

Última ejecución completada observada al preparar este documento:

- [Ejecución 41881222055](https://github.com/chashmdai/formulario-web/actions/runs/41881222055), creada el **04/11/2026 a las 17:52:04 UTC**.
- Rama `main`, commit `e52d93f7da237d274fbc178b0e2fabab911fbcda`.
- Estado `completed`, conclusión `success`; los tres jobs terminaron correctamente.

| Job | Verificación | Evidencia GitHub |
| :--- | :--- | :--- |
| `verificar` | React, lint/build, Spring/H2 y controles Python aislados. | [Job Linux](https://github.com/chashmdai/formulario-web/actions/runs/41881222055/job/126753894040) |
| `agente-windows` | Construcción Java, PowerShell, NTFS, criptografía, interrupciones y recuperación con fixtures 1.0/1.1. | [Job Windows](https://github.com/chashmdai/formulario-web/actions/runs/41881222055/job/126753893660) |
| `empaquetado-docker` | Validación Compose, adaptación del proxy Caddy y construcción de React/backend dentro de la imagen. | [Job Docker](https://github.com/chashmdai/formulario-web/actions/runs/41881222055/job/126753893446) |

La consulta de artefactos de esta ejecución devolvió una lista vacía. El workflow construye artefactos, pero no publica reportes descargables que permitan reemplazar automáticamente los recuentos RC1. Por eso el README mantiene los 680 casos del manifiesto, separados de CI.

El badge oficial apunta al workflow filtrado por `main` y muestra su resultado agregado vigente; no se fabrican badges verdes por job. Los enlaces anteriores son evidencia fechada, no estado en tiempo real. El siguiente push puede mostrar una ejecución en curso o un resultado distinto.

El historial permanece accesible: [ejecución anterior satisfactoria del commit productivo 7603448](https://github.com/chashmdai/formulario-web/actions/runs/41848604520) y [ejecución fallida de 7189a95, del 03/11](https://github.com/chashmdai/formulario-web/actions/runs/41647436967). No se afirma que todas las ejecuciones históricas hayan sido satisfactorias.

## Implementación y límites

Fuente: [manifiesto RC1](PUBLICACION_RC1_MANIFIESTO_2026-11-04.json), verificación de publicación del **04/11/2026 a las 13:52:37 UTC**, y [checkpoint operativo](CHECKPOINT_PRODUCCION_SIGER_2026-11-04.md).

| Indicador | Evidencia y límite |
| :--- | :--- |
| RC1 / recepción | Publicación y recepción habilitadas bajo autorización institucional; contraseña temporal hasta el 15/12/2026, 00:00 America/Santiago. |
| Seis servicios saludables | App, MySQL, Caddy, ClamAV, Inspector y PISEE con `healthy=true` en el inventario documentado; no implica disponibilidad permanente ni un SLA. |
| PISEE productivo | Nodo y consulta Java real acreditados. No se repitieron consultas personales para preparar el README. |
| ClaveÚnica | Integración oficial implementada y redirección real observada. Autenticación personal completa aún no acreditada. |
| Custodia documental | Persistencia, originales, comprobante firmado y respaldo cifrado. Custodia institucional definitiva corresponde a SIGER. |
| PULL/ACK 1.0–1.1 | Productor preparado y Bearer comprobado; recuperación cerrada hasta incorporar clave pública ACK e identificador de Windows. E2E físico de esta instalación pendiente. |
| Ed25519 / SHA-256 | Firma e integridad implementadas según [contrato](../contratos/formulario-completo-multimedia-v1.1.md); no son por sí mismas una certificación o aprobación externa. |
| Recuperación | Respaldo local cifrado y procedimientos de reversión/restauración documentados; destino de copia fuera del anfitrión aún no suministrado. |

No se publican métricas de rendimiento, carga, cobertura o disponibilidad que no estén acreditadas. No se afirma certificación ISO, WCAG, Gobierno Digital ni cumplimiento legal formal mediante estos indicadores.
