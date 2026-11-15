package cl.nexbyte.siger.custodia;
import cl.nexbyte.siger.robustez.FalloTecnico;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
/** TLS/token de operación existentes + firma independiente de la garantía. Ningún payload ciudadano. */
@RestController @RequestMapping("/operacion/v1/custodia")
public class CustodiaTransitoriaController {
 private final ObjectProvider<CustodiaTransitoria> provider;
 private final ObjectProvider<MinimizacionBorradores> borradores;
 public CustodiaTransitoriaController(ObjectProvider<CustodiaTransitoria> provider,ObjectProvider<MinimizacionBorradores> borradores){
  this.provider=provider;this.borradores=borradores;
 }
 private CustodiaTransitoria service(){var s=provider.getIfAvailable();if(s==null)throw new FalloTecnico(503);return s;}
 @GetMapping ResponseEntity<Map<String,Object>> estado(){
  var custodia=provider.getIfAvailable();var minimizacion=borradores.getIfAvailable();
  if(custodia==null&&minimizacion==null)throw new FalloTecnico(503);
  var estado=new LinkedHashMap<String,Object>();
  if(custodia!=null)estado.putAll(custodia.estado());
  if(minimizacion!=null)estado.put("minimizacionBorradores",minimizacion.estado());
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(estado);
 }
 public record Hold(String cola,UUID id,boolean conservar){}
 @PostMapping("/conservacion") ResponseEntity<Void> conservar(@RequestBody Hold body){service().conservar(body.cola(),body.id(),body.conservar());return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
}