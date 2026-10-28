# ClaveÚnica productiva y PISEE configurable · 28/10/2026

## Decisión y alcance

El responsable solicitó instalar el par productivo de CU para revisión y eventual certificación en el WSL actual, con inicio local habilitado. SGD proporcionará habilitación temporal; no se acredita todavía certificación definitiva. PISEE debe continuar en DEV hasta la entrega del Nodo productivo.

Se retiraron las restricciones artificiales que impedían combinar el ambiente de credenciales CU con otro ambiente del Nodo, o iniciar recorridos bajo perfiles production/prod. Se conservaron HTTPS, validación OIDC, CSRF, sesión, claves, cuotas, autorización por recurso y reglas documentales.

No hay una excepción temporal que obligue a otra modificación de código al migrar. El perfil de despliegue, las credenciales CU y el Nodo PISEE son configuraciones independientes.

## Configuración efectiva del integrado

| Elemento | Estado |
|---|---|
| URL | https://claveunica.formularioadulto.defensorianinez.gob.cl/?vista=inicio |
| CU | PRODUCTION; par extraído de las etiquetas Client ID/Secret Producción del paquete institucional |
| Callback local | Habilitado; mismo /redirect, /logout y origen registrados |
| PISEE | DEV, idéntica URL, autorización, allowlist, timeout y concurrencia anteriores |
| Formularios | Adultos y NNA habilitados |
| Adjuntos | Multimedia habilitada en el integrado, almacén privado propio fuera de Git |
| Workers | Antivirus e inspector reales existentes, reutilizados |
| Infraestructura | WSL, loopback y TLS de laboratorio; no publicación productiva en Internet |
| Mantenimiento y eliminación | Deshabilitados; no son requisitos de habilitación CU |
| Modalidad ACK 72 horas | No activada; aceptación de riesgo permanece false |

No se incluye Client ID ni Client Secret, RUN, tokens, cookies o rutas de claves en este documento. El Client ID productivo se informa al canal oficial por la institución. Las credenciales están fuera de Git, en archivos privados 0600 bajo directorios 0700. No se copiaron a argumentos ni al JAR.

La aplicación usa las credenciales productivas; que el botón responda localmente no significa que SGD las haya habilitado en sus servidores. No se fuerza esa habilitación ni se ejecutó una autenticación gubernamental para comprobarla.

## Código

- FormularioConfiguration ya no impone SANDBOX o rechaza production/prod.
- PiseeConfiguration admite DEV y PRODUCTION sin depender del ambiente CU.
- DEV conserva su autorización existente y la lista privada acotada.
- PRODUCTION consulta con la autorización del Nodo institucional y no reutiliza la allowlist DEV. Se mantienen finalidad PRESENTADOR, consentimiento/canal cuando corresponda, RUN/DV módulo 11, cuotas y prohibición de búsquedas de terceros.
- El lanzador integrado admite SANDBOX/QA/PRODUCTION; la habilitación se lee de la configuración privada callback-approved. La opción antigua --permitir-cu-sandbox se mantiene por compatibilidad, pero no es un segundo bloqueo.
- ClaveUnicaProperties sigue exigiendo HTTPS, .gob.cl, cookies Secure y callback aprobado para PRODUCTION.
- Endpoints, state, nonce, firma de ID token, UserInfo, logout y formatos documentales 1.0/1.1 no se alteran.
- No hay migraciones nuevas ni cambios a SIGER, al agente o al Nodo DEV.

Plantilla sin secretos: [claveunica-produccion.properties.example](../infra/claveunica-produccion.properties.example). Es un overlay, no una configuración autónoma con base/almacén/TLS.

## Cambiar al Nodo productivo cuando TI lo entregue

Manteniendo el contrato /srcei/informacionPersonalBasico y su XML, no hace falta recompilar:

1. Instalar o actualizar el Nodo con su configuración/certificados institucionales y almacenamiento persistente.
2. Cambiar PISEE_ENVIRONMENT de DEV a PRODUCTION.
3. Actualizar PISEE_BASE_URL y PISEE_LOCAL_AUTHORIZATION_PATH en la configuración privada, usando la autorización local del Nodo nuevo. Si se conserva dirección/archivo, actualizar allí su contenido.
4. Mantener límites operativos, TLS verificado en cualquier tramo HTTPS, roles, cuotas, privacidad y procedencia.
5. Reiniciar de forma controlada solo el proceso que necesita recargar la configuración y verificar una consulta autorizada del nuevo servicio.

PISEE_ALLOWED_RUNS_PATH y PISEE_DEV_AUTHORIZED pertenecen a DEV; no limitan la autorización del Nodo en PRODUCTION. La selección de ambiente es información de configuración, no una bandera de certificación adicional.

La conexión productiva externa queda pendiente de la entrega de ese Nodo. La selección y el cliente se probaron con dobles; no se declara verificado un servicio externo todavía no disponible. Si el proveedor cambia el contrato o sus requisitos de transporte, debe evaluarse ese cambio concreto.

## Evidencia ejecutada

| Verificación | Resultado |
|---|---|
| Nuevos casos de ambientes | 14 correctos, incluidos en H2 y MySQL |
| Backend H2 completo sin Carga*Test | 338 seleccionadas, 336 correctas, dos omisiones, cero fallos/errores |
| Backend MySQL QA, misma selección | 338 seleccionadas, 336 correctas, dos omisiones, cero fallos/errores |
| Python de lanzadores | 18 correctas |
| Frontend | Sin cambios de código; se reutiliza el dist con las 176 pruebas/lint/build correctos de la unidad anterior del mismo día |
| Registro CU real configurado | Inicio local 302 con client_id productivo y callback exacto, valores no publicados |
| Sesión anónima | environment=PRODUCTION, loginAvailable=true, authenticated=false |
| Capacidades | Adultos, NNA y multimedia true |
| Multimedia integrada | TXT sintético, bloque y SHA-256, antivirus/inspector reales, manifiesto, descarga 200 y Range 206 correctos |
| Limpieza de fixture | Lote retirado del borrador y carga sintética no recibida cancelada; cero Comunicaciones creadas y ningún original ciudadano eliminado |
| PISEE DEV | Configuración idéntica antes/después; Nodo conservado, sin consultas gubernamentales nuevas |
| TLS Windows | HTTP 200 y ssl_verify_result=0, sin omitir validación |

Se actualizó un test que antes esperaba prohibir producción: ahora verifica ambientes desconocidos, orígenes inseguros y credenciales ausentes. No se deshabilitaron pruebas.

Omisiones existentes: workers reales opt-in y reinicio destructivo MySQL. El smoke de multimedia sí utilizó los workers reales para un archivo pequeño; no equivale a calificación de tamaños máximos o carga.

## Instalación, preservación y reversión

JAR instalado en el integrado: d4395beefb374732e1200a6b504280cfcfe1319a71668e8901e56b867400a91d. Se conserva una copia inmutable privada; nunca se ejecuta desde target. La base permanece en V12.

Se respaldaron configuración, selección de JAR y registro de proceso antes del cambio. No se modificaron registros ciudadanos, comprobantes, claves, certificados o datos existentes. El almacén integrado es distinto del multimedia aislado de 19443; no se duplican originales ni se comparten escritores.

Para consultar o recuperar el integrado con su configuración actual:

```bash
python3 scripts/linux/arrancar-nna-local.py /home/nexbyteproveedor/.local/share/siger/nna-local-20261023 --estado
python3 scripts/linux/arrancar-nna-local.py /home/nexbyteproveedor/.local/share/siger/nna-local-20261023
```

El segundo comando solo debe usarse si está detenido; el lanzador rechaza duplicar procesos. No requiere una opción especial por usar el par productivo.

Reversión: detener únicamente el PID identificado, preservar cualquier nueva carga, restaurar la configuración y selección privadas anteriores, y arrancar el JAR V12 anterior compatible. Esta unidad no hizo migraciones y no requiere downgrade. No borrar almacenes ni restaurar bases a ciegas.

Evidencia completa privada: .local/cu-produccion-20261028/. Backups y credenciales permanecen fuera de Git. No se ejecutaron push, exposición pública, reinicios MySQL/PISEE o activación de retiradas.

## Pendientes operativos

SGD debe entregar el acceso temporal y revisar login/callback/Token/UserInfo/logout sobre este candidato. La institución tramita el Nodo productivo. La futura VM requiere sus DNS, certificado, base, almacenamiento, operación y pruebas de conectividad reales.

No quedan restricciones de código por SANDBOX/production/prod para esas integraciones. Las validaciones de seguridad y los requisitos externos siguen vigentes; no se presenta WSL como producción pública o sistema certificado.
