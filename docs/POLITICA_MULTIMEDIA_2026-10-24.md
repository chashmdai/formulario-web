# Política multimedia y cuotas · revisión de ingeniería

La función está desactivada por defecto. El laboratorio separado permite comprobarla; el baseline del 96 % conserva su alcance anterior. Los límites son configurables y no constituyen una certificación de capacidad ni habilitación productiva.

## Perfil inicial

| Grupo | Extensiones habilitables | Máximo por archivo |
|---|---|---:|
| Imágenes | JPG/JPEG, PNG, WebP | 25 MiB |
| Documentos | PDF, TXT UTF-8, DOCX, ODT, XLSX, PPTX | 50 MiB |
| Audio | MP3, M4A, WAV, OGG | 75 MiB |
| Video | MP4, MOV, WebM | 150 MiB |

Máximo 12 adjuntos y 250 MiB por Comunicación. Un MiB son 1 048 576 bytes. La interfaz existente redondea los tamaños con la etiqueta MB; los límites efectivos están expresados en bytes en la API. Ningún MIME proporcionado por el navegador determina el tipo aceptado.

**Deshabilitados en el nuevo camino:** DOC binario (no se dispone de validación de macros/contenido activo suficiente), HEIC y HEIF (decodificación no verificada), ejecutables y extensiones de macros. Las cargas multipart antiguas se rechazan cuando multimedia está habilitada para evitar eludir el perfil. Los originales históricos siguen siendo descargables sin conversión ni modificación.

## Validación

- Nombres Unicode NFC, hasta 180 caracteres, sin rutas, controles, prefijos ocultos ni extensiones peligrosas encadenadas. El nombre solo es metadato; la clave física es un UUID interno.
- Hash por bloque y SHA-256 completo antes de analizar. Antivirus INSTREAM del original entero antes de aceptar; un límite excedido o un resultado ambiguo jamás se interpreta como limpio.
- PDF: qpdf comprueba estructura completa, se rechazan cifrado, JavaScript, acciones, archivos embebidos y contenido activo identificado. Solo la primera página se representa como PNG. No se afirma renderizado completo de todas las páginas; si no se obtiene una representación segura, queda descarga controlada.
- Office XML/ODT: ZIP sin cifrado ni travesías, entradas duplicadas, enlaces de filesystem, macros, ActiveX, objetos embebidos, scripts o relaciones externas. Hasta 2000 entradas, 100 MiB expandidos, razón 200 y XML de 8 MiB. Se comprueban las partes principales, namespaces, tipos y relaciones internas; ODT exige mimetype y manifiesto. XLSX se limita a valores sin fórmulas ni conexiones externas; los objetos OLE/binarios embebidos se rechazan también dentro del XML. Es un perfil estructural acotado, no validación exhaustiva de ECMA/ISO ni prueba de apertura en cada suite Office. No se convierte ni ejecuta Office.
- Imágenes: decodificación local, hasta 24 millones de píxeles y PNG derivado de hasta 1024 px y 2 MiB. Se representa el primer cuadro; no se promete animación.
- Audio: hasta 30 minutos, dos canales y 96 kHz; codecs admitidos MP3, AAC, Opus, Vorbis y PCM enumerados por el inspector. Video: hasta tres minutos, área de hasta 1920 × 1080 y H.264/VP8/VP9. Se decodifica todo el flujo con un hilo. HEVC/H.265 y otros codecs se rechazan aunque la extensión sea MOV/MP4. Las carátulas/streams extra o contenedores fuera del perfil pueden rechazarse.
- Cada proceso de análisis tiene Landlock ABI ≥ 3 y seccomp obligatorios, sin sockets y con acceso exclusivo al trabajo actual, CPU 45 s, memoria virtual 768 MiB, archivos generados de 16 MiB y deadline padre de 50 s. Hay además límites del contenedor. Un archivo que exceda capacidad/tiempo no se acepta por su extensión ni se reenvía a terceros.

La aceptación acredita los controles implementados; no garantiza ausencia universal de amenazas. Las actualizaciones de scanner, firmas y decodificadores requieren mantenimiento institucional.

## Cuotas y transferencia

- Bloques secuenciales de hasta 8 MiB. Dos peticiones de carga simultáneas por instancia; análisis único, sin cola ilimitada; hasta cuatro descargas concurrentes.
- Por sesión: hasta 48 inicios en 24 h, 24 cargas activas por recorrido y un volumen reservado de originales de hasta dos lotes. Se conservan los límites antiabuso de creación de recorridos. Duplicación por hash/tamaño solo dentro del recorrido propio, sin oráculo entre ciudadanos.
- Cuota global inicial reservada de 10 GiB y mínimo libre de 512 MiB. Se reserva conservadoramente `2 × tamaño + hasta 2 MiB de derivada`; se conserva esa reserva tras validar aunque se hayan retirado los bloques. Por tanto 10 GiB reservados no equivalen a 10 GiB de originales útiles. No aumentar cuotas sin inventario, disco y presupuesto de respaldo.
- Alertas agregadas: reserva ≥ 80 % o espacio libre inferior al mínimo más dos videos máximos. El sondeo devuelve código de atención; no se ha instalado un canal automático de notificación ni tarea programada.
- Incompletos vencen a las 24 h. La limpieza requiere acción explícita y no elimina originales confirmados ni recorridos con Comunicación. Cancelar un archivo no incorporado libera su reserva cuando deja de analizarse. Los originales retirados de un borrador validado se conservan a la espera de una política de retención.

## Capacidad comprobada y condiciones

Se verificaron pequeños archivos sintéticos de cada extensión del perfil, rechazos malformados y activos, un antivirus real con EICAR al final de un archivo de más de 12 MiB y un recorrido HTTP completo de 16 MiB + 7 bytes en bloques de 8 MiB. No se ejecutó carga, concurrencia intensiva, lotes de 250 MiB ni videos de 150 MiB por la inestabilidad pendiente de Windows. Los máximos configurados son techos del perfil de laboratorio, no capacidad de producción validada.

La reanudación automática funciona mientras la sesión Tomcat propietaria sigue vigente, incluyendo recarga y transporte interrumpido. Reiniciar el proceso pierde esa sesión en la arquitectura actual: MySQL y archivos sobreviven, pero la sesión nueva recibe 404. No se ha añadido un token alternativo que permita recuperar identidad o archivos. Resolver recuperación ciudadana entre sesiones y calificar tamaños máximos son condiciones pendientes antes de una habilitación institucional amplia.
