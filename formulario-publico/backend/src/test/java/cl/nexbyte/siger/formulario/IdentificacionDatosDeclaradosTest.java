package cl.nexbyte.siger.formulario;

import cl.nexbyte.siger.srcei.RunConsulta;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static cl.nexbyte.siger.formulario.IdentificacionCiudadana.*;
import static org.junit.jupiter.api.Assertions.*;

/** Mismas reglas que personalData.ts: el navegador adelanta mensajes; el servidor decide. */
class IdentificacionDatosDeclaradosTest {
    private final Clock clock = Clock.systemUTC();
    private Persona presentador() { return crear("presentador-adulto", Rol.PRESENTADOR, new RunConsulta("1", "9"), null, null, clock); }
    private Persona tercero() { return crear("senalado-1", Rol.PERSONA_SENALADA, null, null, null, clock); }

    @Test void aceptaNombresLegitimosNacionalesEInternacionales() {
        for (var name : new String[]{"María José", "O'Higgins", "O’Higgins", "Pérez-Cotapos", "De la Fuente", "Ma. José", "Jr.", "Zoë", "Łukasz",
                "Nguyễn", "Ng", "Иван", "Αλέξανδρος", "محمد", "Kaʻiulani", "李", "김", "D́aniel"}) {
            var declared = declarar(presentador(), Map.of("primerNombre", name), clock);
            assertEquals(name, declared.atributos().get("primerNombre").valor(), name);
            assertEquals(Fuente.DECLARACION_CIUDADANA, declared.atributos().get("primerNombre").fuente());
        }
    }
    @Test void rechazaNumerosSimbolosYSignosSinLetrasASusLados() {
        for (var name : new String[]{"Ana2", "١٢", "Ana@", "Ana_María", "<script>", "Ana/María", "Ana, María", "-Ana", "Ana-", "'Ana",
                "Ana--María", "Ana  María", "Ana - María", ".", "...", "Ana..", "A".repeat(101)})
            assertThrows(IllegalArgumentException.class, () -> declarar(presentador(), Map.of("primerApellido", name), clock), name);
    }
    @Test void exigeMasDeUnaInicialSoloAQuienPresenta() {
        for (var initial : new String[]{"J", "P."})
            assertThrows(IllegalArgumentException.class, () -> declarar(presentador(), Map.of("primerNombre", initial), clock), initial);
        assertEquals("J", declarar(tercero(), Map.of("primerNombre", "J"), clock).atributos().get("primerNombre").valor());
        assertThrows(IllegalArgumentException.class, () -> declarar(tercero(), Map.of("primerNombre", "J1"), clock));
        assertEquals("A".repeat(100), declarar(presentador(), Map.of("segundoNombre", "A".repeat(100)), clock).atributos().get("segundoNombre").valor());
    }
    @Test void numeroDeDocumentoExtranjeroRequiereContenidoReconocible() {
        var foreign = crearExtranjera(clock);
        for (var number : new String[]{"AB/123-xyz", "X1234567", "P 123.456", "Ж123"})
            assertEquals(number, declarar(foreign, Map.of("numeroDocumento", number), clock).atributos().get("numeroDocumento").valor(), number);
        for (var number : new String[]{"x", "1-2", "AB#123", "A".repeat(41), "../.."})
            assertThrows(IllegalArgumentException.class, () -> declarar(foreign, Map.of("numeroDocumento", number), clock), number);
    }
}
