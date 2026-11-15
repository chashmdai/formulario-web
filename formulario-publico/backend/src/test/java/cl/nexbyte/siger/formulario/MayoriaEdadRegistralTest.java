package cl.nexbyte.siger.formulario;

import cl.nexbyte.siger.srcei.ResultadoSrcei;
import cl.nexbyte.siger.srcei.RunConsulta;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static cl.nexbyte.siger.formulario.IdentificacionCiudadana.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Correspondencia exigida para derivar por edad: RUN, dígito y fecha de nacimiento SRCEI de una misma consulta
 * (evidencia = referencia de consulta) y RUN contrastado igual al consultado. RUN sintéticos, sin proveedores reales.
 */
class MayoriaEdadRegistralTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC);
    private final Instant at = clock.instant();
    private static final String RUN = "12345678", DV = "5";

    private ResultadoSrcei.Atributo srcei(String value, UUID evidence) {
        return new ResultadoSrcei.Atributo(value, null, "SRCEI", "CONTRASTADO_EN_FUENTE", at, evidence);
    }
    /** Como XmlSrcei: todos los atributos comparten la evidencia de la respuesta. */
    private ResultadoSrcei respuesta(String run, String dv, String birth) {
        var ref = UUID.randomUUID(); var attrs = new HashMap<String, ResultadoSrcei.Atributo>();
        if (run != null) attrs.put("runNumero", srcei(run, ref));
        if (dv != null) attrs.put("runDv", srcei(dv, ref));
        attrs.put("nombres", srcei("NOMBRE SINTETICO", ref));
        if (birth != null) attrs.put("fechaNacimiento", srcei(birth, ref));
        return new ResultadoSrcei(ResultadoSrcei.Estado.COMPLETO, attrs, at, ref);
    }
    private Persona nna(ResultadoSrcei result) {
        return crear("presentador-nna", Rol.PRESENTADOR, new RunConsulta(RUN, DV), null, result, clock);
    }
    private Valor valor(String value, Fuente source, String level, String code, UUID evidence) {
        return new Valor(value, code, source, level, at, evidence);
    }
    private Valor registral(String value, UUID evidence) { return valor(value, Fuente.SRCEI, "CONTRASTADO_EN_FUENTE", null, evidence); }
    private Valor declarado(String value) { return valor(value, Fuente.DECLARACION_CIUDADANA, "DECLARADO_NO_CONTRASTADO", null, UUID.randomUUID()); }
    private Persona persona(UUID ref, Map<String, Valor> attrs, Map<String, Valor> discrepancies) {
        return new Persona("presentador-nna", Rol.PRESENTADOR, RUN, DV, true, false, ResultadoSrcei.Estado.COMPLETO, at, ref, attrs, discrepancies);
    }
    private Map<String, Valor> completo(UUID ref) {
        return new HashMap<>(Map.of("runNumero", registral(RUN, ref), "runDv", registral(DV, ref), "fechaNacimiento", registral("2000-01-01", ref)));
    }

    // Registros correctamente contrastados.
    @Test void respuestaContrastadaDeUnAdultoDeriva() {
        var person = nna(respuesta(RUN, DV, "2000-01-01"));
        assertEquals(Fuente.SRCEI, person.atributos().get("runNumero").fuente());
        assertEquals(Fuente.SRCEI, person.atributos().get("runDv").fuente());
        assertEquals(person.consultaRef(), person.atributos().get("runNumero").evidencia());
        assertTrue(mayorDeEdadRegistral(person, clock));
    }
    @Test void respuestaContrastadaDeUnMenorNoDeriva() {
        assertFalse(mayorDeEdadRegistral(nna(respuesta(RUN, DV, "2008-10-24")), clock));
    }
    @Test void registroPersistidoCorrectoDerivaAunqueLaFechaFigureEnDiscrepancias() {
        var ref = UUID.randomUUID(); var attrs = completo(ref); var date = attrs.remove("fechaNacimiento");
        assertTrue(mayorDeEdadRegistral(persona(ref, completo(ref), Map.of()), clock));
        assertTrue(mayorDeEdadRegistral(persona(ref, attrs, Map.of("fechaNacimiento", date)), clock));
    }
    @Test void dvConMayusculaDistintaSigueCorrespondiendo() {
        var ref = UUID.randomUUID(); var attrs = completo(ref); attrs.put("runDv", registral("K", ref));
        var person = new Persona("presentador-nna", Rol.PRESENTADOR, RUN, "k", true, false, ResultadoSrcei.Estado.COMPLETO, at, ref, attrs, Map.of());
        assertTrue(mayorDeEdadRegistral(person, clock));
    }
    @Test void reanudarConservaLaProcedenciaRegistralDelRun() {
        var previous = nna(respuesta(RUN, DV, "2000-01-01"));
        var resumed = reanudar(nna(null), previous);
        assertEquals(Fuente.SRCEI, resumed.atributos().get("runNumero").fuente());
        assertTrue(mayorDeEdadRegistral(resumed, clock));
        assertTrue(mayorDeEdadRegistral(declarar(previous, Map.of("segundoApellido", "DECLARADO"), clock), clock));
    }

    // Registros incompletos.
    @Test void registroHistoricoConRunSoloDeclaradoNoDeriva() {
        // Forma persistida antes de esta versión: RUN declarado, fecha SRCEI. No demuestra la correspondencia del RUN.
        var ref = UUID.randomUUID(); var attrs = completo(ref);
        attrs.put("runNumero", declarado(RUN)); attrs.put("runDv", declarado(DV));
        assertFalse(mayorDeEdadRegistral(persona(ref, attrs, Map.of()), clock));
    }
    @Test void faltaCualquieraDeLosTresAntecedentesNoDeriva() {
        for (var key : List.of("runNumero", "runDv", "fechaNacimiento")) {
            var ref = UUID.randomUUID(); var attrs = completo(ref); attrs.remove(key);
            assertFalse(mayorDeEdadRegistral(persona(ref, attrs, Map.of()), clock), key);
        }
        assertFalse(mayorDeEdadRegistral(nna(respuesta(null, null, "2000-01-01")), clock), "Respuesta sin RUN contrastado");
        assertFalse(mayorDeEdadRegistral(nna(respuesta(RUN, null, "2000-01-01")), clock), "Respuesta sin dígito contrastado");
    }
    @Test void sinReferenciaFechaDeConsultaOEstadoExitosoNoDeriva() {
        var ref = UUID.randomUUID(); var attrs = completo(ref);
        assertFalse(mayorDeEdadRegistral(new Persona("presentador-nna", Rol.PRESENTADOR, RUN, DV, true, false, ResultadoSrcei.Estado.COMPLETO, at, null, attrs, Map.of()), clock));
        assertFalse(mayorDeEdadRegistral(new Persona("presentador-nna", Rol.PRESENTADOR, RUN, DV, true, false, ResultadoSrcei.Estado.COMPLETO, null, ref, attrs, Map.of()), clock));
        assertFalse(mayorDeEdadRegistral(new Persona("presentador-nna", Rol.PRESENTADOR, RUN, null, true, false, ResultadoSrcei.Estado.COMPLETO, at, ref, attrs, Map.of()), clock));
        assertFalse(mayorDeEdadRegistral(new Persona("presentador-nna", Rol.PRESENTADOR, RUN, DV, true, false, ResultadoSrcei.Estado.TIMEOUT, at, ref, attrs, Map.of()), clock));
    }
    @Test void procedenciaNoContrastadaOCodificadaNoDeriva() {
        for (var key : List.of("runNumero", "runDv", "fechaNacimiento")) {
            var ref = UUID.randomUUID(); var attrs = completo(ref); var original = attrs.get(key);
            attrs.put(key, valor(original.valor(), Fuente.SRCEI, "DECLARADO_NO_CONTRASTADO", null, ref));
            assertFalse(mayorDeEdadRegistral(persona(ref, attrs, Map.of()), clock), key + " nivel");
            attrs.put(key, valor(original.valor(), Fuente.SRCEI, "CONTRASTADO_EN_FUENTE", "TRUNCADA", ref));
            assertFalse(mayorDeEdadRegistral(persona(ref, attrs, Map.of()), clock), key + " código");
            attrs.put(key, valor(original.valor(), Fuente.CLAVE_UNICA_USERINFO, "IDENTIDAD_AUTENTICADA", null, ref));
            assertFalse(mayorDeEdadRegistral(persona(ref, attrs, Map.of()), clock), key + " otra fuente");
        }
    }

    // RUN discrepantes.
    @Test void runODigitoSrceiDistintoAlConsultadoNoDeriva() {
        var discrepantNumber = nna(respuesta("87654321", DV, "2000-01-01"));
        assertEquals("87654321", discrepantNumber.discrepancias().get("runNumero").valor());
        assertFalse(mayorDeEdadRegistral(discrepantNumber, clock));
        assertFalse(mayorDeEdadRegistral(nna(respuesta(RUN, "K", "2000-01-01")), clock));
        for (var key : List.of("runNumero", "runDv")) {
            var ref = UUID.randomUUID(); var attrs = completo(ref);
            attrs.put(key, registral(key.equals("runNumero") ? "87654321" : "K", ref));
            assertFalse(mayorDeEdadRegistral(persona(ref, attrs, Map.of()), clock), key);
        }
    }
    @Test void copiaRegistralContradictoriaEnDiscrepanciasNoDeriva() {
        for (var key : List.of("runNumero", "runDv", "fechaNacimiento")) {
            var ref = UUID.randomUUID();
            var other = registral(switch (key) { case "runNumero" -> "87654321"; case "runDv" -> "K"; default -> "2001-01-01"; }, ref);
            assertFalse(mayorDeEdadRegistral(persona(ref, completo(ref), Map.of(key, other)), clock), key);
        }
    }

    // Evidencias diferentes.
    @Test void antecedenteDeOtraEvidenciaNoDeriva() {
        for (var key : List.of("runNumero", "runDv", "fechaNacimiento")) {
            var ref = UUID.randomUUID(); var attrs = completo(ref);
            attrs.put(key, registral(attrs.get(key).valor(), UUID.randomUUID()));
            assertFalse(mayorDeEdadRegistral(persona(ref, attrs, Map.of()), clock), key);
            // También cuando la copia ajena está solo en discrepancias.
            var displaced = completo(ref);
            assertFalse(mayorDeEdadRegistral(persona(ref, displaced, Map.of(key, registral(displaced.get(key).valor(), UUID.randomUUID()))), clock), key + " en discrepancias");
        }
    }
    @Test void antecedentesConsistentesEntreSiPeroDeOtraConsultaNoDerivan() {
        var evidence = UUID.randomUUID();
        assertFalse(mayorDeEdadRegistral(persona(UUID.randomUUID(), completo(evidence), Map.of()), clock));
    }
    @Test void respuestaConAtributosDeEvidenciasDistintasNoDeriva() {
        var ref = UUID.randomUUID();
        var result = new ResultadoSrcei(ResultadoSrcei.Estado.COMPLETO, Map.of("runNumero", srcei(RUN, ref), "runDv", srcei(DV, UUID.randomUUID()),
            "fechaNacimiento", srcei("2000-01-01", ref)), at, ref);
        assertFalse(mayorDeEdadRegistral(nna(result), clock));
    }

    @Test void respuestaParcialContrastadaTambienDeriva() {
        var full = respuesta(RUN, DV, "2000-01-01");
        assertTrue(mayorDeEdadRegistral(nna(new ResultadoSrcei(ResultadoSrcei.Estado.PARCIAL, full.atributos(), at, full.evidencia())), clock));
    }
    @Test void copiasDelDigitoQueSoloDifierenEnMayusculaCorresponden() {
        var ref = UUID.randomUUID(); var attrs = completo(ref); attrs.put("runDv", registral("K", ref));
        var person = new Persona("presentador-nna", Rol.PRESENTADOR, RUN, "K", true, false, ResultadoSrcei.Estado.COMPLETO, at, ref, attrs, Map.of("runDv", registral("k", ref)));
        assertTrue(mayorDeEdadRegistral(person, clock));
    }
    @Test void reanudarNoReutilizaUnRegistroAmbiguo() {
        // Antes de fusionar, el registro ambiguo no deriva; la fusión no debe borrar la copia en conflicto y volverlo derivable.
        for (var key : List.of("runNumero", "runDv", "fechaNacimiento")) {
            var ref = UUID.randomUUID(); var attrs = completo(ref);
            for (var conflict : List.of(registral(switch (key) { case "runNumero" -> "87654321"; case "runDv" -> "K"; default -> "2001-01-01"; }, ref),
                    registral(attrs.get(key).valor(), UUID.randomUUID()))) {
                var previous = persona(ref, attrs, Map.of(key, conflict));
                assertFalse(mayorDeEdadRegistral(previous, clock), key);
                assertFalse(registralesConsistentes(previous), key);
                assertThrows(IllegalArgumentException.class, () -> reanudar(nna(null), previous), key);
            }
        }
        assertTrue(registralesConsistentes(nna(respuesta(RUN, DV, "2000-01-01"))));
    }
    @Test void reanudarUnRegistroHistoricoConRunDeclaradoNoLoVuelveDerivable() {
        var ref = UUID.randomUUID(); var attrs = completo(ref);
        attrs.put("runNumero", declarado(RUN)); attrs.put("runDv", declarado(DV));
        var resumed = reanudar(nna(null), persona(ref, attrs, Map.of()));
        assertEquals(Fuente.DECLARACION_CIUDADANA, resumed.atributos().get("runNumero").fuente());
        assertFalse(mayorDeEdadRegistral(resumed, clock));
    }

    // Alcance: no altera otros flujos.
    @Test void elRunSoloAdoptaProcedenciaSrceiParaElPresentadorNnaConFechaRegistral() {
        var adult = crear("presentador-adulto", Rol.PRESENTADOR, new RunConsulta(RUN, DV), null, respuesta(RUN, DV, "2000-01-01"), clock);
        assertEquals(Fuente.DECLARACION_CIUDADANA, adult.atributos().get("runNumero").fuente());
        assertEquals(Fuente.DECLARACION_CIUDADANA, reanudar(crear("presentador-adulto", Rol.PRESENTADOR, new RunConsulta(RUN, DV), null, null, clock), adult)
            .atributos().get("runNumero").fuente());
        var withoutDate = nna(respuesta(RUN, DV, null));
        assertEquals(Fuente.DECLARACION_CIUDADANA, withoutDate.atributos().get("runNumero").fuente());
        assertEquals(Fuente.DECLARACION_CIUDADANA, withoutDate.atributos().get("runDv").fuente());
        assertTrue(withoutDate.discrepancias().isEmpty());
    }
}
