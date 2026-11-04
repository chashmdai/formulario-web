# Actualización productiva — favicon institucional

04/11/2026, America/Santiago. Misma instalación `/srv/ddn-formulario-produccion` y dominio oficial. Actualización solicitada por el operador; sin migraciones ni cambios de contratos.

- Fuente de la imagen: `main`, commit `c42bc4fb913c85001e01c6ba4c7b332a530ca448`.
- Imagen instalada: `sha256:6fa0f5d53cc72e98fd9789537bf3e98269826be7e4ea9fbbd40158651ffaba6b`.
- JAR SHA-256: `5f778552618fd734af4d04c8cf511d67b0d80a21756c57d9e8ab81abf81fbaaa`.
- Imagen anterior conservada para reversión: `sha256:8bf75004aa68c3ebdb5fe3f22b18296f85a66c3e5a1653a40ae9745d9ce5ce08`.

Archivo de seis imágenes instalado: `software/images-favicon-20261104.tar`, SHA-256 `1d24e757083718128dd3b00741983a971a76f473473c6f99b23f302e245826ca`.

## Corrección y verificación

Se usó el PNG adjuntado por el operador, conservando transparencia y proporción al codificar los iconos. Se distribuyen `favicon.png` de 192×192 e ICO de 16/32/48/64 píxeles dentro del JAR. La portada React y la página de acceso temporal declaran el icono con versión para renovar la caché.

Los dos recursos públicos exactos admiten GET/HEAD sin contraseña; las API conservan sus barreras. No se habilitó acceso anónimo general a los recursos del Formulario.

- Docker build: lint, React **219 pruebas**, TypeScript/Vite y backend **424 pruebas, cero fallos/errores, dos omisiones opt-in**. Incluye comprobación GET/HEAD de ambos iconos y protecciones de API existentes.
- Firefox contra el dominio oficial, TLS validado: PNG decodificado de 192×192, ICO HTTP 200 `image/x-icon`, API ciudadana anónima HTTP 401.
- Verificación pública autenticada: recepción adultos/NNA y adjuntos habilitados; Bearer técnico 401/401/404 sobre recurso inexistente, sin reclamar entregas.
- Configuración institucional, claves, certificados y contenedores históricos comparados antes/después, sin cambios. Sin consultas personales ni Comunicaciones de prueba.

## Operación y reversión

Admisión cerrada mediante CAS durante el respaldo/actualización y restaurada a su estado anterior: **recepción habilitada, recuperación PULL cerrada**. El reinicio de la aplicación invalida sesiones/borradores HTTP; la custodia durable permanece en MySQL y archivos persistentes.

Respaldo previo cifrado y verificado: `/srv/ddn-formulario-respaldos/productivo-diario/formulario-20261104T183237Z.gpg`, SHA-256 `55d8c967f8b07477101c0befa77ac12d79bcde9de686fadf62e4d18e3a27abf4`. Configuración anterior y metadatos conservados privadamente en `updates/favicon-20261104/`. La reversión repone el `FORM_IMAGE` anterior y recrea solo `app`, con cierre/reapertura CAS y comprobación de salud; no restaura una base antigua ni borra volúmenes.

Se conserva el mismo Bearer y la misma identidad Ed25519; confianza ACK Windows y E2E físico siguen pendientes. La contraseña y su caducidad no cambiaron. Esta actualización sustituye únicamente el artefacto de aplicación del [corte de publicación RC1](PUBLICACION_RC1_MANIFIESTO_2026-11-04.json); no reescribe esa evidencia histórica.
