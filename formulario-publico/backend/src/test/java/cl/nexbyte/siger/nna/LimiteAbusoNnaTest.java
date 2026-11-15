package cl.nexbyte.siger.nna;

import cl.nexbyte.siger.config.OrigenCliente;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import static org.assertj.core.api.Assertions.assertThat;

class LimiteAbusoNnaTest {
    private static final String HEADER = "X-Ddn-Client-Ip";

    private LimiteAbusoNna limite(boolean confiar) {
        var env = new MockEnvironment();
        if (confiar) env.withProperty("formulario.origen.cabecera", HEADER)
            .withProperty("formulario.origen.proxies-confiables", "192.0.2.0/24");
        return new LimiteAbusoNna(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), new OrigenCliente(env));
    }

    private MockHttpServletResponse solicitar(LimiteAbusoNna filtro, String peer, String ip, boolean sesion)
            throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/nna/comunicaciones");
        request.setRemoteAddr(peer);
        request.addHeader(HEADER, ip);
        request.addHeader("X-Forwarded-For", ip);
        if (sesion) request.setSession(new MockHttpSession());
        var response = new MockHttpServletResponse();
        filtro.doFilter(request, response, (q, r) -> r.getWriter().write("permitido"));
        return response;
    }

    @Test void ciudadanosTrasElMismoProxyTienenCuotasIndependientesAunqueNoHayaSesion() throws Exception {
        var filtro = limite(true);
        for (int i = 0; i < 6; i++)
            assertThat(solicitar(filtro, "192.0.2.10", "203.0.113.1", false).getStatus()).isEqualTo(200);
        var bloqueada = solicitar(filtro, "192.0.2.10", "203.0.113.1", false);
        assertThat(bloqueada.getStatus()).isEqualTo(429);
        assertThat(bloqueada.getHeader("Retry-After")).isEqualTo("60");
        assertThat(bloqueada.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(bloqueada.getContentAsString()).isEmpty();
        assertThat(solicitar(filtro, "192.0.2.10", "203.0.113.2", false).getStatus()).isEqualTo(200);
    }

    @Test void rotarSesionesNoEvitaCuotaIpYOtroCiudadanoPuedeContinuar() throws Exception {
        var filtro = limite(true);
        for (int i = 0; i < 30; i++)
            assertThat(solicitar(filtro, "192.0.2.10", "203.0.113.1", true).getStatus()).isEqualTo(200);
        assertThat(solicitar(filtro, "192.0.2.11", "203.0.113.1", true).getStatus()).isEqualTo(429);
        assertThat(solicitar(filtro, "192.0.2.10", "203.0.113.2", true).getStatus()).isEqualTo(200);
    }

    @Test void sinConfianzaConfiguradaRotarCabecerasNoEvitaCuotaTcp() throws Exception {
        var filtro = limite(false);
        for (int i = 0; i < 30; i++)
            assertThat(solicitar(filtro, "192.0.2.10", "203.0.113." + (i + 1), true).getStatus()).isEqualTo(200);
        assertThat(solicitar(filtro, "192.0.2.10", "203.0.113.99", true).getStatus()).isEqualTo(429);
    }

    @Test void unParNoConfiableNoPuedeEludirCuotaConCabeceras() throws Exception {
        var filtro = limite(true);
        for (int i = 0; i < 30; i++)
            assertThat(solicitar(filtro, "198.51.100.10", "203.0.113." + (i + 1), true).getStatus()).isEqualTo(200);
        assertThat(solicitar(filtro, "198.51.100.10", "203.0.113.99", true).getStatus()).isEqualTo(429);
    }

    @Test void ipv6ComparteCuotaPor64AunqueCambienProxySesionYDireccion() throws Exception {
        var filtro = limite(true);
        for (int i = 0; i < 30; i++)
            assertThat(solicitar(filtro, "192.0.2.10", "2001:db8:1:2::" + (i + 1), true).getStatus()).isEqualTo(200);
        assertThat(solicitar(filtro, "192.0.2.11", "2001:db8:1:2::99", true).getStatus()).isEqualTo(429);
        assertThat(solicitar(filtro, "192.0.2.10", "2001:db8:1:3::1", true).getStatus()).isEqualTo(200);
    }
}
