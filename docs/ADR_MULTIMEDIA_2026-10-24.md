# ADR · Custodia multimedia privada y transferencia reanudable

Estado: implementado y verificado en laboratorio separado, pendiente de revisión y habilitación institucional; no habilitado sobre el baseline integrado ni publicado.
Base: `origin/main` / `c1e4ead`, comprobado por fetch, árbol limpio y rama `multimedia-adjuntos-2026-10-24`.

## Auditoría previa

- `MultipartFile.getBytes`, `Archivo(byte[])`, ClamAV `limpio(byte[])`, JDBC `getBytes` y respuesta `byte[]` materializan archivos completos. La recepción vuelve a leer cada binario dentro de una transacción con timeout de 5 segundos. Reemplazar construye un lote completo en heap.
- Límites actuales: 5 archivos, 10 MiB cada uno, lote 25 MiB; multipart Spring 10/26 MB y filtro de admisión 26 MiB, dos cargas. `/tmp` Docker usa tmpfs de 128 MiB. No hay progreso, reanudación o retiro individual.
- `citizen_attachment` V7 guarda LONGBLOB NOT NULL. MySQL observado: `max_allowed_packet=67108864` y buffer pool 128 MiB; el contenedor tiene 1536 MiB. Un binario de 150 MiB no debe circular por ese canal.
- ClamAV real existente: StreamMaxLength/MaxFileSize 10M, MaxScanSize 50M, AlertExceedsMax yes, dos threads y puerto loopback 13310. No es suficiente anunciar límites mayores desde React.
- Caddy no impone hoy un límite de cuerpo explícito; el proxy integrado tiene timeout de cabeceras de respuesta de 30 segundos. Una finalización síncrona prolongada sería inadecuada.
- WSL observado: 12 GiB RAM, aproximadamente 9 GiB libres, sin swap utilizada, espacio Linux amplio. No hay FFmpeg, qpdf, LibreOffice ni sandbox de conversión instalados en el anfitrión. Se evita un conversor nativo privilegiado.

Se leyeron completos los servicios de adjuntos/recepción/entrega/ClamAV, configuración, migraciones y contrato, además de clientes y controles React. Las firmas y los documentos históricos deben conservarse.

## Decisiones

1. Abstracción común de lectura: MySQL para históricos y archivos privados inmutables para cargas nuevas. Metadatos, propiedad, estados, reservas, hashes y auditoría permanecen en MySQL. UUID internos; nunca rutas ciudadanas ni directorio estático. No hay copia física por asociación futura a Casos.
2. Bloques de 8 MiB secuenciales e idempotentes, hash por bloque y SHA-256 completo. Reservas de cuota antes de recibir; temporales durables, fsync y renombrado atómico antes de confirmar metadatos. Estado de carga separado del lote confirmado; una falla no reemplaza el lote anterior.
3. Análisis completo fuera de transacciones JDBC y con concurrencia acotada; la publicación del manifiesto es una transacción corta. No se descartan originales después de ACK. Limpieza de incompletos separada y controlada.
4. Antivirus de flujo y validación aislada local, sin red, como prerrequisitos reales de habilitación. FFmpeg/qpdf/poppler acotados; Office binario, HEIC/HEIF y conversiones Word no se anuncian hasta disponer de controles verificados. Derivadas nunca sustituyen originales.
5. Mantener la forma del descriptor: `id,nombre,tamanio,sha256,tipo,estado`. La ubicación física no forma parte de él. El contrato de visualización es una proyección aparte y deriva el origen ciudadano del servidor. Los contenidos multimedia nuevos usan versión 1.1 y reclamación con opt-in explícito; las entregas 1.0 mantienen sus bytes, firmas y consumo por defecto.
6. Nuevo camino detrás de configuración optativa; el baseline permanece operativo durante la revisión de esta rama. Los límites efectivos se publican desde backend y se reutilizan en React, proxy, scanner y pruebas.

## Referencias primarias

- [ClamAV INSTREAM](https://docs.clamav.net/manual/Usage/ClamdProtocol.html): el stream completo está limitado por StreamMaxLength; fragmentar la subida HTTP no elude ese límite del scanner.
- [Cloudflare 413 y límites por petición](https://developers.cloudflare.com/support/troubleshooting/http-status-codes/4xx-client-error/error-413/): los bloques se mantienen por debajo del límite de una petición, sin depender de contratar un plan mayor. No se configuró Cloudflare ni DNS.
- [FFprobe](https://ffmpeg.org/ffprobe.html), [qpdf](https://qpdf.readthedocs.io/en/stable/cli.html): validar contenedores/estructura no basta por sí solo; se requiere aislamiento, límites y pruebas corruptas.
- [Noble hashes](https://github.com/paulmillr/noble-hashes): API incremental; evita cargar el archivo entero en el navegador para su SHA-256. Su incorporación se limita al hashing, no determina el protocolo ni almacenamiento.

## Resultado de la decisión

Se conserva MySQL para los binarios históricos (lectura acotada) y se agrega FILE privado para nuevos originales, con una interfaz común de lectura. No se migra masivamente ningún binario. La ampliación usa V8, no modificaciones de migraciones aplicadas. El documento nuevo se versiona explícitamente 1.1 para evitar suponer que consumidores preparados para cinco adjuntos pequeños ya admiten multimedia.

El inspector es un worker local pequeño, con protocolo de entrada de archivo actual, sin acceso al almacén. La fragmentación HTTP se implementó sin incorporar un servidor/protocolo de terceros o base de datos adicional. Se añadió únicamente Noble hashes 2.4.0 (sin dependencias transitivas) para hashing incremental en Web Worker. FFmpeg/qpdf/Poppler provienen de Ubuntu 24.04, separados del runtime Spring.

La elección evita el límite observado de paquete MySQL de 64 MiB, materialización en heap y una gran transacción de lectura. Un único escritor por almacén y un análisis simultáneo constituyen un diseño deliberado para este laboratorio; escalar horizontalmente requiere coordinar almacenamiento/leases antes de abrir más instancias.

Se probaron almacenamiento, cuotas, rechazos, procedencia contractual, streaming, navegador y restauración. El tamaño real mayor del flujo completo fue 16 MiB + 7 bytes, no 150/250 MiB. El reinicio preserva archivos/BD pero invalida la sesión Tomcat ciudadana; no se introdujo recuperación entre sesiones ni se serializaron tokens OIDC al disco. Es una limitación explícita pendiente.

Consultar [política](POLITICA_MULTIMEDIA_2026-10-24.md), [operación y respaldos](OPERACION_MULTIMEDIA.md), [contrato 1.1](../contratos/formulario-completo-multimedia-v1.1.md), [visor](VISOR_INSTITUCIONAL_MULTIMEDIA.md) e informe de pruebas de esta unidad. No hay publicación ni habilitación productiva.


El inspector recibe únicamente el archivo actual por socket privado; no monta el almacén de originales. Landlock ABI ≥3 restringe cada proceso al trabajo actual y librerías, y seccomp bloquea sockets, señales y escapes de grupo. El servicio cierra si no supera su comprobación real de aislamiento. Referencia: https://docs.kernel.org/userspace-api/landlock.html.
