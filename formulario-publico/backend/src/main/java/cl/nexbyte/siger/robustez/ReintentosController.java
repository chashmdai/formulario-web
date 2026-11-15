package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.formulario.RecepcionFormularioJdbc;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("jdbc-reception") @RequestMapping("/operacion/v1")
public class ReintentosController {
    private final RecepcionJdbc store;
    private final ObjectProvider<RecepcionFormularioJdbc> completo;
    public ReintentosController(RecepcionJdbc store,ObjectProvider<RecepcionFormularioJdbc> completo){
        this.store=store;this.completo=completo;
    }
    public record Reintento(int intentosEsperados,String motivo){}
    @PostMapping("/entregas/{id}/reintentar") @ResponseStatus(HttpStatus.NO_CONTENT)
    void reintentar(@PathVariable UUID id,@RequestBody Reintento body){
        store.reponer(id,body.intentosEsperados(),body.motivo());
    }
    @PostMapping("/formulario-completo/entregas/{id}/reintentar") @ResponseStatus(HttpStatus.NO_CONTENT)
    void reintentarCompleto(@PathVariable UUID id,@RequestBody Reintento body){
        var service=completo.getIfAvailable();if(service==null)throw new FalloTecnico(503);
        service.reponer(id,body.intentosEsperados(),body.motivo());
    }
}
