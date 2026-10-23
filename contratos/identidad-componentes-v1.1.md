# Identidad estructurada y compatibilidad histórica

**Proyección aditiva de sesión (15-10-2026):** runNumero/runDv se conservan directamente desde RolUnico.numero/DV, con procedencia CU. El RUN completo permanece compatible; el sobre firmado 1.1 y su algoritmo no cambian. No se infieren partes para identidades históricas.

Los elementos de userinfo.name.nombres y userinfo.name.apellidos se conservan en orden y sin trim, cambios de mayúsculas, tildes o normalización Unicode. Nunca se separan cadenas por espacios.

## Sesión, vista previa y nuevos registros

El contrato mantiene nombres/apellidos como textos derivados de presentación y compatibilidad, y añade nombresComponentes/apellidosComponentes como listas fuente. Las cadenas se calculan con String.join(" ", componentes); el servidor exige correspondencia exacta.

Ejemplo exclusivamente sintético:

~~~json
{
  "nombres":"María José De los ángeles",
  "apellidos":"Del Río González de la Fuente",
  "nombresComponentes":["María José","De los ángeles"],
  "apellidosComponentes":["Del Río","González de la Fuente"]
}
~~~

GET /api/session y POST /api/comunicaciones/preview conservan esa estructura. GET /api/identidad/atributos añade componentes a la afirmación de nombres/apellidos, manteniendo fuente, nivel y evidencia. No se transforma la lista de afirmaciones de distintas fuentes en una lista de nombres. SRCeI sigue NO_HABILITADO.

Principal y contratos usan copias inmutables. Cada lista conserva los límites anteriores de seguridad: entre 1 y 10 elementos, cada cadena no blanca y de hasta 100 unidades UTF-16. Se rechazan controles y Unicode con sustitutos no emparejados; no se corrigen ni truncan. La vista vigente desde 15-10-2026 muestra únicamente los dos primeros nombres/apellidos, sin limitar las listas de sesión o del sobre.

## Sobre de Comunicación 1.1

La entrega firmada v2 y el registro/ACK v3 se conservan. Su campo comunicacion admite ahora:

- 1.0: identidad histórica con cadenas y sin componentes. Sus huellas y comprobantes mantienen el algoritmo y formato anteriores.
- 1.1: requiere las dos listas, además de los textos derivados. No se puede quitar listas, alterar límites ni degradar a 1.0 conservando la huella.

La huella SHA-256 de 1.1 codifica estos campos, UTF-8, cada uno precedido por longitud de bytes como entero big endian de 32 bits:

1. version, id, recibidaEn UTC, identidad.run, identidad.nombres, identidad.apellidos.
2. Cantidad de nombres como cadena decimal; cada elemento de nombresComponentes, en orden.
3. Cantidad de apellidos como cadena decimal; cada elemento de apellidosComponentes, en orden.
4. mensaje, avisoPrivacidadVersion.

El [vector independiente](fixtures/comunicacion-identidad-v1.1.json) se verifica en ambos artefactos. Los sellos públicos y comprobantes institucionales siguen vinculados a esa huella y a la procedencia de nombres/apellidos. La evidencia de sesión también distingue componentes diferentes aunque produzcan el mismo texto concatenado.

Los registros históricos mantienen componentes ausentes, cadenas intactas y firmas anteriores; no se migran a listas ni se reemiten comprobantes. Reintentar una operación pública ya existente devuelve su resultado original. La UI conserva la presentación agregada cuando solo existe una identidad histórica.

## Actualización

Compilar y actualizar ambos JAR coordinadamente antes de aceptar sobres 1.1. Un consumidor antiguo que solo admita 1.0 no puede interpretar los nuevos sobres. No cambia el protocolo OIDC, las URIs, las credenciales, las rutas v3 ni el mecanismo de folio/registro. No se necesitan migraciones SQL: el sobre versionado ya se guarda como JSON.
