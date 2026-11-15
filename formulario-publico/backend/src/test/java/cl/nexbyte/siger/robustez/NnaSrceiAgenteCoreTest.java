package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.custodia.ArchivoAgente;
import cl.nexbyte.siger.custodia.GarantiaCustodia;
import cl.nexbyte.siger.custodia.RespaldoIngresoVerificado;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static cl.nexbyte.siger.robustez.NnaSrceiFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Mismo núcleo que AgenteCustodiaNativo: SELECT sintéticos y restauración POSIX, sin emitir garantía. */
@EnabledOnOs(OS.LINUX)
class NnaSrceiAgenteCoreTest {
    @TempDir Path temp;

    @ParameterizedTest @ValueSource(strings = {"1.0", "1.1"})
    void validadorDelConsumidorAceptaDocumentoYAckConPersonas(String version) throws Exception {
        var delivery = leer(version);
        var ack = Files.readString(FIXTURES.resolve("registro-nna-srcei-v" + version + ".json"));
        var context = GarantiaCustodia.contexto(MAPPER.writeValueAsString(delivery), ack, "COMPLETO",
            confianza("public-nna-srcei-test.json"), confianza("institution-public-nna-srcei-test.json"), MAPPER);
        assertEquals(delivery.comunicacion().id(), context.id());
        assertEquals(delivery.sha256(), context.contenido());
        assertEquals(delivery.huellaProcedencia(), context.procedencia());
        assertThrows(IllegalArgumentException.class, () -> GarantiaCustodia.contexto(
            MAPPER.writeValueAsString(delivery), ack, "COMPLETO", confianza("public-nna-srcei-test.json"), Map.of(), MAPPER));
        var otherVersion = "1.0".equals(version) ? "1.1" : "1.0";
        var otherAck = Files.readString(FIXTURES.resolve("registro-nna-srcei-v" + otherVersion + ".json"));
        assertThrows(IllegalArgumentException.class, () -> GarantiaCustodia.contexto(
            MAPPER.writeValueAsString(delivery), otherAck, "COMPLETO",
            confianza("public-nna-srcei-test.json"), confianza("institution-public-nna-srcei-test.json"), MAPPER));
    }

    private record Scenario(JdbcTemplate db, Path source, Path backup, Path scratch, UUID id) {}

    private Scenario preparar(String version, boolean corrupt) throws Exception {
        var delivery = leer(version);
        var ack = MAPPER.readValue(Files.readString(FIXTURES.resolve("registro-nna-srcei-v" + version + ".json")),
            RegistroInstitucional.class);
        var record = ack.registro();
        delivery.validarRegistro(ack, confianza("institution-public-nna-srcei-test.json"), MAPPER);
        var body = MAPPER.valueToTree(delivery);
        if (corrupt) {
            ((tools.jackson.databind.node.ObjectNode) body.at("/comunicacion/personas/0/atributos/runNumero"))
                .put("fuente", "DECLARACION_CIUDADANA");
        }
        var metadata = new HashMap<String, Object>();
        metadata.put("body", MAPPER.writeValueAsString(body));
        metadata.put("receipt", MAPPER.writeValueAsString(ack));
        metadata.put("document_kind", "FORMULARIO_COMPLETO");
        metadata.put("comunicacion_id", record.comunicacionId().toString());
        metadata.put("numero", record.numeroFolio());
        metadata.put("canal", record.canalOrigen());
        metadata.put("content_hash", record.sha256());
        metadata.put("provenance_hash", record.huellaProcedencia());
        metadata.put("registrada_ms", record.registradaEn().toEpochMilli());
        metadata.put("folio_prefix", "COM");
        metadata.put("folio_width", 1);
        var source = temp.resolve("origen");
        var backup = temp.resolve("respaldo");
        var scratch = temp.resolve("scratch");
        for (var path : List.of(source, backup, scratch)) ArchivoAgente.directorio(path);
        var id = delivery.comunicacion().id();
        var documents = new ArrayList<Map<String, Object>>();
        if (!delivery.comunicacion().adjuntos().isEmpty()) {
            var directory = source.resolve("objects").resolve(id.toString());
            ArchivoAgente.directorio(source.resolve("objects"));
            ArchivoAgente.directorio(directory);
            for (var descriptor : delivery.comunicacion().adjuntos()) {
                ArchivoAgente.escribir(directory.resolve(descriptor.id() + ".bin"), original());
                var row = new HashMap<String, Object>();
                row.put("id", ATTACHMENT.toString());
                row.put("attachment_id", descriptor.id().toString());
                row.put("operation_id", id.toString());
                row.put("communication_id", record.comunicacionId().toString());
                row.put("origin", "INGRESO_CIUDADANO");
                row.put("name", descriptor.nombre());
                row.put("size_bytes", descriptor.tamanio());
                row.put("sha256", descriptor.sha256());
                row.put("media_type", descriptor.tipo());
                row.put("storage_key", id + "/" + descriptor.id());
                documents.add(row);
            }
        }
        var db = new JdbcTemplate() {
            @Override public List<Map<String, Object>> queryForList(String sql, Object... args) {
                assertTrue(sql.startsWith("SELECT "));
                if (sql.contains("communication_registry")) return List.of(metadata);
                if (sql.contains("institution_document")) return documents;
                throw new AssertionError("Consulta inesperada del agente");
            }
            @Override public int update(String sql, Object... args) {
                throw new AssertionError("El agente no debe escribir en la BD institucional");
            }
        };
        return new Scenario(db, source, backup, scratch, id);
    }

    @ParameterizedTest @ValueSource(strings = {"1.0", "1.1"})
    void consumidorAceptaFirmaProcedenciaAckYOriginalDelFixture(String version) throws Exception {
        var scenario = preparar(version, false);
        var result = RespaldoIngresoVerificado.ensayar(scenario.db(), scenario.source(), scenario.backup(),
            scenario.scratch(), scenario.id(), "COMPLETO", confianza("public-nna-srcei-test.json"),
            confianza("institution-public-nna-srcei-test.json"), MAPPER);
        assertEquals(leer(version).sha256(), result.contexto().contenido());
        assertEquals(leer(version).huellaProcedencia(), result.contexto().procedencia());
        var packet = scenario.backup().resolve("completo-" + scenario.id());
        var saved = MAPPER.readValue(Files.readString(packet.resolve("registro.json")),
            RespaldoIngresoVerificado.Paquete.class);
        var restored = MAPPER.readValue(saved.registro().get("body").toString(),
            cl.nexbyte.siger.formulario.EntregaFormulario.class);
        NnaSrceiContratoTest.comprobarPersona(restored.comunicacion().personas().getFirst());
        if ("1.1".equals(version)) assertArrayEquals(original(), Files.readAllBytes(
            packet.resolve("objects").resolve(scenario.id().toString()).resolve(ATTACHMENT + ".bin")));
        try (var paths = Files.list(scenario.scratch())) { assertEquals(0, paths.count()); }
    }

    @ParameterizedTest @ValueSource(strings = {"1.0", "1.1"})
    void consumidorRechazaProcedenciaAlteradaAntesDePublicarElPaquete(String version) throws Exception {
        var scenario = preparar(version, true);
        assertThrows(IllegalArgumentException.class, () -> RespaldoIngresoVerificado.ensayar(
            scenario.db(), scenario.source(), scenario.backup(), scenario.scratch(), scenario.id(), "COMPLETO",
            confianza("public-nna-srcei-test.json"), confianza("institution-public-nna-srcei-test.json"), MAPPER));
        try (var paths = Files.list(scenario.backup())) { assertEquals(0, paths.count()); }
    }
}
