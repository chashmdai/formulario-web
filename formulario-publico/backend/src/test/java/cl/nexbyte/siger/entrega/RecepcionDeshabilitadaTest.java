package cl.nexbyte.siger.entrega;
import cl.nexbyte.siger.identidad.IdentidadVerificada;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"siger.claveunica.client-id=test", "siger.claveunica.client-secret=test"})
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class RecepcionDeshabilitadaTest {
    @Autowired MockMvc mvc;
    @Test void configuracionNormalNuncaPrometeRecepcionSinAlmacenamiento() throws Exception {
        var auth = UsernamePasswordAuthenticationToken.authenticated(new IdentidadVerificada("44444444-4", "Ciudadana", "Prueba"), null,
                List.of(new SimpleGrantedAuthority("ROLE_CIUDADANO")));
        mvc.perform(get("/api/comunicaciones/capacidades").with(authentication(auth))).andExpect(status().isOk())
                .andExpect(jsonPath("$.recepcionDisponible").value(false));
        mvc.perform(post("/api/comunicaciones").with(authentication(auth)).with(csrf()).header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json").content("{\"mensaje\":\"Mensaje\",\"avisoPrivacidadLeido\":true}"))
                .andExpect(status().isServiceUnavailable());
    }
}
