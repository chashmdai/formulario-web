package cl.nexbyte.siger.robustez;
import org.springframework.dao.DataAccessException;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
public class ErroresTecnicos{
    @ExceptionHandler(FalloTecnico.class) ResponseEntity<ProblemDetail> tecnico(FalloTecnico e){return respuesta(e.status);}
    @ExceptionHandler(DataAccessException.class) ResponseEntity<ProblemDetail> persistencia(DataAccessException e){return respuesta(503);}
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<ProblemDetail> invalido(IllegalArgumentException e){return respuesta(400);}
    private ResponseEntity<ProblemDetail> respuesta(int code){
        var body=ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(code),"Operación no completada. Conserve su clave o identificador para reintentar.");
        var builder=ResponseEntity.status(code).cacheControl(CacheControl.noStore());if(code==503||code==429)builder.header("Retry-After","2");return builder.body(body);
    }
}
