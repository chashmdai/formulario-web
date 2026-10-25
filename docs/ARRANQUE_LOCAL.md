> **Lanzador Windows histórico.** No describe el arranque del laboratorio Linux/POSIX ni la activación V10. Consultar [README](../README.md), [operación](OPERACION_MULTIMEDIA.md) y [checkpoint vigente](CHECKPOINT_RECEPTOR_TRANSITORIO_2026-10-25.md) antes de iniciar procesos. No ejecutar este lanzador sobre la infraestructura Linux existente.

> Rutas actualizadas tras separar las aplicaciones. Esta guía inicia solo el Formulario Público. Consulta también MIGRACION_APLICACIONES.md.

# Levantar el Formulario Público después de reiniciar Windows

Hacer doble clic en **`FORMULARIO.cmd`** en la raíz del proyecto. También funciona desde PowerShell o CMD:

```powershell
Set-Location 'C:\Users\Benja\Desktop\pisee-claveunica\SIGER'
.\FORMULARIO.cmd
```

Esperar el mensaje **FORMULARIO PÚBLICO LISTO**. Se abre automáticamente:

[https://claveunica.formularioadulto.defensorianinez.gob.cl/](https://claveunica.formularioadulto.defensorianinez.gob.cl/)

Ahí pulsar «Iniciar sesión» y usar el RUN oficial `44.444.444-4`, clave de prueba `testing`, únicamente en Gobierno Digital. Tras autenticarse aparecerán RUN, nombres y apellidos de Sandbox en campos de solo lectura.

No es necesario abrir Codex, ejecutar npm ni iniciar scripts auxiliares por separado. `formulario-publico/scripts/Formulario.ps1` es el script madre; `FORMULARIO.cmd` lo ejecuta con PowerShell instalado en Windows. Los procesos quedan en segundo plano al terminar el asistente y se detienen al apagar el equipo. Volver a ejecutar `FORMULARIO.cmd` en el siguiente arranque.

## Otros comandos

```powershell
.\FORMULARIO.cmd estado
.\FORMULARIO.cmd detener
.\FORMULARIO.cmd iniciar -Reconstruir
```

`detener` conserva la resolución hosts y la confianza del certificado para el próximo inicio. `-Reconstruir` actualiza el JAR con cambios de Java y React; requiere Maven y Node/npm. Si el JAR falta, el asistente lo construye automáticamente. No se recompila por defecto en cada arranque.

## Qué comprueba automáticamente

1. Procesos anteriores de este checkout y registros de PID que quedaron de otro arranque. No detiene procesos cuyo inicio/ruta no coincida.
2. Herramientas portables y certificado local. La CA se confía solo en el usuario actual.
3. Resolución del nombre mediante el resolvedor de Windows. Si falta hosts, usa sudo o UAC para agregar exclusivamente la entrada de SIGER y vaciar caché DNS.
4. Caddy en loopback:443 y Spring con React empaquetado en loopback:8080, inicialmente con inicio bloqueado.
5. Certificado sin omitir validación, cookie Secure/HttpOnly/SameSite=Lax, origen público y rutas.
6. Habilitación exclusivamente Sandbox y redirección de autorización hacia el endpoint oficial, con URI/scopes/state correctos. El chequeo no sigue el redirect ni ingresa credenciales: la autenticación la hace el usuario en el navegador.

## Si vuelve a aparecer 503 o localhost

`Callback pendiente de habilitación institucional` significa que se usó el modo con ingreso bloqueado (`Start-Backend.ps1` o la primera etapa manual del perfil HTTPS). `npm run dev` muestra `localhost:5173`, que es otro recorrido de desarrollo.

Ejecutar **`FORMULARIO.cmd`**, esperar a que termine y utilizar el dominio HTTPS que abre. Si un puerto pertenece a un proceso no identificado, cerrar la terminal anterior de `mvn spring-boot:run` o el servicio correspondiente y volver a ejecutar. El asistente no mata servicios desconocidos.

Si falla una comprobación, el mensaje indica la causa. Los diagnósticos están en `.local/https/backend.log` y `backend-error.log`. No activar `CU_CALLBACK_APPROVED` manualmente para ocultar el fallo ni omitir la verificación de certificado.

Para retirar por completo hosts, CA y archivos locales, seguir [la reversión del entorno HTTPS](SANDBOX_HTTPS_LOCAL.md#detener-y-revertir). QA, Producción e infraestructura institucional no son modificados por este asistente.
