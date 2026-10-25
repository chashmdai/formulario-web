# Consolidación y publicación en main · 25/10/2026

## Alcance de esta entrega

Publicación íntegra del receptor transitorio integral, sin funciones nuevas ni cambios de infraestructura. Main conserva el baseline anterior y todos los commits de capacidad/V9/V10. El README es ahora la entrada para un desarrollador nuevo; la [arquitectura vigente](ARQUITECTURA_RECEPTOR_TRANSITORIO.md) y el [checkpoint técnico](CHECKPOINT_RECEPTOR_TRANSITORIO_2026-10-25.md) conservan el detalle y la evidencia.

La publicación de código **no implica despliegue, migración de bases, instalación del agente, activación técnica ni autorización de producción**. Las decisiones de habilitación del responsable del proyecto se ejecutarán después de comprobar las condiciones de infraestructura. La responsabilidad de SIGER como custodia definitiva ya está resuelta en software y no queda pendiente de otra definición genérica.

## Historial incorporado

Base de `main`/`origin/main` verificada mediante fetch: **`a84d2e718a824e01cc8bd8cc78fba5e918d483d3`**. La rama `receptor-transitorio-integral-2026-10-25` es descendiente; no se observó divergencia remota al preparar esta consolidación.

| Commit | Contenido |
|---|---|
| `4b148bc` | Auditoría agregada, inventario de capacidad y retención |
| `2d5b2f3` | V9: retirada recuperable de bytes, conservación y reservas |
| `4a621a2` | Pruebas/evidencia de custodia y dimensionamiento transitorio |
| `a5572455e804291155b980e4d0510bddfe796c39` | V10: minimización de MySQL, garantías, agente y mantenimiento online |
| `c971c42ace2e0210445b70c695d9d8f962dba034` | Arquitectura, checkpoint, resultados y README anteriores |
| Commit de este documento | README integral, coherencia de manuales e identificación del historial |

Este archivo pertenece al commit documental de publicación; no incorpora su propio SHA como autorreferencia. El SHA exacto se obtiene de `git log -1 -- README.md docs/CONSOLIDACION_MAIN_2026-10-25.md` y se contrasta con la rama remota. La respuesta de entrega y la evidencia privada guardan el resultado del push y del contraste.

Integración por **fast-forward**, conservando historial y ramas. Push normal a `origin/main`, sin force. Si aparece trabajo remoto concurrente o protección incompatible, no se sobrescribe: el estado se conserva y el impedimento se informa.

## Código y documentación incluidos

- Flyway V1–V10 para H2/MySQL, sin modificar versiones aplicadas. V9/V10 son nuevas respecto del baseline remoto; su presencia en Git no las aplica a ninguna base.
- Paquete `custodia`: garantías firmadas entrantes, recibos mínimos, minimización, piso de capacidad y programador online; agente privado de respaldo/restauración.
- Motor de retirada, guardias concurrentes, liberación de BLOB/archivos/reservas y recuperación idempotente.
- Pruebas backend y scripts sintéticos de custodia SIGER, backup/restore, capacidad e inventario.
- Perfiles candidatos de 25 GB y plantillas del agente/systemd; no instalados ni habilitados.
- README renovado y referencias vigentes en arquitectura, checkpoint y manual operativo. El cierre del 24/10, el checkpoint V9, la preparación Linux y el lanzador Windows se identifican como históricos.
- Contratos 1.0/1.1, fixtures, firmas, UUID y canonicalización conservados. No se modificó React, el backend ni los tests en este commit documental.

## Validación de publicación y regresiones previas

Esta unidad comprueba Git, presencia de archivos, referencias relativas, ejemplos contra scripts/clases reales, coherencia documental, higiene de lo incorporado y `git diff --check`. No inicia servicios, ejecuta migraciones ni consulta proveedores. Tampoco repite las regresiones ya aprobadas.

Se conservan los resultados anteriores, con su alcance original:

| Evidencia anterior | Resultado |
|---|---|
| Frontend | 160 correctas; lint/build correctos |
| Backend H2 y MySQL aislado | 245 descubiertas por motor; 243 correctas y dos omisiones opcionales; sin fallos/errores |
| Python backup/restore e inventario | Nueve + siete correctas |
| Integración Spring | Temporizador/motor reales; HTTP MockMvc con TLS simulado |
| Clases reales del artefacto SIGER | 1.0/1.1, COMMIT/ACK, respaldo/restauración, retirada pública y recuperación institucional independiente |
| Restore público MySQL V10 | Dos recibos mínimos restaurados y firmas verificadas |

Las omisiones, pruebas sintéticas, uso de filesystem temporal en QA y límites físicos no calificados se detallan en el [checkpoint técnico](CHECKPOINT_RECEPTOR_TRANSITORIO_2026-10-25.md). No se presentan como un despliegue, handshake TLS real, carga ni desastre físico. No se atribuye éxito a CI remoto sin inspeccionar ese run.

La revisión de publicación excluye secretos, claves/certificados privados, RUN privados autorizados SRCeI, dumps, respaldos y evidencias privadas. Los fixtures sintéticos y ejemplos de contratos preexistentes se preservan; no se reescriben documentos firmados ni historial Git. Las evidencias de QA permanecen fuera de Git.

## Estado operativo conservado

| Componente | Estado de laboratorio informado y no alterado por esta publicación |
|---|---|
| Integrado | `robust_public_local`, V7; 13 custodias/13 adjuntos; multimedia apagada |
| Multimedia | `robust_public_media_lab`, V8; multimedia solo en ese laboratorio |
| V10 | Solo aplicada previamente en pruebas/restore sintéticos, no en los dos laboratorios anteriores |
| Programador online | `ciclo-vida.online-habilitado=false` o ausente |
| Retirada | `ciclo-vida.eliminacion.habilitada=false` o ausente |
| SIGER | Ninguna modificación de repositorio, JAR, base, configuración o despliegue en esta unidad |
| Servicios | No reiniciados ni reconfigurados; sin volúmenes recreados ni datos eliminados |

Las banderas de recepción, ClaveÚnica y SRCeI existentes no se reinterpretan como habilitación productiva. Las guardas de recorridos Sandbox/perfiles de laboratorio siguen en el código. El perfil de 6 GiB/4 GiB, overlay MySQL y timers son propuestas de configuración concretas ya verificadas, **no configuración aplicada**.

## Pendientes operativos de destino

1. Medir/calificar presupuesto real de 25 GB, cuotas, binlogs, alertas y respaldo de pendientes públicos fuera del SSD. El modelo exige tiempos de transferencia/verificación presupuestados y no disponibilidad ilimitada de almacenamiento.
2. Instalar y calificar el agente privado Linux/POSIX, cuenta SELECT local con TLS, archivo SIGER, respaldo durable independiente, scratch y clave propia; confiar solo su pública en el Formulario.
3. Comprobar respaldo/restore con el ambiente y claves reales autorizados; aplicar V10 mediante cuenta migradora y procedimiento, conservando compatibilidad y claves históricas.
4. Observar primero con programador habilitado y retirada cerrada; verificar garantías, reloj/TLS, holds, estado, cuotas y recuperación. Activar retirada después de cumplir controles, por decisión operativa autorizada.
5. Abordar habilitación y hardening productivo de ambos aplicativos en su unidad posterior. La revisión institucional de redacciones/opciones/campos sigue siendo normal y no congela el baseline.

Estos pasos son despliegue, configuración y calificación del ambiente; la recepción transitoria, minimización y mantenimiento ya están implementados. No se abre desarrollo adicional por esta publicación.

## Verificación reproducible de la publicación

Después del push, comparar contra el remoto real y comprobar ancestros:

```bash
git fetch origin
git status --short --branch
git rev-parse HEAD main origin/main
git ls-remote origin refs/heads/main
git merge-base --is-ancestor a557245 origin/main
git merge-base --is-ancestor c971c42 origin/main
git diff --check origin/main
git log --oneline a84d2e7..origin/main
```

HEAD, main y la referencia remota deben coincidir en la publicación; `ls-remote` confirma el SHA remoto efectivo, sin depender solo del tracking local. El árbol debe quedar limpio y todos los commits anteriores ser alcanzables. No se eliminan las ramas de trabajo.
