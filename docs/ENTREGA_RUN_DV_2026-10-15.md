# Entrega: RUN/DV separados e investigación de identidad

15-10-2026, America/Santiago. Cambios sobre el repositorio vigente, sin migraciones SQL o entidades institucionales nuevas.

## Interfaz y contrato

La autenticación conserva RolUnico.numero y RolUnico.DV directamente como runNumero/runDv en la identidad de sesión y su proyección. Se valida su correspondencia con el RUN completo compatible; nunca se utiliza split para deducirlos.

La interfaz muestra Número de RUN, Dígito verificador, Primer/Segundo nombre y Primer/Segundo apellido existentes, todos readonly. Limitar la vista a dos componentes no modifica las listas. Se conserva el tercer nombre, así como cualquier otro componente admitido, en sesión, procedencia y nuevas Comunicaciones. La vista previa no vuelve a mostrar la identidad agregada con los componentes adicionales.

Cambios de implementación: IdentidadVerificada.java, ProyeccionAtributos.java, api.ts, App.tsx y pruebas correspondientes. OIDC, sobre 1.1, algoritmos de huella/firma, folios y registro/ACK institucional se conservan. Las identidades históricas sin partes de RUN no adquieren partes inferidas.

## Evidencia efectiva

Se completó autenticación real Sandbox con la identidad ficticia oficial. La pantalla mostró los seis campos requeridos. Una lectura filtrada de nuestra API de sesión conservó:

~~~json
{
  "runNumero": "44444444",
  "runDv": "4",
  "nombresComponentes": ["Maria", "Carmen", "De los angeles"],
  "apellidosComponentes": ["Del rio", "Gonzalez"]
}
~~~

La Comunicación sintética 5e581915-5e85-47f3-b356-72b2560d7ca1 quedó primero pendiente con SIGER detenido. Al continuar con los procesos nuevamente iniciados, SIGER la registró como COM-000004 y confirmó el mismo registro al público. Se verificaron las tres partes de nombres en ambos almacenes y su comprobante Ed25519 con una herramienta Node independiente.

Se compararon las cuatro filas históricas de los dos almacenes que se habían leído antes de la prueba: SHA-256 de ID/cuerpo/comprobante o ACK sin cambios. Se preservaron los demás registros; no se borró ni reconstruyó identidad histórica. Las lecturas usaron cuentas locales limitadas y solo se publicaron hashes o datos ficticios seleccionados.

- [Pantalla readonly](evidencias/run-dv-identidad-2026-10-14/interfaz-run-dv.png).
- [Sesión filtrada](evidencias/run-dv-identidad-2026-10-14/sandbox-identidad-filtrada.json).
- [Registro confirmado y tercer nombre](evidencias/run-dv-identidad-2026-10-14/resultado-recorrido.json).
- [Comprobante](evidencias/run-dv-identidad-2026-10-14/comprobante-nuevo.json).
- [Comparación histórica](evidencias/run-dv-identidad-2026-10-14/comparacion-historicos.json).
- [Pruebas y verificación criptográfica](evidencias/run-dv-identidad-2026-10-14/pruebas-finales.json).

Ambos JAR compilados; lint/TypeScript/build y ocho pruebas frontend correctos. Batería MySQL real: 53 pruebas públicas y 29 institucionales exitosas, sin fallos/errores. Cuatro pruebas especiales de carga/caída están omitidas por activación explícita, no contadas como éxitos. Se mantienen los vectores históricos firmados, idempotencia, integridad, ACK y controles existentes.

## Investigación y propuesta para revisar

El [diagnóstico con fuentes oficiales](IDENTIDAD_CLAVEUNICA_ATRIBUTOS_Y_PERSISTENCIA_2026-10-14.md) distingue pruebas Sandbox, documentación y datos pendientes de confirmar. No se halló soporte oficialmente documentado para ampliar nuestra integración con correo, nacimiento, domicilio, sexo o teléfono; el estándar OIDC por sí solo no concede esos atributos.

La recomendación es mantener la instantánea por Comunicación y evaluar posteriormente una referencia institucional de persona, conservando la evidencia de cada ingreso. No se implementaron persona, fusión, rectificaciones automáticas, retención o adaptadores SRCeI.

## Repetir

~~~powershell
.\scripts\Recepcion-Local.ps1 detener
.\scripts\Build-Todo.ps1
.\scripts\Robustez.ps1 pruebas
.\scripts\Recepcion-Local.ps1 publico
.\scripts\Recepcion-Local.ps1 siger
~~~

Los esquemas locales y sus claves ya están preparados. El script de habilitación existente permite activar los controles del laboratorio si se cerraron; solo utilizar datos ficticios. El origen HTTPS Sandbox habitual se conserva. Detener la demo con Recepcion-Local.ps1 detener, MySQL con Local-MySQL.ps1 detener y volver al público habitual con FORMULARIO.cmd iniciar.

Estado al cerrar: público habitual iniciado en HTTPS local, sin recepción JDBC habilitada en ese modo; SIGER y MySQL detenidos, con sus datos conservados. El recorrido completo se inicia con los scripts anteriores.
