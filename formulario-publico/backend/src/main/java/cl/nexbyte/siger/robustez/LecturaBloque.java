package cl.nexbyte.siger.robustez;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.time.Duration;

/** Lee como máximo un bloque en memoria (8 MiB), sin ocupar un hilo esperando al cliente. */
final class LecturaBloque implements ReadListener, AsyncListener {
    private final AsyncContext async;
    private final HttpServletResponse response;
    private final ServletInputStream input;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final int max;
    private final long vence;
    private boolean terminada;
    private byte[] cuerpo;

    static void iniciar(HttpServletRequest request, HttpServletResponse response, int max,
                        Duration plazo) throws IOException {
        if (!request.isAsyncSupported()) throw new IOException("Transferencia requiere Servlet async");
        var lectura = new LecturaBloque(request, response, max, plazo);
        lectura.async.addListener(lectura);
        lectura.async.setTimeout(plazo.toMillis());
        lectura.input.setReadListener(lectura);
    }

    private LecturaBloque(HttpServletRequest request, HttpServletResponse response, int max,
                          Duration plazo) throws IOException {
        this.response = response;
        this.max = max;
        vence = System.nanoTime() + plazo.toNanos();
        input = request.getInputStream();
        var wrapped = new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() throws IOException {
                if (cuerpo == null) return input;
                var in = new ByteArrayInputStream(cuerpo);
                return new ServletInputStream() {
                    @Override public int read() { return in.read(); }
                    @Override public int read(byte[] b, int off, int len) { return in.read(b, off, len); }
                    @Override public boolean isFinished() { return in.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) {
                        throw new UnsupportedOperationException("Bloque ya recibido");
                    }
                };
            }
        };
        async = request.startAsync(wrapped, response);
    }

    @Override public synchronized void onDataAvailable() throws IOException {
        byte[] buffer = new byte[16384];
        while (!terminada && input.isReady() && !input.isFinished()) {
            if (System.nanoTime() >= vence) { fallar(408); return; }
            int n = input.read(buffer, 0, Math.min(buffer.length, max + 1 - bytes.size()));
            if (n < 0) break;
            bytes.write(buffer, 0, n);
            if (bytes.size() > max) { fallar(413); return; }
        }
    }

    @Override public synchronized void onAllDataRead() {
        if (terminada) return;
        if (System.nanoTime() >= vence) { fallar(408); return; }
        terminada = true;
        cuerpo = bytes.toByteArray();
        // El plazo sólo cubre la recepción del cuerpo, no el trabajo JDBC posterior.
        async.setTimeout(0);
        async.dispatch();
    }
    private synchronized void fallar(int status) {
        if (terminada) return;
        terminada = true;
        response.setStatus(status);
        response.setHeader("Cache-Control", "no-store");
        // No cerrar/consumir el cuerpo: el contenedor termina la petición sin drenar un cliente lento.
        async.complete();
    }
    @Override public void onError(Throwable error) { fallar(400); }
    @Override public void onTimeout(AsyncEvent event) { fallar(408); }
    @Override public void onError(AsyncEvent event) { fallar(400); }
    @Override public void onComplete(AsyncEvent event) { /* El filtro de admisión libera los cupos. */ }
    @Override public void onStartAsync(AsyncEvent event) { event.getAsyncContext().addListener(this); }
}
