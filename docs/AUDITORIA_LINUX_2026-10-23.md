# Auditoría y registro de implementación Linux

Encargo rector leído íntegramente el 23-10-2026 desde el paquete institucional externo. No se copia al repositorio porque contiene instrucciones con credenciales locales. Solo `formulario-web` recibe cambios; prototipo y SIGER se conservan como referencias de lectura. No hay push ni publicación.

## Baseline

Referencias: público `8a140bf`, mockup `13f9cf6`, consumidor `86cfe31`. Los tres clones estaban limpios. Rama local de ejecución: `integracion-siger-linux-2026`.

Entorno comprobado: Ubuntu 24.04.5 WSL2 x86-64, systemd, usuario institucional, SSH GitHub existente operativo. No se alteró identidad Git ni claves SSH. Al inicio no había JDK, Node ni motor Docker Linux; Maven/npm resueltos desde Windows no constituyen herramientas Linux. Se instalaron Temurin 25.0.5, Node 24.22.1 (SHA-256 contrastado con distribución oficial), Docker Engine 29.9.2/Compose 5.6.1, utilidades de lectura y diagnóstico. Maven Ubuntu 3.8.7 ejecutó el baseline; se requiere actualizar a 3.9+ para la herramienta final.

| Capacidad | Estado inicial | Evidencia | Brecha | Cambio propuesto | Riesgo |
|---|---|---|---|---|---|
| React 19/TypeScript | FUNCIONAL/PROBADO | lint, 13 pruebas y build Linux correctos | Interfaz mínima muy distinta del HTML | Migrar componentes y estilos conservando recorridos | Omitir condicionales del prototipo |
| Spring Boot/Java 25 | FUNCIONAL/PROBADO | 70 descubiertas, 66 correctas, 4 opcionales omitidas | Lanzadores Windows | Build y operación Linux separados | Confundir H2 con MySQL |
| OIDC CU | IMPLEMENTADO/NO PROBADO en este equipo | Pruebas de flujo controlado, nonce, state y JWT pasan | Callback/TLS institucional en WSL sin validar | Mantener implementación y gate; preflight por ambiente | No habilitar artificialmente callback |
| Custodia/idempotencia/firma | FUNCIONAL/PROBADO en H2 | Pruebas originales de recepción/HTTP/contratos | Revalidar con MySQL 8.4 Linux | Laboratorio aislado persistente | No cambiar firmas históricas |
| HTTP V3/SIGER | SIMULADO_VALIDADO | Fixtures firmados y pruebas originales | Consumidor real separado, no desplegado aquí | Conservar bytes/DTO, pruebas contra fixtures | Campos nuevos no consumidos por SIGER |
| Adulto sin CU | AUSENTE | Rutas públicas requieren rol ciudadano | RUN declarado y fallback | Flujo distinto de autenticación CU | Enumeración de registros |
| NNA sin RUN/CU | PARCIAL | 3 pruebas UI y tests HTTP; gate de laboratorio | Solo nombres/región/relato, sin recorrido completo | Preservar requisitos mínimos y replicar preguntas | No elevar declaración a identidad autenticada |
| SRCeI | AUSENTE | Proyección declara NO_HABILITADO | No existe cliente HTTP/XML | Adaptador seguro, errores tipados y pruebas | XXE, fuga de atributos, red institucional |
| Nodo PISEE Linux | IMPLEMENTADO/NO PROBADO | ELF64 x86-64 estático; SHA-256 registrado | Arranque/central y consulta real pendientes | Contenedor privado con xmlsec1 | Credenciales y estado Windows |
| Adjuntos | AUSENTE | No API/almacenamiento de adjuntos | HTML solo muestra metadatos locales | Custodia privada, cuotas y autorización | Descarga IDOR y contenido peligroso |
| Docker/MySQL/backups | AUSENTE | No Dockerfile ni Compose | Reproducibilidad y restauración | Servicios separados y secretos externos | Exposición de puertos/pérdida de datos |

Pruebas baseline: `npm ci`, `npm run lint`, `npm test`, `npm run build`; `mvn -B -ntp test`. Logs completos privados en `.local/evidencias`, excluidos de Git. No se contactó a proveedores desde las pruebas originales.

## Decisiones y discrepancias

- El informe PISEE del 21/10 documenta respuesta vacía. La referencia posterior informa diez atributos; no son evidencia equivalente. Se requiere reproducción Linux autorizada.
- La etiqueta histórica `entradaSRCeI` contradice `entradaSRCel` (U+006C final) de la referencia posterior. No se tratará como confirmada hasta contrastar respuesta efectiva.
- El manual Nodo §4.4.2.A exige `xmlsec1` para servicios antiguos SRCeI aunque el ejecutable Go sea estático.
- El HTML inventa CU, atributos registrales, envíos y folios. Solo su presentación/interacción es referencia; la identidad debe proceder de sesión backend y el folio del ACK firmado de SIGER.
- Los gates NNA existentes solo permiten laboratorio HTTPS/loopback. No deben eliminarse para hacer pasar un contenedor; cualquier extensión deberá ser explícita y probada.

## Secuencia de trabajo

1. Baseline y material institucional; validar binario/certificados sin revelar secretos.
2. Nodo privado DEV y adaptador SRCeI con pruebas completas, parciales y fallos.
3. Réplica React por pantallas y paridad visual escritorio/móvil.
4. Validación/persistencia de campos y adjuntos sin degradar HTTP V3.
5. Compose, MySQL real, reinicios, backup/restore y guías de traslado.

Este registro es incremental. Ninguna capacidad pendiente se considera terminada por figurar en la secuencia.

## Avance posterior al baseline

- **REAL_VALIDADO:** Nodo Linux 2.4.0 Habilis en contenedor no-root, autenticación local, sin puertos publicados, pares activos comprobados; respuesta SRCeI DEV con diez elementos y etiqueta `entradaSRCel` confirmada. Adaptador Java obtuvo resultado completo en esa red. No se persistieron ni expusieron valores registrales en la aplicación ciudadana.
- **SIMULADO_VALIDADO:** quince pruebas nuevas del cliente/parser con datos sintéticos, sin proveedor; regresión backend H2 81 correctas/4 opcionales omitidas de 85; misma batería en MySQL 8.4.11. Consumidor original: 87 pruebas H2 correctas, sin cambios de fuentes. Fixtures entre clones idénticos.
- **REAL_VALIDADO:** Maven final 3.9.15; build Linux frontend/backend/JAR y Docker multietapa. Bootstrap del JAR cerrado, saludable, localhost:18080. MySQL con cuenta DML/migrador, inicialización fresca comprobada, respaldo/restauración real aislada y persistencia tras reinicio.
- **REAL_VALIDADO acotado:** inicio React extraído a componentes; PNG de inicio idénticos al HTML en escritorio y móvil, tanto Vite como JAR. Se conservan los recorridos previos y el nuevo inicio es opt-in. Dieciséis pruebas UI correctas; lint y build correctos.
- **REAL_VALIDADO tras autorización expresa posterior:** CU Sandbox real en WSL, con hosts Windows/WSL respaldados, CA solo CurrentUser y proxy Docker HTTPS. Se conservaron callbacks oficiales. Autorización, callback, token/userinfo, sesión backend y cierre verificados; QA/producción no habilitados. [Procedimiento y reversión](SANDBOX_HTTPS_WSL_2026-10-23.md).
- **REAL_VALIDADO acotado:** aviso de privacidad adulto, rechazo/alternativas/reelección replicados; geometría/texto/foco iguales en ambos tamaños, 17 pruebas UI correctas. La elección sigue en memoria; registro servidor pendiente. Imagen Docker reconstruida con React y 81 pruebas backend correctas/4 opcionales omitidas.
- **PENDIENTE de implementación:** formularios completos y condicionales, adulto sin CU, enriquecimiento/persistencia registral, adjuntos, evolución de campos para consumidor y Compose integrado/operación productiva. No son bloqueos que requieran detener desarrollo independiente.

Detalles y reproducción: `PISEE_LINUX.md`, `LINUX_DOCKER.md`, `PARIDAD_MOCKUP_2026-10-23.md`. El encargo integral sigue abierto.
