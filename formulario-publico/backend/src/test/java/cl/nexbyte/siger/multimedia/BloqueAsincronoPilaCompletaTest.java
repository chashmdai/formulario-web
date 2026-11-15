package cl.nexbyte.siger.multimedia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cl.nexbyte.siger.comunicaciones.SesionIngreso;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Pila completa con Tomcat real: Spring Security (CSRF), LimitesCarga, BloquesTransferencia (lectura
 * asíncrona del cuerpo) y dispatch al controlador real. Comprueba que el cuerpo llega íntegro, que un
 * cliente lento recibe 408 sin alcanzar el controlador y que los cupos de carga no se pierden.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "siger.claveunica.client-id=test", "siger.claveunica.client-secret=test",
    "server.forward-headers-strategy=native",
    "server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.1",
    "server.tomcat.remoteip.protocol-header=X-Forwarded-Proto",
    "formulario.transferencias.plazo-carga=PT1S"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BloqueAsincronoPilaCompletaTest {
    static final AtomicReference<byte[]> recibido = new AtomicReference<>();
    @LocalServerPort int port;
    @Autowired CargasMultimedia cargas;

    @TestConfiguration static class Fixture {
        @Bean CargasMultimedia cargasMultimedia() throws IOException {
            var mock = mock(CargasMultimedia.class);
            when(mock.bloque(any(), any(), anyString(), anyInt(), anyString(), anyLong(), any())).thenAnswer(call -> {
                recibido.set(((InputStream) call.getArgument(6)).readAllBytes());
                return null;
            });
            return mock;
        }
        /** Sólo prueba: crea la capacidad de sesión interna que en producción emite el inicio del recorrido. */
        @Bean FilterRegistrationBean<Filter> sesionSintetica() {
            var bean = new FilterRegistrationBean<Filter>((req, res, chain) -> {
                if (((HttpServletRequest) req).getHeader("X-Test-Sesion") != null) SesionIngreso.iniciar((HttpServletRequest) req);
                chain.doFilter(req, res);
            });
            bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return bean;
        }
    }

    // La cookie de sesión es Secure (origen HTTPS tras el proxy): se reenvía explícitamente por el loopback.
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private String cookie;

    private String csrf() throws Exception {
        var res = client.send(HttpRequest.newBuilder(uri("/api/csrf")).header("X-Forwarded-Proto", "https").build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(200);
        cookie = res.headers().allValues("Set-Cookie").stream().map(v -> v.split(";", 2)[0])
            .reduce((a, b) -> a + "; " + b).orElseThrow();
        var m = java.util.regex.Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(res.body());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }

    private String ruta() {
        return "/api/formulario/recorridos/" + UUID.randomUUID() + "/cargas/" + UUID.randomUUID() + "/bloques/0";
    }

    private HttpResponse<String> put(String token, byte[] body) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(ruta())).timeout(Duration.ofSeconds(20))
            .header("X-Forwarded-Proto", "https").header("X-CSRF-TOKEN", token).header("X-Test-Sesion", "1")
            .header("Cookie", cookie)
            .header("X-SHA256", "0".repeat(64)).header("Content-Type", "application/octet-stream")
            .PUT(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void bloqueAtraviesaSeguridadYDespachaElCuerpoIntegroSinPerderCupos() throws Exception {
        String token = csrf();
        byte[] body = new byte[3 * 1024 * 1024 + 17];
        new java.util.Random(7).nextBytes(body);
        // Más solicitudes que cupos de carga (2): un permiso perdido produciría 429.
        for (int i = 0; i < 5; i++) {
            recibido.set(null);
            var res = put(token, body);
            assertThat(res.statusCode()).as("respuesta %s: %s", i, res.body()).isEqualTo(200);
            assertThat(recibido.get()).isEqualTo(body);
        }
    }

    @Test void clienteLentoRecibe408SinAlcanzarAlControladorYLiberaElCupo() throws Exception {
        String token = csrf();
        clearInvocations(cargas);
        for (int i = 0; i < 3; i++) {
            try (var socket = new Socket("127.0.0.1", port)) {
                socket.setSoTimeout(15000);
                var out = socket.getOutputStream();
                out.write(("PUT " + ruta() + " HTTP/1.1\r\nHost: 127.0.0.1\r\nX-Forwarded-Proto: https\r\n"
                    + "X-CSRF-TOKEN: " + token + "\r\nCookie: " + cookie + "\r\nX-Test-Sesion: 1\r\n"
                    + "X-SHA256: " + "0".repeat(64) + "\r\nContent-Type: application/octet-stream\r\n"
                    + "Content-Length: 4096\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.write(new byte[100]);
                out.flush();
                var status = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)).readLine();
                assertThat(status).startsWith("HTTP/1.1 408");
            }
        }
        verify(cargas, never()).bloque(any(), any(), anyString(), anyInt(), anyString(), anyLong(), any());
        var res = put(token, new byte[1024]);
        assertThat(res.statusCode()).isEqualTo(200);
    }
}
