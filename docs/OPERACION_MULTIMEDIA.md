# Operación, respaldos y recuperación multimedia

## Separación del laboratorio

El baseline de `origin/main@c1e4ead` continúa en HTTPS 443 y proceso nativo 18443, esquema `robust_public_local` y sus volúmenes. Esta rama utiliza **19443**, cookie distinta, esquema **`robust_public_media_lab`** y directorio privado **fuera de Git**. No se migró el baseline; conserva V7. El esquema de prueba `robust_public_test` y el esquema multimedia sí validaron V8. No desplegar un JAR de esta rama contra V7 sin una migración planificada y respaldo previo.

La compilación de laboratorio utiliza `-Dsiger.build.directory=/home/nexbyteproveedor/proyectos/formulario-web/.local/multimedia-target`. El JAR en ejecución es una copia privada `runtime.jar`, separada de las salidas de Maven. No sobreescribir un JAR activo ni reutilizar `backend/target` del baseline.

La primera compilación de esta unidad utilizó por error la ruta de artefacto activa. Se corrigió antes de habilitar multimedia: se reconstruyó exactamente el código de main, se restauró su JAR y se reinició el mismo proceso/configuración. Las custodias, adjuntos y migraciones protegidas del baseline conservaron conteos/checksums. La evidencia privada registra la restauración; todas las compilaciones siguientes se aislaron.

## Prerrequisitos y habilitación optativa

Linux POSIX con filesystem durable local (ext4 verificado), Landlock ABI ≥ 3, seccomp, Docker y MySQL. El inspector falla al iniciar si no comprueba aislamiento de filesystem y sockets. El almacén rechaza rutas bajo Git, enlaces, directorios ajenos y un segundo escritor. No se admite un directorio estático ni un volumen efímero para originales.

Propiedades privadas necesarias, además de la configuración existente de recepción:

```properties
formulario.multimedia.habilitada=true
formulario.multimedia.directorio=/ruta/privada/almacen
formulario.multimedia.antivirus-socket=/ruta/privada/ipc-antivirus/clamd.sock
formulario.multimedia.antivirus-config=/ruta/privada/clamd-media.conf
formulario.multimedia.inspector-socket=/ruta/privada/ipc-inspector/inspector.sock
```

Por defecto la bandera está apagada. Los sockets, directorios y configuración pertenecen al UID/GID de aplicación (1000 en Compose); directorios 0700 y configuración 0600. No publicar IPC por TCP ni montar el socket Docker o todos los originales en el inspector.

Overrides: `cantidad`, `total`, `bloque`, `imagen`, `documento`, `audio`, `video`, `cuota-global`, `minimo-libre`, bajo `formulario.multimedia`. Los tamaños aceptan DataSize Spring (usar `MB` solo conociendo su representación; preferir bytes exactos o el perfil por defecto). No exceder los techos validados por código ni reducir scanner debajo del archivo máximo.

`infra/compose.multimedia.yml` define únicamente los dos workers nuevos. Sus variables son `MULTIMEDIA_IPC_DIR`, `MULTIMEDIA_AV_IPC_DIR`, `MULTIMEDIA_AV_DATABASE`. Los dos primeros son directorios privados POSIX ya creados con UID 1000. El tercero contiene definiciones ClamAV reales y actualizadas, montadas solo lectura. Construir con `docker compose -f infra/compose.multimedia.yml build`; iniciar ese mismo proyecto sin intervenir los otros servicios.

Para ejecutar Spring dentro de Docker, montar el almacén persistente y ambos directorios IPC en las rutas exactas de su configuración privada, con UID/GID 1000. Mantener fuera de la imagen claves, certificados y configuración; reutilizar los controles de proxy confiable ya establecidos para que HTTPS/CSRF sigan siendo efectivos. La prueba integrada de esta unidad utiliza Spring nativo con TLS y los workers en Docker; la imagen Spring se compiló y probó, pero no se desplegó contra el baseline.

Inspector: sin red, solo IPC y scratch, 1 CPU, 768 MiB, 32 PIDs, scratch 192 MiB. ClamAV: sin red, 1 CPU, 1536 MiB, 32 PIDs, scratch 320 MiB, un análisis. Mantener definiciones por procedimiento institucional; el worker no descarga actualizaciones ni datos ciudadanos. Revisar versión/edad de firmas y vulnerabilidades de decodificadores antes de habilitar. Los motores y paquetes requieren seguimiento; las imágenes no equivalen a un perfil productivo certificado.

ClamAV: StreamMaxLength 160M, MaxFileSize 150M, MaxScanSize 300M y AlertExceedsMax yes; el Java comprueba esos mínimos y PING al arrancar. Plazos Java de AV/inspector 90/75 s, lease de análisis 180 s y polling ciudadano acotado. Un fallo intermedio conserva la carga como reintentable o la rechaza; jamás publica un archivo no analizado.

## Proxy, Spring, MySQL y capacidad

El PUT raw de bloques admite hasta 8 388 608 bytes; el filtro de admisión conserva dos cargas concurrentes. No depende de ampliar multipart: el camino histórico mantiene 10/26 MiB y se rechaza para cargas nuevas con multimedia activa. `infra/media/Caddyfile.bloques` se importa dentro del sitio HTTPS existente y limita ese PUT a 8 MiB. Se validó su sintaxis con Caddy 2.11.7; no se recargó el proxy del baseline. La finalización devuelve 202 sin mantener una respuesta HTTP abierta durante el análisis.

El navegador integrado se verificó contra TLS nativo 19443, sin Cloudflare. Bloques de 8 MiB están por debajo de los límites por petición publicados por [Cloudflare](https://developers.cloudflare.com/support/troubleshooting/http-status-codes/4xx-client-error/error-413/); no se configuró ni probó ese servicio. Deben verificarse timeouts, WAF, buffering y publicación real antes de habilitarlo.

MySQL conserva metadatos y referencias UUID. Los archivos FILE no pasan por paquetes BLOB de 64 MiB ni `getBytes`; el histórico se mantiene acotado. Originales: escritura temporal, fsync, rename atómico, fsync de directorio, permisos 0400 y confirmación posterior en BD. Un cierre entre archivo y commit puede dejar un objeto huérfano, nunca una aceptación previa a su escritura durable. Reintentar valida el hash; nunca reemplaza un original distinto.

Cuota conservadora y alertas: ver [política](POLITICA_MULTIMEDIA_2026-10-24.md). `GET /operacion/multimedia` requiere autenticación técnica y devuelve agregados, sin nombres ni rutas. `scripts/linux/media-capacity.py --config CONFIG_PRIVADA --ca CA --origin ORIGEN_HTTPS` muestra esos agregados y devuelve código 2 si hay alerta. No instala notificaciones ni tareas programadas. Respaldos requieren capacidad adicional independiente de la cuota de custodia.

## Limpieza controlada

`POST /operacion/multimedia/temporales` con `{"aplicar":false}` inventaría incompletos vencidos; `true` aplica bajo las validaciones del servidor. Se evita la limpieza mientras hay análisis activo. Primero se marca el estado terminal, después se retira el temporal, finalmente se libera la reserva. Las operaciones son reintentables. No se borra un archivo referido por `citizen_attachment`, ni un recorrido con Comunicación, ni un validado retirado a la espera de retención.

Tras una caída pueden quedar archivos `incoming/carga-*.part` sin fila de carga. Con **aplicación detenida**, `scripts/linux/media-archive.py clean-incoming --store ALMACEN` presenta candidatos de más de 24 h; `--apply` aplica. Rechaza enlaces/nombres inesperados y no opera `objects`, `derived` o documentos recibidos. Los huérfanos de `objects` requieren inventario/decisión institucional; no se eliminan por ausencia de referencia ni por ACK.

## Respaldo y restauración comprobable

`scripts/linux/media-archive.py` requiere acceso administrativo local a Docker y `/run/secrets/root.cnf` dentro del contenedor MySQL; no recibe contraseñas por argumentos. El respaldo es **offline para esta aplicación**: exige el lock exclusivo `instance.lock`, incompatible con un proceso Spring activo. No bloquea ni detiene los otros laboratorios.

1. Cerrar mantenimiento del laboratorio correspondiente y detener exclusivamente su proceso; conservar configuración, claves y volúmenes. La sesión ciudadana en memoria se pierde: informar/planificar esa interrupción.
2. Ejecutar `python3 scripts/linux/media-archive.py backup --database ESQUEMA --store ALMACEN --destination RESPALDO_NUEVO`. El contenedor por defecto es `siger-mysql-lab-mysql-1`; `--container` permite seleccionarlo explícitamente.
3. Obtiene mysqldump transaccional/quick/hex-blob sin DROP, copia por buffers los cuatro directorios y genera manifiesto SHA-256. El destino es nuevo, privado y fuera de Git. Verifica originales validados, derivados y BLOB históricos contra metadatos. Un resultado incompleto se conserva para diagnóstico y no se declara correcto.
4. Copiar SQL, store y manifiesto a un medio institucional protegido y separado. **No se implementó cifrado ni copia externa automática**: aplicar el mecanismo institucional de protección del respaldo. Excluirlo de Git, servidores estáticos, artefactos CI e imágenes.
5. Restaurar con `python3 scripts/linux/media-archive.py restore --source RESPALDO --database ESQUEMA_NUEVO --destination ALMACEN_NUEVO`. Se rechazan destinos/bases existentes, SQL o archivos alterados, rutas y enlaces inválidos. No se elimina una base fallida. Se verifica hash, conteos y referencias después de importar; conserva UID/GID sin privilegios y originales 0400.
6. Recuperar por separado las claves de firma históricas, trust stores, clave antiabuso y configuración autorizada. No se incluyen en el respaldo documental ni deben regenerarse para hacer coincidir firmas. Configurar usuarios/permisos MySQL del nuevo esquema por el procedimiento institucional antes de arrancar. Un restore no configura cuentas ni cambia callbacks.
7. Verificar migraciones y firmas, lectura privada y controles antes de reabrir. Reiniciar solo el proceso respaldado con su copia privada de JAR. Retener el origen hasta validar completamente el destino.

Se restauraron 57 archivos, 32 referencias FILE, un BLOB sintético histórico y una custodia sintética en `robust_public_media_restore_20261024`, fuera del esquema de baseline. Se verificaron hashes y CHECKSUM idénticos de custodia, adjuntos, cargas, bloques y Flyway. Es una restauración de laboratorio local, no una prueba de pérdida física del anfitrión. La exportación a un medio externo y la recuperación del material criptográfico en otro equipo siguen pendientes de operación institucional.

## Reinicio y límites reales

Se comprobó persistencia de originales, bloques y metadatos, y recuperación institucional real tras reiniciar Spring: reserva renovada y 16 MiB + 7 bytes descargados con SHA-256 correcto, sin sesión ciudadana ni nueva Comunicación. Tomcat no persiste la sesión ciudadana: tras reiniciar, la cookie anterior recibe 404 y no recupera el borrador ajeno. No se han serializado sesiones OIDC ni introducido un token alternativo. La reanudación HTTP dentro de sesión vigente está probada; recuperación ciudadana entre sesiones es un pendiente explícito.

Antes de habilitar ampliamente: revisar esta rama, acordar recuperación de sesiones, calificar máximos de archivo/lote sin estrés hasta resolver la inestabilidad del anfitrión, validar proxy publicado, actualización de firmas/decodificadores, respaldos externos y adaptar consumidor 1.1. No se habilita producción ni se modifica SIGER institucional en esta unidad.

## Reproducción de las verificaciones

- `python3 scripts/qa/test_media_archive.py`: cinco pruebas de destinos nuevos, SQL corrupto, rutas, enlaces y saneamiento limitado.
- `scripts/qa/generar-multimedia-sintetica.py`: ejecutarlo en el contenedor inspector, con salida dedicada y límites de recursos. Genera solo fixtures diminutos. `probar-inspector-multimedia.py` usa `MULTIMEDIA_SYNTHETIC_DIR` y `MULTIMEDIA_INSPECTOR_SOCKET`; `probar-clamav-multimedia.py --autorizar-eicar` usa además `MULTIMEDIA_AV_SOCKET`. EICAR es exclusivamente prueba benigna y sus bytes no se guardan en Git.
- `scripts/qa/media-resume.py iniciar|reanudar` con `--private DIRECTORIO_NUEVO_FUERA_DE_GIT --ca CA` verifica bloques/replay dentro de una sesión válida. La fase opcional `custodia --config CONFIG` crea una única recepción sintética idempotente necesaria para probar entrega y respaldo. Conservar su estado privado, reiniciar únicamente el laboratorio, esperar el vencimiento de la reserva anterior y ejecutar `recuperar --config CONFIG`: recupera por autenticación técnica sin volver a identificar al ciudadano. `historico` agrega un BLOB sintético **solo si el camino histórico está habilitado**; con multimedia activa se rechaza deliberadamente. No deshabilitar el perfil del baseline para generar ese fixture.
- `scripts/qa/multimedia.mjs`, usando Playwright/Edge de pruebas, exige hostname loopback y puerto 19443, bloquea cualquier origen externo y los POST de Comunicaciones. Comprueba UI, hashes, preview, Range, CSRF y acceso cruzado; deja capturas sintéticas en `.local/evidencias/multimedia-navegador`.
- `MultimediaWorkersRealTest` es opt-in con `MEDIA_REAL_CHECK=true`, `MEDIA_INSPECTOR_SOCKET`, `MEDIA_AV_SOCKET`, `MEDIA_AV_CONFIG`, `MEDIA_SYNTHETIC_DIR`. Usa exclusivamente fixtures locales y sockets Unix. La omisión de `RecuperacionProcesoTest` heredado permanece explícita; no sustituirla por una afirmación de cobertura total.

Para retirar el laboratorio de revisión: detener exclusivamente `arrancar-multimedia-local.py DIRECTORIO_PRIVADO --detener` y los servicios del proyecto `infra/compose.multimedia.yml` mediante `stop`. Conservar almacén, respaldo, configuración, claves y bases; no utilizar eliminación de volúmenes ni limpieza automática. El baseline 443/18443 sigue independiente.

## Capacidad y retención: auditoría del 25/10/2026

Ver [dimensionamiento de 25 GB, consumo real de cuotas y ciclo posterior al ACK](CAPACIDAD_Y_RETENCION_2026-10-25.md). El nuevo `scripts/linux/media-storage-report.py` inventaría solo metadatos de filesystem y, opcionalmente, agregados MySQL V8; no modifica archivos, reservas ni retención. El ACK sigue sin autorizar borrado. Los umbrales del informe son configurables y no cambian los controles de admisión del backend. No se instaló limpieza ni monitor periódico.
