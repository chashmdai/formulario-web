package cl.nexbyte.siger.robustez;
import java.nio.file.*;import java.security.*;import java.time.*;import java.util.*;
import cl.nexbyte.siger.identidad.*;import org.junit.jupiter.api.Test;import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.json.JsonMapper;import static org.assertj.core.api.Assertions.*;
@EnabledIfSystemProperty(named="recuperacion.real",matches="true")
class RecuperacionProcesoTest {
 @Test void comprobanteConfirmadoSobreviveCaidaRealDeMySQLYUnaFallaNoSeAcepta()throws Exception{
  if(System.getenv("ROBUST_DB_URL")==null)throw new IllegalStateException("Requiere el coordinador MySQL local");
  var mapper=JsonMapper.builder().build();var keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
  var identity=new IdentidadVerificada("44444444-4","Prueba","Recuperación");var evidence=EvidenciaAutenticacion.de(identity);
  try(var db=TestDatabase.open(Files.createTempDirectory("recuperacion-"))){
   new ControlesOperacion(db).cambiar(0,true,true,"NORMAL","PRUEBA_LOCAL");
   var store=new RecepcionJdbc(db,mapper,"laboratorio-v1",true,"recovery-v1",keys.getPrivate(),Map.of("recovery-v1",keys.getPublic()),Clock.systemUTC(),300000);
   var committed=UUID.randomUUID();var rejected=UUID.randomUUID();store.recibirVerificada(committed,identity,evidence,"Commit antes de caída","laboratorio-v1");var receipt=mapper.readValue(db.sql.queryForObject("SELECT body FROM public_delivery WHERE id=?",String.class,committed.toString()),EntregaVerificada.class).comprobante();
   Files.writeString(Path.of("target/recuperacion-lista.json"),"{\"commitConfirmado\":true}");await("target/recuperacion-caida.signal");
   assertThatThrownBy(()->store.recibirVerificada(rejected,identity,evidence,"No recibido sin base","laboratorio-v1")).isInstanceOf(org.springframework.dao.DataAccessException.class);
   assertThat(store.disponible()).isFalse();Files.writeString(Path.of("target/recuperacion-sin-db.json"),"{\"rechazoSinConfirmacion\":true}");
   await("target/recuperacion-reinicio.signal");
   assertThat(mapper.readValue(db.sql.queryForObject("SELECT body FROM public_delivery WHERE id=?",String.class,committed.toString()),EntregaVerificada.class).comprobante()).isEqualTo(receipt);
   assertThat(db.sql.queryForObject("SELECT COUNT(*) FROM public_delivery WHERE id=?",Long.class,rejected.toString())).isZero();
   store.recibirVerificada(committed,identity,evidence,"Commit antes de caída","laboratorio-v1");
   assertThat(mapper.readValue(db.sql.queryForObject("SELECT body FROM public_delivery WHERE id=?",String.class,committed.toString()),EntregaVerificada.class).comprobante()).isEqualTo(receipt);
   Files.writeString(Path.of("target/recuperacion-resultado.json"),"{\"motor\":\"MySQL 8.4.11\",\"caidaProcesoReal\":true,\"comprobanteSobrevive\":true,\"sinAceptacionFalsa\":true,\"reintentoIdempotente\":true}");
  }
 }
 private static void await(String file)throws Exception{long deadline=System.nanoTime()+Duration.ofSeconds(90).toNanos();while(!Files.exists(Path.of(file))){if(System.nanoTime()>deadline)throw new AssertionError("Coordinador no emitió señal de recuperación");Thread.sleep(100);}}
}
