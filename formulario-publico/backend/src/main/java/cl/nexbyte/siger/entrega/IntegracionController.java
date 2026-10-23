package cl.nexbyte.siger.entrega;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/integracion/v1/comunicaciones")
public class IntegracionController {
    private final RecepcionPublica recepcion;
    public IntegracionController(RecepcionPublica recepcion) { this.recepcion = recepcion; }
    public record Pendientes(List<SobreComunicacion> comunicaciones) {}
    @GetMapping("/pendientes")
    Pendientes pendientes(@RequestParam(defaultValue="20") int limite) {
        if (limite < 1 || limite > 100) throw new EntregaException(400);
        return new Pendientes(recepcion.pendientes(limite));
    }
    @PostMapping("/{id}/confirmacion")
    ResponseEntity<Void> confirmar(@PathVariable UUID id, @RequestBody SobreComunicacion.Confirmacion ack) {
        recepcion.confirmar(id, ack);
        return ResponseEntity.noContent().build();
    }
}
