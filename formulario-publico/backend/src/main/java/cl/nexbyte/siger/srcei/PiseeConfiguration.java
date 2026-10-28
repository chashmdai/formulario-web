package cl.nexbyte.siger.srcei;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class PiseeConfiguration {
    @Bean ClientePisee clientePisee(Environment env) throws java.io.IOException {
        boolean enabled = env.getProperty("PISEE_SRCEI_ENABLED", Boolean.class, false);
        String authorization = null;
        if (enabled) {
            String stage = env.getRequiredProperty("PISEE_ENVIRONMENT");
            if (!java.util.Set.of("DEV", "PRODUCTION").contains(stage))
                throw new IllegalArgumentException("PISEE_ENVIRONMENT debe ser DEV o PRODUCTION");
            if ("DEV".equals(stage) && !env.getProperty("PISEE_DEV_AUTHORIZED", Boolean.class, false))
                throw new IllegalStateException("PISEE DEV requiere su autorización existente");
            // El ambiente de credenciales CU es independiente del Nodo. La autorización
            // del consumidor productivo la acredita la configuración privada del Nodo.
            String file = env.getRequiredProperty("PISEE_LOCAL_AUTHORIZATION_PATH");
            Path path = Path.of(file);
            if (Files.size(path) > 256) throw new IllegalArgumentException("Credencial local PISEE excesiva");
            authorization = Files.readString(path).strip();
            if (authorization.isBlank()) throw new IllegalArgumentException("Credencial local PISEE requerida");
        }
        return new ClientePisee(enabled, URI.create(env.getProperty("PISEE_BASE_URL", "http://pisee:8084")), authorization,
            Duration.ofMillis(env.getProperty("PISEE_TIMEOUT_MS", Long.class, 15000L)),
            env.getProperty("PISEE_CONCURRENCY", Integer.class, 2), Clock.systemUTC());
    }
}
