# Entrega · Estabilización y NNA provisional · 19-10-2026

**Corrección posterior de arranque:** el bloqueo CU de las pruebas NNA pasa a ser explícito con `-SinClaveUnica`. El arranque normal del script madre ahora habilita conjuntamente adulto Sandbox y NNA, solo después del preflight HTTPS local. La redirección se inspecciona sin contactar al proveedor; no acredita una nueva autenticación real. [Modalidades actuales](FORMULARIO_NNA_PROVISIONAL.md).

Dos conjuntos revisables: [A · correcciones técnicas](CORRECCIONES_TECNICAS_2026-10-19.md) y [B · formulario, seguridad, arranque y migración](FORMULARIO_NNA_PROVISIONAL.md). [Contrato NNA](../contratos/comunicacion-nna-v2.0.md). Sin publicación ni cambios en infraestructura institucional. El repositorio carecía de commit base; no se hicieron commits ni se incorporó indiscriminadamente su contenido a Git.

## Resultado funcional comprobado

El público funcionó con SIGER detenido. Un cliente HTTPS con cookie de sesión y CSRF envió datos exclusivamente sintéticos **sin ClaveÚnica y sin RUN**; recibió `PENDIENTE_REGISTRO_SIGER`, repitió la misma operación sin duplicarla y comprobó el aislamiento frente a otra sesión. Al arrancar el JAR institucional, su consumidor HTTPS saliente recuperó y registró:

- Operación pública: `46d61872-0006-4280-b4d7-1205d12ce434`.
- Comunicación institucional: `3632e35c-d98b-4ec6-9e97-8fae6c4f84f2`.
- Folio: **COM-000006**.
- Fecha firmada: `2026-10-19T12:09:57.721Z` (09:09:57.721 de Santiago).
- Canal: `FORMULARIO_WEB_NNA`.

La misma sesión descargó el comprobante después del registro. La herramienta Node independiente verificó Ed25519 con la clave pública institucional confiada. La lectura de ambas bases comprobó estado público CONFIRMADA, ACK institucional cerrado, cuerpo y ACK iguales, `owner_run=NULL`, propietario de sesión presente, todos los atributos declarados y un registro por operación. Se conservaron nombres compuestos, espacios y relato con salto de línea.

Evidencias sin secretos: [recepción pendiente](evidencias/nna-2026-10-19/recepcion-tecnica.json), [resultado HTTP y registro](evidencias/nna-2026-10-19/resultado-http.json), [comprobación MySQL](evidencias/nna-2026-10-19/verificacion-mysql.json), [comprobante descargado](evidencias/nna-2026-10-19/comprobante-nna.json), [firma](evidencias/nna-2026-10-19/verificacion-firma.json). Son datos sintéticos del laboratorio, no ingresos ciudadanos reales.

Se compararon **10 cuerpos/ACK históricos** de las dos bases mediante huellas antes/después: intactos. Un comprobante anterior, **COM-000004**, se verificó nuevamente con la herramienta independiente sin regenerarlo: [resultado histórico](evidencias/nna-2026-10-19/comprobante-historico-verificado.json). Los fixtures históricos siguen siendo verificados por ambas baterías y su coherencia se controla en el build.

## Pruebas

`scripts/Build-Todo.ps1`: lint, TypeScript, build React y ambos Maven `clean verify`, JAR independientes. `scripts/Robustez.ps1 pruebas`: mismas regresiones backend sobre MySQL portable **8.4.11**, puerto loopback 13306, con cuentas de aplicación y migración separadas. Los resultados consolidados, omisiones y hashes de artefactos figuran en [pruebas-ejecutadas.json](evidencias/nna-2026-10-19/pruebas-ejecutadas.json).

Resultado final: **100 pruebas backend exitosas por batería** (66 públicas y 34 institucionales), tanto en build con H2 como en la ejecución con MySQL; cuatro pruebas opt-in omitidas por batería y ninguna contabilizada como éxito. **13 pruebas frontend exitosas**, lint/TypeScript/Vite correctos. Se ejecutaron además los dos JAR con `--migrate` y URL TLS contradictoria: ambos rechazaron antes de crear pool o invocar Flyway ([evidencia](evidencias/nna-2026-10-19/migradores-negativos.json)).

La ejecución final del script madre completo, con ambos JAR finales, repitió el recorrido y obtuvo **COM-000007**, firma válida, cookies Secure/HttpOnly/SameSite=Lax, ACK cerrado y **12 históricos intactos**: [resultado](evidencias/nna-2026-10-19/repeticion-script-madre/resultado-http.json), [MySQL](evidencias/nna-2026-10-19/repeticion-script-madre/verificacion-mysql.json), [firma](evidencias/nna-2026-10-19/repeticion-script-madre/verificacion-firma.json). No sustituye la primera prueba COM-000006 con SIGER detenido. También se arrancó SIGER solo y se comprobó su respuesta sin público/proxy ([independencia](evidencias/nna-2026-10-19/independencia.json)).

Cobertura relevante:

| Requisito | Comprobación |
|---|---|
| ACK sin inanición | 35 registros: 25 rechazos 409 y 10 válidos posteriores; avance, aplazamiento, bloqueo y rearme auditable |
| Fallos transitorios y ACK perdido | Diferimiento sin confirmar, conciliación idempotente y comprobante estable; también variante NNA |
| TLS MySQL | Matriz negativa y positiva en ambas aplicaciones y validación usada por sus migradores; excepción local estricta |
| V2 retirado | 410 por rutas MVC, estado PENDIENTE y reservas sin cambios; V3 efectivo en la demo HTTPS |
| Adulto | OIDC/state/token/userinfo con proveedor de test, identidad de sesión, readonly, CSRF, procedencia e históricos; ninguna llamada CU real |
| NNA | Sin CU/RUN, opcionales, compuestos, módulo 11 separado, Unicode, rechazo de campos desconocidos/duplicados y elevación de procedencia |
| Atomicidad e idempotencia | Fallo antes de evento provoca rollback; 36 intentos NNA concurrentes guardan una sola entrega; concurrencia institucional existente preservada |
| Autorización por recurso | Estado/comprobante 404 en otra sesión, incluso con UUID conocido; adulto nuevo también vinculado a sesión |
| Controles | Recepción operacional cerrada rechaza ingresos nuevos; bandera NNA cerrada por defecto y activación productiva rechazada |
| Abuso/tamaño | CSRF 403, tamaño 413, cuota temporal 429 aun sin persistencia, cuotas de sesión/IP/global |
| Sin restauración | Frontend sin sessionStorage; remontar componentes adulto/entrega y NNA comienza vacío y no consulta una operación anterior; reintento activo conserva clave |

No se ejecutó la batería opt-in de carga masiva ni la caída abrupta de MySQL/proceso en esta iteración. Sus pruebas permanecen disponibles, pero sus resultados anteriores no se presentan como mediciones nuevas. Sí se ejercitaron errores transaccionales, concurrencia, ACK perdido y desconexión real de SIGER. La excepción TLS MySQL local no demuestra un handshake MySQL productivo con VERIFY_IDENTITY.

**Límite de verificación visual:** la herramienta Computer Use detuvo la automatización al no poder determinar la URL actual del navegador con suficiente confianza. No se realizaron acciones posteriores de navegador ni se afirma inspección visual real o envío por Chrome. La interfaz React fue compilada y probada con Testing Library; el recorrido real HTTP/MySQL/HTTPS/firma fue ejecutado mediante el cliente de laboratorio. Falta la comprobación visual humana en la URL indicada.

## Archivos principales y motivo

| Área | Cambios |
|---|---|
| Ambas `robustez/ConfiguracionDatos`, `MigrarDatos` | Política TLS no eludible, también antes de Flyway |
| Institucional `RecepcionInstitucionalJdbc`, `RecuperarEntregasV2`, `OperacionController`, migración V4 | Conciliación justa/acotada, metadatos y revisión de ACK |
| Público `IntegracionV2Controller` | Protocolo retirado no reserva ni muta |
| Ambos `SobreComunicacion`, `EntregaVerificada`, `DeclaracionNna`, `RegistroInstitucional`, `ComprobanteInstitucional` | Variante declarada versionada y canal/registro verificables; históricos intactos |
| Público `RecepcionDurable`, `RecepcionJdbc`, migración V4 | Custodia NNA sin RUN, transacción, idempotencia y propiedad de sesión |
| Público `nna/*`, `comunicaciones/SesionIngreso`, `SecurityConfiguration`, `LimitesCarga`, `ApiErrors`, application.yml | Entrada anónima limitada, CSRF, habilitación local, cuotas y validación |
| Público `ComunicacionesPublicasController` | Nuevas operaciones adultas vinculadas a la sesión originadora |
| Front `App`, `DeliveryPanel`, `api`, `nna/*` | Entrada separada, adaptadores pequeños, campos provisionales y cero restauración automática |
| Pruebas/fixtures en ambas aplicaciones | Contratos históricos y NNA, fallos, permisos, concurrencia, TLS, ACK y recarga |
| Scripts Nna-Local, Probar-Nna, Verificar-Nna-MySQL, verificador Ed25519 y Build-Todo | Script madre, demostración y evidencias reproducibles; compatibilidad de comprobantes 1.0/1.1 |

## Uso y límites pendientes

```powershell
.\scripts\Nna-Local.ps1 detener
.\scripts\Build-Todo.ps1
.\scripts\Robustez.ps1 pruebas
.\scripts\Nna-Local.ps1 iniciar
# Abrir https://claveunica.formularioadulto.defensorianinez.gob.cl/?formulario=nna
.\scripts\Nna-Local.ps1 demostrar
.\scripts\Nna-Local.ps1 detener
```

Para observar SIGER desconectado, iniciar solo `publico` y, después del envío pendiente, ejecutar `siger`. No se necesita Vite. El asistente rechaza puertos ocupados; ver la [guía completa](FORMULARIO_NNA_PROVISIONAL.md).

Al cerrar la entrega se detuvieron los procesos NNA y MySQL portable creados/reutilizados por el laboratorio, conservando datos, claves y certificados. No queda una recepción NNA activa. El arranque normal adulto conserva su configuración anterior.

No hay categorías, subcategorías ni reglas condicionales inventadas. Faltan definición del formulario definitivo, aviso aprobado, privacidad/retención, accesibilidad apropiada para NNA, riesgos de abuso de una publicación pública, infraestructura autorizada y operación institucional. No se implementaron clasificación, distribución, Caso, Requerimiento o integraciones gubernamentales. Las cuotas actuales son básicas y por proceso; no constituyen protección distribuida ni habilitación productiva. El flujo NNA está limitado al laboratorio, cerrado por defecto y rechazado en configuración productiva.
