# Arranque Ubuntu sin dependencias gubernamentales ni confianza SIGER

Cambio acotado sobre H1 `bfb950690e85381b69e6dcfac167f0a237c80071`, autorizado para GATES U0–U6. No modifica Flyway V1–V12, contratos, firmas ni proveedores. La instalación inicial sigue cerrada; no representa apertura institucional.

## Preparación nueva

```bash
sudo python3 scripts/ubuntu/preparar.py /srv/ddn-formulario-institucional \
  --commit SHA_COMPLETO_VERIFICADO --project ddn-formulario-institucional \
  --sin-claveunica --sin-confianza-ack
```

`--sin-claveunica` omite credenciales y establece `siger.claveunica.habilitada=false`, `callback-approved=false`. No se crea registro OAuth ni se instala el login OAuth. Login y callback devuelven 503 sin redirección; sesión informa loginAvailable=false; logout local conserva CSRF y no consulta proveedor. Las validaciones de URI/TLS/cookie siguen vigentes. Sin el flag, habilitada conserva true por compatibilidad y exige credenciales y validaciones originales.

`--sin-confianza-ack` no crea archivo ni clave institucional. Establece `publico.integracion.habilitada=false`: todas las rutas /integracion responden 503 incluso con token correcto; el mapa de confianza ACK en memoria está vacío. Se exige recepción adulta/NNA, avisos, mantenimiento y retirada deshabilitados, y controles persistidos cerrados. El token operacional no puede habilitar recepción/PULL (409). El parser general de confianza sigue rechazando mapas vacíos; habilitar integración vuelve a exigir el archivo real. No se generan claves SIGER ficticias.

Compose monta un directorio CU privado y opcional de configtree. Vacío en esta etapa; sin contraseñas dummy ni variables OAuth inventadas. Corrección adicional de H1: incluir mysql.cnf referenciado por Compose (faltaba en Git), con TLS obligatorio, certificados propios, MySQLX deshabilitado y límites de binlog/buffer.

## Incorporación posterior sin recompilar

No volver a ejecutar el preparador sobre una instalación existente ni regenerar sus claves. En ventana con app detenida:

1. CU: archivos privados 0400, uid1000, en `cu/siger.claveunica.client-id` y `cu/siger.claveunica.client-secret`. Elegir algoritmo/env y verificar registro exacto de `/redirect` y `/logout` con Gobierno Digital. Configuración externa: `siger.claveunica.habilitada=true`, callback-approved=true solo cuando esté acreditado. Se conservan verificaciones originales de secreto/algoritmo, URI HTTPS institucional, cookie Secure, state/nonce/firmas/token/userinfo. Recrear solo app; no requiere build. Volver a false y recrear app revierte la disponibilidad.
2. SIGER: instalar mapa público auténtico Ed25519 en `keys/institutional-trust.json`, uid1000/0400, identificadores exactos del firmante. Revisar confianza por canal privado, separar claves del recibo y ACK. Cambiar `publico.integracion.habilitada=true` y recrear app; todavía mantener controles BD cerrados y recepción false. El token técnico propio se entrega por canal privado, nunca por Git/chat. Apertura de PULL/recepción exige el GATE específico y controles autenticados.
3. PISEE permanece false. No es parte del JAR ni de este arranque; recibir ZIP solo en su etapa. Ningún smoke usa proveedores gubernamentales.

## Verificación

Pruebas nuevas cubren arranque sin credenciales, ausencia de registro OAuth, callback falsificado sin autenticación, loginAvailable=false, identidad vacía, logout local/CSRF, integración cerrada con token correcto, rechazo de apertura operacional sin confianza y fallos de configuración incoherente. Regresiones existentes de proveedor controlado verifican flujo habilitado, token/userinfo/firmas/logout. Ejecutar build completo antes de publicar/desplegar; registrar resultados reales separadamente.

## Interoperabilidad y dependencias

Compose publica únicamente 127.0.0.1:24443. No sirve todavía como endpoint LAN para Windows. Hace falta IP/nombre del consumidor SIGER QA, ruta privada autorizada, resolución de nombre institucional y confianza en la CA pública de origen. Se puede acordar transporte SSH local o publicación exclusivamente en IP LAN con política institucional revisada; no cambiar firewall/SSH ni publicar Internet automáticamente. Windows verifica SAN del nombre canónico y cadena CA, nunca omite verificación TLS.

Prueba futura: intercambio de claves públicas y token por canal privado → HTTPS Windows→Ubuntu → recepción exclusivamente sintética controlada → PULL/reserva → hashes de originales → COMMIT/folio en SIGER → ACK Ed25519 → CONFIRMADA. No acceso remoto/modificación Windows sin autorización. No habilitar dos consumidores sobre el mismo endpoint/lease sin coordinación.

PISEE: seguir INTEGRACIONES_UBUNTU.md al recibir paquete auténtico; staging privado nuevo, inspección antes de extracción, Linux/dependencias, certificados vigentes/coincidentes, estado persistente nuevo y red privada. No solicitar paquete ni hacer consulta real durante instalación cerrada.

U6: faltan credenciales CU, algoritmo y registro/habilitación de URI, recorrido real autorizado, dominio/DNS/túnel/cuenta/políticas y certificados. Apertura pública y recepción ciudadana permanecen decisiones separadas. Un backup/restauración en el mismo SSD no acredita respaldo externo, RPO/RTO institucional ni continuidad física.

Validación local previa a publicar (DDN-UBUNTU, 02/11/2026): build multietapa final correcto; lint/212 pruebas React/build, 376 pruebas backend (374 correctas, 2 omisiones opcionales), 27 pruebas Python (26 correctas, 1 omisión de clase por falta de JDK en host). Los lanzadores con JDK se verificarán en contenedor. Una primera ejecución de la prueba nueva usó esquema HTTP en MockMvc y verificó la redirección HTTPS; corregido el fixture a HTTPS, sin alterar controles. Error transitorio Maven Central 502 resuelto con reintento.

Hallazgo físico adicional U2: la imagen ClamAV mínima no incluía CA públicas y freshclam fallaba con código77. Se añade ca-certificates (sin omitir verificación TLS). Descarga oficial HTTPS y comprobación interna de las tres bases correctas: daily28172, main63, bytecode339. sigtool no está instalado en esta imagen mínima; la validación la realiza freshclam/libclamav y se completa con carga real del daemon y prueba EICAR en U3.
