package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.custodia.*;
import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.multimedia.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

/** ACK no equivale a respaldo: política explícita de riesgo, fixtures aislados y dos conexiones concurrentes. */
class RetencionAckAprobadaTest {
 @TempDir Path temp;
 ReceptorTransitorioTest r;
 static final String REF="LAB-ACK72-2026-01";
 @BeforeEach void setup()throws Exception{r=new ReceptorTransitorioTest();r.temp=temp;r.setup();}
 @AfterEach void close()throws Exception{r.close();}
 CicloVidaCustodia.Politica policy(boolean enabled,String ref){return new CicloVidaCustodia.Politica(enabled,Duration.ofHours(72),Duration.ofHours(48),CicloVidaCustodia.Modalidad.ACK_RETENCION_LIMITADA_APROBADA,ref);}
 CustodiaTransitoria app(boolean enabled,String ref){return new CustodiaTransitoria(r.t.f.db,r.t.f.store,false,r.t.f.mapper,null,Map.of("publico",r.t.publicKey.getPublic()),Map.of("institucion",r.t.institutional.getPublic()),"publico",r.t.publicKey.getPrivate(),r.ownerKey,r.t.clock,policy(enabled,ref));}
 CicloVidaCustodia motor(BaseJdbc db,String ref,BiConsumer<String,UUID> pause){return new CicloVidaCustodia(db,r.t.f.store,null,r.t.f.mapper,Map.of("publico",r.t.publicKey.getPublic()),Map.of("institucion",r.t.institutional.getPublic()),r.t.clock,policy(true,ref),pause);}
 BaseJdbc connection()throws Exception{
  String url;try(var c=r.t.f.db.sql.getDataSource().getConnection()){url=c.getMetaData().getURL();}
  return new BaseJdbc(url,System.getenv("ROBUST_DB_URL")==null?"sa":System.getenv("ROBUST_DB_USER"),System.getenv("ROBUST_DB_URL")==null?"":System.getenv("ROBUST_DB_PASSWORD"),4);
 }
 long first(){return r.t.f.db.sql.queryForObject("SELECT first_ack_ms FROM citizen_submission WHERE id=?",Long.class,r.t.communication.toString());}
 void at(long delta){r.t.now.set(first()+delta);}
 String phase(){return r.t.f.db.sql.queryForObject("SELECT phase FROM custody_lifecycle WHERE submission_id=?",String.class,r.t.communication.toString());}
 static void await(CountDownLatch latch){try{assertTrue(latch.await(4,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
 void receive(boolean blob)throws Exception{r.receive(blob);r.app=app(true,REF);}

 @Test void configuracionPredeterminadaConservaGarantiaYRetiradaCerrada(){
  var defaults=CustodiaTransitoriaConfiguration.politica(new MockEnvironment());
  assertTrue(defaults.independiente());assertFalse(defaults.eliminar());assertEquals(Duration.ofHours(24),defaults.postAck());
  var env=new MockEnvironment().withProperty("ciclo-vida.modalidad","ACK_RETENCION_LIMITADA_APROBADA");
  assertThrows(IllegalArgumentException.class,()->CustodiaTransitoriaConfiguration.politica(env));
  env.withProperty("ciclo-vida.ack-riesgo-aceptado","true");
  assertThrows(IllegalArgumentException.class,()->CustodiaTransitoriaConfiguration.politica(env));
  env.withProperty("ciclo-vida.aprobacion-ref",REF);
  var approved=CustodiaTransitoriaConfiguration.politica(env);assertFalse(approved.independiente());assertFalse(approved.eliminar());assertEquals(Duration.ofHours(72),approved.postAck());
  env.withProperty("ciclo-vida.retencion-post-ack","PT24H");assertThrows(IllegalArgumentException.class,()->CustodiaTransitoriaConfiguration.politica(env));
  assertThrows(IllegalArgumentException.class,()->new CicloVidaCustodia.Politica(true,Duration.ofHours(71),Duration.ofHours(48),CicloVidaCustodia.Modalidad.ACK_RETENCION_LIMITADA_APROBADA,REF));
  assertThrows(IllegalArgumentException.class,()->CustodiaTransitoriaConfiguration.politica(new MockEnvironment().withProperty("ciclo-vida.modalidad","ACK")));
 }
 @ParameterizedTest @ValueSource(booleans={false,true})
 void limiteExacto72HorasDesdePrimerAckSinAgenteConReciboEIdempotencia(boolean blob)throws Exception{
  receive(blob);long first=first();assertEquals(r.t.f.clock.millis(),first);
  var expected=r.t.reception.consultar(r.t.communication,r.t.f.session);
  at(Duration.ofHours(72).toMillis()-1);assertEquals(0,r.app.ejecutar().minimizadas());
  assertNotNull(r.t.f.db.sql.queryForObject("SELECT body FROM citizen_submission",String.class));
  at(Duration.ofHours(72).toMillis());assertEquals(1,r.app.ejecutar().minimizadas());assertEquals("MINIMIZADA",phase());
  assertEquals(0,r.t.reserved());assertNull(r.t.f.db.sql.queryForObject("SELECT content FROM citizen_attachment WHERE id=?",byte[].class,r.t.attachment.toString()));
  assertNull(r.t.f.db.sql.queryForObject("SELECT body FROM citizen_submission",String.class));
  assertEquals(0,r.t.f.db.sql.queryForObject("SELECT COUNT(*) FROM citizen_person",Integer.class));
  assertEquals(0,r.t.f.db.sql.queryForObject("SELECT COUNT(*) FROM custody_guarantee",Integer.class));
  assertEquals(expected,r.t.reception.consultar(r.t.communication,r.t.f.session));
  assertEquals(expected,r.t.reception.recibir(r.t.communication,r.t.f.journey,r.t.f.session,r.data()));
  r.t.reception.confirmar(r.t.communication,r.t.lease.reserva(),expected.registro());assertEquals(first,first());
  assertEquals(0,r.app.ejecutar().minimizadas());
  assertEquals("ACK_RETENCION_LIMITADA_APROBADA",r.t.f.db.sql.queryForObject("SELECT retirement_mode FROM custody_lifecycle",String.class));
  assertEquals(REF,r.t.f.db.sql.queryForObject("SELECT retirement_policy_ref FROM custody_lifecycle",String.class));
  assertEquals(1,r.t.f.db.sql.queryForObject("SELECT COUNT(*) FROM operational_event WHERE code='RETIRADA_ACK_72H_INICIADA'",Integer.class));
  assertEquals("REGISTRADA_SIGER",expected.estado());
 }
 @Test void primeraEjecucionTardiaNoIniciaOtras72HorasNiDuplicadoReiniciaPlazo()throws Exception{
  receive(false);long start=first();at(Duration.ofHours(72).toMillis());
  var ack=r.t.f.mapper.readValue(r.t.ackText,RegistroInstitucional.class);
  r.t.reception.confirmar(r.t.communication,r.t.lease.reserva(),ack);assertEquals(start,first());
  assertEquals(1,r.app.ejecutar().minimizadas());assertEquals(start,first());
 }
 @Test void ackInvalidoNoRegistraFechaYNoPermiteRetirada()throws Exception{
  receive(false);
  r.t.f.db.sql.update("UPDATE citizen_submission SET state='RESERVADA',ack=NULL,first_ack_ms=NULL,lease_until_ms=? WHERE id=?",r.t.f.clock.millis()+300000,r.t.communication.toString());
  var good=r.t.f.mapper.readValue(r.t.ackText,RegistroInstitucional.class);
  var other=java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
  var bad=new RegistroInstitucional(good.registro(),ComprobanteInstitucional.firmar(good.registro(),"institucion",other.getPrivate(),r.t.f.mapper));
  assertEquals(409,assertThrows(FalloTecnico.class,()->r.t.reception.confirmar(r.t.communication,r.t.lease.reserva(),bad)).status);
  assertNull(r.t.f.db.sql.queryForObject("SELECT first_ack_ms FROM citizen_submission",Long.class));
  r.t.due();assertEquals(0,r.app.ejecutar().minimizadas());assertTrue(Files.exists(r.t.f.store.original(r.t.attachment)));
  r.t.reception.confirmar(r.t.communication,r.t.lease.reserva(),good);assertEquals(r.t.f.clock.millis(),first());
 }
 @Test void ackPersistidoAlteradoFirmaODatosBloqueanAunquePasen72Horas()throws Exception{
  receive(false);at(Duration.ofDays(4).toMillis());r.t.f.db.sql.update("UPDATE citizen_submission SET ack='{}' WHERE id=?",r.t.communication.toString());
  assertEquals(0,r.app.ejecutar().minimizadas());assertTrue(Files.exists(r.t.f.store.original(r.t.attachment)));
  r.t.f.db.sql.update("UPDATE citizen_submission SET ack=?,body=? WHERE id=?",r.t.ackText,r.t.signed.replace("Relato sintético","Relato alterado"),r.t.communication.toString());
  assertEquals(0,r.app.ejecutar().minimizadas());assertTrue(r.t.reserved()>0);
 }
 @Test void historicoSinFechaSeObservaSinRetrofecharAunqueAckSeaAntiguo()throws Exception{
  receive(false);r.t.f.db.sql.update("UPDATE citizen_submission SET first_ack_ms=NULL WHERE id=?",r.t.communication.toString());
  r.t.due();long observed=r.t.now.get();assertEquals(0,r.app.ejecutar().minimizadas());assertEquals(observed,first());
  at(Duration.ofHours(72).toMillis()-1);assertEquals(0,r.app.ejecutar().minimizadas());
  at(Duration.ofHours(72).toMillis());assertEquals(1,r.app.ejecutar().minimizadas());
 }
 @Test void cierreExplicitoConservaAunqueModalidadYAckSeanValidos()throws Exception{
  receive(false);r.app.close();r.app=app(false,REF);at(Duration.ofDays(4).toMillis());assertEquals(0,r.app.ejecutar().minimizadas());r.t.intact();
  assertEquals(503,assertThrows(FalloTecnico.class,()->r.app.garantias()).status);
 }
 @ParameterizedTest @ValueSource(strings={"PENDIENTE","RESERVADA","CUARENTENA"})
 void estadosNoConfirmadosSeConservan(String state)throws Exception{
  receive(false);r.t.f.db.sql.update("UPDATE citizen_submission SET state=? WHERE id=?",state,r.t.communication.toString());
  at(Duration.ofDays(4).toMillis());assertEquals(0,r.app.ejecutar().minimizadas());r.t.intact();
 }
 @Test void originalAusenteOModificadoSinIntencionNoSeAcepta()throws Exception{
  receive(false);at(Duration.ofDays(4).toMillis());Files.setPosixFilePermissions(r.t.f.store.original(r.t.attachment),java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));Files.writeString(r.t.f.store.original(r.t.attachment),"alterado");
  assertEquals(0,r.app.ejecutar().minimizadas());assertTrue(r.t.reserved()>0);assertEquals(0,r.t.f.db.sql.queryForObject("SELECT COUNT(*) FROM storage_retirement",Integer.class));
  Files.delete(r.t.f.store.original(r.t.attachment));assertEquals(0,r.app.ejecutar().minimizadas());assertEquals("OBSERVADA",phase());
 }
 @Test void holdConcurrenteConfirmadoAntesDeFronteraGanaConOtraConexion()throws Exception{
  receive(false);at(Duration.ofDays(4).toMillis());var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  try(var other=connection();var pool=Executors.newSingleThreadExecutor()){
   var worker=motor(r.t.f.db,REF,(point,id)->{if(point.equals("ANTES_RETIRANDO")){entered.countDown();await(release);}});
   var future=pool.submit(()->assertThrows(IOException.class,()->worker.retirar(r.t.communication)));
   try{await(entered);motor(other,REF,(p,id)->{}).conservar(r.t.communication,true);}finally{release.countDown();}
   future.get(4,TimeUnit.SECONDS);r.t.intact();assertNotEquals("RETIRANDO",phase());
  }
 }
 @Test void holdDuranteRetiradaIrreversibleEs409YReanudaSinDobleReserva()throws Exception{
  receive(false);at(Duration.ofDays(4).toMillis());long before=r.t.reserved();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  try(var other=connection();var pool=Executors.newSingleThreadExecutor()){
   var worker=motor(r.t.f.db,REF,(point,id)->{if(point.equals("RETIRANDO")){entered.countDown();await(release);throw new IllegalStateException("Caída sintética");}});
   var future=pool.submit(()->assertThrows(IllegalStateException.class,()->worker.retirar(r.t.communication)));
   try{await(entered);assertEquals(409,assertThrows(FalloTecnico.class,()->motor(other,REF,(p,id)->{}).conservar(r.t.communication,true)).status);}finally{release.countDown();}
   future.get(4,TimeUnit.SECONDS);assertEquals(before,r.t.reserved());assertEquals("RETIRANDO",phase());
   var restarted=motor(other,REF,(p,id)->{});restarted.verificar(r.t.communication);assertEquals("RETIRANDO",phase());restarted.retirar(r.t.communication);restarted.retirar(r.t.communication);
   assertEquals(0,r.t.reserved());assertEquals(1,r.app.ejecutar().minimizadas());assertEquals(409,assertThrows(FalloTecnico.class,()->r.app.conservar("COMPLETO",r.t.communication,true)).status);
  }
 }
 @Test void politicaReversiblePuedeCambiarPeroNoHayFallbackAutomatico()throws Exception{
  receive(false);r.app.ejecutar();at(Duration.ofDays(4).toMillis());
  try(var original=r.service(true)){assertEquals(0,original.ejecutar().minimizadas());r.t.intact();}
  assertEquals(1,r.app.ejecutar().minimizadas());assertEquals(0,r.t.f.db.sql.queryForObject("SELECT COUNT(*) FROM custody_guarantee",Integer.class));
 }
 @Test void cambioConcurrenteDeReferenciaAntesDeFronteraImpideRetirarConEvidenciaVieja()throws Exception{
  receive(false);at(Duration.ofDays(4).toMillis());
  try(var other=connection()){
   var worker=motor(r.t.f.db,REF,(point,id)->{if(point.equals("ANTES_RETIRANDO"))try{motor(other,"OTRA-POLITICA",(p,k)->{}).verificar(id);}catch(IOException e){throw new IllegalStateException(e);}});
   assertThrows(IOException.class,()->worker.retirar(r.t.communication));r.t.intact();assertNotEquals("RETIRANDO",phase());
  }
 }
 @Test void falloTrasUnlinkExigeMismaPoliticaHastaReanudarYMinimizar()throws Exception{
  receive(false);at(Duration.ofDays(4).toMillis());long before=r.t.reserved();
  assertThrows(IllegalStateException.class,()->motor(r.t.f.db,REF,(point,id)->{if(point.equals("ARCHIVOS_RETIRADOS"))throw new IllegalStateException("Interrupción sintética");}).retirar(r.t.communication));
  assertEquals("RETIRANDO",phase());assertEquals(before,r.t.reserved());assertFalse(Files.exists(r.t.f.store.original(r.t.attachment)));
  try(var original=r.service(true);var changed=app(true,"NUEVA-REFERENCIA")){
   assertEquals(0,original.ejecutar().minimizadas());assertEquals(0,changed.ejecutar().minimizadas());assertEquals(before,r.t.reserved());
   assertEquals(REF,r.t.f.db.sql.queryForObject("SELECT retirement_policy_ref FROM custody_lifecycle",String.class));
  }
  assertEquals(1,r.app.ejecutar().minimizadas());assertEquals(0,r.t.reserved());assertEquals(0,r.app.ejecutar().minimizadas());
  assertEquals(1,r.t.f.db.sql.queryForObject("SELECT COUNT(*) FROM operational_event WHERE code='RETIRADA_FINALIZADA'",Integer.class));
 }
 @Test void reinicioDeConexionYAlmacenReanudaIntencionPersistida()throws Exception{
  receive(false);at(Duration.ofDays(4).toMillis());
  assertThrows(IllegalStateException.class,()->motor(r.t.f.db,REF,(point,id)->{if(point.equals("ARCHIVOS_RETIRADOS"))throw new IllegalStateException("Interrupción sintética");}).retirar(r.t.communication));
  String url;try(var c=r.t.f.db.sql.getDataSource().getConnection()){url=c.getMetaData().getURL();}
  var path=r.t.f.store.raiz();r.app.close();r.t.f.store.close();r.t.f.db.close();
  r.t.f.db=new BaseJdbc(url,System.getenv("ROBUST_DB_URL")==null?"sa":System.getenv("ROBUST_DB_USER"),System.getenv("ROBUST_DB_URL")==null?"":System.getenv("ROBUST_DB_PASSWORD"),4);
  r.t.f.store=new AlmacenPrivado(path);r.app=app(true,REF);
  assertEquals(1,r.app.ejecutar().minimizadas());assertEquals(0,r.t.reserved());assertEquals(0,r.app.ejecutar().minimizadas());
 }
 @Test void falloTransaccionalAlMinimizarConservaPayloadYReintenta()throws Exception{
  receive(false);at(Duration.ofDays(4).toMillis());
  TestDatabase.migration(r.t.f.db,sql->sql.execute("ALTER TABLE custody_lifecycle ADD CONSTRAINT test_ack_min CHECK (phase<>'MINIMIZADA')"));
  try{assertEquals(0,r.app.ejecutar().minimizadas());assertEquals("LIBERADA",phase());assertNotNull(r.t.f.db.sql.queryForObject("SELECT body FROM citizen_submission",String.class));assertEquals(0,r.t.reserved());}
  finally{TestDatabase.migration(r.t.f.db,sql->sql.execute("ALTER TABLE custody_lifecycle DROP "+(System.getenv("ROBUST_DB_URL")==null?"CONSTRAINT":"CHECK")+" test_ack_min"));}
  assertEquals(1,r.app.ejecutar().minimizadas());assertEquals(0,r.t.reserved());
 }
 @Test void agotamientoDeCuotaNoAcortaRetencionYRecuperaAdmisionTrasLiberar()throws Exception{
  receive(false);long quota=r.t.reserved();r.app.cuotaMultimedia(quota);
  var next=r.t.f.journeys.iniciar(IdentificacionCiudadana.Tipo.NNA,false,"NNA_TEST_1",r.t.f.other,"127.0.0.8").id();
  var small=new PoliticaMultimedia(12,quota/2,4,512,512,512,512,quota,0,60000);
  try(var media=new CargasMultimedia(r.t.f.db,r.t.f.journeys,r.t.f.store,small,p->true,(p,f)->new CargasMultimedia.Inspeccion(f.tipo(),"DESCARGA",null),r.t.f.clock,r.t.f.mapper)){
   var input=new CargasMultimedia.Inicio("sintetico.txt",r.t.f.text().length,MultimediaJdbcTest.sha(r.t.f.text()));
   assertEquals(507,assertThrows(FalloTecnico.class,()->media.iniciar(next,r.t.f.other,input)).status);
   at(Duration.ofHours(72).toMillis()-1);assertThrows(IOException.class,()->motor(r.t.f.db,REF,(p,id)->{}).retirar(r.t.communication));r.t.intact();
   at(Duration.ofHours(72).toMillis());assertEquals(1,r.app.ejecutar().minimizadas());assertEquals(0,r.t.reserved());var fresh=r.t.f.journeys.iniciar(IdentificacionCiudadana.Tipo.NNA,false,"NNA_TEST_1",r.t.f.other,"127.0.0.9").id();assertNotNull(media.iniciar(fresh,r.t.f.other,input));
  }
 }
 @Test void indicadoresNoConfundenGarantiaAusenteConBloqueoEnModalidadAck()throws Exception{
  receive(false);var state=r.app.estado();
  assertEquals("ACK_RETENCION_LIMITADA_APROBADA",state.get("modalidadRetirada"));assertEquals(259200L,state.get("retencionSegundos"));
  var full=ObservabilidadCustodiaTest.map(state,"completo");assertEquals(0L,full.get("confirmadasEsperandoGarantia"));assertEquals(false,full.get("garantiaRequeridaParaRetirada"));assertEquals(1L,full.get("esperandoRetencionTecnica"));
  var json=r.t.f.mapper.writeValueAsString(state);assertFalse(json.contains(REF));assertFalse(json.contains(r.t.communication.toString()));assertFalse(json.contains(r.t.f.session));
 }
 @Test void historicoUsaPrimerAckYMinimizaSinGarantiaConservandoConsulta()throws Exception{
  var helper=new CierreV10CustodiaTest();helper.r=r;var id=helper.historical();r.app=app(true,REF);
  long first=r.t.f.db.sql.queryForObject("SELECT first_ack_ms FROM public_delivery WHERE id=?",Long.class,id.toString());
  r.t.now.set(first+Duration.ofHours(72).toMillis()-1);assertEquals(0,r.app.ejecutar().minimizadas());
  r.app.conservar("HISTORICO",id,true);r.t.now.incrementAndGet();assertEquals(0,r.app.ejecutar().minimizadas());
  r.app.conservar("HISTORICO",id,false);assertEquals(1,r.app.ejecutar().minimizadas());
  var row=r.t.f.db.sql.queryForMap("SELECT * FROM public_delivery WHERE id=?",id.toString());assertNull(row.get("body"));assertNull(row.get("owner_run"));assertEquals(REF,row.get("retirement_policy_ref"));
  assertNotNull(ReciboMinimo.validar(row.get("minimal_receipt").toString(),id,Map.of("publico",r.t.publicKey.getPublic()),Map.of("institucion",r.t.institutional.getPublic()),r.t.f.mapper));
  assertEquals(409,assertThrows(FalloTecnico.class,()->r.app.conservar("HISTORICO",id,true)).status);
 }
 @Test void discrepanciaDeclaradaFirmadaNoSeMinimizaAutomaticamente()throws Exception{
  receive(false);var old=r.t.f.mapper.readValue(r.t.signed,EntregaFormulario.class);var c=old.comunicacion();var p=c.personas().getFirst();
  var person=new IdentificacionCiudadana.Persona(p.id(),p.rol(),p.runNumero(),p.runDv(),p.modulo11(),p.autenticada(),p.estadoConsulta(),p.consultadaEn(),p.consultaRef(),p.atributos(),Map.of("primerNombre",p.atributos().get("primerNombre")));
  var content=new EntregaFormulario.Contenido(c.version(),c.id(),c.recorridoId(),c.tipo(),c.recibidaEn(),c.avisoVersion(),c.aceptaCanalDigital(),c.datos(),List.of(person),c.adjuntos());
  var hash=CanonFormulario.huella("CONTENIDO",content,r.t.f.mapper);var source=EntregaFormulario.procedencia(content,r.t.f.mapper);
  var proof=ComprobanteDigital.firmar(new ComprobanteDigital.Contenido("1.0",c.id(),c.recibidaEn(),"CUSTODIA_PUBLICA",hash,source,"FORMULARIO_PUBLICO_DDN",c.avisoVersion()),"publico",r.t.publicKey.getPrivate(),r.t.f.mapper);
  var delivery=new EntregaFormulario(content,hash,source,proof);delivery.validar(Map.of("publico",r.t.publicKey.getPublic()),r.t.f.mapper);
  var reg=new ComprobanteInstitucional.Contenido("1.1","SIGER_DDN","COMUNICACION_REGISTRADA",c.id(),UUID.randomUUID(),1,"COM-1",r.t.clock.instant(),"FORMULARIO_WEB_NNA",hash,source);
  var ack=new RegistroInstitucional(reg,ComprobanteInstitucional.firmar(reg,"institucion",r.t.institutional.getPrivate(),r.t.f.mapper));delivery.validarRegistro(ack,Map.of("institucion",r.t.institutional.getPublic()),r.t.f.mapper);
  r.t.f.db.sql.update("UPDATE citizen_submission SET body=?,ack=? WHERE id=?",r.t.f.mapper.writeValueAsString(delivery),r.t.f.mapper.writeValueAsString(ack),c.id().toString());
  at(Duration.ofDays(4).toMillis());assertEquals(0,r.app.ejecutar().minimizadas());assertTrue(Files.exists(r.t.f.store.original(r.t.attachment)));assertTrue(r.t.reserved()>0);
 }
 @Test void contabilidadDiscrepanteNoSeCorrigeBorrandoArchivos()throws Exception{
  receive(false);at(Duration.ofDays(4).toMillis());r.t.f.db.sql.update("UPDATE media_capacity SET reserved_bytes=reserved_bytes+1 WHERE id=1");
  assertEquals(0,r.app.ejecutar().minimizadas());r.t.intact();assertEquals(0,r.t.f.db.sql.queryForObject("SELECT COUNT(*) FROM storage_retirement",Integer.class));
 }
 @Test void historicoRetencionNoSeReiniciaConDuplicadoNiRetiraConAckAlterado()throws Exception{
  var helper=new CierreV10CustodiaTest();helper.r=r;var id=helper.historical();r.app=app(true,REF);
  var before=r.t.f.db.sql.queryForMap("SELECT * FROM public_delivery WHERE id=?",id.toString());long first=((Number)before.get("first_ack_ms")).longValue();
  r.t.now.set(first+Duration.ofHours(72).toMillis());
  var legacy=new RecepcionJdbc(r.t.f.db,r.t.f.mapper,"TEST",true,"publico",r.t.publicKey.getPrivate(),Map.of("publico",r.t.publicKey.getPublic()),r.t.clock,300000,Map.of("institucion",r.t.institutional.getPublic()),r.ownerKey);
  var ack=r.t.f.mapper.readValue(before.get("ack").toString(),RegistroInstitucional.class);
  legacy.confirmarRegistro(id,UUID.fromString(before.get("lease_id").toString()),ack);
  assertEquals(first,r.t.f.db.sql.queryForObject("SELECT first_ack_ms FROM public_delivery WHERE id=?",Long.class,id.toString()));
  r.t.f.db.sql.update("UPDATE public_delivery SET ack='{}' WHERE id=?",id.toString());assertEquals(0,r.app.ejecutar().minimizadas());assertNotNull(r.t.f.db.sql.queryForObject("SELECT body FROM public_delivery WHERE id=?",String.class,id.toString()));
  r.t.f.db.sql.update("UPDATE public_delivery SET ack=? WHERE id=?",before.get("ack"),id.toString());assertEquals(1,r.app.ejecutar().minimizadas());
 }

}
