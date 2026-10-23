package cl.nexbyte.siger.entrega;

import cl.nexbyte.siger.identidad.IdentidadVerificada;
import cl.nexbyte.siger.identidad.EvidenciaAutenticacion;
import cl.nexbyte.siger.robustez.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/comunicaciones")
public class ComunicacionesPublicasController {
    private final RecepcionPublica recepcion;
    public ComunicacionesPublicasController(RecepcionPublica recepcion) { this.recepcion = recepcion; }
    public record Solicitud(@NotBlank @Size(max=2000) String mensaje, @AssertTrue boolean avisoPrivacidadLeido, @Size(max=100) String avisoVersion) {}
    public record Capacidades(boolean recepcionDisponible, String avisoVersion, String aviso) {}
    @GetMapping("/capacidades")
    Capacidades capacidades() {
        return new Capacidades(recepcion.disponible(), recepcion instanceof RecepcionDurable durable ? durable.avisoVersion() : RecepcionArchivosDemo.AVISO,
                "Demostración técnica: se conservarán RUN, nombres, apellidos, mensaje y evidencia de recepción para su entrega a SIGER. Use solo datos de prueba. La recepción pública y la incorporación institucional son confirmaciones diferentes. El aviso definitivo, finalidad y plazos de conservación requieren validación institucional.");
    }
    @PostMapping
    ResponseEntity<RecepcionPublica.Estado> enviar(@RequestHeader("Idempotency-Key") UUID key,
            @AuthenticationPrincipal IdentidadVerificada identity, org.springframework.security.core.Authentication authentication,
            @Valid @RequestBody Solicitud request,jakarta.servlet.http.HttpServletRequest http) {
        SobreComunicacion sobre;
        if (recepcion instanceof RecepcionDurable durable) {
            if (!(authentication.getDetails() instanceof EvidenciaAutenticacion evidence)) throw new FalloTecnico(401);
            sobre = durable.recibirVerificadaEnSesion(key, identity, evidence, request.mensaje(), request.avisoVersion(),cl.nexbyte.siger.comunicaciones.SesionIngreso.iniciar(http));
        } else sobre = recepcion.recibir(key, new SobreComunicacion.Identidad(identity.run(), identity.nombres(), identity.apellidos(), identity.nombresComponentes(), identity.apellidosComponentes()), TextoCanonico.mensaje(request.mensaje()));
        return ResponseEntity.accepted().location(URI.create("/api/comunicaciones/" + sobre.id()))
                .body(recepcion instanceof RecepcionDurable d?d.consultarSesion(sobre.id(),cl.nexbyte.siger.comunicaciones.SesionIngreso.actual(http)):recepcion.consultar(sobre.id(), identity.run()));
    }
    @GetMapping("/{id}")
    RecepcionPublica.Estado estado(@PathVariable UUID id, @AuthenticationPrincipal IdentidadVerificada identity,jakarta.servlet.http.HttpServletRequest http) {
        return recepcion instanceof RecepcionDurable d?d.consultarSesion(id,cl.nexbyte.siger.comunicaciones.SesionIngreso.actual(http)):recepcion.consultar(id, identity.run());
    }
    @GetMapping("/{id}/comprobante")
    ResponseEntity<ComprobanteInstitucional> comprobante(@PathVariable UUID id,@AuthenticationPrincipal IdentidadVerificada identity,jakarta.servlet.http.HttpServletRequest http) {
        if (!(recepcion instanceof RecepcionDurable durable)) throw new FalloTecnico(503);
        var registered=durable.consultarSesion(id,cl.nexbyte.siger.comunicaciones.SesionIngreso.actual(http)).registroInstitucional();
        if(registered==null)throw new FalloTecnico(409);
        return ResponseEntity.ok().header("Content-Disposition","attachment; filename=\"comprobante-"+registered.registro().folio()+".json\"")
                .cacheControl(org.springframework.http.CacheControl.noStore()).body(registered.comprobante());
    }
}
