# Corrección funcional SRCeI — instancia privada

## Diagnóstico y cambio

Las consultas iniciales desde Java terminaban en AUTORIZACION, sin datos normalizados. El nodo productivo admitía exclusivamente loopback; el backend usa la red Docker privada. Se añadió exclusivamente la IP privada exacta del backend a `servidores.interno.seguridad.ipPermitidas` de la copia institucional autorizada, sin modificar credenciales, certificados, servicios o XML. Se reinició únicamente el nodo privado. No es un problema de serialización U+006C ni un éxito HTTP con datos vacíos.

Tras recrear backend o red, verificar que su IP en `pisee_privada` sigue coincidiendo con esa única entrada. Si cambia, detener la demostración hasta ajustar esa entrada exacta; no ampliar permisos a toda la subred. El nodo no publica puertos al host. Original e histórico permanecen intactos.

En código se comprueba que nombres agregados sean útiles antes de ocultar campos. El mensaje ciudadano deja de afirmar que se obtuvieron antecedentes cuando el proveedor pudo fallar. Se agregaron pruebas de corrección vacía, fallback y proyección con lista cerrada de propiedades, sin valores registrales ni acceso desde otra sesión. Los contratos de intercambio permanecen intactos.

## Evidencia real y visual

Con la única identidad ingresada en custodia privada, Firefox gráfico ejecutó el recorrido React → Spring Boot → ClientePisee → nodo productivo → SRCeI. Resultado interno COMPLETO, siete atributos registrales minimizados con procedencia SRCEI. No se reproducen valores, RUN, XML, identificadores de recorrido ni secretos.

Respuesta ciudadana: cero campos faltantes y cero valores declarados; no contiene los antecedentes registrales. La identidad continúa como ADULTO_DECLARADO, autenticada=false. Se verificaron visualmente campos obtenidos ocultos, corrección voluntaria con campos vacíos y navegación hasta revisión de comunicación con texto de demostración. La recarga restaura la proyección privada sin reconsultar. No se envió la comunicación.

Firefox de comprobación: perfil independiente `formulario-real-auditoria-20261103`, CA local en ese perfil, HTTPS y hostname verificados, sin excepciones TLS ni interceptar respuestas. En la orientación, el controlador automatizado requirió eventos DOM; las capturas finales deshabilitan animaciones para evitar espera del controlador. Esto no altera validaciones ni proveedor. Queda abierto en la identificación revisada. La ventana previa puede conservar un fallback de una consulta antigua: reiniciar explícitamente su identificación o comenzar otro recorrido; no se convierten retrospectivamente rechazos en éxitos.

Capturas revisadas sin valores personales y resultado resumido permanecen exclusivamente en custodia privada, fuera de Git. Los estados PARCIAL, VACÍO, ERROR y corrección persistida con ambas procedencias se cubren con fixtures sintéticos automatizados; no se provocaron errores artificiales en SRCeI productivo ni se declararon correcciones falsas sobre la identidad real.

## Validación y operación

Código funcional: `2253b23`. Imagen inmutable: `sha256:ed535010b9a55307512ca131a4ec97afc79920f3ee3a5a62590a60f1fab9578a`.

Pruebas específicas: 48 backend (incluido XML) y 16 React, sin fallos. La construcción Docker completó los gates de lint, React, TypeScript/Vite y backend; la segunda construcción reutilizó esos pasos al fijar la etiqueta de revisión. No se usan identidades reales en pruebas automatizadas unitarias.

Instancia `ddn-funcional-real`, HTTPS únicamente en loopback 28443. URL: https://formulario-real-privado.defensorianinez.gob.cl:28443/?vista=inicio

Recepción, PULL y envío registral permanecen cerrados. Ninguna conexión Windows ni ACK físico. ClaveÚnica no se modificó durante esta corrección. El sitio publicado pasó 30/30 controles y conserva los 597 archivos vigilados. Los inventarios antes/después se guardan en la raíz privada de la instancia. Los servicios privados quedan operativos.

Para revertir exclusivamente el código privado, restaurar en su `compose.env` el digest anterior `sha256:a5d808743d56e7fcc9edf2a9a345898fa8dea7bb792f87e464e1ebf3f1d0c69b` y recrear únicamente `app` con compose base y overlay productivo. No bajar volúmenes ni modificar el sitio institucional. La entrada exacta del backend en el nodo debe conservarse para mantener la conectividad autorizada; retirarla cierra de nuevo ese acceso.
