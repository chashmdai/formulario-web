package cl.nexbyte.siger.identidad;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;

final class CallbackAvailabilityFilter extends OncePerRequestFilter {
    private final ClaveUnicaProperties properties;
    CallbackAvailabilityFilter(ClaveUnicaProperties properties) { this.properties = properties; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof IdentidadVerificada) {
            if (path.equals("/redirect")) {
                response.setStatus(409); // A forged/replayed callback must never log out an existing citizen session.
                return;
            }
            if (path.equals("/oauth2/authorization/claveunica")) {
                response.sendRedirect(properties.getPublicOrigin().toString());
                return;
            }
        }
        if (path.equals("/oauth2/authorization/claveunica") && !properties.isCallbackApproved()) {
            response.setStatus(503);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"title\":\"Callback pendiente de habilitación institucional\",\"status\":503}");
            return;
        }
        chain.doFilter(request, response);
    }
}
