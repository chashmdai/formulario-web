package cl.nexbyte.siger.entrega;

import cl.nexbyte.siger.identidad.IdentidadVerificada;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"siger.claveunica.client-id=test", "siger.claveunica.client-secret=test",
        "spring.profiles.active=local-integration-demo", "publico.integracion.token=integration-test-only-01234567890123456789"})
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class ContratoPublicoHttpTest {
    static final String TOKEN = "Bearer integration-test-only-01234567890123456789";
    static final Path DIRECTORY;
    static { try { DIRECTORY = Files.createTempDirectory("public-contract-"); } catch (Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void directory(DynamicPropertyRegistry r) { r.add("publico.entrega.directorio", DIRECTORY::toString); }
    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    @Test void recepcionConIdentidadDeSesionExportacionYConfirmacionIdempotente() throws Exception {
        var key = UUID.randomUUID();
        var auth = UsernamePasswordAuthenticationToken.authenticated(new IdentidadVerificada("44444444-4", "Ciudadana", "Prueba"), null,
                List.of(new SimpleGrantedAuthority("ROLE_CIUDADANO")));
        var request = "{\"mensaje\":\"Mensaje contractual\",\"avisoPrivacidadLeido\":true}";
        mvc.perform(post("/api/comunicaciones").with(authentication(auth)).with(csrf()).header("Idempotency-Key", key)
                .contentType("application/json").content(request)).andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/comunicaciones/" + key)).andExpect(jsonPath("$.estado").value("PENDIENTE_SIGER"));
        mvc.perform(post("/api/comunicaciones").with(authentication(auth)).with(csrf()).header("Idempotency-Key", key)
                .contentType("application/json").content(request)).andExpect(status().isAccepted());
        var result = mvc.perform(get("/integracion/v1/comunicaciones/pendientes").header("Authorization", TOKEN)).andExpect(status().isOk()).andReturn();
        var lote = mapper.readValue(result.getResponse().getContentAsByteArray(), IntegracionController.Pendientes.class);
        var sobre = lote.comunicaciones().stream().filter(s -> s.id().equals(key)).findFirst().orElseThrow();
        assertThat(sobre.identidad().run()).isEqualTo("44444444-4"); assertThat(sobre.integra()).isTrue();
        var ack = mapper.writeValueAsString(new SobreComunicacion.Confirmacion(UUID.randomUUID(), Instant.now(), sobre.sha256()));
        for (int i=0; i<2; i++) mvc.perform(post("/integracion/v1/comunicaciones/{id}/confirmacion", key)
                .header("Authorization", TOKEN).contentType("application/json").content(ack)).andExpect(status().isNoContent());
        mvc.perform(get("/api/comunicaciones/{id}", key).with(authentication(auth))).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA_SIGER"));
        mvc.perform(get("/integracion/v1/comunicaciones/pendientes").header("Authorization", TOKEN)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(key.toString()))));
    }
    @Test void sesionesCiudadanasNoAutorizanIntegracionYValidacionImpideSuplantacion() throws Exception {
        var auth = UsernamePasswordAuthenticationToken.authenticated(new IdentidadVerificada("44444444-4", "Ciudadana", "Prueba"), null, List.of(new SimpleGrantedAuthority("ROLE_CIUDADANO")));
        mvc.perform(get("/integracion/v1/comunicaciones/pendientes").with(authentication(auth))).andExpect(status().isUnauthorized());
        mvc.perform(get("/integracion/v1/comunicaciones/pendientes").header("Authorization", "Bearer incorrecto")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/comunicaciones").with(authentication(auth)).header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json").content("{\"mensaje\":\"hola\",\"avisoPrivacidadLeido\":true}")).andExpect(status().isForbidden());
        for (var body : List.of("{\"mensaje\":\"hola\",\"avisoPrivacidadLeido\":false}",
                "{\"mensaje\":\"hola\",\"avisoPrivacidadLeido\":true,\"run\":\"11111111-1\"}",
                "{\"mensaje\":\"  \",\"avisoPrivacidadLeido\":true}")) {
            mvc.perform(post("/api/comunicaciones").with(authentication(auth)).with(csrf()).header("Idempotency-Key", UUID.randomUUID())
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
    }
}
