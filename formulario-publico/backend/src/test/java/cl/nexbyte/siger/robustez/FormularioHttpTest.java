package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.srcei.*;
import java.nio.file.Files;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"siger.claveunica.client-id=test","siger.claveunica.client-secret=test"})
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Import(FormularioHttpTest.Fixture.class)
class FormularioHttpTest {
    @Autowired MockMvc mvc;@Autowired BaseJdbc db;@Autowired JsonMapper mapper;
    static final AtomicInteger QUERIES=new AtomicInteger();
    @TestConfiguration static class Fixture {
        @Bean(destroyMethod="close") BaseJdbc formularioDb()throws Exception{return TestDatabase.open(Files.createTempDirectory("formulario-http-"));}
        @Bean RecorridosJdbc recorridosTest(BaseJdbc db,JsonMapper mapper){
            return new RecorridosJdbc(db,mapper,run->{QUERIES.incrementAndGet();var at=java.time.Instant.now();var ref=UUID.randomUUID();return new ResultadoSrcei(ResultadoSrcei.Estado.PARCIAL,
                Map.of("nombres",new ResultadoSrcei.Atributo("NOMBRE REGISTRAL SINTETICO",null,"SRCEI","CONTRASTADO_EN_FUENTE",at,ref)),at,ref);},run->true,Clock.systemUTC(),new byte[32],"ADULTO_LAB_1","NNA_LAB_1");
        }
    }
    @BeforeEach @AfterEach void limpiar(){db.sql.update("DELETE FROM citizen_identity_history");db.sql.update("DELETE FROM citizen_person");db.sql.update("DELETE FROM citizen_journey");db.sql.update("DELETE FROM citizen_abuse_bucket");QUERIES.set(0);}
    private String inicio(String tipo){return "{\"tipo\":\""+tipo+"\",\"aceptaCanalDigital\":true,\"avisoVersion\":\""+(tipo.equals("NNA")?"NNA":"ADULTO")+"_LAB_1\"}";}
    private String iniciar(MockHttpSession session,String tipo)throws Exception {
        var result=mvc.perform(post("/api/formulario/recorridos").secure(true).session(session).with(csrf()).contentType("application/json").content(inicio(tipo)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("id").asString();
    }
    @Test void requiereHttpsCsrfYVersionDelAvisoSinCrearRecorrido()throws Exception {
        mvc.perform(post("/api/formulario/recorridos").secure(true).contentType("application/json").content(inicio("ADULTO_DECLARADO"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/formulario/recorridos").with(csrf()).contentType("application/json").content(inicio("ADULTO_DECLARADO"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/formulario/recorridos").secure(true).with(csrf()).contentType("application/json").content(inicio("ADULTO_DECLARADO").replace("ADULTO_LAB_1","ANTERIOR"))).andExpect(status().isConflict());
        assertEquals(0,db.sql.queryForObject("SELECT COUNT(*) FROM citizen_journey",Integer.class));assertEquals(0,QUERIES.get());
    }
    @Test void consultaAsociadaASesionNoDevuelveAtributosYRechazaProcedenciaInventada()throws Exception {
        var session=new MockHttpSession();String id=iniciar(session,"ADULTO_DECLARADO");String path="/api/formulario/recorridos/"+id+"/personas/presentador-adulto";
        var response=mvc.perform(post(path+"/identificacion").secure(true).session(session).with(csrf()).contentType("application/json")
            .content("{\"rol\":\"PRESENTADOR\",\"runNumero\":\"12345678\",\"runDv\":\"5\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.autenticada").value(false)).andExpect(jsonPath("$.valoresDeclarados").isEmpty()).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("REGISTRAL"));assertFalse(response.contains("12345678"));
        mvc.perform(get(path).secure(true).session(new MockHttpSession())).andExpect(status().isNotFound());
        mvc.perform(post(path+"/declaracion").secure(true).session(session).with(csrf()).contentType("application/json").content("{\"atributos\":{\"primerNombre\":\"FALSO\"}}" )).andExpect(status().isBadRequest());
        mvc.perform(post(path+"/declaracion").secure(true).session(session).with(csrf()).contentType("application/json").content("{\"atributos\":{\"primerApellido\":\"DECLARADO\"},\"autenticada\":true}" )).andExpect(status().isBadRequest());
        mvc.perform(post(path+"/declaracion").secure(true).session(session).with(csrf()).contentType("application/json").content("{\"atributos\":{\"primerApellido\":\"DECLARADO\"}}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.valoresDeclarados.primerApellido").value("DECLARADO"));
        assertEquals(1,QUERIES.get());
    }
    @Test void nnaSinRunNoInvocaProveedorYActualDependeDeLaSesion()throws Exception {
        var session=new MockHttpSession();String id=iniciar(session,"NNA");String path="/api/formulario/recorridos/"+id+"/personas/presentador-nna";
        mvc.perform(post(path+"/identificacion").secure(true).session(session).with(csrf()).contentType("application/json").content("{\"rol\":\"PRESENTADOR\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.runInformado").value(false)).andExpect(jsonPath("$.autenticada").value(false));
        mvc.perform(get("/api/formulario/actual").secure(true).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/formulario/actual").secure(true).session(new MockHttpSession())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/formulario/recorridos/"+id).secure(true).session(session)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/formulario/recorridos/"+id).secure(true).session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get(path).secure(true).session(session)).andExpect(status().isNotFound());assertEquals(0,QUERIES.get());
    }
    @Test void adultoCuNoSeConvierteEnDeclaradoNiAceptaRunDeCliente()throws Exception {
        var session=new MockHttpSession();String id=iniciar(session,"ADULTO_CU");
        mvc.perform(post("/api/formulario/recorridos/"+id+"/personas/presentador-adulto/identificacion").secure(true).session(session).with(csrf()).contentType("application/json")
            .content("{\"rol\":\"PRESENTADOR\",\"runNumero\":\"12345678\",\"runDv\":\"5\"}" )).andExpect(status().isUnauthorized());
        assertEquals(0,QUERIES.get());assertEquals(0,db.sql.queryForObject("SELECT COUNT(*) FROM citizen_person",Integer.class));
    }
    @Test void cuerpoExcesivoYJsonDuplicadoNoAlcanzanAlProveedor()throws Exception {
        mvc.perform(post("/api/formulario/recorridos").secure(true).with(csrf()).contentType("application/json").content("x".repeat(9000))).andExpect(status().isPayloadTooLarge());
        mvc.perform(post("/api/formulario/recorridos").secure(true).with(csrf()).contentType("application/json").content("{\"tipo\":\"NNA\",\"tipo\":\"ADULTO_CU\",\"avisoVersion\":\"NNA_LAB_1\"}" )).andExpect(status().isBadRequest());
        assertEquals(0,QUERIES.get());assertEquals(0,db.sql.queryForObject("SELECT COUNT(*) FROM citizen_journey",Integer.class));
    }
    @Test void reinicioDeIdentificacionRequierePropiedadYCsrfYConservaHistorial()throws Exception {
        var session=new MockHttpSession();String id=iniciar(session,"ADULTO_DECLARADO"),path="/api/formulario/recorridos/"+id+"/personas/presentador-adulto/identificacion";
        mvc.perform(post(path).secure(true).session(session).with(csrf()).contentType("application/json").content("{\"rol\":\"PRESENTADOR\",\"runNumero\":\"12345678\",\"runDv\":\"5\"}" )).andExpect(status().isOk());
        mvc.perform(delete(path).secure(true).session(session)).andExpect(status().isForbidden());
        mvc.perform(delete(path).secure(true).session(new MockHttpSession()).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete(path).secure(true).session(session).with(csrf())).andExpect(status().isNoContent());
        assertEquals(1,db.sql.queryForObject("SELECT COUNT(*) FROM citizen_identity_history",Integer.class));assertEquals(1,QUERIES.get());
    }
    @Test void cuConEvidenciaRealDeSesionNoExponeRegistroYRechazaSustituirSuRun()throws Exception {
        var session=new MockHttpSession();String id=iniciar(session,"ADULTO_CU");
        var identity=new cl.nexbyte.siger.identidad.IdentidadVerificada("12345678-5","NOMBRE CU CONTROLADO","APELLIDO CU CONTROLADO",List.of("NOMBRE","CU CONTROLADO"),List.of("APELLIDO","CU CONTROLADO"));
        var proof=cl.nexbyte.siger.identidad.EvidenciaAutenticacion.de(identity);
        var auth=org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(identity,null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CIUDADANO")));auth.setDetails(proof);
        String path="/api/formulario/recorridos/"+id+"/personas/presentador-adulto/identificacion";
        mvc.perform(post(path).secure(true).session(session).with(authentication(auth)).with(csrf()).contentType("application/json").content("{\"rol\":\"PRESENTADOR\",\"runNumero\":\"11111111\",\"runDv\":\"1\"}" )).andExpect(status().isBadRequest());
        var result=mvc.perform(post(path).secure(true).session(session).with(authentication(auth)).with(csrf()).contentType("application/json").content("{\"rol\":\"PRESENTADOR\"}" ))
            .andExpect(status().isOk()).andExpect(jsonPath("$.autenticada").value(true)).andReturn().getResponse().getContentAsString();
        assertFalse(result.contains("CONTROLADO"));assertFalse(result.contains("REGISTRAL"));assertEquals(1,QUERIES.get());
        assertTrue(db.sql.queryForObject("SELECT body FROM citizen_person WHERE journey_id=?",String.class,id).contains(proof.id().toString()));
    }
    @Test void identificacionExtranjeraEsPrivadaDeclaradaYRequiereCsrfHttpsYCanalAdulto()throws Exception{
        var session=new MockHttpSession();String id=iniciar(session,"ADULTO_DECLARADO");
        String url="/api/formulario/recorridos/"+id+"/identificacion-extranjera";
        mvc.perform(post(url).secure(true).session(session)).andExpect(status().isForbidden());
        mvc.perform(post(url).session(session).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(url).secure(true).session(new MockHttpSession()).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post(url).secure(true).session(session).with(csrf())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.autenticada").value(false)).andExpect(jsonPath("$.runInformado").value(false)).andExpect(jsonPath("$.valoresDeclarados").isEmpty());
        String person="/api/formulario/recorridos/"+id+"/personas/presentador-adulto";
        mvc.perform(post(person+"/declaracion").secure(true).session(session).with(csrf()).contentType("application/json")
            .content("{\"atributos\":{\"primerNombre\":\"DECLARADO\",\"primerApellido\":\"APELLIDO\",\"nacionalidad\":\"Perú\",\"tipoDocumento\":\"PASAPORTE\",\"numeroDocumento\":\"AB/123-X\"}}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.valoresDeclarados.numeroDocumento").value("AB/123-X")).andExpect(jsonPath("$.autenticada").value(false));
        mvc.perform(get(person).secure(true).session(new MockHttpSession())).andExpect(status().isNotFound());
        var nna=iniciar(session,"NNA");mvc.perform(post("/api/formulario/recorridos/"+nna+"/identificacion-extranjera").secure(true).session(session).with(csrf())).andExpect(status().isBadRequest());
        var cu=iniciar(session,"ADULTO_CU");mvc.perform(post("/api/formulario/recorridos/"+cu+"/identificacion-extranjera").secure(true).session(session).with(csrf())).andExpect(status().isBadRequest());
        assertEquals(0,QUERIES.get());
    }

}
