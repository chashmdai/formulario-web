package cl.nexbyte.siger.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(cl.nexbyte.siger.entrega.EntregaException.class)
    ProblemDetail entrega(cl.nexbyte.siger.entrega.EntregaException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(error.status()),
                "Recepción no completada, registro no disponible o conflicto de idempotencia. Conserve la misma clave para reintentar.");
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ProblemDetail invalidInput(Exception ignored) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Entrada inválida. Revise los campos obligatorios, sus formatos y límites. El relato admite hasta 2000 caracteres.");
    }
}
