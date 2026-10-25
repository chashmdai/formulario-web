package cl.nexbyte.siger.custodia;
import cl.nexbyte.siger.robustez.FalloTecnico;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
/** Integración saliente existente desde SIGER; nunca concede acceso a controles operativos. */
@RestController @RequestMapping("/integracion/v3/custodia")
public class GarantiasCustodiaController {
 private final ObjectProvider<CustodiaTransitoria> provider;
 public GarantiasCustodiaController(ObjectProvider<CustodiaTransitoria> p){provider=p;}
 private CustodiaTransitoria service(){var s=provider.getIfAvailable();if(s==null)throw new FalloTecnico(503);return s;}
 @PostMapping("/garantias") ResponseEntity<Void> recibir(@RequestBody FirmaOperativa body){try{service().garantias().aceptar(body);}catch(IllegalArgumentException invalid){throw new FalloTecnico(400);}return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
 @GetMapping("/candidatos") ResponseEntity<List<Map<String,Object>>> candidatos(@RequestParam(defaultValue="20")int limite){if(limite<1||limite>100)throw new FalloTecnico(400);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service().candidatos(limite));}
}