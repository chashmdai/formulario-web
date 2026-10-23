# Conjunto B · Formulario NNA provisional

Implementación del 19-10-2026 sobre las dos aplicaciones existentes. No es el formulario definitivo ni un canal habilitado para ciudadanos reales. [Contrato y representación binaria](../contratos/comunicacion-nna-v2.0.md), [correcciones técnicas](CORRECCIONES_TECNICAS_2026-10-19.md), [resultados](ENTREGA_NNA_2026-10-19.md).

## Recorrido

React NNA → POST con CSRF y clave idempotente → validación server-side → transacción pública MySQL (declaración, procedencia, sello de custodia, evento y propietario de sesión) → respuesta técnica pendiente → SIGER reclama por HTTPS saliente → valida firma, huellas y variante → COMMIT institucional con Comunicación, UUID propio, folio y comprobante firmado → ACK → público verifica el firmante institucional y conserva el ACK → misma sesión consulta o descarga el comprobante definitivo.

SIGER puede estar detenido durante el ingreso. En ese caso no se anuncia folio ni registro institucional. La pérdida de una respuesta no prueba que el servidor no recibió el ingreso. Un reintento con la misma clave recupera la misma custodia o registro; un ACK perdido conserva la recuperación anterior. Ninguna firma ni folio se fabrica en React.

## Arranque Windows

Requisitos existentes: Java 25, Maven, Node/npm, PowerShell 7.4+, MySQL portable local, Caddy y certificado/CA locales confiables preparados anteriormente. No requiere Docker/WSL, DNS público ni servicios gubernamentales. El archivo hosts debe seguir resolviendo el nombre exacto a loopback. Este asistente **no cambia hosts, certificados ni URIs** y no salta verificaciones TLS.

Desde `C:\Users\Benja\Desktop\pisee-claveunica\SIGER`:

```powershell
# Detener primero el laboratorio anterior si sigue activo; cada script verifica sus propios PID.
.\scripts\Recepcion-Local.ps1 detener
.\scripts\Nna-Local.ps1 detener
.\scripts\Build-Todo.ps1

# Los dos procesos independientes y el proxy existente:
.\scripts\Nna-Local.ps1 iniciar
.\scripts\Nna-Local.ps1 estado
```

Abrir **https://claveunica.formularioadulto.defensorianinez.gob.cl/?formulario=nna**. No iniciar Vite ni abrir localhost como origen ciudadano. `Nna-Local.ps1` es el script madre; `Probar-Nna.mjs` y `Verificar-Nna-MySQL.ps1` son sus verificadores auxiliares.

**Arranque combinado vigente:** `Nna-Local.ps1 iniciar` deja disponibles el adulto con ClaveÚnica Sandbox, NNA y SIGER. Lee solamente el par Sandbox del TXT externo, arranca con CU bloqueada, comprueba resolución local, TLS, cookies y callback y después reinicia el público con CU habilitada. Verifica el redirect oficial sin seguirlo ni autenticar automáticamente. El origen y las URIs registradas no cambian. Adulto: `/`; NNA: `/?formulario=nna`.

Para pruebas que exigen mantener CU completamente bloqueada, usar `Nna-Local.ps1 iniciar -SinClaveUnica` (o `publico -SinClaveUnica`): no lee credenciales institucionales y utiliza valores inertes de configuración, sin autenticación ficticia. Esta fue la modalidad usada para las pruebas NNA iniciales del 19-10-2026. Las pruebas automatizadas adultas siguen usando proveedores simulados dentro de test. Para alternar modos, ejecutar primero `Nna-Local.ps1 detener` y luego el arranque elegido. Recargar la página después del reinicio. El asistente adulto habitual `FORMULARIO.cmd iniciar` se conserva.

El asistente reutiliza claves Ed25519 y credenciales locales DPAPI, prepara solamente `robust_public_local` y `robust_inst_local` mediante cuentas de migración distintas, aplica Flyway y arranca cada servicio con cuenta DML. No limpia esos esquemas. Rechaza puertos ocupados y no detiene servicios ajenos. Los logs quedan privados bajo `.local/nna`; no se registran relatos, cuerpos, cookies ni secretos. La base de pruebas de las baterías es distinta (`*_test`).

## Demostración con desconexión

```powershell
.\scripts\Nna-Local.ps1 publico  # SIGER no se inicia
```

En el navegador completar solo datos sintéticos, dejar número de RUN y DV vacíos, revisar, leer el aviso y enviar. Debe verse recepción técnica pendiente, sin comprobante definitivo. En otra consola:

```powershell
.\scripts\Nna-Local.ps1 siger
```

Consultar en la misma pestaña: aparecerán folio, UUID, fecha y descarga cuando SIGER confirme. Recargar deja el formulario vacío. No se debe usar una nueva sesión para recuperar la entrega anterior: esta iteración deliberadamente no ofrece esa capacidad.

Alternativa automática, sin navegador: con el público levantado, ejecutar `Nna-Local.ps1 demostrar`; mientras espera (máximo 90 segundos), iniciar `Nna-Local.ps1 siger` en otra consola. Si ambos ya estaban iniciados, la misma prueba valida el recorrido inmediato. El cliente comprueba HTTPS con la CA existente, cookies Secure/HttpOnly/SameSite, CSRF, reintento, aislamiento de sesiones, registro y descarga; verifica Ed25519 mediante la herramienta independiente. El auxiliar compara cuerpo/ACK entre ambas bases, propiedad y huellas históricas. Los resultados nuevos se guardan en `.local/nna/evidencias/<fecha-hora>` y la ruta en `ultima-prueba.txt`; se conservan las evidencias entregadas en `docs/evidencias/nna-2026-10-19`.

```powershell
.\scripts\Nna-Local.ps1 detener
.\scripts\Robustez.ps1 pruebas
# Opcional al terminar: detiene solo el MySQL portable registrado, sin borrar datos.
.\scripts\Local-MySQL.ps1 detener
```

## Habilitación, seguridad y límites

- Por defecto `PUBLIC_NNA_ENABLED=false`. Para admitir el prototipo se requieren además perfiles `nna-lab` y `jdbc-reception`, ambiente CU Sandbox, bind literal loopback, origen HTTPS y cookies Secure. Con ambiente PRODUCTION o perfiles `prod`/`production`, activarlo aborta el arranque. No hay un interruptor que autorice recepción NNA productiva. Su eventual habilitación exige una nueva revisión y aprobación DDN.
- La bandera NNA no abre los controles operacionales: siguen requiriéndose recepción/aviso de laboratorio habilitados, `aceptarNuevos` y contingencia NORMAL. El script los activa exclusivamente en el laboratorio. El aviso definitivo sigue sin aprobación.
- CSRF permanece activo. NNA usa sesión anónima y secreto interno de propiedad de 256 bits; no un `IdentidadVerificada` falso. El flujo adulto mantiene rol ciudadano, principal CU y evidencia de autenticación. Nuevos ingresos adultos también quedan vinculados a la sesión originadora. No se habilita búsqueda de registros históricos después de otra autenticación.
- Admisión por tamaño (8192 bytes), trabajo simultáneo existente y **cuotas temporales**: 6 POST/minuto por sesión, 30 por IP y 120 globales. Los intentos rechazados por CSRF también consumen cuota; rotar sesión no elude IP/global. Ventanas y mapa acotados, sin persistir IP en base o logs. No se confía directamente en un encabezado de IP del cliente. En el proxy local se conserva la configuración confiable ya existente.
- Las cuotas son protección básica para laboratorio, por proceso, con ventana fija; se reinician al arrancar y no sustituyen una evaluación de abuso pública. No se implementó CAPTCHA ni infraestructura adicional. El prototipo no debe publicarse con solo estas cuotas como garantía frente a ataques distribuidos.
- Campos validados de nuevo en backend y en receptor; los datos declarados se conservan sin convertirlos en identidad verificada. Región es texto obligatorio provisional; comuna opcional por la mención explícita del levantamiento. No se inventaron catálogos, dependencias territoriales ni reglas de materia.

## Estructura y migración

Frontend: `nna/fields.ts` (campos/tipos y módulo 11), `nna/api.ts` (adaptador), `nna/NnaForm.tsx` (interfaz). `DeliveryPanel` conserva un mecanismo pequeño común de envío/consulta/comprobante con adaptadores adulto/NNA. No hay motor genérico ni catálogo institucional. Cambiar campos definitivos requiere versionar su contrato, pero no rehacer OIDC, reservas, registro o firmas.

Backend público: `nna/HabilitacionNna`, `NnaController`, `LimiteAbusoNna`; `comunicaciones/SesionIngreso`; extensión de `RecepcionDurable` y `RecepcionJdbc`. Contratos declarados y validadores se compilan independientemente en ambas aplicaciones. El institucional distingue el canal NNA, conservando el mismo registro mínimo de Comunicación.

Flyway público **V4__sesion_ingreso_nna.sql**, MySQL y H2: permite null en `owner_run`, añade `owner_session` e índice. Históricos conservan su RUN, cuerpos, huellas y comprobantes; no se crean propietarios de sesión artificiales para ellos. Las rutas ciudadanas nuevas no permiten apropiarse de un histórico solo por conocer su UUID. Las lecturas técnicas históricas y verificaciones siguen funcionando.

Actualizar primero SIGER para admitir la nueva variante, después el público con NNA cerrado; aplicar cada migración mediante su cuenta separada. En laboratorio el script prepara ambos esquemas antes de arrancar. Para revertir el prototipo, detener sus procesos y desactivar su bandera. **No deshacer Flyway ni degradar un receptor después de haber aceptado contratos 2.1**: conservar un receptor compatible hasta vaciar la custodia y respaldar los registros. No modificar ni regenerar firmas para adaptar un binario antiguo.

## Pendientes DDN

Campos, catálogos, materias, subcategorías, condicionales, destinatarios/personas involucradas, privacidad, finalidad, retención, accesibilidad infantil, aviso aprobado y tratamiento de riesgos. Publicación y transporte institucional reales siguen pendientes. No se implementaron distribución, clasificación, permisos de funcionarios, Caso, Requerimiento ni PISEE/SRCeI/Notificador.
