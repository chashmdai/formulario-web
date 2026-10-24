# Consolidación en main y contactos · 24/10/2026

## Publicación comprobada

Repositorio: https://github.com/chashmdai/formulario-web. Se actualizó `origin/main` mediante **push normal**, desde `8a140bf` hasta **`735013717e4190d63137e5cd693744df6e8f5d4a`**, y se comprobó igualdad exacta del HEAD local y remoto con `git ls-remote`. La actualización documental que contiene este informe/checkpoint se publica después sobre el mismo código funcional; su SHA se obtiene con `git rev-parse HEAD` y se comprueba contra `origin/main`.

Se hizo fetch antes de integrar. No había divergencia ni cambios remotos nuevos; `main` avanzó directamente desde `8a140bf`, conservando sus modificaciones y los 22 commits pendientes. No se utilizó force, reset, rebase destructivo ni eliminación de ramas. Las ramas de trabajo se conservan.

## Commits y alcance

- `4ad601f`: contactos normalizados, edición móvil, casos compartidos React/Java y preservación de custodia/idempotencia histórica.
- `ad18d1a`: baseline operativo del 96 %, evolutivo, y contrato para la futura adaptación de SIGER.
- `7350137`: consolidación de guías, ayudas de contacto y evidencias en main.
- Incluidos por ascendencia: `1e6af92` identificación, `c996b97` custodia/adjuntos, `08f5031` NNA, `df3b43a` adultos, `4191804` RUN/SRCeI, `512614d` / `d4d759a` pulido visual y sus checkpoints, junto con el resto del historial.

## Contactos y garantías

Se auditaron tres celulares y tres correos en los recorridos completos. Las personas relacionadas no tienen campos de contacto propios; no se añadieron. Los teléfonos fijos institucionales siguen siendo enlaces y no se normalizan como móviles.

El prefijo `+56 9` es fijo y los ocho dígitos restantes se editan normalmente: escritura, selección, reemplazo, borrado y pegado con/sin prefijo. La captura nueva usa `+569` seguido de ocho dígitos. El teléfono adulto continúa opcional; NNA conserva sus condiciones de canal y seguridad. Los errores tienen identificadores y relación accesible con el control.

Correos: recorte de extremos, validación en ambos extremos, alias `+`, subdominios y `.algo` admitidos; parte local y mayúsculas preservadas. Un formato válido no acredita existencia ni titularidad.

El backend normaliza solo capturas nuevas antes de persistir y firmar. No se modificaron migraciones, datos históricos, firmas ni algoritmo de huellas. La huella de solicitud mantiene igualdad exacta del cuerpo original; los reintentos existentes no se normalizan ni se revalidan con reglas nuevas. Prueba histórica de documento firmado y recuperación idéntica ejecutada en ambos motores.

## Verificaciones finales

| Verificación | Resultado |
|---|---|
| Frontend | 152 correctas, 17 archivos; lint y build correctos |
| Backend H2 | 176 correctas, 1 omisión opcional, 177 descubiertas |
| Backend MySQL | 176 correctas, 1 omisión opcional, 177 descubiertas; esquema aislado `robust_public_test` |
| Navegador de contactos | 6 recorridos CU/declarado/NNA, escritorio/móvil; 8 campos y 40 pegados; edición, corrección, revisión y captura canónica |
| HTTPS integrado | Los mismos 6 recorridos controlados pasaron en el hostname local oficial con TLS; portada en dos tamaños, sin llamadas API ni errores JS |
| Regresión visual | 110 estados, escritorio/móvil, temas y escala, sin desbordes ni fallos de contraste de texto medidos |
| V3 histórico | Ambos fixtures siguen idénticos al repositorio institucional y a los recursos de tests |
| Historial/secretos | Escaneo de blobs históricos y archivos actuales sin coincidencias de valores reservados contrastados ni material PEM privado; evidencias/configuración privada excluidas de Git |

Todas las pruebas de navegador interceptan las API y usan datos sintéticos; los dobles no se incorporan al frontend productivo. No se consultaron proveedores gubernamentales ni se enviaron comunicaciones reales nuevas. No se ejecutaron clases de carga. El esquema de laboratorio `robust_public_local` conserva 7 migraciones, 13 custodias y sus adjuntos con los mismos CHECKSUM de las tablas protegidas; los borradores y cuotas no se tratan como históricos inmutables. No se alteraron bases o volúmenes ni se publicaron otros repositorios.

## Servicio local y reversión

HTTPS oficial local: https://claveunica.formularioadulto.defensorianinez.gob.cl/?vista=inicio. Caddy 443 conserva el destino 127.0.0.1:18443. JAR construido desde `7350137`, SHA-256 **`908dc216d5c0fa6e4abeb96d656e394b4180e932192f01d6504d186ef50cd722`**, PID observado **146720**. Verificar siempre el PID/configuración vigente antes de operar.

Configuración privada sin cambios, sin migraciones adicionales, sin cambio de credenciales, certificados, endpoints ni banderas. Docker/MySQL/PISEE se reutilizaron. Esto **no es un despliegue productivo** ni certificación.

Respaldo anterior: `.local/evidencias/contactos-rollback-ui.jar`, SHA-256 `baf036b32c51cf03a15e8b1d6f6c5b7254de803389a808667f6f6d335353faec`. Para revertir el servicio, comprobar el hash, detener exclusivamente el proceso validado con `scripts/linux/arrancar-nna-local.py <directorio_privado> --permitir-cu-sandbox --detener`, copiar el respaldo sobre el JAR de `target` y arrancar con el mismo helper/opt-in, sin `--migrar`. No borrar tablas, documentos, firmas, adjuntos ni volúmenes.

Evidencias privadas: `.local/evidencias/contactos-*`, `contactos-navegador/` y `pulido-ui/contactos-regresion/`. El procedimiento de pruebas MySQL reutiliza las credenciales privadas del laboratorio; nunca se pegan en comandos o documentación. Las pruebas ejecutan `-Dtest=*Test,!Carga*Test`.

## Handoff y pendientes

Leer primero [BASELINE_FORMULARIO_PUBLICO_2026-10-24.md](BASELINE_FORMULARIO_PUBLICO_2026-10-24.md) y [CUSTODIA_FORMULARIO_COMPLETO.md](CUSTODIA_FORMULARIO_COMPLETO.md). La base de integración es `/integracion/v3/formulario-completo/entregas`; el consumidor SIGER **aún debe adaptarse**, persistir/verificar el agregado y emitir ACK después de COMMIT. No confundir esa extensión con el sobre histórico.

El 96 % es una evaluación operativa aproximada, no una medición de cobertura, aceptación definitiva o habilitación productiva. El formulario continúa abierto a cambios funcionales y visuales de validación institucional. Coordinar/versionar los cambios que afecten al consumidor, conservando los originales históricos. Siguen pendientes la adaptación institucional, las pruebas cruzadas y la campaña independiente de resiliencia/hardening autorizada.
