package cl.nexbyte.siger.formulario;

import cl.nexbyte.siger.comunicaciones.SesionIngreso;
import cl.nexbyte.siger.identidad.*;
import cl.nexbyte.siger.robustez.FalloTecnico;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import static cl.nexbyte.siger.formulario.IdentificacionCiudadana.*;

@RestController @RequestMapping("/api/formulario")
public class FormularioController {
    private static final String ACTUAL=FormularioController.class.getName()+".actual";
    private static final String DERIVADO=FormularioController.class.getName()+".derivado";
    /**
     * Recuerda en la sesión propietaria que su recorrido vigente se derivó, para que una respuesta 410 perdida o una
     * recarga no abran otro recorrido NNA. Se consume una sola vez en {@code /actual}; no guarda datos personales.
     */
    static <T>T derivacion(HttpServletRequest request,UUID journey,java.util.function.Supplier<T> action) {
        try{return action.get();}
        catch(FalloTecnico failure){
            var session=request.getSession(false);
            if(failure.status==RecorridosJdbc.DERIVADO_ADULTO&&session!=null&&journey!=null)
                synchronized(org.springframework.web.util.WebUtils.getSessionMutex(session)){if(journey.equals(session.getAttribute(ACTUAL)))session.setAttribute(DERIVADO,journey);}
            throw failure;
        }
    }
    private final ObjectProvider<RecorridosJdbc> service;private final cl.nexbyte.siger.config.OrigenCliente origen;
    public FormularioController(ObjectProvider<RecorridosJdbc> service,cl.nexbyte.siger.config.OrigenCliente origen){this.service=service;this.origen=origen;}
    public record Capacidades(boolean disponible,String avisoAdulto,String avisoNna){}
    public record Inicio(Tipo tipo,boolean aceptaCanalDigital,String avisoVersion){}
    public record Identificacion(Rol rol,String runNumero,String runDv){@Override public String toString(){return "IdentificacionFormulario[protegida]";}}
    /** Sin indicadores de corrección: con fail-on-unknown-properties, cualquier campo adicional (p. ej. corregirRegistrales) responde 400. */
    public record Declaracion(Map<String,String> atributos){@Override public String toString(){return "DeclaracionFormulario[protegida]";}}
    private RecorridosJdbc enabled(HttpServletRequest request) {
        var s=service.getIfAvailable();if(s==null)throw new FalloTecnico(503);
        if(!request.isSecure())throw new FalloTecnico(403);return s;
    }
    private static <T>ResponseEntity<T> respuesta(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @GetMapping("/capacidades") ResponseEntity<Capacidades> capacidades() {
        var s=service.getIfAvailable();return respuesta(s==null?new Capacidades(false,"",""):new Capacidades(true,s.aviso(Tipo.ADULTO_DECLARADO),s.aviso(Tipo.NNA)));
    }
    @PostMapping("/recorridos") ResponseEntity<RecorridosJdbc.Recorrido> iniciar(@RequestBody Inicio body,HttpServletRequest request) {
        var result=enabled(request).iniciar(body.tipo(),body.aceptaCanalDigital(),body.avisoVersion(),SesionIngreso.iniciar(request),origen.de(request));
        var session=request.getSession();
        synchronized(org.springframework.web.util.WebUtils.getSessionMutex(session)){session.setAttribute(ACTUAL,result.id());session.removeAttribute(DERIVADO);}
        return respuesta(result);
    }
    @GetMapping("/actual") ResponseEntity<RecorridosJdbc.Recorrido> actual(HttpServletRequest request) {
        var s=enabled(request);var session=request.getSession(false);
        if(session==null||!(session.getAttribute(ACTUAL) instanceof UUID id))throw new FalloTecnico(404);
        // Se consume una vez (bajo el mutex de sesión, sin borrar un ACTUAL nuevo de otra petición): así otra persona que use
        // el mismo navegador puede abrir después un recorrido NNA sin quedar redirigida indefinidamente.
        synchronized(org.springframework.web.util.WebUtils.getSessionMutex(session)){
            if(id.equals(session.getAttribute(ACTUAL))&&id.equals(session.getAttribute(DERIVADO))){session.removeAttribute(DERIVADO);session.removeAttribute(ACTUAL);throw new FalloTecnico(RecorridosJdbc.DERIVADO_ADULTO);}
        }
        return respuesta(s.consultar(id,SesionIngreso.actual(request)));
    }
    @DeleteMapping("/recorridos/{id}") ResponseEntity<Void> cancelar(@PathVariable UUID id,HttpServletRequest request) {
        enabled(request).cancelar(id,SesionIngreso.actual(request));
        if(request.getSession(false)!=null&&id.equals(request.getSession().getAttribute(ACTUAL)))request.getSession().removeAttribute(ACTUAL);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @GetMapping("/recorridos/{id}/personas/{persona}") ResponseEntity<Vista> persona(@PathVariable UUID id,@PathVariable String persona,HttpServletRequest request) {
        var s=enabled(request);return respuesta(derivacion(request,id,()->s.persona(id,persona,SesionIngreso.actual(request))));
    }
    @PostMapping("/recorridos/{id}/personas/{persona}/identificacion") ResponseEntity<Vista> identificar(@PathVariable UUID id,@PathVariable String persona,@RequestBody Identificacion body,HttpServletRequest request,Authentication auth) {
        AutenticacionCiudadana identity=auth!=null&&auth.getPrincipal() instanceof IdentidadVerificada value
            &&auth.getDetails() instanceof EvidenciaAutenticacion proof&&proof.corresponde(value)?new AutenticacionCiudadana(value,proof):null;
        var s=enabled(request);
        return respuesta(derivacion(request,id,()->s.identificar(id,persona,body.rol(),body.runNumero(),body.runDv(),SesionIngreso.actual(request),origen.de(request),identity)));
    }
    @PostMapping("/recorridos/{id}/identificacion-extranjera") ResponseEntity<Vista> extranjera(@PathVariable UUID id,HttpServletRequest request) {
        return respuesta(enabled(request).identificarExtranjera(id,SesionIngreso.actual(request),origen.de(request)));
    }
    @PostMapping("/recorridos/{id}/personas/{persona}/declaracion") ResponseEntity<Vista> declarar(@PathVariable UUID id,@PathVariable String persona,@RequestBody Declaracion body,HttpServletRequest request) {
        var s=enabled(request);return respuesta(derivacion(request,id,()->s.declarar(id,persona,body.atributos(),SesionIngreso.actual(request))));
    }
    @DeleteMapping("/recorridos/{id}/personas/{persona}/identificacion") ResponseEntity<Void> reiniciarPersona(@PathVariable UUID id,@PathVariable String persona,HttpServletRequest request) {
        var s=enabled(request);derivacion(request,id,()->{s.reiniciarPersona(id,persona,SesionIngreso.actual(request));return null;});
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
