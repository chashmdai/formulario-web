> **Actualización 24/10/2026:** adultos y NNA ya cuentan con recorridos React integrados y custodia pública real en laboratorio. Este informe conserva el diagnóstico anterior. Estado vigente, pruebas y límites: [Cierre de adultos](CIERRE_ADULTOS_2026-10-24.md) y [checkpoint](CHECKPOINT_2026-10-24.md). Continúan pendientes el disparador automático RUN→SRCeI del formulario y la evolución autorizada del consumidor institucional; no se declara concluido el Encargo Maestro.

# Informe de ejecución verificable — 23 octubre 2026

**El encargo integral sigue abierto.** Se completaron y comprobaron la preparación Linux, el cliente SRCeI y Nodo DEV, el laboratorio MySQL/restauración, el empaquetado Docker, ClaveÚnica Sandbox real por HTTPS y dos tramos de la réplica visual. Todavía no existe el formulario completo conectado de extremo a extremo. No se entregan simulaciones como funcionalidades reales.

## Baseline y estado actual

Solo se modificó `formulario-web`, rama local `integracion-siger-linux-2026`, desde `8a140bf`. Referencias de solo lectura: `ddn-formulario` `13f9cf6` y `sistema-de-requerimientos` `86cfe31`; fuentes limpias al cierre. Git/SSH e identidad existentes conservados. Sin push, publicación, cambios al DNS público o consultas productivas.

| Capacidad | Baseline → estado actual | Evidencia y límite |
|---|---|---|
| Linux/build | Dependencias Linux ausentes → REAL_VALIDADO | WSL2 Ubuntu 24.04.5 x86-64, systemd, herramientas nativas y build completo |
| React | UI mínima → PARCIAL / REAL_VALIDADO acotado | Inicio fiel y aviso adulto/alternativas; el resto de formularios sigue pendiente |
| CU Sandbox | No probado en este equipo → REAL_VALIDADO | Dos ingresos reales: autorización, callback oficial, código/token/userinfo, identidad backend, sesión y logout. Segundo recorrido con el aviso empaquetado |
| CU QA / producción | No probado → NO HABILITADO | No se utilizaron sus secretos ni se ejecutaron consultas |
| Nodo PISEE | Binario institucional no probado → REAL_VALIDADO DEV | Contenedor Linux 2.4.0 Habilis, certificados activos concordantes, sin publicar 8084/8489 |
| Cliente SRCeI | Ausente → REAL_VALIDADO DEV + SIMULADO_VALIDADO | Dos consultas acotadas con RUN autorizado; diez elementos XML, doce atributos normalizados en Java. Quince pruebas sintéticas nuevas |
| SRCeI en formulario | Ausente → PENDIENTE | Sin endpoint ciudadano, enriquecimiento ligado a sesión ni persistencia de procedencia. Bean deshabilitado en la app |
| MySQL/Flyway | Código sin laboratorio Linux → REAL_VALIDADO | MySQL 8.4.11; batería backend, persistencia tras restart, respaldo y restauración aislada |
| HTTP V3 | Baseline funcional controlado → SIMULADO_VALIDADO conservado | Fixtures idénticos; firmas/idempotencia/ACK/contratos probados. Sin consumidor real desplegado ni cambio de contrato |
| Adulto sin CU | Ausente → PENDIENTE | La interfaz no inventa acceso manual: tras aviso informa indisponibilidad |
| NNA | Provisional → PARCIAL conservado | Recorrido mínimo original probado, sin réplica completa; recepción cerrada en app actual |
| Adjuntos | Ausentes → PENDIENTE | Sin custodia/escaneo/descarga real; no se considera implementada la simulación HTML |
| Despliegue transferible | Sin Docker → PARCIAL | JAR React/Java y Compose separados operativos; falta Compose integrado y operación institucional |

La evidencia original y matriz de brechas están en [auditoría](AUDITORIA_LINUX_2026-10-23.md). La compatibilidad de nuevos campos con SIGER no se presume: todavía no se evolucionaron DTO, migraciones o consumidor para ellos.

Incidencia de custodia: durante una reconsulta del documento rector se mostró por error en la salida de una herramienta una línea con la contraseña temporal de la cuenta WSL. No se reproduce aquí el valor. La exposición ocurrió en esta conversación, no en Git ni en una imagen; se informó al responsable y las lecturas posteriores excluyeron la sección sensible. No se rotó la cuenta sin una instrucción específica. El escaneo de candidatos versionables no sustituye la revisión de esta incidencia.

## Herramientas y pruebas

Host Linux: Temurin 25.0.5+8, Maven 3.9.15, Node 24.22.1, npm 11.19.1, Docker Engine 29.9.2, Compose 5.6.1, Python 3.12.3, OpenSSL 3.0.13, ripgrep/Poppler. La construcción Docker usa Node 24, Maven 3.9.15 y Temurin Java 25; la imagen Java usada en esa construcción informa 25.0.3. PISEE 2.4.0/xmlsec1 1.2.37; Caddy 2.11.7. Las imágenes permanecen locales y los secretos se montan desde archivos privados.

| Verificación | Resultado |
|---|---|
| Baseline frontend | lint/build y 13 pruebas correctas |
| Frontend actual | lint, TypeScript/Vite y 17 pruebas correctas, también dentro del build Docker |
| Backend baseline | 70 descubiertas: 66 correctas, 4 opcionales omitidas |
| Backend final H2 | 85 descubiertas: 81 correctas, 4 opcionales omitidas; build JAR/Docker correcto |
| Backend con MySQL real | Misma batería 85/81/4, sin fallos; no se confunde con ejecución H2 |
| Consumidor de referencia | 87 pruebas H2 correctas, fuentes sin modificar; sin prueba contra consumidor institucional en red |
| SRCeI DEV real | Dos consultas autorizadas exitosas, sin guardar valores en Git/logs/informes; etiqueta `entradaSRCel` U+006C confirmada |
| Backup/restore | mysqldump transaccional + gzip/SHA-256; restauración en volumen separado, comparación de cuerpos/ACK/estado/huellas/Flyway y reinicio del origen |
| CU Sandbox real | Dos ingresos completos y logout con invalidación backend; sin HAR, códigos, tokens ni capturas de identidad |
| Paridad inicio | PNG iniciales iguales por SHA-256 en 1440×900 y 390×844, tanto Vite como JAR anterior al aviso |
| Paridad aviso y alternativas | En ambos tamaños: texto, rectángulo y foco iguales. Diferencias JPEG >16/255: aviso escritorio 0,00463%; restantes tres estados 0%. Inspección visual realizada |
| Credenciales candidatas | Escaneo contra nueve valores reservados conocidos: cero coincidencias en archivos candidatos. No equivale a demostrar ausencia universal de secretos |

Cuatro pruebas opcionales omitidas permanecen identificadas como omisiones, no como éxitos. Las pruebas sintéticas no contactan a CU ni SRCeI. El respaldo restaurado contenía una entrega sintética; no es restauración integral del sistema ni copia fuera del equipo.

## Servicios, datos y custodia

En ejecución: `siger-app-lab-app-1`, `siger-sandbox-https-caddy-1`, `siger-mysql-lab-mysql-1`, `siger-pisee-dev-pisee-1`. App/MySQL/PISEE saludables; Caddy comprobado por HTTPS. Puerto app `127.0.0.1:18080`; HTTPS `127.0.0.1:443`; MySQL de laboratorio `127.0.0.1:13306`. No se interfirió con el servicio ajeno en 8080. Vite 5173 y servidor del prototipo 8181 son herramientas locales de revisión, sin función productiva.

Volúmenes conservados: `siger-mysql-lab_mysql-lab`, `siger-mysql-lab_mysql-restore` y `siger-mysql-initcheck_mysql-lab`. Restauración e inicialización independiente están detenidas. Estado PISEE y secretos permanecen en directorios privados bajo `/home/nexbyteproveedor/.local/share/siger`, no en Git ni capas de imagen. La app monta solo su archivo de configuración, Caddy solo hoja TLS/clave de sitio, nunca la clave de CA. Logs de app/MySQL rotados; sin persistencia de logs de cuerpos PISEE o accesos Caddy.

Hosts Windows y WSL respaldados; solo se añadió la resolución autorizada. CA confiada únicamente en el usuario Windows. Certificado de hoja de 30 días, SAN exacto. Origen, redirect y logout oficiales conservados. El proxy sobrescribe cabeceras recibidas; Tomcat confía solo loopback e IP concreta del proxy. Resolución, confianza TLS normal, HSTS/cookie y rechazo de Host incoherente comprobados. [Procedimiento y reversión acotada](SANDBOX_HTTPS_WSL_2026-10-23.md).

## Commits locales y reproducción

- `8b11eac`: Nodo Linux y cliente PISEE/SRCeI privado.
- `a748613`: JAR/Docker Linux, MySQL y restauración.
- `ced8ee7`: inicio institucional y comparación visual.
- `60820ef`: HTTPS local WSL y CU Sandbox real.
- `cd97b94`: aviso de privacidad y segundo acceso CU real.
- Este informe y el ajuste a montaje individual se conservan en un commit posterior de documentación/custodia.

Comandos Linux desde `/home/nexbyteproveedor/proyectos/formulario-web`; Docker requiere permisos disponibles o elevación Linux normal. No reejecutar preparadores sobre destinos existentes ni lanzar consultas reales como parte de una batería automática.

```bash
bash scripts/linux/compilar.sh
docker build -t siger-formulario:linux-20261023 .
APP_PRIVATE_DIR=/home/nexbyteproveedor/.local/share/siger/app-cerrada docker compose -f infra/compose.app-lab.yml up -d --wait
SANDBOX_TLS_DIR=/home/nexbyteproveedor/.local/share/siger/sandbox-https docker compose -f infra/compose.sandbox-https.yml up -d
PISEE_PRIVATE_DIR=/home/nexbyteproveedor/.local/share/siger/pisee-dev docker compose -f infra/compose.pisee.yml up -d --wait
MYSQL_PRIVATE_DIR=/home/nexbyteproveedor/.local/share/siger/mysql-lab docker compose -f infra/compose.mysql-lab.yml up -d --wait mysql
bash scripts/linux/probar-mysql.sh /home/nexbyteproveedor/.local/share/siger/mysql-lab --autorizar-laboratorio
```

El último comando opera sobre el esquema sintético de laboratorio y elimina sus datos de pruebas; no apuntarlo a bases con valor. El inicio CU necesita además el procedimiento de preflight bloqueado/activación de la guía HTTPS. Build, restauración, actualización y rollback están en [guía Linux](LINUX_DOCKER.md); consulta DEV acotada en [guía PISEE](PISEE_LINUX.md); comparación y cobertura en [matriz de paridad](PARIDAD_MOCKUP_2026-10-23.md).

## Trabajo pendiente verificable

1. Replicar los pasos adultos/NNA, motivo/subpreguntas, personas relacionadas, región/comuna, contacto seguro, revisión/errores/confirmación y todos los temas/responsive del HTML. Mantener la distinción entre Comunicación y objetos institucionales posteriores.
2. Implementar ingreso adulto declarado, RUN/DV y fallback, autorización/antiabuso y enriquecimiento ligado a sesión. SRCeI nunca autentica ni devuelve libremente sus atributos; NNA sin RUN/CU no se bloquea por esa ausencia.
3. Persistir atributos/procedencia/discrepancias y elección versionada del canal; diseñar DTO/migraciones de nuevos campos sin alterar huellas, sobres o comprobantes históricos. Probar compatibilidad con fixtures y delimitar qué necesita evolución del consumidor SIGER.
4. Implementar adjuntos privados con límites, verificación de contenido/cuarentena/escaneo, autorización y limpieza; respaldo/restauración conjunta de datos, claves históricas y archivos.
5. Integrar app/MySQL/PISEE/proxy con readiness, JDBC TLS, redes mínimas, operación de cuentas DML/migrador y monitoreo. Preparar transferencia a Ubuntu Server de referencia; no basta copiar imágenes. Preparar configuración de Tunnel futuro sin publicarlo.
6. Ejecutar pruebas de recorridos completos, seguridad/IDOR/CSRF/abuso y accesibilidad; restauración fuera del equipo y consumidor real bajo autorización institucional. QA/certificación/producción requieren sus condiciones expresas.

Estos pendientes son trabajo de implementación y validación, no solicitudes de nueva aprobación para decisiones técnicas ordinarias. HTTPS/CU Sandbox ya no bloquean el avance. No se declara concluido el Encargo Maestro.
