package cl.nexbyte.siger.robustez;
import cl.nexbyte.siger.nna.HabilitacionNna;
import cl.nexbyte.siger.identidad.*;
import java.nio.file.*;import java.security.*;import java.time.*;import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;import org.springframework.context.annotation.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"siger.claveunica.client-id=test","siger.claveunica.client-secret=test"})
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Import(NnaHttpTest.Fixture.class)
class NnaHttpTest {
    @Autowired MockMvc mvc;@Autowired BaseJdbc db;@Autowired RecepcionJdbc store;@Autowired JsonMapper mapper;
    @MockitoBean HabilitacionNna gate;
    static final KeyPair KEYS=keys();static KeyPair keys(){try{return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();}catch(Exception e){throw new IllegalStateException(e);}}
    @TestConfiguration static class Fixture {
        @Bean(destroyMethod="close") BaseJdbc testDb()throws Exception{return TestDatabase.open(Files.createTempDirectory("nna-test-"));}
        @Bean @Primary RecepcionJdbc testStore(BaseJdbc db){
            return new RecepcionJdbc(db,JsonMapper.builder().build(),"laboratorio-v1",true,"test",KEYS.getPrivate(),Map.of("test",KEYS.getPublic()),Clock.systemUTC(),300000);
        }
    }
    @BeforeEach void reset(){when(gate.habilitado()).thenReturn(true);db.sql.update("DELETE FROM public_delivery");var c=new ControlesOperacion(db);c.cambiar(c.estado().revision(),true,true,"NORMAL","PRUEBA_LOCAL");}
    String body(){return "{\"datos\":{\"primerNombre\":\"María José\",\"primerApellido\":\"Del Río\",\"region\":\"Región sintética\",\"direccion\":\" Calle  sintética \"},\"mensaje\":\"Relato  sintético\\ncon tildes\",\"avisoPrivacidadLeido\":true,\"avisoVersion\":\"laboratorio-v1\"}";}
    @Test void ingresoSinCUniRunConCSRFProcedenciaYCustodiaDeSesion()throws Exception{
        var session=new MockHttpSession();var id=UUID.randomUUID();
        for(int i=0;i<2;i++)mvc.perform(post("/api/nna/comunicaciones").secure(true).session(session).with(csrf()).header("Idempotency-Key",id).contentType("application/json").content(body()))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.estado").value("PENDIENTE_REGISTRO_SIGER")).andExpect(jsonPath("$.comprobante").doesNotExist());
        var json=db.sql.queryForObject("SELECT body FROM public_delivery WHERE id=?",String.class,id.toString());
        var d=mapper.readValue(json,EntregaVerificada.class);d.validar(Map.of("test",KEYS.getPublic()),mapper);
        assertThat(d.comunicacion().identidad()).isNull();assertThat(d.comunicacion().declaracionNna().runNumero()).isNull();
        assertThat(d.comunicacion().declaracionNna().direccion()).isEqualTo(" Calle  sintética ");
        assertThat(d.comunicacion().mensaje()).isEqualTo("Relato  sintético\ncon tildes");
        assertThat(d.procedencia().values()).allMatch(a->a.fuente().equals("CIUDADANO")&&a.nivel().equals("DECLARADO_NO_CONTRASTADO"));
        assertThat(db.sql.queryForObject("SELECT owner_run FROM public_delivery WHERE id=?",String.class,id.toString())).isNull();
        assertThat(db.sql.queryForObject("SELECT COUNT(*) FROM public_delivery",Long.class)).isEqualTo(1);
        mvc.perform(get("/api/nna/comunicaciones/{id}",id).secure(true).session(session)).andExpect(status().isOk());
        for(String suffix:List.of("","/comprobante"))mvc.perform(get("/api/nna/comunicaciones/"+id+suffix).secure(true).session(new MockHttpSession())).andExpect(status().isNotFound());
        mvc.perform(post("/api/nna/comunicaciones").secure(true).session(new MockHttpSession()).with(csrf()).header("Idempotency-Key",id).contentType("application/json").content(body())).andExpect(status().isConflict());
        mvc.perform(get("/api/nna/comunicaciones/{id}/comprobante",id).secure(true).session(session)).andExpect(status().isConflict());
    }
    @Test void sinCSRFHTTPcerradoCuotasYTamanosNoAceptanEnvios()throws Exception{
        mvc.perform(post("/api/nna/comunicaciones").secure(true).contentType("application/json").content(body()).header("Idempotency-Key",UUID.randomUUID())).andExpect(status().isForbidden());
        mvc.perform(post("/api/nna/comunicaciones").with(csrf()).contentType("application/json").content(body()).header("Idempotency-Key",UUID.randomUUID())).andExpect(status().isForbidden());
        mvc.perform(post("/api/nna/comunicaciones").secure(true).session(new MockHttpSession()).with(csrf()).contentType("application/json").content("x".repeat(9000)).header("Idempotency-Key",UUID.randomUUID())).andExpect(status().isPayloadTooLarge());
        var c=new ControlesOperacion(db);c.cambiar(c.estado().revision(),false,false,"SOLO_CONSULTA","INCIDENTE");
        mvc.perform(post("/api/nna/comunicaciones").secure(true).session(new MockHttpSession()).with(csrf()).contentType("application/json").content(body()).header("Idempotency-Key",UUID.randomUUID())).andExpect(status().isServiceUnavailable());
        when(gate.habilitado()).thenReturn(false);
        mvc.perform(post("/api/nna/comunicaciones").secure(true).session(new MockHttpSession()).with(csrf()).contentType("application/json").content(body()).header("Idempotency-Key",UUID.randomUUID())).andExpect(status().isNotFound());
        assertThat(db.sql.queryForObject("SELECT COUNT(*) FROM public_delivery",Long.class)).isZero();
    }
    @Test void validacionServerSideNoPermiteRunInvalidoAtributosElevadosNiDuplicadosJson()throws Exception{
        for(var bad:List.of(body().replace("\"region\":\"Región sintética\"","\"region\":\"\""),body().replace("\"datos\":{","\"datos\":{\"runNumero\":\"44444444\",\"runDv\":\"5\","),body().replace("\"datos\":{","\"datos\":{\"fuente\":\"CLAVE_UNICA_USERINFO\","),body().replace("\"mensaje\":","\"mensaje\":\"uno\",\"mensaje\":")))
            mvc.perform(post("/api/nna/comunicaciones").secure(true).session(new MockHttpSession()).with(csrf()).contentType("application/json").content(bad).header("Idempotency-Key",UUID.randomUUID())).andExpect(status().isBadRequest());
        assertThat(db.sql.queryForObject("SELECT COUNT(*) FROM public_delivery",Long.class)).isZero();
    }
    @Test void versionRetiradaNoReservaYAdultoNoAceptaSesionAnonima()throws Exception{
        var retired=new IntegracionV2Controller(store);
        assertThatThrownBy(()->retired.reclamar(new IntegracionV2Controller.Solicitud(20))).isInstanceOf(FalloTecnico.class).extracting(e->((FalloTecnico)e).status).isEqualTo(410);
        mvc.perform(post("/api/comunicaciones").secure(true).session(new MockHttpSession()).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(body())).andExpect(status().isUnauthorized());
    }
    @Test void rutasV2RetiradasDevuelven410SinReservarUnaEntrega()throws Exception{
        var id=UUID.randomUUID();var data=new cl.nexbyte.siger.contrato.DeclaracionNna("Sintético",null,"Prueba",null,null,null,null,null,"R");
        store.recibirNna(id,data,"Solo pruebas","laboratorio-v1","a".repeat(64));
        var legacy=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new IntegracionV2Controller(store)).setControllerAdvice(new ErroresTecnicos()).build();
        legacy.perform(post("/integracion/v2/entregas/reclamar").contentType("application/json").content("{\"limite\":20}")).andExpect(status().isGone());
        legacy.perform(post("/integracion/v2/entregas/"+id+"/fallo").contentType("application/json").content("{\"reserva\":\""+UUID.randomUUID()+"\",\"codigo\":\"TRANSITORIO\"}")).andExpect(status().isGone());
        assertThat(db.sql.queryForObject("SELECT state FROM public_delivery WHERE id=?",String.class,id.toString())).isEqualTo("PENDIENTE");
        assertThat(db.sql.queryForObject("SELECT attempts FROM public_delivery WHERE id=?",Integer.class,id.toString())).isZero();
    }
    @Test void abusoTemporalRechazaSolicitudesAunSinPersistencia()throws Exception{
        var session=new MockHttpSession();
        for(int i=0;i<6;i++)mvc.perform(post("/api/nna/comunicaciones").secure(true).session(session).with(q->{q.setRemoteAddr("127.0.0.22");return q;}).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(body())).andExpect(status().isForbidden());
        mvc.perform(post("/api/nna/comunicaciones").secure(true).session(session).with(q->{q.setRemoteAddr("127.0.0.22");return q;}).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(body())).andExpect(status().isTooManyRequests());
        assertThat(db.sql.queryForObject("SELECT COUNT(*) FROM public_delivery",Long.class)).isZero();
    }

 @Test void adultoConservaIdentidadDeSesionYNoExponeIngresosEntreSesiones()throws Exception{
  var identity=new IdentidadVerificada("44444444-4","Persona","Sintética");
  var auth=org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(identity,null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CIUDADANO")));auth.setDetails(EvidenciaAutenticacion.de(identity));
  var session=new MockHttpSession();var id=UUID.randomUUID();var body="{\"mensaje\":\"Adulto sintético\",\"avisoPrivacidadLeido\":true,\"avisoVersion\":\"laboratorio-v1\"}";
  mvc.perform(post("/api/comunicaciones").secure(true).session(session).with(authentication(auth)).with(csrf()).header("Idempotency-Key",id).contentType("application/json").content(body)).andExpect(status().isAccepted());
  mvc.perform(get("/api/comunicaciones/{id}",id).session(session).with(authentication(auth))).andExpect(status().isOk());
  mvc.perform(get("/api/comunicaciones/{id}",id).session(new MockHttpSession()).with(authentication(auth))).andExpect(status().isNotFound());
  mvc.perform(post("/api/comunicaciones").secure(true).session(session).with(authentication(auth)).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(body)).andExpect(status().isForbidden());
 }
}
