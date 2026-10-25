package cl.nexbyte.siger.robustez;
import cl.nexbyte.siger.custodia.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.annotation.DirtiesContext;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"siger.claveunica.client-id=test","siger.claveunica.client-secret=test","publico.integracion.token=integracion-sintetica-transitorio-2026","operacion.token=operacion-sintetica-transitorio-2026","ciclo-vida.intervalo=PT0.2S","ciclo-vida.inicio=PT0.01S"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc(print=MockMvcPrint.NONE) @Import(CustodiaTransitoriaHttpTest.Fixture.class)
class CustodiaTransitoriaHttpTest {
 @Autowired MockMvc mvc;@Autowired CustodiaTransitoria service;@Autowired GarantiasCustodiaJdbc guarantees;
 @TestConfiguration @EnableScheduling static class Fixture {
  @Bean CustodiaTransitoriaConfiguration.Programador programador(CustodiaTransitoria service){return new CustodiaTransitoriaConfiguration.Programador(service);}
  @Bean CustodiaTransitoria service(){return mock(CustodiaTransitoria.class);}
  @Bean GarantiasCustodiaJdbc guarantees(){return mock(GarantiasCustodiaJdbc.class);}
 }
 @BeforeEach void resetMocks(){reset(service,guarantees);when(service.garantias()).thenReturn(guarantees);when(service.candidatos(anyInt())).thenReturn(List.of());}
 @Test void nuncaUsaSesionCiudadanaNiPermiteHttp()throws Exception{
  mvc.perform(get("/integracion/v3/custodia/candidatos").secure(true).with(user("ciudadano").roles("INTEGRACION"))).andExpect(status().isUnauthorized());
  mvc.perform(get("/integracion/v3/custodia/candidatos").header("Authorization","Bearer integracion-sintetica-transitorio-2026")).andExpect(status().isForbidden());
 }
 @Test void tokenIntegracionNoConcedeControlesOperativos()throws Exception{
  mvc.perform(post("/operacion/v1/custodia/conservacion").secure(true).header("Authorization","Bearer integracion-sintetica-transitorio-2026").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
 }
 @Test void candidatoEsMinimoPrivadoYAcotado()throws Exception{
  mvc.perform(get("/integracion/v3/custodia/candidatos").secure(true).header("Authorization","Bearer integracion-sintetica-transitorio-2026")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(content().json("[]"));
  mvc.perform(get("/integracion/v3/custodia/candidatos?limite=1000").secure(true).header("Authorization","Bearer integracion-sintetica-transitorio-2026")).andExpect(status().isBadRequest());
 }
 @Test void tokenNoSustituyeFirmaYSeConservaLimiteDeCuerpo()throws Exception{
  doThrow(new IllegalArgumentException("Firma sintética inválida")).when(guarantees).aceptar(any());
  String body="{\"keyId\":\"test\",\"payload\":\"YQ\",\"firma\":\""+"a".repeat(86)+"\"}";
  mvc.perform(post("/integracion/v3/custodia/garantias").secure(true).header("Authorization","Bearer integracion-sintetica-transitorio-2026").contentType("application/json").content(body)).andExpect(status().isBadRequest());
  mvc.perform(post("/integracion/v3/custodia/garantias").secure(true).header("Authorization","Bearer integracion-sintetica-transitorio-2026").contentType("application/json").content("x".repeat(9000))).andExpect(status().isPayloadTooLarge());
 }
 @Test void elTemporizadorEjecutaElMotorSinInvocacionManual(){verify(service,timeout(1500).atLeastOnce()).ejecutar();}

 @Test void indicadoresSoloOperacionalesYNoStore()throws Exception{
  when(service.estado()).thenReturn(Map.of("completo",Map.of("minimizadas",2),"historico",Map.of("minimizadas",1)));
  mvc.perform(get("/operacion/v1/custodia").secure(true)).andExpect(status().isUnauthorized());
  mvc.perform(get("/operacion/v1/custodia").secure(true).with(user("ciudadano").roles("OPERACION"))).andExpect(status().isUnauthorized());
  mvc.perform(get("/operacion/v1/custodia").secure(true).header("Authorization","Bearer integracion-sintetica-transitorio-2026")).andExpect(status().isUnauthorized());
  mvc.perform(get("/operacion/v1/custodia").header("Authorization","Bearer operacion-sintetica-transitorio-2026")).andExpect(status().isForbidden());
  mvc.perform(get("/operacion/v1/custodia").secure(true).header("Authorization","Bearer operacion-sintetica-transitorio-2026")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(content().json("{\"completo\":{\"minimizadas\":2},\"historico\":{\"minimizadas\":1}}"));
 }
 @Test void holdIrreversibleEsConflictoHttpSinFalsaConservacion()throws Exception{
  doThrow(new FalloTecnico(409)).when(service).conservar(eq("COMPLETO"),any(),eq(true));
  mvc.perform(post("/operacion/v1/custodia/conservacion").secure(true).header("Authorization","Bearer operacion-sintetica-transitorio-2026").contentType("application/json").content("{\"cola\":\"COMPLETO\",\"id\":\"00000000-0000-0000-0000-000000000001\",\"conservar\":true}")).andExpect(status().isConflict()).andExpect(header().string("Cache-Control","no-store"));
 }

}