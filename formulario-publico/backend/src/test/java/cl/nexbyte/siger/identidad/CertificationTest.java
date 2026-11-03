package cl.nexbyte.siger.identidad;

import java.util.List;
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

@SpringBootTest(properties={"siger.certificacion-claveunica=true", "siger.claveunica.habilitada=false",
    "siger.claveunica.callback-approved=false", "publico.integracion.habilitada=false"})
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class CertificationTest {
    @Autowired MockMvc mvc;
    @Test void blocksAllBusinessRoutesEvenWithSessionCsrfAndTechnicalAuthorization() throws Exception {
        var identity=new IdentidadVerificada("44444444-4", "Persona", "Prueba");
        var citizen=UsernamePasswordAuthenticationToken.authenticated(identity,null,List.of(new SimpleGrantedAuthority("ROLE_CIUDADANO")));
        for(String path:List.of("/api/comunicaciones", "/api/comunicaciones/preview", "/api/nna/sesion",
                "/api/formulario/recorridos", "/api/formulario/recorridos/actual", "/api/formulario/recorridos/1/cargas",
                "/api/formulario/recorridos/1/adjuntos", "/integracion/v3/formulario-completo/entregas/reclamar",
                "/operacion/v1/control", "/actuator/health", "/admin")) {
            mvc.perform(post(path).secure(true).with(authentication(citizen)).with(csrf())
                    .header("Authorization","Bearer test-only-token").contentType("application/json").content("{}"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Cache-Control","no-store"));
            mvc.perform(get(path).secure(true)).andExpect(status().isServiceUnavailable());
        }
        mvc.perform(put("/api/formulario/recorridos/1/cargas/1/bloques/0").secure(true).with(csrf()))
            .andExpect(status().isServiceUnavailable());
    }
    @Test void sessionRevealsOnlyAuthenticationResultAndNeverPersonalIdentity() throws Exception {
        var identity=new IdentidadVerificada("44444444-4", "Persona", "Prueba");
        var citizen=UsernamePasswordAuthenticationToken.authenticated(identity,null,List.of(new SimpleGrantedAuthority("ROLE_CIUDADANO")));
        mvc.perform(get("/api/session").secure(true).with(authentication(citizen)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.certificationMode").value(true)).andExpect(jsonPath("$.identity").isEmpty())
            .andExpect(jsonPath("$.ingresoScope").isEmpty());
        mvc.perform(get("/api/identidad/atributos").secure(true).with(authentication(citizen)))
            .andExpect(status().isServiceUnavailable());
    }
    @Test void keepsCsrfForLogoutAndNeverAllowsGetLogoutMutation() throws Exception {
        mvc.perform(get("/api/csrf").secure(true)).andExpect(status().isOk());
        mvc.perform(post("/auth/logout").secure(true)).andExpect(status().isForbidden());
        mvc.perform(post("/auth/logout").secure(true).with(csrf())).andExpect(status().isFound());
        mvc.perform(get("/auth/logout").secure(true)).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/session").secure(true)).andExpect(jsonPath("$.authenticated").value(false));
    }
}
