package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.config.OrigenCliente;
import cl.nexbyte.siger.multimedia.CuposTransferencia;
import jakarta.servlet.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;

class LecturaBloqueTest {
    private static final String URI = "/api/formulario/recorridos/00000000-0000-0000-0000-000000000001"
            + "/cargas/00000000-0000-0000-0000-000000000002/bloques/0";
    private final MockEnvironment env = new MockEnvironment().withProperty("formulario.transferencias.cargas", "2").withProperty("formulario.transferencias.descargas", "4")
            .withProperty("formulario.transferencias.descargas-por-origen", "1")
            .withProperty("formulario.transferencias.plazo-carga", "PT0.1S");
    private final CuposTransferencia cupos = new CuposTransferencia(env, new OrigenCliente(env));
    private final LimitesCarga limites = new LimitesCarga(env, cupos);
    private final BloquesTransferencia bloques = new BloquesTransferencia(cupos);

    private static class Entrada extends ServletInputStream {
        private ReadListener listener;
        private int pendientes, leidos;
        private boolean fin;
        private IOException error;
        @Override public boolean isReady() { return pendientes > 0; }
        @Override public boolean isFinished() { return fin; }
        @Override public void setReadListener(ReadListener listener) { this.listener = listener; }
        @Override public int read() throws IOException {
            var b = new byte[1]; return read(b, 0, 1) < 0 ? -1 : b[0];
        }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            if (error != null) throw error;
            int n = Math.min(len, pendientes); Arrays.fill(b, off, off + n, (byte) 'x');
            pendientes -= n; leidos += n; return n;
        }
    }
    private static class Peticion extends MockHttpServletRequest {
        final Entrada entrada = new Entrada();
        Peticion() {
            super("PUT", URI); setAsyncSupported(true); setContentType("application/octet-stream"); setRemoteAddr("192.0.2.1");
        }
        @Override public ServletInputStream getInputStream() { return entrada; }
    }
    private MockHttpServletResponse iniciar(Peticion req) throws Exception {
        var res = new MockHttpServletResponse();
        limites.doFilter(req, res, (r, s) -> bloques.doFilter(r, s, (ignored, unused) -> fail("No despachar cuerpo incompleto")));
        return res;
    }
    private void timeout(Peticion req) throws IOException {
        var async = (MockAsyncContext) req.getAsyncContext();
        for (var listener : List.copyOf(async.getListeners())) listener.onTimeout(new AsyncEvent(async));
    }
    private void libre(Peticion req) {
        try (var ignored = cupos.carga(req)) { }
        assertEquals(0, limites.metricas().get("activas").intValue());
    }

    @Test void timeoutSinBytesAbortaCon408YLiberaAdmisionYCupoPorOrigen() throws Exception {
        var req = new Peticion(); var res = iniciar(req);
        assertEquals(100, req.getAsyncContext().getTimeout());
        assertThrows(FalloTecnico.class, () -> cupos.carga(req));
        timeout(req); assertEquals(408, res.getStatus()); libre(req);
        req.entrada.listener.onError(new IOException()); libre(req);
    }

    @Test void goteoNoPuedeRenovarPlazoTotalNiDespacharDatosParciales() throws Exception {
        var req = new Peticion(); var res = iniciar(req);
        req.entrada.pendientes = 1; req.entrada.listener.onDataAvailable();
        Thread.sleep(130);
        req.entrada.pendientes = 1; req.entrada.listener.onDataAvailable();
        assertEquals(1, req.entrada.leidos); assertEquals(408, res.getStatus()); libre(req);
        req.entrada.listener.onAllDataRead();
        assertNull(((MockAsyncContext) req.getAsyncContext()).getDispatchedPath());
    }

    @Test void cuerpoCompletoSeReproduceEnDispatchYLuegoLiberaPermisos() throws Exception {
        var req = new Peticion(); iniciar(req);
        req.entrada.pendientes = 3; req.entrada.listener.onDataAvailable();
        req.entrada.fin = true; req.entrada.listener.onAllDataRead();
        var async = (MockAsyncContext) req.getAsyncContext();
        assertNotNull(async.getDispatchedPath());
        assertEquals("xxx", new String(async.getRequest().getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII));
        assertThrows(FalloTecnico.class, () -> cupos.carga(req));
        async.complete(); libre(req);
    }

    @Test void excesoChunkedErrorYCancelacionLiberanLosDosPermisosIniciales() throws Exception {
        var req = new Peticion(); var res = iniciar(req);
        req.entrada.pendientes = 8 * 1024 * 1024 + 1;
        req.entrada.listener.onDataAvailable();
        assertEquals(413, res.getStatus()); libre(req);
        for (int i = 0; i < 2; i++) {
            var r = new Peticion(); iniciar(r);
            r.entrada.listener.onError(new IOException("Desconexión sintética")); libre(r);
        }
        var otro = new Peticion(); otro.setRemoteAddr("192.0.2.2");
        try (var uno = cupos.carga(req); var dos = cupos.carga(otro)) {
            var tercero = new Peticion(); tercero.setRemoteAddr("192.0.2.3");
            assertThrows(FalloTecnico.class, () -> cupos.carga(tercero));
        }
    }

    @Test void saturacionPorOrigenEs429RetryAfterYSinConsumirElCuerpo() throws Exception {
        var req = new Peticion(); iniciar(req);
        var segundo = new Peticion(); var rechazado = iniciar(segundo);
        assertEquals(429, rechazado.getStatus()); assertEquals("2", rechazado.getHeader("Retry-After"));
        assertNull(segundo.entrada.listener);
        timeout(req); libre(req);
    }

    @Test void excepcionDelConsumidorYMultipartRecuperanLosCupos() throws Exception {
        var req = new Peticion(); req.setMethod("POST");
        req.setRequestURI("/api/formulario/recorridos/00000000-0000-0000-0000-000000000001/adjuntos");
        req.setContentType("multipart/form-data; boundary=test"); req.setContent(new byte[]{1});
        assertThrows(IOException.class, () -> limites.doFilter(req, new MockHttpServletResponse(),
                (r, s) -> { throw new IOException("Error sintético"); }));
        libre(req);
    }
}
