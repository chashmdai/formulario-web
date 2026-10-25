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
 public CustodiaTransitoriaController(ObjectProvider<CustodiaTransitoria> provider){this.provider=provider;}
 private CustodiaTransitoria service(){var s=provider.getIfAvailable();if(s==null)throw new FalloTecnico(503);return s;}
 @GetMapping ResponseEntity<Map<String,Object>> estado(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service().estado());}
 public record Hold(String cola,UUID id,boolean conservar){}
 @PostMapping("/conservacion") ResponseEntity<Void> conservar(@RequestBody Hold body){service().conservar(body.cola(),body.id(),body.conservar());return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
}