package cl.nexbyte.siger.identidad;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {
    private final ClaveUnicaProperties properties;
    public SessionController(ClaveUnicaProperties properties) { this.properties = properties; }

    public record SessionView(boolean authenticated, IdentidadVerificada identity, String environment, boolean loginAvailable, String ingresoScope) { }
    @GetMapping("/api/session")
    SessionView session(Authentication auth) {
        var identity = auth != null && auth.getPrincipal() instanceof IdentidadVerificada i ? i : null;
        var scope=identity!=null&&auth.getDetails() instanceof EvidenciaAutenticacion proof?proof.id().toString():null;
        return new SessionView(identity != null, identity, properties.getEnvironment().name(), properties.isCallbackApproved(),scope);
    }
    public record CsrfView(String token, String parameterName, String headerName) { }
    @GetMapping("/api/csrf")
    CsrfView csrf(CsrfToken token) { return new CsrfView(token.getToken(), token.getParameterName(), token.getHeaderName()); }

    @GetMapping("/logout")
    ResponseEntity<Void> logoutReturn(HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof IdentidadVerificada) return ResponseEntity.status(409).build();
        new SecurityContextLogoutHandler().logout(request, response, auth);
        return ResponseEntity.status(303).header(HttpHeaders.LOCATION, properties.getPublicOrigin() + "/?session=closed").build();
    }

    @GetMapping("/auth/session-expired")
    ResponseEntity<Void> expired(Authentication auth) {
        // Browser can clear CU after local expiration; an active session requires CSRF-protected POST logout.
        if (auth != null && auth.getPrincipal() instanceof IdentidadVerificada) return ResponseEntity.status(409).build();
        return ResponseEntity.status(303).header(HttpHeaders.LOCATION, ProviderLogout.url(properties)).build();
    }
}
