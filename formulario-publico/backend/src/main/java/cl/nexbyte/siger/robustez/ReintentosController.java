package cl.nexbyte.siger.robustez;
import java.util.UUID;import org.springframework.context.annotation.Profile;import org.springframework.web.bind.annotation.*;
@RestController @Profile("jdbc-reception") @RequestMapping("/operacion/v1/entregas")
public class ReintentosController {
 private final RecepcionJdbc store;public ReintentosController(RecepcionJdbc store){this.store=store;}
 public record Reintento(int intentosEsperados,String motivo){}
 @PostMapping("/{id}/reintentar") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
 void reintentar(@PathVariable UUID id,@RequestBody Reintento body){store.reponer(id,body.intentosEsperados(),body.motivo());}
}
