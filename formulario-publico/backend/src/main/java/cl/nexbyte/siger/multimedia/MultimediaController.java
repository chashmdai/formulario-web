package cl.nexbyte.siger.multimedia;
import cl.nexbyte.siger.comunicaciones.SesionIngreso;
import cl.nexbyte.siger.robustez.FalloTecnico;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/formulario/recorridos/{id}")
public class MultimediaController{
 private final ObjectProvider<CargasMultimedia> service;private final ObjectProvider<cl.nexbyte.siger.formulario.AdjuntosFormulario> files;
 public MultimediaController(ObjectProvider<CargasMultimedia> service,ObjectProvider<cl.nexbyte.siger.formulario.AdjuntosFormulario> files){this.service=service;this.files=files;}
 private CargasMultimedia enabled(HttpServletRequest r){if(!r.isSecure())throw new FalloTecnico(403);var s=service.getIfAvailable();if(s==null)throw new FalloTecnico(503);return s;}
 private static <T>ResponseEntity<T> ok(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
 @GetMapping("/multimedia/politica") ResponseEntity<Map<String,Object>> politica(@PathVariable UUID id,HttpServletRequest r){var s=enabled(r);s.lote(id,SesionIngreso.actual(r));return ok(Map.of("habilitada",true,"limites",s.politica(),"formatos",s.politica().formatos(),"deshabilitados",Map.of("doc","Word binario: validación segura no habilitada","heic","Decodificación HEIC no verificada","heif","Decodificación HEIF no verificada")));}
 @GetMapping("/cargas") ResponseEntity<List<CargasMultimedia.Estado>> cargas(@PathVariable UUID id,HttpServletRequest r){return ok(enabled(r).listar(id,SesionIngreso.actual(r)));}
 @PostMapping("/cargas") ResponseEntity<CargasMultimedia.Estado> iniciar(@PathVariable UUID id,@RequestBody CargasMultimedia.Inicio body,HttpServletRequest r){return ok(enabled(r).iniciar(id,SesionIngreso.actual(r),body));}
 @GetMapping("/cargas/{carga}") ResponseEntity<CargasMultimedia.Estado> carga(@PathVariable UUID id,@PathVariable UUID carga,HttpServletRequest r){return ok(enabled(r).estado(id,carga,SesionIngreso.actual(r)));}
 @PutMapping(path="/cargas/{carga}/bloques/{index}",consumes="application/octet-stream") ResponseEntity<CargasMultimedia.Estado> bloque(@PathVariable UUID id,@PathVariable UUID carga,@PathVariable int index,@RequestHeader("X-SHA256") String hash,HttpServletRequest r)throws IOException{return ok(enabled(r).bloque(id,carga,SesionIngreso.actual(r),index,hash,r.getContentLengthLong(),r.getInputStream()));}
 @PostMapping("/cargas/{carga}/finalizar") ResponseEntity<CargasMultimedia.Estado> finalizar(@PathVariable UUID id,@PathVariable UUID carga,HttpServletRequest r){return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(enabled(r).finalizar(id,carga,SesionIngreso.actual(r)));}
 @DeleteMapping("/cargas/{carga}") ResponseEntity<Void> cancelar(@PathVariable UUID id,@PathVariable UUID carga,HttpServletRequest r){enabled(r).cancelar(id,carga,SesionIngreso.actual(r));return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
 @GetMapping("/multimedia/manifiesto") ResponseEntity<CargasMultimedia.Lote> lote(@PathVariable UUID id,HttpServletRequest r){return ok(enabled(r).lote(id,SesionIngreso.actual(r)));}
 @PostMapping("/multimedia/manifiesto") ResponseEntity<CargasMultimedia.Lote> confirmar(@PathVariable UUID id,@RequestBody CargasMultimedia.Manifest body,HttpServletRequest r){return ok(enabled(r).confirmar(id,SesionIngreso.actual(r),body));}
 @GetMapping("/adjuntos/{adjunto}/vista") ResponseEntity<CargasMultimedia.Vista> vista(@PathVariable UUID id,@PathVariable UUID adjunto,HttpServletRequest r){return ok(enabled(r).vista(id,adjunto,SesionIngreso.actual(r)));}
 @RequestMapping(path="/adjuntos/{adjunto}/representacion",method={RequestMethod.GET,RequestMethod.HEAD}) void representar(@PathVariable UUID id,@PathVariable UUID adjunto,HttpServletRequest r,HttpServletResponse response)throws IOException{var source=enabled(r).representacion(id,adjunto,SesionIngreso.actual(r),files.getObject());TransferenciaPrivada.enviar(source,true,r,response);}

}
