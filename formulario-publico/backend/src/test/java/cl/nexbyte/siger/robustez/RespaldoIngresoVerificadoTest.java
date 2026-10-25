package cl.nexbyte.siger.robustez;
import cl.nexbyte.siger.custodia.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RespaldoIngresoVerificadoTest {
 @TempDir Path temp;CicloVidaCustodiaTest t;Path backup,scratch;JdbcTemplate source;KeyPair issuer;
 @BeforeEach void setup()throws Exception{
  t=new CicloVidaCustodiaTest();t.temp=temp;t.setup();t.receive(false);t.f.media.close();issuer=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
  backup=Files.createTempDirectory(Path.of("/dev/shm"),"respaldo-ingreso-test-");scratch=Files.createDirectory(temp.resolve("scratch"));source=mock(JdbcTemplate.class);
  var ack=t.f.mapper.readValue(t.ackText,RegistroInstitucional.class).registro();var d=t.lease.entrega().comunicacion().adjuntos().getFirst();
  var meta=new HashMap<String,Object>();meta.put("body",t.signed);meta.put("receipt",t.ackText);meta.put("document_kind","FORMULARIO_COMPLETO");meta.put("comunicacion_id",ack.comunicacionId().toString());meta.put("numero",1L);meta.put("canal",ack.canalOrigen());meta.put("content_hash",ack.sha256());meta.put("provenance_hash",ack.huellaProcedencia());meta.put("registrada_ms",ack.registradaEn().toEpochMilli());meta.put("folio_prefix","COM");meta.put("folio_width",1);
  var document=new HashMap<String,Object>();document.put("id",UUID.randomUUID().toString());document.put("name",d.nombre());document.put("size_bytes",d.tamanio());document.put("sha256",d.sha256());document.put("media_type",d.tipo());document.put("attachment_id",d.id().toString());document.put("communication_id",ack.comunicacionId().toString());document.put("operation_id",t.communication.toString());document.put("storage_key",t.communication+"/"+d.id());document.put("origin","INGRESO_CIUDADANO");
  when(source.queryForList(anyString(),any(Object[].class))).thenAnswer(call->{String sql=call.getArgument(0);if(sql.contains("communication_registry"))return List.of(meta);if(sql.contains("institution_document"))return List.of(document);throw new AssertionError("Solo SELECT institucional esperado");});
 }
 @AfterEach void close()throws Exception{if(backup!=null)try(var files=Files.walk(backup)){for(var p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}if(t!=null)t.close();}
 FirmaOperativa emit()throws Exception{return RespaldoIngresoVerificado.emitir(source,t.institution,backup,scratch,t.communication,"COMPLETO",Map.of("publico",t.publicKey.getPublic()),Map.of("institucion",t.institutional.getPublic()),"issuer",issuer.getPrivate(),t.clock,t.f.mapper);}
 Path packet(){return backup.resolve("completo-"+t.communication);}
 @Test void publicaRestauraYFirmaSinNecesitarRestauracionSqlManual()throws Exception{
  var proof=emit();var result=GarantiaCustodia.validar(proof,Map.of("issuer",issuer.getPublic()),t.clock,t.f.mapper);assertEquals(t.communication,result.contexto().id());assertEquals(t.lease.entrega().sha256(),result.contexto().contenido());
  assertTrue(Files.exists(packet().resolve("registro.json")));assertArrayEquals(t.f.text(),Files.readAllBytes(packet().resolve("objects").resolve(t.communication.toString()).resolve(t.attachment+".bin")));try(var dirs=Files.list(scratch)){assertEquals(0,dirs.count());}
  verify(source,never()).update(anyString(),any(Object[].class));
 }
 @Test void repetirReutilizaPaqueteYNoDuplicaDocumentos()throws Exception{
  emit();var before=Files.getLastModifiedTime(packet().resolve("registro.json"));emit();assertEquals(before,Files.getLastModifiedTime(packet().resolve("registro.json")));try(var dirs=Files.list(backup)){assertEquals(1,dirs.count());}
 }
 @Test void corrupcionDeRespaldoNoSeOcultaReescribiendoDesdeElOriginal()throws Exception{
  emit();var file=packet().resolve("objects").resolve(t.communication.toString()).resolve(t.attachment+".bin");Files.setPosixFilePermissions(file,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));Files.writeString(file,"alterado");assertThrows(IOException.class,this::emit);assertEquals("alterado",Files.readString(file));t.intact();try(var dirs=Files.list(scratch)){assertEquals(0,dirs.count());}
 }
 @Test void origenAusenteNoPublicaPaqueteNiEmiteGarantia()throws Exception{
  Files.delete(t.institution.resolve("objects").resolve(t.communication.toString()).resolve(t.attachment+".bin"));assertThrows(IOException.class,this::emit);assertFalse(Files.exists(packet()));try(var dirs=Files.list(backup)){assertEquals(0,dirs.count());}t.intact();
 }
}