package cl.nexbyte.siger.identidad;

import java.nio.file.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class AccesoTemporalTest {
    @TempDir Path dir;
    static final String PASSWORD="SYNTHETIC-ACCESS-ONLY-2026-TEST";
    MockEnvironment env;MutableClock clock;AccesoTemporal access;
    static class MutableClock extends Clock {
        Instant at=Instant.parse("2026-11-03T20:00:00Z");
        public Instant instant(){return at;}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}
    }
    @BeforeEach void setup()throws Exception {
        env=new MockEnvironment().withProperty("formulario.acceso-temporal.habilitado","true")
            .withProperty("formulario.acceso-temporal.hash-path",dir.resolve("hash").toString())
            .withProperty("formulario.acceso-temporal.limites-path",dir.resolve("limits.json").toString())
            .withProperty("formulario.acceso-temporal.apertura-path",dir.resolve("go.json").toString());
        Files.writeString(dir.resolve("hash"),AccesoTemporal.PREFIX+Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(PASSWORD));
        clock=new MutableClock();access=new AccesoTemporal(env,clock,()->false,JsonMapper.builder().build());
    }
    @Test void fechaExactaSantiagoYLimiteInclusivo() {
        assertEquals(Instant.parse("2026-12-15T03:00:00Z"),access.expires());
        clock.at=access.expires().minusNanos(1);assertEquals(AccesoTemporal.Mode.PASSWORD,access.mode());
        clock.at=access.expires();assertEquals(AccesoTemporal.Mode.UNAVAILABLE,access.mode());
    }
    @Test void contraseñaIncorrectaCorrectaYSesionIndependiente() {
        var s=new MockHttpSession();assertFalse(access.granted(s));
        assertEquals(AccesoTemporal.Result.REJECTED,access.authenticate("WRONG-SYNTHETIC-ACCESS", "127.0.0.1",s));
        assertEquals(AccesoTemporal.Result.ACCEPTED,access.authenticate(PASSWORD,"127.0.0.1",s));assertTrue(access.granted(s));
        assertFalse(access.granted(new MockHttpSession()));assertNull(s.getAttribute("SPRING_SECURITY_CONTEXT"));
    }
    @Test void caducaSesionAbsolutaSinReiniciarServidor() {
        var s=new MockHttpSession();access.authenticate(PASSWORD,"127.0.0.1",s);clock.at=clock.at.plusSeconds(8*3600);
        assertFalse(access.granted(s));
    }
    @Test void rotacionEnArchivoRevocaSesionSinRecompilar()throws Exception {
        var s=new MockHttpSession();access.authenticate(PASSWORD,"127.0.0.1",s);
        Files.writeString(dir.resolve("hash"),AccesoTemporal.PREFIX+Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode("NEW-SYNTHETIC-ACCESS-ONLY"));
        assertFalse(access.granted(s));assertEquals(AccesoTemporal.Result.REJECTED,access.authenticate(PASSWORD,"127.0.0.1",s));
        assertEquals(AccesoTemporal.Result.ACCEPTED,access.authenticate("NEW-SYNTHETIC-ACCESS-ONLY","127.0.0.1",s));
    }
    @Test void limitesPersistenEntreInstanciasYNoGuardanIpNiContraseña()throws Exception {
        var s=new MockHttpSession();for(int i=0;i<5;i++)assertEquals(AccesoTemporal.Result.REJECTED,access.authenticate("WRONG-SYNTHETIC-ACCESS","192.0.2.10",s));
        var restart=new AccesoTemporal(env,clock,()->false,JsonMapper.builder().build());
        assertEquals(AccesoTemporal.Result.LIMITED,restart.authenticate(PASSWORD,"192.0.2.10",s));
        String state=Files.readString(dir.resolve("limits.json"));assertFalse(state.contains("192.0.2.10"));assertFalse(state.contains(PASSWORD));
        clock.at=clock.at.plusSeconds(901);assertEquals(AccesoTemporal.Result.ACCEPTED,restart.authenticate(PASSWORD,"192.0.2.10",s));
    }
    @Test void ausenciaOEstadoCorruptoFallaCerrado()throws Exception {
        Files.writeString(dir.resolve("limits.json"),"CORRUPT");assertEquals(AccesoTemporal.Result.UNAVAILABLE,access.authenticate(PASSWORD,"127.0.0.1",new MockHttpSession()));
        Files.delete(dir.resolve("hash"));assertEquals(AccesoTemporal.Mode.UNAVAILABLE,access.mode());
    }
    @Test void expiracionSinGoNiSesionesPreviasAbreRecepcion() {
        var s=new MockHttpSession();access.authenticate(PASSWORD,"127.0.0.1",s);clock.at=access.expires();
        assertEquals(AccesoTemporal.Mode.UNAVAILABLE,access.mode());assertFalse(access.granted(s));
        assertEquals(AccesoTemporal.Result.UNAVAILABLE,access.authenticate(PASSWORD,"127.0.0.1",s));
    }
    @Test void goDocumentalNoSustituyeControlesInstitucionales()throws Exception {
        Files.writeString(dir.resolve("go.json"),"{\"authorized\":true,\"receiptSignerApproved\":true,\"windowsReady\":true,\"physicalE2eVerified\":true,\"noticeApproved\":true}");
        clock.at=access.expires();assertEquals(AccesoTemporal.Mode.UNAVAILABLE,access.mode());
        var approved=new AccesoTemporal(env,clock,()->true,JsonMapper.builder().build());assertEquals(AccesoTemporal.Mode.OPEN,approved.mode());
    }
    @Test void deshabilitadoConservaComportamientoAnterior() {
        env.setProperty("formulario.acceso-temporal.habilitado","false");clock.at=access.expires();
        assertEquals(AccesoTemporal.Mode.OPEN,new AccesoTemporal(env,clock,()->false,JsonMapper.builder().build()).mode());
    }
    @Test void caducidadContinuaMismoFormularioAutorizadoSinExigirE2eWindows()throws Exception {
        Files.writeString(dir.resolve("go.json"),"{\"schema\":\"RC1_PUBLICATION_AUTHORIZATION_V2\",\"authorized\":true,\"publicationAuthorized\":true,\"receptionAuthorized\":true,\"receiptSignerUseAuthorized\":true,\"existingNoticeUseAuthorized\":true,\"windowsReady\":false,\"physicalE2eVerified\":false}");
        var authorized=new AccesoTemporal(env,clock,()->true,JsonMapper.builder().build());
        clock.at=access.expires().minusNanos(1);assertEquals(AccesoTemporal.Mode.PASSWORD,authorized.mode());
        clock.at=access.expires();assertEquals(AccesoTemporal.Mode.OPEN,authorized.mode());
        assertEquals(AccesoTemporal.Mode.UNAVAILABLE,access.mode()); // Cierre operacional sigue prevaleciendo.
    }
    @Test void filtroExpiradoNoMuestraContraseñaNiEjecutaApiCiudadana()throws Exception {
        clock.at=access.expires();var filter=new AccesoTemporalFilter(access);
        for(String path:java.util.List.of("/","/api/formulario/recorridos")) {
            var q=new org.springframework.mock.web.MockHttpServletRequest("GET",path);q.setSecure(true);
            var r=new org.springframework.mock.web.MockHttpServletResponse();
            filter.doFilter(q,r,(request,response)->fail("No ejecutar recepción después de caducidad sin GO"));
            assertEquals(503,r.getStatus());assertFalse(r.getContentAsString().contains("type=\"password\""));
        }
    }
    @Test void estadoDeAccesoNuncaEscribeDentroDeCustodiaDeAdjuntos()throws Exception {
        Path store=dir.resolve("custody");
        env.setProperty("formulario.multimedia.directorio",store.toString());
        env.setProperty("formulario.acceso-temporal.limites-path",store.resolve("forbidden/limits.json").toString());
        assertEquals(AccesoTemporal.Result.UNAVAILABLE,access.authenticate(PASSWORD,"127.0.0.1",new MockHttpSession()));
        assertFalse(Files.exists(store));
        env.setProperty("formulario.acceso-temporal.limites-path",dir.resolve("separate/limits.json").toString());
        try(var custody=new cl.nexbyte.siger.multimedia.AlmacenPrivado(store)) {
            assertEquals(AccesoTemporal.Result.REJECTED,access.authenticate("WRONG-SYNTHETIC-PASSWORD","127.0.0.1",new MockHttpSession()));
        }
        try(var restarted=new cl.nexbyte.siger.multimedia.AlmacenPrivado(store)){assertTrue(Files.exists(dir.resolve("separate/limits.json")));}
    }
}
