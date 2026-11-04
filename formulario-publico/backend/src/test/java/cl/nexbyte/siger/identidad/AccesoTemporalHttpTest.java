package cl.nexbyte.siger.identidad;

import java.nio.file.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"siger.claveunica.habilitada=false","siger.claveunica.callback-approved=false",
    "formulario.acceso-temporal.habilitado=true","publico.integracion.habilitada=false","publico.integracion.preparada=true",
    "publico.integracion.token=synthetic-temporal-machine-token-2026"})
@AutoConfigureMockMvc
class AccesoTemporalHttpTest {
    static final String PASSWORD="SYNTHETIC-HTTP-ACCESS-ONLY-2026";
    static Path dir;
    static {try {dir=Files.createTempDirectory("synth-temporal-");Files.writeString(dir.resolve("hash"),AccesoTemporal.PREFIX+Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(PASSWORD));}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void props(DynamicPropertyRegistry p){p.add("formulario.acceso-temporal.hash-path",()->dir.resolve("hash").toString());p.add("formulario.acceso-temporal.limites-path",()->dir.resolve("limits").toString());}
    @TestConfiguration static class Fixed {
        @Bean @Primary AccesoTemporal fixedAccess(Environment e,JsonMapper m){return new AccesoTemporal(e,Clock.fixed(Instant.parse("2026-11-03T20:00:00Z"),ZoneOffset.UTC),()->false,m);}
    }
    @Autowired MockMvc mvc;
    @BeforeEach void reset()throws Exception{Files.deleteIfExists(dir.resolve("limits"));}
    MockHttpSession login()throws Exception {
        var session=new MockHttpSession();
        mvc.perform(post("/acceso-temporal").secure(true).session(session).with(csrf()).contentType("application/x-www-form-urlencoded").param("password",PASSWORD))
            .andExpect(status().isSeeOther()).andExpect(header().string("Location","/?vista=inicio"));return session;
    }
    @Test void interfazYApiProtegidasSinSesion()throws Exception {
        mvc.perform(get("/").secure(true)).andExpect(status().isSeeOther()).andExpect(header().string("Location","/acceso-temporal"));
        for(String path:java.util.List.of("/api/session","/api/csrf","/api/formulario/capacidades","/api/nna/sesion","/assets/unknown.js"))mvc.perform(get(path).secure(true)).andExpect(status().isUnauthorized());
    }
    @Test void pantallaInstitucionalSinCredencialesEnRespuesta()throws Exception {
        mvc.perform(get("/acceso-temporal").secure(true)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("type=\"password\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(PASSWORD))));
        mvc.perform(get("/acceso-temporal/estilo.css").secure(true)).andExpect(status().isOk());
        mvc.perform(get("/acceso-temporal/logo.svg").secure(true)).andExpect(status().isOk());
    }
    @Test void csrfYTransporteNoSeDebilitan()throws Exception {
        mvc.perform(post("/acceso-temporal").secure(true).contentType("application/x-www-form-urlencoded").param("password",PASSWORD)).andExpect(status().isForbidden());
        mvc.perform(post("/acceso-temporal").with(csrf()).contentType("application/x-www-form-urlencoded").param("password",PASSWORD)).andExpect(status().isForbidden());
    }
    @Test void incorrectaRechazadaYLimitadaSinReflejarValor()throws Exception {
        for(int i=0;i<5;i++)mvc.perform(post("/acceso-temporal").secure(true).with(csrf()).contentType("application/x-www-form-urlencoded").param("password","WRONG-SYNTHETIC-PASSWORD"))
            .andExpect(status().isUnauthorized()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("WRONG-SYNTHETIC-PASSWORD"))));
        mvc.perform(post("/acceso-temporal").secure(true).with(csrf()).contentType("application/x-www-form-urlencoded").param("password",PASSWORD)).andExpect(status().isTooManyRequests());
    }
    @Test void correctaNavegaSinAutenticarCiudadanoNiCompartirOtraSesion()throws Exception {
        var session=login();mvc.perform(get("/api/session").secure(true).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(false));
        mvc.perform(get("/api/session").secure(true).session(new MockHttpSession())).andExpect(status().isUnauthorized());
    }
    @Test void pullAckConservaBearerIndependienteSinContraseña()throws Exception {
        mvc.perform(get("/integracion/unknown").secure(true)).andExpect(status().isUnauthorized());
        mvc.perform(get("/integracion/unknown").secure(true).header("Authorization","Bearer synthetic-temporal-machine-token-2026")).andExpect(status().isNotFound());
    }
    @Test void saludMinimaNoExponeSessionNiFormulario()throws Exception {
        mvc.perform(get("/acceso-temporal/salud").secure(true)).andExpect(status().isOk()).andExpect(content().json("{\"status\":\"UP\"}"));
    }
}
