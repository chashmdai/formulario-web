# Identidad ClaveÚnica: interfaz, atributos y persistencia futura

Investigación iniciada el 14-10-2026 y cierre el 15-10-2026, America/Santiago. Implementación limitada a la vista ciudadana y su proyección de sesión. La recomendación sobre personas es una decisión propuesta para revisar en Hito 1, sin entidad, migración o automatismo nuevos.

## Ajuste implementado

IdentidadVerificada.from conserva RolUnico.numero y RolUnico.DV en runNumero/runDv. Son campos aditivos de sesión, vista previa y proyección por procedencia; no se extraen del RUN concatenado. El RUN completo anterior permanece como referencia compatible. Su DV canónico continúa en mayúsculas; el campo de origen separado conserva la representación recibida y ambos se validan como coherentes.

React muestra Número de RUN, Dígito verificador, dos nombres y dos apellidos como máximo, todos readonly; omite segundos elementos ausentes. No divide compuestos. Los demás componentes siguen íntegros en sesión, contratos, procedencia y sobres nuevos. La vista previa no repite una segunda identidad agregada.

No cambia el sobre firmado 1.1 ni su huella, ni el registro/folio/ACK v3. Los históricos 1.0 conservan cadenas y comprobantes; no se infieren componentes de RUN o nombres. Una identidad histórica que no contiene los atributos separados conserva el campo RUN agregado en la vista compatible. No se agregan campos persistidos, SQL ni migraciones.

## Fuentes y vigencia

- **F1. Material institucional aportado:** manual 6.5, mayo de 2026; guía de botón 2.0 y observaciones de fidelidad. Requisitos específicos de esta integración.
- **F2. Manual público enlazado actualmente por SGD:** [documento público](https://docs.google.com/document/d/16c0D2jVhuYOYGI9z4kC2aoNH8oWNA9v1cc3tXQ76TO0/edit), obtenido mediante exportación HTTPS, enlazado desde la [Guía Técnica de Autenticación](https://wikiguias.digital.gob.cl/guias/guia-tecnica-autenticacion). Su control incluye 6.5 de 07-05-2026 y 6.6 de 02-06-2025, fecha anterior a 6.5. Se conserva esa inconsistencia; no se inventa su corrección o una fecha de publicación.
- **F3. PDF público anterior:** [guía alojada en WikiGuías](https://wikiguias.digital.gob.cl/documentos/guia_tecnica_de_integracion_a_claveunica__8.pdf), cuyo control llega a 5.5 de 06-01-2025. Contiene reglas que difieren de F1/F2; no se trata como la última revisión.
- **F4. Metadatos actuales del proveedor:** [discovery OIDC](https://accounts.claveunica.gob.cl/openid/.well-known/openid-configuration), consultado sin credenciales. No publica scopes_supported ni claims_supported; por tanto no enumera atributos adicionales.
- **F5. Estándar:** [OpenID Connect Core 1.0, errata set 2](https://openid.net/specs/openid-connect-core-1_0.html), secciones 5.1, 5.3.2 y 5.4. Describe posibilidades del estándar, no permisos concedidos por CU.
- **F6. Condiciones oficiales:** [Resolución Exenta 733, 06-11-2025](https://www.bcn.cl/leychile/navegar?idNorma=1218250), secciones 4–6. Exige certificación/habilitación, contrapartes y comunicaciones formales mediante Mesa de Servicios.

No se consultó el estado privado de las credenciales productivas ni se realizaron autenticaciones QA/productivas. [Metadatos de consulta y huella del texto público](evidencias/run-dv-identidad-2026-10-14/fuentes-consultadas.json).

## Qué entrega userinfo y con qué garantías

El contrato descrito en F1 y corroborado en F2 contiene:

| Atributo | Forma | Requisito y evidencia |
|---|---|---|
| sub | Identificador OIDC | Obligatorio por F5; Spring comprueba coincidencia con ID token. No se usa como RUN o persona institucional ni se conserva en la Comunicación. |
| RolUnico.numero | Número en el ejemplo oficial | Documentado con el conjunto de scopes requerido; comprobado en Sandbox. SIGER lo exige y conserva su representación textual para la interfaz. |
| RolUnico.DV | Cadena | Documentado y comprobado en Sandbox; SIGER lo exige y valida. |
| RolUnico.tipo | RUN | Documentado; el parser exige este valor. |
| name.nombres | Arreglo de cadenas | Documentado y comprobado. SIGER exige lista válida y conserva orden/componentes. |
| name.apellidos | Arreglo de cadenas | Documentado y comprobado, con el mismo tratamiento. |

El manual exige conjuntamente openid run name. No publica una matriz exhaustiva de campos obligatorios/opcionales por scope aislado ni un JSON Schema con todas sus cardinalidades. Exigir esos campos en nuestro parser es un control de SIGER, no prueba de una garantía universal para cualquier autorización del proveedor. El segundo nombre o apellido no se presupone; se muestra solamente cuando existe.

Nuestra prueba real confirmó tres nombres y dos apellidos de la identidad ficticia oficial. No conservamos un inventario completo del JSON original de userinfo: no podemos afirmar experimentalmente que ningún otro campo haya estado presente. Sí podemos afirmar qué campos utilizamos y qué estructura conserva SIGER.

### Correo, nacimiento, domicilio, sexo y teléfono

No se encontró en F1/F2 una extensión oficialmente documentada de CU que habilite esos atributos para nuestra integración, ni scopes adicionales autorizados para obtenerlos. F4 tampoco anuncia su soporte. Su disponibilidad queda **sin confirmar**, no implementada.

F5 contempla email/email_verified, birthdate, gender, address y phone_number/phone_number_verified mediante scopes opcionales del estándar. Ese catálogo no acredita soporte o autorización en CU. Tampoco se equipara gender a sexo registral. El scope name usado por CU y su objeto name no deben confundirse con el perfil estándar completo.

Que ClaveÚnica administre correo/teléfono para recuperación o segundo factor no implica su entrega a SIGER. La [información oficial de CMF sobre segundo factor](https://www.cmfchile.cl/portal/prensa/625/w4-article-100963.html), publicada el 25-11-2025 para su aplicación desde el 15-12-2025, documenta el uso del correo por el proveedor, sin establecer su exposición mediante userinfo.

No se probaron scopes nuevos. Si Hito 1 requiere otros datos, confirmar primero fuente, finalidad, contrato y permiso: datos declarados de contacto y atributos SRCeI deben mantener procedencia y verificación distintas de CU.

### Sandbox, QA y producción

F1/F2 describen QA como el mismo ambiente limitado de Sandbox, con otro par de credenciales y RUN de prueba. Producción permite cuentas reales después de habilitación. No se documenta un catálogo ampliado de datos por pasar a producción ni una estructura userinfo diferente para QA. Solo Sandbox ha sido comprobado por nosotros; no se afirma equivalencia experimental entre los tres ambientes.

Solicitar a SGD: contrato/matriz de atributos vigente por ambiente y scope, cardinalidades/omisiones permitidas, existencia de extensiones autorizables y versión formal del manual ante la anomalía de fechas.

## Qué falta para producción

**Documentación oficial (F1/F2/F6):** habilitación de la integración por SGD, cumplimiento de identidad visual/protocolo/seguridad y gestión institucional de contrapartes. Poseer client_id/client_secret no demuestra activación.

En nuestro caso se debe resolver y acreditar:

1. Alojamiento del componente público y sitio productivo terminado, origen/Redirect/Logout registrados, DNS público y TLS confiable. El hosts/mkcert local no constituye publicación.
2. Certificación por el trámite institucional de Cerofilas/Mesa de Servicios, evidencias sanitizadas de llamadas backend y contacto administrativo para revisión en línea.
3. Confirmación de activación, aplicación/finalidad y credenciales productivas correctas, por el gestor/contraparte autorizada; su estado actual es desconocido para esta investigación.
4. Operación aprobada: secretos, permisos, auditoría de autenticación, incidentes, continuidad, privacidad/retención y evaluación de seguridad/factor adicional según el trámite. La [Guía Técnica de Autenticación](https://wikiguias.digital.gob.cl/guias/guia-tecnica-autenticacion) desarrolla esos aspectos; no se declara su validación institucional completa.

F2 exige revisar el sitio productivo y excluye desarrollo, QA y preproducción; permite coordinar videollamada si no es accesible externamente. F3 todavía admitía QA como alternativa: esa regla anterior no se aplica como requisito vigente. La separación de aplicaciones permanece: CU autentica al ciudadano en el público, no en el SIGER intranet.

## Persistencia actual inspeccionada

| Lugar | Identidad y evidencia conservadas |
|---|---|
| Sesión pública | RUN, partes de origen para la vista, nombres/apellidos y listas originales; referencia/fecha/huella de autenticación. No se retienen tokens OIDC. |
| public_delivery | owner_run para acceso por propietario; body con sobre 1.0 histórico o 1.1 estructurado, procedencia y sello público. ACK institucional cuando llega. |
| institutional_inbox | Copia íntegra validada del sobre, procedencia y sello, más comprobante institucional. |
| communication_registry | UUID/folio/fecha/canal/origen/operación y huellas; FK al inbox que contiene la instantánea. |

El RUN completo sigue siendo el campo persistido del sobre 1.1; separarlo visualmente no altera ese contrato. Los arreglos completos de nombres/apellidos sí están incluidos en dicho sobre. Los campos runNumero/runDv de sesión no crean una tabla nueva o versión de huella.

La procedencia por run/nombres/apellidos indica CLAVE_UNICA_USERINFO / IDENTIDAD_AUTENTICADA, fecha y referencia; mensaje indica CIUDADANO / DECLARADO_NO_CONTRASTADO. Firmas y huellas cubren esa evidencia y el contenido. Es una observación autenticada a una fecha, no verificación SRCeI ni prueba de otros atributos civiles.

Cada nueva clave de operación puede generar otra Comunicación para el mismo RUN. La idempotencia evita repetir una operación; no fusiona personas ni elimina ingresos legítimos de igual texto con otra clave.

No hay entidad Persona, rectificación central, política automática de eliminación o retención implementadas. El código no cifra campos de identidad en la base; la seguridad de almacenamiento/backups debe completarse en el despliegue. Caducar una sesión no elimina estas instantáneas.

## Alternativas para varias Comunicaciones de una persona

| Alternativa | Ventajas | Costos y riesgos |
|---|---|---|
| A. Instantánea por Comunicación, actual | Reconstruye qué identidad/fuente se observó en cada ingreso; encaja con firmas e integridad histórica; evita propagación de cambios entre expedientes. | Duplica datos, amplía exposición y costos de retención; consultas por persona y rectificaciones requieren tratamiento explícito. |
| B. Solo persona reutilizable | Facilita consulta conjunta y administración de datos actuales; reduce repetición de algunos atributos. | Un cambio central puede alterar la lectura histórica; sin historial no reproduce lo observado. Agrupa información y aumenta impacto de accesos indebidos o asociaciones erróneas. |
| C. Instantánea más referencia eventual | Conserva evidencia por ingreso y permite una vista institucional de persona cuando se autorice; admite comparación temporal entre CU y SRCeI sin sobrescribir fuentes. | Retiene duplicación, exige reglas de vinculación, rectificación, permisos y conservación; más complejidad y riesgo de correlación entre Comunicaciones. |

**Recomendación técnica:** mantener A ahora y considerar C como evolución condicionada a Hito 1. Una eventual referencia se resolvería dentro de SIGER, manteniendo la independencia del público. No crear ahora un maestro de personas, unicidad global por nombre/RUN o fusión automática.

Una rectificación no debe reescribir silenciosamente el texto firmado: invalidaría su integridad. Deben definirse anotación/versionado, conservación o supresión autorizada, alcance a copias/backups y evidencia mínima que corresponda. Inmutabilidad técnica no justifica retención indefinida.

Minimizar datos por finalidad, separar custodia pública transitoria de conservación institucional y restringir consultas conjuntas. Definir plazos y habilitantes con la institución; no se proponen duraciones legales. En una comparación futura, conservar fuente/fecha/nivel de cada atributo y registrar discrepancias, sin regla automática de que una fuente sobrescriba a otra.

Pendientes Hito 1: sujeto autenticado frente a otras personas mencionadas, finalidades/campos, vinculación/rectificación, permisos, retención, respaldo y resolución de discrepancias CU/SRCeI. No se implementa ninguna de esas decisiones en esta iteración.
