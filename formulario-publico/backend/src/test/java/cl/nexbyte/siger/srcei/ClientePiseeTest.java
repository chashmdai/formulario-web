package cl.nexbyte.siger.srcei;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static cl.nexbyte.siger.srcei.ResultadoSrcei.Estado.*;

class ClientePiseeTest {
    HttpServer server;
    URI origin;
    final AtomicInteger requests = new AtomicInteger();
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor()); server.start();
        origin = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }
    @AfterEach void stop() { server.stop(0); }
    ClientePisee client(boolean enabled, long timeout) { return new ClientePisee(enabled, origin, "sintetico@prueba", Duration.ofMillis(timeout), 1, Clock.systemUTC()); }
    void reply(int status, String body) {
        server.createContext("/srcei/informacionPersonalBasico", e -> {
            requests.incrementAndGet();
            assertThat(e.getRequestHeaders().getFirst("X-PISEE-LocalAuthorization")).isEqualTo("sintetico@prueba");
            assertThat(new String(e.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).contains("<entradaSRCel>");
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            e.sendResponseHeaders(status, bytes.length); e.getResponseBody().write(bytes); e.close();
        });
    }
    @Test void consultaHttpLocalControladaMapeaRespuestaSinProveedor() {
        reply(200, XmlSrceiTest.response(XmlSrceiTest.PERSONA));
        try (var c = client(true, 2000)) { assertThat(c.consultar(XmlSrceiTest.RUN).estado()).isEqualTo(COMPLETO); }
        assertThat(requests.get()).isEqualTo(1);
    }
    @Test void deshabilitadoNoContactaLaRed() {
        reply(200, "<documento/>");
        try (var c = client(false, 2000)) { assertThat(c.consultar(XmlSrceiTest.RUN).estado()).isEqualTo(NO_HABILITADO); }
        assertThat(requests.get()).isZero();
    }
    @Test void autorizacionNoEsRespuestaVacia() {
        reply(401, "no autorizado");
        try (var c = client(true, 2000)) { assertThat(c.consultar(XmlSrceiTest.RUN).estado()).isEqualTo(AUTORIZACION); }
    }
    @Test void indisponibilidadNoSeReintenta() {
        reply(503, "indisponible");
        try (var c = client(true, 2000)) { assertThat(c.consultar(XmlSrceiTest.RUN).estado()).isEqualTo(INDISPONIBLE); }
        assertThat(requests.get()).isEqualTo(1);
    }
    @Test void redireccionesNoSeSiguen() {
        server.createContext("/srcei/informacionPersonalBasico", e -> {
            requests.incrementAndGet(); e.getResponseHeaders().add("Location", origin + "/otro"); e.sendResponseHeaders(302, -1); e.close();
        });
        server.createContext("/otro", e -> { requests.incrementAndGet(); e.sendResponseHeaders(200, -1); e.close(); });
        try (var c = client(true, 2000)) { assertThat(c.consultar(XmlSrceiTest.RUN).estado()).isEqualTo(INDISPONIBLE); }
        assertThat(requests.get()).isEqualTo(1);
    }
    @Test void limitaTamanoDuranteRecepcion() {
        reply(200, "x".repeat(XmlSrcei.MAX_BYTES + 1));
        try (var c = client(true, 2000)) { assertThat(c.consultar(XmlSrceiTest.RUN).estado()).isEqualTo(RESPUESTA_EXCESIVA); }
    }
    @Test void timeoutIncluyeCuerpoLentoYConcurrenciaEsAcotada() throws Exception {
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        server.createContext("/srcei/informacionPersonalBasico", e -> {
            e.sendResponseHeaders(200, 100); e.getResponseBody().write('<'); e.getResponseBody().flush(); started.countDown();
            try { release.await(2, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            e.close();
        });
        try (var c = client(true, 500)) {
            var pending = CompletableFuture.supplyAsync(() -> c.consultar(XmlSrceiTest.RUN));
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(c.consultar(XmlSrceiTest.RUN).estado()).isEqualTo(SATURADO);
            assertThat(pending.get(2, TimeUnit.SECONDS).estado()).isEqualTo(TIMEOUT);
        } finally { release.countDown(); }
    }
    @Test void noHabilitaQAProduccionNiCredencialesEnUrl() throws Exception {
        assertThatThrownBy(() -> new ClientePisee(true, URI.create("http://user:pass@localhost"), null, Duration.ofSeconds(1), 1, Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientePisee(true, URI.create("http://publico.example"), null, Duration.ofSeconds(1), 1, Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
        var env = new MockEnvironment().withProperty("PISEE_SRCEI_ENABLED", "true").withProperty("PISEE_ENVIRONMENT", "PROD").withProperty("PISEE_DEV_AUTHORIZED", "true");
        assertThatThrownBy(() -> new PiseeConfiguration().clientePisee(env)).isInstanceOf(IllegalStateException.class);
        env.withProperty("PISEE_ENVIRONMENT", "DEV"); env.setActiveProfiles("production");
        assertThatThrownBy(() -> new PiseeConfiguration().clientePisee(env)).isInstanceOf(IllegalStateException.class);
    }
}
