package cl.nexbyte.siger.multimedia;
import cl.nexbyte.siger.robustez.FalloTecnico;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
/** Hereda el control de máquina de las rutas operativas, sin datos ciudadanos en sus métricas. */
@RestController @RequestMapping("/operacion/multimedia")
public class OperacionMultimediaController{
 private final ObjectProvider<CargasMultimedia> service;
 public OperacionMultimediaController(ObjectProvider<CargasMultimedia> service){this.service=service;}
 private CargasMultimedia enabled(){var s=service.getIfAvailable();if(s==null)throw new FalloTecnico(503);return s;}
 @GetMapping ResponseEntity<Map<String,Object>> estado(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(enabled().capacidad());}
 public record Saneamiento(boolean aplicar){}
 @PostMapping("/temporales") ResponseEntity<List<CargasMultimedia.Limpieza>> sanear(@RequestBody Saneamiento body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(enabled().sanear(body.aplicar()));}
}
