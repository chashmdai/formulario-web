# Contrato de visualización y separación documental

Esta unidad implementa el visor ciudadano y prepara el contrato institucional. La interfaz funcionaria y su autorización no se implementan en `sistema-de-requerimientos`.

## Origen y asociaciones

**Documentos del ingreso:** originales aportados al Formulario Público, enlazados a la Comunicación y sus descriptores firmados. Su origen `INGRESO_CIUDADANO` se deriva en el servidor de recepción, no de un campo enviado por el navegador. La firma conserva el canal y las personas del ingreso.

**Documentos de gestión:** archivos incorporados posteriormente por funcionarios y vinculados a Casos, actuaciones u otros objetos. Su origen y autoría deben asignarse por el servidor institucional bajo autorización funcionaria. No se incorporan retroactivamente al documento ciudadano firmado.

El modelo institucional debe tener un objeto documental por evidencia original y relaciones a múltiples objetos/Casos. Asociar una Comunicación a otro Caso no copia el binario ni cambia su UUID/hash/origen. Un hash global no debe utilizarse como buscador público de documentos ajenos. El archivo definitivo institucional se separa de la custodia pública mediante recepción privada verificada y referencias de correlación; la política de transferencia/retención sigue pendiente de aprobación.

## Proyección implementada para ciudadanía

`GET /api/formulario/recorridos/{recorrido}/adjuntos/{id}/vista`, con HTTPS y sesión propietaria, devuelve:

```json
{"version":"1.0","origen":"INGRESO_CIUDADANO","original":{"id":"UUID","nombre":"evidencia.pdf","tamanio":1234,"sha256":"HEX64","tipo":"application/pdf","estado":"VALIDADO"},"representacion":"IMAGEN_DERIVADA","originalInline":false,"descargable":true,"huellaDerivada":"HEX64","tamanioDerivada":456}
```

`representacion` es `IMAGEN_DERIVADA`, `AUDIO`, `VIDEO` o `DESCARGA`. La proyección no forma parte del documento firmado ni concede acceso por sí sola. No contiene rutas físicas ni URLs permanentes.

`GET|HEAD .../representacion` permite solo PNG derivado o audio/video previamente validados. `GET|HEAD .../adjuntos/{id}` descarga el original. Se verifica recurso, propiedad, tamaño/hash y se responde sin cache, con nosniff, CSP sandbox y política de mismo origen. Los históricos sin inspección ampliada ofrecen descarga, no inline.

La UI muestra imágenes/PDF mediante PNG, audio y video mediante reproductor nativo y Office mediante descarga. PDF muestra **primera página**, no un visor PDF completo. No existe conversión Word a PDF, transcodificación ni miniatura de video. Las derivadas tienen hash independiente y nunca sustituyen originales. No hay iframe de Office, visualizador externo ni servicio de pago.

## Implementación futura en SIGER

Se propone una API institucional de metadatos/documento con autorizaciones por actor y por relación al Caso/actuación, más endpoints separados para original y representaciones. Los nombres de rutas deberán definirse en ese repositorio; esta propuesta no anuncia endpoints institucionales ya disponibles.

El consumidor extrae originales mediante la API de [entrega privada y reserva](../contratos/formulario-completo-multimedia-v1.1.md), los guarda una sola vez en su archivo privado y verifica hashes. Después, el visor funciona con permisos institucionales por recurso. No usa la cookie ciudadana, el UUID como autorización, URLs públicas ni una reserva de extracción vencida como sesión de visor.

- Rangos únicos GET, 206/416, If-Range/ETag, HEAD y límites de transferencia permiten reproducción y navegación sin descarga manual completa. Autorización y auditoría se aplican también a HEAD y Range.
- Original Office/PDF activo nunca se ejecuta. Una futura representación Word/PDF por páginas requiere worker sin red ni archivo global montado, usuario sin privilegios, filesystem privado por trabajo, límites CPU/memoria/disco/PIDs/tiempo, proceso descartable y verificación de salida. No habilitar una conversión nativa sin ese aislamiento.
- CSP y tipos estrictos en el dominio institucional: no contenido activo, no interpretaciones HTML del nombre, no sniffing ni cache compartido. Auditar la solicitud con referencia de actor, recurso y representación; no registrar tokens, nombres, contenido o query sensible.
- La auditoría actual registra **solicitudes**, no prueba que se haya completado una reproducción/descarga. Guarda referencias SHA-256 de capacidades de sesión/reserva; el visor futuro debe identificar al funcionario y su fundamento de acceso sin almacenar credenciales.
- Si no hay representación segura o el navegador no reproduce el codec, ofrecer descarga controlada. Mantener metadatos de la derivada (versión, hash, motor/perfil) separados del original; no reescribir su firma.

Quedan por coordinar permisos institucionales, categorías de documentos de gestión, perfil de conversión Office, accesibilidad del visor funcionarial, codec objetivo, custodia definitiva y retención. No se elimina automáticamente evidencia después del ACK.
