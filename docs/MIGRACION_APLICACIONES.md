# Migración desde la estructura inicial

Se inspeccionaron antes de mover backend, frontend y scripts. Todo el código funcional correspondía al público: no había funcionalidades institucionales ni registros definitivos que migrar. Se detuvieron solo los procesos locales registrados antes de mover el JAR.

| Antes | Ahora |
|---|---|
| `backend/`, `front/`, `config/` | Dentro de `formulario-publico/`, conservando sus nombres |
| `scripts/Build-Siger.ps1` | `formulario-publico/scripts/Build-Formulario.ps1` |
| `scripts/SIGER.ps1` | `formulario-publico/scripts/Formulario.ps1` |
| Otros scripts ClaveÚnica | `formulario-publico/scripts/` |
| JAR `siger-0.1.0-SNAPSHOT.jar` | Público `formulario-publico-0.1.0-SNAPSHOT.jar`; institucional `siger-institucional-0.1.0-SNAPSHOT.jar` |
| `SIGER.cmd` abría el ciudadano | **Ahora usar `FORMULARIO.cmd`; `SIGER.cmd` inicia el institucional** |

1. Cierra Vite/Java de la ubicación antigua si aún estuvieran abiertos. No uses Vite para el callback HTTPS.
2. Desde la raíz: `pwsh -NoProfile -File .\scripts\Build-Todo.ps1`, o cada build independiente.
3. `FORMULARIO.cmd iniciar`: reutiliza `.local/https`, herramientas, CA y hosts; revalida HTTPS antes de habilitar Sandbox. No requiere registrar otra URI por mover código. Abre el origen HTTPS indicado.
4. `SIGER.cmd iniciar`: independiente. Puedes apagar cualquiera sin apagar el otro.
5. Para entrega local: `pwsh -File .\scripts\Demo-Integracion.ps1 -Interactiva`. La identidad sigue siendo ClaveÚnica Sandbox real; los archivos son solo una simulación de los repositorios productivos.

Las 18 pruebas originales CU y `docs/evidencias/` se conservan. Las sesiones previas expiran al reiniciar Java: inicia sesión otra vez. No había persistencia institucional ni migraciones SQL. `.local` permanece fuera de Git, incluidos los datos nuevos de demo. El TXT institucional externo no se copió. No se modificaron DNS público, Cloudflare, Cerofilas, QA, Producción o infraestructura.

Para retirar posteriormente la configuración local, detén ambas aplicaciones y ejecuta:

```powershell
# Solo quitar el bloque hosts necesita elevación; no forma parte de esta refactorización.
sudo pwsh -NoProfile -File .\formulario-publico\scripts\Set-LocalSandboxHosts.ps1 -Remove
pwsh -NoProfile -File .\formulario-publico\scripts\Trust-LocalSandboxCertificate.ps1 -Remove
```

`Remove-LocalSandboxCertificateFiles.ps1` puede borrar después certificados/llaves/diagnósticos de esta prueba, comprobando su ruta y retirando primero su CA. No borra `.local/demo`, que se debe limpiar con los procesos detenidos y según la política de datos acordada. No hay servicios Windows instalados.
