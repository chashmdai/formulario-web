package cl.nexbyte.siger.identidad;

import java.net.URI;
import java.time.Instant;
import java.util.Date;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import static org.assertj.core.api.Assertions.*;

class ClaveUnicaJwtDecoderFactoryTest {
    @Test void externallySelectedHs256StillValidatesSignatureAndAudience() throws Exception {
        var properties = new ClaveUnicaProperties();
        properties.setClientId("test-client"); properties.setClientSecret("test-only-secret-at-least-32-bytes-long");
        properties.setRedirectUri(URI.create("https://siger.ddn.gob.cl/redirect"));
        properties.setIdTokenAlgorithm(ClaveUnicaProperties.IdTokenAlgorithm.HS256);
        var registration = new SecurityConfiguration().registrations(properties).findByRegistrationId("claveunica");
        var decoder = new ClaveUnicaJwtDecoderFactory(properties).createDecoder(registration);
        var claims = new JWTClaimsSet.Builder().issuer(SecurityConfiguration.ISSUER).subject("not-a-run")
                .audience("test-client").issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(properties.getClientSecret()));
        assertThat(decoder.decode(jwt.serialize()).getSubject()).isEqualTo("not-a-run");
        var wrongSignature = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        wrongSignature.sign(new MACSigner("different-test-secret-at-least-32-bytes"));
        assertThatThrownBy(() -> decoder.decode(wrongSignature.serialize())).isInstanceOf(JwtException.class);
        var wrongAudience = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder(claims).audience("other-client").build());
        wrongAudience.sign(new MACSigner(properties.getClientSecret()));
        assertThatThrownBy(() -> decoder.decode(wrongAudience.serialize())).isInstanceOf(JwtException.class);
    }
}
