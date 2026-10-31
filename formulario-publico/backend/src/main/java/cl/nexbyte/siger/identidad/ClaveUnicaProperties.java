package cl.nexbyte.siger.identidad;

import java.net.URI;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;

/** External configuration. Deliberately has no toString that could expose secrets. */
@ConfigurationProperties("siger.claveunica")
public class ClaveUnicaProperties {
    public enum Stage { SANDBOX, QA, PRODUCTION }
    public enum IdTokenAlgorithm { RS256, HS256 }
    private Stage environment = Stage.SANDBOX;
    private String clientId;
    private String clientSecret;
    private URI redirectUri;
    private URI logoutUri;
    private URI publicOrigin;
    private boolean callbackApproved;
    private IdTokenAlgorithm idTokenAlgorithm = IdTokenAlgorithm.RS256;
    @org.springframework.beans.factory.annotation.Autowired
    private Environment springEnvironment;

    @PostConstruct
    void validate() {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("Configurar CU_CLIENT_ID y CU_CLIENT_SECRET del ambiente seleccionado fuera del repositorio.");
        }
        if (idTokenAlgorithm == IdTokenAlgorithm.HS256 && clientSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("HS256 requiere secreto de al menos 256 bits.");
        }
        validateUri(redirectUri, "/redirect");
        validateUri(logoutUri, "/logout");
        validateUri(publicOrigin, "");
        if (!publicOrigin.getPath().isEmpty() && !publicOrigin.getPath().equals("/")) {
            throw new IllegalStateException("SIGER_PUBLIC_ORIGIN debe contener solamente el origen.");
        }
        if (environment == Stage.PRODUCTION) {
            for (URI uri : new URI[]{redirectUri, logoutUri, publicOrigin}) {
                if (!"https".equals(uri.getScheme()) || !uri.getHost().endsWith(".gob.cl")) {
                    throw new IllegalStateException("Producción requiere HTTPS y un dominio institucional autorizado.");
                }
            }
            if (!springEnvironment.getProperty("server.servlet.session.cookie.secure", Boolean.class, true)) {
                throw new IllegalStateException("Producción requiere cookie Secure.");
            }
            if (!callbackApproved) throw new IllegalStateException("Producción requiere callback habilitado institucionalmente.");
        }
    }

    private void validateUri(URI uri, String path) {
        if (uri == null || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null || (!path.isEmpty() && !path.equals(uri.getPath()))
                || (!path.isEmpty() && (uri.getHost().matches("[0-9.]+") || uri.getHost().contains(":")))
                || !("https".equals(uri.getScheme()) || (environment != Stage.PRODUCTION
                    && "http".equals(uri.getScheme()) && "localhost".equals(uri.getHost())))) {
            throw new IllegalStateException("URI inválida: usar HTTPS; HTTP solo para localhost en desarrollo. Callback /redirect y retorno /logout sin query ni fragmento.");
        }
    }

    public Stage getEnvironment() { return environment; }
    public void setEnvironment(Stage environment) { this.environment = environment; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public URI getRedirectUri() { return redirectUri; }
    public void setRedirectUri(URI redirectUri) { this.redirectUri = redirectUri; }
    public URI getLogoutUri() { return logoutUri; }
    public void setLogoutUri(URI logoutUri) { this.logoutUri = logoutUri; }
    public URI getPublicOrigin() { return publicOrigin; }
    public void setPublicOrigin(URI publicOrigin) { this.publicOrigin = publicOrigin; }
    public boolean isCallbackApproved() { return callbackApproved; }
    public void setCallbackApproved(boolean callbackApproved) { this.callbackApproved = callbackApproved; }
    public IdTokenAlgorithm getIdTokenAlgorithm() { return idTokenAlgorithm; }
    public void setIdTokenAlgorithm(IdTokenAlgorithm algorithm) { this.idTokenAlgorithm = algorithm; }
}
