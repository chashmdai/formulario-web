package cl.nexbyte.siger.config;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SaludHttpTest {
    @TempDir static Path temp;
    static HttpServer http;
    static HttpsServer https;
    static Path ca;
    static final AtomicInteger redirecciones = new AtomicInteger();
    static final CountDownLatch desbloquear = new CountDownLatch(1);

    @BeforeAll static void preparar() throws Exception {
        var almacen = temp.resolve("servidor.p12");
        ca = temp.resolve("ca.crt");
        keytool("-genkeypair", "-alias", "sonda-test", "-keyalg", "RSA", "-keysize", "2048",
            "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-validity", "1",
            "-storetype", "PKCS12", "-keystore", almacen.toString(), "-storepass", "synthetic-test", "-noprompt");
        keytool("-exportcert", "-alias", "sonda-test", "-rfc", "-keystore", almacen.toString(),
            "-storepass", "synthetic-test", "-file", ca.toString());
        var keys = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(almacen)) { keys.load(input, "synthetic-test".toCharArray()); }
        var gestores = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        gestores.init(keys, "synthetic-test".toCharArray());
        var tls = SSLContext.getInstance("TLS");
        tls.init(gestores.getKeyManagers(), null, null);
        https = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        https.setHttpsConfigurator(new HttpsConfigurator(tls));
        https.createContext("/api/session", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        https.start();
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/api/session", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        http.createContext("/fallo", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        http.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/externo");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        http.createContext("/externo", exchange -> {
            redirecciones.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        http.createContext("/lento", exchange -> {
            try { desbloquear.await(10, TimeUnit.SECONDS); }
            catch (InterruptedException fallo) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        http.start();
    }

    private static void keytool(String... args) throws Exception {
        var comando = new java.util.ArrayList<String>();
        comando.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        comando.addAll(java.util.List.of(args));
        var proceso = new ProcessBuilder(comando).redirectErrorStream(true)
            .redirectOutput(temp.resolve("keytool.log").toFile()).start();
        assertThat(proceso.waitFor(20, TimeUnit.SECONDS)).isTrue();
        assertThat(proceso.exitValue()).isZero();
    }

    @AfterAll static void cerrar() {
        desbloquear.countDown();
        if (http != null) http.stop(0);
        if (https != null) https.stop(0);
    }

    private URI http(String path) { return URI.create("http://127.0.0.1:" + http.getAddress().getPort() + path); }
    private URI https(String host) {
        return URI.create("https://" + host + ":" + https.getAddress().getPort() + "/api/session");
    }

    @Test void estadoHttpDeterminaSaludSinLeerCuerpos() throws Exception {
        assertThat(SaludHttp.disponible(http("/api/session"), null)).isTrue();
        assertThat(SaludHttp.disponible(http("/fallo"), null)).isFalse();
    }

    @Test void noSigueRedirecciones() throws Exception {
        assertThat(SaludHttp.disponible(http("/redirect"), null)).isTrue();
        assertThat(redirecciones.get()).isZero();
    }

    @Test void httpsExigeCaYNombreCorrectos() throws Exception {
        assertThat(SaludHttp.disponible(https("localhost"), ca)).isTrue();
        assertThatThrownBy(() -> SaludHttp.disponible(https("localhost"), null))
            .isInstanceOf(SSLHandshakeException.class);
        assertThatThrownBy(() -> SaludHttp.disponible(https("127.0.0.1"), ca))
            .isInstanceOf(SSLHandshakeException.class);
    }

    @Test void caAusenteOVaciaNoAbreConfianza() throws Exception {
        assertThatThrownBy(() -> SaludHttp.disponible(https("localhost"), temp.resolve("ausente.crt")))
            .isInstanceOf(java.io.IOException.class);
        var vacia = Files.createFile(temp.resolve("vacia.crt"));
        assertThatThrownBy(() -> SaludHttp.disponible(https("localhost"), vacia)).isInstanceOf(Exception.class);
    }

    @Test void limitaLaEsperaDeRespuesta() {
        try {
            assertThatThrownBy(() -> SaludHttp.disponible(http("/lento"), null))
                .isInstanceOf(java.net.SocketTimeoutException.class);
        } finally { desbloquear.countDown(); }
    }

    @Test void ejecutableSoloJreDevuelveCodigosParaCompose() throws Exception {
        assertThat(ejecutar(http("/api/session").toString())).isZero();
        assertThat(ejecutar(http("/fallo").toString())).isEqualTo(1);
        assertThat(ejecutar(https("localhost").toString(), ca.toString())).isZero();
        assertThat(ejecutar(https("127.0.0.1").toString(), ca.toString())).isEqualTo(1);
    }

    private int ejecutar(String... args) throws Exception {
        var comando = new java.util.ArrayList<String>();
        comando.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        comando.addAll(java.util.List.of("-Xmx32m", "-XX:ActiveProcessorCount=1", "-cp",
            Path.of(SaludHttp.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(),
            SaludHttp.class.getName()));
        comando.addAll(java.util.List.of(args));
        var proceso = new ProcessBuilder(comando).redirectErrorStream(true)
            .redirectOutput(temp.resolve("sonda.log").toFile()).start();
        assertThat(proceso.waitFor(10, TimeUnit.SECONDS)).isTrue();
        return proceso.exitValue();
    }
}
