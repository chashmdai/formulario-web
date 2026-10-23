# Corrección de identidad: componentes originales de ClaveÚnica

**Actualización visual del 15-10-2026:** ahora se muestran número de RUN/DV de origen y solo dos nombres/dos apellidos. Los componentes completos siguen conservados; [entrega vigente](ENTREGA_RUN_DV_2026-10-15.md). Las capturas siguientes documentan la presentación anterior.

Implementación sobre el repositorio vigente. No cambian OIDC, URIs, credenciales, flujo institucional o SRCeI.

## Comportamiento

IdentidadVerificada conserva nombresComponentes/apellidosComponentes como listas inmutables provenientes directamente de userinfo.name. No recorta sus elementos ni modifica espacios, letras, tildes, mayúsculas o composición Unicode. nombres/apellidos continúan siendo cadenas derivadas para compatibilidad y presentación.

Sesión, vista previa, proyección por atributo y sobres nuevos conservan las listas. React muestra Primer/Segundo/Tercer nombre y Primer/Segundo apellido si existen; los adicionales también se muestran. Cada valor es readonly y cada elemento compuesto permanece unido. No se usa separación por espacios.

## Integridad e históricos

Los sobres nuevos son versión 1.1 e incluyen listas, orden y cantidades en su huella. La evidencia de autenticación también distingue límites diferentes aunque el texto concatenado coincida. Los comprobantes públicos/institucionales siguen vinculados a las huellas, sin modificar sus algoritmos.

Los sobres 1.0 conservan cadenas, huella y comprobante; componentes ausentes, nunca reconstruidos. Los reintentos recuperan el mismo resultado histórico. No se actualizaron filas o firmas de demostración. No se necesitan cambios SQL, pero se deben desplegar ambos artefactos antes de aceptar sobres 1.1. [Contrato y algoritmo](../contratos/identidad-componentes-v1.1.md).

## Pruebas efectivamente realizadas

- Build React, TypeScript, lint y ocho pruebas UI exitosos. Ambos JAR compilados y empaquetados.
- Batería final con MySQL 8.4.11 real: 52 pruebas públicas y 29 institucionales exitosas; cero fallos/errores. Cuatro pruebas especiales de carga/caída quedan omitidas por requerir coordinador y no se cuentan como éxitos.
- Compuestos, nombres/apellidos adicionales, opcionales ausentes, orden, espacios extremos/interiores, tildes y Unicode descompuesto, copias inmutables, entradas inválidas y evidencia vinculada a componentes.
- Sesión/vista previa/proyección conservan las mismas listas; rechazo de atributos suministrados por cliente conservado.
- Vector de huella independiente 1.1 comprobado en ambos artefactos; redistribuir componentes con el mismo texto invalida la integridad.
- Persistencia pública e institucional de listas, firma y comprobante recuperable; fixtures históricos firmados y reintentos preservados.
- Verificación independiente del comprobante anterior COM-000002: firma válida y SHA-256 del archivo intacto.

## Autenticación real repetida

El 14-10-2026 a las 22:01:54 de Santiago se completó el flujo oficial Sandbox, con el backend consultando userinfo. El frontend mostró:

~~~json
{
  "nombres": ["Maria", "Carmen", "De los angeles"],
  "apellidos": ["Del rio", "Gonzalez"]
}
~~~

Estos son los componentes del proveedor proyectados por el backend; no una división del texto anterior. Se verificaron seis campos readonly contando RUN. Solo se guardó esta identidad ficticia oficial y la observación de sus campos; no una respuesta completa de autenticación ni tokens.

[Captura](evidencias/identidad-componentes-2026-10-14/sandbox-campos-separados.png), [estructura y controles](evidencias/identidad-componentes-2026-10-14/sandbox-componentes.json), [resumen de pruebas](evidencias/identidad-componentes-2026-10-14/pruebas.json). Se cerró la sesión local y de CU; público habitual iniciado, SIGER y MySQL detenidos.

## Repetir

~~~powershell
.\scripts\Recepcion-Local.ps1 detener
.\scripts\Build-Todo.ps1
.\scripts\Robustez.ps1 pruebas
.\scripts\Local-MySQL.ps1 detener
.\FORMULARIO.cmd iniciar
~~~

Abrir el origen HTTPS institucional local habitual y usar únicamente la identidad ficticia oficial de Sandbox en Gobierno Digital. La prueba visual no requiere Vite, SIGER ni MySQL. Los comandos de registro de Comunicación anteriores permanecen disponibles, con ambos JAR actualizados.
