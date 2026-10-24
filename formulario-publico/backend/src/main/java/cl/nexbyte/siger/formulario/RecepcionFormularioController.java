package cl.nexbyte.siger.formulario;

import cl.nexbyte.siger.comunicaciones.SesionIngreso;
import cl.nexbyte.siger.robustez.FalloTecnico;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/formulario")
public class RecepcionFormularioController {
    private final ObjectProvider<RecepcionFormularioJdbc> service;private final ObjectProvider<AdjuntosFormulario> files;
    public RecepcionFormularioController(ObjectProvider<RecepcionFormularioJdbc> service,ObjectProvider<AdjuntosFormulario> files){this.service=service;this.files=files;}
    private static void seguro(HttpServletRequest request){if(!request.isSecure())throw new FalloTecnico(403);}
    private RecepcionFormularioJdbc enabled(HttpServletRequest request){seguro(request);var s=service.getIfAvailable();if(s==null)throw new FalloTecnico(503);return s;}
    private AdjuntosFormulario adjuntos(HttpServletRequest request){seguro(request);var s=files.getIfAvailable();if(s==null)throw new FalloTecnico(503);return s;}
    private static <T>ResponseEntity<T> ok(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @GetMapping("/recepcion/capacidades") ResponseEntity<Map<String,Boolean>> capacidades(){var s=service.getIfAvailable();return ok(Map.of("nna",s!=null&&s.disponible(),"adultos",s!=null&&s.adultosDisponibles(),"multimedia",s!=null&&s.multimediaDisponible()));}
    @PostMapping("/recorridos/{id}/comunicaciones") ResponseEntity<RecepcionFormularioJdbc.Estado> recibir(@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID key,@RequestBody DatosFormulario datos,HttpServletRequest request){
        return ok(enabled(request).recibir(key,id,SesionIngreso.actual(request),datos));
    }
    @GetMapping("/comunicaciones/{id}") ResponseEntity<RecepcionFormularioJdbc.Estado> consultar(@PathVariable UUID id,HttpServletRequest request){return ok(enabled(request).consultar(id,SesionIngreso.actual(request)));}
    @GetMapping("/recorridos/{id}/comunicacion") ResponseEntity<RecepcionFormularioJdbc.Estado> consultarRecorrido(@PathVariable UUID id,HttpServletRequest request){return ok(enabled(request).consultarRecorrido(id,SesionIngreso.actual(request)));}
    @GetMapping("/recorridos/{id}/adjuntos") ResponseEntity<List<AdjuntosFormulario.Descriptor>> listar(@PathVariable UUID id,HttpServletRequest request){return ok(adjuntos(request).listar(id,SesionIngreso.actual(request)));}
    @PostMapping(path="/recorridos/{id}/adjuntos",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<List<AdjuntosFormulario.Descriptor>> cargar(@PathVariable UUID id,@RequestParam("archivos") List<MultipartFile> batch,HttpServletRequest request)throws IOException {
        var s=adjuntos(request);if(s.multimediaDisponible())throw new FalloTecnico(409);if(batch.size()>5||batch.stream().anyMatch(f->f.isEmpty()||f.getSize()>AdjuntosFormulario.MAX_FILE)||batch.stream().mapToLong(MultipartFile::getSize).sum()>AdjuntosFormulario.MAX_TOTAL)throw new FalloTecnico(413);
        var content=new ArrayList<AdjuntosFormulario.Archivo>();for(var f:batch)content.add(new AdjuntosFormulario.Archivo(f.getOriginalFilename(),f.getBytes()));
        return ok(s.reemplazar(id,SesionIngreso.actual(request),content));
    }
    @DeleteMapping("/recorridos/{id}/adjuntos") ResponseEntity<Void> limpiar(@PathVariable UUID id,HttpServletRequest request){adjuntos(request).reemplazar(id,SesionIngreso.actual(request),List.of());return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
    @RequestMapping(path="/recorridos/{id}/adjuntos/{adjunto}",method={RequestMethod.GET,RequestMethod.HEAD}) void descargar(@PathVariable UUID id,@PathVariable UUID adjunto,HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response)throws IOException{cl.nexbyte.siger.multimedia.TransferenciaPrivada.enviar(adjuntos(request).recurso(id,adjunto,SesionIngreso.actual(request)),false,request,response);}
    static ResponseEntity<byte[]> descarga(AdjuntosFormulario.Archivo file){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox; default-src 'none'")
        .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.nombre(),java.nio.charset.StandardCharsets.UTF_8).build().toString()).body(file.contenido());}
}
