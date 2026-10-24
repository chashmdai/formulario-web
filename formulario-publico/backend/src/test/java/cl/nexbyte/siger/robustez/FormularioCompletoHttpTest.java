package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.formulario.*;
import java.nio.file.Files;
import java.security.*;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"siger.claveunica.client-id=test","siger.claveunica.client-secret=test","publico.integracion.token=token-controlado-de-integracion-sintetico-2026"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE) @Import(FormularioCompletoHttpTest.Fixture.class)
class FormularioCompletoHttpTest {
    @Autowired MockMvc mvc;@Autowired BaseJdbc db;@Autowired JsonMapper mapper;
    MockHttpSession session;String journey,path;
    @TestConfiguration static class Fixture {
        @Bean(destroyMethod="close") BaseJdbc formularioCompletoDb()throws Exception{return TestDatabase.open(Files.createTempDirectory("completo-http-"));}
        @Bean RecorridosJdbc journeys(BaseJdbc db,JsonMapper mapper){return new RecorridosJdbc(db,mapper,r->{throw new AssertionError("No usar red");},r->false,Clock.systemUTC(),new byte[32],"ADULTO_LAB_1","NNA_LAB_1");}
        @Bean AdjuntosFormulario adjuntos(BaseJdbc db,RecorridosJdbc journeys){return new AdjuntosFormulario(db,journeys,b->true);}
        @Bean RecepcionFormularioJdbc reception(BaseJdbc db,RecorridosJdbc journeys,AdjuntosFormulario files,JsonMapper mapper)throws Exception {
            var keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            return new RecepcionFormularioJdbc(db,journeys,files,mapper,new ValidacionFormulario(new TerritorioFormulario(mapper)),"test",keys.getPrivate(),Map.of("test",keys.getPublic()),Map.of(),Clock.systemUTC(),true);
        }
    }
    @BeforeEach void before()throws Exception {
        limpiar();var c=new ControlesOperacion(db);c.cambiar(c.estado().revision(),true,true,"NORMAL","PRUEBA_LOCAL");session=new MockHttpSession();
        var response=mvc.perform(post("/api/formulario/recorridos").secure(true).session(session).with(csrf()).contentType("application/json").content("{\"tipo\":\"NNA\",\"aceptaCanalDigital\":false,\"avisoVersion\":\"NNA_LAB_1\"}")).andExpect(status().isOk()).andReturn();
        journey=mapper.readTree(response.getResponse().getContentAsString()).path("id").asString();path="/api/formulario/recorridos/"+journey;
        mvc.perform(post(path+"/personas/presentador-nna/identificacion").secure(true).session(session).with(csrf()).contentType("application/json").content("{\"rol\":\"PRESENTADOR\"}")).andExpect(status().isOk());
        mvc.perform(post(path+"/personas/presentador-nna/declaracion").secure(true).session(session).with(csrf()).contentType("application/json").content("{\"atributos\":{\"primerNombre\":\"NOMBRE\",\"primerApellido\":\"APELLIDO\"}}")).andExpect(status().isOk());
    }
    @AfterEach void limpiar(){db.sql.update("DELETE FROM citizen_submission");db.sql.update("DELETE FROM citizen_attachment");db.sql.update("DELETE FROM citizen_identity_history");db.sql.update("DELETE FROM citizen_person");db.sql.update("DELETE FROM citizen_journey");db.sql.update("DELETE FROM citizen_abuse_bucket");}
    String datos(String story){return mapper.writeValueAsString(new DatosFormulario(Map.of("nna-region","13","nna-primary-method","unsafe","nna-story",story),List.of(),List.of(),List.of()));}
    @Test void envioCompletoAdmiteUtf8LargoPeroMantieneLimiteDeIdentificacionYV3()throws Exception {
        String key=UUID.randomUUID().toString();var body=datos("Ñ".repeat(5000));
        var response=mvc.perform(post(path+"/comunicaciones").secure(true).session(session).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.estado").value("PENDIENTE_REGISTRO_SIGER")).andExpect(jsonPath("$.registro").isEmpty()).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("APELLIDO"));assertFalse(response.contains("Ñ".repeat(50)));
        mvc.perform(get("/api/formulario/comunicaciones/"+key).secure(true).session(new MockHttpSession())).andExpect(status().isNotFound());
        mvc.perform(get("/api/formulario/comunicaciones/"+key).secure(true).session(session)).andExpect(status().isOk());
        mvc.perform(get(path+"/comunicacion").secure(true).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(key));
        mvc.perform(get(path+"/comunicacion").secure(true).session(new MockHttpSession())).andExpect(status().isNotFound());
        mvc.perform(post(path+"/personas/presentador-nna/declaracion").secure(true).session(session).with(csrf()).contentType("application/json").content("x".repeat(9000))).andExpect(status().isPayloadTooLarge());
        mvc.perform(post("/integracion/v3/entregas/reclamar").secure(true).header("Authorization","Bearer token-controlado-de-integracion-sintetico-2026").contentType("application/json").content("x".repeat(9000))).andExpect(status().isPayloadTooLarge());
    }
    @Test void envioExigeHttpsPropiedadCsrfYClaveValida()throws Exception {
        var body=datos("Relato");var key=UUID.randomUUID().toString();
        mvc.perform(post(path+"/comunicaciones").secure(true).session(session).header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(path+"/comunicaciones").session(session).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(path+"/comunicaciones").secure(true).session(new MockHttpSession()).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(status().isNotFound());
        mvc.perform(post(path+"/comunicaciones").secure(true).session(session).with(csrf()).header("Idempotency-Key","invalida").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        assertEquals(0,db.sql.queryForObject("SELECT COUNT(*) FROM citizen_submission",Integer.class));
    }
    @Test void duplicadosJsonCamposDesconocidosYTextoExcesivoNoSeReciben()throws Exception {
        for(var body:List.of(datos("x".repeat(5001)),"{\"campos\":{},\"campos\":{}}","{\"campos\":{},\"autenticada\":true}"))
            mvc.perform(post(path+"/comunicaciones").secure(true).session(session).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        assertEquals(0,db.sql.queryForObject("SELECT COUNT(*) FROM citizen_submission",Integer.class));
    }
    @Test void cargaDescargaYBorradoDeAdjuntoSonPrivadosYRequierenCsrf()throws Exception {
        var file=new MockMultipartFile("archivos","respaldo.txt","text/plain","Contenido de prueba".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart(path+"/adjuntos").file(file).content(new byte[1]).secure(true).session(session)).andExpect(status().isForbidden());
        var result=mvc.perform(multipart(path+"/adjuntos").file(file).content(new byte[1]).secure(true).session(session).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$[0].estado").value("VALIDADO")).andReturn();
        String attachment=mapper.readTree(result.getResponse().getContentAsString()).get(0).path("id").asString();
        mvc.perform(get(path+"/adjuntos/"+attachment).secure(true).session(session)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("Content-Type","application/octet-stream"));
        mvc.perform(get(path+"/adjuntos/"+attachment).secure(true).session(new MockHttpSession())).andExpect(status().isNotFound());
        mvc.perform(delete(path+"/adjuntos").secure(true).session(session)).andExpect(status().isForbidden());
        mvc.perform(delete(path+"/adjuntos").secure(true).session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get(path+"/adjuntos/"+attachment).secure(true).session(session)).andExpect(status().isNotFound());
    }
    @Test void cargaSinLongitudExcesivaOConFormatoFalsoNoAlmacena()throws Exception {
        mvc.perform(post(path+"/adjuntos").secure(true).session(session).with(csrf()).contentType("multipart/form-data; boundary=prueba")).andExpect(status().isLengthRequired());
        mvc.perform(post(path+"/adjuntos").secure(true).session(session).with(csrf()).contentType("multipart/form-data; boundary=prueba").content(new byte[26*1024*1024+1])).andExpect(status().isPayloadTooLarge());
        mvc.perform(multipart(path+"/adjuntos").file(new MockMultipartFile("archivos","falso.pdf","application/pdf","no pdf".getBytes())).content(new byte[1]).secure(true).session(session).with(csrf())).andExpect(status().isBadRequest());
        assertEquals(0,db.sql.queryForObject("SELECT COUNT(*) FROM citizen_attachment",Integer.class));
    }
    @Test void recuperacionAmpliadaNoUsaSesionCiudadanaYExigeTokenMaquina()throws Exception {
        mvc.perform(post("/integracion/v3/formulario-completo/entregas/reclamar").secure(true).session(session).with(csrf()).contentType("application/json").content("{\"limite\":1}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/integracion/v3/formulario-completo/entregas/reclamar").secure(true).header("Authorization","Bearer token-controlado-de-integracion-sintetico-2026").contentType("application/json").content("{\"limite\":1}")).andExpect(status().isOk()).andExpect(jsonPath("$.entregas").isEmpty());
        mvc.perform(post("/integracion/v3/formulario-completo/entregas/reclamar").header("Authorization","Bearer token-controlado-de-integracion-sintetico-2026").contentType("application/json").content("{\"limite\":1}")).andExpect(status().isForbidden());
    }
}
