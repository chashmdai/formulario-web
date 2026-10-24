> **Actualización 24/10/2026:** cliente/Nodo reutilizados desde el formulario integrado con disparador automático, allowlist privada y una consulta DEV real completa. El override de laboratorio publica solo `127.0.0.1:18084` para el JAR nativo; el compose base conserva su red sin puertos. No se modificaron credenciales, certificados ni endpoints externos. Estado actual y reversión: [RUN_SRCEI_AUTOMATICO_2026-10-24.md](RUN_SRCEI_AUTOMATICO_2026-10-24.md). La documentación siguiente conserva la validación inicial independiente del Nodo/cliente.

# Nodo PISEE y cliente SRCeI en Linux

Estado al 23-10-2026: **REAL_VALIDADO en DEV**, con un RUN expresamente autorizado por el responsable en el chat. No se publica ese identificador ni respuestas personales. Dos consultas acotadas: sonda HTTP y adaptador Java. QA/producción no habilitados.

## Evidencia y arquitectura

`NodoV2` institucional es ELF64 x86-64 estático, SHA-256 `576f9d3b8143e41f2503b6bd3456a171b248bfc4baaf942ec0381eddf5eda95b`. Se verificó contra el archivo fuente del paquete, no contra una firma/hash publicado por el proveedor. No se ejecutó el binario Windows. Dos pares de clave/certificado activos coinciden y están vigentes; no se usaron respaldos X509 como reemplazo.

El manual Nodo §4.4.2.A exige `xmlsec1` para SRCeI/PISEE 1. El contenedor Debian lo incluye. UID/GID 1000, raíz de archivos de solo lectura, capacidades retiradas, límites de CPU/memoria y red Docker privada con salida. Sin publicación de 8084/8489. 8489 es entrada de otros organismos según el manual; no es necesaria una publicación para esta prueba de consumo.

El primer arranque reveló escrituras del nodo en `./tui.sock` y `./privados` además de su base configurada. Se corrigió usando `/var/lib/pisee` como directorio de trabajo persistente, con configuración montada de solo lectura y rutas absolutas hacia certificados. No se relajó el contenedor a privilegiado ni se copió SQLite/LevelDB Windows. El estado Linux se crea nuevo.

La configuración privada deriva del original. Solo se adaptan rutas y estado, nivel de registro, autenticación local, selección del único consumidor necesario y eliminación de proveedores/scripts ajenos a esta prueba. Se preservan identidad institucional y parámetros del servicio. La cabecera interna sigue la guía oficial `X-PISEE-LocalAuthorization`; su valor se genera y guarda fuera de Git. Sin CORS abierto.

Consulta HTTP: 200, XML válido, diez elementos principales en `datosPersona`: run, nombre, fechaNacimiento, fechaDefuncion, estadoCivil, sexo, nacionalidad, circunscripcionNacimiento, profesion y fechaDeMuertePresunta. Se confirmó la etiqueta **`entradaSRCel` con U+006C final**; el informe del 21/10 era evidencia histórica con otra transcripción y resultado vacío.

El cliente Java ejecutado en la misma red obtuvo `COMPLETO` y doce atributos normalizados, sin imprimir valores. La diferencia de cantidad respecto de los diez elementos XML se debe al mapeo de subelementos RUN/nombres/fechas y a la omisión de datos sin información útil. La clasificación completa describe disponibilidad de atributos requeridos, **no autentica al remitente**.

## Preparación y operación

Desde Linux, en el repositorio:

```bash
python3 scripts/linux/preparar-pisee.py /ruta/institucional/pisee/nodo /ruta/privada/pisee-dev
export PISEE_PRIVATE_DIR=/ruta/privada/pisee-dev
docker compose -f infra/compose.pisee.yml config --quiet
docker compose -f infra/compose.pisee.yml up -d --build --wait
docker compose -f infra/compose.pisee.yml ps
# Detener conservando estado y material privado:
docker compose -f infra/compose.pisee.yml stop
```

El preparador rechaza un destino existente o dentro de Git. No sobrescribirlo para actualizar: respaldar estado/configuración/secretos y revisar cambios acotados. Montar solo la copia Linux, nunca los originales Windows. `logging: none` evita persistir logs de un binario cuyo registro de cuerpos no controlamos; no se ofrecen esos logs como evidencia de central. La consulta funcional demuestra conectividad efectiva. El healthcheck solo demuestra escucha HTTP local.

La sonda manual requiere `run-autorizado.json` privado con campos `numero` y `dv`, y `--autorizar-dev`. No crear un archivo con RUN reales dentro del repositorio. Por defecto solo conserva evidencia sanitizada; la retención excepcional de XML requiere bandera separada. No ejecutar esta sonda como parte de tests automáticos ni en producción.

## Adaptador Spring

`ClientePisee` es un componente backend sin ruta pública ni proxy de consultas. Se registra deshabilitado por defecto. Habilitación requiere `PISEE_SRCEI_ENABLED=true`, `PISEE_ENVIRONMENT=DEV`, `PISEE_DEV_AUTHORIZED=true`, ruta privada `PISEE_LOCAL_AUTHORIZATION_PATH`, origen `PISEE_BASE_URL` y proceso Sandbox fuera de perfiles productivos. `PISEE_TIMEOUT_MS` (por defecto 15000) y `PISEE_CONCURRENCY` (2) tienen límites validados. Nunca desactivar TLS para un destino HTTPS.

Fallos tipados: no habilitado, red, timeout, autorización, indisponibilidad HTTP, XML inválido, respuesta excesiva, saturación, RUN discrepante, glosa negativa, vacío, parcial y completo. No sigue redirecciones ni reintenta consultas automáticamente. Limita el cuerpo durante recepción a 128 KiB y el tiempo total, incluido un cuerpo lento. Parser sin DTD, entidades externas ni XInclude, con límites de profundidad/nodos y rechazo de duplicados.

Diccionarios centralizados: sexo F/M/I, estado civil S/C/V/D/P/J y nacionalidad C/E/N; U significa ausencia útil. Códigos desconocidos conservan su código y se clasifican desconocidos. Fechas incompletas no se transforman en fechas inventadas; ausencia de defunción no prueba vida. Circunscripción de nacimiento no se convierte en domicilio. Nombres agregados no se separan por espacios. Cada atributo contiene fuente, nivel, instante y referencia de evidencia internos; `toString` protegido.

Quedan **pendientes**: servicio de enriquecimiento ligado a sesión, autorización/antiabuso ciudadano, respuesta pública de campos faltantes sin valores sensibles, persistencia de procedencia, conexión a formularios y evolución de entrega de campos nuevos. La existencia del bean no implica que estos recorridos estén terminados. No se modificaron las firmas ni sobres V3.

Pruebas sintéticas nuevas: 15 correctas, incluyendo XML exacto, completo/parcial/vacío/negativo, códigos/fechas, XXE/DTD, duplicados, RUN discrepante, límites, timeout de cuerpo, saturación y ausencia de redirects/reintentos. Ningún test versionado contiene el RUN autorizado ni contacta al proveedor.
