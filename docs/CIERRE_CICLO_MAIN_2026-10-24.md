# Cierre del ciclo de desarrollo · main · 24/10/2026

> **Cierre histórico del 24/10.** El estado publicado actual incorpora V9/V10: [consolidación del 25/10](CONSOLIDACION_MAIN_2026-10-25.md) y [arquitectura transitoria vigente](ARQUITECTURA_RECEPTOR_TRANSITORIO.md). Publicar código no actualiza las bases V7/V8 ni habilita servicios. Las cifras y decisiones siguientes conservan su fecha original.

## Alcance definitivo

Se consolida la implementación multimedia validada en **main**, conservando el historial y las ramas de trabajo. Base remota inspeccionada y actualizada por fetch: `c1e4eadc1979484007f09758cc1842bca1545995`, sin divergencia. Integración fast-forward de `multimedia-adjuntos-2026-10-24`, incluidos:

- `c67e359`: custodia multimedia privada, bloques, validación, visor ciudadano y contrato.
- `74e4b6e`: operación, respaldo/restauración y QA.
- `6fd73ca`: concurrencia, informe y checkpoint multimedia.

Este checkpoint pertenece al commit documental final del cierre. La publicación autorizada se realiza mediante push normal a `origin/main`, sin force. El SHA exacto de ese commit se verifica comparando HEAD local, `origin/main` actualizado y `git ls-remote origin refs/heads/main`; queda en la respuesta final y en la evidencia local `cierre-main-publicacion.json`. El propio archivo no incorpora una autorreferencia de hash.

No se desarrollan funcionalidades nuevas ni se ejecuta hardening. Tampoco se despliegan los JAR de main, se aplican migraciones al laboratorio integrado, se habilitan servicios públicos o multimedia fuera del laboratorio, ni se modifica/publica `sistema-de-requerimientos`.

## Baseline estable y evolutivo

El Formulario Público conserva su referencia operativa aproximada del **96 % validado según la evaluación del proyecto**. No es cobertura calculada, certificación, aceptación institucional definitiva ni autorización productiva.

La validación funcional institucional pendiente se concentra principalmente en **redacciones, opciones de selección y eventuales eliminaciones de campos**. Estas observaciones no bloquean la integración SIGER sobre el contrato conocido ni congelan interfaz/requisitos. Las decisiones técnicas y arquitectónicas continúan bajo responsabilidad de desarrollo. Coordinar los efectos contractuales reales de cambios posteriores; nunca eliminar o reinterpretar datos históricos para adaptarlos a la interfaz nueva.

## Compatibilidad documental 1.0/1.1

La integración completa usa `/integracion/v3/formulario-completo/entregas`. Se conserva la API V3 histórica separada y sus fixtures.

| Perfil | Garantía |
|---|---|
| Documento 1.0 | Representación y firma históricas intactas; la reclamación sin cabecera adicional solo ofrece 1.0. |
| Documento 1.1 | Nuevas capturas con archivos FILE; el consumidor debe optar explícitamente con `X-Formulario-Versiones: 1.0,1.1` o `1.1`. |
| Originales | Descriptores `id,nombre,tamanio,sha256,tipo,estado`; bytes privados bajo autenticación técnica y reserva, ahora por streaming/Range. Sin rutas públicas. |
| Integridad | UUID, canonicalización, firmas, procedencia, idempotencia, reservas y correspondencia del ACK preservados. No se reescriben documentos recibidos. |
| Registro institucional | SIGER persiste/COMMIT y recién después confirma su ACK firmado. La versión del documento no cambia por sí misma la versión del comprobante institucional por canal ni el número HTTP V3. |

SIGER todavía debe adaptar su consumidor completo y ejecutar pruebas E2E. Puede comenzar desde main sin reconstruir el formulario ni esperar una congelación funcional. El [baseline](BASELINE_FORMULARIO_PUBLICO_2026-10-24.md), la [custodia](CUSTODIA_FORMULARIO_COMPLETO.md) y el [contrato multimedia](../contratos/formulario-completo-multimedia-v1.1.md) son la referencia conjunta.

## Regresiones repetidas sobre main

Sin carga intensiva, proveedores gubernamentales ni nuevas Comunicaciones reales:

| Verificación de este cierre | Resultado |
|---|---|
| ESLint | Correcto |
| React/Vitest | **160 pruebas correctas**, 18 archivos |
| TypeScript/Vite | Build correcto |
| Maven verify · H2 | **199 descubiertas: 197 correctas, 2 omitidas**, cero fallos/errores; JAR generado en salida aislada |
| Maven test · MySQL | **199 descubiertas: 197 correctas, 2 omitidas**, cero fallos/errores; exclusivamente `robust_public_test` |
| Utilidades de respaldo | **5 pruebas Python correctas** |
| Auditoría de publicación | Historial/árbol/commit preparado revisados contra secretos institucionales, privados del laboratorio, material PEM y archivos reservados; sin coincidencias |

Omisiones: `RecuperacionProcesoTest` heredado y `MultimediaWorkersRealTest` opt-in. La evidencia real previa del worker, ClamAV, navegador, transferencia, recuperación y restauración permanece identificada en el [informe multimedia](INFORME_MULTIMEDIA_2026-10-24.md); no se repitieron pruebas reales innecesarias ni se convierten en certificación.

El build utiliza `.local/cierre-main-target`, nunca el JAR del servicio integrado ni `multimedia-lab/runtime.jar`. No se reinicia ningún servicio como consecuencia de la consolidación. Los cambios de esta última unidad, aparte del fast-forward de código ya validado, son documentación, README y la alineación del workflow CI a Ubuntu 24.04/POSIX. El workflow conserva permisos de lectura, usa las mismas pruebas aisladas y no añade despliegues ni activaciones. No se cambian funcionalidades ni arquitectura.

## Banderas y servicios al cierre

La configuración predeterminada conserva recepción/formulario/multimedia sin habilitación por defecto y callback CU cerrado; PISEE está deshabilitado salvo configuración autorizada. La existencia de código en main no sustituye esos gates.

Auditoría de solo lectura de los dos procesos existentes, comparada antes/después del cierre:

| Bandera / capacidad | Laboratorio integrado · 18443 | Laboratorio multimedia aislado · 19443 |
|---|---|---|
| `formulario.habilitado` | true | true |
| `formulario.recepcion.habilitada` | true | true |
| `formulario.adultos.habilitado` | true | true |
| `formulario.multimedia.habilitada` | **false** | **true** |
| `siger.claveunica.callback-approved` | true, exclusivamente Sandbox existente | false |
| `PISEE_SRCEI_ENABLED` | true, DEV y allowlist privada existente | false |
| `PISEE_DEV_AUTHORIZED` | true | false |
| `/api/formulario/recepcion/capacidades` | adultos/NNA disponibles en laboratorio; sin multimedia | adultos/NNA y multimedia disponibles solo en laboratorio aislado |

En ambos, `publico.nna.habilitado=false` y `publico.recepcion.habilitada=false` corresponden al camino previo; no anulan el módulo completo controlado por `formulario.*`. `publico.recepcion.aviso-validado=true` es configuración de laboratorio, no aceptación legal/productiva inferida por este cierre.

Ambos Spring escuchan en **127.0.0.1**. El origen HTTPS local 443 sigue encaminado al proceso integrado 18443 y conserva su JAR/base V7; el puerto 19443 y la base V8 son independientes. No se cambian hosts, certificados, DNS, túneles, firewall o configuraciones privadas. La API y la configuración se consultan sin autenticar al ciudadano ni disparar SRCeI.

V8 queda publicada como migración forward del código; **no se aplica automáticamente** al esquema integrado V7 por hacer push. No se recrean bases/volúmenes ni se regeneran claves. Los originales y firmas existentes permanecen preservados. Se comprobó que el laboratorio integrado conserva 13 custodias, 13 adjuntos y siete migraciones con los mismos CHECKSUM protegidos. Los hashes de configuración y JAR de ambos procesos y las capacidades/banderas coinciden antes/después; no hubo redespliegue.

## Referencias para quien continúe SIGER

1. [README principal](../README.md): entrada, arquitectura, mapa del código y build seguro.
2. [Baseline evolutivo](BASELINE_FORMULARIO_PUBLICO_2026-10-24.md) y [índice contractual](../contratos/README.md).
3. [Custodia del formulario completo](CUSTODIA_FORMULARIO_COMPLETO.md) y [compatibilidad documental 1.0/1.1](../contratos/formulario-completo-multimedia-v1.1.md).
4. [Visor institucional y origen de documentos](VISOR_INSTITUCIONAL_MULTIMEDIA.md): ingreso ciudadano separado de gestión y referencias sin duplicar físicamente evidencia por Caso.
5. [ADR multimedia](ADR_MULTIMEDIA_2026-10-24.md), [formatos/cuotas](POLITICA_MULTIMEDIA_2026-10-24.md) y [operación/respaldos](OPERACION_MULTIMEDIA.md).
6. [Informe multimedia](INFORME_MULTIMEDIA_2026-10-24.md): evidencia y limitaciones reales.
7. [Plan de hardening de producción diferido](PLAN_HARDENING_PRODUCCION_DIFERIDO.md): riesgos/dependencias de ambos aplicativos, bloqueos reales y mejoras opcionales.

## Producción pendiente; plan no ejecutado

Primero integrar ambos aplicativos y probar E2E. Después, una unidad explícita deberá resolver/verificar ambientes productivos y habilitaciones, acceso institucional por recurso, operación de secretos/firmas, red y proxy efectivos, restauración conjunta fuera del anfitrión, actualización de AV/motores, capacidad calificada, alertas y política de retención. No se habilita producción con el porcentaje de avance o con un push correcto.

La continuidad ciudadana entre sesiones tras reinicio sigue pendiente funcional/operativa y falla cerrado; no es un bypass de acceso ni impide iniciar la integración. Los máximos multimedia físicos, Cloudflare si se utiliza, conversión Office/visor PDF completo y formatos actualmente cerrados conservan sus límites documentados. El plan distingue requisitos obligatorios de mejoras opcionales; no impone una arquitectura adicional ni ejecuta cambios.

## Punto final de esta ejecución

Tras comprobar el push y el HEAD remoto, **se detiene la implementación del Formulario Público**. No se continuarán funcionalidades, optimizaciones, arquitectura o hardening sin un nuevo encargo explícito. Se conservan ramas, datos, evidencias y laboratorios; el trabajo institucional posterior se gestiona en su propia unidad autorizada.

Evidencias privadas excluidas de Git: `.local/evidencias/cierre-main-{lint,front,build,h2,mysql}.log`, `cierre-main-respaldo-unit.log`, `cierre-main-auditoria-historial.json`, `cierre-main-banderas-iniciales.json`, comprobación de preservación y `cierre-main-publicacion.json`. Los documentos históricos permanecen disponibles con una nota que remite a este cierre vigente.
