package cl.nexbyte.siger.robustez;
import java.nio.file.*;import java.security.*;import java.time.*;import java.util.*;
import org.flywaydb.core.Flyway;import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;
import cl.nexbyte.siger.identidad.*;import tools.jackson.databind.json.JsonMapper;import static org.assertj.core.api.Assertions.*;
class MigracionRegistroTest {
 @TempDir Path dir;
 @Test void ackV2SeConservaComoAntecedenteYNoSePresentaComoRegistroConFolio()throws Exception{
  var url="jdbc:h2:file:"+dir.resolve("v2").toString().replace('\\','/')+";DB_CLOSE_ON_EXIT=FALSE";
  Flyway.configure().dataSource(url,"sa","").locations("classpath:db/publico/h2").target("2").load().migrate();
  try(var db=new BaseJdbc(url,"sa","",4)){
   var mapper=JsonMapper.builder().build();var keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
   var store=new RecepcionJdbc(db,mapper,"laboratorio-v1",true,"test-v2",keys.getPrivate(),Map.of("test-v2",keys.getPublic()),Clock.systemUTC(),300000);
   new ControlesOperacion(db).cambiar(0,true,true,"NORMAL","PRUEBA_LOCAL");var id=UUID.randomUUID();var identity=new IdentidadVerificada("44444444-4","Sintética","Migración");
   var body=cl.nexbyte.siger.entrega.SobreComunicacion.crear(id,Instant.now(),new cl.nexbyte.siger.entrega.SobreComunicacion.Identidad(identity.run(),identity.nombres(),identity.apellidos()),"Conservar entrega","laboratorio-v1");
   var attrs=EntregaVerificada.procedencia(body.recibidaEn(),UUID.randomUUID(),body.recibidaEn(),id);
   var content=new ComprobanteDigital.Contenido("1.0",id,body.recibidaEn(),"CUSTODIA_PUBLICA",body.sha256(),EntregaVerificada.huella(attrs),"FORMULARIO_PUBLICO_DDN","laboratorio-v1");
   var delivery=new EntregaVerificada("2.0",body,attrs,ComprobanteDigital.firmar(content,"test-v2",keys.getPrivate(),mapper));
   var requestHash=java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((identity.run()+"\u0000"+body.mensaje()+"\u0000laboratorio-v1").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
   db.sql.update("INSERT INTO public_delivery(id,owner_run,request_hash,body,state,created_ms,next_attempt_ms,attempts,lease_until_ms) VALUES(?,?,?,?,'PENDIENTE',0,0,0,0)",id.toString(),identity.run(),requestHash,mapper.writeValueAsString(delivery));
   var legacy="{\"recepcionInstitucionalId\":\"11111111-1111-4111-8111-111111111111\"}";
   db.sql.update("UPDATE public_delivery SET state='CONFIRMADA',ack=? WHERE id=?",legacy,id.toString());
   db.migrate("publico");
   assertThat(db.sql.queryForObject("SELECT legacy_ack FROM public_delivery",String.class)).isEqualTo(legacy);
   assertThat(store.consultar(id,identity.run()).estado()).isEqualTo("PENDIENTE_REGISTRO_SIGER");
   assertThat(store.consultar(id,identity.run()).comprobante()).isNull();assertThat(store.reclamar(1)).hasSize(1);
  }
 }
}
