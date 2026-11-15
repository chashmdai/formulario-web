package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.multimedia.*;
import cl.nexbyte.siger.srcei.*;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static cl.nexbyte.siger.formulario.IdentificacionCiudadana.*;
import static cl.nexbyte.siger.robustez.NnaSrceiFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Contrato y recepción reales con proveedor/inspección controlados, sin red ni datos reales. */
class NnaSrceiContratoTest {
    @TempDir Path temp;

    @ParameterizedTest @ValueSource(strings = {"1.0", "1.1"})
    void fixtureFirmadoConservaProcedenciaRegistralSinAutenticar(String version) throws Exception {
        var delivery = leer(version);
        var trust = confianza("public-nna-srcei-test.json");
        delivery.validar(trust, MAPPER);
        assertEquals(MAPPER.valueToTree(contenido(version)), MAPPER.valueToTree(delivery.comunicacion()));
        assertEquals(EntregaFormulario.procedencia(delivery.comunicacion(), MAPPER), delivery.huellaProcedencia());
        var proof = delivery.custodia().verificar(trust, MAPPER);
        assertEquals(delivery.huellaProcedencia(), proof.huellaProcedencia());
        comprobarPersona(delivery.comunicacion().personas().getFirst());
        assertEquals("1.1".equals(version) ? 1 : 0, delivery.comunicacion().adjuntos().size());
    }

    static void comprobarPersona(Persona person) {
        assertEquals("presentador-nna", person.id());
        assertEquals(Rol.PRESENTADOR, person.rol());
        assertFalse(person.autenticada());
        assertTrue(person.modulo11());
        assertEquals(RUN, new RunConsulta(person.runNumero(), person.runDv()));
        assertEquals(EVIDENCE, person.consultaRef());
        for (var key : List.of("runNumero", "runDv", "fechaNacimiento")) {
            var attribute = person.atributos().get(key);
            assertEquals(Fuente.SRCEI, attribute.fuente(), key);
            assertEquals("CONTRASTADO_EN_FUENTE", attribute.nivel(), key);
            assertEquals(person.consultaRef(), attribute.evidencia(), key);
            assertEquals(person.consultadaEn(), attribute.observadoEn(), key);
        }
        assertTrue(person.discrepancias().isEmpty());
        assertFalse(mayorDeEdadRegistral(person, CLOCK));
    }

    @ParameterizedTest @ValueSource(strings = {"1.0", "1.1"})
    void cambiarFuenteOEvidenciaInvalidaHuellaYFirmaOriginal(String version) throws Exception {
        var original = leer(version);
        var trust = confianza("public-nna-srcei-test.json");
        for (var change : List.of("fuente", "evidencia")) {
            var tree = MAPPER.valueToTree(original);
            var value = (tools.jackson.databind.node.ObjectNode) tree.at("/comunicacion/personas/0/atributos/runNumero");
            value.put(change, "fuente".equals(change) ? "DECLARACION_CIUDADANA" : JOURNEY.toString());
            var altered = MAPPER.treeToValue(tree, EntregaFormulario.class);
            assertNotEquals(original.huellaProcedencia(), EntregaFormulario.procedencia(altered.comunicacion(), MAPPER));
            assertThrows(IllegalArgumentException.class, () -> altered.validar(trust, MAPPER));
            // Recalcular ambas huellas no permite reutilizar el comprobante firmado original.
            var rehashed = new EntregaFormulario(altered.comunicacion(),
                CanonFormulario.huella("CONTENIDO", altered.comunicacion(), MAPPER),
                EntregaFormulario.procedencia(altered.comunicacion(), MAPPER), original.custodia());
            assertThrows(IllegalArgumentException.class, () -> rehashed.validar(trust, MAPPER));
        }
        assertThrows(IllegalArgumentException.class, () -> original.validar(Map.of(), MAPPER));
    }

    @Test void receptor10ProduceLaMismaProcedenciaQueElFixture() throws Exception {
        verificarRecepcion("1.0");
    }

    @Test void receptor11ProduceLaMismaProcedenciaQueElFixture() throws Exception {
        verificarRecepcion("1.1");
    }

    private void verificarRecepcion(String version) throws Exception {
        var queries = new AtomicInteger();
        var session = "a".repeat(64);
        var signer = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        try (var db = TestDatabase.open(temp);
                var store = "1.1".equals(version) ? new AlmacenPrivado(temp.resolve("archivos"), () -> 1L << 30) : null) {
            var journeys = new RecorridosJdbc(db, MAPPER, run -> {
                assertEquals(RUN, run);
                queries.incrementAndGet();
                return resultado();
            }, run -> true, CLOCK, new byte[32], "ADULTO_SINTETICO_1", NOTICE);
            var control = new ControlesOperacion(db);
            control.cambiar(control.estado().revision(), true, true, "NORMAL", "PRUEBA_LOCAL");
            var journey = journeys.iniciar(Tipo.NNA, false, NOTICE, session, "127.0.0.1").id();
            journeys.identificar(journey, "presentador-nna", Rol.PRESENTADOR,
                RUN.numero(), RUN.dv(), session, "127.0.0.1", null);
            List<UUID> attachments = List.of();
            if ("1.1".equals(version)) {
                var policy = new PoliticaMultimedia(12, 1024, 4, 512, 512, 512, 512, 16384, 0, 60000);
                try (var media = new CargasMultimedia(db, journeys, store, policy, path -> true,
                    (path, format) -> new CargasMultimedia.Inspeccion(format.tipo(), "DESCARGA", null), CLOCK, MAPPER)) {
                    var bytes = original();
                    var upload = media.iniciar(journey, session,
                        new CargasMultimedia.Inicio("sintetico.txt", bytes.length, hash(bytes)));
                    for (int offset = 0; offset < bytes.length; offset += 4) {
                        var block = Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + 4));
                        media.bloque(journey, upload.id(), session, offset / 4, hash(block), block.length,
                            new ByteArrayInputStream(block));
                    }
                    media.finalizar(journey, upload.id(), session);
                    var deadline = System.nanoTime() + 5_000_000_000L;
                    while ("ANALIZANDO".equals(media.estado(journey, upload.id(), session).estado())
                            && System.nanoTime() < deadline) Thread.sleep(5);
                    assertEquals("VALIDADO", media.estado(journey, upload.id(), session).estado());
                    media.confirmar(journey, session,
                        new CargasMultimedia.Manifest(List.of(upload.id()), media.lote(journey, session).revision()));
                    attachments = List.of(upload.id());
                }
            }
            var files = new AdjuntosFormulario(db, journeys, bytes -> true, store);
            var reception = new RecepcionFormularioJdbc(db, journeys, files, MAPPER,
                new ValidacionFormulario(new TerritorioFormulario(MAPPER)), PUBLIC_KID, signer.getPrivate(),
                Map.of(PUBLIC_KID, signer.getPublic()), confianza("institution-public-nna-srcei-test.json"), CLOCK, true);
            reception.recibir(UUID.randomUUID(), journey, session, datos(attachments));
            if ("1.1".equals(version)) assertTrue(reception.reclamar(1).isEmpty(), "1.1 exige opt-in");
            var delivery = reception.reclamar(1, Set.of(version)).getFirst().entrega();
            delivery.validar(Map.of(PUBLIC_KID, signer.getPublic()), MAPPER);
            assertEquals(version, delivery.comunicacion().version());
            comprobarPersona(delivery.comunicacion().personas().getFirst());
            assertEquals(leer(version).comunicacion().personas(), delivery.comunicacion().personas());
            assertEquals(leer(version).huellaProcedencia(), delivery.huellaProcedencia());
            assertEquals(1, queries.get());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"adulto", "nna-sin-fecha"})
    void fueraDelPresentadorNnaConFechaElRunSigueDeclarado(String scenario) {
        var result = resultado();
        if ("nna-sin-fecha".equals(scenario)) {
            var attrs = new HashMap<>(result.atributos());
            attrs.remove("fechaNacimiento");
            result = new ResultadoSrcei(result.estado(), attrs, AT, EVIDENCE);
        }
        var person = crear("adulto".equals(scenario) ? "presentador-adulto" : "presentador-nna",
            Rol.PRESENTADOR, RUN, null, result, CLOCK);
        assertFalse(person.autenticada());
        assertEquals(Fuente.DECLARACION_CIUDADANA, person.atributos().get("runNumero").fuente());
        assertEquals(Fuente.DECLARACION_CIUDADANA, person.atributos().get("runDv").fuente());
    }
}
