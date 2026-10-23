package cl.nexbyte.siger.robustez;
import java.util.*;import org.springframework.context.annotation.Profile;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
@RestController @Profile("jdbc-reception") @RequestMapping("/integracion/v3/entregas")
public class IntegracionV3Controller {
    private final RecepcionDurable recepcion;
    public IntegracionV3Controller(RecepcionDurable recepcion){this.recepcion=recepcion;}
    public record Solicitud(int limite){}
    public record Lote(List<RecepcionDurable.EntregaReservada> entregas){}
    public record Confirmacion(UUID reserva,RegistroInstitucional recepcion){}
    public record Fallo(UUID reserva,String codigo){}
    @PostMapping("/reclamar") Lote reclamar(@RequestBody Solicitud body){return new Lote(recepcion.reclamar(body.limite()));}
    @PostMapping("/{id}/confirmacion") ResponseEntity<Void> confirmar(@PathVariable UUID id,@RequestBody Confirmacion body){recepcion.confirmarRegistro(id,body.reserva(),body.recepcion());return ResponseEntity.noContent().build();}
    @PostMapping("/{id}/fallo") ResponseEntity<Void> fallo(@PathVariable UUID id,@RequestBody Fallo body){recepcion.fallo(id,body.reserva(),body.codigo());return ResponseEntity.noContent().build();}
}
