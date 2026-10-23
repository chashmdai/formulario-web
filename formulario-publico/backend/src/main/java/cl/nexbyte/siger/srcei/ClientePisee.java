package cl.nexbyte.siger.srcei;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static cl.nexbyte.siger.srcei.ResultadoSrcei.Estado.*;

/** Cliente de servidor, sin proxy público, redirects, reintentos ni logs de cuerpos. */
public final class ClientePisee implements AutoCloseable {
    private final HttpClient client;
    private final URI endpoint;
    private final String localAuthorization;
    private final Duration timeout;
    private final Semaphore permits;
    private final boolean enabled;
    private final Clock clock;
    public ClientePisee(boolean enabled, URI base, String localAuthorization, Duration timeout, int concurrency, Clock clock) {
        if (timeout == null || timeout.toMillis() < 50 || timeout.toSeconds() > 60 || concurrency < 1 || concurrency > 8)
            throw new IllegalArgumentException("Límites PISEE inválidos");
        this.enabled = enabled; this.timeout = timeout; this.clock = clock;
        permits = new Semaphore(concurrency);
        if (base == null || base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || !(base.getPath().isEmpty() || base.getPath().equals("/"))
                || !("https".equals(base.getScheme()) || "http".equals(base.getScheme()) && Set.of("pisee", "localhost", "127.0.0.1").contains(base.getHost())))
            throw new IllegalArgumentException("Origen privado PISEE inválido");
        if (localAuthorization != null && (localAuthorization.length() > 256 || localAuthorization.chars().anyMatch(Character::isISOControl)))
            throw new IllegalArgumentException("Credencial PISEE inválida");
        this.localAuthorization = localAuthorization;
        endpoint = base.resolve("/srcei/informacionPersonalBasico");
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public ResultadoSrcei consultar(RunConsulta run) {
        Objects.requireNonNull(run);
        Instant at = clock.instant(); UUID id = UUID.randomUUID();
        if (!enabled) return ResultadoSrcei.fallo(NO_HABILITADO, at, id);
        if (!permits.tryAcquire()) return ResultadoSrcei.fallo(SATURADO, at, id);
        CompletableFuture<HttpResponse<byte[]>> future = null;
        try {
            var request = HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type", "application/xml; charset=utf-8")
                .header("Accept", "application/xml").POST(HttpRequest.BodyPublishers.ofString(XmlSrcei.solicitud(run), StandardCharsets.UTF_8));
            if (localAuthorization != null && !localAuthorization.isBlank()) request.header("X-PISEE-LocalAuthorization", localAuthorization);
            future = client.sendAsync(request.build(), info -> new LimitedBody());
            var response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() == 401 || response.statusCode() == 403) return ResultadoSrcei.fallo(AUTORIZACION, at, id);
            if (response.statusCode() != 200) return ResultadoSrcei.fallo(INDISPONIBLE, at, id);
            return XmlSrcei.respuesta(response.body(), run, at, id);
        } catch (TimeoutException e) {
            return ResultadoSrcei.fallo(TIMEOUT, at, id);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); return ResultadoSrcei.fallo(RED, at, id);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
            return ResultadoSrcei.fallo(cause instanceof BodyTooLarge ? RESPUESTA_EXCESIVA : cause instanceof HttpTimeoutException ? TIMEOUT : RED, at, id);
        } finally {
            if (future != null && !future.isDone()) future.cancel(true);
            permits.release();
        }
    }
    @Override public void close() { client.shutdownNow(); }
    private static final class BodyTooLarge extends RuntimeException {}
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return body; }
        public void onSubscribe(Flow.Subscription s) { subscription = s; s.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if ((long) bytes.size() + buffer.remaining() > XmlSrcei.MAX_BYTES) {
                    subscription.cancel(); body.completeExceptionally(new BodyTooLarge()); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable t) { body.completeExceptionally(t); }
        public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
