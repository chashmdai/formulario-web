package cl.nexbyte.siger.robustez;
import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.multimedia.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import static cl.nexbyte.siger.formulario.IdentificacionCiudadana.*;
import static org.junit.jupiter.api.Assertions.*;
/** Archivos e identidades sintéticos, scanner/inspector controlados; ningún proveedor externo. */
class MultimediaJdbcTest{
 @TempDir Path temp;BaseJdbc db;RecorridosJdbc journeys;AlmacenPrivado store;CargasMultimedia media;UUID journey;final String session="a".repeat(64),other="b".repeat(64);final JsonMapper mapper=JsonMapper.builder().build();
 AtomicLong free=new AtomicLong(1L<<30);boolean clean=true,available=true,valid=true;AtomicInteger inspected=new AtomicInteger();
 final Clock clock=Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"),ZoneOffset.UTC);
 final PoliticaMultimedia policy=new PoliticaMultimedia(12,1024,4,512,512,512,512,16384,0,60000);
 @BeforeEach void setup()throws Exception{
  db=TestDatabase.open(temp);journeys=new RecorridosJdbc(db,mapper,r->{throw new AssertionError("No consultar proveedor");},r->false,clock,new byte[32],"ADULTO_TEST_1","NNA_TEST_1");
  var controls=new ControlesOperacion(db);controls.cambiar(controls.estado().revision(),true,true,"NORMAL","PRUEBA_LOCAL");journey=journeys.iniciar(Tipo.NNA,false,"NNA_TEST_1",session,"127.0.0.1").id();journeys.identificar(journey,"presentador-nna",Rol.PRESENTADOR,null,null,session,"127.0.0.1",null);journeys.declarar(journey,"presentador-nna",Map.of("primerNombre","NOMBRE","primerApellido","APELLIDO"),session);
  store=new AlmacenPrivado(temp.resolve("archivos"),free::get);media=service();
 }
 CargasMultimedia service(){return new CargasMultimedia(db,journeys,store,policy,p->{if(!available)throw new IOException("Fallo controlado");return clean;},(p,f)->{inspected.incrementAndGet();if(!valid)throw new IllegalArgumentException("Formato falso");return new CargasMultimedia.Inspeccion(f.tipo(),"DESCARGA",null);},clock,mapper);}
 @AfterEach void close(){if(media!=null)media.close();if(store!=null)try{store.close();}catch(IOException e){throw new RuntimeException(e);}if(db!=null){db.sql.update("DELETE FROM media_access");db.sql.update("DELETE FROM media_chunk");db.sql.update("DELETE FROM citizen_attachment");db.sql.update("DELETE FROM media_upload");db.sql.update("UPDATE media_capacity SET reserved_bytes=0");db.sql.update("DELETE FROM citizen_submission");db.sql.update("DELETE FROM citizen_identity_history");db.sql.update("DELETE FROM citizen_person");db.sql.update("DELETE FROM citizen_journey");db.sql.update("DELETE FROM citizen_abuse_bucket");db.close();}}
 static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
 byte[] text(){return "contenido sintético privado".getBytes(StandardCharsets.UTF_8);}
 CargasMultimedia.Estado start(byte[] bytes)throws Exception{return media.iniciar(journey,session,new CargasMultimedia.Inicio("respaldo.txt",bytes.length,sha(bytes)));}
 CargasMultimedia.Estado send(CargasMultimedia.Estado state,byte[] bytes)throws Exception{
  for(int i=0;4*i<bytes.length;i++){byte[] block=Arrays.copyOfRange(bytes,i*4,Math.min(bytes.length,i*4+4));state=media.bloque(journey,state.id(),session,i,sha(block),block.length,new ByteArrayInputStream(block));}return state;
 }
 CargasMultimedia.Estado finish(UUID id)throws Exception{
  media.finalizar(journey,id,session);long end=System.nanoTime()+5_000_000_000L;CargasMultimedia.Estado state;
  do{state=media.estado(journey,id,session);if(!state.estado().equals("ANALIZANDO"))return state;Thread.sleep(5);}while(System.nanoTime()<end);throw new AssertionError("Análisis no terminó");
 }
 CargasMultimedia.Estado upload(byte[] bytes)throws Exception{var state=send(start(bytes),bytes);state=finish(state.id());assertEquals("VALIDADO",state.estado());return state;}
 void publish(UUID id){var old=media.lote(journey,session);media.confirmar(journey,session,new CargasMultimedia.Manifest(List.of(id),old.revision()));}
 @Test void bloquesSeReanudanTrasRecrearServicioSinDuplicarNiCambiarHash()throws Exception{
  var bytes=text();var state=start(bytes);var first=Arrays.copyOfRange(bytes,0,4);state=media.bloque(journey,state.id(),session,0,sha(first),-1,new ByteArrayInputStream(first));assertEquals(4,state.recibidos());media.close();store.close();store=new AlmacenPrivado(temp.resolve("archivos"),free::get);media=service();
  var replay=media.bloque(journey,state.id(),session,0,sha(first),4,new ByteArrayInputStream(first));assertEquals(4,replay.recibidos());assertEquals(state.id(),start(bytes).id());
  state=send(state,bytes);assertEquals(bytes.length,state.recibidos());assertEquals("VALIDADO",finish(state.id()).estado());assertArrayEquals(bytes,Files.readAllBytes(store.original(state.id())));assertEquals(1,inspected.get());
 }
 @Test void cuerpoParcialHashIncorrectoYOrdenInvalidoNoAdelantanOffset()throws Exception{
  var bytes=text();var state=start(bytes);var id=state.id();var part=Arrays.copyOfRange(bytes,0,4);
  assertEquals(503,assertThrows(FalloTecnico.class,()->media.bloque(journey,id,session,0,sha(part),4,new ByteArrayInputStream(new byte[]{1}))).status);
  assertEquals(422,assertThrows(FalloTecnico.class,()->media.bloque(journey,id,session,0,"0".repeat(64),4,new ByteArrayInputStream(part))).status);
  assertEquals(409,assertThrows(FalloTecnico.class,()->media.bloque(journey,id,session,1,sha(part),4,new ByteArrayInputStream(part))).status);assertEquals(0,media.estado(journey,id,session).recibidos());
 }
 @Test void crucesDeSesionYRecorridoNoRevelanArchivos()throws Exception{
  var state=start(text());assertEquals(404,assertThrows(FalloTecnico.class,()->media.estado(journey,state.id(),other)).status);
  var second=journeys.iniciar(Tipo.NNA,false,"NNA_TEST_1",other,"127.0.0.1").id();assertEquals(404,assertThrows(FalloTecnico.class,()->media.estado(second,state.id(),other)).status);assertEquals(404,assertThrows(FalloTecnico.class,()->media.cancelar(second,state.id(),other)).status);
 }
 @Test void hashCompletoInvalidoNoLlegaAInspeccionNiReemplazaLote()throws Exception{
  var before=upload(text());publish(before.id());var lote=media.lote(journey,session);var bytes="contenido distinto".getBytes(StandardCharsets.UTF_8);
  var state=media.iniciar(journey,session,new CargasMultimedia.Inicio("otro.txt",bytes.length,"0".repeat(64)));send(state,bytes);assertEquals("RECHAZADO",finish(state.id()).estado());assertEquals(lote,media.lote(journey,session));assertEquals(1,inspected.get());
 }
 @Test void antivirusCaidoEsReintentableYMalwareNoSePublica()throws Exception{
  var state=send(start(text()),text());available=false;assertEquals("REINTENTABLE",finish(state.id()).estado());available=true;clean=false;assertEquals("RECHAZADO",finish(state.id()).estado());assertTrue(media.lote(journey,session).archivos().isEmpty());assertEquals(0,inspected.get());
 }
 @Test void falsificacionDeFormatoSeRechazaYManifiestoAnteriorPermanece()throws Exception{
  var before=upload(text());publish(before.id());var previous=media.lote(journey,session);var bytes="otro contenido".getBytes(StandardCharsets.UTF_8);var state=send(start(bytes),bytes);valid=false;assertEquals("RECHAZADO",finish(state.id()).estado());assertThrows(FalloTecnico.class,()->media.confirmar(journey,session,new CargasMultimedia.Manifest(List.of(state.id()),previous.revision())));assertEquals(previous,media.lote(journey,session));
 }
 @Test void duplicadoRenombradoSeDetectaSoloDentroDelRecorridoPropio()throws Exception{
  var bytes=text();var before=start(bytes);assertEquals(before.id(),media.iniciar(journey,session,new CargasMultimedia.Inicio("renombrado.txt",bytes.length,sha(bytes))).id());assertEquals(1,db.sql.queryForObject("SELECT COUNT(*) FROM media_upload",Integer.class));
 }
 @Test void sinEspacioSeRechazaAntesDeRecibirYLaCargaPuedeRetomarse()throws Exception{
  free.set(0);assertEquals(507,assertThrows(FalloTecnico.class,()->start(text())).status);assertEquals(0,db.sql.queryForObject("SELECT COUNT(*) FROM media_upload",Integer.class));free.set(1L<<30);var state=start(text());free.set(0);assertEquals(507,assertThrows(FalloTecnico.class,()->send(state,text())).status);assertEquals(0,media.estado(journey,state.id(),session).recibidos());free.set(1L<<30);assertEquals("VALIDADO",finish(send(state,text()).id()).estado());
 }
 @Test void limitesRealesRechazanExcesoSinReservarNiTruncar()throws Exception{
  var exact=new byte[512];Arrays.fill(exact,(byte)'a');assertNotNull(start(exact));assertEquals(413,assertThrows(FalloTecnico.class,()->media.iniciar(journey,session,new CargasMultimedia.Inicio("grande.txt",513,"0".repeat(64)))).status);assertThrows(IllegalArgumentException.class,()->media.iniciar(journey,session,new CargasMultimedia.Inicio("evidencia.exe.pdf",1,"0".repeat(64))));assertThrows(IllegalArgumentException.class,()->media.iniciar(journey,session,new CargasMultimedia.Inicio("../escape.txt",1,"0".repeat(64))));
 }
 @Test void cancelacionYRevisionOptimistaProtegenElLote()throws Exception{
  var first=upload(text());var initial=media.lote(journey,session);publish(first.id());assertEquals(409,assertThrows(FalloTecnico.class,()->media.confirmar(journey,session,new CargasMultimedia.Manifest(List.of(),initial.revision()))).status);var state=start("segundo archivo".getBytes(StandardCharsets.UTF_8));media.cancelar(journey,state.id(),session);assertEquals("CANCELADO",media.estado(journey,state.id(),session).estado());assertEquals(409,assertThrows(FalloTecnico.class,()->media.finalizar(journey,state.id(),session)).status);assertEquals(1,media.lote(journey,session).archivos().size());
 }
 @Test void custodiaNuevaVersionSeRecuperaSoloConOptInYBytesFluyenBajoReserva()throws Exception{
  var bytes=text();var state=upload(bytes);publish(state.id());var pub=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();var ins=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();var files=new AdjuntosFormulario(db,journeys,b->{throw new AssertionError("No usar scanner de arrays para nuevos archivos");},store);
  var reception=new RecepcionFormularioJdbc(db,journeys,files,mapper,new ValidacionFormulario(new TerritorioFormulario(mapper)),"publico",pub.getPrivate(),Map.of("publico",pub.getPublic()),Map.of("institucion",ins.getPublic()),clock,true);
  var id=UUID.randomUUID();var datos=new DatosFormulario(Map.of("nna-region","13","nna-primary-method","unsafe","nna-story","Relato sintético"),List.of(),List.of(),List.of(state.id()));var accepted=reception.recibir(id,journey,session,datos);assertNull(accepted.registro());assertTrue(reception.reclamar(1).isEmpty());
  var lease=reception.reclamar(1,Set.of("1.0","1.1")).getFirst();assertEquals("1.1",lease.entrega().comunicacion().version());lease.entrega().validar(Map.of("publico",pub.getPublic()),mapper);var source=reception.recursoReservado(id,lease.reserva(),state.id());var out=new ByteArrayOutputStream();source.copiar(out,0,source.tamanio());assertArrayEquals(bytes,out.toByteArray());assertEquals(409,assertThrows(FalloTecnico.class,()->reception.recursoReservado(id,UUID.randomUUID(),state.id())).status);
  assertEquals(409,assertThrows(FalloTecnico.class,()->media.confirmar(journey,session,new CargasMultimedia.Manifest(List.of(),media.lote(journey,session).revision()))).status);assertEquals(accepted,reception.recibir(id,journey,session,datos));
  String signedBody=db.sql.queryForObject("SELECT body FROM citizen_submission WHERE id=?",String.class,id.toString());
  var registered=new ComprobanteInstitucional.Contenido("1.1","SIGER_DDN","COMUNICACION_REGISTRADA",id,UUID.randomUUID(),1,"COM-1",clock.instant(),"FORMULARIO_WEB_NNA",lease.entrega().sha256(),lease.entrega().huellaProcedencia());
  var ack=new RegistroInstitucional(registered,ComprobanteInstitucional.firmar(registered,"institucion",ins.getPrivate(),mapper));reception.confirmar(id,lease.reserva(),ack);reception.confirmar(id,lease.reserva(),ack);
  assertEquals("REGISTRADA_SIGER",reception.consultar(id,session).estado());assertEquals(signedBody,db.sql.queryForObject("SELECT body FROM citizen_submission WHERE id=?",String.class,id.toString()));assertTrue(Files.exists(store.original(state.id())));assertArrayEquals(bytes,Files.readAllBytes(store.original(state.id())));
 }
 @Test void alteracionDelOriginalSeDetectaSinEntregarBytes()throws Exception{
  var state=upload(text());publish(state.id());var file=store.original(state.id());Files.setPosixFilePermissions(file,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));Files.writeString(file,"alterado");var files=new AdjuntosFormulario(db,journeys,b->true,store);assertEquals(503,assertThrows(FalloTecnico.class,()->files.recurso(journey,state.id(),session)).status);
 }

 @Test void saneamientoEsControladoYNoBorraOriginalesConfirmados()throws Exception{
  var accepted=upload(text());publish(accepted.id());var part=send(start("temporal distinto".getBytes(StandardCharsets.UTF_8)),"temporal distinto".getBytes(StandardCharsets.UTF_8));
  db.sql.update("UPDATE media_upload SET expires_ms=?",clock.millis()-1);long before=db.sql.queryForObject("SELECT reserved_bytes FROM media_capacity WHERE id=1",Long.class);
  assertEquals(1,media.sanear(false).size());assertTrue(Files.exists(store.bloque(part.id(),0)));assertEquals(1,media.sanear(true).size());assertFalse(Files.exists(store.bloque(part.id(),0)));assertTrue(Files.exists(store.original(accepted.id())));assertTrue(media.sanear(true).isEmpty());assertTrue(db.sql.queryForObject("SELECT reserved_bytes FROM media_capacity WHERE id=1",Long.class)<before);assertEquals(1,media.lote(journey,session).archivos().size());
 }
 @Test void cancelarLiberaCuotaYNoBorraElLotePrevio()throws Exception{
  var accepted=upload(text());publish(accepted.id());long before=db.sql.queryForObject("SELECT reserved_bytes FROM media_capacity WHERE id=1",Long.class);var pending=send(start("archivo pendiente".getBytes(StandardCharsets.UTF_8)),"archivo pendiente".getBytes(StandardCharsets.UTF_8));
  media.cancelar(journey,pending.id(),session);assertEquals(before,db.sql.queryForObject("SELECT reserved_bytes FROM media_capacity WHERE id=1",Long.class));assertTrue(Files.exists(store.original(accepted.id())));assertEquals(409,assertThrows(FalloTecnico.class,()->media.cancelar(journey,accepted.id(),session)).status);
 }
 @Test void faltaDeOriginalImpidePublicarYConservaManifiestoAnterior()throws Exception{
  var accepted=upload(text());publish(accepted.id());var old=media.lote(journey,session);var candidate=upload("nuevo original".getBytes(StandardCharsets.UTF_8));Files.delete(store.original(candidate.id()));assertEquals(503,assertThrows(FalloTecnico.class,()->media.confirmar(journey,session,new CargasMultimedia.Manifest(List.of(candidate.id()),old.revision()))).status);assertEquals(old,media.lote(journey,session));
 }
 @Test void almacenRechazaSegundoEscritorYDirectorioAjeno()throws Exception{
  assertThrows(IOException.class,()->new AlmacenPrivado(store.raiz()));var wrong=temp.resolve("ajeno");Files.createDirectory(wrong);Files.writeString(wrong.resolve("documento.txt"),"conservar");assertThrows(IOException.class,()->new AlmacenPrivado(wrong));assertEquals("conservar",Files.readString(wrong.resolve("documento.txt")));
 }
 @Test void excepcionMysqlSinTlsSoloAdmiteEsquemaExactoDelLaboratorio(){
  assertDoesNotThrow(()->ConfiguracionDatos.validar("jdbc:mysql://127.0.0.1:13306/robust_public_media_lab?sslMode=DISABLED&allowPublicKeyRetrieval=true",true));
  assertThrows(IllegalArgumentException.class,()->ConfiguracionDatos.validar("jdbc:mysql://127.0.0.1:13306/produccion?sslMode=DISABLED",true));assertThrows(IllegalArgumentException.class,()->ConfiguracionDatos.validar("jdbc:mysql://127.0.0.1:13306/robust_public_media_lab?sslMode=DISABLED",false));
 }
}
