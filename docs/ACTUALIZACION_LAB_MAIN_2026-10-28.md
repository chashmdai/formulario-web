# Actualización del laboratorio al main publicado · 28/10/2026

**Estado:** desplegado localmente y verificado. No habilita producción ni retiradas.

## Versión instalada

- Commit fuente: `cb25b641cce0adebe431b6ec9ca3338216a18ae8`, coincidente con main remoto al comenzar la intervención.
- JAR: `f185af8382efdcce8234ae8f44d5288b4643b8cb5f936b27faa44696ef969ccc`, copia inmutable fuera de Maven en ambos directorios privados.
- Las dos instancias ahora usan el mismo artefacto del código actual. No se modifica código funcional durante esta operación.

| Instancia | Puerto | PID observado |
|---|---:|---:|
| Integrado Sandbox / CU / SRCeI DEV | 18443, mediante Caddy en 443 | 49444 |
| Multimedia aislado | 19443 | 49475 |

Los PID corresponden al control 2026-10-28T12:01:12-03:00. Se conservan JAR anteriores y selección/configuración de arranque previa en el respaldo.

URL preferente: https://claveunica.formularioadulto.defensorianinez.gob.cl/?vista=inicio

Multimedia aislado: https://claveunica.formularioadulto.defensorianinez.gob.cl:19443/?vista=inicio

La aplicación Docker histórica de 18080 permanece cerrada para recepción y no es el destino de Caddy. No se actualiza como una tercera aplicación vigente.

## Bases, respaldo y restauración

Se detuvieron únicamente las dos JVM propias antes de respaldar. MySQL, Nodo PISEE, proxy y workers continuaron ejecutándose; no hubo reinicio de MySQL ni cambios a Windows/SIGER.

| Esquema | Antes | Después |
|---|---:|---:|
| robust_public_local | V7 | V12 |
| robust_public_media_lab | V8 | V12 |

Respaldos protegidos fuera de Git:

```text
/home/nexbyteproveedor/.local/share/siger/actualizacion-main-20261028T145103Z
```

Se respaldaron SQL, configuraciones, selección de JAR y archivos privados referenciados necesarios para recuperar el ambiente. El paquete multimedia incluye originales, derivadas, bloques y temporales de su almacén. Contiene material reservado y no debe adjuntarse ni publicarse.

La restauración se ejecutó en **esquemas nuevos**, sin sobrescribir el origen ni otros laboratorios. Multimedia se restauró también en un directorio nuevo, comprobando hashes y referencias mediante el script existente media-archive.py.

Se calcularon y compararon hashes de las filas de todas las tablas preexistentes, usando exclusivamente sus columnas anteriores a la migración. Resultado:

- Integrado: 11 tablas y 121 filas preexistentes, excluyendo flyway_schema_history; idénticas en restore y tras migrar.
- Multimedia: 15 tablas y 593 filas preexistentes, bajo el mismo criterio; idénticas.
- Checksums/success de las migraciones previas sin cambios; doce migraciones correctas en cada esquema.
- 105 archivos del almacén, 25.361.710 bytes: tamaños y SHA-256 conservados después de arrancar.
- BLOB históricos comprobados por longitud y SHA-256, sin sustitución de originales.
- No hubo retiros ni minimización; transient_maintenance no registra ejecución.

Los respaldos y restauraciones están en el mismo laboratorio. **No acreditan respaldo físicamente independiente ni custodia productiva.**

## Integridad documental con el código nuevo

Una sonda Java de solo lectura utilizó las clases recién compiladas, las credenciales de aplicación locales y la confianza pública ya configurada:

| Base | Comunicaciones firmadas válidas | ACK válidos | Versiones |
|---|---:|---:|---|
| Integrado | 13 | 0 almacenados | 1.0 |
| Multimedia | 7 | 7 | 1.0 y 1.1 |

EntregaFormulario.validar y validarRegistro comprobaron contenido, procedencia, firma y correspondencia de ACK. No se generaron firmas, folios, Comunicaciones o ACK para estas comprobaciones.

## Configuración preservada y controles cerrados

Se conservó íntegramente el contenido anterior de application.properties y se añadieron explícitamente las guardas siguientes, equivalentes al comportamiento cerrado predeterminado:

```properties
ciclo-vida.online-habilitado=false
ciclo-vida.eliminacion.habilitada=false
ciclo-vida.modalidad=GARANTIA_INDEPENDIENTE
ciclo-vida.ack-riesgo-aceptado=false
```

Se preservaron valores de recepción, cookies, credenciales, claves, certificados, callbacks, PISEE, reglas de privacidad y configuración multimedia. No se activó la alternativa de 72 horas, emisión de garantías ni programador.

El integrado conserva su modalidad de adjuntos clásica; el perfil de 19443 conserva multimedia habilitado y aislamiento de proveedores. Actualizar el artefacto no implica cambiar esas banderas. El inspector activo coincide por SHA-256 con infra/media/inspector.py de main; no necesitó reconstrucción.

## Pruebas ejecutadas en esta unidad

| Prueba | Resultado |
|---|---|
| Frontend completo | 176 correctas, cero fallos |
| Lint y build | Correctos |
| Backend MySQL QA completo, excluyendo Carga*Test | 324 seleccionadas: 322 correctas, dos omisiones, cero fallos/errores |
| Empaquetado JAR | Correcto; frontend dist igual al incluido en el JAR |
| Migración de bases del laboratorio | V7→V12 y V8→V12 correctas |
| Restauración y comparación | Correctas en esquemas/directorio nuevos |
| Comprobantes/documentos históricos | 20 firmas y siete ACK válidos con el código nuevo |
| HTTPS 443, 18443 y 19443 | HTML, JS, CSS y API de capacidades correctos |
| Windows hacia URL preferente | HTTP 200 y ssl_verify_result=0, sin excepciones TLS |
| Inicio CU local | 302 al authorize oficial y callback correcto, sin seguir al proveedor |
| Contenedores | MySQL/PISEE saludables; proxy, antivirus e inspector activos |

Las dos omisiones existentes son workers reales opcionales y reinicio destructivo de MySQL. H2 estaba validado para este mismo código (324 seleccionadas, 322 correctas, dos omisiones) en el cierre del 26/10; no se repitió esa batería en esta unidad. No se atribuye ejecución nueva de CI remota.

**La regresión MySQL QA pendiente de GATE 4A.14.2 queda ejecutada y correcta el 28/10.** Su resultado anterior de indisponibilidad sigue siendo evidencia histórica, no un pendiente actual.

No se repitieron logins gubernamentales ni consultas Registro Civil. La revisión visual de certificación posterior al cambio sigue siendo una actividad separada; para ver la versión nueva en una pestaña ya abierta, recargar con Ctrl+F5.

## Trazabilidad y reversión

Resultados técnicos reducidos y logs privados: `.local/actualizacion-main-20261028/`. Incluyen plan, resultados MySQL/frontend, HTTP, preservación y verificador de firmas; no publicarlos íntegramente sin revisión.

Cada directorio jars conserva el archivo SHA.build.json que vincula el artefacto instalado al commit. Los lanzadores validan hash, PID y configuración mediante --estado.

La reversión binaria sola **no revierte V12**. No arrancar el JAR anterior sobre el esquema nuevo sin comprobar compatibilidad. Una reversión completa requiere una ventana controlada, respaldo del estado actual y restauración de la base compatible, utilizando los paquetes cuya restauración ya fue verificada. No ejecutar Flyway clean, DROP, downgrade o restauración sobre el origen a ciegas. Ninguna reversión destructiva fue ejecutada.

No se modifica sistema-de-requerimientos, MySQL80 Windows, contratos 1.0/1.1, canonicalización, claves institucionales ni PISEE. No hubo push ni cambios de lógica. Este despliegue es exclusivamente del laboratorio.
