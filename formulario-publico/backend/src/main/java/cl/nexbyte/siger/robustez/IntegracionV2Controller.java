package cl.nexbyte.siger.robustez;
import cl.nexbyte.siger.entrega.SobreComunicacion;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @Profile("jdbc-reception") @RequestMapping("/integracion/v2/entregas")
public class IntegracionV2Controller {
    private final RecepcionDurable recepcion;
    public IntegracionV2Controller(RecepcionDurable recepcion){this.recepcion=recepcion;}
    public record Solicitud(int limite){}
    public record Lote(List<RecepcionDurable.EntregaReservada> entregas){}
    public record Confirmacion(UUID reserva,SobreComunicacion.Confirmacion recepcion){}
    public record Fallo(UUID reserva,String codigo){}
    @PostMapping("/reclamar") Lote reclamar(@RequestBody Solicitud request){throw new FalloTecnico(410);}
    @PostMapping("/{id}/confirmacion") ResponseEntity<Void> confirmar(@PathVariable UUID id,@RequestBody Confirmacion body){throw new FalloTecnico(410);}
    @PostMapping("/{id}/fallo") ResponseEntity<Void> fallo(@PathVariable UUID id,@RequestBody Fallo body){throw new FalloTecnico(410);}
}
