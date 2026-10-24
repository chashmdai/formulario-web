# Informe de ingeniería multimedia · 24/10/2026

## Entrega y alcance

Rama local `multimedia-adjuntos-2026-10-24`, creada desde `origin/main@c1e4ead` tras fetch y comprobación de árbol limpio. Commits principales: **c67e359** (almacenamiento, transferencia, validación, contrato y UI), **74e4b6e** (operación, respaldos y QA). El commit que contiene este informe agrega la prueba final de concurrencia y el checkpoint; consultar `git log -3` para su identificador. No se hizo push, merge en main ni cambio en SIGER institucional.

Se implementó una capacidad optativa y verificable de laboratorio. No se habilitó multimedia sobre el baseline ni se modificó su estimación operativa aproximada del 96 %. La entrega no constituye certificación de seguridad/capacidad ni autorización productiva.

- Nuevos originales en almacenamiento privado durable por UUID; MySQL conserva metadatos, propietario, estados, cuotas, hashes y auditoría. Lectura común FILE/MySQL; sin migración de binarios históricos.
- Subida raw secuencial de 8 MiB, hashes por bloque/completo, reintentos, pausa y reanudación dentro de sesión vigente. Duplicados por hash/tamaño dentro del mismo recorrido. El lote confirmado anterior se preserva ante fallos.
- Análisis fuera de transacciones, ClamAV completo real y worker aislado sin red ni acceso al almacén global. No hay ejecutables/macros, conversiones Office ni procesadores externos.
- UI compartida para adultos y NNA: elegir/arrastrar, retirar individualmente, progreso durable individual/global, pausa, reintento, recuperación de cargas de la sesión y preview seguro. La falla del adjunto no borra el relato. La revisión espera a que no queden archivos pendientes.
- Imágenes y primera página PDF mediante PNG derivado; audio/video mediante controles nativos con Range. Word/otros Office mediante descarga controlada. Originales y hashes se conservan.
- Documento **1.1** para nuevas capturas FILE, con opt-in de consumidor; los descriptores mantienen sus claves. Documento firmado **1.0**, canonicalización, UUID, idempotencia, reservas y ACK históricos se conservan. No se elimina evidencia al recibir ACK.
- Respaldos offline, restauración exclusivamente en destinos nuevos, limpieza controlada de incompletos y sondeo de cuotas/capacidad. No hay eliminación automática de originales ni notificaciones programadas.

Ver [ADR](ADR_MULTIMEDIA_2026-10-24.md), [política](POLITICA_MULTIMEDIA_2026-10-24.md), [operación](OPERACION_MULTIMEDIA.md), [contrato de entrega 1.1](../contratos/formulario-completo-multimedia-v1.1.md) y [visor institucional](VISOR_INSTITUCIONAL_MULTIMEDIA.md).

## Resultados de verificación

| Verificación | Resultado y alcance |
|---|---|
| React | 160 pruebas correctas; 18 archivos de pruebas. Incluye ocho casos nuevos del cargador; conserva regresiones adultos/NNA/contactos/identidad/revisión/recepción. |
| Lint y build | Correctos en entorno nativo y Docker; hashing incremental en Web Worker independiente. |
| Backend H2 | 199 descubiertas, 197 ejecutadas correctas y dos omisiones opcionales. Sin fallos. |
| Backend MySQL | 199 descubiertas, 197 ejecutadas correctas y dos omisiones opcionales, sobre `robust_public_test`. Sin fallos. |
| Worker Java real | Una prueba opt-in adicional correcta, con ClamAV e inspector reales, imágenes/PDF/Office/audio/video y rechazo falsificado. |
| Imagen Docker | `siger-formulario:multimedia-revision` construida con lint, 160 React y batería backend de 198/196 ejecutadas/2 omitidas. Corresponde al código c67e359, anterior únicamente a la prueba adicional final de concurrencia. No se desplegó sobre el baseline. |
| Inspector real | 30 fixtures diminutos: 17 formatos aceptados y 13 casos rechazados. Incluye falsificación/corrupción, PDF activo, VBA, travesía, relaciones externas, enlaces falsificados, fórmulas XLSX y OLE en ODT. |
| ClamAV real | PNG limpio, EICAR benigno detectado y EICAR al final de ZIP de 12 583 210 bytes detectado; no hay truncamiento a los 10 MiB del scanner anterior. |
| Transferencia real | 16 777 223 bytes, tres bloques (8 MiB, 8 MiB y 7 bytes), replay idempotente y hash completo correcto. Reanudación dentro de sesión vigente, análisis y descarga por flujo. |
| Custodia 1.1 | Una única recepción sintética necesaria en esquema aislado; repetición del mismo envío, reserva opt-in y recuperación Range correctas. El consumidor sin opt-in no la recibe. |
| ACK | Doble institucional criptográfico: confirmación repetida de documento 1.1, cuerpo firmado intacto y original conservado. No se afirmó registro SIGER real. |
| Navegador integrado | Tres recorridos NNA representativos con servicios multimedia reales: escritorio claro 1440×1000, móvil oscuro y contraste 390×844, texto al 130 % y movimiento reducido. Cinco tipos representativos, hashes, imágenes/PDF, audio/video, Word descarga, rechazo sin perder relato, Range 206, CSRF 403 y acceso de otra sesión 404. Sin errores JS/desborde horizontal. No se enviaron Comunicaciones desde esas pruebas visuales. |
| Concurrencia controlada | Un análisis bloqueado mediante latch: el segundo recibe 429, cancelar no publica ni borra el lote anterior, el temporal se retira y la cuota se libera al finalizar. No es una prueba de estrés. |
| Fallos sintéticos | Hash/cuerpo/orden incorrectos, tipo falso, antivirus caído, cancelación, archivo ausente/alterado, sesión cruzada, cuota/sin espacio, restauración del servicio, revisión optimista y límites escalados. No se llenó físicamente el disco. |
| Respaldo | Cinco pruebas Python de controles, más respaldo/restauración reales. Se rechazó respaldar con Spring activo antes de crear SQL. |
| Caddy | Fragmento de límite 8 MiB validado con Caddy 2.11.7. No se modificó el proxy del baseline ni se probó Cloudflare. |

Omisiones de la batería ordinaria: `RecuperacionProcesoTest` heredado y `MultimediaWorkersRealTest`. El segundo se ejecutó separadamente con opt-in real. Además se reinició realmente Spring multimedia y se verificó la recuperación institucional de la custodia y sus 16 MiB + 7 bytes sin sesión ciudadana, sin otra recepción. Las omisiones se conservan visibles; no se suman estas evidencias como porcentaje de cobertura.

## Restauración y preservación

El snapshot privado restaurado contiene **57 archivos de almacén** (originales, derivadas y temporales), **32 referencias FILE**, **un BLOB histórico sintético** y **una custodia sintética**. Destino nuevo `robust_public_media_restore_20261024` y directorio privado nuevo; no se sobrescribió el origen. Todos los hashes se verificaron, incluyendo originales validados no incorporados, derivadas y BLOB. Custodia, adjuntos, cargas, bloques y Flyway tenían CHECKSUM idéntico al origen al verificar la restauración. La consulta institucional posterior al reinicio volvió a validar el documento firmado y recuperó el original íntegro.

Las verificaciones visuales finales posteriores al snapshot dejan **47 referencias FILE, un BLOB sintético y una custodia** en el laboratorio multimedia. La reserva de extracción puede renovarse durante QA; no es un cambio del cuerpo firmado. El respaldo y la base restaurada se conservan para revisión. Las claves de firma/confianza están fuera del respaldo documental y deben custodiarse/restaurarse por separado.

El baseline conserva **13 custodias, 13 adjuntos y siete migraciones**, con los mismos CHECKSUM protegidos. No se recrearon sus bases, volúmenes o servicios. Su HTTPS continúa respondiendo 200. Los cambios previos en tablas de borradores/sesiones se distinguen de esas tablas protegidas; no se presenta una comparación de todo el laboratorio como si fuese inmutable.

Durante la primera compilación se reutilizó por error la ruta del JAR activo. Se restauró inmediatamente un JAR reconstruido desde el código exacto de main y su misma configuración; desde entonces todos los builds multimedia usan salida aislada y una copia privada de ejecución. La comprobación final conserva el SHA-256 de ese baseline restaurado; ver operación y evidencia privada.

## Evidencias locales

Bajo `.local/evidencias` (excluido de Git):

- `multimedia-final-h2.log`, `multimedia-base-regresion-mysql.log`, `multimedia-front-regresion.log`, `multimedia-front-build.log`, `multimedia-java-real.log`, `multimedia-docker-build.log`.
- `multimedia-inspector-real.json`, `multimedia-formatos-final.log`, `multimedia-clamav-real.json`, `multimedia-caddy-validate.log`.
- `multimedia-restauracion.json`, `multimedia-controles-respaldo.json`, `multimedia-recuperacion-proceso.json`, `multimedia-estado-final.json`.
- `multimedia-navegador/resultado.json` y doce capturas de adjuntos, imagen, PDF y video en los tres modos. Se inspeccionaron visualmente las capturas, incluyendo el reproductor móvil ampliado.

JAR privado probado: SHA-256 `24ccc1674bc3be3168158997a3d4c8665493933a14febc96ff51945230381d27`. Imagen de revisión: `sha256:50571033617db67e785739318d590f28b928c68d6065f20104063f2f0193c599`. La evidencia contiene solo resultados agregados o contenido sintético; SQL, cookies, configuraciones, claves y respaldos permanecen fuera de Git/imágenes. Escaneos contra secretos institucionales existentes y los cinco valores privados del nuevo laboratorio: sin coincidencias en los commits preparados.

## Pendientes y condiciones de habilitación

1. **Sesión tras reinicio:** MySQL/archivos sobreviven y la recuperación institucional funciona. Tomcat pierde la sesión ciudadana y la cookie anterior devuelve 404. No existe recuperación de borradores entre sesiones; no se serializaron tokens OIDC ni se añadió una capacidad alternativa de identidad. Resolver este flujo antes de una apertura amplia que prometa esa continuidad.
2. **Capacidad máxima:** 12 archivos/250 MiB por lote y los techos por categoría están implementados/configurados. No se probaron físicamente lotes de 250 MiB, videos de 150 MiB, concurrencia sostenida ni condiciones reales de una red móvil publicada. Calificar esos máximos cuando se resuelva la inestabilidad del anfitrión. Los plazos de análisis pueden rechazar un archivo complejo por debajo del techo nominal.
3. **Formatos:** DOC binario y HEIC/HEIF siguen deshabilitados. XLSX solo admite valores sin fórmulas/conexiones; Office no tiene conversión ni validación exhaustiva de todas las suites. PDF ofrece primera página; no reproducción de todas las páginas. Codecs fuera del perfil se rechazan; el navegador puede necesitar descarga.
4. **Operación:** comprobar proxy/Cloudflare publicado, actualización de firmas/decodificadores, respaldo protegido fuera del anfitrión y recuperación de claves en otro equipo. El respaldo probado es local y offline; no se implementó cifrado/copia externa/notificación automática. Un escritor por almacén; no hay HA/horizontalidad declarada.
5. **Institucional:** revisar esta rama y adaptar el consumidor explícitamente a documento 1.1, streaming, archivo único por evidencia y permisos del futuro visor. Acordar retención y transferencia definitiva. No se elimina evidencia por ACK ni se modifica ese repositorio en esta unidad.

No hubo consultas gubernamentales, pruebas de estrés, producción ni push. Se entrega la implementación revisable con sus límites comprobados; no se declara terminado el Encargo Maestro ni certificada la ampliación.
