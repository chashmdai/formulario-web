package cl.nexbyte.siger.identidad;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class ProviderLogout {
    public static final String ENDPOINT = "https://accounts.claveunica.gob.cl/api/v1/accounts/app/logout";
    private ProviderLogout() { }
    public static String url(ClaveUnicaProperties properties) {
        return ENDPOINT + "?redirect=" + URLEncoder.encode(properties.getLogoutUri().toString(), StandardCharsets.UTF_8);
    }
}
