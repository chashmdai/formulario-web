package cl.nexbyte.siger.identidad;

import java.io.IOException;
import java.util.Set;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Deployment-only allowlist, before security, controllers and multipart processing. */
@Component
@Order(-150)
@ConditionalOnProperty(name = "siger.certificacion-claveunica", havingValue = "true")
public final class CertificationFilter extends OncePerRequestFilter {
    private static final Set<String> READ = Set.of("/", "/index.html", "/favicon.svg", "/api/session", "/api/csrf",
            "/oauth2/authorization/claveunica", "/redirect", "/logout", "/auth/session-expired");

    public CertificationFilter(Environment environment) {
        for (String key : Set.of("publico.recepcion.habilitada", "publico.nna.habilitado", "formulario.adultos.habilitado",
                "formulario.recepcion.habilitada", "publico.integracion.habilitada", "PISEE_SRCEI_ENABLED",
                "ciclo-vida.online-habilitado", "ciclo-vida.eliminacion.habilitada")) {
            if (environment.getProperty(key, Boolean.class, false)) {
                throw new IllegalStateException("Certificación requiere deshabilitar recepción, integración y mantenimiento: " + key);
            }
        }
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean read = Set.of("GET", "HEAD").contains(request.getMethod());
        if (read && (READ.contains(path) || path.startsWith("/assets/"))
                || "POST".equals(request.getMethod()) && "/auth/logout".equals(path)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(503);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/problem+json");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        response.setHeader("Referrer-Policy", "no-referrer");
        if (request.isSecure()) response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        response.getWriter().write("{\"title\":\"Recepción suspendida: sitio exclusivo de certificación ClaveÚnica\",\"status\":503}");
    }
}
