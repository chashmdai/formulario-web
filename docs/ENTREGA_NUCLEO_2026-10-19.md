> Actualización vigente 20-10: F04 ya está confirmada por Benjamín. F05–F09 habilitan evaluación y decisión de reapertura ordinaria según [V8](ANTECEDENTES_Y_REAPERTURAS_LAB.md). Las restricciones descritas abajo corresponden a la entrega inicial y se conservan como historia; rechazo/cancelación de derivaciones y reapertura de DERIVACION siguen pendientes.

# Entrega: núcleo institucional provisional · 19-10-2026

**Referencia de la entrega inicial.** El acceso propio, cierre BAJA y solicitud/aceptación se actualizan en [la siguiente iteración](COMPLEJIDAD_Y_DERIVACION_LAB.md), sin atribuir aprobación al procedimiento completo.

Implementación funcional en el repositorio vigente. Referencia: texto/diagrama aportados en el encargo, transcritos con sus restricciones en [flujo consolidado provisional](flujo.consolidado-sinformsinbit.md); no había un archivo de ese nombre disponible. Se leyeron código de ambas aplicaciones, entregas de estabilización/NNA, contratos, migraciones y pruebas. ACK diferido/auditable, validación estricta TLS, V2 410, NNA sin RUN y ausencia de restauración ya existían y se conservaron. No se reimplementaron desde versiones históricas.

## Cambios revisables

1. **Registro y gestión:** `siger-institucional/backend/.../nucleo/{ContratosNucleo,CatalogoLaboratorio,NucleoInstitucional}.java`; V5 MySQL/H2; pruebas `DatosNucleoTest` y `NucleoInstitucionalTest`. Único Requerimiento atómico por origen, varios destinos/Casos, asignaciones, actuaciones/revisión, vínculos y avisos. `RecepcionInstitucionalJdbc.entregaRegistrada` permite lectura validada del original; `ConsultaComunicaciones` admite operación nula del registro manual.
2. **Acceso e interfaz:** `SeguridadInstitucional`, `nucleo/{HabilitacionNucleo,NucleoConfiguration,SesionFuncionario,NucleoController}`, `application.yml`, filtro `robustez/LimitesCarga`, pruebas `HabilitacionNucleoTest` y `NucleoHttpTest`; nuevo `siger-institucional/front` (App/componentes, cliente API, estilos y cinco pruebas); `pom.xml` y `Build-Siger.ps1` empaquetan React propio. Configuración HTTPS funcionaria nueva y opt-in.
3. **Operación/evidencia:** `scripts/Nucleo-Local.ps1`, `Probar-Nucleo.mjs`, ampliación compatible de `Nna-Local.ps1`, guías/contrato/README/decisiones y evidencias. El público no recibe lógica de gestión ni cambios de identidad/contratos. No se hicieron commits: el repositorio tenía todo sin seguimiento y no tenía HEAD; no se incorporaron archivos ajenos ni secretos al índice.

Comparación de archivos existentes en `.local/nucleo-baseline` (copias y hashes previos); parches de revisión en `.local/nucleo-revision`. Los conjuntos son incrementales. No se modificaron V1–V4 ni algoritmos/fuentes históricas de huella y firma.

## Tramos del flujo y pruebas ejecutadas

| Criterios del encargo | Implementación y evidencia |
|---|---|
| 1. Web en buzón, identidad/firma intactas | `buzonWebConservaCuerpoFirmaEIdentidadMientrasGestionSeGuardaAparte`; demo HTTPS/MySQL y cotejo de huellas |
| 2. Atención respondida sin agrupador vacío | `manualYaRespondidaSinCasoSinRequerimientoNiCiudadanoFalso`; manual real en demo |
| 3. Destinos independientes | `dosDestinosIndependientesUnaRespuestaNoResuelveElOtro`; UPR con Casos y UPD responde sin Caso |
| 4–5. Primeras aperturas concurrentes, varios Casos en misma unidad | `dosPrimerasAperturasConcurrentesCreanUnAgrupadorYVariosCasosEnMismaUnidad`, sobre MySQL real |
| 6–8. Cierre individual, destino pendiente y último resuelto | `cierreIndependienteYUltimoDestinoSinCasoReevaluaTerminoGlobal`; demo no termina hasta última respuesta |
| 9–10. Actor/fecha, actuaciones y observaciones conservadas | `observacionesDevuelvenGestionConActuacionesAutorYFechaConservados`; dos ciclos de revisión en demo |
| 11. Hija documental sin alterar/reabrir | `vinculacionConservaOriginalesNoReabreYAvisaSoloAlResponsableSeleccionado`; aviso leído y huellas invariantes |
| 12. UUID ajeno no concede acceso | `profesionalNoTieneVisibilidadGlobalNiPuedeCambiarUUIDAjeno`, `NucleoHttpTest`; HTTP 404 de otro profesional/jefatura en demo |
| 13. Transiciones pendientes no habilitadas | Derivar/reabrir: 409 después de autorizar; UI deshabilitada; pruebas de gate productivo y gestión personal |
| 14. Regresiones conservadas | Baterías existentes: ACK >20 con 409, ACK perdido, TLS/migración, V2 410, NNA, sesión/CSRF, idempotencia y fixtures históricos |
| 15. Rollback sin cierre global falso/huérfanos | `rollbackAperturaNoDejaAgrupadorVacioYReintentoEsIdempotente`, `rollbackUltimaResolucionNoDejaRequerimientoTerminadoConDestinoAbierto`; fallas SQL sobre MySQL |

Ejecución final: `scripts/Robustez.ps1 pruebas` con MySQL 8.4.11 local, cuentas DDL/DML separadas. **Público: 70 descubiertas, 66 exitosas, 4 optativas omitidas. Institucional: 52 exitosas, 0 omitidas/fallos.** Las 18 nuevas institucionales: 14 de dominio/paginación, 2 HTTP/seguridad y 2 del gate. Además **13 pruebas React públicas y 5 institucionales**, lint y compilación TypeScript/Vite de ambos, compilación Java y JAR institucional con frontend propio. [Resumen JUnit real](evidencias/nucleo-2026-10-19/junit-resumen.json). Verificación institucional final: Maven `verify` sobre MySQL en `.local/nucleo-institucional-final.log`, con nueva prueba de lectura consistente frente a decisión concurrente. Logs públicos en `.local/nucleo-regresiones-mysql.log`, reportes Maven en cada `target/surefire-reports`.

Se verificó visualmente la pantalla de acceso HTTPS en el navegador integrado. El resto se comprobó con React Testing Library y HTTP real, no mediante inspección visual completa de todas las pantallas. No se ejecutó nueva batería de rendimiento/caída de procesos, autenticación real CU ni llamadas PISEE/SRCeI/Notificador. Las pruebas OIDC adultas usaron proveedor simulado, exclusivamente dentro de pruebas. La integración Sandbox real previamente validada se conserva; arranque normal volvió a habilitarse tras preflight **local**, sin seguir redirecciones gubernamentales.

## Demostración real ejecutada

Se inició con `iniciar -SinClaveUnica`, se demostró el flujo y se comprobaron los backends por separado. Se repitió la demo con el artefacto actualizado; el cliente solo usa NNA/sesiones sintéticas, sin contactar CU aunque su botón esté habilitado.

- NNA **COM-000015**, sin RUN: custodia pública MySQL, HTTPS iniciado desde SIGER, registro MySQL institucional, ACK confirmado y Ed25519 verificado. [Resultado público](evidencias/nucleo-2026-10-19/resultado-http.json), [firma](evidencias/nucleo-2026-10-19/verificacion-firma.json).
- Dos Casos UPR en el mismo Requerimiento; actuaciones íntegras, observaciones y cierre independiente. Ambos Casos cerrados con destinos pendientes mantienen ACTIVO. Fin explícito UPR y respuesta UPD sin Caso producen TERMINADO. Manual respondida sin Caso, descarte sin borrado, hija sin reapertura y aviso interno leído. [Resultado e historial](evidencias/nucleo-2026-10-19/resultado-nucleo.json).
- **22 registros/huellas anteriores comparados sin cambios**, cuerpos/ACK iguales entre bases, un registro por operación, sesión propietaria y ACK no pendiente. [Resumen MySQL](evidencias/nucleo-2026-10-19/verificacion-mysql-resumen.json).
- **Independencia efectiva:** SIGER responde con público detenido; público responde con SIGER detenido. [Resultado](evidencias/nucleo-2026-10-19/independencia.json).

Evidencias publicadas: identificadores técnicos y cuentas sintéticas, sin cookies, contraseñas, tokens ni respuestas completas de autenticación. Los datos nuevos permanecen en esquemas `*_local`. Esto no resuelve el despliegue productivo.

## Decisiones y pendientes

[Guía de operación y matriz](NUCLEO_INSTITUCIONAL_LAB.md), [API](../contratos/nucleo-institucional-lab.md), [decisiones técnicas](DECISIONES_TECNICAS.md).

Pendientes DDN: autenticación/matriz sensible real, sedes/competencias, formularios/catálogos/bitácoras, complejidad, fin definitivo de evaluación, redistribución, derivación/aceptación/cierre de origen, reapertura, destinatarios e incorporación de hijas, relación Caso/Requerimiento/folios, adjuntos, retención y backups. No se habilitan reglas ficticias. La visibilidad central de distribución y resúmenes globales es una concesión sintética limitada al laboratorio que requiere aprobación institucional.

Los dos backends, MySQL y proxy se dejan encendidos en loopback: interfaz funcionaria en 8443 y pública en 443. Sin cambios de DNS, certificados, QA/Producción, infraestructura institucional o SIGER legado. [Inicio y recorrido](NUCLEO_INSTITUCIONAL_LAB.md#iniciar-y-recorrer).


