package cl.nexbyte.siger.robustez;
import cl.nexbyte.siger.custodia.*;
import cl.nexbyte.siger.multimedia.CicloVidaCustodia;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Temporizador REAL y motor REAL; prueba institucional de custodia controlada, sin proveedores. */
@SpringBootTest(properties={"siger.claveunica.client-id=test","siger.claveunica.client-secret=test","publico.integracion.token=periodico-sintetico-integracion-2026","ciclo-vida.intervalo=PT0.2S","ciclo-vida.inicio=PT0.05S"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE) @Import(CustodiaPeriodicaHttpTest.Fixture.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class CustodiaPeriodicaHttpTest {
 @Autowired MockMvc mvc;@Autowired Scenario scenario;@Autowired CustodiaTransitoria service;
 static class Scenario implements AutoCloseable {
  final ReceptorTransitorioTest t=new ReceptorTransitorioTest();CustodiaTransitoria service;
  Scenario()throws Exception{t.temp=Files.createTempDirectory("custodia-periodica-");t.setup();t.receive(false);}
  public void close()throws Exception{if(service!=null){service.close();long end=System.nanoTime()+2_000_000_000L;while(Boolean.TRUE.equals(service.estado().get("ejecutando"))&&System.nanoTime()<end)Thread.sleep(10);}t.close();}
 }
 @TestConfiguration @EnableScheduling static class Fixture {
  @Bean(destroyMethod="close") Scenario scenario()throws Exception{return new Scenario();}
  @Bean CustodiaTransitoria service(Scenario s){var t=s.t;var f=t.t.f;s.service=new CustodiaTransitoria(f.db,f.store,false,f.mapper,t.proofs,Map.of("publico",t.t.publicKey.getPublic()),Map.of("institucion",t.t.institutional.getPublic()),"publico",t.t.publicKey.getPrivate(),t.ownerKey,t.t.clock,new CicloVidaCustodia.Politica(true,Duration.ofHours(24),Duration.ofHours(48)));return s.service;}
  @Bean CustodiaTransitoriaConfiguration.Programador programador(CustodiaTransitoria service){return new CustodiaTransitoriaConfiguration.Programador(service);}
 }
 @Test void garantiaPorHttpsYTimerRetiranSinEjecutarManualmenteElMotor()throws Exception{
  var t=scenario.t;var f=t.t.f;long end=System.nanoTime()+3_000_000_000L;
  while(f.db.sql.queryForObject("SELECT COUNT(*) FROM custody_lifecycle",Integer.class)==0&&System.nanoTime()<end)Thread.sleep(20);
  assertEquals(1,f.db.sql.queryForObject("SELECT COUNT(*) FROM custody_lifecycle",Integer.class));
  t.t.now.addAndGet(Duration.ofHours(25).toMillis());
  assertNotNull(f.db.sql.queryForObject("SELECT body FROM citizen_submission",String.class));
  mvc.perform(post("/integracion/v3/custodia/garantias").secure(true).header("Authorization","Bearer periodico-sintetico-integracion-2026").contentType("application/json").content(f.mapper.writeValueAsString(t.proof(t.t.communication,"COMPLETO")))).andExpect(status().isNoContent());
  end=System.nanoTime()+4_000_000_000L;
  while(!f.db.sql.queryForObject("SELECT content_retired FROM citizen_submission",Boolean.class)&&System.nanoTime()<end)Thread.sleep(20);
  assertNull(f.db.sql.queryForObject("SELECT body FROM citizen_submission",String.class));assertEquals(0,f.db.sql.queryForObject("SELECT COUNT(*) FROM citizen_person",Integer.class));assertEquals(0,t.t.reserved());
  assertEquals("REGISTRADA_SIGER",t.t.reception.consultar(t.t.communication,f.session).estado());
 }
}