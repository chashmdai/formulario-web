# Baseline del Formulario Público · 24 de octubre de 2026

## Alcance y estado operativo

El Formulario Público se considera **aproximadamente un 96 % validado según la evaluación operativa del proyecto**. Es una estimación de avance aportada por el proyecto: no es un cálculo de cobertura, una certificación formal, aceptación institucional definitiva ni autorización de producción.

Este baseline establece una referencia de integración conocida y evolutiva. **El formulario no está congelado funcional ni visualmente.** Se esperan ajustes de identidad visual, interfaz, experiencia, redacción, instrucciones, opciones, preguntas, eliminación de campos y condiciones durante la validación institucional. Estos ajustes no rompen por sí mismos el baseline; su efecto contractual debe evaluarse por separado.

La implementación completa parte del historial conservado de `integracion-siger-linux-2026` y `pulido-ui-formulario-2026-10-24`. Normalización de contactos: commit **`4ad601f`**. El checkpoint vigente identifica la consolidación y la publicación final en `origin/main`. Repositorio: https://github.com/chashmdai/formulario-web. No se modificó ni publicó `sistema-de-requerimientos`.

## Capacidades verificadas y límites

| Capacidad | Estado y evidencia | Límite real |
|---|---|---|
| Adultos con/sin CU | Tres pasos y vista completa: consulta, entrevista, estadísticas, orientación a empleo, promoción y vulneración; personas relacionadas e instituciones | Validación de laboratorio; cambios institucionales posteriores esperados |
| NNA | Dos pasos, RUN voluntario/sin RUN, mínimos de identificación, región/comuna y contacto seguro | La excepción para omitir mínimos no está aprobada; no se inventa anonimato fuera de las reglas confirmadas |
| ClaveÚnica | **REAL_VALIDADO** en Sandbox: autorización, callback, identidad de sesión y logout, evidencia en informes previos | No equivale a certificación ni validación productiva; no se repitió autenticación real en esta unidad |
| PISEE/SRCeI | **REAL_VALIDADO** DEV: Nodo Linux y consulta autorizada acotada; disparador RUN validado, debounce, deduplicación y restauración | Solo presentadores/finalidades autorizados y allowlist privada. No búsquedas registrales indiscriminadas de terceros. No pruebas de carga ni consultas nuevas en esta unidad |
| Identidad/procedencia | Sesión persistida y procedencia individual; pruebas de resultado completo/parcial/vacío/error/timeout y respuestas tardías | CU autentica; RUN declarado y SRCeI no autentican. Complementos registrales de un RUN declarado no se devuelven al navegador |
| Adjuntos/antivirus | Almacenamiento privado, límites, integridad y acceso por propietario/reserva. ClamAV real verificado anteriormente | El scanner debe estar disponible/configurado en cada ambiente; no se repitió una campaña real de antivirus en esta unidad |
| Custodia e idempotencia | MySQL, transacciones, huellas, Ed25519, recuperación y ACK sintético comprobados | Las custodias del laboratorio aún no acreditan registro institucional real ni folio SIGER |
| UI/UX | Responsive, temas claro/oscuro/contraste, escala de texto, foco, teclado y reducción de movimiento; comparativa visual preservada | Accesibilidad funcional y mediciones, no auditoría WCAG integral ni aceptación visual institucional definitiva |
| Contactos | Tres celulares y tres correos de captura normalizados y validados; escritura, pegado, selección, corrección y custodia comprobados | Formato no demuestra existencia del número/correo, entrega de mensajes ni titularidad |

Referencias de evidencia: [CIERRE_ADULTOS_2026-10-24.md](CIERRE_ADULTOS_2026-10-24.md), [RUN_SRCEI_AUTOMATICO_2026-10-24.md](RUN_SRCEI_AUTOMATICO_2026-10-24.md), [INFORME_UI_UX_2026-10-24.md](INFORME_UI_UX_2026-10-24.md) y [CUSTODIA_FORMULARIO_COMPLETO.md](CUSTODIA_FORMULARIO_COMPLETO.md). Sus cifras históricas no sustituyen los resultados actuales siguientes.

## Contactos: representación actual e históricos

La auditoría de todos los componentes encontró el celular adulto `telefono` y los NNA `nna-primary-celular` / `nna-secondary-celular`; correos `email`, `nna-primary-email` / `nna-secondary-email`. Las tarjetas de personas relacionadas e instituciones no tienen campos de teléfono/correo. Los enlaces institucionales a teléfonos fijos permanecen intactos; no se les aplica un formato de móvil.

El control móvil presenta el prefijo fijo **+56 9**, separado de la zona editable. Se escriben ocho dígitos; el teclado es numérico y el control es `type=tel`. El pegado admite ocho dígitos, nueve dígitos nacionales iniciados en 9, prefijo 56 / +56 y 0056, con separadores de presentación. No se duplican los prefijos. Es posible seleccionar, reemplazar, corregir y borrar; un móvil adulto opcional puede quedar vacío. NNA conserva la obligatoriedad del canal elegido y las alternativas de contacto seguro/inseguro.

Las capturas nuevas guardan móviles como **`+569` seguido de ocho dígitos**, sin espacios. Los correos se recortan en los extremos, conservando la parte local y su capitalización; se aceptan alias con `+`, subdominios y sufijos como `.algo`. React y Java comparten casos sintéticos de formato en `contratos/fixtures/contactos-formulario-v1.json`. Se rechazan direcciones incompletas, espacios internos, dominios mal formados y caracteres de control. Esto no es una verificación de buzón ni de identidad.

El backend normaliza **solo capturas nuevas antes de validar y firmar su contenido**. La huella de solicitud para idempotencia conserva el cuerpo original recibido y su semántica de igualdad exacta. Un reintento con misma clave/cuerpo retorna la custodia existente; otro cuerpo con esa clave sigue en conflicto, aunque represente el mismo número. Las lecturas, recuperaciones, firmas y reintentos históricos no pasan por la nueva normalización. Hay una prueba con un documento firmado según reglas antiguas que verifica recuperación íntegra y firma sin modificación en H2 y MySQL.

Los consumidores deben admitir que los registros históricos conserven su formato original y los nuevos móviles estén en representación canónica. No se debe reescribir un documento firmado para uniformar su aspecto. Un índice de contacto derivado puede construirse separadamente, preservando el original y su trazabilidad.

## Contrato disponible para SIGER desde ahora

La base para el consumidor completo es la extensión **ya implementada**:

`/integracion/v3/formulario-completo/entregas`

Es optativa y separada de `/integracion/v3/entregas`. El sobre histórico no contiene todo el formulario; no deben truncarse relatos, contactos, adjuntos o fuentes para ajustarlos a él. **SIGER institucional todavía debe adaptar su consumidor al formulario completo.** No se afirma interoperabilidad integral con su consumidor actual.

Se utiliza PULL HTTPS iniciado por SIGER, autenticación de máquina `Authorization: Bearer …` y confianza criptográfica configurada fuera de Git. No hay conexión directa entre bases ni llamadas del navegador a la VM institucional. Las versiones de documento son independientes del número de ruta HTTP: `comunicacion.version` del documento completo actual es `1.0`.

### Rutas y reservas

| Operación | Solicitud | Respuesta/garantía |
|---|---|---|
| `POST /reclamar` | `{limite: 1..10}` | `{entregas:[{reserva,venceEn,intento,entrega}]}`; `venceEn` es epoch en milisegundos; reserva de cinco minutos |
| `GET /{id}/adjuntos/{adjunto}` | `X-Reserva: UUID` y autenticación de máquina | Bytes privados durante reserva vigente; 404 si no corresponde, 409 por reserva inválida/vencida; verificar tamaño y SHA-256 |
| `POST /{id}/confirmacion` | `{reserva,recepcion:{registro,comprobante}}` | 204 tras verificar firma institucional confiada, operación, ambas huellas, canal y reserva. ACK idéntico es idempotente |
| `POST /{id}/fallo` | `{reserva,codigo}` | Códigos: `TRANSITORIO`, `CONTRATO_INVALIDO`, `RECEPTOR_NO_DISPONIBLE`, `CONFIRMACION_AMBIGUA`; backoff/cuarentena |

El consumidor debe guardar el token de reserva y su vencimiento, recuperar y verificar adjuntos, persistir la Comunicación íntegra, asignar su registro y comprobante, **confirmar su COMMIT institucional y recién después emitir el ACK**. Debe deduplicar por operación pública, conservar reservas/confirmaciones ambiguas y conciliar un ACK perdido sin crear otra Comunicación. No hay endpoint de renovación de reserva en esta extensión: al vencer se necesita una nueva reclamación válida, conservando la deduplicación institucional.

### Estructura del documento

`entrega` contiene:

- `comunicacion`: `version`, `id`, `recorridoId`, `tipo`, `recibidaEn`, `avisoVersion`, `aceptaCanalDigital`, `datos`, `personas` y `adjuntos`.
- `tipo`: `ADULTO_CU`, `ADULTO_DECLARADO` o `NNA`. No inferir autenticación desde el tipo de una persona relacionada ni desde la presencia de RUN.
- `datos.campos`: mapa de IDs semánticos a cadenas. Incluye motivo/subtipo, relato, territorio, contacto y respuestas condicionales activas. No confundir etiquetas visuales con IDs del contrato.
- `datos.personas`: referencias `{personaId,detalles}` de personas activas. `datos.instituciones`: `{nombre,tipo,rol,region,comuna}`. `datos.adjuntos`: UUID de archivos del recorrido.
- `comunicacion.personas`: agregado interno autorizado del presentador y las personas activas, con `id`, `rol`, `runNumero`, `runDv`, `modulo11`, `autenticada`, `estadoConsulta`, `consultadaEn`, `consultaRef`, `atributos` y `discrepancias`.
- Cada atributo/discrepancia contiene `valor`, `codigo`, `fuente`, `nivel`, `observadoEn` y `evidencia`. Se distingue CU, SRCeI y declaración ciudadana; no aplanar ni elevar una declaración a identidad verificada. Los campos no informados y las fuentes discrepantes deben preservarse.
- `comunicacion.adjuntos`: descriptores `{id,nombre,tamanio,sha256,tipo,estado}`. Los bytes se recuperan por la ruta con reserva, no por una URL pública.
- `sha256`, `huellaProcedencia` y `custodia`: huella de contenido completo, huella de procedencia y comprobante técnico público Ed25519.

Se admiten hasta cinco adjuntos, 10 MiB por archivo y 25 MiB por lote. Las capturas ciudadanas completas admiten 64 KiB de JSON; los relatos admiten hasta 5000 caracteres. El consumidor no debe asumir el límite histórico de 2000 caracteres ni descartar campos desconocidos antes de verificar una firma.

### Huellas, firmas y ACK

`CanonFormulario` ordena recursivamente las claves de objetos y conserva el orden de arreglos. Calcula SHA-256 sobre UTF-8 con dominio `SIGER_FORMULARIO_COMPLETO_1\0CONTENIDO\0` o `…\0PROCEDENCIA\0`. La procedencia usa el mapa de atributos/discrepancias por ID de persona. No sustituirlo por el algoritmo histórico ni por la serialización arbitraria de otro cliente.

`custodia` tiene `{algoritmo,keyId,payload,firma}`; payload y firma usan base64url sin padding. La firma pública vincula ID, fecha, aviso y ambas huellas. Debe verificarse con claves públicas confiadas, no con una clave adjunta a la entrega. La firma institucional usa claves separadas y confirma `{registro,comprobante}`; el sello público nunca sirve como ACK institucional.

`registro` liga operación pública, UUID institucional, número/folio, fecha, canal y ambas huellas. En los modelos actuales, adultos usan registro `version=1.0` / canal `FORMULARIO_WEB_PUBLICO`; NNA usa `version=1.1` / canal `FORMULARIO_WEB_NNA`. El consumidor debe respetar esa vinculación y la representación firmada exacta de `ComprobanteInstitucional`. El folio es emitido por SIGER, nunca derivado del UUID público.

Antes del ACK verificable, el ciudadano ve `PENDIENTE_REGISTRO_SIGER`, referencia y comprobante de recepción; después ve `REGISTRADA_SIGER` y comprobante institucional. Recepción pública y registro formal son hechos distintos. No se afirma FEA ni un servicio externo de sellado de tiempo.

### Fuentes técnicas y pruebas de contrato

- [CUSTODIA_FORMULARIO_COMPLETO.md](CUSTODIA_FORMULARIO_COMPLETO.md): extensión, controles, archivos y reversión.
- [registro-comunicacion-v3.md](../contratos/registro-comunicacion-v3.md), [identidad-componentes-v1.1.md](../contratos/identidad-componentes-v1.1.md) y fixtures históricos: se conservan; no representan por sí solos todo el agregado nuevo.
- Modelos/autenticación efectivos: `IntegracionFormularioController`, `EntregaFormulario`, `DatosFormulario`, `IdentificacionCiudadana`, `AdjuntosFormulario`, `CanonFormulario`, `ComprobanteDigital`, `ComprobanteInstitucional`, `RegistroInstitucional` e `IntegracionSecurity`.
- Ejemplos ejecutables con claves generadas sintéticamente: `FormularioCompletoJdbcTest`, `FormularioCompletoHttpTest`, `AdultosFormularioJdbcTest` y pruebas históricas V3. Deben usarse para la batería compartida del consumidor; no trasladar secretos o datos DEV a fixtures.

## Evolución y compatibilidad

| Clase de cambio | Compatibilidad y coordinación |
|---|---|
| Presentación, CSS, orden visual, instrucciones o etiqueta sin cambiar su significado/ID | No cambia el contrato de transporte; mantener validaciones y regresiones funcionales |
| Campo opcional añadido, respuesta opcional ausente o pregunta retirada de capturas futuras | Puede conservar estructura/versiones **si** el consumidor admite presencia/ausencia y preserva campos desconocidos. Verificar sus listas de permitidos y validaciones; documentar el cambio y añadir fixtures. No implica compatibilidad automática de todos los consumidores |
| Renombrar IDs, cambiar tipos/unidades/semántica, obligatoriedad, opciones interpretadas como enums, fuentes, límites o formato esperado por el consumidor | Requiere actualizar/coordinar consumidor y validaciones; versionar cuando no pueda mantenerse compatibilidad. Publicar casos anterior/nuevo y estrategia de transición antes de activar |
| Cambiar canonicalización, algoritmos de firma, estructura de identidad/ACK o rutas/protocolo | Evolución contractual explícita, coordinación entre repositorios y versiones/fixtures de compatibilidad; no reinterpretar históricos |

La normalización actual conserva IDs, mapas, versiones y algoritmos. Un consumidor que espere exactamente nueve dígitos nacionales debe ajustar su validación al móvil canónico nuevo y conservar lectura de formatos históricos. No debe modificar el cuerpo firmado para calcular nuevamente una firma anterior.

Los datos y firmas existentes se conservan aunque una pregunta se elimine o una opción cambie de etiqueta. No reclasificar ni elevar procedencias históricas de forma implícita. Registrar toda evolución que afecte compatibilidad en ambos repositorios cuando se autorice trabajar en el institucional. Se mantiene abierta la evolución visual y funcional necesaria para la validación institucional.

## Regresión de esta unidad

- Frontend: **152 pruebas correctas**, 17 archivos; lint y build correctos.
- Backend H2: **176 correctas, 1 omisión opcional**, 177 descubiertas.
- Backend MySQL 8.4 de pruebas: **176 correctas, 1 omisión opcional**, 177 descubiertas; esquema `robust_public_test`, separado de `robust_public_local`.
- Navegador: **seis recorridos completos controlados**, adultos CU/declarado y NNA, escritorio/móvil; ocho campos móviles ejercitados y 40 pegados completos, selección/borrado/corrección, correo con alias y subdominio, revisión y cuerpo canónico. Sin proveedores ni comunicaciones reales nuevas.
- Regresión visual ampliada y despliegue local: resultados finales en el checkpoint.
- Sin cambios en migraciones V1–V7 ni en los fixtures V3 históricos. Custodias y archivos recibidos del laboratorio conservan conteos y CHECKSUM. Los borradores/sesiones pueden evolucionar por uso del laboratorio; no se presentan como históricos inmutables.

Evidencia privada local: `.local/evidencias/contactos-{front,lint,build,backend-h2,backend-mysql}.log`, `contactos-navegador/`, `pulido-ui/contactos-regresion/`, `contactos-auditoria-historial.json` y `contactos-laboratorio-estado.json`. No se publican logs, capturas, bases, secretos ni identificadores DEV privados.

## Pendientes para la siguiente unidad institucional

1. Adaptar el consumidor SIGER a la extensión completa: DTO, persistencia íntegra, procedencia, adjuntos, firmas, reservas, deduplicación y ACK después de COMMIT.
2. Ejecutar pruebas de contrato cruzadas y de conciliación con datos/firmantes sintéticos antes de una integración real autorizada. La extensión está implementada; el consumidor real aún no fue adaptado ni validado contra ella.
3. Continuar la revisión institucional funcional, visual y de privacidad; registrar los cambios de preguntas y condiciones que resulten.
4. Campaña independiente de estrés/resiliencia/hardening y requisitos de producción, solo cuando corresponda por autorización y estabilidad del equipo. No se ejecutó carga en esta unidad.

La base es estable para comenzar esa integración y sigue siendo evolutiva. No se declara un producto terminado ni se impone una congelación artificial de campos.
