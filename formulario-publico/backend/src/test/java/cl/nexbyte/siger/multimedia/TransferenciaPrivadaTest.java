package cl.nexbyte.siger.multimedia;

import cl.nexbyte.siger.config.OrigenCliente;
import cl.nexbyte.siger.robustez.FalloTecnico;
import jakarta.servlet.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;

class TransferenciaPrivadaTest {
    private final MockEnvironment env = new MockEnvironment().withProperty("formulario.transferencias.cargas", "2").withProperty("formulario.transferencias.descargas", "4")
            .withProperty("formulario.transferencias.descargas-por-origen", "1")
            .withProperty("formulario.transferencias.plazo-descarga", "PT0.1S")
            .withProperty("formulario.transferencias.plazo-siger", "PT1H");
    private final CuposTransferencia cupos = new CuposTransferencia(env, new OrigenCliente(env));
    private final TransferenciaPrivada transferencias = new TransferenciaPrivada(cupos);
    private final AtomicInteger cerrados = new AtomicInteger();

    private MockHttpServletRequest request(String ip) {
        var r = new MockHttpServletRequest("GET", "/adjunto"); r.setRemoteAddr(ip); r.setAsyncSupported(true); return r;
    }
    private RecursoPrivado recurso(boolean error) {
        return new RecursoPrivado("sintetico.txt", 4, "a".repeat(64), "text/plain", () -> new ByteArrayInputStream(new byte[4]) {
            @Override public synchronized int read(byte[] b, int off, int len) {
                if (error) throw new UncheckedIOException(new IOException("Error sintético"));
                return super.read(b, off, len);
            }
            @Override public void close() { cerrados.incrementAndGet(); }
        });
    }
    private void timeout(MockHttpServletRequest request) throws IOException {
        var async = (MockAsyncContext) request.getAsyncContext();
        for (var listener : List.copyOf(async.getListeners())) listener.onTimeout(new AsyncEvent(async));
    }
    private void libre(String ip) { try (var permiso = cupos.descarga(request(ip))) { } }
    private static class Salida extends ServletOutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private boolean lista;
        private WriteListener listener;
        private boolean error;
        @Override public boolean isReady() { return lista; }
        @Override public void setWriteListener(WriteListener listener) { this.listener = listener; }
        @Override public void write(int b) throws IOException { if (error) throw new IOException("Cliente canceló"); bytes.write(b); }
        @Override public void write(byte[] b, int off, int len) throws IOException {
            if (error) throw new IOException("Cliente canceló"); bytes.write(b, off, len);
        }
    }
    private static class Respuesta extends MockHttpServletResponse {
        private final Salida salida = new Salida();
        @Override public ServletOutputStream getOutputStream() { return salida; }
    }

    @Test void descargaDetenidaNoEscribeYTimeoutLiberaYCierraUnaSolaVez() throws Exception {
        var req = request("192.0.2.1"); var res = new Respuesta();
        transferencias.enviarCiudadano(recurso(false), false, req, res);
        res.salida.listener.onWritePossible(); assertEquals(0, res.salida.bytes.size());
        assertEquals(429, assertThrows(FalloTecnico.class, () -> cupos.descarga(req)).status);
        assertEquals(100, req.getAsyncContext().getTimeout());
        timeout(req); assertEquals(1, cerrados.get()); libre("192.0.2.1");
        res.salida.lista = true; res.salida.listener.onWritePossible(); res.salida.listener.onError(new IOException());
        assertEquals(0, res.salida.bytes.size()); assertEquals(1, cerrados.get());
    }

    @Test void plazoTotalNoSeRenuevaSiVuelveAEstarEscribible() throws Exception {
        var req = request("192.0.2.1"); var res = new Respuesta();
        transferencias.enviarCiudadano(recurso(false), false, req, res);
        Thread.sleep(130); res.salida.lista = true; res.salida.listener.onWritePossible();
        assertEquals(0, res.salida.bytes.size()); assertEquals(1, cerrados.get()); libre("192.0.2.1");
    }

    @Test void sigerEscribeAunqueTodasLasDescargasCiudadanasEstenOcupadas() throws Exception {
        var reqs = new ArrayList<MockHttpServletRequest>();
        try {
            for (int i = 1; i <= 4; i++) {
                var req = request("192.0.2." + i); reqs.add(req);
                transferencias.enviarCiudadano(recurso(false), false, req, new Respuesta());
            }
            var req = request("192.0.2.1"); var res = new Respuesta();
            transferencias.enviarIntegracion(recurso(false), req, res);
            assertEquals(3_600_000, req.getAsyncContext().getTimeout());
            res.salida.lista = true; res.salida.listener.onWritePossible();
            assertEquals(4, res.salida.bytes.size()); assertEquals(200, res.getStatus());
        } finally { for (var req : reqs) timeout(req); }
        assertEquals(5, cerrados.get()); libre("192.0.2.1");
    }

    @Test void errorDeLecturaEscrituraYDesconexionDevuelvenTodosLosPermisos() throws Exception {
        for (var error : List.of("leer", "escribir", "cancelar")) {
            var req = request("192.0.2.1"); var res = new Respuesta();
            transferencias.enviarCiudadano(recurso(error.equals("leer")), false, req, res);
            if (error.equals("cancelar")) res.salida.listener.onError(new IOException("Cancelación sintética"));
            else {
                res.salida.lista = true; res.salida.error = error.equals("escribir");
                assertThrows(Exception.class, () -> res.salida.listener.onWritePossible());
            }
            libre("192.0.2.1");
        }
        assertEquals(3, cerrados.get());
        var permisos = new ArrayList<CuposTransferencia.Permiso>();
        try {
            for (int i = 1; i <= 4; i++) permisos.add(cupos.descarga(request("192.0.2." + i)));
            assertThrows(FalloTecnico.class, () -> cupos.descarga(request("192.0.2.5")));
        } finally { permisos.forEach(CuposTransferencia.Permiso::close); }
    }

    @Test void headRangoInvalidoYAperturaFallidaNoPierdenCupo() throws Exception {
        var req = request("192.0.2.1"); req.setMethod("HEAD");
        transferencias.enviarCiudadano(recurso(false), false, req, new Respuesta()); libre("192.0.2.1");
        req.setMethod("GET"); req.addHeader("Range", "bytes=5-"); var invalid = new Respuesta();
        transferencias.enviarCiudadano(recurso(false), false, req, invalid);
        assertEquals(416, invalid.getStatus()); libre("192.0.2.1");
        req.removeHeader("Range");
        var source = new RecursoPrivado("x", 4, "a".repeat(64), "text/plain", () -> { throw new IOException(); });
        assertThrows(IOException.class, () -> transferencias.enviarCiudadano(source, false, req, new Respuesta()));
        libre("192.0.2.1"); assertEquals(0, cerrados.get());
    }
}
