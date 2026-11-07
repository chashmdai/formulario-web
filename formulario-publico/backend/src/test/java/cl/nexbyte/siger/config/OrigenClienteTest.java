package cl.nexbyte.siger.config;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

/** El origen antiabuso solo cambia con la cabecera propia del proxy confiable; nada aportado por clientes cuenta. */
class OrigenClienteTest {
    private static final String H = "X-Ddn-Client-Ip";
    private static MockHttpServletRequest from(String peer, String... headers) {
        var r = new MockHttpServletRequest(); r.setRemoteAddr(peer);
        for (int i = 0; i < headers.length; i += 2) r.addHeader(headers[i], headers[i + 1]);
        return r;
    }
    private static OrigenCliente trusting(String proxies) { return new OrigenCliente(H, proxies, Clock.systemUTC()); }

    @Test void sinConfiguracionConservaLaDireccionTcpEIgnoraCualquierCabecera() {
        var origen = new OrigenCliente("", "", Clock.systemUTC());
        assertFalse(origen.habilitado());
        assertEquals("172.20.0.5", origen.de(from("172.20.0.5", H, "203.0.113.7", "X-Forwarded-For", "198.51.100.1", "CF-Connecting-IP", "198.51.100.2")));
    }
    @Test void elProxyConfiableDistingueCiudadanosYUnaMismaDireccionConservaSuClave() {
        var origen = trusting("172.20.0.0/16");
        assertEquals("203.0.113.7", origen.de(from("172.20.0.5", H, "203.0.113.7")));
        assertEquals("203.0.113.8", origen.de(from("172.20.0.5", H, " 203.0.113.8 ")));
        assertEquals(origen.de(from("172.20.0.5", H, "203.0.113.7")), origen.de(from("172.20.0.9", H, "203.0.113.7")));
    }
    @Test void unParNoConfiableNoPuedeFalsificarElOrigenConNingunaCabecera() {
        var origen = trusting("172.20.0.0/16");
        for (var header : new String[]{H, "X-Forwarded-For", "CF-Connecting-IP", "X-Real-IP", "Forwarded"})
            assertEquals("10.9.8.7", origen.de(from("10.9.8.7", header, "203.0.113.7")), header);
        // Desde el proxy, solo su propia cabecera cuenta: X-Forwarded-For o CF-Connecting-IP llegados al backend se ignoran.
        assertEquals("172.20.0.5", origen.de(from("172.20.0.5", "X-Forwarded-For", "203.0.113.7", "CF-Connecting-IP", "203.0.113.8")));
    }
    @Test void valoresAmbiguosOInvalidosVuelvenALaDireccionTcp() {
        var origen = trusting("172.20.0.0/16");
        for (var value : new String[]{"", "unknown", "203.0.113.7, 198.51.100.1", "203.0.113.300", "example.org", "fe80::1%eth0", "1".repeat(50), "203.0.113.7:443"})
            assertEquals("172.20.0.5", origen.de(from("172.20.0.5", H, value)), value);
        var twice = from("172.20.0.5", H, "203.0.113.7"); twice.addHeader(H, "198.51.100.1");
        assertEquals("172.20.0.5", origen.de(twice));
    }
    @Test void ipv6SeAgrupaPorPrefijo64ParaQueRotarDireccionesNoEludaLaCuota() {
        var origen = trusting("172.20.0.0/16");
        var first = origen.de(from("172.20.0.5", H, "2001:db8:abcd:12::1"));
        assertEquals("2001:db8:abcd:12:0:0:0:0/64", first);
        assertEquals(first, origen.de(from("172.20.0.5", H, "2001:db8:abcd:12:ffff:1:2:3")));
        assertNotEquals(first, origen.de(from("172.20.0.5", H, "2001:db8:abcd:13::1")));
        assertEquals("203.0.113.7", origen.de(from("172.20.0.5", H, "::ffff:203.0.113.7")));
    }
    @Test void reconoceAlProxyPorNombreDeServicioInternoYPorCidrIpv6() {
        assertEquals("203.0.113.7", trusting("localhost").de(from("127.0.0.1", H, "203.0.113.7")));
        assertEquals("127.0.0.2", trusting("localhost").de(from("127.0.0.2", H, "203.0.113.7")));
        assertEquals("203.0.113.7", trusting("fd00:1::/64").de(from("fd00:1::5", H, "203.0.113.7")));
    }
    @Test void configuracionInseguraOIncompletaImpideArrancar() {
        for (var header : new String[]{"X-Forwarded-For", "x-real-ip", "CF-Connecting-IP", "X Mal", "Forwarded"})
            assertThrows(IllegalStateException.class, () -> new OrigenCliente(header, "172.20.0.0/16", Clock.systemUTC()), header);
        assertThrows(IllegalStateException.class, () -> trusting(""));
        assertThrows(IllegalStateException.class, () -> trusting("0.0.0.0/33"));
        assertThrows(IllegalStateException.class, () -> trusting("proxy;rm"));
    }
}
