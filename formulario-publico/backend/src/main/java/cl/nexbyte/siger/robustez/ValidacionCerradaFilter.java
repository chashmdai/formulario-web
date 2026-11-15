package cl.nexbyte.siger.robustez;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Validación cerrada de una instalación restaurada (scripts/formulario/formulario.py, `arrancar --validacion`).
 *
 * <p>La validación conserva los controles CAS del origen (p. ej. PULL abierto), por lo que la integración debe seguir
 * habilitada para que la aplicación arranque. Este filtro, anterior a toda cadena de seguridad, deja inaccesibles
 * {@code /integracion/**} (reclamar, confirmar ACK, fallos, adjuntos, custodia) y {@code /operacion/**} (controles
 * CAS y demás acciones operativas) <b>con o sin credencial válida</b>: la validación no reserva, no confirma y no
 * modifica controles. La recepción ciudadana se cierra aparte, con sus propias propiedades.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "formulario.validacion.cerrada", havingValue = "true")
public final class ValidacionCerradaFilter extends OncePerRequestFilter {
    public static final String CABECERA = "X-Formulario-Validacion";
    private static final Set<String> CERRADOS = Set.of("integracion", "operacion");
    private final UrlPathHelper rutas = new UrlPathHelper(); // decodifica, quita ';…' y une barras repetidas

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (cerrado(rutas.getPathWithinApplication(request))) {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader(CABECERA, "cerrada");
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        chain.doFilter(request, response);
    }

    /** Primer segmento no vacío, sin distinguir mayúsculas. */
    static boolean cerrado(String path) {
        for (String segmento : path.split("/")) {
            if (!segmento.isEmpty()) return CERRADOS.contains(segmento.toLowerCase(Locale.ROOT));
        }
        return false;
    }
}
