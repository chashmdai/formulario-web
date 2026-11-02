package cl.nexbyte.siger.identidad;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "siger.claveunica.habilitada=false", "siger.claveunica.client-id=", "siger.claveunica.client-secret=",
    "siger.claveunica.environment=PRODUCTION", "siger.claveunica.callback-approved=false",
    "siger.claveunica.public-origin=https://formulario.defensorianinez.gob.cl",
    "siger.claveunica.redirect-uri=https://formulario.defensorianinez.gob.cl/redirect",
    "siger.claveunica.logout-uri=https://formulario.defensorianinez.gob.cl/logout",
    "publico.integracion.habilitada=false", "publico.integracion.token=test-only-integration-token-32-bytes"
})
@AutoConfigureMockMvc
class ClaveUnicaDeshabilitadaTest {
    @Autowired MockMvc mvc;
    @Autowired ClientRegistrationRepository registrations;
    @Test void startsWithoutCredentialsAndDoesNotRegisterAnOAuthClient() {
        assertThat(registrations.findByRegistrationId("claveunica")).isNull();
    }
    @Test void loginAndForgedCallbackFailClosedWithoutRedirectOrIdentity() throws Exception {
        for(String path:new String[]{"/oauth2/authorization/claveunica","/redirect?code=forged&state=forged"}) {
            mvc.perform(get(path).secure(true).with(request -> { request.setScheme("https"); return request; })).andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist("Location"));
        }
        mvc.perform(get("/api/session").secure(true).with(request -> { request.setScheme("https"); return request; })).andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(false)).andExpect(jsonPath("$.loginAvailable").value(false))
            .andExpect(jsonPath("$.identity").isEmpty());
        mvc.perform(get("/api/identidad/atributos").secure(true).with(request -> { request.setScheme("https"); return request; })).andExpect(status().isUnauthorized());
    }
    @Test void noProviderLogoutAndCsrfStillRequired() throws Exception {
        mvc.perform(get("/auth/session-expired").secure(true).with(request -> { request.setScheme("https"); return request; })).andExpect(status().isSeeOther())
            .andExpect(redirectedUrl("https://formulario.defensorianinez.gob.cl/?session=closed"));
        mvc.perform(post("/auth/logout").secure(true).with(request -> { request.setScheme("https"); return request; })).andExpect(status().isForbidden());
        mvc.perform(post("/auth/logout").secure(true).with(request -> { request.setScheme("https"); return request; }).with(csrf())).andExpect(status().isFound())
            .andExpect(redirectedUrl("https://formulario.defensorianinez.gob.cl/?session=closed"));
    }
    @Test void integrationRejectsEvenAValidTechnicalToken() throws Exception {
        for(String path:new String[]{"/integracion/v3/formulario-completo/entregas/reclamar","/integracion/v3/formulario-completo/entregas/00000000-0000-0000-0000-000000000001/confirmacion"})
            mvc.perform(post(path).secure(true).with(request -> { request.setScheme("https"); return request; }).header("Authorization","Bearer test-only-integration-token-32-bytes"))
                .andExpect(status().isServiceUnavailable());
    }
}
