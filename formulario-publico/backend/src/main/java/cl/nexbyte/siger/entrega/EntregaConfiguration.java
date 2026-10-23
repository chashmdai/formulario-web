package cl.nexbyte.siger.entrega;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class EntregaConfiguration {
    @Bean @Profile("local-integration-demo")
    RecepcionArchivosDemo localRecepcion(Environment env, JsonMapper mapper) throws java.io.IOException {
        if (!"SANDBOX".equals(env.getProperty("siger.claveunica.environment", "SANDBOX"))
                || !"127.0.0.1".equals(env.getProperty("server.address", "127.0.0.1")))
            throw new IllegalStateException("La recepción de demostración requiere Sandbox y loopback");
        var directory = env.getRequiredProperty("publico.entrega.directorio");
        if (directory.isBlank() || !Path.of(directory).isAbsolute() || env.getProperty("publico.integracion.token", "").length() < 32)
            throw new IllegalStateException("La demostración requiere directorio absoluto y credencial de integración externa");
        return new RecepcionArchivosDemo(Path.of(directory), mapper);
    }
    @Bean @Profile("!local-integration-demo & !jdbc-reception")
    RecepcionPublica recepcionDeshabilitada() {
        return new RecepcionPublica() {
            public boolean disponible() { return false; }
            public SobreComunicacion recibir(UUID id, SobreComunicacion.Identidad identidad, String mensaje) { throw new EntregaException(503); }
            public Estado consultar(UUID id, String run) { throw new EntregaException(503); }
            public List<SobreComunicacion> pendientes(int limite) { throw new EntregaException(503); }
            public void confirmar(UUID id, SobreComunicacion.Confirmacion ack) { throw new EntregaException(503); }
        };
    }
}
