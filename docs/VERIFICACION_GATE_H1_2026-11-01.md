# Evidencia de preparación — GATE H1, 01/11/2026

## Alcance y procedencia

Fuente inicial `943b0875d902556c4e842f58030e0f17486b719b`, rama `gate-h1-preparacion-despliegue-2026-11-01`. El commit candidato es el que incorpora este informe; obtener SHA exacto con `git rev-parse HEAD` en el checkout aprobado y comprobar origin/main. No requiere contexto del chat: [traspaso](TRASPASO_DDN_UBUNTU_GATE_H1_2026-11-01.md), [instalación](INSTALACION_UBUNTU_DOCKER.md), [integraciones](INTEGRACIONES_UBUNTU.md).

**Pruebas ejecutadas en WSL Ubuntu24.04 de DDN-TEST-SIGER, no en DDN-UBUNTU.** No se accedió al destino ni se modificó SIGER/WindowsMySQL80, claves/configuración/volúmenes QA. No consultas gubernamentales, envíos ciudadanos, túneles, DNS ni habilitación pública.

Informe recibido `Informe_Estado_DDN-UBUNTU_2026-11-01.md`, SHA256 `4f4ad5980bc1740ab8547f563e344832ab987fdb55b48ab7b166351023fb4ff5`. Hechos de ese documento se atribuyen al reporte, no a una inspección remota propia. La futura VM Windows de SIGER sigue sin datos finales por desarrollo en curso; no se inventó una tercera máquina existente.

## Defectos corregidos

1. Dockerfile copiaba únicamente territorio.json al contexto Maven; paises.json quedaba ausente aunque el POM lo incluye. Ahora copia ambos y verifica las tres entradas obligatorias del JAR: catálogos y static/index.html.
2. Scanner clásico ClamAV solo admitía loopback. Host configurable para la red privada Docker, manteniendo constructor y comportamiento loopback anterior, límites/errores/INSTREAM. Pruebas de hostname y transporte.
3. Migrador solo recibía password por variable de entorno. Soporta archivo secreto montado; rechaza doble fuente, ausencia, archivo inválido/excesivo/vacío/multilínea. Conserva compatibilidad del mecanismo anterior.
4. Compose integrado comprueba salud real. Se corrigieron dos fallos detectados en ejecución: newline del healthcheck inspector y uso de clamdscan ausente en la imagen. Antivirus usa PING TCP privado; inspector acredita Landlock3/seccomp por socket.
5. Caddy oficial con file capability no ejecuta con cap_drop ALL. Se reutiliza el wrapper existente sin file capabilities de infra/sandbox/Dockerfile, sin agregar privilegios.
6. Límite8MiB se aplica a ruta real cargas/bloques mediante handles exclusivos; límite26MiB general no lo reemplaza.
7. Harness multimedia admite directorio de evidencia externo para no sobrescribir resultados anteriores.

Ningún cambio en React/recorridos, ClaveÚnica/OAuth, PISEE/cliente SRCeI, V1–V12, canonicalización o contratos1.0/1.1. No migraciones nuevas.

## Pruebas locales

| Batería / verificación | Resultado y alcance |
|---|---|
| Docker build multietapa React+Spring/JRE25 | Correcto; lint, suite React y build incorporados |
| Frontend | 212 casos del baseline; fuente frontend sin cambios en H1, suite ejecutada dentro del build |
| Backend H2 completo sin carga | 366 ejecutadas:364 correctas,2 omisiones opcionales,0 fallos/errores |
| Python | 41 correctas:backup9,medición7,runtime19,preparaciónUbuntu6 |
| MySQL8.4.11 nuevo aislado | Inicialización de volumen propio, doce migraciones V1→V12 y repetición: valida12, versión12, sin nuevas migraciones |
| Privilegios runtime | Solo DELETE/INSERT/SELECT/UPDATE sobre formulario_publico |
| TLS MySQL | JDBC VERIFY_IDENTITY real con Java25; cliente confirma TLSv1.3/TLS_AES_128_GCM_SHA256; hostname erróneo rechazado; sinTLS rechazado1045(REQUIRE SSL), require_secure_transport=ON |
| HTTPS Caddy→Spring | Cadena/hostname verificados; index y dos referencias JS/CSS servidas idénticas a entradas del JAR |
| Catálogos empaquetados | Bytes idénticos a fuentes; territorio SHA256191621ac1fbbe69e7d45eb069b193c76493b4373ad187267b5339a76c98ae66b; países0cc7d5c8eb3054cde6cf11c35b124facc58a1dbb793096673cbd1666f0e4bd26 |
| TLS origen negativo | HTTPS por127.0.0.1 con CA correcta pero SAN incorrecto:curl60 |
| Salud integrada | MySQL, app, ClamAV e inspector saludables; proxy sirve frontend/API |
| Perfil cerrado | revision0,aceptarNuevos=false,recuperar=false,SOLO_CONSULTA. Capacidades:adultosfalse,NNAfalse,multimediatrue |
| Observación sin mantenimiento | Estado operacional autenticado funciona; custodia503 se informa como mantenimiento deshabilitado, sin activar nada |
| Workers recién construidos | Inspector real:17 formatos sintéticos aceptados y13 inválidos/activos/corruptos rechazados |
| ClamAV real | PNG limpio; EICAR benigno detectado; EICAR al final de ZIP12583210bytes detectado |
| Scanner Java entre contenedores | Conecta antivirus:3310:limpio aceptado/EICAR rechazado |
| Backup/restore | MySQLV12+archivo sintético110592bytes:manifest/hashes/conteos concordantes en base/directorio NUEVOS.0 Comunicaciones creadas |
| Revisión documental | Enlaces relativos, scripts/clases/rutas, sintaxis Python, Compose y Caddy comprobados; git diff --check correcto |
| Migraciones/contratos | Diff contra baseline vacío en db/publico y contratos; checksums/fuentes conservados |

Dos omisiones H2 existentes: `MultimediaWorkersRealTest` requiere MEDIA_REAL_CHECK; `RecuperacionProcesoTest` requiere recuperacion.real. No fueron deshabilitadas para H1. Los workers se verificaron por separado mediante harness Python contra los procesos reales recién construidos; no afirmar que se ejecutó esa clase Java opt-in ni una caída real de MySQL.

**No se ejecutó de nuevo la batería completa JDBC sobre MySQL.** H1 verificó migraciones, JDBC TLS, arranque del backend y backup/restore sobre el nuevo MySQL. La batería general fue H2. No se conectó ningún test a la base QA compartida13306.

## Imágenes y evidencia privada

Se construyeron desde Dockerfile las cuatro imágenes locales `ddn-formulario:h1-validacion`, `ddn-inspector:h1-validacion`, `ddn-clamav:h1-validacion` y `ddn-caddy:h1-validacion`. Sus tags no son versiones productivas; la instalación futura usa SHA aprobado/digest y etiqueta OCI de revisión. Bases oficiales Node/Maven/JRE fijadas por digest en Dockerfile; apt e imágenes auxiliares se resuelven según versiones y se registra el ID al instalar.

Proyecto sintético `siger-h1-20261101`, puerto loopback26443, volumen propio y árbol privado nuevo fuera de Git. Credenciales y confianza ACK sintéticas explícitas; nunca se enviaron a proveedores. Evidencias/PKI/SQL/manifiesto/restore se conservan únicamente en directorios privados de laboratorio, no se publican. Tras verificar se detienen solo sus contenedores; no se borran volumen ni evidencia.

Los scripts usados se encuentran en `scripts/qa/probar-inspector-multimedia.py` y `probar-clamav-multimedia.py`. Variables: MULTIMEDIA_SYNTHETIC_DIR, MULTIMEDIA_INSPECTOR_SOCKET, MULTIMEDIA_AV_SOCKET y MULTIMEDIA_EVIDENCE_DIR externo; AV requiere --autorizar-eicar. Generador reproducible `scripts/qa/generar-multimedia-sintetica.py` usa ffmpeg/Python dentro de la imagen inspector. Ejecución secuencial, archivos pequeños; máximo ejercitado en AV~12MiB, no calificación150/250MiB.

JAR final local verificado: SHA256 `a73a8d31863ffecf939bd962768aa71448a53cfdadb816204b92a8b17ab68dd8`. Copia de prueba fuera de Git; no sustituye el JAR activo QA.

## Preservación QA

Instancia original mantiene PID10282 y JARinmutable SHA256 `b7db05d3fa774c7c3959e7cdae7c80b6a2d3282f34f63cbd2ef4163e1ade38da`. Configuración misma huella `67eeda60f8a3e8d2a6aa5190395e2058605188b4bdd0e4e4fe5c92bfa91c7a66`. Consulta desde Windows a `https://formulario.defensorianinez.gob.cl/api/session` retorna200. MySQL y Caddy actuales permanecen activos; no reinicio/actualización de esa QA. Integración automática con SIGER preservada, sin reclamar/reservar/reenviar ingresos durante H1.

## Límites y siguiente GATE

CI remota se comprueba tras publicar, separada de resultados locales; workflow incorpora nueva verificación de empaquetado Docker además de Linux/Windows existentes. El enlace y conclusión del run se informan al entregar SHA; no inferir éxito de que el push termine.

No hay despliegue Ubuntu ni prueba física sobre su kernel/permisos/disco. Faltan H2-inventario, configuración institucional, respaldo externo/restore físico, calificación de máximos/capacidad/continuidad, Cloudflare/túnel/DNS/tratamiento de datos, registro/certificación CU y ZIP/servicio PISEE productivo. Datos de la VM Windows se completan en integración, no bloquean preparar Formulario.

npm ci informó10 advertencias moderadas de dependencias; no se ejecutó npm audit fix ni cambios de versiones fuera del alcance. Clasificar/remediar conforme al hardening antes de habilitar servicio, sin confundirlo con un fallo de build H1.

Mantenimiento online=false, retirada=false, política ACK72h no activada, garantías independientes no emitidas. Backups en otro directorio local y prueba funcional no acreditan independencia física. H1 **prepara** la siguiente sesión; la publicación no autoriza activar esos mecanismos.
