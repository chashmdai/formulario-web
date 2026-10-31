package cl.nexbyte.siger.identidad;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Solo configuración y endpoints locales; nunca contacta Gobierno Digital. */
@SpringBootTest(properties = {
    "siger.claveunica.environment=PRODUCTION", "siger.claveunica.client-id=test-client",
    "siger.claveunica.client-secret=test-secret", "siger.claveunica.callback-approved=true",
    "siger.claveunica.public-origin=https://formulario.defensorianinez.gob.cl",
    "siger.claveunica.redirect-uri=https://formulario.defensorianinez.gob.cl/redirect",
    "siger.claveunica.logout-uri=https://formulario.defensorianinez.gob.cl/logout",
    "server.servlet.session.cookie.secure=true"
})
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class ClaveUnicaDominioInstitucionalTest {
    static final String ORIGIN = "https://formulario.defensorianinez.gob.cl";
    @Autowired MockMvc mvc;
    @Autowired ClaveUnicaProperties configured;

    ClaveUnicaProperties properties(String origin) {
        var p = new ClaveUnicaProperties();
        p.setEnvironment(ClaveUnicaProperties.Stage.PRODUCTION);
        p.setClientId("test-client"); p.setClientSecret("test-secret");
        p.setCallbackApproved(true);
        p.setPublicOrigin(URI.create(origin));
        p.setRedirectUri(URI.create(origin + "/redirect"));
        p.setLogoutUri(URI.create(origin + "/logout"));
        ReflectionTestUtils.setField(p, "springEnvironment",
                new MockEnvironment().withProperty("server.servlet.session.cookie.secure", "true"));
        return p;
    }

    @Test void admiteDominioCorregidoYConservaDominiosHistoricos() {
        properties(ORIGIN).validate();
        properties("https://claveunica.formularioadulto.defensorianinez.gob.cl").validate();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://formulario.defensorianinez.gob.cl.example.org",
        "https://formulario.defensorianinez.cl", "https://otro.defensorianinez.cl", "https://defensorianinez.cl",
        "http://formulario.defensorianinez.gob.cl", "https://127.0.0.1",
        "https://formulario-defensorianinez.cl"})
    void rechazaDominiosParecidosYTransporteInseguro(String origin) {
        assertThatThrownBy(() -> properties(origin).validate()).isInstanceOf(IllegalStateException.class);
    }

    @Test void conservaValidacionesDeCookieCallbackYRutas() {
        var p = properties(ORIGIN);
        ReflectionTestUtils.setField(p, "springEnvironment",
                new MockEnvironment().withProperty("server.servlet.session.cookie.secure", "false"));
        assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
        p = properties(ORIGIN); p.setCallbackApproved(false);
        assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
        p = properties(ORIGIN); p.setRedirectUri(URI.create(ORIGIN + "/redirect?next=otro"));
        assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test void inicioOidcUsaElCallbackNuevoConStateYNonce() throws Exception {
        var response = mvc.perform(get(URI.create(ORIGIN + "/oauth2/authorization/claveunica")).secure(true))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse();
        var parameters = java.util.Arrays.stream(URI.create(response.getRedirectedUrl()).getRawQuery().split("&"))
                .map(pair -> pair.split("=", 2)).collect(java.util.stream.Collectors.toMap(
                    pair -> java.net.URLDecoder.decode(pair[0], java.nio.charset.StandardCharsets.UTF_8),
                    pair -> java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(parameters).containsEntry("redirect_uri", ORIGIN + "/redirect")
                .containsEntry("client_id", "test-client");
        assertThat(parameters.get("state")).hasSizeGreaterThanOrEqualTo(30);
        assertThat(parameters.get("nonce")).isNotBlank();
    }

    @Test void retornoYFallosConservanNuevoOrigenYNoCreanIdentidad() throws Exception {
        mvc.perform(get(URI.create(ORIGIN + "/logout")).secure(true)).andExpect(status().isSeeOther())
                .andExpect(redirectedUrl(ORIGIN + "/?session=closed"));
        mvc.perform(get(URI.create(ORIGIN + "/redirect")).secure(true).param("code", "synthetic").param("state", "incorrect"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(ORIGIN + "/?auth=failed"));
        assertThat(ProviderLogout.url(configured)).endsWith(
                "redirect=https%3A%2F%2Fformulario.defensorianinez.gob.cl%2Flogout");
    }
}
