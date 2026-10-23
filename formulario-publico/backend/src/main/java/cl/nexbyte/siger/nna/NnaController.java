package cl.nexbyte.siger.nna;
import cl.nexbyte.siger.comunicaciones.SesionIngreso;
import cl.nexbyte.siger.contrato.DeclaracionNna;
import cl.nexbyte.siger.entrega.RecepcionPublica;
import cl.nexbyte.siger.robustez.*;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/nna")
public class NnaController {
    private final HabilitacionNna gate;private final RecepcionPublica reception;
    public NnaController(HabilitacionNna gate,RecepcionPublica reception){this.gate=gate;this.reception=reception;}
    public record Capacidades(boolean recepcionDisponible,String avisoVersion,String aviso){}
    @GetMapping("/capacidades") Capacidades capacidades(){
        return new Capacidades(gate.habilitado()&&reception.disponible(),reception instanceof RecepcionDurable d?d.avisoVersion():"",
            "Prototipo de laboratorio: usa solo datos sintéticos. Los datos son declarados, sin verificación de identidad. El aviso, finalidad y conservación requieren aprobación DDN. La recepción técnica no acredita todavía registro en SIGER.");
    }
    private RecepcionDurable enabled(HttpServletRequest request){
        if(!gate.habilitado()||!(reception instanceof RecepcionDurable))throw new FalloTecnico(404);
        if(!request.isSecure())throw new FalloTecnico(403);return (RecepcionDurable)reception;
    }
    public record Solicitud(DeclaracionNna datos,String mensaje,boolean avisoPrivacidadLeido,String avisoVersion){
        @Override public String toString(){return "SolicitudNna[datos protegidos]";}
    }
    @PostMapping("/comunicaciones") ResponseEntity<RecepcionPublica.Estado> enviar(@RequestHeader("Idempotency-Key")UUID key,@RequestBody Solicitud body,HttpServletRequest request){
        var durable=enabled(request);
        if(body.datos()==null||!body.avisoPrivacidadLeido()||body.avisoVersion()==null||!body.avisoVersion().matches("[A-Za-z0-9_.-]{1,100}"))throw new FalloTecnico(400);
        var session=SesionIngreso.iniciar(request);
        var sobre=durable.recibirNna(key,body.datos(),body.mensaje(),body.avisoVersion(),session);
        return ResponseEntity.accepted().location(URI.create("/api/nna/comunicaciones/"+sobre.id())).cacheControl(CacheControl.noStore()).body(durable.consultarSesion(sobre.id(),session));
    }
    @GetMapping("/comunicaciones/{id}") RecepcionPublica.Estado estado(@PathVariable UUID id,HttpServletRequest request){return enabled(request).consultarSesion(id,SesionIngreso.actual(request));}
    @GetMapping("/comunicaciones/{id}/comprobante") ResponseEntity<ComprobanteInstitucional> comprobante(@PathVariable UUID id,HttpServletRequest request){
        var state=enabled(request).consultarSesion(id,SesionIngreso.actual(request));
        if(state.comprobante()==null)throw new FalloTecnico(409);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Content-Disposition","attachment; filename=\"comprobante-"+state.registroInstitucional().registro().folio()+".json\"").body(state.comprobante());
    }
}
