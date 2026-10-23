package cl.nexbyte.siger.identidad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/** Tokens are used only during login; SIGER has no reason to retain/refresh them. */
final class DiscardingAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {
    @Override public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String id, Authentication a, HttpServletRequest r) { return null; }
    @Override public void saveAuthorizedClient(OAuth2AuthorizedClient c, Authentication a, HttpServletRequest r, HttpServletResponse s) { }
    @Override public void removeAuthorizedClient(String id, Authentication a, HttpServletRequest r, HttpServletResponse s) { }
}
