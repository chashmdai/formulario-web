package cl.nexbyte.siger.identidad;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** All provider stubs live exclusively in tests. Never uses institutional credentials. */
@SpringBootTest(properties = {
        "siger.claveunica.client-id=test-client", "siger.claveunica.client-secret=test-secret",
        "siger.claveunica.callback-approved=true", "siger.claveunica.public-origin=http://localhost:5173"
})
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class ClaveUnicaFlowTest {
    static final RSAKey KEY;
    static final HttpServer PROVIDER;
    static final AtomicReference<String> NONCE = new AtomicReference<>();
    static final AtomicReference<String> MODE = new AtomicReference<>("ok");
    static final AtomicReference<String> TOKEN_BODY = new AtomicReference<>();
    static final AtomicReference<String> USERINFO_METHOD = new AtomicReference<>();
    static final AtomicReference<String> USERINFO_AUTH = new AtomicReference<>();
    static final AtomicInteger TOKEN_CALLS = new AtomicInteger();
    static final AtomicInteger USERINFO_CALLS = new AtomicInteger();
    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
            PROVIDER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            PROVIDER.createContext("/jwks", exchange -> respond(exchange, 200, "{\"keys\":[" + KEY.toPublicJWK().toJSONString() + "]}"));
            PROVIDER.createContext("/token", exchange -> {
                TOKEN_CALLS.incrementAndGet();
                TOKEN_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                if (!exchange.getRequestMethod().equals("POST")) { respond(exchange, 405, "{}"); return; }
                if (MODE.get().equals("token-error")) { respond(exchange, 400, "{\"error\":\"invalid_grant\"}"); return; }
                try {
                    var claims = new JWTClaimsSet.Builder().issuer(MODE.get().equals("wrong-issuer") ? "https://other.example" : SecurityConfiguration.ISSUER).subject("subject-is-not-run")
                            .audience(MODE.get().equals("wrong-audience") ? "other-client" : "test-client").issueTime(new Date())
                            .expirationTime(Date.from(Instant.now().plusSeconds(MODE.get().equals("expired-token") ? -600 : 300)))
                            .claim("nonce", MODE.get().equals("wrong-nonce") ? "wrong" : NONCE.get()).build();
                    var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID("test-key").build(), claims);
                    var signingKey = MODE.get().equals("invalid-signature") ? new RSAKeyGenerator(2048).generate() : KEY;
                    jwt.sign(new RSASSASigner(signingKey));
                    respond(exchange, 200, "{\"access_token\":\"test-access-token\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"id_token\":\"" + jwt.serialize() + "\"}");
                } catch (Exception e) { respond(exchange, 500, "{}"); }
            });
            PROVIDER.createContext("/userinfo", exchange -> {
                USERINFO_CALLS.incrementAndGet();
                USERINFO_METHOD.set(exchange.getRequestMethod()); USERINFO_AUTH.set(exchange.getRequestHeaders().getFirst("Authorization"));
                if (MODE.get().equals("userinfo-error")) { respond(exchange, 503, "{}"); return; }
                String subject = MODE.get().equals("wrong-sub") ? "different" : "subject-is-not-run";
                String identity = MODE.get().equals("missing-run") ? "" : "\"RolUnico\":{\"numero\":44444444,\"DV\":\"4\",\"tipo\":\"RUN\"},";
                respond(exchange, 200, "{\"sub\":\"" + subject + "\"," + identity
                        + "\"name\":{\"nombres\":[\"María\",\"Carmen\"],\"apellidos\":[\"Del Río\",\"González\"]}}");
            });
            PROVIDER.start();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    static void respond(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
    }
    @AfterAll static void stopProvider() { PROVIDER.stop(0); }
    @BeforeEach void reset() { MODE.set("ok"); TOKEN_CALLS.set(0); USERINFO_CALLS.set(0); }

    @TestConfiguration
    static class TestProvider {
        @Bean @Primary ClientRegistrationRepository testRegistrations(ClaveUnicaProperties properties) {
            var real = new SecurityConfiguration().registrations(properties).findByRegistrationId("claveunica");
            String base = "http://127.0.0.1:" + PROVIDER.getAddress().getPort();
            var registration = ClientRegistration.withClientRegistration(real).tokenUri(base + "/token")
                    .userInfoUri(base + "/userinfo").jwkSetUri(base + "/jwks").build();
            return new InMemoryClientRegistrationRepository(registration);
        }
    }
    @Autowired MockMvc mvc;
    @Autowired ClaveUnicaProperties properties;
    record Login(MockHttpSession session, String state) { }
    Login initiate() throws Exception {
        var result = mvc.perform(get("/oauth2/authorization/claveunica").secure(true)).andExpect(status().is3xxRedirection()).andReturn();
        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).startsWith(SecurityConfiguration.AUTHORIZE).contains("response_type=code");
        var parameters = query(URI.create(location).getRawQuery());
        assertThat(parameters.get("scope")).isEqualTo("openid run name");
        assertThat(parameters.get("redirect_uri")).isEqualTo("https://claveunica.formularioadulto.ddn.gob.cl/redirect");
        assertThat(parameters.get("state")).hasSizeGreaterThanOrEqualTo(30);
        assertThat(parameters).doesNotContainKeys("code_challenge", "code_challenge_method");
        NONCE.set(parameters.get("nonce"));
        return new Login((MockHttpSession) result.getRequest().getSession(false), parameters.get("state"));
    }
    MvcResult callback(Login login) throws Exception {
        return mvc.perform(get("/redirect").secure(true).session(login.session()).param("code", "test-code").param("state", login.state()))
                .andExpect(status().is3xxRedirection()).andReturn();
    }
    MockHttpSession authenticated() throws Exception {
        Login login = initiate(); var result = callback(login);
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:5173");
        return (MockHttpSession) result.getRequest().getSession(false);
    }
    static Map<String, String> query(String text) {
        return java.util.Arrays.stream(text.split("&")).map(p -> p.split("=", 2)).collect(java.util.stream.Collectors.toMap(
                p -> URLDecoder.decode(p[0], StandardCharsets.UTF_8), p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8)));
    }

    @Test void fullFlowValidatesIdentityAndKeepsOnlyIdentityInSession() throws Exception {
        Login login = initiate(); String oldId = login.session().getId();
        var result = callback(login);
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("http://localhost:5173");
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session.getId()).isNotEqualTo(oldId);
        Map<String, String> body = query(TOKEN_BODY.get());
        assertThat(body).containsEntry("state", login.state()).containsEntry("client_id", "test-client")
                .containsEntry("client_secret", "test-secret").containsEntry("grant_type", "authorization_code")
                .containsEntry("code", "test-code").containsEntry("redirect_uri", "https://claveunica.formularioadulto.ddn.gob.cl/redirect");
        assertThat(USERINFO_METHOD.get()).isEqualTo("POST");
        assertThat(USERINFO_AUTH.get()).isEqualTo("Bearer test-access-token");
        mvc.perform(get("/api/session").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true)).andExpect(jsonPath("$.identity.run").value("44444444-4"))
                .andExpect(jsonPath("$.identity.nombresComponentes[0]").value("María")).andExpect(jsonPath("$.identity.nombresComponentes[1]").value("Carmen")).andExpect(jsonPath("$.identity.apellidosComponentes[0]").value("Del Río")).andExpect(jsonPath("$.identity.nombres").value("María Carmen")).andExpect(jsonPath("$.identity.apellidos").value("Del Río González"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("token"))));
        var context = (SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(context.getAuthentication().getPrincipal()).isInstanceOf(IdentidadVerificada.class);
        assertThat(context.getAuthentication().getCredentials()).isNull();
        assertThat(context.getAuthentication().getAuthorities()).allMatch(a -> a.getClass().equals(org.springframework.security.core.authority.SimpleGrantedAuthority.class));
        assertThat(java.util.Collections.list(session.getAttributeNames())).doesNotContain("SPRING_SECURITY_SAVED_REQUEST", "org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository.AUTHORIZED_CLIENTS");
    }
    @Test void stateIsDynamic() throws Exception { assertThat(initiate().state()).isNotEqualTo(initiate().state()); }
    @Test void wrongStateAndMissingSessionNeverExchangeCode() throws Exception {
        Login login = initiate();
        mvc.perform(get("/redirect").session(login.session()).param("code", "test-code").param("state", "forged"))
                .andExpect(redirectedUrl("http://localhost:5173/?auth=failed"));
        mvc.perform(get("/redirect").param("code", "test-code").param("state", login.state()))
                .andExpect(redirectedUrl("http://localhost:5173/?auth=failed"));
        assertThat(TOKEN_CALLS.get()).isZero();
    }
    @Test void callbackIsSingleUse() throws Exception {
        Login login = initiate(); callback(login);
        mvc.perform(get("/redirect").session(login.session()).param("code", "test-code").param("state", login.state()))
                .andExpect(status().isConflict());
        assertThat(login.session().isInvalid()).isFalse();
        assertThat(TOKEN_CALLS.get()).isEqualTo(1);
    }
    @Test void forgedCallbackCannotLogOutAnAlreadyAuthenticatedCitizen() throws Exception {
        var session = authenticated();
        mvc.perform(get("/redirect").session(session).param("code", "forged").param("state", "forged"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/session").session(session)).andExpect(jsonPath("$.authenticated").value(true));
        assertThat(TOKEN_CALLS.get()).isEqualTo(1);
    }
    @Test void providerErrorsAndInvalidTokensNeverCreateAnAuthenticatedSession() throws Exception {
        for (String mode : new String[]{"token-error", "userinfo-error", "wrong-sub", "missing-run", "invalid-signature", "expired-token", "wrong-nonce", "wrong-issuer", "wrong-audience"}) {
            MODE.set(mode); Login login = initiate();
            var result = callback(login);
            assertThat(result.getResponse().getRedirectedUrl()).as(mode).startsWith(ProviderLogout.ENDPOINT);
            assertThat(login.session().isInvalid()).as(mode).isTrue();
        }
    }
    @Test void communicationRequiresSessionAndCsrfAndRejectsForgedIdentity() throws Exception {
        mvc.perform(post("/api/comunicaciones/preview").with(csrf()).contentType("application/json").content("{\"mensaje\":\"hola\"}"))
                .andExpect(status().isUnauthorized());
        MockHttpSession session = authenticated();
        mvc.perform(post("/api/comunicaciones/preview").session(session).contentType("application/json").content("{\"mensaje\":\"hola\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/comunicaciones/preview").session(session).with(csrf()).contentType("application/json")
                .content("{\"mensaje\":\"hola\",\"run\":\"99999999-9\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/comunicaciones/preview").session(session).with(csrf()).contentType("application/json").content("{\"mensaje\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/comunicaciones/preview").session(session).with(csrf()).contentType("application/json").content("{\"mensaje\":\"Hola\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.identidad.run").value("44444444-4")).andExpect(jsonPath("$.almacenada").value(false));
    }
    @Test void csrfEndpointWorksAndLogoutInvalidatesLocalSessionThenNavigatesToProvider() throws Exception {
        mvc.perform(get("/api/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        MockHttpSession session = authenticated();
        mvc.perform(post("/auth/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/auth/logout").session(session).with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(ProviderLogout.url(properties)));
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/logout")).andExpect(status().isSeeOther()).andExpect(redirectedUrl("http://localhost:5173/?session=closed"));
    }
    @Test void implicitLogoutIsOnlyAvailableAfterLocalSessionExpires() throws Exception {
        mvc.perform(get("/auth/session-expired")).andExpect(status().isSeeOther()).andExpect(redirectedUrl(ProviderLogout.url(properties)));
        mvc.perform(get("/auth/session-expired").session(authenticated())).andExpect(status().isConflict());
        mvc.perform(get("/logout").session(authenticated())).andExpect(status().isConflict());
    }
    @Test void actualCsrfTokenSupportsHtmlLogoutForm() throws Exception {
        var session = authenticated();
        var csrfResult = mvc.perform(get("/api/csrf").session(session)).andExpect(status().isOk()).andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.token");
        String parameter = com.jayway.jsonpath.JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.parameterName");
        mvc.perform(post("/auth/logout").session(session).param(parameter, token)).andExpect(redirectedUrl(ProviderLogout.url(properties)));
        assertThat(session.isInvalid()).isTrue();
    }
    @Test void sessionResponseHasSecurityHeaders() throws Exception {
        mvc.perform(get("/api/session").secure(true)).andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Strict-Transport-Security"));
    }
}
