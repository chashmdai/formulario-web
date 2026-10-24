# Formulario Público

> Para el formulario completo y multimedia consolidados, comenzar por el [README principal](../README.md) y el [cierre vigente](../docs/CIERRE_CICLO_MAIN_2026-10-24.md). Los apartados siguientes conservan el alcance histórico de los lanzadores Windows y de NNA provisional; no describen una habilitación productiva ni el consumidor SIGER completo.

**Recepción vigente:** [Comunicación institucional y comprobante v3](../docs/REGISTRO_COMUNICACION.md). El arranque normal conserva CU sin MySQL; para enviar y obtener folio utiliza Recepcion-Local.ps1. El perfil de archivos es histórico y no genera folio institucional.

Aplicación ciudadana independiente: `backend` Java 25 / Boot 4.1.1, `front` React 19 / TypeScript. React se empaqueta en **su propio JAR público**; ClaveÚnica, identidad y sesión pertenecen exclusivamente aquí.

Desde esta carpeta:

Si está ejecutándose, detén primero el proceso antes del build; alternativamente usa `iniciar -Reconstruir`, que lo detiene y recompila.

```powershell
pwsh -NoProfile -File .\scripts\Build-Formulario.ps1
pwsh -NoProfile -File .\scripts\Formulario.ps1 iniciar
pwsh -NoProfile -File .\scripts\Formulario.ps1 estado
pwsh -NoProfile -File .\scripts\Formulario.ps1 detener
```

El script local busca `../../CLAVEUNICATEST/DefensoriadelaNinez.txt`, selecciona solo Sandbox y reutiliza `.local` de la raíz del repositorio. No necesita SIGER Institucional. Origen HTTPS: `https://claveunica.formularioadulto.defensorianinez.gob.cl`, callback `/redirect`, retorno logout `/logout`. Se comprueban resolver del sistema, TLS, cookies y rutas antes de habilitar inicio. No se cambian URIs registradas.

Configuración externa en `config/`, conservada para los tres ambientes. Las variables `CU_*` y `SIGER_PUBLIC_ORIGIN` solo pertenecen a este backend. No se empaquetan secretos. El perfil de archivos `local-integration-demo` está restringido a Sandbox/loopback y no habilita recepción productiva.

Para editar solo UI: en `front`, `npm ci` y `npm run dev`. Vite:5173 no es el origen para el callback HTTPS registrado. Su proxy apunta solo al backend público. Pruebas: `npm test` en `front`, `mvn test` en `backend`. Simulaciones de proveedor únicamente en pruebas.
# Formulario NNA provisional · 19-10-2026

Entrada independiente `/?formulario=nna`, cerrada por defecto y habilitable únicamente en laboratorio. [Script madre y comandos](../docs/FORMULARIO_NNA_PROVISIONAL.md), [entrega y pruebas](../docs/ENTREGA_NNA_2026-10-19.md). No requiere ClaveÚnica ni RUN; los datos son declarados. El arranque habitual de ClaveÚnica se conserva. Adulto y NNA comienzan vacíos al recargar; no hay recuperación entre sesiones.

