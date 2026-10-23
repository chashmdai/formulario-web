# Formulario Público

Código vigente de laboratorio de SIGER 2026, desarrollado por Nexbyte para validación de la Defensoría de la Niñez. No es habilitación productiva.

- **[Linux/Docker: build, laboratorios y restauración verificada](docs/LINUX_DOCKER.md)**.
- [PISEE DEV Linux y adaptador SRCeI](docs/PISEE_LINUX.md).
- [ClaveÚnica Sandbox real por HTTPS local en Docker/WSL y reversión](docs/SANDBOX_HTTPS_WSL_2026-10-23.md).
- [Auditoría y estado de migración del mockup](docs/AUDITORIA_LINUX_2026-10-23.md), [paridad visual](docs/PARIDAD_MOCKUP_2026-10-23.md).

- **[Manual Windows: instalación, configuración, compilación y arranque](docs/MANUAL_REPOSITORIOS.md)**.
- [Guía de esta aplicación](formulario-publico/README.md).
- [Contratos de integración](contratos/README.md).
- [Arquitectura de las dos aplicaciones](docs/ARQUITECTURA_APLICACIONES.md).
- [Reglas y operación del núcleo V8](docs/ANTECEDENTES_Y_REAPERTURAS_LAB.md).

## Compilar esta aplicación

Java 25, Maven 3.9+, Node 24 y PowerShell 7.4+.

```powershell
pwsh -NoProfile -File .\formulario-publico\scripts\Build-Formulario.ps1
```

El build verifica frontend y backend y empaqueta React en su propio JAR. No requiere clonar el otro aplicativo. Para el laboratorio integrado MySQL/HTTPS y las cuentas sintéticas, seguir el manual; no basta ejecutar un JAR sin configuración.

El otro repositorio es [sistema-de-requerimientos](https://github.com/chashmdai/sistema-de-requerimientos). Las aplicaciones tienen bases, sesiones y despliegues independientes. SIGER inicia las conexiones HTTPS de recuperación; el navegador ciudadano no llama a SIGER.

Se excluyen secretos, certificados privados, bases, logs y evidencias de autenticación. Los fixtures son sintéticos y sus claves públicas permiten verificar firmas históricas. La documentación de entregas anteriores conserva contexto y resultados anteriores; sus capturas y archivos privados de evidencia no se publican.
