> Actualización 20-10-2026: el nuevo encargo de Benjamín confirma F04 (origen abierto hasta aceptación, nuevo Caso bajo el mismo Requerimiento y cierre por DERIVACION). Reemplaza su tratamiento anterior como propuesta. F05–F09 habilitan evaluación de hijas y solicitud/decisión de reapertura ordinaria; no habilitan rechazo/cancelación de derivaciones ni reapertura de DERIVACION. Esta entrega se conserva como evidencia histórica; reglas vigentes en [Antecedentes y reaperturas](ANTECEDENTES_Y_REAPERTURAS_LAB.md).

# Acceso local, complejidad por Caso y derivación aceptada · 19-10-2026

Iteración acotada sobre V5. **Laboratorio sintético, sin habilitación productiva.** Las reglas confirmadas y propuestas siguientes proceden de la nota de Benjamín aportada por el usuario; no equivalen a aprobación de todo el procedimiento ni de SIGER.

| Condición | Estado de definición | Implementación |
|---|---|---|
| Aprobar derivación corresponde a jefatura destino | Confirmada según nota | Solicitud separada de aceptación, autorizada por unidad/sede en backend |
| Profesional responsable puede cerrar BAJA | Confirmada según nota | Solo EN_GESTION, clasificación vigente BAJA y fundamento; sin aprobación ni mínimo de actuaciones |
| Complejidad individual por Caso | Confirmada según nota | Código estructurado nullable y descripción separada, historial de clasificación |
| Origen abierto hasta aceptar; aceptar crea Caso y cierra origen | Propuesta para validar | Una transacción, mismo Requerimiento, relación `origin_case_id`, modalidad DERIVACION |
| Promoción por jefatura origen; una solicitud por Caso en este circuito | Provisional | Se conserva alcance existente, sin ampliación al profesional; una pendiente y ninguna bifurcación |
| Rechazo, cancelación, escalamiento y reapertura | Pendientes | UI deshabilitada; API 409 después de comprobar alcance, sin efectos |

## Acceso y comandos

[SIGER funcionario](https://localhost:8443/) tiene ahora host propio. [Formulario Público](https://claveunica.formularioadulto.defensorianinez.gob.cl/) continúa en el origen anterior, 443. `SIGER_PUBLIC_SOURCE` sigue apuntando a ese público, **no** a localhost:8443. Callback/logout y CU QA/Producción no cambian.

```powershell
# Desde C:\Users\Benja\Desktop\pisee-claveunica\SIGER
.\scripts\Nucleo-Local.ps1 estado
# Si hay instancias propias previas, detener antes de compilar/reiniciar:
.\scripts\Nucleo-Local.ps1 detener
.\scripts\Build-Todo.ps1
.\scripts\Nucleo-Local.ps1 iniciar
# Demostración aislada: no invoca CU, aunque esté habilitado el botón adulto.
.\scripts\Nucleo-Local.ps1 demostrar-derivacion
# Pruebas sin leer credenciales CU durante el arranque:
.\scripts\Nucleo-Local.ps1 detener
.\scripts\Nucleo-Local.ps1 iniciar -SinClaveUnica
.\scripts\Nucleo-Local.ps1 demostrar-derivacion
```

El script madre conserva `demostrar` para el recorrido anterior y añade `demostrar-derivacion`. Las cuentas sintéticas y su credencial DPAPI existentes se conservan; no se incluyen en documentos/evidencias. No ejecutar `acceso` dentro de una captura de logs: es una utilidad humana previa que muestra esa credencial en la consola local.

Origen configurable mediante `-OrigenFuncionario https://localhost:9443` o `NUCLEO_LOCAL_ORIGIN`. Debe ser HTTPS, host `localhost` o `*.localhost` resuelto exclusivamente a loopback, puerto propio >=1024, sin ruta, usuario, query ni fragmento. No se admiten 8080/8081/13306. Windows resolvió localhost a ::1/127.0.0.1, pero **no resolvió siger.localhost**: se eligió localhost para evitar cualquier cambio privilegiado. Si se elige siger.localhost, gestionar primero su resolución local con autorización; el script no modifica hosts ni DNS.

`Origen-Funcionario.ps1 -PrepararCertificado` emite un certificado **nuevo y separado**, SAN DNS=localhost, en `.local/nna/https/localhost-8443.pem` y su clave hermana, usando la CA de desarrollo que ya estaba confiada. No ejecuta `mkcert -install`, no reemplaza la CA ni el certificado público. Si falta confianza, se detiene y explica esa dependencia; no instala confianza unilateralmente. El directorio hereda la protección de `.local/nna`. No se abren firewall ni servicios Windows. Caddy existente sirve 443 público y 8443 funcionario con certificados distintos; todos los listeners permanecen en loopback. Cookie funcionaria host-only, Secure/HttpOnly/SameSite Strict, sin Domain compartido, CSRF y gate `HabilitacionNucleo` conservados. **Los puertos no aíslan cookies: lo hacen los hosts distintos y la ausencia de Domain.**

SIGER puede arrancar primero con `.\scripts\Nucleo-Local.ps1 siger`: prepara el proxy local si falta y no inicia el backend público. Puede añadirse después `publico`, reutilizando ese proxy. `detener-publico` o `detener-siger` detienen solo el backend indicado. `detener` conserva MySQL y datos. Estos scripts de laboratorio verifican ambos esquemas con los JAR ya construidos; eso no requiere que ambos backends estén activos.

## Recorrido en la interfaz

1. Con `demo.distribucion`, abrir una Comunicación real del buzón y distribuir a UPR/sede sintética A. Su original y comprobante permanecen de solo lectura.
2. Con `demo.jefatura.upr.a`, abrir dos Casos bajo ese destino, para `demo.profesional.upr.a` y `demo.profesional2.upr.a`. En el primero registrar clasificación **BAJA** con fundamento. El texto de complejidad es independiente y no concede permisos.
3. Con el primer profesional, escribir fundamento y elegir «Cerrar directamente Caso BAJA». No necesita una actuación anterior. Hermano y destinos permanecen abiertos.
4. Con jefatura origen, solicitar derivación del segundo Caso a UPD/sede sintética A, con motivo y antecedentes expresamente asociados a evaluación. No se copia la bitácora ni se concede acceso general al Caso. El origen permanece EN_GESTION y admite actuaciones. Durante la espera no se permite cierre ordinario ni solicitar término; esto protege la secuencia propuesta, sin pausar plazos ni congelar bitácora.
5. Con `demo.jefatura.upd.a`, revisar «Derivaciones entrantes». Elegir destino nuevo o uno compatible EVALUANDO y fundamentar la aceptación. Si el origen cambió mientras se evaluaba, refrescar: versiones obsoletas producen 409, sin mutación.
6. Aceptar crea exactamente un Caso vinculado, bajo el mismo Requerimiento, sin responsable, descripción ni clasificación heredados, y cierra origen por DERIVACION. La jefatura destino realiza después la asignación explícita desde su buzón. No se cierra automáticamente ningún destino ni el Requerimiento.

La demostración termina con **dos Casos iniciales y tres registros de Caso después de derivar**: BAJA cerrado, origen cerrado por derivación y nuevo Caso abierto/asignado explícitamente. Requerimiento ACTIVO y dos destinos pendientes: resultado esperado, no error ni cierre global ficticio. Para finalizar destinos sigue rigiendo la decisión explícita provisional previa.

Para cualquier clasificación no explícitamente BAJA, se mantiene la ruta restrictiva de solicitar/revisar por jefatura, **provisional**, no catálogo MEDIA/ALTA ni procedimiento final para otros niveles. La gestión personal de jefatura conserva las restricciones anteriores. Los cierres históricos no se reinterpretan: modalidad y clasificación nuevas quedan NULL cuando no existían.

## Persistencia y protocolo

- **V6 MySQL/H2:** agrega `complexity_code` (NULL o BAJA), `closure_mode` y `core_complexity` con clasificación, fundamento, actor y fecha. No actualiza textos/históricos ni modifica V1–V5.
- **V7 MySQL/H2:** permite responsable NULL para el Caso nuevo sin asignar; agrega `core_transfer`: origen, destino unidad/sede, solicitante/fecha, revisión del origen al solicitar, motivo, antecedentes, estado PENDIENTE/ACEPTADA, revisión, decisión/actor/fecha y Caso creado. La restricción única sobre origen expresa el límite de este circuito, que no tiene cancelación ni reapertura.
- Solicitar, aceptar y cerrar BAJA bloquean el registro de Comunicación dentro de la transacción. Actor/alcance, clasificación, responsable y revisiones se comprueban en backend; no se leen permisos desde el cuerpo HTTP. Requerimiento/Caso/eventos/comando idempotente confirman juntos.
- Aceptar exige revisión de solicitud y origen; si usa destino existente exige su revisión, mismo origen y alcance destino, y EVALUANDO. Un RESUELTO produce 409; no se reabre. Un destino nuevo es otra atención explícita, no modificación encubierta de uno resuelto.
- La aceptación registra revisión CIERRE vinculada al origen, modalidad DERIVACION, solicitud aceptada y eventos inequívocos de ambas gestiones. El nuevo Caso referencia al origen. La modalidad profesional BAJA también queda vinculada al Caso y su decisión, actor y fecha.
- Reintentos con misma clave/actor/contenido recuperan exactamente el resultado previo, incluso ante aceptación concurrente. Claves distintas concurrentes: una confirma y otra 409. Un fallo revierte solicitud, destino/Caso nuevo, cierre, auditoría y resultado, conservando origen abierto. No hay bucles ni concurrencia ilimitada.
- La consulta entrante devuelve únicamente los datos de solicitud, antecedentes explícitos y versiones/metadatos técnicos necesarios; no original ciudadano ni actuaciones ajenas. Tras aceptar se aplican los alcances ordinarios del destino nuevo; el Caso origen sigue fuera de alcance del destinatario. Listado entrante limitado a 100 y circuito de laboratorio, no consola operacional definitiva de grandes volúmenes.

[Contrato HTTP concreto](../contratos/nucleo-complejidad-derivacion-lab.md). El transporte público V3, sobres adulto/NNA, identidad, custodia, firmas y ACK no cambian. La evidencia de gestión se conserva separada del comprobante original.

## Decisiones pendientes DDN

Quién puede promover y cancelar; procedimiento ante rechazo/cancelación/escalamiento; excepciones y cambios mientras se espera; catálogo completo y criterios de complejidad; cierre de niveles no BAJA; bitácoras; matriz sensible y autenticación real; definición final de destino resuelto, reasignaciones/competencias y secuencia exacta de derivación propuesta. No se implementan plazos, notificaciones externas, reapertura, materias ciudadanas ni reglas de formularios.

Una solicitud pendiente sin aceptación no dispone aún de salida de rechazo/cancelación. Es una limitación deliberada de esta iteración; usar únicamente datos sintéticos. No se presenta todo el ciclo de derivaciones como terminado ni se habilita producción.
