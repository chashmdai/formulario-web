package cl.nexbyte.siger.identidad;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

/** Fixed algorithm per external configuration; never trust the JWT's unverified alg header. */
final class ClaveUnicaJwtDecoderFactory implements JwtDecoderFactory<ClientRegistration> {
    private final ClaveUnicaProperties properties;
    private final ConcurrentMap<String, JwtDecoder> decoders = new ConcurrentHashMap<>();
    ClaveUnicaJwtDecoderFactory(ClaveUnicaProperties properties) { this.properties = properties; }
    @Override public JwtDecoder createDecoder(ClientRegistration registration) {
        return decoders.computeIfAbsent(registration.getRegistrationId(), ignored -> {
            NimbusJwtDecoder decoder;
            if (properties.getIdTokenAlgorithm() == ClaveUnicaProperties.IdTokenAlgorithm.HS256) {
                var key = new SecretKeySpec(registration.getClientSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
                decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
            } else {
                decoder = NimbusJwtDecoder.withJwkSetUri(registration.getProviderDetails().getJwkSetUri())
                        .jwsAlgorithm(SignatureAlgorithm.RS256).restOperations(new RestTemplate(SecurityConfiguration.requestFactory())).build();
            }
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), new OidcIdTokenValidator(registration)));
            return decoder;
        });
    }
}
