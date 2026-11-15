package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.srcei.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import static cl.nexbyte.siger.formulario.IdentificacionCiudadana.*;

/** Generador sintético aditivo. Las claves privadas solo existen en memoria y nunca se serializan. */
public final class NnaSrceiFixtures {
    public static final JsonMapper MAPPER = JsonMapper.builder().build();
    public static final Instant AT = Instant.parse("2026-11-15T12:00:00Z");
    public static final Clock CLOCK = Clock.fixed(AT, ZoneOffset.UTC);
    public static final UUID EVIDENCE = UUID.fromString("66666666-6666-4666-8666-666666666666");
    public static final UUID JOURNEY = UUID.fromString("66666666-6666-4666-8666-666666666667");
    public static final UUID ATTACHMENT = UUID.fromString("66666666-6666-4666-8666-666666666668");
    public static final String PUBLIC_KID = "nna-srcei-public-test";
    public static final String INSTITUTION_KID = "nna-srcei-institution-test";
    public static final String NOTICE = "NNA_SRCEI_SINTETICO_1";
    public static final RunConsulta RUN = new RunConsulta("99999999", "9");
    public static final Path FIXTURES = Path.of("../../contratos/fixtures");

    private NnaSrceiFixtures() {}

    public static ResultadoSrcei resultado() {
        var attrs = new TreeMap<String, ResultadoSrcei.Atributo>();
        Map.of("runNumero", RUN.numero(), "runDv", RUN.dv(), "fechaNacimiento", "2012-03-04",
            "nombres", "NOMBRE SINTETICO", "primerApellido", "APELLIDO SINTETICO")
            .forEach((key, value) -> attrs.put(key, new ResultadoSrcei.Atributo(
                value, null, "SRCEI", "CONTRASTADO_EN_FUENTE", AT, EVIDENCE)));
        return new ResultadoSrcei(ResultadoSrcei.Estado.PARCIAL, attrs, AT, EVIDENCE);
    }

    public static Persona persona() {
        return crear("presentador-nna", Rol.PRESENTADOR, RUN, null, resultado(), CLOCK);
    }

    public static byte[] original() {
        return "Original exclusivamente sintetico NNA SRCeI.\n".getBytes(StandardCharsets.UTF_8);
    }

    public static String hash(byte[] bytes) throws GeneralSecurityException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    public static DatosFormulario datos(List<UUID> attachments) {
        return new DatosFormulario(Map.of("nna-region", "13", "nna-primary-method", "unsafe",
            "nna-story", "Relato exclusivamente sintético para el contrato NNA SRCeI."),
            List.of(), List.of(), attachments);
    }

    public static EntregaFormulario.Contenido contenido(String version) throws GeneralSecurityException {
        var attachments = "1.1".equals(version)
            ? List.of(new AdjuntosFormulario.Descriptor(ATTACHMENT, "sintetico.txt", original().length,
                hash(original()), "text/plain", "VALIDADO"))
            : List.<AdjuntosFormulario.Descriptor>of();
        return new EntregaFormulario.Contenido(version,
            UUID.fromString("66666666-6666-4666-8666-6666666666" + ("1.0".equals(version) ? "70" : "71")),
            JOURNEY, Tipo.NNA, AT, NOTICE, false,
            datos(attachments.stream().map(AdjuntosFormulario.Descriptor::id).toList()), List.of(persona()), attachments);
    }

    public static EntregaFormulario firmar(EntregaFormulario.Contenido content, PrivateKey key) {
        var hash = CanonFormulario.huella("CONTENIDO", content, MAPPER);
        var provenance = EntregaFormulario.procedencia(content, MAPPER);
        var proof = ComprobanteDigital.firmar(new ComprobanteDigital.Contenido("1.0", content.id(),
            content.recibidaEn(), "CUSTODIA_PUBLICA", hash, provenance, "FORMULARIO_PUBLICO_DDN",
            content.avisoVersion()), PUBLIC_KID, key, MAPPER);
        return new EntregaFormulario(content, hash, provenance, proof);
    }

    public static EntregaFormulario leer(String version) throws java.io.IOException {
        return MAPPER.readValue(Files.readString(FIXTURES.resolve("formulario-nna-srcei-v" + version + ".json")),
            EntregaFormulario.class);
    }

    public static Map<String, PublicKey> confianza(String file) throws java.io.IOException {
        var node = MAPPER.readTree(Files.readString(FIXTURES.resolve(file)));
        var trust = new HashMap<String, PublicKey>();
        node.properties().forEach(e -> trust.put(e.getKey(), ComprobanteDigital.publicPem(e.getValue().asString())));
        return Map.copyOf(trust);
    }

    private static String pem(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(key.getEncoded())
            + "\n-----END PUBLIC KEY-----\n";
    }

    private static void escribir(Path output, String name, Object value) throws java.io.IOException {
        Files.writeString(output.resolve(name), MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n",
            StandardOpenOption.CREATE_NEW);
    }

    /** Argumento: directorio NUEVO de salida; nunca sobrescribe los fixtures publicados. */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Indicar un directorio nuevo de salida");
        var output = Files.createDirectory(Path.of(args[0]));
        var publicPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var institutionPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        escribir(output, "public-nna-srcei-test.json", Map.of(PUBLIC_KID, pem(publicPair.getPublic())));
        escribir(output, "institution-public-nna-srcei-test.json", Map.of(INSTITUTION_KID, pem(institutionPair.getPublic())));
        for (var version : List.of("1.0", "1.1")) {
            var delivery = firmar(contenido(version), publicPair.getPrivate());
            delivery.validar(Map.of(PUBLIC_KID, publicPair.getPublic()), MAPPER);
            escribir(output, "formulario-nna-srcei-v" + version + ".json", delivery);
            var receipt = new ComprobanteInstitucional.Contenido("1.1", "SIGER_DDN", "COMUNICACION_REGISTRADA",
                delivery.comunicacion().id(), UUID.fromString("66666666-6666-4666-8666-6666666666"
                    + ("1.0".equals(version) ? "80" : "81")),
                1, "COM-1", AT, "FORMULARIO_WEB_NNA", delivery.sha256(), delivery.huellaProcedencia());
            escribir(output, "registro-nna-srcei-v" + version + ".json", new RegistroInstitucional(receipt,
                ComprobanteInstitucional.firmar(receipt, INSTITUTION_KID, institutionPair.getPrivate(), MAPPER)));
        }
    }
}
