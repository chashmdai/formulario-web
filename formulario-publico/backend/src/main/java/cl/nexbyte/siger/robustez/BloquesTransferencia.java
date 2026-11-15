package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.multimedia.CuposTransferencia;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Después de Spring Security: validar sesión/CSRF antes de iniciar la lectura asíncrona. */
@Component
@Order(-50)
public final class BloquesTransferencia extends OncePerRequestFilter {
    private final CuposTransferencia cupos;
    public BloquesTransferencia(CuposTransferencia cupos) { this.cupos = cupos; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws IOException, ServletException {
        if ("PUT".equals(request.getMethod()) && request.getRequestURI().matches(
                "/api/formulario/recorridos/[a-fA-F0-9-]{36}/cargas/[a-fA-F0-9-]{36}/bloques/[0-9]{1,4}")) {
            LecturaBloque.iniciar(request, response, 8 * 1024 * 1024, cupos.plazoCarga());
        } else chain.doFilter(request, response);
    }
}
