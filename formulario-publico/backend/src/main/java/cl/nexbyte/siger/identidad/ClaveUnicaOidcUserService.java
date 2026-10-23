package cl.nexbyte.siger.identidad;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.client.RestOperations;

public final class ClaveUnicaOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private final OidcUserService delegate = new OidcUserService();

    public ClaveUnicaOidcUserService(RestOperations restOperations) {
        var userService = new DefaultOAuth2UserService();
        userService.setRestOperations(restOperations);
        userService.setRequestEntityConverter(request -> {
            var headers = new HttpHeaders();
            headers.setBearerAuth(request.getAccessToken().getTokenValue());
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            return new RequestEntity<>(headers, HttpMethod.POST,
                    URI.create(request.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUri()));
        });
        delegate.setOauth2UserService(userService);
        delegate.setRetrieveUserInfo(request -> true);
        // CU's name is an object, not the standard OIDC string claim. Preserve the documented contract.
        delegate.setClaimTypeConverterFactory(registration -> claims -> claims);
    }

    @Override public OidcUser loadUser(OidcUserRequest request) {
        var user = delegate.loadUser(request); // Spring also verifies UserInfo sub equals ID token sub.
        if (user.getUserInfo() == null) throw new IllegalStateException("UserInfo obligatorio.");
        IdentidadVerificada.from(user.getUserInfo().getClaims());
        return user;
    }
}
