# SMR — requisitos de implementación Windows

04/11/2026. Destino: `chashmdai/sistema-de-requerimientos`. Este documento es antecedente técnico, no un prompt de ejecución ni autorización para alterar SIGER/QA. [Handoff](HANDOFF_CODEX_SMR_2026-11-04.md) · [contrato efectivo](SMR_CONTRATO_PRODUCTOR_FORMULARIO.md).

## Separación obligatoria del PC de desarrollo

Inspeccionar primero AGENTS, Git/worktrees, procesos Java, servicios, puertos, MySQL, almacenamiento y tareas programadas. Crear worktree/branch SMR independiente desde una base revisada, conservar historial y cambios. No sobrescribir JAR activo de `C:\req\bin`, tocar `C:\agente`, MySQL80 global, Kaspersky, GPO/firewall o procesos ajenos. No reutilizar `siger_institucional_windows_qa`, `siger_institucional_windows_lab`, `siger_u4_ddn_ubuntu` ni sus originales, claves y COM históricos. U4.4 cerrado es evidencia sintética, no autorización de nueva campaña.

Definir con el operador rutas, nombres de servicio, cuenta y puertos libres: no se inventan aquí valores definitivos. Comprobar ausencia de colisiones y capacidad antes de iniciar. No cambiar Java global ni detener SIGER para obtener RAM.

## Componentes y aceptación

| Componente | Exigencia contractual / operativa | Decisión interna Windows |
|---|---|---|
| Runtime/build | Base existente Java 25; Maven, Node 24 y PowerShell 7.4+ según AGENTS SIGER. Construcción aislada | Reutilizar build y frontend existentes; confirmar versiones exactas en pom/package, no trasladar automáticamente todo SIGER |
| Backend SMR | Documento íntegro, firma, procedencia, autenticación y autorización institucional | Paquetes/módulos, endpoints UI y esquema propio; no se prescribe desde Ubuntu un nuevo modelo SQL SIGER |
| MySQL | Instancia propia o esquema nuevo aislado, cuenta de migración separada de runtime, unicidad por origen+id público y detección de id/hash conflictivo | Nombres y migraciones nuevas compatibles; no editar V1–V13 históricos ni ejecutar V14 sobre bases preservadas por rutina |
| Originales | Directorio privado NTFS, tamaño y SHA-256 íntegros, escritura temporal segura y publicación durable antes del ACK | Cuenta de servicio, ACL mínimas, estrategia de fsync/rename y acreditación real de durabilidad; no exponer raíz estática |
| Consumidor automático | PULL saliente HTTPS/Bearer, negociación 1.0/1.1, exclusión mutua, timeout, backoff y conciliación ACK durable | Planificador/tarea/servicio independiente; iniciar desactivado, no apropiarse del consumidor QA |
| Claves | Confianza pública Formulario y privada ACK Ed25519 independiente por entorno | Custodia ACL/DPAPI o mecanismo institucional aprobado; nunca argumentos, frontend, logs ni Git |
| UI | Detalle completo y procedencias, folio y originales autorizados | Autenticación institucional y roles persistentes, sin usuarios/contraseñas predeterminados |
| Operación | Reinicio recupera registros, documentos y ACK pendientes sin duplicar folio | Métricas operativas sin PII, códigos de error, alertas/capacidad, mantenimiento y backups/restore ensayados |

No se exige al SMR consultar CU ni PISEE: recibe la evidencia de origen firmada, sin elevar un RUN declarado a login. No copiar credenciales gubernamentales de Ubuntu a Windows.

## Reutilización verificada, no reconstrucción

SIGER HEAD remoto inspeccionado: **95cf2ae8173b4c6cab3eb8a1531659b8459dd898**. Volver a leer AGENTS y estado Windows antes de implementar; no se inspeccionó la máquina Windows. Rutas relativas a ese repositorio:

- `siger-institucional/backend/src/main/java/cl/nexbyte/siger/institucional/formulario/FuenteFormulario.java`: HTTP, sonda no destructiva 401/401/404, negociación, streaming y timeouts. URI configurada es el **origen** sin path; la clase agrega la base. Rechaza redirects.
- Mismo directorio: `EntregaCompleta.java`, `RecuperarFormulario.java`, `ArchivoInstitucional.java`, `ExclusionConsumidorFormulario.java`, `FormularioInstitucionalConfiguration.java`, `EstadoConsumidorFormularioController.java`, `DocumentosIngresoController.java`.
- `.../robustez/RecepcionInstitucionalJdbc.java`, `ComprobanteInstitucional.java`: validación/registro/folio, confirmaciones pendientes. Revisar dependencias de gestión antes de extraer; no afirmar que ya es SMR autónomo.
- `siger-institucional/front/src/FormularioRecibido.tsx`, `CamposFormulario.ts`: visualización/labels existentes.
- `scripts/Windows-Preparar-Conexion-Formulario.ps1`, `Windows-Recuperar-Formulario-Una-Vez.ps1`, `Windows-Ensayo-Formulario-Ubuntu.ps1`: referencias de operación; perfiles QA/U4 no son configuración institucional SMR.
- Pruebas `FormularioCompletoInstitucionalTest`, `RecuperarFormularioHttpTest`, `PlanificadorFormularioTest` y ensayos Windows: reutilizar fixtures en base nueva.

Fuente congelada: https://github.com/chashmdai/sistema-de-requerimientos/tree/95cf2ae8173b4c6cab3eb8a1531659b8459dd898 . El ZIP incluye extractos públicos con hashes. La adaptación a SMR, la extracción de dependencias y las pruebas pertenecen al futuro encargo Windows.

Limitación conocida del cliente actual: descarga completa 200; no afirmar soporte Range reanudable ya implementado. Su sonda usa HEAD sin reclamar. Su configuración no fija User-Agent explícito: calificar la petición real de Java frente a Cloudflare antes de abrir PULL.

## Interfaz institucional mínima y datos de entrada

| Pantalla / control | Datos que aporta el Formulario | Responsabilidad SMR |
|---|---|---|
| Login y permisos | Ninguna credencial de funcionario | Autenticación segura, sesiones, CSRF, autorización por recurso, usuarios y roles persistentes; no hacer lector universal al operador TI |
| Bandeja | UUID público, tipo, fecha recepción, campos/motivo, adjuntos | Folio local, fecha registro, paginación/búsqueda autorizada y vínculo inmutable al origen |
| Detalle | datos.campos, relaciones, instituciones, personas, atributos/discrepancias | Mostrar íntegramente consulta/promoción/vulneración, contactos/preferencias, contexto y origen; no convertir automáticamente en Caso |
| Identidad | Tipo y rol, autenticada, fuentes/niveles/evidencias, estadoConsulta | Distinguir CLAVE_UNICA_USERINFO, SRCEI y DECLARACION_CIUDADANA; no inventar autenticación o semántica de códigos |
| Documentos | Descriptor id/nombre/tipo/tamaño/hash y bytes originales | Descarga/visor bajo permiso; no ejecutar contenido activo; conservar original y hash, tratar nombres como metadatos |
| Operación | Lease, intento, vencimiento y respuestas HTTP | Mostrar salud del consumidor, último ciclo, ACK pendiente/error, conciliación y acciones con permisos/auditoría sin exponer Bearer |

Aceptación local: login y rechazo sin permiso; ingreso de fixture completo; visualización de todas las secciones; integridad del original; original inaccesible a otro usuario; identidad declarada permanece no autenticada; un solo folio tras reintento; reinicio conserva detalle/documentos/ACK pendiente. Pruebas en Windows real, no declarar NTFS por correr en Linux. No basta validar consumidor sin interfaz.

## Traslado del mismo aplicativo a VM institucional

Externalizar: bind/puerto/URL del servicio, conexión MySQL y cuentas, migraciones, raíz de custodia, cuenta de ejecución, certificado HTTPS local y su clave, confianza del productor, URI pública de origen, Bearer, identidad ACK y keyId del entorno, activación/intervalo/lote del consumidor, límites/timeouts, backup y destino de restauración. Los nombres concretos de propiedades los decide Windows reutilizando las existentes; estos son requisitos, no variables inventadas del contrato.

Construir una vez el JAR/paquete aprobado, registrar SHA-256 y versión, copiar por canal autorizado y comprobar en destino con `Get-FileHash -Algorithm SHA256`. El hash debe coincidir antes de iniciar; no compilar ni corregir lógica en la VM. Dependencias frontend empaquetadas y artefactos complementarios deben viajar con manifiesto y hashes. Runtime Java compatible explícito por servicio.

La VM necesita cuenta/ACL, hostname/SAN y HTTPS, MySQL aislado, almacenamiento/capacidad, salida DNS/HTTPS al productor y respaldos acordados con TI. No inferir compatibilidad o ausencia de impacto con SIGER instalado: preflight de puertos, procesos, cuentas y permisos, ensayo y comparación antes/después obligatorios.

No trasladar automáticamente la base de desarrollo, DPAPI, privadas ACK ni Bearer. DPAPI depende de identidad/máquina. La VM genera/custodia su propia identidad o recibe una identidad institucional por procedimiento autorizado con trazabilidad. Si se aceptó excepcionalmente una Comunicación real en el PC, ese PC pasa a ser custodio: no retirarlo hasta migración autorizada de SQL, originales, folios, claves verificadoras y ACK pendientes comprobada y sin consumidores simultáneos. Preferir primer E2E real en VM; desarrollo solo sintético.

Entrega futura exigible: SMR funcional y aislado, backend/consumidor automático/UI/autenticación, persistencia y documentos, pruebas sintéticas y Windows, JAR portable, manifiesto SHA, instalación/config externa, servicio, backup/restore y rollback. No incluye gestión completa de Casos, distribución, tareas ni correo.
