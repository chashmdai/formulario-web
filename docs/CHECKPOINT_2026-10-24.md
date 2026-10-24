# Continuación y recuperación — 2026-10-24

Encargo Maestro **pendiente de completar**. Repositorio `/home/nexbyteproveedor/proyectos/formulario-web`, rama `integracion-siger-linux-2026`. Se conservaron `1e6af92`, `42b142d`, `9e8eafe`, `c996b97` y todos los cambios sin commit del 23/10. No hubo push, intervención en SIGER institucional, borrado de bases/volúmenes ni investigación del fallo de Windows.

## Recuperación comprobada

- Docker reinició los servicios existentes. Se conservaron los volúmenes de MySQL, restore e initcheck. MySQL 8.4.11 completó crash recovery y quedó listo; no reportó corrupción en las últimas líneas verificadas de su log.
- `CHECK TABLE ... QUICK`: OK en las 12 tablas de `robust_public_test` y las 12 de `robust_public_local`. Esta verificación ordinaria no sustituye una auditoría exhaustiva de almacenamiento.
- Ambas bases conservaron V1–V7 exitosas y los mismos checksums. Flyway volvió a validar las siete migraciones en la base local y reportó que no había ninguna por aplicar.
- El único archivo Git pendiente con longitud cero era `scripts/qa/comparar-nna.mjs`; se recuperó desde su contenido inmediatamente anterior a la interrupción. Los demás archivos pendientes conservaron contenido. No se reconstruyó el proyecto.
- Evidencia privada sin datos personales: `.local/evidencias/recuperacion-20261024.json`.

## NNA conectado y verificado

La ruta `/?vista=formulario-nna` contiene los dos pasos del mockup, vista completa, navegación, identificación por sesión, región/comuna, nombre preferido/tratamiento, contactos condicionales y precauciones, relato, adjuntos, revisión, envío y comprobante real de custodia. La portada `/?vista=inicio` enlaza con esta ruta. Se conservan las rutas provisionales anteriores para regresión.

Solo se muestran atributos faltantes, declaraciones propias y estados públicos. La recarga restaura la proyección existente antes de identificar; no necesita volver a enviar un RUN privado ni repetir SRCeI. Una reserva de consulta vencida pasa a fallback manual desde el estado persistido; una respuesta tardía no sobrescribe ese fallback. Los campos obligatorios de personas relacionadas siguen siendo distintos de los mínimos del presentador.

La revisión conserva la captura original al iniciar el envío. Un error de red no muestra éxito: permite consultar la recepción o repetir la misma clave y el mismo cuerpo. Un recorrido recibido restaura el estado desde la API. Los antecedentes completos, fuentes, fechas y comprobantes se guardan en MySQL; el navegador no recibe el agregado privado. Sin ACK firmado no se muestra folio ni comprobante institucional.

La sesión HTTP sigue teniendo vigencia limitada y no se persiste como una sesión ciudadana permanente. La recuperación del comprobante al recargar se verificó dentro de la misma sesión. Tras reiniciar el proceso se verificó **recuperación institucional**, no una recuperación ciudadana sin credenciales de sesión.

## Laboratorio real reutilizable

HTTPS: `https://claveunica.formularioadulto.defensorianinez.gob.cl:18443/?vista=formulario-nna`.

- JAR nativo en WSL, bind `127.0.0.1`, TLS real con el certificado exacto y la CA ya confiada. Windows y Edge lo verifican sin omitir validaciones TLS. Cookie `SIGERNNA2026` separada de la del servicio existente.
- Configuración privada: `/home/nexbyteproveedor/.local/share/siger/nna-local-20261023/`. Claves, contraseñas y tokens permanecen fuera de Git. Usa `robust_public_local`, creado antes del reinicio sobre el MySQL existente, con cuentas de aplicación/migración separadas. La excepción JDBC sin TLS está limitada al loopback y esquema de laboratorio ya admitidos por las guardas; no se amplió esa excepción.
- El puerto oficial `443`, Caddy y el contenedor anterior se conservaron. El callback ClaveÚnica está bloqueado en este laboratorio NNA; no se presentan estas pruebas como una nueva verificación CU. PISEE está deshabilitado aquí. No se consultaron proveedores gubernamentales durante estas verificaciones.
- `scripts/linux/preparar-nna-local.py` solo crea un laboratorio nuevo si no existen su directorio, esquema ni cuentas. No usar para reinicializar el existente.
- Arranque: `python3 scripts/linux/arrancar-nna-local.py /home/nexbyteproveedor/.local/share/siger/nna-local-20261023`. `--migrar` ejecuta validación/migración explícita; no es necesario repetirla sin cambios de esquema.
- Detención: el mismo comando con `--detener`. Verifica PID, JAR y configuración privada antes de enviar SIGTERM; no mata otros procesos.

## ClamAV y adjuntos reales

Se instaló ClamAV 1.5.4 tras comprobar que no estaba presente, conservando el resto de la infraestructura. Servicio `clamav-daemon` activo en `127.0.0.1:13310`, dos hilos, inspección acotada, alertas por límites excedidos y registro extendido deshabilitado. Bases oficiales descargadas y verificadas por FreshClam. Respaldo de configuración: `/etc/clamav/clamd.conf.before-siger-nna-20261023`.

Pruebas ordinarias contra el proceso real y la API HTTPS:

- Texto limpio: INSTREAM OK y carga HTTP 200; bytes persistidos y descarga con SHA-256 coincidente.
- Cadena estándar de prueba EICAR: detección real y carga HTTP 400. No es malware ejecutable.
- Archivo con extensión PDF y contenido falso: HTTP 400.
- Ambos rechazos conservaron el lote previo íntegro.
- Descarga desde otra sesión: HTTP 404.

Evidencia: `.local/evidencias/adjuntos-clamav-real-20261024.json`. No se efectuaron pruebas de estrés ni cargas prolongadas.

## Persistencia y recuperación institucional

Tras reiniciar exclusivamente el JAR se recuperó una entrega persistida por la extensión autenticada, se verificaron independientemente la huella canónica, la firma Ed25519 de custodia y los bytes/SHA-256 de su adjunto. Se devolvió un fallo transitorio controlado para conservar la entrega reintentable. Evidencia: `.local/evidencias/recuperacion-institucional-lab-20261024.json`.

No se emitió un ACK institucional real en esta comprobación ni se atribuyó un folio a SIGER. Los ACK firmados válidos/ajenos/falsos, idempotencia y reservas vencidas están cubiertos por pruebas sintéticas. La ampliación necesaria del consumidor está descrita en `CUSTODIA_FORMULARIO_COMPLETO.md`; el repositorio institucional no se modificó.

## Pruebas y paridad

- Antes de la interrupción: 149 pruebas backend descubiertas, 145 correctas y 4 opcionales omitidas, en H2 y MySQL. Logs `nna-pagina-backend-{h2,mysql}.log`. La recuperación validó además las tablas y migraciones reales sin reinicializarlas.
- Tras los ajustes visuales: 45 pruebas frontend correctas, lint y build correctos. Log `nna-recuperacion-front-20261024.log`.
- Edge con TLS verificado, pruebas secuenciales a 1440×900 y 390×844: pasos 1/2, completo, contraste; React además revisión, envío con adjunto inspeccionado, custodia y recarga. Sin errores JavaScript ni desborde horizontal en los estados capturados. No se modifica la supervisión de seguridad del navegador; se espera UI disponible porque mantiene peticiones abiertas.
- Geometría y 22 capturas: `.local/evidencias/paridad-nna/`. Tras corregir textos y posición de las pantallas, coinciden fuente Cabin, ancho y posición del encabezado, hero y primera tarjeta: escritorio tarjeta x=348, y=502.16, ancho=1028; móvil x=14, y=565.34, ancho=362.
- No se afirma identidad de píxeles de todo el recorrido. Diferencias intencionales: herramientas de simulación retiradas; aviso de laboratorio y textos de envío real; estado de carga/error real; excepción para omitir nombres deshabilitada según los mínimos institucionales confirmados; custodia real en vez de recibo simulado. Se ajustaron las marcas de campos condicionales y el desplazamiento/foco entre pasos.
- Los fixtures históricos V3 continúan byte a byte iguales a los del repositorio de referencia. El chequeo de secretos no halló coincidencias con los valores reservados contrastados.

## Pendientes y siguiente paso exacto

1. **Adultos:** trasladar desde el HTML existente las tres etapas, motivos consulta/vulneración/promoción, orientación, preguntas condicionales, personas relacionadas/instituciones y ubicación/contactos. Reutilizar los controles de identificación ya persistentes y los adjuntos existentes. No rediseñar ni usar los simuladores del HTML.
2. Las reglas backend adultas están implementadas y probadas (avance descrito abajo). La recepción ampliada de adultos sigue cerrada con `formulario.adultos.habilitado=false` hasta completar React. Mantener CU autenticada, RUN declarado y atributos SRCeI separados, sin alterar V1–V7 ya aplicadas.
3. Conectar acceso adulto/aviso de privacidad al recorrido persistente y su revisión/envío; conservar el OIDC ya validado en `443`. Verificar la aplicación integrada sin reconfigurar esa infraestructura innecesariamente.
4. Ampliar matriz visual/funcional con adultos, personas múltiples y estados registrales sintéticos. Mantener separadas las pruebas sintéticas y las pruebas reales autorizadas.
5. La adaptación del consumidor institucional al documento completo y su ACK real sigue pendiente de autorización para ese repositorio; no inventar compatibilidad ni truncar datos.

Reversión local: detener únicamente el proceso NNA; cerrar sus banderas/controles conservando filas, claves y adjuntos. Para revertir ClamAV, detener su servicio antes de restaurar el respaldo y recargar sus unidades; no desinstalar ni borrar firmas/configuración de forma automática. No borrar el esquema local, volúmenes o información recibida. El servicio anterior en `443` no depende del laboratorio NNA.

## Avance posterior: backend adulto

`ValidacionAdultos` valida consulta general, los cuatro perfiles de entrevista, estadísticas con las dos confirmaciones, vulneración con riesgo/relación/parentesco, y promoción con o sin organización. Empleo se deriva al canal oficial y no se recibe como postulación. Se rechazan campos inactivos, tipos no admitidos, contactos/ubicaciones/fechas inválidos y narrativas mayores de 5000 caracteres.

La custodia selecciona únicamente el presentador y las personas referenciadas en la captura activa; no incluye datos de tarjetas retiradas ni de otros motivos. Valida NNA afectados con algún antecedente o declaración de datos desconocidos, adultos relacionados opcionales, personas señaladas con algún dato/alias/vínculo e instituciones declaradas. No convierte estas personas en identidades autenticadas. No cambian el esquema firmado ya recibido ni las migraciones.

Pruebas nuevas: 18 escenarios/casos en `AdultosFormularioJdbcTest`, incluidos procedencia mixta, CU con prueba de sesión, RUN declarado, múltiples personas, instituciones, descarte de campos inactivos, custodia y ACK adulto sintético. Regresión funcional H2 y MySQL: 164 pruebas descubiertas, 163 correctas, una opcional omitida. Por precaución del anfitrión se excluyeron explícitamente las clases `Carga*Test`; no se ejecutó una campaña de carga. Logs `adultos-regresion-{h2,mysql}-20261024.log`.

`formulario.adultos.habilitado` es una bandera adicional, cerrada por defecto y sin modificar la configuración privada del laboratorio. La API de capacidades comunica su habilitación efectiva. **Próximo trabajo:** componentes React adultos y tarjetas de personas, conectados a estos DTO/validadores y al aviso de privacidad; después pruebas funcionales y visuales antes de habilitar la bandera.

## Avance posterior: identificación React de terceros

`PersonIdentity.tsx` implementa el bloque reutilizable de identificación de NNA afectados, adultos relacionados y personas señaladas. Usa los IDs/clases de las tarjetas del mockup, RUN/DV separados, pegado de RUN completo, validación módulo 11, restauración por sesión y reinicio explícito al cambiar RUN. No solicita edad adulta, fecha de nacimiento o sexo que el mockup no pide a terceros. El backend tampoco los ofrece como atributos faltantes de estas tarjetas; mantiene internamente los recuperados.

La consulta completa no dibuja campos recuperados, la parcial ofrece los faltantes y el fallback permite declarar lo conocido sin imponer nombres al tercero. Rechaza cualquier proyección que atribuya CU autenticada a una tarjeta. Errores de carga/guardado permiten reintentar; una declaración escrita no se pierde si falla su persistencia. Las ubicaciones, parentescos, alias y demás metadatos se conectarán desde el contenedor de tarjetas.

Nueve pruebas nuevas; regresión frontend final: 54 correctas, lint y build correctos (`personas-regresion-front-20261024.log`). Los 18 casos JDBC adultos pasan de nuevo en MySQL con las comprobaciones adicionales de atributos no solicitados (`personas-backend-mysql-20261024.log`). Este componente todavía no está montado en una pantalla adulta: no se declara terminado ese recorrido.

La aplicación en `18443` sigue ejecutando el JAR verificado NNA. El código adulto posterior está conservado en Git, pero no se habilitó su bandera ni se desplegó sobre `443`. No volver a ejecutar `.local/generar-nna.py`: es un generador inicial que sobrescribiría ajustes ya verificados. Para los adultos trasladar únicamente sus nodos desde el HTML original, manteniendo los componentes NNA existentes.
