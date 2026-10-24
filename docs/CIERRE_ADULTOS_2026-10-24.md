# Cierre funcional local de adultos — 24 de octubre de 2026

Rama `integracion-siger-linux-2026`, repositorio `/home/nexbyteproveedor/proyectos/formulario-web`. Se conservaron todos los cambios pendientes y los commits anteriores, incluidos `1e6af92`, `c996b97`, `08f5031`, `e7a0ed4` y `9f32383`. Este cierre comprende el Formulario Público adulto en laboratorio; no declara terminado el Encargo Maestro, una certificación gubernamental ni el registro institucional de estas comunicaciones.

## Resultado implementado

- `AdultStepOne/Two/Three`, `AdultFlow`, `AdultPersonCard`, `ConsultationGuidance` y `ReviewAdult` trasladan las secciones del mockup `ddn-formulario@13f9cf6`. Se conservan Cabin, SVG, CSS, etapas, preguntas, alternativas, regiones/comunas, vista completa, temas y escala. Se corrigieron los atributos `type` de RUN/DV y la ubicación del estado junto al botón, que afectaban su presentación.
- La portada acepta y registra en servidor la versión del aviso y el tipo de recorrido antes de continuar. El callback oficial retoma ese recorrido en la raíz; no recibe identidad desde parámetros de URL. Se mantiene el acceso provisional anterior mediante `?vista=provisional`.
- Consulta: orientación con aceptación explícita, consulta general, entrevista con cuatro perfiles y campos condicionales, estadísticas tras confirmar revisión de fuentes. Empleo presenta el canal oficial y no admite envío como postulación.
- Promoción: organización sí/no, entidad condicional, actividad, público, fecha, ubicación y relato. Vulneración: riesgo y orientación urgente, relación/parentesco, ubicación y momento, NNA afectados, datos desconocidos, adultos relacionados y personas/instituciones señaladas. Las tarjetas retiradas y los campos de motivos inactivos quedan fuera de la custodia.
- RUN declarado obligatorio, separado de DV y validado módulo 11; nombres declarados y atributos faltantes usan la identificación persistente existente. CU utiliza exclusivamente la identidad autenticada y la evidencia conservada por el backend. Las personas relacionadas no se autentican por consultar un RUN. No se muestran valores SRCeI en revisión ni se afirma su procedencia desde el cliente.
- Revisión y envío usan el backend real. Un intento conserva clave y cuerpo para reintentar sin duplicar; una respuesta perdida permite consultar la recepción. La recarga restaura el comprobante de la sesión. El formulario no inventa folios ni ACK: muestra custodia pública y registro SIGER pendiente.
- Adjuntos utilizan el almacén privado y ClamAV existentes; un rechazo bloquea la revisión, se puede retirar el lote sin perder el formulario y los reemplazos fallidos conservan el lote anterior. Se mantienen límites, hash, autorización por sesión y descarga protegida.
- El cierre de sesión elimina el nombre de cookie configurado, en vez de asumir siempre `SIGERSESSION`; el laboratorio usa `SIGERNNA2026`. Se conserva POST con CSRF, invalidación de sesión y logout oficial del proveedor.

## Verificaciones y alcance de la evidencia

| Evidencia | Resultado | Naturaleza |
|---|---|---|
| Frontend, 13 archivos de prueba | 82 correctas; lint y build correctos | Sintética, incluye NNA y adultos |
| Backend H2 | 165 descubiertas, 164 correctas, 1 opcional omitida | Funcional, sin `Carga*Test` |
| Backend MySQL | 165 descubiertas, 164 correctas, 1 opcional omitida | Funcional, esquema de pruebas; sin carga |
| Navegador previo, 1440×900 y 390×844 | Módulo 11, empleo, estadísticas, entrevista/otro, recepción cerrada, PDF inválido que bloquea revisión | Frontend y backend locales reales; SRCeI desactivado |
| Navegador de recepción | Consulta, promoción y vulneración en ambos tamaños: 6 comunicaciones, adjunto TXT limpio, revisión, envío, comprobante y recarga | HTTPS/ClamAV/MySQL reales, datos sintéticos |
| Seguridad de esos 6 envíos | Repetición idéntica conserva referencia; cambio con la misma clave devuelve 409; otro contexto no obtiene el adjunto (404); sin errores JS ni desborde en estados capturados | HTTP local real |
| MySQL independiente | Las 6 capturas coinciden íntegramente con lo enviado; huellas canónicas de contenido/procedencia, firma Ed25519 y bytes/SHA-256 de adjuntos correctos; ningún tercero retirado incluido | SQL y OpenSSL, sin publicar datos |
| CU Sandbox | Autorización/callback/userinfo, identidad de solo lectura, pasos y revisión en escritorio y móvil; 1 comunicación CU enviada desde móvil y verificada en MySQL | Proveedor real, identidad de prueba autorizada |
| Logout CU | Ingreso acotado adicional, POST de cierre, retorno `/?session=closed` y sesión anónima, sin errores JS | Proveedor real; sin nueva comunicación |
| Presentación final | Claro, oscuro, contraste y escala 130%, ambos tamaños, sin desborde | API controlada explícita; no crea recorridos ni consulta proveedores |
| Contrato histórico V3 | Fixtures byte a byte iguales al repositorio institucional de referencia y regresiones correctas | No cambia sus rutas, DTO, firmas ni cola |

Las pruebas de CU se limitaron al enlace necesario con el formulario integrado. Los primeros verificadores tuvieron errores: selector de botón de ingreso, espera de la ruta intermedia `/logout` y eventos de ingreso incompletos. El verificador corregido utiliza teclado en los controles oficiales y espera el retorno final. Se conserva por separado la evidencia del recorrido CU recibido y la del cierre correcto; los intentos fallidos de QA no se contabilizan como fallos funcionales resueltos del proveedor.

La revisión CU en escritorio y móvil se efectuó sobre **el mismo recorrido autenticado**; se envió una sola comunicación al terminar la revisión móvil. No se afirman dos envíos CU independientes. Las pruebas de ACK adulto, reservas, fallos, procedencia mixta y SRCeI completo/parcial/vacío/indisponible son sintéticas; esta ejecución no consultó PISEE/SRCeI real.

Evidencias privadas, excluidas de Git: `.local/evidencias/adultos-cierre-{h2,mysql}.log`, `adultos-front-final.log`, `adultos-empaquetado.log`, `adultos-navegador/{preflight,recepcion,mysql,mysql-cu,claveunica,claveunica-logout,mockup,presentacion-controlada}.json` y PNG de estados. No copiar cookies, credenciales, identidades o cuerpos completos a informes públicos.

## Paridad observada y diferencias justificadas

Referencia y React se recorrieron en Edge con fuentes cargadas y TLS validado. En las capturas de recepción, la primera tarjeta coincide en posición/ancho/fuente: escritorio x=348, y=421.96875, ancho=1028; móvil x=14, y=510.75, ancho=362; Cabin 16 px. Se compararon también las etapas 2/3 del mockup y se inspeccionaron las capturas reales y las correcciones finales de identificación.

Se mantienen diferencias necesarias: herramientas/identidades de simulación retiradas, indicación de laboratorio, procedencia auténtica, carga/errores reales, estado de recepción cerrada y custodia sin folio simulado. La altura cambia por esos textos y por retirar las herramientas de demostración. La vista final de identificación se comprobó con API controlada separada, sin volver a consumir la cuota de recorridos. No se declara igualdad de píxeles de todos los estados ni auditoría de accesibilidad exhaustiva con lectores de pantalla. Se verifican etiquetas, controles nativos, foco, resumen de errores, diálogos y navegación mediante pruebas funcionales.

## Entorno integrado y reversión

El servicio nativo existente, sobre el esquema persistente `robust_public_local`, sirve ahora adultos y NNA en `127.0.0.1:18443`. Se reutilizan Java, Docker, MySQL, ClamAV, certificados, confianza y entradas hosts ya preparados. V1–V7 permanecen aplicadas y sin modificaciones; no se recrearon bases ni volúmenes. El JAR comprobado tiene SHA-256 `4cb8a742c29b66caadb7d82861491587d6a67cadce9a17968e05b8d1eb26102d`.

Era necesario que el hostname del callback llegara al nuevo formulario y a la misma sesión. `infra/compose.formulario-https.yml`, sobre el compose anterior, dirige el Caddy existente desde loopback 443 hacia HTTPS loopback 18443, verificando la CA y el nombre del upstream. El Caddyfile usa la [configuración oficial de confianza TLS del proxy](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy); no se omite validación TLS. El servicio antiguo sigue intacto en `127.0.0.1:18080` para reversión.

En red host el enlace de 443 requirió UID 0 con grupo 1000 y únicamente `NET_BIND_SERVICE`; se conservan `cap_drop: ALL`, `no-new-privileges`, filesystem de solo lectura, límites y logs desactivados. El proxy solo monta el certificado de sitio, su clave y la CA pública, individualmente y de solo lectura. Esos tres archivos pasaron de 0600 a 0640, propietario/grupo 1000; el directorio privado sigue 0700. No se monta la clave de la CA. Se comprobaron Windows, WSL y Edge sin omitir errores de certificado. No se cambiaron hosts, DNS público ni el almacén de confianza en esta ejecución.

Configuración reservada: `/home/nexbyteproveedor/.local/share/siger/nna-local-20261023/application.properties`; respaldo previo `application-before-adultos-integrados.properties`. Se reutilizaron las credenciales Sandbox existentes. `formulario.adultos.habilitado=true` se preparó manteniendo cerrada la recepción efectiva; se abrió el control operativo **después** de las regresiones y verificaciones previas en navegador. El callback se habilitó después del preflight bloqueado, preservando exactamente `/redirect`, `/logout` y el origen oficial. PISEE continúa desactivado en este laboratorio integrado.

Para retomar el JAR tras un reinicio, comprobar antes proceso/puerto y salud MySQL. El script `scripts/linux/arrancar-nna-local.py <directorio_privado> --permitir-cu-sandbox` exige callbacks/origen exactos y solo arranca el JAR ya compilado. `--detener --permitir-cu-sandbox` verifica PID, JAR y ubicación de configuración antes de terminar exclusivamente ese proceso. No usar `--migrar` sin necesidad: las migraciones ya están validadas.

Reversión, sin pérdida de datos:

1. Cerrar aceptación mediante el control operativo autenticado y bloquear callback/bandera adulta en la configuración privada, conservando posteriores ajustes. Reiniciar solo el JAR con el script y el opt-in correspondiente al estado configurado.
2. Para restaurar el encaminamiento anterior: `SANDBOX_TLS_DIR=/home/nexbyteproveedor/.local/share/siger/sandbox-https docker compose -f infra/compose.sandbox-https.yml up -d --no-deps --no-build caddy`. Omitir el override integrado. Ejecutar con los permisos Docker normales disponibles. El compose base restaura el proxy UID 1000 y su red/puerto anteriores hacia la aplicación Docker.
3. Una vez restaurado el proxy anterior, devolver a 0600 únicamente `site.pem`, `site-key.pem` y `rootCA.pem` del directorio privado TLS si se desea recuperar los modos originales. No restaurar todo el directorio ni tocar otros certificados.
4. Conservar comunicaciones, claves, adjuntos, esquemas y volúmenes. La reversión de ruta no traslada ni elimina las nuevas custodias. El reinicio del JAR invalida sesiones HTTP en memoria; las comunicaciones persisten y siguen recuperables institucionalmente.

## Reproducibilidad y siguiente unidad

Los scripts QA usan Edge instalado y Node Windows con `--use-system-ca`, sin `ignoreHTTPSErrors`. `scripts/qa/cerrar-adultos.mjs` requiere `ADULT_PHASE=preflight` con recepción cerrada, o `ADULT_PHASE=recepcion` después de abrirla. La segunda fase crea **seis** comunicaciones; no repetir sin necesidad. `referencia-adultos.mjs` solo navega el HTML original en 8181. `adultos-presentacion.mjs` intercepta explícitamente su API para cotejar presentación, sin crear registros reales. `adultos-claveunica.mjs` requiere `CU_TEST_CREDENTIALS` apuntando a un archivo privado autorizado fuera de Git; `CU_LOGOUT_ONLY=1` limita la comprobación al cierre sin crear otro recorrido. No automatizar campañas repetidas contra proveedores.

Se respetó la cuota de 12 inicios por ventana/IP; no se borraron contadores ni se alteró la IP para evitarla. Una repetición inmediata puede recibir 429 hasta renovar su ventana. Las credenciales permanecen fuera de Git, imágenes y evidencias.

**RUN → SRCeI: implementado frente a pendiente.** Existen el cliente Java y el Nodo Linux verificados previamente, consulta backend por recorrido/persona/sesión, RUN/DV separados y módulo 11, allowlist privada DEV, límites contra enumeración, control de respuestas tardías, proyección pública sin valores registrales, fallback y procedencia individual persistidos. Los estados completo/parcial/vacío/error/timeout están cubiertos sintéticamente. CU deriva el RUN de la sesión; NNA sin RUN no consulta al proveedor.

La interfaz declarada y las tarjetas de terceros todavía ejecutan la revisión al pulsar **Revisar RUN**; no está conectado un disparador automático al completar RUN y DV. La siguiente unidad deberá añadir ese disparador con deduplicación y protección frente a cambios/respuestas tardías, reutilizando el cliente existente; habilitar la configuración DEV autorizada y su allowlist fuera de Git en el backend integrado; comprobar red/normalización y realizar una única consulta real autorizada con evidencia privada. Esta unidad adulta no empieza otro desarrollo de PISEE ni declara probada esa consulta desde el formulario.

**SIGER institucional:** el V3 histórico se preserva, pero el consumidor institucional aún debe implementar la extensión optativa de documento completo, adjuntos y ACK descrita en `CUSTODIA_FORMULARIO_COMPLETO.md`. No se modificó ese repositorio. Las siete comunicaciones adultas de esta ejecución permanecen custodiadas y pendientes de registro SIGER; no se atribuyen folios ni ACK reales. La campaña de estrés, resiliencia y hardening preproductivo sigue fuera de esta unidad.
