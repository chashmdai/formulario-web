# Cierre de ingeniería del Formulario Público y agente de custodia

**25/10/2026. Baseline publicado recuperado: `20328063aaf840ada57684167d2106932b4402ab`.**

**EL FORMULARIO PÚBLICO NO SE ENCUENTRA HABILITADO PARA PRODUCCIÓN.**

Este cierre consolida la integración Windows/MySQL anterior, audita código y dependencias, corrige defectos acotados y entrega el README maestro. No implementa nuevos recorridos ni una arquitectura alternativa. No modifica, compila, migra o despliega SIGER, `siger_institucional_windows_lab`, sus usuarios o `C:\req`. No ocupa 8080/8081, activa tareas/emisión/retirada, consulta Gobierno o expone servicios.

## Recuperación y trazabilidad

Al comenzar se conservó la rama `agente-windows-integracion-tls-2026-10-25` con todos los cambios sin commit. Main/origin/main seguían en el baseline: los resultados Windows/MySQL aún no estaban publicados. Se revisaron status/diff/historial y referencias remotas antes de modificar.

| Commit de implementación | Contenido |
|---|---|
| `f3362e5` | Agente 1.1, acreditación/compuerta Windows, MySQL TLS/QA, read-only, clave privada protegida, CI Windows y parches Jackson/Tomcat |
| `fe40499` | Saneamiento de temporales vencidos, cierre Office legacy, UI/validaciones y parche undici |
| Commit documental de esta revisión | README maestro, informes y referencias especializadas coherentes |

El SHA final de publicación es el HEAD que contiene este documento y ambos commits, verificado con `git rev-parse HEAD origin/main` y `git ls-remote origin refs/heads/main`. El informe final y la evidencia privada de publicación registran ese valor exacto y el run, sin una autorreferencia imposible de SHA dentro del archivo que lo determina.

[Historial de main](https://github.com/chashmdai/formulario-web/commits/main/) · [GitHub Actions por main](https://github.com/chashmdai/formulario-web/actions?query=branch%3Amain) · [workflow versionado](../.github/workflows/ci.yml).

## Auditoría: alcance y correcciones

Se inspeccionaron backend, frontend, persistencia, contratos/controladores, identificación, adjuntos, custodia, operación Linux y agente Windows. La revisión combinó lectura, pruebas focales de reproducción, regresiones completas y consulta de avisos públicos. No es un pentest ni certifica todo el entorno productivo.

| Hallazgo comprobado | Corrección / prueba |
|---|---|
| Saneamiento manual seleccionaba cargas >24 h pero reutilizaba autorización de recorrido vencida a las 4 h | Lock directo y ordenado del recorrido/carga, estado admitido y ausencia de citizen_submission revalidados; tres tests de vencimiento, cancelado y recibido, con cuota/idempotencia |
| DOC por cabecera OLE2 y DOCX por ZIP mínimo podían aceptar macros/ActiveX/embebidos no detectados por un AV | Se reproducen dos aceptaciones indebidas; nuevas cargas Office legacy cerradas, sin tocar BLOB históricos; DOCX permanece en multimedia con inspector; tres tests backend y dos UI |
| Auxiliar administrativo TLS admitía parámetros JDBC ambiguos por contains | Parser estricto compartido, host/puerto/esquema local permitidos y VERIFY_IDENTITY/fallback exactos; 21 casos nativos sin conexiones |
| Clave privada del emisor leída sin validar enlace/permisos/límite | Archivo regular, ruta/ACL, NOFOLLOW, máximo 16 KiB; POSIX 0400/0600; seis tests focales y dos verificaciones nativas adicionales |
| Dependencias con avisos publicados | Jackson BOM 3.1.5 → 3.1.7, Tomcat 11.0.24 → 11.0.27 y undici dev 8.12.1 → 8.12.3; regresiones y nueva consulta |
| Redacción ciudadana conservaba “maqueta” | Se aclara que el envío no agenda entrevista; sin cambio de reglas/contrato |
| Documentación confundía preparación, laboratorio y activación | README ampliado, bloqueos productivos expresos, evidencias históricas separadas y límites de borrador/legacy/POSIX documentados |

El cierre de nuevas cargas DOC/DOCX legacy es una decisión de seguridad, no un cambio cosmético ni una retirada de soporte histórico. El controlador ya impide ese multipart cuando multimedia está habilitada; el perfil ampliado sigue validando DOCX con aislamiento. PDF/imágenes clásicos tienen comprobación básica de formato y ClamAV, descarga privada y sin preview inline: no se afirma análisis profundo de acciones PDF. Para anunciar el perfil ampliado se debe desplegar multimedia inspeccionado, no el camino clásico.

Fuentes de código: [CargasMultimedia](../formulario-publico/backend/src/main/java/cl/nexbyte/siger/multimedia/CargasMultimedia.java), [AdjuntosFormulario](../formulario-publico/backend/src/main/java/cl/nexbyte/siger/formulario/AdjuntosFormulario.java), [EmisorGarantiaCustodia](../formulario-publico/backend/src/main/java/cl/nexbyte/siger/custodia/EmisorGarantiaCustodia.java), [QA TLS](../agente-custodia/qa/MysqlTlsLab.java).

### Dependencias y alcance de la revisión

Se consultaron 102 coordenadas Maven resueltas contra OSV y npm audit (incluidas dependencias de desarrollo). El primer resultado señalaba siete avisos Jackson y tres Tomcat; npm agrupaba tres avisos de undici en un paquete high. La presencia de un aviso por versión no demuestra que se cumplan sus condiciones de explotación en este sistema. Se aplicaron parches en las mismas ramas compatibles.

Tras los parches: **OSV sin avisos para esas 102 coordenadas y npm audit sin avisos** en el momento de revisión. No cubre SO, binarios privados del Nodo, imágenes completas, firmware ni vulnerabilidades futuras; no es certificación de ausencia de vulnerabilidades.

Referencias primarias: [Jackson 3.1.7](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-cxp5-3px4-pw24), [avisos Tomcat 11 y correcciones](https://tomcat.apache.org/security-11.html), [undici corregido](https://github.com/nodejs/undici/security/advisories/GHSA-xjj6-f33q-929c). Se revisaron también los otros IDs devueltos por OSV; las respuestas/árboles públicos se conservan en evidencia local. No se incorporó un servicio de escaneo al runtime.

## Windows/MySQL: trabajo recuperado y cerrado

El [informe Windows/MySQL TLS](CIERRE_WINDOWS_MYSQL_TLS_2026-10-25.md) detalla respaldo de nueve archivos, CA local, SAN, ACL, truststore, recarga TLS y rollback. Se comprobó cero sesiones SQL ajenas antes de intervenir. No se reinició MySQL80 ni se cambió su política global de transporte/cuentas.

Las conexiones habituales utilizan DPAPI de aplicación, Connector/J y VERIFY_IDENTITY. Los fixtures están únicamente en siger_custodia_lab, sin datos reales. El agente lee; el preparador QA es quien inserta. Se comprueba read-only tanto en pool/driver como en sesión del motor y se rechazan DML.

Se ejecutó realmente Java 25 Windows por ruta absoluta. La restauración en otro proceso reconstruyó tablas temporales MySQL desde el paquete mientras los originales sintéticos de origen estaban desconectados, y repuso esos originales. No demuestra desastre físico ni otra infraestructura.

| Artefacto final instalado | Valor |
|---|---|
| Versión | `1.1.0-windows-lab` |
| Ruta | `C:\agente\bin\agente-custodia-1.1.0-windows-lab-476cfde631f9.jar` |
| SHA-256 | `476cfde631f9ab4fbf3793b6e97444f24fc81537afb57f3caaa60548c77ec790` |
| Tamaño | 12 008 133 bytes |
| Reproducibilidad | Segunda construcción idéntica |
| Runtime | Java 25.0.4.1 absoluto; Java 21/JAVA_HOME/PATH global conservados |
| Tarea | XML disabled preparado, no registrado |
| Emisión | False/ausente, código 42; ninguna garantía operativa enviada |

Se preservaron artefactos anteriores. El JAR no contiene servidor Spring Boot ejecutable, Tomcat, recursos React, migraciones, H2 ni las sondas/harness QA.

La independencia Windows exige firma TI, topología vigente, dominio de fallo y recuperación, autorización explícita coincidente y nueva comprobación por ingreso. La evidencia se vuelve a verificar antes de firmar y limita el vencimiento de garantía. C:/D: comparten disco y se rechazan incluso con bandera/evidencia sintética. No se añadió un override para declarar independiente el laboratorio.

## Pruebas nuevas y regresiones ejecutadas

| Batería | Ejecución / resultado |
|---|---|
| Backend completo H2 | **284 descubiertas, 282 correctas, dos omitidas**, cero fallos/errores; verify y JAR |
| Backend MySQL aislado Linux | **284 descubiertas, 282 correctas, dos omitidas**, cero fallos/errores; robust_public_test exclusivamente |
| Frontend | **162 correctas en 18 archivos**, lint y build correctos |
| Python existentes | **9 + 7 correctas** |
| Windows nativo 1.0 | **39 verificaciones correctas** |
| Windows nativo 1.1 | **39 verificaciones correctas** |
| Connector/J MySQL80 | **5 comprobaciones**: localhost/IP válidos, nombre/CA/credencial incorrectos rechazados |
| Parser QA TLS Windows | **21 comprobaciones**, sin secretos/SQL/sockets |
| Agente CLI real + MySQL80 | Ambas versiones, respuesta 0 sin firma; transacción, integridad, restauración, idempotencia, corrupción y reintento |
| HTTPS loopback efímero | **9 comprobaciones**; candidatos, ACK validado, garantías sintéticas, reintento503, duplicados, firma falsa,401,CA,timeout |
| Procesos/logs | Solapamiento12, kill y recuperación0; log propio vencido retirado y control preservado; XML deshabilitado |
| Documentación | Enlaces locales, anchors/índice, cuatro diagramas Mermaid parseados y diff --check |
| Revisión de publicación | Contratos/firmas/migraciones históricos idénticos, sin secretos/binarios/DPAPI/respaldos/evidencias privadas añadidos |

Las dos omisiones backend preexistentes son workers reales y recuperación de proceso opt-in; no se deshabilitaron pruebas para pasar. El antecedente multimedia con ClamAV real se conserva separado; no se volvió a hacer carga sobre Windows, pruebas máximas 150/250 MiB ni consultas gubernamentales.

Las 284 incluyen seis tests de acreditación, seis de carga de clave, tres de temporales y tres de Office; no sumarlos otra vez. Las 39 de cada harness Windows usan H2 solo como fixture de ese harness; **no sustituyen las ejecuciones MySQL80 separadas**.

La CI de [Ubuntu + Windows Server 2022](../.github/workflows/ci.yml) usa runners efímeros y no necesita la máquina institucional, DPAPI, MySQL80 privado ni medios de respaldo. Linux ejecuta frontend/backend/Python; Windows compila y ejecuta los harness de NTFS/H2 y parser TLS. Sus resultados se comprueban por SHA después del push y se informan separadamente: la CI no se atribuye las pruebas MySQL locales.

Evidencias locales privadas: logs cierre-ingenieria-*, agente-tls-final-{h2,mysql}, agente-clave-focal y auditoria-adjuntos-saneamiento-h2 bajo .local/evidencias. No se publican respuestas/cookies/dumps o datos institucionales. Comandos reproducibles y precondiciones en el [README](../README.md).

### Evidencia institucional anterior

No se reejecutó ni alteró el artefacto SIGER. El [cierre V10](CIERRE_TECNICO_V10_2026-10-25.md) registra su SHA-256 `2ed038c567b672e7c7aaab6ac04f83efd22101f0b0ea1f5d4e0a33333be4ca97` y la procedencia del JAR usado anteriormente. Las pruebas previas con sus clases/COM/ACK/restauración son evidencia histórica sintética, no un despliegue definitivo ni una auditoría nueva del otro repositorio.

## Despliegue y límites operativos

La inspección Docker de solo lectura observó los servicios existentes app/MySQL8.4/Nodo/Caddy/inspector/ClamAV, sin reiniciarlos. No se desplegó el JAR nuevo del Formulario ni se migraron sus bases de laboratorio. Los estados V7/V8 de esos esquemas son los documentados previamente; V11 se verifica en pruebas aisladas, no se aplica por el push.

Se mantuvieron configuración pública, montajes, volúmenes, sesiones e integraciones existentes; el único cambio de motor autorizado de esta unidad fue TLS MySQL80 del laboratorio Windows. Los puertos 8080/8081 permanecieron libres. Se comprobó configuración privada sin exponer secretos: mantenimiento/retirada públicos nuevos ausentes/false y emisión Windows false/ausente.

MySQL TLS está resuelto en laboratorio, no en una infraestructura productiva nueva. No se calificó protocolo X33060, reinicio de servicio, clientes antiguos, expiración/renovación bajo incidente, cuenta definitiva Scheduler o pérdida física del almacenamiento. El medio independiente sigue sin provisionar; el USB no se seleccionó como archivo institucional.

El respaldo/restore público por script es offline con lock; mantenimiento de retirada es online, por lotes y con leases/locks en BD. El diseño actual no habilita múltiples escritores del almacén por el solo hecho de que MySQL sea compartido.

## Preparación productiva: bloqueos y pendientes

1. **Guardas de código:** FormularioConfiguration bloquea prod/production y CU no-SANDBOX; PiseeConfiguration restringe DEV autorizado. Su revisión/levantamiento exige una intervención de habilitación con evidencia y autorización; no se realizó.
2. **Infraestructura:** dominio/TLS público, red/proxy, cuentas y certificados, persistencia externa, firmas antivirus actualizadas y operación/alertas.
3. **Integración efectiva:** validar versión de SIGER instalada y E2E conjunto, reservas/documentos/COMMIT/ACK, fallos y recuperación. No se cambió SIGER para adaptar el agente.
4. **Custodia/recuperación:** medio y dominio independientes, ACL/principal definitivo, firma TI, restauración física y durabilidad. En Linux FileStore diferente tampoco acredita por sí solo independencia física.
5. **Capacidad/continuidad:** medir VM25GB, carga/ráfagas y tamaños máximos en anfitrión estable, RPO/RTO y backups externos. El modelo 22GiB y retenciones técnicas no son una medición de esa VM.
6. **Privacidad/función:** validación de avisos, textos, opciones, campos y flujos por la contraparte; aceptación formal y autorización explícita de operación.

La falta de autoguardado del relato y la pérdida de sesión tras reinicio son límites funcionales conocidos y fallan cerrado; no implican un bypass ni obligan a rediseñar contratos para integrar SIGER. Si se promete continuidad de borradores, debe evaluarse antes de publicarla como capacidad. El perfil legacy no se anuncia como inspección profunda de PDF. Los formatos cerrados, conversión Office y nuevas plataformas observacionales no se convierten en nuevas unidades por este cierre.

## README maestro y continuidad

El README conserva el conocimiento válido y lo amplía con índice estable, cuatro Mermaid, responsabilidades, recorridos/módulos React, APIs reales/autorización, tablas/Flyway, identidad y confianza, PULL/ACK, minimización, agente, herramientas/Linux/Windows,25GB, diagnósticos, escenarios no aprobados y matriz no productiva. El código/contrato, prueba, antecedente histórico, configuración observada y propuesta se distinguen explícitamente.

No se congelan textos/campos/presentación. Su mantenimiento menor debe conservar IDs/semántica y datos históricos. Una modificación de custodia, identidad, autorización, formatos/almacenamiento o documento firmado exige reevaluación técnica; no es cosmética. Los informes especializados se actualizan solo donde cambió el comportamiento, conservando evidencias previas como históricas.

**Conclusión:** la ingeniería central está suficientemente estabilizada y documentada para concentrar el desarrollo en SIGER institucional. Continúa mantenimiento funcional/visual menor, y queda separada la futura habilitación productiva técnica/operativa descrita. Esta conclusión depende de las verificaciones publicadas y CI del SHA de entrega; no se basa en el porcentaje de avance ni autoriza producción.

No iniciar nuevas funcionalidades después de este cierre sin un encargo explícito.
