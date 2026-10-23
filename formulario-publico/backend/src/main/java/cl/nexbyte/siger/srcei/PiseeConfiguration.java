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
            if (env.matchesProfiles("production", "prod") || !"SANDBOX".equals(env.getProperty("siger.claveunica.environment", "SANDBOX")))
                throw new IllegalStateException("La integración DEV no puede habilitarse en un proceso QA/producción");
            if (!"DEV".equals(env.getProperty("PISEE_ENVIRONMENT")) || !env.getProperty("PISEE_DEV_AUTHORIZED", Boolean.class, false))
                throw new IllegalStateException("Este adaptador requiere autorización explícita DEV; QA/PROD aún no validados");
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
