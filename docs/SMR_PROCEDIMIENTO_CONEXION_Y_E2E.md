# Conexión SMR Windows ↔ Formulario y primer E2E

04/11/2026. **Procedimiento futuro, no ejecutado en este encargo.** [Contrato](SMR_CONTRATO_PRODUCTOR_FORMULARIO.md) · [Windows](SMR_REQUISITOS_IMPLEMENTACION_WINDOWS.md). Cada mutación requiere ventana coordinada y autorización del operador. No habilitar consumidor por instalar un JAR.

## A. Preparación Windows

1. Inventariar y preservar SIGER/QA/U4. Iniciar SMR aislado con consumidor desactivado, MySQL/esquema nuevos y raíz NTFS propia.
2. Ejecutar fixtures con proveedor local aislado, claves de prueba y sin URI/Bearer productivos. Verificar firma, hash, persistencia, folio, originales y reinicio.
3. Comprobar login/roles, bandeja, detalle íntegro, fuentes y discrepancias, descarga autorizada, estado del consumidor/ACK pendiente. No convertir Comunicación en Caso.
4. Probar SQL/NTFS/backup/restore y la exclusión de dos consumidores. Aprovisionar identidad ACK independiente solo para el entorno autorizado; guardar privada fuera de Git y perfiles históricos.

## B. Conectividad pública sin reclamos

Resolver `formulario.defensorianinez.gob.cl`; validar cadena y hostname con Java real del SMR y reloj correcto. Probar salida TCP443/HTTPS y cabeceras técnicas desde la cuenta de servicio. No usar `-k`, trust-all, cookies de ciudadano ni contraseña temporal. Rechazar redirects, HTML/challenge y respuestas de otro host; no registrar Authorization.

Usar la sonda existente `FuenteFormulario.comprobarConexion()`, que hace únicamente **HEAD** sobre:
`/integracion/v3/formulario-completo/entregas/00000000-0000-0000-0000-000000000000/adjuntos/00000000-0000-0000-0000-000000000000`
con `X-Reserva: 00000000-0000-0000-0000-000000000000`.

Resultados exigidos: sin Bearer 401, inválido 401, válido 404. No usar POST reclamar como sonda. Codex Windows debe confirmar códigos desde Windows, sin mostrar credencial ni cuerpos personales. Las pruebas públicas previas 401/401/404 no acreditan automáticamente transporte desde la futura VM.

**Observación actual:** GET público con Python-urllib por defecto devolvió 403; con `User-Agent: DDN-SIGER-Readiness/1.0` devolvió 200, TLS validado y borde Cloudflare observado. No se alteró WAF. No asumir que cualquier cliente funciona: comprobar Java/SIGER real. Si hay challenge/403, coordinar con TI una política técnica compatible sin desactivar TLS, Bearer o firmas; no automatizar la pantalla de contraseña como solución.

## C. Intercambio criptográfico y configuración

Paquete público existente: `/home/admin-ti/operacion-formulario/intercambio-siger-publico-20261104.zip`.
SHA-256: **1456fdf2c1c6be2e74cb71733c92ad33bf9376b4aabd7c962a100c604d0977ab**.

| Archivo | Uso |
|---|---|
| formulario-signing-public.pem | Clave pública Ed25519 del productor, SPKI PEM; hash 712fd54ba0676c6457901a9cc6f8eea7b30c4ff1eea1bba63ee055ba64948f92 |
| formulario-signing-trust.json | Mapa keyId→PEM. Reconocer `ddn-formulario-prod-20261103-v1` |
| public-edge-root-ca.crt | Confianza preparada para el dominio público, GTS Root R4; PEM hash 7e8b80d078d3dd77d3ed2108dd2b33412c12d7d72cb0965741c70708691776a2 |
| origin-ca.crt | Solo conexión privada directa/SSH al origen; **no** CA del borde público |
| consumer-profile.json | Perfil orientativo. Campos de túnel son alternativa histórica; no abren un túnel ni sustituyen hostname público |
| SHA256SUMS.json | Comprobar cada archivo después de comprobar hash del ZIP por canal independiente |

Validar caducidad, basicConstraints de CA, cadena y SAN/hostname; una raíz pública incluida no garantiza que el borde siga siempre la misma cadena. Revalidar al instalar. El estado de aprobación formal del firmante anterior se conserva; su uso para recepción protegida fue autorizado, no afirmar una firma cualificada.

Windows entrega únicamente JSON `{ "ID_ACK_INSTITUCIONAL": "-----BEGIN PUBLIC KEY-----\\n...\\n-----END PUBLIC KEY-----\\n" }`, con PEM Ed25519 SPKI real, ID `[A-Za-z0-9_-]{1,64}` y SHA-256 del archivo acordado por canal independiente. La privada nunca sale de su custodia Windows. No usar IDs/claves de `smr-ejemplos` ni reutilizar la identidad Formulario. El JSON anterior es una plantilla descriptiva, no archivo instalable.

Ubuntu conserva el Bearer existente; se entrega al responsable autorizado por un canal seguro separado, sin copiarlo al paquete. No regenerarlo por rutina. La contraseña temporal ciudadana no autentica al consumidor.

**Acciones futuras del Codex Ubuntu**, no ejecutadas ahora:

```sh
sudo python3 /srv/ddn-formulario-produccion/release/scripts/ubuntu/productor.py --root /srv/ddn-formulario-produccion close
sudo python3 /srv/ddn-formulario-produccion/release/scripts/ubuntu/productor.py --root /srv/ddn-formulario-produccion install-ack --trust-file /custodia/ack-publico-verificado.json --sha256 HUELLA_VERIFICADA
sudo python3 /srv/ddn-formulario-produccion/release/scripts/ubuntu/productor.py --root /srv/ddn-formulario-produccion open --accept-new
```

Importación exige recepción/PULL cerrados, valida huella/tipo Ed25519/separación de identidades, instala confianza externa y recrea app para releerla. Sin recompilar. Conservar copia del mapa actual: la herramienta reemplaza el mapa, no agrega silenciosamente; en rotación conservar claves necesarias para ACK históricos. Esta ventana invalida sesiones HTTP; no borra SQL. PULL sigue cerrado hasta coordinación final. Para abrirlo posteriormente: `productor.py --root /srv/ddn-formulario-produccion open --accept-new --pull`, comando privilegiado con autorización y receptor listo. Cierre de emergencia mediante `close`; no matar procesos ni modificar SQL para revertir una confirmación.

## D. Primer E2E autorizado

**No existe filtro de campaña/UUID en reclamar.** `limite=1` no aísla una prueba: el productor ordena por fecha/id y puede entregar la Comunicación pendiente más antigua. No conectar PC de desarrollo a la cola productiva para ensayar. Primero desarrollar con fixtures/proveedor de prueba aislados, sin credenciales productivas.

Preferencia: primer E2E real cuando la VM institucional y su custodia estén listas. Si se requiere excepcionalmente PC antes de la VM, autorización específica de tratamiento/destino y plan de continuidad/migración son obligatorios. Si la cola contiene Comunicaciones ajenas a la campaña, no se puede seleccionar solo la sintética con la API actual: dejar PULL cerrado hasta que el destino esté autorizado para toda la cola elegible, o usar un productor de pruebas independiente ya autorizado. No borrar/alterar pendientes, no falsear ACK ni inventar filtro servidor.

Ventana coordinada:

1. Inventario operacional de cola sin cuerpos/PII, autorización de destino y alcance, respaldo verificado y receptor único. Ubuntu verifica controles/confianza; Windows verifica consumidor aún desactivado.
2. Persona autorizada envía desde el Formulario una Comunicación de finalidad e identidad autorizadas; preferir enteramente sintética en campaña aprobada. Confirmar UUID/comprobante y custodia durable Ubuntu. No usar una consulta SRCeI real para generar fixtures.
3. Habilitar recuperación y un ciclo consumidor según autorización de toda la cola; Windows persiste reserva, vencimiento e intento. Validar documento, procedencias y firma antes de procesar.
4. Descargar originales, comprobar bytes/SHA-256, custodiar NTFS con ACL y persistencia; registrar en MySQL documento íntegro, original, vínculo origen/id/hash, folio estable y comprobante institucional. **COMMIT antes del ACK**.
5. Persistir ACK pendiente y reserva; transmitir registro firmado. Ubuntu confirma en transacción y devuelve 204. Si se pierde respuesta, conciliar, no registrar otra Comunicación.
6. Verificar CONFIRMADA/REGISTRADA_SIGER en Ubuntu, ACK pendiente resuelto en Windows y mismo UUID/huellas/folio. UI SMR muestra contenido completo, fuentes/discrepancias, originales y folio bajo permisos.
7. Cerrar ventana o pasar a servicio continuo solo por acuerdo. Documentar receptor custodio, evidencia sin PII y respaldo/restore. No declarar E2E exitoso si faltó cualquiera de original, COMMIT, ACK, persistencia o visualización.

ACK confirmado retira el elemento de pendientes recuperables, aunque no elimina inmediatamente el original. Trasladar SMR sin sus registros/originales/folios/trazabilidad puede perder acceso institucional a algo ya confirmado: antes de retirar PC migrar custodia autorizadamente, verificar hashes y registros, conciliar ACK pendientes, detener/excluir consumidor anterior y verificar persistencia en VM. No esperar que Ubuntu reenvíe automáticamente lo ya confirmado.

## E. Resiliencia

Ejecutar primero en entorno sintético aislado; no inducir corrupción, caídas de MySQL ni vencimientos repetidos sobre datos institucionales para demostrar una prueba.

| Ensayo | Resultado exigido |
|---|---|
| ACK idéntico repetido | 204, mismo registro/folio; distinto ACK rechazado |
| Respuesta perdida tras ACK | Conciliar ACK persistido; no nuevo folio |
| Caída después de COMMIT y antes de ACK | Registro/originales conservados, ACK pendiente durable tras reinicio |
| Lease expirada/reemplazada | 409 antes del primer ACK; nueva reserva actualiza token sin duplicar registro |
| Archivo alterado/incompleto | No COMMIT/ACK; incidente y reproceso controlado; hash completo obligatorio |
| Falla SQL/NTFS | No aceptación institucional falsa, sin folio publicado antes de COMMIT |
| Red/TLS interrumpidos | Timeouts/backoff acotados, secretos ausentes de logs |
| Dos consumidores | Exclusión y unicidad; sin registros/folios duplicados |
| Reinicio y traslado | Persisten documentos/folios/ACK pendientes y confianza histórica; un solo escritor/consumidor activo |

Pruebas automáticas, Windows sintético y E2E productivo son evidencias diferentes. Conservarlas por separado con versiones, hashes, fecha y omisiones.
