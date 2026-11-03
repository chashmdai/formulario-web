package cl.nexbyte.siger.srcei;

import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class PoliticaPiseeTest {
    @TempDir Path dir;
    MockEnvironment controlled() throws Exception {
        Path runs=dir.resolve("allowed.json");Files.writeString(runs,"[{\"numero\":\"1\",\"dv\":\"9\"}]");
        return new MockEnvironment().withProperty("PISEE_SRCEI_ENABLED","true")
            .withProperty("PISEE_ENVIRONMENT","CONTROLLED_TEST").withProperty("PISEE_CONTROLLED_TEST_AUTHORIZED","true")
            .withProperty("PISEE_ALLOWED_RUNS_PATH",runs.toString());
    }
    @Test void deshabilitadoNoLeeArchivosNiAutorizaConsultas() throws Exception {
        assertThat(PoliticaPisee.autorizados(new MockEnvironment(),new JsonMapper()).test(new RunConsulta("1","9"))).isFalse();
    }
    @Test void cadaAmbienteExigeSuPropiaAutorizacionYLista() throws Exception {
        for (String stage:java.util.List.of("DEV","CONTROLLED_TEST","PRODUCTION")) {
            var env=new MockEnvironment().withProperty("PISEE_SRCEI_ENABLED","true").withProperty("PISEE_ENVIRONMENT",stage);
            assertThatThrownBy(()->PoliticaPisee.validar(env)).isInstanceOf(IllegalStateException.class);
        }
        var env=controlled();var allowed=PoliticaPisee.autorizados(env,new JsonMapper());
        assertThat(allowed.test(new RunConsulta("1","9"))).isTrue();
        assertThat(allowed.test(new RunConsulta("2","7"))).isFalse();
        Files.writeString(dir.resolve("allowed.json"),"[]");
        assertThat(PoliticaPisee.autorizados(env,new JsonMapper()).test(new RunConsulta("1","9"))).isFalse();
    }
    @Test void produccionNoSeHabilitaPorCertificadoNiCredencialesSinFinalidadAprobada() throws Exception {
        var env=controlled().withProperty("PISEE_ENVIRONMENT","PRODUCTION").withProperty("PISEE_PRODUCTION_AUTHORIZED","true");
        assertThatThrownBy(()->PoliticaPisee.validar(env)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Base legal");
        env.withProperty("PISEE_LEGAL_BASIS_APPROVED","true").withProperty("PISEE_PURPOSE","REGISTRO DE USUARIOS");
        assertThatThrownBy(()->PoliticaPisee.validar(env)).isInstanceOf(IllegalStateException.class).hasMessageContaining("PISEE_X509_CERTIFICATE_PATH");
        // Certificado exclusivamente sintético, generado por el JDK, sin dependencia de claves institucionales.
        Path store=dir.resolve("test.p12"),cert=dir.resolve("test.cer");
        String keytool=Path.of(System.getProperty("java.home"),"bin","keytool").toString();
        for(var command:java.util.List.of(
            new String[]{keytool,"-genkeypair","-alias","test","-keyalg","RSA","-keysize","2048","-dname","CN=Synthetic","-validity","1","-keystore",store.toString(),"-storepass","synthetic-test-password","-noprompt"},
            new String[]{keytool,"-exportcert","-alias","test","-keystore",store.toString(),"-storepass","synthetic-test-password","-file",cert.toString()})) {
            var p=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            assertThat(p.waitFor()).isZero();
        }
        var fingerprint=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(cert)));
        env.withProperty("PISEE_X509_CERTIFICATE_PATH",cert.toString()).withProperty("PISEE_X509_SHA256",fingerprint);
        assertThat(PoliticaPisee.validar(env)).isEqualTo("PRODUCTION");
        env.withProperty("PISEE_X509_SHA256","0".repeat(64));
        assertThatThrownBy(()->PoliticaPisee.validar(env)).isInstanceOf(IllegalStateException.class).hasMessageContaining("no coincide");
    }
}
