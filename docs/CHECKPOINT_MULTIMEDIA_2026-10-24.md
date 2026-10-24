# Checkpoint multimedia · 24/10/2026

> Documento histórico de su unidad. El [cierre y checkpoint vigentes de main](CIERRE_CICLO_MAIN_2026-10-24.md) registra la consolidación posterior autorizada. Las indicaciones anteriores de no publicar corresponden al estado previo a ese cierre.

## Estado conservado

Repositorio único: `/home/nexbyteproveedor/proyectos/formulario-web`. Rama **`multimedia-adjuntos-2026-10-24`**, base `origin/main@c1e4ead`. Código **c67e359**; operación/QA **74e4b6e**; este checkpoint y la prueba final de concurrencia se confirman juntos en el commit documental/de verificación siguiente. `git log -3` identifica los tres sin reconstruir nada. No se hizo push ni se movió main.

Leer [informe](INFORME_MULTIMEDIA_2026-10-24.md), [ADR](ADR_MULTIMEDIA_2026-10-24.md), [política](POLITICA_MULTIMEDIA_2026-10-24.md), [operación](OPERACION_MULTIMEDIA.md) y [contrato 1.1](../contratos/formulario-completo-multimedia-v1.1.md). El [visor institucional](VISOR_INSTITUCIONAL_MULTIMEDIA.md) distingue origen ciudadano de documentos de gestión; su implementación funcionaria sigue fuera de este repositorio.

## Verificaciones finales

- React: 160 correctas; lint/build correctos. Backend: 199 descubiertas, 197 correctas y dos omisiones explícitas en H2 y MySQL. Worker Java opt-in real: una correcta separada.
- Docker revisión compilado y probado (160 React; 198 backend en el snapshot de código anterior a la prueba adicional final de concurrencia).
- Inspector: 17 formatos sintéticos aceptados, 13 casos rechazados; ClamAV real, incluido EICAR después de 12 MiB.
- HTTP real: 16 MiB + 7 bytes reanudados en sesión, SHA correcto, custodia sintética idempotente y recuperación institucional después de reiniciar Spring. ACK criptográfico con doble conserva original/cuerpo firmado.
- Tres recorridos visuales integrados NNA, escritorio/móvil, oscuro/contraste, 130 %/reduced-motion; sin envío desde navegador. Los recorridos adultos y ClaveÚnica conservan regresiones con dobles, sin nueva consulta gubernamental.
- Respaldo/restauración en destino nuevo: 57 archivos de almacén, 32 FILE, un BLOB y una custodia sintéticos. Hashes/CHECKSUM comprobados. Respaldar con app activa se rechaza. Cinco pruebas Python correctas.
- Baseline protegido: 13 custodias, 13 adjuntos, siete migraciones y CHECKSUM conservados. No hubo recreación ni limpieza de volúmenes/bases.

## Servicios y artefactos

- **Baseline:** HTTPS 443 → nativo 18443, esquema `robust_public_local` V7. Sigue activo e independiente. No dirigirle el JAR multimedia ni ejecutar V8 allí durante esta revisión.
- **Multimedia:** HTTPS `https://claveunica.formularioadulto.defensorianinez.gob.cl:19443`, esquema `robust_public_media_lab` V8, cookie separada, proveedores externos deshabilitados y callback cerrado. Proceso de revisión activo; archivo PID privado. Tiene 47 referencias FILE, un BLOB sintético y una custodia sintética después de la última QA.
- **Workers:** proyecto Compose `siger-multimedia-lab`, inspector y ClamAV por Unix socket, sin red. No cambiar otros proyectos Docker.
- **Privado:** `/home/nexbyteproveedor/.local/share/siger/multimedia-lab` contiene config, PID/log, runtime.jar, IPC y `almacen`. Almacén 0700, originales 0400. Nunca agregar a Git ni servirlo estáticamente.
- **Restauración:** `robust_public_media_restore_20261024`, directorio privado `multimedia-restaurado-20261024`; respaldo en `multimedia-backups/verificacion-20261024`, bajo el mismo directorio privado institucional. Se conservan. Las claves y confianza históricas se custodian aparte.
- **Build:** `.local/multimedia-target`; nunca usar el `backend/target` activo del baseline. JAR probado SHA-256 `24ccc1674bc3be3168158997a3d4c8665493933a14febc96ff51945230381d27`.
- **Imagen:** `siger-formulario:multimedia-revision`, ID `sha256:50571033617db67e785739318d590f28b928c68d6065f20104063f2f0193c599`. No desplegada sobre el baseline.
- **Evidencias:** `.local/evidencias/multimedia-*` y capturas en `multimedia-navegador`; no se publican respaldos, cookies o datos privados.

## Próximo punto exacto

Primero revisar `git status`, `git diff` y los tres commits. Conservar cualquier modificación nueva del usuario. La unidad se entrega para revisión; no mezclar ni publicar automáticamente.

Pendientes reales: recuperación ciudadana de borradores entre sesiones/reinicios; calificación de máximos 150/250 MiB y red móvil/proxy publicado; respaldo externo y recuperación criptográfica en otro equipo; actualización operativa de firmas/motores; adaptación del consumidor 1.1 y visor institucional, cuando se autorice esa unidad. DOC/HEIC/HEIF, conversión Word y visor PDF completo siguen fuera del soporte habilitado.

No sustituir la limitación de sesión por un token improvisado ni afirmar que el reinicio conserva autenticación OIDC. No serializar credenciales al disco. MySQL/archivos y recuperación institucional ya están comprobados: no reconstruir infraestructura ni borrarlos para retomar.

El Formulario Público completo, ClaveÚnica y SRCeI permanecen en el baseline conocido. La ampliación está apagada por defecto y habilitada solamente en su laboratorio separado. No hay habilitación productiva, consulta gubernamental, cambio en SIGER institucional, estrés ni push.
