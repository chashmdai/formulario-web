package cl.nexbyte.siger.entrega;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/** Adaptador explícitamente local; no activar en un despliegue público. */
public final class RecepcionArchivosDemo implements RecepcionPublica, AutoCloseable {
    public static final String AVISO = "demo-local-v1";
    private final ArchivosDemo files;
    public RecepcionArchivosDemo(Path directory, JsonMapper mapper) throws IOException { files = new ArchivosDemo(directory, mapper); }
    public boolean disponible() { return true; }
    public synchronized SobreComunicacion recibir(UUID clave, SobreComunicacion.Identidad identidad, String mensaje) {
        try {
            var name = clave + ".json";
            if (files.exists(name)) {
                var previous = files.read(name, SobreComunicacion.class);
                if (!previous.integra()) throw new EntregaException(503);
                var prior=previous.identidad();
                boolean same=prior.equals(identidad) || ("1.0".equals(previous.version()) && prior.run().equals(identidad.run())
                        && prior.nombres().equals(identidad.nombres()) && prior.apellidos().equals(identidad.apellidos()));
                if (!same || !previous.mensaje().equals(mensaje)) throw new EntregaException(409);
                return previous;
            }
            var sobre = SobreComunicacion.crear(clave, Instant.now(), identidad, mensaje, AVISO);
            files.write(name, sobre); // Solo responder 202 después de que write finalice.
            return sobre;
        } catch (IOException | tools.jackson.core.JacksonException e) { throw new EntregaException(503); }
    }
    public synchronized Estado consultar(UUID id, String run) {
        try {
            if (!files.exists(id + ".json")) throw new EntregaException(404);
            var sobre = files.read(id + ".json", SobreComunicacion.class);
            if (!sobre.identidad().run().equals(run)) throw new EntregaException(404);
            if (!sobre.integra()) throw new EntregaException(503);
            var ack = files.exists(id + ".ack") ? files.read(id + ".ack", SobreComunicacion.Confirmacion.class) : null;
            return new Estado(id, ack == null ? "PENDIENTE_SIGER" : "CONFIRMADA_SIGER", ack);
        } catch (IOException | tools.jackson.core.JacksonException e) { throw new EntregaException(503); }
    }
    public synchronized List<SobreComunicacion> pendientes(int limite) {
        try (var paths = Files.list(files.directory)) {
            var result = new java.util.ArrayList<SobreComunicacion>();
            for (var path : paths.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                var name = path.getFileName().toString();
                var id = UUID.fromString(name.substring(0, name.length() - 5));
                if (!files.exists(id + ".ack")) {
                    var sobre = files.read(name, SobreComunicacion.class);
                    if (!sobre.integra()) throw new EntregaException(503);
                    result.add(sobre);
                    if (result.size() == limite) break;
                }
            }
            return List.copyOf(result);
        } catch (IOException | IllegalArgumentException | tools.jackson.core.JacksonException e) { throw new EntregaException(503); }
    }
    public synchronized void confirmar(UUID id, SobreComunicacion.Confirmacion ack) {
        try {
            if (!files.exists(id + ".json")) throw new EntregaException(404);
            var sobre = files.read(id + ".json", SobreComunicacion.class);
            if (!sobre.integra() || !sobre.sha256().equals(ack.sha256())) throw new EntregaException(409);
            if (files.exists(id + ".ack")) {
                if (!files.read(id + ".ack", SobreComunicacion.Confirmacion.class).equals(ack)) throw new EntregaException(409);
                return;
            }
            files.write(id + ".ack", ack);
        } catch (IOException | tools.jackson.core.JacksonException e) { throw new EntregaException(503); }
    }
    public void close() throws IOException { files.close(); }
}
