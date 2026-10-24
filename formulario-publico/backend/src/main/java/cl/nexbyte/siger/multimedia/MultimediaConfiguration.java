package cl.nexbyte.siger.multimedia;
import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.robustez.BaseJdbc;
import java.nio.file.*;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.json.JsonMapper;
@Configuration @Profile("jdbc-reception") @ConditionalOnProperty(name={"formulario.habilitado","formulario.multimedia.habilitada"},havingValue="true")
public class MultimediaConfiguration{
 @Bean PoliticaMultimedia politicaMultimedia(Environment e){var p=PoliticaMultimedia.propuesta();return new PoliticaMultimedia(e.getProperty("formulario.multimedia.cantidad",Integer.class,p.cantidad()),size(e,"total",p.total()),size(e,"bloque",p.bloque()),size(e,"imagen",p.imagen()),size(e,"documento",p.documento()),size(e,"audio",p.audio()),size(e,"video",p.video()),size(e,"cuota-global",p.global()),size(e,"minimo-libre",p.libreMinimo()),p.venceMs());}
 private static long size(Environment e,String key,long fallback){return DataSize.parse(e.getProperty("formulario.multimedia."+key,Long.toString(fallback)+"B")).toBytes();}
 @Bean AlmacenPrivado almacenPrivado(Environment e)throws java.io.IOException{return new AlmacenPrivado(Path.of(e.getRequiredProperty("formulario.multimedia.directorio")));}
 @Bean(destroyMethod="close") CargasMultimedia cargasMultimedia(BaseJdbc db,RecorridosJdbc journeys,AlmacenPrivado store,PoliticaMultimedia policy,JsonMapper mapper,Environment e)throws java.io.IOException{
  long max=Math.max(Math.max(policy.imagen(),policy.documento()),Math.max(policy.audio(),policy.video()));
  var scanner=new AntivirusLocal(Path.of(e.getRequiredProperty("formulario.multimedia.antivirus-socket")),Path.of(e.getRequiredProperty("formulario.multimedia.antivirus-config")),max);
  var inspector=new InspectorLocal(Path.of(e.getRequiredProperty("formulario.multimedia.inspector-socket")));
  return new CargasMultimedia(db,journeys,store,policy,scanner,inspector,Clock.systemUTC(),mapper);
 }
}
