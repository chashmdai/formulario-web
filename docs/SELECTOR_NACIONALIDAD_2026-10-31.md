# Selector de nacionalidad · 31/10/2026

Ajuste acotado de la identificación adulta extranjera sin RUN. No cambia ClaveÚnica, PISEE/SRCeI, NNA, custodia, contratos firmados ni migraciones.

## Comportamiento

- Nacionalidad conserva el identificador adult-nationality y pasa a ser una selección nativa con buscador local etiquetado. La búsqueda ignora tildes y mayúsculas y filtra las opciones; el texto buscado nunca se guarda como nacionalidad.
- El catálogo compartido contiene 249 países y territorios ISO 3166-1, con etiquetas españolas. No utiliza servicios externos, geolocalización ni una nueva dependencia.
- Otra / situación especial habilita un único detalle de texto obligatorio, de hasta 120 caracteres, por ejemplo para una persona apátrida. El ingreso libre no está disponible para las opciones ordinarias.
- El backend acepta únicamente una etiqueta del catálogo o la excepción explícita con detalle válido. Rechaza texto libre sin clasificación, códigos no seleccionados, excepciones vacías, controles y detalles excesivos.
- La selección se almacena en el atributo existente nacionalidad, con fuente DECLARACION_CIUDADANA y sin autenticación. La excepción usa el prefijo Otra / situación especial: seguido del detalle. No añade campos contractuales ni altera firmas o documentos históricos.
- Las declaraciones anteriores que no coinciden con el catálogo aparecen bajo la opción excepcional, conservando su texto. Solo al confirmar una nueva declaración se envía su clasificación explícita; no se ejecutan actualizaciones masivas ni se truncan antecedentes.
- El número del documento sigue siendo texto, sin imponer formatos nacionales.

## Catálogo y mantenimiento

Fuente local: iso-codes 4.16.0-1 de Ubuntu, archivo /usr/share/iso-codes/json/iso_3166-1.json y traducción española iso_3166-1. Se conserva common_name cuando está disponible. Referencias: [ISO 3166](https://www.iso.org/iso-3166-country-codes.html), [iso-codes](https://salsa.debian.org/iso-codes-team/iso-codes).

La instantánea versionada está en [paises.json](../formulario-publico/front/src/formulario/paises.json). React y Java consumen ese mismo recurso; Maven lo incorpora a catalogos/paises.json. Una actualización futura del catálogo requiere revisar etiquetas y compatibilidad de valores anteriores antes de publicarla.

Los avisos de autoría y LGPL-2.1+ del origen, junto con el texto de licencia, se conservan en [licenses/iso-codes.txt](../formulario-publico/front/public/licenses/iso-codes.txt), incluido en la distribución estática. No se modifica la licencia del código propio.

## Verificaciones locales ejecutadas

- Frontend: 200 pruebas correctas en 22 archivos; ESLint y build TypeScript/Vite correctos.
- Backend H2 completo sin Carga*Test: 363 seleccionadas, 361 correctas y dos omisiones opcionales existentes; cero fallos o errores.
- MySQL QA aislado robust_public_test: 53 pruebas afectadas correctas (identificación HTTP, recepción adulta y catálogo).
- Regresiones: búsqueda con y sin tildes, selección/corrección, búsqueda sin resultados, país preservado mientras se filtra, texto excepcional, mínimo obligatorio, restauración de declaraciones anteriores, procedencia y recepción/ACK sintéticos.
- Comprobación visual controlada en Edge Windows: escritorio/móvil y temas claro, oscuro y alto contraste; teclado, filtrado, excepción y ausencia de desborde/errores de contraste. Todos los recursos y API se interceptan con dobles locales; no hay Comunicaciones ni consultas gubernamentales.

Las dos omisiones H2 corresponden a workers reales opt-in y reinicio destructivo MySQL. El resultado MySQL es una batería enfocada, no una nueva ejecución completa de todas las pruebas en ese motor.

Evidencia privada ignorada por Git: .local/selector-nacionalidad-20261031/. Este ajuste se publica en main por autorización expresa; no actualiza ni reinicia los JAR del laboratorio. Su instalación local queda para una actualización autorizada posterior. No se cambian banderas, claves, datos o configuración ciudadana.
