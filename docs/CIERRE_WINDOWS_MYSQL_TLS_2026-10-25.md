# Cierre de integración Windows, MySQL TLS y preparación de custodia

**25/10/2026 · laboratorio DDN-TEST-SIGER · baseline 20328063aaf840ada57684167d2106932b4402ab.** Esta unidad modifica exclusivamente formulario-web y la configuración TLS autorizada de MySQL80. No modifica, compila, migra ni despliega SIGER. No activa tareas, emisión institucional ni retiradas del Formulario.

## Resultado y límites

| Capacidad | Resultado |
|---|---|
| Java 25 Windows + Connector/J + DPAPI | REAL_VALIDADO, sesión MySQL80 con VERIFY_IDENTITY |
| MySQL TLS | REAL_VALIDADO, recarga y reversión runtime sin reinicio |
| Esquema y documentos sintéticos 1.0/1.1 | REAL_VALIDADO en siger_custodia_lab |
| Agente CLI real contra MySQL80 | REAL_VALIDADO, lectura/respaldo/restauración sin firma |
| Restauración en proceso nuevo | REAL_VALIDADO, tablas temporales MySQL reconstruidas desde paquete y originales de origen desconectados temporalmente |
| HTTPS, ACK y garantías sintéticas | SIMULADO_VALIDADO, receptor efímero en loopback; nunca Formulario real |
| Acreditación Windows | Implementada, con verificaciones criptográficas y de topología; laboratorio rechazado |
| Medio independiente / desastre físico | PENDIENTE, C: y D: comparten DiskNumber 0 |
| Ejecución no interactiva | Proceso pwsh -NonInteractive probado bajo identidad DPAPI original; cuenta definitiva y logon de Scheduler pendientes |
| Task Scheduler | XML deshabilitado preparado; no registrado ni iniciado |
| CI Windows | Job añadido; ejecución y SHA se verifican durante la publicación del cierre de ingeniería |

Las restauraciones anteriores no equivalen a recuperar la infraestructura institucional ante un desastre. Tampoco se acredita resistencia del hardware frente a corte de energía. TLS del protocolo X en 33060 y los clientes antiguos no se calificaron; esta unidad verifica JDBC en 3306.

## TLS de MySQL80

Se inspeccionaron servicio, identidad, configuración, certificados, ACL y conexiones. MySQL80 8.0.46 ejecuta como NT AUTHORITY\NetworkService. Había procesos Java, pero **cero conexiones TCP establecidas y cero sesiones SQL ajenas**, excluido el daemon y la conexión administrativa, antes de las recargas. No se detuvo ningún proceso ajeno. Los puertos 8080/8081 quedaron libres.

Valores previos: ssl_ca=ca.pem, ssl_cert=server-cert.pem, ssl_key=server-key.pem; TLSv1.2/TLSv1.3, named_pipe=OFF, require_secure_transport=OFF. No se cambió esta última política global del motor compartido ni usuarios/credenciales. Se exige TLS en el cliente del agente.

El certificado inicial no tenía SAN localhost. Para la intervención administrativa se confió en la CA **original respaldada**, manteniendo VERIFY_IDENTITY y el CN original mediante un hosts file **privado del proceso Java** (-Djdk.net.hosts.file); no se alteró hosts Windows, DNS ni confianza global. No se utilizó REQUIRED, VERIFY_CA, DISABLED ni un TrustManager permisivo para autenticar.

### Respaldo y permisos

- C:\agente\config\mysql-tls-respaldo-20261025: my.ini y ocho PEM originales, incluidos los privados; nueve archivos con SHA-256 comprobado origen/copia y SDDL en manifest.json.
- Directorio protegido: usuario original, SYSTEM y Administradores. No se versiona ni distribuye.
- [Backup-MysqlTlsLab.ps1](../agente-custodia/windows/Backup-MysqlTlsLab.ps1) rechaza sobrescribir un respaldo.
- La elevación se realizó mediante RunAs/UAC normal, solo para leer/copiar los archivos protegidos y actualizar my.ini.
- No se copió ni reexportó la credencial DPAPI root. Se importó en memoria exclusivamente para inspección administrativa y recargas TLS. Fixtures y agente utilizan la credencial de aplicación.

La auditoría final añadió validación estricta compartida de URL al auxiliar administrativo (21 casos sin conexiones), y lectura de clave privada con NOFOLLOW, ACL/permisos y límite de 16 KiB (seis tests focales). El JAR final incorpora Jackson 3.1.7; no altera firmas históricas.\n\n### Identidad nueva y confianza

[New-MysqlTlsLab.ps1](../agente-custodia/windows/New-MysqlTlsLab.ps1) genera RSA 3072/SHA-256, CA de laboratorio de dos años y servidor de un año, SAN DNS:localhost e IP:127.0.0.1, EKU serverAuth, KeyUsage y BasicConstraints apropiados; comprueba la cadena con CustomRootTrust.

- C:\agente\config\mysql-ca-lab-20261025\ca-key.pem: clave privada CA, sin acceso para NetworkService.
- C:\agente\config\mysql-tls-lab-20261025: CA pública, certificado y clave servidor. NetworkService solo lectura; usuario/SYSTEM/Administradores control.
- C:\agente\config\mysql-lab-ca-20261025.jks: **solo CA pública**, confianza exclusiva del proceso cliente.
- CA DER SHA-256: 73a5bcf64081ef036b69bf66ed3570c16fba6eebfefebfec1355296b14caab4c.
- No se instaló la CA en Windows ni en cacerts/JAVA_HOME global.
- El JKS usa la etiqueta pública public-trust; protege integridad de un almacén de certificados públicos. **No es una contraseña de MySQL ni protege una clave privada.**

[MysqlTlsLab.java](../agente-custodia/qa/MysqlTlsLab.java) comprobó sesiones y ejecutó SET GLOBAL ssl_ca/ssl_cert/ssl_key y ALTER INSTANCE RELOAD TLS, con rollback por defecto. En fallo restaura las variables anteriores y recarga; nunca usa la opción que suprime rollback. Se probó una reversión runtime al material original y una nueva recarga al certificado de laboratorio. Las conexiones administrativas verificaron ambas cadenas e identidades.

[Set-MysqlTlsLabConfig.ps1](../agente-custodia/windows/Set-MysqlTlsLabConfig.ps1) conservó los bytes previos de my.ini, añadió únicamente las rutas TLS bajo mysqld y ejecutó mysqld --validate-config, **sin arrancar otro servidor**. Si falla, repone los bytes previos. Mantiene sus ACL. El manifiesto config-aplicada.json registra hash anterior/posterior. No se cambió mysqld-auto.cnf ni se usó SET PERSIST. El servicio permaneció Running; no se solicitó ni ejecutó parada/reinicio.

Referencia oficial: [recarga y variables dinámicas MySQL 8.0](https://dev.mysql.com/doc/refman/8.0/en/using-encrypted-connections.html), [ALTER INSTANCE y rollback TLS](https://dev.mysql.com/doc/refman/8.0/en/alter-instance.html), [VERIFY_IDENTITY de Connector/J](https://dev.mysql.com/doc/connector-j/en/connector-j-server-authentication.html).

### Parámetros para el futuro SIGER Windows

No se tocó C:\req ni siger_institucional_windows_lab ni siger_windows_app. TI puede proporcionar al cliente SIGER la **CA pública verificada** (jamás ca-key.pem ni server-key.pem), confirmar su huella y configurar un truststore de aplicación. El acceso al JKS debe corresponder a su identidad de servicio; no añadir permisos sobre el directorio privado del agente de forma indiscriminada.

URL orientativa, sin credenciales; no fue aplicada ni probada contra la base SIGER:

    jdbc:mysql://localhost:3306/siger_institucional_windows_lab?sslMode=VERIFY_IDENTITY&fallbackToSystemTrustStore=false&trustCertificateKeyStoreUrl=file:/C:/RUTA-PRIVADA-SIGER/mysql-lab-ca.jks&trustCertificateKeyStoreType=JKS&trustCertificateKeyStorePassword=public-trust&connectTimeout=3000&socketTimeout=5000

127.0.0.1 también está acreditado. Un futuro hostname distinto requiere certificado con SAN correcto y revisión de confianza. No reutilizar esta CA de laboratorio como CA productiva. La caducidad y renovación deben monitorizarse antes del vencimiento, con prueba de recarga y clientes.

## MySQL nativo y separación de funciones

El esquema siger_custodia_lab estaba vacío. Se aplicaron únicamente las tres tablas del [DDL QA revisado](../agente-custodia/qa/schema-mysql-lab.sql): institutional_inbox, communication_registry e institution_document. Sin migraciones SIGER, DROP ni TRUNCATE. Los fixtures tienen UUID aleatorios, texto sin personas, un archivo TXT, claves Ed25519 efímeras y COM/ACK sintéticos. Los ensayos fallidos iniciales también se conservan como material sintético; no se borraron datos reales.

[PruebaMysqlNativo](../agente-custodia/qa/PruebaMysqlNativo.java) separa explícitamente el preparador que inserta fixtures del agente que solo lee. Las inserciones se confirman transaccionalmente antes de ejecutarlo. No instala una base productiva adicional ni simula MySQL con H2.

El pool del agente utiliza BaseJdbc(..., true): Connector/J activa read-only; se comprobó @@session.transaction_read_only=1 y rechazo de INSERT/UPDATE/DELETE. Se ensayaron comandos sin filas afectables; el driver rechazó DML con S1009 y el motor rechazó una escritura en sesión SQL read-only. Esto complementa, **no reemplaza**, la cuenta SELECT de mínimo privilegio requerida en destino. La credencial app existente conserva sus permisos previos de laboratorio.

El agente:
1. Recupera documento/ACK/COM y valida firmas, hashes y referencias.
2. Copia originales por streaming a un paquete privado y publica atómicamente, con sincronización.
3. Restaura desde el paquete, verifica bytes y vuelve a contrastar el registro.
4. Repite sin sobrescribir el paquete histórico.
5. Rechaza corrupción y discrepancias; reintenta tras restaurar el fixture.
6. En el laboratorio informa ensayo restaurado SIN firma e independencia=false.

La recuperación en proceso nuevo reconstruye **tablas TEMPORARY de sesión**, no reemplaza tablas persistentes; renombra temporalmente solo objects del fixture propio para demostrar que la restauración no necesita esos originales. finally repone el nombre; se conservan todos los bytes. No es un restore de instancia MySQL ni una prueba física externa.

## Acreditación extensible y compuerta de emisión

[AcreditacionAlmacenWindows](../formulario-publico/backend/src/main/java/cl/nexbyte/siger/custodia/AcreditacionAlmacenWindows.java) introduce una evidencia de infraestructura con dominio criptográfico **distinto**: DDN-ACREDITACION-ALMACEN-WINDOWS-1. Reutiliza FirmaOperativa/Ed25519, sin cambiar documentos, ACK ni garantías existentes.

La evidencia firmada por una clave TI previamente confiada vincula:
- Equipo, raíces privadas, IDs de volumen y disco obtenidos en vivo mediante Get-Partition/Get-Disk/Get-Volume.
- Dominios de fallo distintos acreditados por TI; números de disco o letras no bastan.
- Referencia de autorización, SHA-256 de informe de recuperación y fechas.
- Vigencia máxima 90 días; prueba de restauración de infraestructura de los últimos 30 días.

Se rechazan firma incorrecta, evidencia ausente/vencida, topología cambiada, disco compartido, dominio compartido, rutas discrepantes, reparse, ACL inseguras y proveedor desconocido. La sonda se ejecuta con comando fijo, stdin estructurado, límite de salida y timeout. No altera discos. El proveedor implementado es NTFS_LOCAL_V1.

Hay **tres barreras acumulativas**, deshabilitadas por defecto:
1. Acreditación TI válida y topología actual.
2. garantia.windows.emision-habilitada=true y garantia.windows.autorizacion-referencia coincidente con la referencia firmada.
3. Comprobación de COM/ACK/originales y respaldo/restauración por ingreso, durabilidad, y revalidación de acreditación antes de firmar.

La capacidad Permiso tiene constructor privado, no se acepta una simple propiedad independiente=true. Se liga a las raíces y vuelve a comprobar firma, caducidad y topología durante la operación. La garantía nunca vence después de la acreditación ni de la vigencia de la prueba de infraestructura; mantiene el máximo histórico de 24 h. El entrypoint legado Windows y la ruta de dos BD restauradas sin capacidad permanecen cerrados. Linux conserva el flujo POSIX.

**Laboratorio:** bandera false; no referencia aprobada, clave privada operativa ni medio independiente. Enviar devuelve 42 antes de consultar candidatos/contactar el Formulario. Incluso con una evidencia sintética firmada y bandera true, el disco compartido es rechazado. No se emitió garantía operativa.

### Evidencia que debe aportar TI

Inventario del medio/topología real y sus dominios de fallo (hipervisor, discos/pool, alimentación, controlador, cuenta de administración, cifrado, disponibilidad y ransomware); aprobación del destino; ACL e identidad no interactiva; informe de restauración desde copia aislada sin origen, con hashes, prueba de interrupción/pérdida y durabilidad; acceso a las claves públicas TI y referencia firmada vigente. No firmar una acreditación basándose solo en Get-Disk: en una VM dos discos virtuales pueden compartir almacenamiento físico.

Para respaldo remoto se conserva el punto de extensión Proveedor/Topologia y evidencia firmada, pero **no se habilitan UNC ni un proveedor remoto genérico**. El adaptador del destino elegido deberá demostrar identidad remota, versionado/commit durable, ACL, no sobrescritura, recuperación aislada, dominios de fallo y revalidación durante interrupciones. Puede materializar una restauración privada local verificable sin montar todo SIGER en el Formulario. Hasta implementar/calificar ese adaptador, su tipo es rechazado. Cambiar el proveedor no cambia documentos firmados ni reglas de negocio SIGER.

## Artefacto e instalación

- Versión del agente **1.1.0-windows-lab** (diferente de la versión documental 1.1).
- JAR: C:\agente\bin\agente-custodia-1.1.0-windows-lab-476cfde631f9.jar.
- SHA-256: 476cfde631f9ab4fbf3793b6e97444f24fc81537afb57f3caaa60548c77ec790.
- Tamaño: **12 008 133 bytes**.
- Manifiesto instalado: C:\agente\config\instalacion-agente-1.1-final.json.
- Runtime: Java 25 absoluto ya instalado; Java 21/JAVA_HOME/PATH globales intactos.
- Se conservan JAR 1.0 y candidatos anteriores. Solo agente/clases QA/scripts en C:\agente; sin aplicación web completa ni migraciones.
- Construcción: bash agente-custodia/build.sh; Maven produce el clasificador compartido y el JAR separado. Los auxiliares QA, H2, servidores HTTPS de prueba y claves no forman parte del JAR operativo.

La configuración custodia-nativa-lab.properties conserva raíces iniciales e incorpora confianza TLS y bandera explícita false. Cada fixture nuevo tiene su propio agente.properties y mapas de claves públicas, sin contraseñas, sin origen público ni clave de emisor.

## Automatización, logs y operación

El [launcher](../agente-custodia/windows/Invoke-AgenteCustodia.ps1) sigue importando DPAPI bajo la identidad original y entrega el secreto por stdin anónimo. Password no aparece en argumentos, variables, logs o archivos nuevos. La invocación pwsh -NoProfile -NonInteractive se probó con el agente MySQL real.

El [preparador](../agente-custodia/windows/Preparar-TareaAgente.ps1) admite InteractiveToken o Password. Se generó tarea-custodia-1.1-476cfde631f9-disabled.xml con logon Password y **settings/trigger disabled**; no se registró ni almacenó contraseña de tarea. Falta probar un logon real del principal definitivo, carga del perfil DPAPI y permisos. S4U no se presenta como sustituto para credenciales que requieran identidad protegida.

Se mantienen IgnoreNew, FileLock entre procesos, reintentos 3×5 min, límite de tarea 10 min y launcher 540 s. Se prueban códigos 0, 12, 20, 30, 40 y 42 según modo. No hay cambios de Task Scheduler, servicios Java antiguos, firewall ni puertos institucionales.

Rotación: JSON propios con patrón exacto, máximo inicial 14 días/1000 archivos/16 MiB; solo elimina candidatos de más de 24 h y sin reparse. Parámetros LogRetentionDays, MaxLogFiles, MaxLogBytes. Los recientes se preservan aun superando una cuota: alertar sobre exceso, no borrar evidencia ajena. Los errores de rotación fallan de forma explícita. Paquetes, documentos y otros nombres quedan fuera. Se comprobó con un log sintético vencido y un archivo de control conservado.

La limpieza de staging mantiene límite de 20 temporales propios vencidos por ejecución, bajo el lock. No toca paquetes recibidos, originales o copias históricas. El timeout/kill limpia el proceso propio; el sistema libera el lock y el siguiente ensayo puede continuar. Logs genéricos JSON, sin UUID de ciudadanos, SQL de negocio, cuerpos ni contraseñas.

## Pruebas ejecutadas

| Batería | Resultado |
|---|---|
| Connector/J nativo con DPAPI | 5 comprobaciones: localhost, IP, rechazo de nombre/CA/password incorrectos |
| Windows Java 25, harness 1.0 | 39 comprobaciones correctas |
| Windows Java 25, harness 1.1 | 39 comprobaciones correctas |
| MySQL80 Windows, 1.0 y 1.1 | Fixtures transaccionales, CLI real 0, hashes/ACK/COM, repetición, corrupción/reintento, DML rechazado, restauración en otro proceso |
| HTTPS efímero Windows | 9 comprobaciones correctas: candidatos, ACK previo validado, garantía sintética, 503/reintento, duplicados, firma falsa, 401, CA no confiada, timeout |
| Linux backend H2 | 284 descubiertas; 282 correctas, dos omisiones opcionales; cero fallos/errores |
| Linux backend MySQL aislado | 284 descubiertas; 282 correctas, dos omisiones opcionales; cero fallos/errores; solo robust_public_test |
| Python existente | 9 respaldo/restauración + 7 inventario correctas |
| Logs/concurrencia | Rotación acotada; CLI devuelve 12 bajo lock ajeno; kill/reintento 0; XML disabled |
| CI Windows | Definición incluida; resultado remoto por SHA en el cierre/informe final, sin secretos ni dependencia de DDN-TEST-SIGER |

Las dos omisiones Linux existentes son opt-in de workers/proceso. No se ocultaron tests ni se hicieron cargas. La batería MySQL Linux local conserva su configuración aislada histórica; **no se confunde con MySQL80 Windows ni con su TLS**. Las 284 incluyen seis tests nuevos de acreditación (positivo de política con proveedor sintético, firma falsa, caducidad, dominios/topología, formatos remotos y bloqueo sin evidencia). Un resultado positivo sintético no acredita almacenamiento físico.

Los harness nativos incluyen permisos, enlaces/junctions/hardlinks, integridad, firmas buenas/malas, corrupción, no sobrescritura, procesos simultáneos, halt y recuperación, rechazo de ambos bloqueos sin acreditación, y restauración en proceso nuevo. El HTTPS de QA firma directamente con clave efímera y usa un receptor de prueba, **no atraviesa ni desactiva la compuerta de emisión operativa Windows**.

[Job Windows](../.github/workflows/ci.yml): Windows Server 2022 efímero, Java 25, compilación del clasificador/agente y harness NTFS/H2 1.0/1.1, sin MySQL privado ni DPAPI institucional. [Prepare-CiNative](../agente-custodia/windows/Prepare-CiNative.ps1) se niega a operar fuera de GITHUB_ACTIONS o si C:\agente ya existe. La CI Linux completa permanece.

## Comandos reproducibles

PowerShell: cada línea es independiente, sin continuaciones. No pegar contraseñas. Usar la identidad DPAPI original. Inspección y ensayo no habilitan envío:

```powershell
& 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin\java.exe' --enable-native-access=ALL-UNNAMED -jar 'C:\agente\bin\agente-custodia-1.1.0-windows-lab-476cfde631f9.jar' --version
& 'C:\Program Files\PowerShell\7\pwsh.exe' -NoProfile -NonInteractive -File 'C:\agente\scripts\Invoke-AgenteCustodia.ps1' -Jar 'C:\agente\bin\agente-custodia-1.1.0-windows-lab-476cfde631f9.jar' -Config 'C:\agente\config\custodia-nativa-lab.properties' -Modo VerificarTLS
& 'C:\Program Files\PowerShell\7\pwsh.exe' -NoProfile -NonInteractive -File 'C:\agente\scripts\Invoke-AgenteCustodia.ps1' -Jar 'C:\agente\bin\agente-custodia-1.1.0-windows-lab-476cfde631f9.jar' -Config 'C:\agente\config\custodia-nativa-lab.properties' -Modo Inspeccionar
```

Para repetir MySQL: instalar las fuentes qa compiladas con javac.exe absoluto y el JAR indicado; Invoke-MysqlQa.ps1 exige -Jar, -Url, -Class y -Arguments explícitos. Solo preparar crea fixtures, y solo en siger_custodia_lab. recuperar usa tablas temporales y repone el objeto sintético desconectado. El DDL preparado no debe aplicarse a una base institucional.

No ejecutar scripts administrativos por rutina: inspeccionar antes sesiones, hashes y autorización de cambio. No usar Administrar para el agente ni para fixtures.

## Rollback y continuidad

1. **Agente:** dejar de invocar 1.1 y seleccionar el JAR 1.0 conservado si se necesita; conservar logs y paquetes. El launcher previo está preservado en scripts como Invoke-AgenteCustodia.ps1.before-1.1; verificar su hash antes de reponerlo. La configuración puede marcarse fuera de uso mediante rename, sin borrar DPAPI ni documentos.
2. **TLS runtime:** comprobar ausencia de sesiones ajenas; conectar administrativamente con la CA/nombre que estén activos y ejecutar el modo rollback del auxiliar QA. Reestablece las tres rutas originales y recarga con rollback por defecto. Esta reversión **se ejecutó** y después se reaplicó la identidad nueva, sin reinicio. Para verificar el certificado viejo usar únicamente el truststore bootstrap/hosts del proceso.
3. **my.ini:** con elevación normal, ejecutar Set-MysqlTlsLabConfig.ps1 -Revertir. Exige que el hash actual corresponda a esta intervención y verifica los nueve archivos del respaldo antes de reponer los bytes originales. La reversión del archivo es comprobable por hash; no se hizo rollback del archivo activo como prueba ni se reinició el servicio.
4. **Material:** no borrar CA, claves o respaldo mientras algún cliente confíe en esa identidad. No retirar certificados de servicios ajenos ni reinstalar MySQL. No hacer rollback de datos ni eliminar fixtures en esta unidad.
5. **Futuro reinicio:** solo bajo autorización explícita, después de sesiones, respaldos y procedimiento de recuperación. La configuración fue validada offline, pero no se afirma haber probado un reinicio del servicio.
6. **Tarea:** no hay registro que deshacer. Si otro operador la registra en el futuro, documentar identidad, autorización, ruta exacta y rollback de esa tarea únicamente.

Pendientes para operación: medio/dominio independiente aprobado y calificado; firma TI y referencia de autorización; cuenta SELECT y ACL del productor; identidad no interactiva/DPAPI y monitor de diagnósticos; renovación TLS; pruebas con tamaños/tiempos reales y recuperación física; autorización separada para emisión y retirada. La integración de negocio y las migraciones de SIGER permanecen fuera del alcance. La observación de almacenamiento o una firma sintética nunca habilitan producción.

La configuración pública, V1–V11, documentos firmados 1.0/1.1, canonicalización, claves históricas, UUID, flujos ciudadanos, ClaveÚnica y PISEE permanecen intactos. La consolidación y publicación de código están autorizadas por el encargo de cierre de ingeniería posterior. No autorizan activar servicios, emisión o retirada. El [registro de cierre](CIERRE_INGENIERIA_FORMULARIO_PUBLICO_2026-10-25.md) y el informe final identifican el SHA y CI publicados.
