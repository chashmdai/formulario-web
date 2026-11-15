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
        assertEquals(AccesoTemporal.Result.ACCEPTED,restart.authenticate(PASSWORD,"192.0.2.11",new MockHttpSession()));
        String state=Files.readString(dir.resolve("limits.json"));assertFalse(state.contains("192.0.2.10"));assertFalse(state.contains(PASSWORD));
        clock.at=clock.at.plusSeconds(901);assertEquals(AccesoTemporal.Result.ACCEPTED,restart.authenticate(PASSWORD,"192.0.2.10",s));
    }
    @Test void formatoAntiguoConCienFallosGlobalesNoBloqueaOtroOrigen()throws Exception {
        var oldState=java.util.Map.of("windowEnds",clock.at.plusSeconds(900).getEpochSecond(),
            "global",100,"clients",java.util.Map.of("a".repeat(64),5));
        Files.writeString(dir.resolve("limits.json"),JsonMapper.builder().build().writeValueAsString(oldState));
        var restart=new AccesoTemporal(env,clock,()->false,JsonMapper.builder().build());
        assertEquals(AccesoTemporal.Mode.PASSWORD,restart.mode());
        assertEquals(AccesoTemporal.Result.ACCEPTED,restart.authenticate(PASSWORD,"192.0.2.11",new MockHttpSession()));
        assertEquals(AccesoTemporal.Result.REJECTED,restart.authenticate("wrong","192.0.2.11",new MockHttpSession()));
        var state=JsonMapper.builder().build().readTree(Files.readString(dir.resolve("limits.json")));
        assertEquals(101,state.path("global").asInt());
        assertEquals(5,state.path("clients").path("a".repeat(64)).asInt());
    }
    @Test void topeGlobalConfigurableFrenaFallosDistribuidosYPersiste() {
        env.setProperty("formulario.acceso-temporal.max-fallos-globales","50");
        access=new AccesoTemporal(env,clock,()->false,JsonMapper.builder().build());
        for(int i=0;i<50;i++)
            assertEquals(AccesoTemporal.Result.REJECTED,access.authenticate("wrong","192.0.2."+i,new MockHttpSession()));
        var restart=new AccesoTemporal(env,clock,()->false,JsonMapper.builder().build());
        assertEquals(AccesoTemporal.Result.LIMITED,restart.authenticate(PASSWORD,"198.51.100.1",new MockHttpSession()));
        clock.at=clock.at.plusSeconds(900);
        assertEquals(AccesoTemporal.Result.ACCEPTED,restart.authenticate(PASSWORD,"198.51.100.1",new MockHttpSession()));
    }
    @Test void topeGlobalPredeterminadoEsQuinientos()throws Exception {
        var state=java.util.Map.of("windowEnds",clock.at.plusSeconds(900).getEpochSecond(),
            "global",499,"clients",java.util.Map.of("a".repeat(64),5));
        Files.writeString(dir.resolve("limits.json"),JsonMapper.builder().build().writeValueAsString(state));
        assertEquals(AccesoTemporal.Result.REJECTED,access.authenticate("wrong","192.0.2.10",new MockHttpSession()));
        assertEquals(AccesoTemporal.Result.LIMITED,access.authenticate(PASSWORD,"192.0.2.11",new MockHttpSession()));
    }
    @Test void configuracionNoPermiteTopeGlobalPequeñoNiMapaSinCota() {
        for(String value:java.util.List.of("0","5","49","1001")) {
            env.setProperty("formulario.acceso-temporal.max-fallos-globales",value);
            assertThrows(IllegalArgumentException.class,()->new AccesoTemporal(env,clock,()->false,JsonMapper.builder().build()));
        }
    }
    @Test void estadoTieneTopeDeMilOrigenesYEliminaVentanaVencida()throws Exception {
        env.setProperty("formulario.acceso-temporal.max-fallos-globales","1000");
        access=new AccesoTemporal(env,clock,()->false,JsonMapper.builder().build());
        for(int i=0;i<1000;i++)
            assertEquals(AccesoTemporal.Result.REJECTED,access.authenticate("wrong","2001:db8:"+i+"::/64",new MockHttpSession()));
        var mapper=JsonMapper.builder().build();
        String stored=Files.readString(dir.resolve("limits.json"));
        assertEquals(1000,mapper.readTree(stored).path("clients").size());
        assertEquals(AccesoTemporal.Result.LIMITED,access.authenticate("wrong","198.51.100.1",new MockHttpSession()));
        assertEquals(stored,Files.readString(dir.resolve("limits.json")));
        clock.at=clock.at.plusSeconds(900);
        assertEquals(AccesoTemporal.Result.REJECTED,access.authenticate("wrong","198.51.100.1",new MockHttpSession()));
        var fresh=mapper.readTree(Files.readString(dir.resolve("limits.json")));
        assertEquals(1,fresh.path("global").asInt());
        assertEquals(1,fresh.path("clients").size());
    }
    @Test void validaHashPbkdf2V58ConParametrosDelScriptUbuntu()throws Exception {
        byte[] salt=new byte[16];
        java.util.Arrays.fill(salt,(byte)0x42); // Sal y contraseña exclusivamente sintéticas.
        var spec=new javax.crypto.spec.PBEKeySpec(PASSWORD.toCharArray(),salt,310000,256);
        byte[] derived=javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        String hash=AccesoTemporal.PREFIX+java.util.HexFormat.of().formatHex(salt)+java.util.HexFormat.of().formatHex(derived);
        Files.writeString(dir.resolve("hash"),hash);
        assertEquals(AccesoTemporal.Result.ACCEPTED,access.authenticate(PASSWORD,"192.0.2.10",new MockHttpSession()));
    }
    @Test void ausenciaOEstadoCorruptoFallaCerrado()throws Exception {
        Files.writeString(dir.resolve("limits.json"),"CORRUPT");
        var restart=new AccesoTemporal(env,clock,()->false,JsonMapper.builder().build());
        assertEquals(AccesoTemporal.Mode.PASSWORD,restart.mode()); // El estado nunca impide arrancar.
        assertEquals(AccesoTemporal.Result.UNAVAILABLE,restart.authenticate(PASSWORD,"127.0.0.1",new MockHttpSession()));
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
        clock.at=access.expires();var filter=new AccesoTemporalFilter(access,new cl.nexbyte.siger.config.OrigenCliente(env));
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
