package cl.nexbyte.siger.robustez;
import cl.nexbyte.siger.custodia.*;
import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.multimedia.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Garantías sintéticas controladas; la prueba con el emisor y JAR SIGER se ejecuta por separado. */
class ReceptorTransitorioTest {
 @TempDir Path temp;CicloVidaCustodiaTest t;KeyPair witness;GarantiasCustodiaJdbc proofs;CustodiaTransitoria app;
 final byte[] ownerKey=new byte[32];
 @BeforeEach void setup()throws Exception{
  t=new CicloVidaCustodiaTest();t.temp=temp;t.setup();witness=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
  t.f.db.sql.update("DELETE FROM custody_guarantee");t.f.db.sql.update("UPDATE transient_maintenance SET lease_id=NULL,lease_until_ms=0,last_ms=0,last_code='CERRADO'");
  proofs=new GarantiasCustodiaJdbc(t.f.db,t.f.mapper,Map.of("publico",t.publicKey.getPublic()),Map.of("institucion",t.institutional.getPublic()),Map.of("witness",witness.getPublic()),t.clock);
 }
 @AfterEach void close()throws Exception{if(app!=null)app.close();if(t!=null&&t.f!=null&&t.f.db!=null){t.f.db.sql.update("DELETE FROM custody_guarantee");t.f.db.sql.update("DELETE FROM public_delivery");t.close();}}
 CustodiaTransitoria service(boolean enabled){return new CustodiaTransitoria(t.f.db,t.f.store,false,t.f.mapper,proofs,Map.of("publico",t.publicKey.getPublic()),Map.of("institucion",t.institutional.getPublic()),"publico",t.publicKey.getPrivate(),ownerKey,t.clock,new CicloVidaCustodia.Politica(enabled,Duration.ofHours(72),Duration.ofHours(48)));}
 void receive(boolean blob)throws Exception{t.receive(blob);t.f.media.close();}
 FirmaOperativa proof(UUID id,String queue)throws Exception{
  String table=queue.equals("COMPLETO")?"citizen_submission":"public_delivery";var r=t.f.db.sql.queryForMap("SELECT body,ack FROM "+table+" WHERE id=?",id.toString());
  var c=GarantiaCustodia.contexto(r.get("body").toString(),r.get("ack").toString(),queue,Map.of("publico",t.publicKey.getPublic()),Map.of("institucion",t.institutional.getPublic()),t.f.mapper);
  return FirmaOperativa.firmar(new GarantiaCustodia.Contenido(c,t.clock.instant(),t.clock.instant().plus(Duration.ofHours(24)),UUID.randomUUID()),GarantiaCustodia.DOMAIN,"witness",witness.getPrivate(),t.f.mapper);
 }
 DatosFormulario data(){return new DatosFormulario(Map.of("nna-region","13","nna-primary-method","unsafe","nna-story","Relato sintético"),List.of(),List.of(),List.of(t.attachment));}
 @ParameterizedTest @ValueSource(booleans={false,true}) void retiraPayloadIdentidadNombreArchivoYBytesConservandoReciboEIdempotencia(boolean blob)throws Exception{
  receive(blob);app=service(true);proofs.aceptar(proof(t.communication,"COMPLETO"));app.ejecutar();t.due();proofs.aceptar(proof(t.communication,"COMPLETO"));
  String person=t.f.db.sql.queryForObject("SELECT body FROM citizen_person WHERE journey_id=?",String.class,t.f.journey.toString());
  t.f.db.sql.update("INSERT INTO citizen_identity_history(id,journey_id,person_id,body,query_state,retired_ms) VALUES(?,?,?,?,?,?)",UUID.randomUUID().toString(),t.f.journey.toString(),"presentador-nna",person,"REVISADO",t.now.get());
  var expected=t.reception.consultar(t.communication,t.f.session);var result=app.ejecutar();assertEquals(1,result.minimizadas());
  assertNull(t.f.db.sql.queryForObject("SELECT body FROM citizen_submission WHERE id=?",String.class,t.communication.toString()));
  assertEquals(0,t.f.db.sql.queryForObject("SELECT COUNT(*) FROM citizen_person WHERE journey_id=?",Integer.class,t.f.journey.toString()));assertEquals(0,t.f.db.sql.queryForObject("SELECT COUNT(*) FROM citizen_identity_history WHERE journey_id=?",Integer.class,t.f.journey.toString()));
  assertEquals("retirado",t.f.db.sql.queryForObject("SELECT name FROM citizen_attachment WHERE id=?",String.class,t.attachment.toString()));assertEquals(0,t.reserved());
  assertEquals(expected,t.reception.consultar(t.communication,t.f.session));assertEquals(expected,t.reception.recibir(t.communication,t.f.journey,t.f.session,data()));
  t.reception.confirmar(t.communication,t.lease.reserva(),expected.registro());assertEquals(0,app.ejecutar().minimizadas());
  var receipt=ReciboMinimo.validar(t.f.db.sql.queryForObject("SELECT minimal_receipt FROM citizen_submission WHERE id=?",String.class,t.communication.toString()),t.communication,Map.of("publico",t.publicKey.getPublic()),Map.of("institucion",t.institutional.getPublic()),t.f.mapper);
  assertEquals(expected.custodia(),receipt.custodia());assertEquals(1,receipt.archivos().size());assertEquals(t.attachment,receipt.archivos().getFirst().id());
  assertEquals(404,assertThrows(FalloTecnico.class,()->t.f.journeys.consultar(t.f.journey,t.f.session)).status);
  assertEquals(409,assertThrows(FalloTecnico.class,()->t.reception.recibir(t.communication,t.f.journey,t.f.session,new DatosFormulario(Map.of("nna-story","alterado"),List.of(),List.of(),List.of()))).status);
 }
 @Test void firmaDeAckNoEsGarantiaYUnaGarantiaCaducadaNoBorra()throws Exception{
  receive(false);app=service(true);app.ejecutar();t.due();app.ejecutar();t.intact();
  var valid=proof(t.communication,"COMPLETO");proofs.aceptar(valid);t.now.addAndGet(Duration.ofHours(25).toMillis());app.ejecutar();t.intact();assertTrue(t.reserved()>0);
  proofs.aceptar(proof(t.communication,"COMPLETO"));assertEquals(1,app.ejecutar().minimizadas());
 }
 @Test void garantiaFalsaYReplayNoActualizanLaVentana()throws Exception{
  receive(false);var valid=proof(t.communication,"COMPLETO");proofs.aceptar(valid);proofs.aceptar(valid);assertEquals(1,t.f.db.sql.queryForObject("SELECT COUNT(*) FROM custody_guarantee",Integer.class));
  var c=valid.leer(GarantiaCustodia.DOMAIN,GarantiaCustodia.Contenido.class,Map.of("witness",witness.getPublic()),t.f.mapper);var other=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
  assertThrows(IllegalArgumentException.class,()->proofs.aceptar(FirmaOperativa.firmar(c,GarantiaCustodia.DOMAIN,"witness",other.getPrivate(),t.f.mapper)));
  t.now.addAndGet(1000);proofs.aceptar(proof(t.communication,"COMPLETO"));assertEquals(409,assertThrows(FalloTecnico.class,()->proofs.aceptar(valid)).status);
  assertThrows(IllegalArgumentException.class,()->new GarantiasCustodiaJdbc(t.f.db,t.f.mapper,Map.of("publico",t.publicKey.getPublic()),Map.of("institucion",t.institutional.getPublic()),Map.of("wrong",t.institutional.getPublic()),t.clock));
 }
 @Test void flagCerradaYHoldProtegenPayloadYArchivos()throws Exception{
  receive(false);app=service(false);proofs.aceptar(proof(t.communication,"COMPLETO"));app.ejecutar();t.due();proofs.aceptar(proof(t.communication,"COMPLETO"));app.ejecutar();t.intact();app.close();app=service(true);
  app.conservar("COMPLETO",t.communication,true);app.ejecutar();t.intact();app.conservar("COMPLETO",t.communication,false);assertEquals(1,app.ejecutar().minimizadas());
 }
 @Test void descargaActivaBloqueaSoloSuRecorridoMientrasOtroPuedeRecibir()throws Exception{
  receive(false);app=service(true);proofs.aceptar(proof(t.communication,"COMPLETO"));app.ejecutar();t.due();proofs.aceptar(proof(t.communication,"COMPLETO"));
  try(var active=t.f.store.lectura(t.f.journey)){
   assertEquals(0,app.ejecutar().minimizadas());
   var other=t.f.journeys.iniciar(IdentificacionCiudadana.Tipo.NNA,false,"NNA_TEST_1",t.f.other,"127.0.0.4").id();assertNotNull(other);
   t.f.journeys.identificar(other,"presentador-nna",IdentificacionCiudadana.Rol.PRESENTADOR,null,null,t.f.other,"127.0.0.4",null);
   t.f.journeys.declarar(other,"presentador-nna",Map.of("primerNombre","NUEVO","primerApellido","SINTETICO"),t.f.other);
   assertEquals("PENDIENTE_REGISTRO_SIGER",t.reception.recibir(UUID.randomUUID(),other,t.f.other,new DatosFormulario(Map.of("nna-region","13","nna-primary-method","unsafe","nna-story","Otra recepción durante el mantenimiento"),List.of(),List.of(),List.of())).estado());t.intact();
  }
  assertEquals(1,app.ejecutar().minimizadas());assertEquals("NORMAL",new ControlesOperacion(t.f.db).estado().contingencia());assertTrue(new ControlesOperacion(t.f.db).estado().aceptarNuevos());
 }
 @Test void abandonoSinArchivosTambienRetiraIdentidadYElPendienteSeConserva()throws Exception{
  app=service(true);t.due();assertNotNull(t.f.db.sql.queryForObject("SELECT body FROM citizen_person",String.class));app.ejecutar();assertEquals(0,t.f.db.sql.queryForObject("SELECT COUNT(*) FROM citizen_person",Integer.class));
 }
 @Test void pendienteNoSeMinimizaAunqueVenzaElRecorrido()throws Exception{
  receive(false);t.f.db.sql.update("UPDATE citizen_submission SET state='PENDIENTE',ack=NULL WHERE id=?",t.communication.toString());app=service(true);t.due();app.ejecutar();assertEquals(t.signed,t.f.db.sql.queryForObject("SELECT body FROM citizen_submission WHERE id=?",String.class,t.communication.toString()));assertTrue(Files.exists(t.f.store.original(t.attachment)));
 }
 @Test void historicoRetiraRelatoRunYLegacyAckPeroMantieneConsultaAutorizada()throws Exception{
  var id=UUID.randomUUID();var identity=RecepcionJdbcTest.IDENTITY;
  var legacy=new RecepcionJdbc(t.f.db,t.f.mapper,"TEST",true,"publico",t.publicKey.getPrivate(),Map.of("publico",t.publicKey.getPublic()),t.clock,300000,Map.of("institucion",t.institutional.getPublic()),ownerKey);
  var sobre=legacy.recibirVerificada(id,identity,new cl.nexbyte.siger.identidad.EvidenciaAutenticacion(UUID.randomUUID(),t.clock.instant(),cl.nexbyte.siger.identidad.EvidenciaAutenticacion.de(identity).identidadHash()),"Relato histórico sensible","TEST");var lease=legacy.reclamar(1).getFirst();
  var e=t.f.mapper.readValue(t.f.db.sql.queryForObject("SELECT body FROM public_delivery WHERE id=?",String.class,id.toString()),EntregaVerificada.class);
  var content=new ComprobanteInstitucional.Contenido("1.0","SIGER_DDN","COMUNICACION_REGISTRADA",id,UUID.randomUUID(),1,"COM-1",t.clock.instant(),"FORMULARIO_WEB_PUBLICO",sobre.sha256(),EntregaVerificada.huella(e.procedencia()));var ack=new RegistroInstitucional(content,ComprobanteInstitucional.firmar(content,"institucion",t.institutional.getPrivate(),t.f.mapper));legacy.confirmarRegistro(id,lease.reserva(),ack);
  var expected=legacy.consultar(id,identity.run());app=service(true);app.ejecutar();t.due();proofs.aceptar(proof(id,"HISTORICO"));assertEquals(1,app.ejecutar().minimizadas());
  var row=t.f.db.sql.queryForMap("SELECT * FROM public_delivery WHERE id=?",id.toString());assertNull(row.get("body"));assertNull(row.get("owner_run"));assertNull(row.get("legacy_ack"));assertNotNull(row.get("owner_fingerprint"));assertEquals(expected,legacy.consultar(id,identity.run()));
  legacy.confirmarRegistro(id,lease.reserva(),ack);assertEquals(410,assertThrows(FalloTecnico.class,()->legacy.recibirVerificada(id,identity,new cl.nexbyte.siger.identidad.EvidenciaAutenticacion(UUID.randomUUID(),t.clock.instant(),cl.nexbyte.siger.identidad.EvidenciaAutenticacion.de(identity).identidadHash()),"Relato histórico sensible","TEST")).status);
  assertEquals(1,t.f.db.sql.queryForObject("SELECT COUNT(*) FROM public_delivery",Integer.class));assertEquals(404,assertThrows(FalloTecnico.class,()->legacy.consultar(id,"12345678-5")).status);
 }
 @Test void selloMinimoAlteradoSeDetectaSinReconstituirDatosPersonales()throws Exception{
  receive(false);app=service(true);proofs.aceptar(proof(t.communication,"COMPLETO"));app.ejecutar();t.due();proofs.aceptar(proof(t.communication,"COMPLETO"));app.ejecutar();t.f.db.sql.update("UPDATE citizen_submission SET minimal_receipt='{}' WHERE id=?",t.communication.toString());assertEquals(503,assertThrows(FalloTecnico.class,()->t.reception.consultar(t.communication,t.f.session)).status);
 }
 @Test void seisCiclosNoAcumulanPayloadsIdentidadesNiBinarios()throws Exception{
  app=service(true);
  for(int i=0;i<6;i++){
   if(i>0){t.f.journey=t.f.journeys.iniciar(IdentificacionCiudadana.Tipo.NNA,false,"NNA_TEST_1",t.f.session,"127.0.0."+(i+5)).id();t.f.journeys.identificar(t.f.journey,"presentador-nna",IdentificacionCiudadana.Rol.PRESENTADOR,null,null,t.f.session,"127.0.0.5",null);t.f.journeys.declarar(t.f.journey,"presentador-nna",Map.of("primerNombre","PRIVADO","primerApellido","SINTETICO"),t.f.session);t.f.media=t.f.service();}
   receive(i%2==1);proofs.aceptar(proof(t.communication,"COMPLETO"));app.ejecutar();t.due();proofs.aceptar(proof(t.communication,"COMPLETO"));assertEquals(1,app.ejecutar().minimizadas());
   assertEquals(0,t.f.db.sql.queryForObject("SELECT COUNT(*) FROM citizen_submission WHERE body IS NOT NULL",Integer.class));assertEquals(0,t.f.db.sql.queryForObject("SELECT COUNT(*) FROM citizen_person",Integer.class));assertEquals(0,t.reserved());
  }
  assertEquals(6,t.f.db.sql.queryForObject("SELECT COUNT(*) FROM citizen_submission WHERE content_retired=TRUE",Integer.class));
  long retained=t.f.db.sql.queryForObject("SELECT SUM(OCTET_LENGTH(minimal_receipt)+OCTET_LENGTH(ack)) FROM citizen_submission",Long.class);assertTrue(retained<6*8192);System.out.println("METRICAS_MINIMAS_SINTETICAS comunicaciones=6 bytesRecibos="+retained);
 }
 @Test void presupuestoDeDiscoCierraIngresosNuevosPeroNoConsultaNiIdempotencia()throws Exception{
  receive(false);var allowed=new java.util.concurrent.atomic.AtomicBoolean(false);t.reception.admision(allowed::get);assertFalse(t.reception.disponible());assertEquals("REGISTRADA_SIGER",t.reception.consultar(t.communication,t.f.session).estado());
  t.f.journeys.admision(allowed::get);assertEquals(507,assertThrows(FalloTecnico.class,()->t.f.journeys.iniciar(IdentificacionCiudadana.Tipo.NNA,false,"NNA_TEST_1",t.f.other,"127.0.0.8")).status);
  assertEquals("REGISTRADA_SIGER",t.reception.recibir(t.communication,t.f.journey,t.f.session,data()).estado());
 }
 @Test void falloAlLiberarLeaseNoDejaElProgramadorAtascado()throws Exception{
  app=service(true);
  TestDatabase.migration(t.f.db,sql->sql.execute("ALTER TABLE transient_maintenance ADD CONSTRAINT test_lease_release CHECK (lease_id IS NOT NULL OR last_code='CERRADO')"));
  try{assertThrows(RuntimeException.class,()->app.ejecutar());assertEquals(false,app.estado().get("ejecutando"));}
  finally{TestDatabase.migration(t.f.db,sql->sql.execute("ALTER TABLE transient_maintenance DROP "+(System.getenv("ROBUST_DB_URL")==null?"CONSTRAINT":"CHECK")+" test_lease_release"));}
  t.now.addAndGet(Duration.ofMinutes(11).toMillis());assertFalse(app.ejecutar().solapada());
 }

 @Test void metadatosDeBorradorVencenSinBorrarRecibosDeComunicaciones()throws Exception{
  app=service(true);t.due();app.ejecutar();assertEquals(0,t.f.db.sql.queryForObject("SELECT COUNT(*) FROM citizen_person",Integer.class));
  t.now.addAndGet(Duration.ofDays(8).toMillis());app.ejecutar();assertEquals(0,t.f.db.sql.queryForObject("SELECT COUNT(*) FROM citizen_journey",Integer.class));assertFalse(app.ejecutar().solapada());
 }

 @Test void temporalesDeCaidaSeLimpianOnlineSinTocarActivosNiOriginales()throws Exception{
  receive(false);var store=t.f.store;var active=store.temporal();Files.writeString(active,"en curso");var stale=store.raiz().resolve("incoming/carga-huerfana.part");Files.writeString(stale,"descartable");var old=java.nio.file.attribute.FileTime.fromMillis(t.clock.millis()-Duration.ofDays(2).toMillis());Files.setLastModifiedTime(active,old);Files.setLastModifiedTime(stale,old);
  try{var cleaned=store.limpiarIncoming(t.clock.millis()-Duration.ofDays(1).toMillis(),20);assertEquals(1,cleaned.archivos());assertTrue(Files.exists(active));assertFalse(Files.exists(stale));t.intact();}finally{store.liberarTemporal(active);}
 }

 @Test void otraComunicacionSeRecibeMientrasElMantenimientoEstaEnEjecucion()throws Exception{
  receive(false);app=service(true);proofs.aceptar(proof(t.communication,"COMPLETO"));app.ejecutar();t.due();proofs.aceptar(proof(t.communication,"COMPLETO"));app.close();
  var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var once=new java.util.concurrent.atomic.AtomicBoolean();
  var slow=org.mockito.Mockito.spy(proofs);
  org.mockito.Mockito.doAnswer(call->{if(once.compareAndSet(false,true)){entered.countDown();if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new java.io.IOException("Pausa sintética agotada");}return call.callRealMethod();}).when(slow).comprobar(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("COMPLETO"));
  app=new CustodiaTransitoria(t.f.db,t.f.store,false,t.f.mapper,slow,Map.of("publico",t.publicKey.getPublic()),Map.of("institucion",t.institutional.getPublic()),"publico",t.publicKey.getPrivate(),ownerKey,t.clock,new CicloVidaCustodia.Politica(true,Duration.ofHours(72),Duration.ofHours(48)));
  try(var pool=java.util.concurrent.Executors.newSingleThreadExecutor()){
   var running=pool.submit(app::ejecutar);try{assertTrue(entered.await(3,java.util.concurrent.TimeUnit.SECONDS));assertEquals(true,app.estado().get("ejecutando"));
    var route=t.f.journeys.iniciar(IdentificacionCiudadana.Tipo.NNA,false,"NNA_TEST_1",t.f.other,"127.0.0.4").id();t.f.journeys.identificar(route,"presentador-nna",IdentificacionCiudadana.Rol.PRESENTADOR,null,null,t.f.other,"127.0.0.4",null);t.f.journeys.declarar(route,"presentador-nna",Map.of("primerNombre","OTRA","primerApellido","RECEPCION"),t.f.other);
    assertEquals("PENDIENTE_REGISTRO_SIGER",t.reception.recibir(UUID.randomUUID(),route,t.f.other,new DatosFormulario(Map.of("nna-region","13","nna-primary-method","unsafe","nna-story","Ingreso sin pausa global"),List.of(),List.of(),List.of())).estado());
   }finally{release.countDown();}
   assertEquals(1,running.get(5,java.util.concurrent.TimeUnit.SECONDS).minimizadas());
  }
 }

 @Test void perfilDe24HorasRetiraSoloConGarantiaFrescaYConservaComprobante()throws Exception{
  receive(false);app=new CustodiaTransitoria(t.f.db,t.f.store,false,t.f.mapper,proofs,Map.of("publico",t.publicKey.getPublic()),Map.of("institucion",t.institutional.getPublic()),"publico",t.publicKey.getPrivate(),ownerKey,t.clock,new CicloVidaCustodia.Politica(true,Duration.ofHours(24),Duration.ofHours(48)));
  proofs.aceptar(proof(t.communication,"COMPLETO"));assertEquals(0,app.ejecutar().minimizadas());t.now.addAndGet(Duration.ofHours(25).toMillis());assertEquals(0,app.ejecutar().minimizadas());t.intact();proofs.aceptar(proof(t.communication,"COMPLETO"));assertEquals(1,app.ejecutar().minimizadas());assertEquals("REGISTRADA_SIGER",t.reception.consultar(t.communication,t.f.session).estado());
 }

 @Test void falloSqlDeMinimizacionRevierteDatosYReintentaSinPerderComprobante()throws Exception{
  receive(false);app=service(true);proofs.aceptar(proof(t.communication,"COMPLETO"));app.ejecutar();t.due();proofs.aceptar(proof(t.communication,"COMPLETO"));
  TestDatabase.migration(t.f.db,sql->sql.execute("ALTER TABLE custody_lifecycle ADD CONSTRAINT test_min_rollback CHECK (phase<>'MINIMIZADA')"));
  try{assertEquals(0,app.ejecutar().minimizadas());assertEquals(t.signed,t.f.db.sql.queryForObject("SELECT body FROM citizen_submission",String.class));assertEquals(1,t.f.db.sql.queryForObject("SELECT COUNT(*) FROM citizen_person",Integer.class));assertNull(t.f.db.sql.queryForObject("SELECT minimal_receipt FROM citizen_submission",String.class));assertEquals(0,t.reserved());}
  finally{TestDatabase.migration(t.f.db,sql->sql.execute("ALTER TABLE custody_lifecycle DROP "+(System.getenv("ROBUST_DB_URL")==null?"CONSTRAINT":"CHECK")+" test_min_rollback"));}
  assertEquals(1,app.ejecutar().minimizadas());assertEquals("REGISTRADA_SIGER",t.reception.consultar(t.communication,t.f.session).estado());
 }

 @Test void candidatosSinGarantiaNoImpidenRevisarLosSiguientes()throws Exception{
  app=service(false);
  for(int i=0;i<21;i++){String id=UUID.randomUUID().toString(),journey=UUID.randomUUID().toString();t.f.db.sql.update("INSERT INTO citizen_journey(id,owner_session,kind,notice_version,accepted_channel,state,query_count,created_ms,expires_ms) VALUES(?,?,'NNA','TEST',FALSE,'RECIBIDO',0,?,?)",journey,t.f.session,t.now.get(),t.now.get()+1000);t.f.db.sql.update("INSERT INTO citizen_submission(id,journey_id,owner_session,request_hash,body,state,created_ms,next_attempt_ms,ack,document_version) VALUES(?,?,?,?,'{}','CONFIRMADA',?,0,'{}','1.0')",id,journey,t.f.session,"0".repeat(64),t.now.get());}
  var ids=new HashSet<Object>();app.candidatos(20).forEach(r->ids.add(r.get("id")));app.candidatos(20).forEach(r->ids.add(r.get("id")));assertEquals(21,ids.size());
 }

}