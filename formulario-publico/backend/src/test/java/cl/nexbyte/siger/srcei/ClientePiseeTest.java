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
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints={401,403})
    void autorizacionNoEsRespuestaVacia(int status) {
        reply(status, "no autorizado");
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
    @Test void rechazaOrigenesInsegurosAmbientesDesconocidosYCredencialAusente() throws Exception {
        assertThatThrownBy(() -> new ClientePisee(true, URI.create("http://user:pass@localhost"), null, Duration.ofSeconds(1), 1, Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientePisee(true, URI.create("http://publico.example"), null, Duration.ofSeconds(1), 1, Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
        var env = new MockEnvironment().withProperty("PISEE_SRCEI_ENABLED", "true").withProperty("PISEE_ENVIRONMENT", "PROD").withProperty("PISEE_DEV_AUTHORIZED", "true");
        assertThatThrownBy(() -> new PiseeConfiguration().clientePisee(env)).isInstanceOf(IllegalArgumentException.class);
        env.withProperty("PISEE_ENVIRONMENT", "PRODUCTION"); env.setActiveProfiles("production");
        assertThatThrownBy(() -> new PiseeConfiguration().clientePisee(env)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Autorización");
    }
    @Test void etiquetaErroneaDevuelve200VacioPeroNoExitoFuncional() throws Exception {
        server.createContext(XmlSrcei.RUTA, e -> {
            String request = new String(e.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            boolean correct = request.contains("<entradaSRCel>") && !request.contains("entradaSRCeI");
            String xml = correct ? XmlSrceiTest.response(XmlSrceiTest.PERSONA)
                : "<documento><datosPersona/><glosa>No existe informacion disponible.</glosa></documento>";
            byte[] bytes = xml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            e.sendResponseHeaders(200, bytes.length); e.getResponseBody().write(bytes); e.close();
        });
        byte[] literal = XmlSrcei.solicitud(XmlSrceiTest.RUN).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(new String(literal, java.nio.charset.StandardCharsets.UTF_8)).contains("<entradaSRCel>");
        assertThat("entradaSRCeI".codePointAt("entradaSRCeI".length()-1)).isEqualTo(0x49);
        try (var http = java.net.http.HttpClient.newHttpClient(); var c = client(true, 2000)) {
            var wrong = java.net.http.HttpRequest.newBuilder(origin.resolve(XmlSrcei.RUTA))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(XmlSrcei.solicitud(XmlSrceiTest.RUN).replace("entradaSRCel","entradaSRCeI"))).build();
            var response = http.send(wrong, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(XmlSrcei.respuesta(response.body(),XmlSrceiTest.RUN,java.time.Instant.now(),java.util.UUID.randomUUID()).estado()).isEqualTo(VACIO);
            assertThat(c.consultar(XmlSrceiTest.RUN).estado()).isEqualTo(COMPLETO);
        }
    }
    @Test void tlsInvalidoSeRechazaYNoSeConfundeConRespuestaVacia() {
        try (var c = new ClientePisee(true, URI.create(origin.toString().replace("http:","https:")), null, Duration.ofMillis(300), 1, Clock.systemUTC())) {
            // El listener HTTP no negocia TLS; nunca se degrada a HTTP.
            assertThat(c.consultar(XmlSrceiTest.RUN).estado()).isIn(TIMEOUT,RED);
        }
    }
    @Test void falloDeHandshakeTlsTieneDiagnosticoOperativoSinDesactivarLaValidacion() throws Exception {
        var https = com.sun.net.httpserver.HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);
        https.setHttpsConfigurator(new com.sun.net.httpserver.HttpsConfigurator(javax.net.ssl.SSLContext.getDefault()));
        https.start();
        try (var c = new ClientePisee(true,URI.create("https://127.0.0.1:"+https.getAddress().getPort()),null,Duration.ofSeconds(2),1,Clock.systemUTC())) {
            var result = c.consultar(XmlSrceiTest.RUN);
            assertThat(result.estado()).isEqualTo(RED);
            assertThat(result.motivo()).isEqualTo(ResultadoSrcei.Motivo.TLS);
            assertThat(c.contadores()).containsEntry(RED,1L);
        } finally { https.stop(0); }
    }
}
