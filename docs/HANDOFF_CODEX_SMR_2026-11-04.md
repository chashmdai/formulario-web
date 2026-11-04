# Traspaso técnico — Formulario productivo → SMR Windows

**Primer documento para el nuevo Codex.** Corte 04/11/2026, America/Santiago. SMR significa SIGER Mínimo Receptor: aplicativo independiente de recepción, validación, registro, almacenamiento y visualización de Comunicaciones. Este expediente prepara antecedentes; no implementa SMR ni activa la conexión física.

## Resumen y estado efectivo

El Formulario Público recibe NNA y adultos (CU o identificación declarada), valida antecedentes y adjuntos, persiste SQL/originales, emite comprobante de custodia y ofrece PULL/ACK firmado. SIGER/SMR debe registrar una Comunicación, no convertirla automáticamente en Caso. Microsoft Graph/correo no pertenecen al Formulario ni son requisito de este SMR mínimo.

| Evidencia actual inspeccionada en Ubuntu | Resultado |
|---|---|
| Dominio | https://formulario.defensorianinez.gob.cl, HTTPS verificado con CA del sistema; borde Cloudflare observado |
| Instalación | `/srv/ddn-formulario-produccion`, proyecto `ddn-formulario-produccion` |
| Fuente **compilada** / label OCI | `c42bc4fb913c85001e01c6ba4c7b332a530ca448` |
| Release documental instalado antes de este traspaso | `5fccaad73afbf1ac3a297168b577bc5370156be0` |
| Imagen app | `sha256:6fa0f5d53cc72e98fd9789537bf3e98269826be7e4ea9fbbd40158651ffaba6b` |
| JAR ejecutado, hash leído dentro del contenedor | `5f778552618fd734af4d04c8cf511d67b0d80a21756c57d9e8ab81abf81fbaaa` |
| Seis servicios | App, proxy Caddy, MySQL, ClamAV, Inspector y PISEE: running/healthy al inspeccionar |
| Persistencia | MySQL con volumen `/var/lib/mysql`; custodia `store` presente; no se leyeron cuerpos ni registros personales |
| Controles CAS leídos | Revisión 10; aceptarNuevos=true, recuperar=false, contingencia=NORMAL |
| Configuración efectiva | Recepción habilitada; integración preparada; recuperación institucional deshabilitada; confianza ACK `{}` |
| Acceso ciudadano | Contraseña temporal hasta 15/12/2026, 00:00 America/Santiago; autorización vigente permite continuar sin contraseña al vencer, sujeto a controles operacionales |
| PISEE / CU | PISEE productivo y consulta Java acreditados previamente; no nueva consulta personal. CU oficial implementada/redirección observada; login personal completo no acreditado |
| Respaldo más reciente registrado | `formulario-20261104T183237Z.gpg`, cifrado/integridad verificados; timer activo |

Inspección de contenedores/metadatos/JAR/control realizada **04/11 a las 21:57:52 UTC (18:57:52 America/Santiago)**. No implica monitorización permanente. El manifiesto RC1 inicial contiene la imagen anterior; prevalecen para el artefacto activo la [actualización del favicon](ACTUALIZACION_FAVICON_PRODUCCION_2026-11-04.md) y la inspección de este expediente. Este commit documental no recompila ni despliega nada.

Backup SHA-256: `55d8c967f8b07477101c0befa77ac12d79bcde9de686fadf62e4d18e3a27abf4`, en `/srv/ddn-formulario-respaldos/productivo-diario/`. `previousControlRestored=false` en ese registro significa que el respaldo se tomó con el cierre CAS ya aplicado por la actualización; el despliegue reabrió después, como confirma revisión 10. No interpretar ese booleano aislado como recepción actualmente cerrada. No se repitió backup/restore en este encargo. Copia fuera del anfitrión aún pendiente de destino institucional.

## Qué está pendiente

SMR funcional independiente, su cuenta de servicio/usuarios/permisos, MySQL y NTFS propios, identidad ACK institucional y confianza en Ubuntu, conectividad desde Windows, interfaz mínima y E2E físico de esta instalación. No se afirma que Windows/VM hayan sido inspeccionados, que SMR ya exista ni que el despliegue U4.4 pueda reutilizarse como receptor productivo.

## INSTRUCCIONES DE ARRANQUE PARA CODEX SMR

Estas son condiciones técnicas de arranque, **no el prompt definitivo ni autorización para ejecutar cambios fuera del futuro encargo**.

1. Repositorio destino: **https://github.com/chashmdai/sistema-de-requerimientos**. Leer AGENTS, traspaso H2 y U4.4 vigentes. Inspeccionar Git y máquina Windows antes de tocar servicios. HEAD remoto observado: `95cf2ae8173b4c6cab3eb8a1531659b8459dd898`; volver a comprobarlo.
2. Trabajar en **worktree independiente** con rama SMR y build/runtime separados. Preservar SIGER operativo, bases QA/U4/lab, originales, claves, COM/ACK, procesos y tareas. No usar perfiles QA como modo institucional productivo.
3. Leer en orden los cuatro manuales siguientes. El contrato efectivo es formulario-completo 1.0/1.1, no el sobre histórico `/integracion/v3/entregas`. No implementar un POST entrante paralelo.
4. Reutilizar consumidor, validadores, custodia y detalle existentes cuando corresponda, extrayendo dependencias con pruebas. [Inventario de reutilización](SMR_REQUISITOS_IMPLEMENTACION_WINDOWS.md#reutilización-verificada-no-reconstrucción). No rehacer canonicalización, firmas, contratos, recibos ni folios históricos.
5. Construir backend, consumidor automático, interfaz institucional mínima, autenticación/permisos, persistencia/documentos y pruebas en entorno propio. Recepción durable y COMMIT preceden al ACK. No implementar gestión completa de Casos, distribución/tareas.
6. Validar UI, MySQL y NTFS con datos sintéticos y proveedor de pruebas local. Consumidor productivo desactivado. No introducir datos registrales reales en QA.
7. Preparar JAR portable/config externa/servicio, manifiesto SHA-256, instalación, actualización, backup/restore y rollback. El mismo artefacto se traslada a la VM; secretos/identidades/datos no son portables por presunción.
8. Coordinar con Codex Ubuntu identidad ACK, conectividad sin reclamar y apertura de ventana. La ausencia de filtro de campaña obliga a autorizar el destino para toda cola elegible antes de cualquier PULL real.

Entrega futura: SMR ejecutable en Windows con backend, recuperación/conciliación automáticas, UI completa mínima, autenticación, originales y procedencia, idempotencia, persistencia tras reinicio, pruebas y JAR portable. Una sonda HTTP o una bandeja vacía no cumple la entrega.

## Orden de lectura y paquete

1. [Contrato productor y ejemplos completos](SMR_CONTRATO_PRODUCTOR_FORMULARIO.md).
2. [Requisitos de implementación Windows](SMR_REQUISITOS_IMPLEMENTACION_WINDOWS.md).
3. [Procedimiento de conexión y E2E](SMR_PROCEDIMIENTO_CONEXION_Y_E2E.md).
4. [Checkpoint Ubuntu](CHECKPOINT_PRODUCCION_SIGER_2026-11-04.md), [arquitectura transitoria](ARQUITECTURA_RECEPTOR_TRANSITORIO.md), [actualización activa](ACTUALIZACION_FAVICON_PRODUCCION_2026-11-04.md).
5. [Contratos multimedia](../contratos/formulario-completo-multimedia-v1.1.md), [registro/ACK](../contratos/registro-comunicacion-v3.md), [identidad histórica](../contratos/identidad-componentes-v1.1.md) y advertencias de separación en el manual.

La entrega contiene los **cuatro documentos solicitados**: contrato, requisitos, procedimiento y handoff. Identidades y material de intercambio se detallan en el procedimiento. El ZIP contiene los cuatro, referencias, fuentes congeladas de ambos repositorios, ejemplos offline, material público y manifiesto/hash por archivo.

Paquete para entregar al Codex Windows: `/home/admin-ti/operacion-formulario/handoff-smr-windows-20261104.zip`. Verificar su SHA-256 contra el entregado separadamente por el operador; extraer en directorio propio, leer README y este archivo. Los ejemplos tienen claves **de prueba exclusivamente públicas**, no se instalan en Ubuntu. El paquete público institucional original se incluye sin modificar y se verifica antes de usar.

## Datos y coordinaciones humanas pendientes

- Confirmar alcance de SMR, responsables, roles lectores/administradores y mecanismo de autenticación institucional.
- Aprobar hostname/puertos, cuenta Windows/ACL, esquema y credenciales MySQL, custodia/capacidad y respaldo/restore; TI proporciona parámetros reales de VM y acceso, sin inventarlos desde Ubuntu.
- Autorizar destino, finalidad y datos de la primera campaña/E2E; identidad de prueba solo por canal privado, no en prompt/chat/fixtures.
- Entregar Bearer existente por canal seguro separado; intercambiar huellas del productor y de la clave pública ACK Windows. Privada ACK queda en Windows.
- Codex Ubuntu importa confianza externa en ventana controlada (recrea app, sin recompilar), mantiene admisión acordada y habilita PULL solo al coordinar receptor único. No dar al Codex Windows acceso indiscriminado al servidor.
- Si se quiere primer E2E real en PC de desarrollo, aprobar expresamente ese custodio y el traslado posterior; preferir VM para no confirmar en una máquina que luego se descarte.

## Riesgos que no deben ocultarse

ACK confirmado saca la Comunicación de pendientes: una pérdida del receptor confirmado no se resuelve esperando otro PULL. Preservar SQL, originales, folios y ACK pendientes al migrar. No introducir filtros de campaña inexistentes, no borrar cola, no afirmar cero impacto sobre SIGER sin preflight y pruebas. Reservas vencen a cinco minutos, hay backoff/cuarentena y no renovación de lease. Conservar claves verificadoras históricas en rotaciones. Cloudflare exige calificar cliente real; hubo 403 con Python genérico y 200 con UA técnico. No debilitar TLS ni autenticar SMR con la contraseña ciudadana.

Este encargo hizo solo lecturas de Git, fuentes, hashes, inventario, control operacional y HTTPS de portada. **No ejecutó Compose, reinicios, cambios CAS, PISEE personal, PULL, ACK, apertura técnica ni mutaciones SQL.** No modifica el repositorio SIGER, sus instrucciones o su infraestructura.
