package cl.nexbyte.siger.srcei;

import java.nio.file.*;
import java.security.MessageDigest;
import java.security.cert.*;
import java.util.*;
import java.util.function.Predicate;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;

/** Autorización de finalidad y campaña; las credenciales de catálogo no autorizan consultas ciudadanas. */
public final class PoliticaPisee {
    private PoliticaPisee() {}
    public static String validar(Environment env) throws java.io.IOException {
        if (!env.getProperty("PISEE_SRCEI_ENABLED", Boolean.class, false)) return "DISABLED";
        String stage = env.getRequiredProperty("PISEE_ENVIRONMENT");
        if (!Set.of("DEV", "CONTROLLED_TEST", "PRODUCTION").contains(stage))
            throw new IllegalArgumentException("Ambiente PISEE desconocido");
        String gate = switch (stage) {
            case "DEV" -> "PISEE_DEV_AUTHORIZED";
            case "CONTROLLED_TEST" -> "PISEE_CONTROLLED_TEST_AUTHORIZED";
            default -> "PISEE_PRODUCTION_AUTHORIZED";
        };
        if (!env.getProperty(gate, Boolean.class, false)) throw new IllegalStateException("Autorización PISEE de ambiente requerida");
        if ("PRODUCTION".equals(stage)) {
            if (!env.getProperty("PISEE_LEGAL_BASIS_APPROVED", Boolean.class, false)
                || !"REGISTRO DE USUARIOS".equals(env.getProperty("PISEE_PURPOSE")))
                throw new IllegalStateException("Base legal y finalidad PISEE requeridas");
            Path cert = Path.of(env.getRequiredProperty("PISEE_X509_CERTIFICATE_PATH"));
            if (!Files.isRegularFile(cert) || Files.size(cert) > 16384) throw new IllegalStateException("Certificado institucional requerido");
            try (var input = Files.newInputStream(cert)) {
                var x509 = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
                x509.checkValidity();
                String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(x509.getEncoded()));
                if (!fingerprint.equalsIgnoreCase(env.getRequiredProperty("PISEE_X509_SHA256")))
                    throw new IllegalStateException("Certificado institucional no coincide");
            } catch (java.security.GeneralSecurityException e) {
                throw new IllegalStateException("Certificado institucional inválido");
            }
        }
        // Producción institucional admite RUN variables. Una integración privada puede
        // restringirse expresamente sin convertir la campaña en requisito productivo.
        if (campana(stage, env) && !Files.isRegularFile(Path.of(env.getRequiredProperty("PISEE_ALLOWED_RUNS_PATH"))))
            throw new IllegalStateException("Campaña acotada PISEE requerida");
        return stage;
    }
    public static Predicate<RunConsulta> autorizados(Environment env, JsonMapper mapper) throws java.io.IOException {
        String stage = validar(env);
        if (stage.equals("DISABLED")) return run -> false;
        if (!campana(stage, env)) return Objects::nonNull; // RunConsulta ya exige módulo 11.
        Path path = Path.of(env.getRequiredProperty("PISEE_ALLOWED_RUNS_PATH"));
        if (Files.size(path) > 4096) throw new IllegalArgumentException("Campaña PISEE excesiva");
        var node = mapper.readTree(Files.readString(path));
        var entries = node.isArray() ? node : mapper.createArrayNode().add(node);
        if (entries.size() > 10) throw new IllegalArgumentException("Campaña acotada PISEE requerida");
        var allowed = new HashSet<RunConsulta>();
        for (var entry : entries) {
            if (!entry.isObject() || entry.size() != 2 || !entry.has("numero") || !entry.has("dv"))
                throw new IllegalArgumentException("Autorización PISEE inválida");
            allowed.add(new RunConsulta(entry.path("numero").asString(), entry.path("dv").asString()));
        }
        return Set.copyOf(allowed)::contains;
    }
    private static boolean campana(String stage, Environment env) {
        return !"PRODUCTION".equals(stage) || env.getProperty("PISEE_RESTRICTED_CAMPAIGN", Boolean.class, false);
    }
}
