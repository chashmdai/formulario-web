package cl.nexbyte.siger.multimedia;

import cl.nexbyte.siger.config.OrigenCliente;
import cl.nexbyte.siger.robustez.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

/** Tomcat real y sockets loopback: clientes detenidos, con goteo y cancelación, sin servicios externos. */
class TransferenciasHttpTest {
    private static final String BLOQUE = "/api/formulario/recorridos/00000000-0000-0000-0000-000000000001"
            + "/cargas/00000000-0000-0000-0000-000000000002/bloques/0";
    @TempDir Path temp;
    private Tomcat tomcat;
    private int port;
    private CuposTransferencia cupos;
    private LimitesCarga limites;
    private final AtomicInteger abiertos = new AtomicInteger(), cerrados = new AtomicInteger();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    private static void requiereSocketsLoopback() {
        try (var socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            assertTrue(socket.isBound());
        } catch (IOException | SecurityException denied) {
            Assumptions.abort("El entorno no permite sockets loopback: " + denied.getClass().getSimpleName());
        }
    }

    @BeforeEach void iniciar() throws Exception {
        requiereSocketsLoopback();
        var env = new MockEnvironment().withProperty("formulario.origen.cabecera", "X-Ddn-Client-Ip")
                .withProperty("formulario.origen.proxies-confiables", "127.0.0.1/32")
                .withProperty("formulario.transferencias.cargas", "2").withProperty("formulario.transferencias.descargas", "4")
            .withProperty("formulario.transferencias.descargas-por-origen", "1")
                .withProperty("formulario.transferencias.plazo-carga", "PT0.25S")
                .withProperty("formulario.transferencias.plazo-descarga", "PT0.25S")
                .withProperty("formulario.transferencias.plazo-siger", "PT10S");
        cupos = new CuposTransferencia(env, new OrigenCliente(env));
        limites = new LimitesCarga(env, cupos);
        var transferencias = new TransferenciaPrivada(cupos);
        tomcat = new Tomcat(); tomcat.setBaseDir(temp.toString()); tomcat.setPort(0);
        var connector = tomcat.getConnector();
        connector.setProperty("address", "127.0.0.1");
        connector.setProperty("maxSwallowSize", "8192");
        var context = tomcat.addContext("", temp.toString());
        filtro(context, "limites", limites);
        filtro(context, "bloques", new BloquesTransferencia(cupos));
        var servlet = Tomcat.addServlet(context, "transferencias", new HttpServlet() {
            @Override protected void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
                try {
                    if ("PUT".equals(req.getMethod())) {
                        var bytes = req.getInputStream().readAllBytes();
                        if ("error".equals(req.getHeader("X-Test"))) throw new IOException("Error sintético");
                        res.getOutputStream().write(bytes);
                        return;
                    }
                    boolean grande = req.getRequestURI().endsWith("/grande");
                    var source = recurso(grande ? 64L * 1024 * 1024 : 4, req.getHeader("X-Test"));
                    if (req.getRequestURI().startsWith("/integracion/"))
                        transferencias.enviarIntegracion(source, req, res);
                    else transferencias.enviarCiudadano(source, false, req, res);
                } catch (FalloTecnico error) {
                    res.setStatus(error.status); res.setHeader("Retry-After", "2");
                }
            }
        });
        servlet.setAsyncSupported(true); context.addServletMappingDecoded("/*", "transferencias");
        tomcat.start(); port = connector.getLocalPort();
    }
    private void filtro(org.apache.catalina.Context context, String name, Filter filter) {
        var def = new FilterDef(); def.setFilterName(name); def.setFilter(filter); def.setAsyncSupported("true");
        context.addFilterDef(def);
        var map = new FilterMap(); map.setFilterName(name); map.addURLPattern("/*");
        map.setDispatcher("REQUEST"); map.setDispatcher("ASYNC"); context.addFilterMap(map);
    }
    @AfterEach void detener() throws Exception { if (tomcat != null) { tomcat.stop(); tomcat.destroy(); } }

    private RecursoPrivado recurso(long size, String error) {
        return new RecursoPrivado("sintetico.txt", size, "a".repeat(64), "text/plain", () -> {
            if ("abrir".equals(error)) throw new IOException("Apertura sintética");
            abiertos.incrementAndGet();
            return new InputStream() {
                private long left = size;
                private boolean closed;
                @Override public int read() throws IOException {
                    var b = new byte[1]; return read(b, 0, 1) < 0 ? -1 : b[0];
                }
                @Override public int read(byte[] b, int off, int len) throws IOException {
                    if ("leer".equals(error)) throw new IOException("Lectura sintética");
                    if (left == 0) return -1;
                    int n = (int) Math.min(len, left); Arrays.fill(b, off, off + n, (byte) 'x'); left -= n; return n;
                }
                @Override public void close() { if (!closed) { closed = true; cerrados.incrementAndGet(); } }
            };
        });
    }
    private HttpResponse<byte[]> get(String path, String ip) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(4)).header("X-Ddn-Client-Ip", ip).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private MockHttpServletRequest origen(String ip) {
        var r = new MockHttpServletRequest(); r.setRemoteAddr("127.0.0.1"); r.addHeader("X-Ddn-Client-Ip", ip); return r;
    }
    private void esperar(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < end) Thread.sleep(10);
        assertTrue(condition.getAsBoolean(), "No liberó dentro del plazo de prueba");
    }
    private boolean cargaLibre(String ip) {
        try (var ignored = cupos.carga(origen(ip))) { return true; } catch (FalloTecnico full) { return false; }
    }
    private boolean descargaLibre(String ip) {
        try (var ignored = cupos.descarga(origen(ip))) { return true; } catch (FalloTecnico full) { return false; }
    }
    private Socket socket() throws IOException {
        var s = new Socket(); s.setReceiveBufferSize(1024); s.connect(new InetSocketAddress("127.0.0.1", port));
        s.setSoTimeout(4000); return s;
    }
    private void enviar(Socket s, String data) throws IOException {
        s.getOutputStream().write(data.getBytes(StandardCharsets.US_ASCII)); s.getOutputStream().flush();
    }
    private Socket cargaLenta(String ip) throws IOException {
        var s = socket();
        enviar(s, "PUT " + BLOQUE + " HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/octet-stream\r\n"
                + "X-Ddn-Client-Ip: " + ip + "\r\nContent-Length: 10\r\nConnection: close\r\n\r\nx");
        return s;
    }

    @Test void sigerDescargaConPoolCiudadanoAgotadoY429IncluyeRetryAfter() throws Exception {
        var permisos = new ArrayList<CuposTransferencia.Permiso>();
        try {
            for (int i = 1; i <= 4; i++) permisos.add(cupos.descarga(origen("192.0.2." + i)));
            var citizen = get("/ciudadano", "192.0.2.5");
            assertEquals(429, citizen.statusCode()); assertEquals("2", citizen.headers().firstValue("Retry-After").orElseThrow());
            var siger = get("/integracion/adjunto", "192.0.2.1");
            assertEquals(200, siger.statusCode()); assertEquals("xxxx", new String(siger.body(), StandardCharsets.US_ASCII));
        } finally { permisos.forEach(CuposTransferencia.Permiso::close); }
        assertEquals(200, get("/ciudadano", "192.0.2.1").statusCode());
    }

    @Test void bloqueLentoAbortaSinLlegarAlConsumidorYLiberaCupoYAdmision() throws Exception {
        try (var socket = cargaLenta("192.0.2.1")) {
            esperar(() -> !cargaLibre("192.0.2.1"));
            assertTrue(new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine().contains("408"));
            esperar(() -> cargaLibre("192.0.2.1"));
            esperar(() -> limites.metricas().get("activas").intValue() == 0);
        }
    }

    @Test void goteoNoRenuevaElPlazoTotalDeBloque() throws Exception {
        try (var socket = cargaLenta("192.0.2.1")) {
            esperar(() -> !cargaLibre("192.0.2.1"));
            for (int i = 0; i < 4; i++) { Thread.sleep(80); enviar(socket, "x"); }
            assertTrue(new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine().contains("408"));
            esperar(() -> cargaLibre("192.0.2.1"));
        }
    }

    @Test void bloqueCompletoSeDespachaUnaVezYElCupoSeRecuperaTrasError() throws Exception {
        for (var error : List.of("no", "error", "no")) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + BLOQUE))
                    .header("Content-Type", "application/octet-stream").header("X-Ddn-Client-Ip", "192.0.2.1")
                    .header("X-Test", error).PUT(HttpRequest.BodyPublishers.ofString("prueba")).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals("error".equals(error) ? 500 : 200, response.statusCode());
            if (!"error".equals(error)) assertEquals("prueba", new String(response.body(), StandardCharsets.UTF_8));
            esperar(() -> cargaLibre("192.0.2.1"));
            esperar(() -> limites.metricas().get("activas").intValue() == 0);
        }
    }

    @Test void cancelarCargaLiberaPermisos() throws Exception {
        try (var socket = cargaLenta("192.0.2.1")) { esperar(() -> !cargaLibre("192.0.2.1")); }
        esperar(() -> cargaLibre("192.0.2.1"));
        esperar(() -> limites.metricas().get("activas").intValue() == 0);
    }

    @Test void descargaDetenidaVenceYCierraElOriginalSinAgotarSiger() throws Exception {
        try (var socket = socket()) {
            enviar(socket, "GET /ciudadano/grande HTTP/1.1\r\nHost: localhost\r\nX-Ddn-Client-Ip: 192.0.2.1\r\n\r\n");
            esperar(() -> abiertos.get() == 1);
            assertFalse(descargaLibre("192.0.2.1"));
            assertEquals(200, get("/integracion/adjunto", "192.0.2.1").statusCode());
            esperar(() -> cerrados.get() == 2);
            assertTrue(descargaLibre("192.0.2.1"));
        }
    }

    @Test void desconexionYErroresDeFuenteNoPierdenPermisos() throws Exception {
        try (var socket = socket()) {
            enviar(socket, "GET /ciudadano/grande HTTP/1.1\r\nHost: localhost\r\nX-Ddn-Client-Ip: 192.0.2.1\r\n\r\n");
            esperar(() -> abiertos.get() == 1);
        }
        esperar(() -> cerrados.get() == 1); assertTrue(descargaLibre("192.0.2.1"));
        for (var error : List.of("abrir", "leer")) {
            try (var socket = socket()) {
                enviar(socket, "GET /ciudadano HTTP/1.1\r\nHost: localhost\r\nX-Ddn-Client-Ip: 192.0.2.1\r\n"
                        + "X-Test: " + error + "\r\nConnection: close\r\n\r\n");
                socket.getInputStream().readAllBytes();
            }
            esperar(() -> descargaLibre("192.0.2.1"));
        }
        assertEquals(abiertos.get(), cerrados.get());
    }

    @Test void rangoHeadY416MantienenSemanticaYRecuperanCupo() throws Exception {
        for (var method : List.of("HEAD", "GET")) {
            var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/integracion/adjunto"))
                    .header("Range", "bytes=1-2").method(method, HttpRequest.BodyPublishers.noBody()).build();
            var res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(method.equals("HEAD") ? 200 : 206, res.statusCode());
            assertEquals(method.equals("HEAD") ? 0 : 2, res.body().length);
        }
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/ciudadano"))
                .header("Range", "bytes=7-").build();
        var res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(416, res.statusCode()); assertEquals("bytes */4", res.headers().firstValue("Content-Range").orElseThrow());
        assertTrue(descargaLibre("127.0.0.1"));
    }
}
