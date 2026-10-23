package cl.nexbyte.siger.robustez;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @Profile("jdbc-reception") @RequestMapping("/operacion/v1")
public class OperacionController{
    private final BaseJdbc db;private final ControlesOperacion controls;
    public OperacionController(BaseJdbc db,ControlesOperacion controls){this.db=db;this.controls=controls;}
    public record Cambio(boolean aceptarNuevos,boolean recuperar,String contingencia,String motivo){}
    @GetMapping("/control") ResponseEntity<ControlesOperacion.Control> control(){var c=controls.estado();return ResponseEntity.ok().eTag(Long.toString(c.revision())).body(c);}
    @PutMapping("/control") ResponseEntity<ControlesOperacion.Control> cambiar(@RequestHeader("If-Match")String version,@RequestBody Cambio body){
        if(!version.matches("\"[0-9]{1,18}\""))throw new FalloTecnico(400);
        var result=controls.cambiar(Long.parseLong(version.substring(1,version.length()-1)),body.aceptarNuevos(),body.recuperar(),body.contingencia(),body.motivo());
        return ResponseEntity.ok().eTag(Long.toString(result.revision())).body(result);
    }
    @GetMapping("/eventos") List<Map<String,Object>> eventos(@RequestParam(defaultValue="0")long despues,@RequestParam(defaultValue="100")int limite){
        if(despues<0||limite<1||limite>200)throw new FalloTecnico(400);
        return db.sql.queryForList("SELECT seq,at_ms,category,aggregate_id,code FROM operational_event WHERE seq>? ORDER BY seq LIMIT ?",despues,limite);
    }
    @GetMapping("/controles-historial") List<Map<String,Object>> historial(){return db.sql.queryForList("SELECT * FROM control_audit ORDER BY revision DESC LIMIT 100");}
    public record Preparar(UUID id,String tipo){}
    public record Transicion(long revision,String estado,String referenciaProveedor,String codigo){}
    @PostMapping("/externas") RegistroOperacionesExternas.Operacion preparar(@RequestBody Preparar body){return new RegistroOperacionesExternas(db).preparar(body.id(),body.tipo());}
    @PutMapping("/externas/{id}") RegistroOperacionesExternas.Operacion transicion(@PathVariable UUID id,@RequestBody Transicion body){return new RegistroOperacionesExternas(db).transicion(id,body.revision(),body.estado(),body.referenciaProveedor(),body.codigo());}
    @GetMapping("/entregas") List<Map<String,Object>> entregas(){return db.sql.queryForList("SELECT state,COUNT(*) total,MIN(created_ms) oldest_ms FROM public_delivery GROUP BY state");}
    @GetMapping("/entregas-pendientes") List<Map<String,Object>> pendientes(){return db.sql.queryForList("SELECT id,state,attempts,next_attempt_ms,lease_until_ms,last_error,created_ms FROM public_delivery WHERE state<>'CONFIRMADA' ORDER BY created_ms,id LIMIT 100");}
    @GetMapping("/externas") List<Map<String,Object>> externas(){return db.sql.queryForList("SELECT id,kind,state,provider_ref,last_code,updated_ms,revision FROM external_operation ORDER BY updated_ms,id LIMIT 100");}
    @GetMapping("/resumen") Map<String,Object> resumen(){
        return Map.of("control",controls.estado(),"eventos",db.sql.queryForObject("SELECT COUNT(*) FROM operational_event",Long.class),
            "notificacionesAdapterHabilitado",false,"operacionesExternas",db.sql.queryForList("SELECT kind,state,COUNT(*) total FROM external_operation GROUP BY kind,state"));
    }
}
