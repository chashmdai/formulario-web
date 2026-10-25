package cl.nexbyte.siger.custodia;
import cl.nexbyte.siger.multimedia.*;
import cl.nexbyte.siger.robustez.*;
import java.nio.file.*;
import java.time.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.*;
import tools.jackson.databind.json.JsonMapper;
@Configuration @Profile("jdbc-reception") @EnableScheduling
@ConditionalOnProperty(name={"formulario.habilitado","ciclo-vida.online-habilitado"},havingValue="true")
public class CustodiaTransitoriaConfiguration {
 @Bean(destroyMethod="close") CustodiaTransitoria custodiaTransitoria(BaseJdbc db,JsonMapper mapper,ObjectProvider<AlmacenPrivado> stores,Environment env)throws Exception {
  var publicKeys=JdbcRecepcionConfiguration.publicKeys(mapper,Path.of(env.getRequiredProperty("publico.comprobante.trusted-keys")));
  var institutional=JdbcRecepcionConfiguration.publicKeys(mapper,Path.of(env.getRequiredProperty("publico.comprobante.institutional-trusted-keys")));
  var guarantees=JdbcRecepcionConfiguration.publicKeys(mapper,Path.of(env.getRequiredProperty("ciclo-vida.garantias.trusted-keys")));
  var service=new GarantiasCustodiaJdbc(db,mapper,publicKeys,institutional,guarantees,Clock.systemUTC());
  Duration period=Duration.parse(env.getProperty("ciclo-vida.intervalo","PT5M"));if(period.compareTo(Duration.ofMinutes(1))<0)throw new IllegalArgumentException("Intervalo mínimo un minuto");
  var store=stores.getIfAvailable();boolean owned=store==null;if(owned)store=new AlmacenPrivado(Path.of(env.getRequiredProperty("formulario.multimedia.directorio")));
  return new CustodiaTransitoria(db,store,owned,mapper,service,publicKeys,institutional,env.getRequiredProperty("publico.comprobante.key-id"),ComprobanteDigital.privatePem(Files.readString(Path.of(env.getRequiredProperty("publico.comprobante.private-key")))),Files.readAllBytes(Path.of(env.getRequiredProperty("formulario.abuso-key-path"))),Clock.systemUTC(),new CicloVidaCustodia.Politica(env.getProperty("ciclo-vida.eliminacion.habilitada",Boolean.class,false),Duration.parse(env.getProperty("ciclo-vida.retencion-post-ack","PT24H")),Duration.parse(env.getProperty("ciclo-vida.retencion-abandono","PT48H")))).retencionesTecnicas(Duration.parse(env.getProperty("ciclo-vida.borradores-metadatos-retencion","P7D")),Duration.parse(env.getProperty("ciclo-vida.auditoria-tecnica-retencion","P30D")));
 }
 @Bean Programador programadorCustodia(CustodiaTransitoria service){return new Programador(service);}
 public static final class Programador {
  private final CustodiaTransitoria service;public Programador(CustodiaTransitoria s){service=s;}
  @Scheduled(fixedDelayString="${ciclo-vida.intervalo:PT5M}",initialDelayString="${ciclo-vida.inicio:PT1M}")
  public void ejecutar(){service.ejecutar();}
 }
}