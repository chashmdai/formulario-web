# Publicación separada y verificación · 23-10-2026

Se prepararon dos repositorios a partir del código vigente, sin reescribir el núcleo ni mover los datos del workspace original:

- `https://github.com/chashmdai/formulario-web`: aplicación `formulario-publico`.
- `https://github.com/chashmdai/sistema-de-requerimientos`: aplicación `siger-institucional`.

Cada repositorio conserva backend, frontend, lockfile, configuración de ejemplo, scripts propios, pruebas y migraciones. Se incluyen contratos y utilidades de integración compartidas. La estructura conserva el nombre de la carpeta de cada aplicación para no romper rutas existentes. Cada README indica su build independiente y enlaza el [manual completo](MANUAL_REPOSITORIOS.md).

Los scripts integrados pueden conectar los clones mediante `scripts/Conectar-Repositorios.ps1`; la aplicación ajena queda enlazada y expresamente ignorada por Git. Se comprobó el modo sin cambios, creación de junction e ignorado de la ruta ajena. No es necesario conectar clones en el workspace original, que ya contiene las dos carpetas. `.local` sigue siendo privado y no se traslada entre equipos.

Cambio mínimo al arranque: `CU_SANDBOX_CREDENTIALS_PATH` permite indicar el TXT institucional externo en otra ubicación; si no está definida, conserva la ruta anterior. No cambia credenciales, selección de Sandbox, callback, logout ni la validación previa de habilitación.

## Verificación ejecutada antes de publicar

Desde cada copia separada se ejecutó el script de build completo: instalación reproducible con `npm ci`, lint, pruebas React, compilación TypeScript/Vite y `mvn clean verify`, incluido empaquetado Spring Boot.

| Aplicación | Backend H2 | Frontend | Resultado build |
|---|---|---|---|
| Formulario | 66 correctas, 4 opcionales omitidas de 70 descubiertas | 13 correctas | Correcto |
| SIGER | 87 correctas, sin omisiones | 14 correctas | Correcto |

Las 4 omitidas del público son pruebas opcionales de carga/recuperación, no fallos. No se ejecutó nuevamente la batería MySQL, carga, caída de procesos ni autenticación real de Gobierno Digital durante esta publicación. Los resultados MySQL de entregas anteriores conservan su fecha y no se presentan como pruebas de esta iteración.

También se comprobó sintaxis PowerShell de scripts propios/compartidos y, sobre los servicios reales del workspace original, HTTPS confiable, aislamiento de cookies por host y logout independiente. El preflight público confirmó resolver Windows a loopback, callback/logout en backend, React empaquetado, cookie Secure/HttpOnly, y redirección de autorización con seguimiento desactivado. No se contactó al proveedor ni se acreditó una nueva autenticación Sandbox.

Se añade un workflow de GitHub Actions por repositorio para repetir su build H2 en Windows con Java 25 y Node 24, sin credenciales institucionales ni proveedores gubernamentales. Su resultado remoto debe consultarse en Actions; la ejecución local no acredita por sí sola el entorno remoto.

## Exclusiones y revisión previa

No se incluyen credenciales, archivos DPAPI, claves privadas, certificados de equipo, bases, logs, binarios compilados, dependencias instaladas, capturas de autenticación ni el directorio de evidencias. Los documentos históricos pueden referirse a esas evidencias no distribuidas. Se excluyen también scripts/informe detallado del laboratorio PISEE, que dependen de materiales e identidades institucionales externos. Los fixtures de contratos sintéticos con claves públicas y firmas se conservan.

Escaneo Gitleaks 8.30.1 sobre las dos copias: nueve coincidencias revisadas, todas por delimitadores PEM del lector, nombres de variables y una contraseña sintética fija de pruebas HTTP. No contienen material privado PEM ni una credencial operativa. Además se contrastaron todos los archivos candidatos con 19 valores de credenciales/secrets conocidos del entorno local, sin imprimirlos: cero coincidencias. Se verificó ausencia de rutas y extensiones privadas en los candidatos. Esta revisión no incorpora esos valores ni informes privados a Git.

## Operación y límites

El arranque local conserva los JAR y bases existentes y deja ambos aplicativos en sus orígenes HTTPS de laboratorio. No se modifica DNS, firewall, confianza de certificados, servicios institucionales ni URIs de ClaveÚnica.

La publicación de código no habilita producción. Continúan los gates de recepción/NNA y acceso funcionario, autenticación sintética restringida y decisiones institucionales pendientes. Véanse el manual, los contratos y las reglas V8. No copiar `.local` al repositorio ni intentar resolver un arranque fallido borrando esquemas o regenerando comprobantes.
