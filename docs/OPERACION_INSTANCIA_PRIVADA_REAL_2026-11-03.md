# Instancia privada con nodo real — 2026-11-03

Actualización de vigencia: este documento describe el arranque inicial. La operación actual RC1, la red fija y los resultados posteriores están en [RC1_CIERRE_2026-11-03.md](RC1_CIERRE_2026-11-03.md). No usar el overlay lab antiguo para operar el candidato RC1.

Instancia nueva `ddn-funcional-real`, raíz privada `/home/admin-ti/operacion-formulario/funcional-real-20261103`, sin reutilizar base/custodia de campañas anteriores. Rama release, referencia 392992f; imagen final inmutable del informe anterior. Seis servicios levantados: MySQL persistente nuevo, aplicación React/Spring, Caddy, ClamAV, Inspector y nodo real. Único puerto publicado: loopback 28443. Overlay exclusivamente `compose.pisee-production-lab.yml`; ningún stub conectado.

URL: https://formulario-real-privado.defensorianinez.gob.cl:28443/?vista=inicio

Firefox gráfico iniciado con perfil exclusivo `/home/admin-ti/snap/firefox/common/formulario-real-operativo-20261103`. Su cert9.db confía exclusivamente para ese perfil en la CA de la instalación; `network.dns.localDomains` resuelve el hostname a loopback dentro de Firefox. Sin cambios de confianza/DNS globales ni excepciones TLS. HTTPS del origen verificado con CA y hostname: /api/session 200, certificationMode=false, authenticated=false, loginAvailable=false.

El nodo usa exclusivamente `/home/admin-ti/operacion-formulario/release-20261103/pisee`, configuración/certificados RO y estado propio existente autorizado. GESCOD real: HTTP200, 747 bytes. Backend y nodo están unidos por la red privada productiva; no se publican 8084/8489. No se inició ninguna consulta personal real ni se empleó respuesta simulada.

## Bloqueos que impiden acreditar el objetivo completo

No hay identidad expresamente autorizada para esta campaña ni evidencia nueva de finalidad/base legal aprobada. La lista nueva está vacía; PISEE_SRCEI_ENABLED=false, ambiente PRODUCTION, PISEE_PRODUCTION_AUTHORIZED=false y PISEE_LEGAL_BASIS_APPROVED=false. Son barreras intencionales: no se cambiaron para fabricar un resultado. La integración de red está preparada, pero NO se acredita todavía recorrido UI → Java → SRCeI real. Una consulta deshabilitada puede ofrecer entrada manual; eso no es validación registral real.

ClaveÚnica está deshabilitada en esta instalación nueva, sin copiar el secreto de certificación. Falta un cliente/URI independiente autorizado para este origen privado o una decisión institucional sobre callbacks. No se ha comprobado un rechazo temporal de Gobierno Digital y no se atribuye el bloqueo a ese proveedor sin evidencia. El callback público permanece intacto. Certificación false no implica disponibilidad OAuth.

Recepción, envío registral e integración institucional/PULL permanecen cerrados; control inicial false/false SOLO_CONSULTA. No se suministró confianza ACK Windows ni se generaron claves destinadas a SIGER. Los recorridos y adjuntos existentes están en el JAR, pero no se acredita en este turno su recorrido completo con datos registrales reales. No se crearon/reclamaron Comunicaciones ni se ejecutó ACK. Las pruebas contractuales previas siguen siendo evidencia separada.

## Operación

Usar el compose base de infra/ubuntu y el overlay productivo con `--env-file /home/admin-ti/operacion-formulario/funcional-real-20261103/compose.env -p ddn-funcional-real`. No usar el overlay stub. Conservar los seis servicios operativos. Para abrir Firefox nuevamente usar ese perfil independiente; no omitir TLS. No cambiar el origen público ni su Tunnel.

Para habilitar una consulta UI real hace falta depositar una única identidad autorizada y su aprobación institucional en custodia privada, revisar finalidad/retención, y entonces activar exclusivamente los gates productivos de esta instancia. Mantener `formulario.srcei.envio-autorizado=false`, recepción/PULL cerrados y evitar toda Comunicación real hacia desarrollo. La sonda Java del manual anterior es complementaria y no reemplaza el recorrido UI requerido.

Verificación del sitio publicado: 30/30 controles correctos. Comparación antes/después: 597 archivos institucionales y los 23 contenedores preexistentes idénticos. Nuevos recursos exclusivamente bajo el proyecto indicado. Inventarios privados: `release-20261103/funcional-antes.json`, `funcional-real-despues.json`; arranque y diagnóstico en `funcional-real-arranque.log` y `funcional-real-reinicio.log`, fuera de Git. No adjuntar las configuraciones privadas.

Resultado: infraestructura privada operativa con nodo real, sin simulador; objetivo de consulta personal UI real pendiente de autorización e identidad. No se declara éxito integral ni habilitación ciudadana.
