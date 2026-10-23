package cl.nexbyte.siger.comunicaciones;

import cl.nexbyte.siger.identidad.IdentidadVerificada;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ComunicacionController {
    public record PreviewRequest(@NotBlank @Size(max = 2000) String mensaje) { }
    public record PreviewResponse(IdentidadVerificada identidad, String mensaje, boolean almacenada) { }

    @PostMapping("/api/comunicaciones/preview")
    PreviewResponse preview(@AuthenticationPrincipal IdentidadVerificada identity, @Valid @RequestBody PreviewRequest body) {
        return new PreviewResponse(identity, body.mensaje().trim(), false);
    }
}
