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
 public static CicloVidaCustodia.Politica politica(Environment env) {
  var mode=CicloVidaCustodia.Modalidad.valueOf(env.getProperty("ciclo-vida.modalidad","GARANTIA_INDEPENDIENTE"));
  if(mode==CicloVidaCustodia.Modalidad.ACK_RETENCION_LIMITADA_APROBADA && !env.getProperty("ciclo-vida.ack-riesgo-aceptado",Boolean.class,false))
   throw new IllegalArgumentException("Modalidad ACK requiere aceptación explícita de riesgo");
  return new CicloVidaCustodia.Politica(env.getProperty("ciclo-vida.eliminacion.habilitada",Boolean.class,false),
    Duration.parse(env.getProperty("ciclo-vida.retencion-post-ack",mode==CicloVidaCustodia.Modalidad.GARANTIA_INDEPENDIENTE?"PT24H":"PT72H")),
    Duration.parse(env.getProperty("ciclo-vida.retencion-abandono","PT48H")),mode,env.getProperty("ciclo-vida.aprobacion-ref",""));
 }
 @Bean(destroyMethod="close") CustodiaTransitoria custodiaTransitoria(BaseJdbc db,JsonMapper mapper,ObjectProvider<AlmacenPrivado> stores,ObjectProvider<PoliticaMultimedia> policies,Environment env)throws Exception {
  var publicKeys=JdbcRecepcionConfiguration.publicKeys(mapper,Path.of(env.getRequiredProperty("publico.comprobante.trusted-keys")));
  var institutional=JdbcRecepcionConfiguration.publicKeys(mapper,Path.of(env.getRequiredProperty("publico.comprobante.institutional-trusted-keys")));
  var policy=politica(env);
  var trust=env.getProperty("ciclo-vida.garantias.trusted-keys");
  if(policy.independiente()&&(trust==null||trust.isBlank()))throw new IllegalArgumentException("Confianza de garantías requerida");
  var service=trust==null||trust.isBlank()?null:new GarantiasCustodiaJdbc(db,mapper,publicKeys,institutional,JdbcRecepcionConfiguration.publicKeys(mapper,Path.of(trust)),Clock.systemUTC());
  Duration period=Duration.parse(env.getProperty("ciclo-vida.intervalo","PT5M"));if(period.compareTo(Duration.ofMinutes(1))<0)throw new IllegalArgumentException("Intervalo mínimo un minuto");
  var store=stores.getIfAvailable();boolean owned=store==null;if(owned)store=new AlmacenPrivado(Path.of(env.getRequiredProperty("formulario.multimedia.directorio")));
  var custodia=new CustodiaTransitoria(db,store,owned,mapper,service,publicKeys,institutional,env.getRequiredProperty("publico.comprobante.key-id"),ComprobanteDigital.privatePem(Files.readString(Path.of(env.getRequiredProperty("publico.comprobante.private-key")))),Files.readAllBytes(Path.of(env.getRequiredProperty("formulario.abuso-key-path"))),Clock.systemUTC(),policy).retencionesTecnicas(Duration.parse(env.getProperty("ciclo-vida.borradores-metadatos-retencion","P7D")),Duration.parse(env.getProperty("ciclo-vida.auditoria-tecnica-retencion","P30D")));
  var multimedia=policies.getIfAvailable();if(multimedia!=null)custodia.cuotaMultimedia(multimedia.global());return custodia;
 }
 @Bean Programador programadorCustodia(CustodiaTransitoria service){return new Programador(service);}
 public static final class Programador {
  private final CustodiaTransitoria service;public Programador(CustodiaTransitoria s){service=s;}
  @Scheduled(fixedDelayString="${ciclo-vida.intervalo:PT5M}",initialDelayString="${ciclo-vida.inicio:PT1M}")
  public void ejecutar(){service.ejecutar();}
 }
}