package cl.nexbyte.siger.formulario;

import cl.nexbyte.siger.robustez.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Extensión explícita y optativa. Las rutas /integracion/v3/entregas permanecen intactas. */
@RestController @RequestMapping("/integracion/v3/formulario-completo/entregas")
public class IntegracionFormularioController {
    private final ObjectProvider<RecepcionFormularioJdbc> service;
    public IntegracionFormularioController(ObjectProvider<RecepcionFormularioJdbc> service){this.service=service;}
    private RecepcionFormularioJdbc enabled(){var s=service.getIfAvailable();if(s==null)throw new FalloTecnico(503);return s;}
    public record Solicitud(int limite){}
    public record Lote(List<RecepcionFormularioJdbc.Reserva> entregas){}
    public record Confirmacion(UUID reserva,RegistroInstitucional recepcion){}
    public record Fallo(UUID reserva,String codigo){}
    @PostMapping("/reclamar") ResponseEntity<Lote> reclamar(@RequestBody Solicitud body,@RequestHeader(name="X-Formulario-Versiones",required=false) String versions){var allowed=versions==null?Set.of("1.0"):new HashSet<>(Arrays.asList(versions.split(",")));return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Lote(enabled().reclamar(body.limite(),allowed)));}
    @PostMapping("/{id}/confirmacion") ResponseEntity<Void> confirmar(@PathVariable UUID id,@RequestBody Confirmacion body){enabled().confirmar(id,body.reserva(),body.recepcion());return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
    @PostMapping("/{id}/fallo") ResponseEntity<Void> fallo(@PathVariable UUID id,@RequestBody Fallo body){enabled().fallo(id,body.reserva(),body.codigo());return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
    @RequestMapping(path="/{id}/adjuntos/{adjunto}",method={RequestMethod.GET,RequestMethod.HEAD}) void adjunto(@PathVariable UUID id,@PathVariable UUID adjunto,@RequestHeader("X-Reserva") UUID reserva,jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response)throws java.io.IOException{cl.nexbyte.siger.multimedia.TransferenciaPrivada.enviar(enabled().recursoReservado(id,reserva,adjunto),false,request,response);}
}
