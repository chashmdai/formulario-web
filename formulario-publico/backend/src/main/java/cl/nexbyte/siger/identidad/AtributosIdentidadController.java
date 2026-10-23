package cl.nexbyte.siger.identidad;
import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
@RestController
public class AtributosIdentidadController {
    @GetMapping("/api/identidad/atributos") ProyeccionAtributos.Vista atributos(Authentication auth){
        if(auth==null||!(auth.getPrincipal() instanceof IdentidadVerificada id)||!(auth.getDetails() instanceof EvidenciaAutenticacion proof))throw new cl.nexbyte.siger.robustez.FalloTecnico(401);
        return ProyeccionAtributos.claveUnica(id,proof);
    }
}
