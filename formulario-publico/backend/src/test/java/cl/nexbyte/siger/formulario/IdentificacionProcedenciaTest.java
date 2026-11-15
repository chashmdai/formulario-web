package cl.nexbyte.siger.formulario;

import cl.nexbyte.siger.identidad.EvidenciaAutenticacion;
import cl.nexbyte.siger.identidad.IdentidadVerificada;
import cl.nexbyte.siger.srcei.ResultadoSrcei;
import cl.nexbyte.siger.srcei.RunConsulta;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static cl.nexbyte.siger.formulario.IdentificacionCiudadana.*;
import static org.junit.jupiter.api.Assertions.*;

/** Los antecedentes PISEE/SRCEI o ClaveÚnica no se sustituyen por declaraciones; solo se completan campos faltantes. */
class IdentificacionProcedenciaTest {
    private final Clock clock = Clock.systemUTC();
    private static ResultadoSrcei.Atributo srcei(String value) {
        return new ResultadoSrcei.Atributo(value, null, "SRCEI", "CONTRASTADO_EN_FUENTE", Instant.now(), UUID.randomUUID());
    }
    private static ResultadoSrcei resultado(ResultadoSrcei.Estado estado, Map<String, ResultadoSrcei.Atributo> attrs) {
        return new ResultadoSrcei(estado, attrs, Instant.now(), UUID.randomUUID(), null);
    }
    private static final Map<String, ResultadoSrcei.Atributo> COMPLETO = Map.of("nombres", srcei("NOMBRE REGISTRAL PRIVADO"),
        "primerApellido", srcei("APELLIDO REGISTRAL"), "segundoApellido", srcei("MATERNO REGISTRAL"),
        "fechaNacimiento", srcei("1990-01-01"), "sexo", srcei("F"), "nacionalidad", srcei("Chile"));
    private Persona adulto(ResultadoSrcei result) {
        return crear("presentador-adulto", Rol.PRESENTADOR, new RunConsulta("1", "9"), null, result, clock);
    }

    @Test void consultaCompletaNoAdmiteSustituirNingunAntecedenteRegistral() {
        var person = adulto(resultado(ResultadoSrcei.Estado.COMPLETO, COMPLETO));
        var view = vista(person);
        assertTrue(view.camposFaltantes().isEmpty(), view.camposFaltantes().toString());
        assertTrue(view.valoresDeclarados().isEmpty());
        for (var attempt : List.of(Map.of("primerNombre", "OTRO"), Map.of("segundoNombre", "OTRO"), Map.of("primerApellido", "OTRO"),
                Map.of("segundoApellido", "OTRO"), Map.of("fechaNacimiento", "1991-02-02"), Map.of("sexo", "M"), Map.of("nacionalidad", "Perú"),
                Map.of("primerApellido", ""), Map.of("tipoDocumento", "PASAPORTE")))
            assertThrows(IllegalArgumentException.class, () -> declarar(person, attempt, clock), attempt.keySet().toString());
        assertEquals("NOMBRE REGISTRAL PRIVADO", person.atributos().get("nombres").valor());
        assertTrue(person.discrepancias().isEmpty());
    }

    @Test void consultaParcialSoloCompletaLoQueFalta() {
        var person = adulto(resultado(ResultadoSrcei.Estado.PARCIAL, Map.of("nombres", srcei("NOMBRE REGISTRAL PRIVADO"), "primerApellido", srcei("APELLIDO REGISTRAL"))));
        var view = vista(person);
        assertEquals(Set.of("segundoApellido", "fechaNacimiento", "sexo"), view.camposFaltantes());
        assertTrue(view.camposObligatorios().isEmpty(), "primerNombre/primerApellido ya están en la fuente");
        assertThrows(IllegalArgumentException.class, () -> declarar(person, Map.of("primerApellido", "OTRO", "segundoApellido", "MATERNO"), clock));
        var declared = declarar(person, Map.of("segundoApellido", "MATERNO", "sexo", "M"), clock);
        assertEquals(Fuente.SRCEI, declared.atributos().get("primerApellido").fuente());
        assertEquals(Fuente.SRCEI, declared.atributos().get("nombres").fuente());
        assertEquals(Fuente.DECLARACION_CIUDADANA, declared.atributos().get("segundoApellido").fuente());
        assertEquals(Map.of("segundoApellido", "MATERNO", "sexo", "M"), vista(declared).valoresDeclarados());
        // Lo ya declarado sigue editable, pero lo registral no.
        var edited = declarar(declared, Map.of("segundoApellido", "OTRO MATERNO"), clock);
        assertEquals("OTRO MATERNO", edited.atributos().get("segundoApellido").valor());
        assertThrows(IllegalArgumentException.class, () -> declarar(edited, Map.of("primerNombre", "OTRO"), clock));
    }

    @ParameterizedTest @EnumSource(value = ResultadoSrcei.Estado.class, names = {"VACIO", "RED", "TIMEOUT", "NEGATIVA", "NO_HABILITADO"})
    void consultaSinDatosPideNombresSinAfirmarExistencia(ResultadoSrcei.Estado estado) {
        var person = adulto(resultado(estado, Map.of()));
        var view = vista(person);
        assertTrue(view.camposFaltantes().containsAll(Set.of("primerNombre", "primerApellido", "fechaNacimiento", "sexo")));
        assertEquals(Set.of("primerNombre", "primerApellido"), view.camposObligatorios());
        var declared = declarar(person, Map.of("primerNombre", "NOMBRE", "primerApellido", "APELLIDO"), clock);
        assertTrue(completa(declared));
    }

    @Test void valorNoUtilDeLaFuenteSePuedeCompletarYSeRestituyeAlRetirarLaDeclaracion() {
        var person = adulto(resultado(ResultadoSrcei.Estado.PARCIAL, Map.of("nombres", srcei("NOMBRE"), "primerApellido", srcei("APELLIDO"), "sexo", srcei("Desconocido"))));
        assertTrue(vista(person).camposFaltantes().contains("sexo"));
        var declared = declarar(person, Map.of("sexo", "F"), clock);
        assertEquals(Fuente.DECLARACION_CIUDADANA, declared.atributos().get("sexo").fuente());
        var cleared = declarar(declared, Map.of("sexo", ""), clock);
        assertEquals(Fuente.SRCEI, cleared.atributos().get("sexo").fuente());
        assertFalse(cleared.discrepancias().containsKey("sexo"));
    }

    @Test void claveUnicaNoAdmiteDeclaracionesSobreNombresVerificados() {
        var identity = new IdentidadVerificada("12345678-5", "NOMBRE CU", "APELLIDO CU", List.of("NOMBRE", "CU"), List.of("APELLIDO", "CU"));
        var person = crear("presentador-adulto", Rol.PRESENTADOR, null, new AutenticacionCiudadana(identity, EvidenciaAutenticacion.de(identity)),
            resultado(ResultadoSrcei.Estado.PARCIAL, Map.of("nombres", srcei("OTRO NOMBRE"))), clock);
        for (var key : List.of("primerNombre", "segundoNombre", "primerApellido", "segundoApellido"))
            assertThrows(IllegalArgumentException.class, () -> declarar(person, Map.of(key, "SUSTITUTO"), clock), key);
        assertTrue(vista(person).valoresDeclarados().isEmpty());
    }

    @Test void recorridoHistoricoConCorreccionConservaEdicionYRestituyeRegistroAlRetirarla() {
        var base = adulto(resultado(ResultadoSrcei.Estado.COMPLETO, COMPLETO));
        // Estado persistido por la corrección retirada: la declaración desplazó los antecedentes a discrepancias.
        var attrs = new HashMap<>(base.atributos()); var discrepancies = new HashMap<String, Valor>();
        discrepancies.put("nombres", attrs.remove("nombres")); discrepancies.put("primerApellido", attrs.get("primerApellido"));
        attrs.put("primerNombre", new Valor("CORREGIDO", null, Fuente.DECLARACION_CIUDADANA, "DECLARADO_NO_CONTRASTADO", Instant.now(), UUID.randomUUID()));
        attrs.put("primerApellido", new Valor("CORREGIDO", null, Fuente.DECLARACION_CIUDADANA, "DECLARADO_NO_CONTRASTADO", Instant.now(), UUID.randomUUID()));
        var legacy = new Persona(base.id(), base.rol(), base.runNumero(), base.runDv(), base.modulo11(), base.autenticada(), base.estadoConsulta(),
            base.consultadaEn(), base.consultaRef(), attrs, discrepancies);
        assertEquals(Map.of("primerNombre", "CORREGIDO", "primerApellido", "CORREGIDO"), vista(legacy).valoresDeclarados());
        // Retirables: no se exigen en la interfaz porque el servidor restituye el antecedente registral.
        assertTrue(vista(legacy).camposObligatorios().isEmpty(), vista(legacy).camposObligatorios().toString());
        assertThrows(IllegalArgumentException.class, () -> declarar(legacy, Map.of("segundoApellido", "OTRO"), clock));
        var cleared = declarar(legacy, Map.of("primerNombre", "", "primerApellido", ""), clock);
        assertEquals(Fuente.SRCEI, cleared.atributos().get("nombres").fuente());
        assertEquals(Fuente.SRCEI, cleared.atributos().get("primerApellido").fuente());
        assertTrue(cleared.discrepancias().isEmpty());
        assertTrue(vista(cleared).camposFaltantes().isEmpty());
    }

    @Test void apiPublicaNoOfreceIndicadorDeCorreccion() {
        assertTrue(Arrays.stream(IdentificacionCiudadana.class.getMethods()).filter(m -> m.getName().equals("declarar"))
            .allMatch(m -> m.getParameterCount() == 3), "declarar no debe aceptar un indicador de corrección registral");
        assertTrue(Arrays.stream(RecorridosJdbc.class.getMethods()).filter(m -> m.getName().equals("declarar"))
            .allMatch(m -> m.getParameterCount() == 4));
        assertArrayEquals(new String[]{"atributos"}, Arrays.stream(FormularioController.Declaracion.class.getRecordComponents()).map(c -> c.getName()).toArray());
    }
}
