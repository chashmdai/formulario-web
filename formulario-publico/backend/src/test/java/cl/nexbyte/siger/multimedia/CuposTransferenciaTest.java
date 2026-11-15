package cl.nexbyte.siger.multimedia;

import cl.nexbyte.siger.config.OrigenCliente;
import cl.nexbyte.siger.robustez.FalloTecnico;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

class CuposTransferenciaTest {
    // Cupos fijados explícitamente: las aserciones no dependen de los valores por defecto.
    private final MockEnvironment env = new MockEnvironment().withProperty("formulario.transferencias.cargas", "2").withProperty("formulario.transferencias.descargas", "4")
            .withProperty("formulario.transferencias.descargas-por-origen", "1");
    private CuposTransferencia cupos() { return new CuposTransferencia(env, new OrigenCliente(env)); }
    private MockHttpServletRequest request(String ip) {
        var req = new MockHttpServletRequest(); req.setRemoteAddr(ip); return req;
    }
    private void lleno(org.junit.jupiter.api.function.Executable action) {
        assertEquals(429, assertThrows(FalloTecnico.class, action).status);
    }

    @Test void valoresPorDefectoToleranConexionesLentasYExigenVariosOrigenesParaSaturar() {
        var defecto = new MockEnvironment();
        var cupos = new CuposTransferencia(defecto, new OrigenCliente(defecto));
        assertEquals(Duration.ofMinutes(3), cupos.plazoCarga());
        assertEquals(Duration.ofMinutes(10), cupos.plazoDescarga(false));
        assertEquals(Duration.ofMinutes(15), cupos.plazoDescarga(true));
        var cargas = new java.util.ArrayList<CuposTransferencia.Permiso>();
        try {
            for (int i = 1; i <= 6; i++) cargas.add(cupos.carga(request("192.0.2." + i)));
            lleno(() -> cupos.carga(request("192.0.2.7")));
            var req = request("198.51.100.1");
            try (var a = cupos.descarga(req); var b = cupos.descarga(req)) { lleno(() -> cupos.descarga(req)); }
        } finally { cargas.forEach(CuposTransferencia.Permiso::close); }
    }

    @Test void sigerTienePoolExclusivoAunqueCiudadaniaOcupeTodosSusPermisos() {
        var cupos = cupos();
        try (var a = cupos.descarga(request("192.0.2.1")); var b = cupos.descarga(request("192.0.2.2"));
             var c = cupos.descarga(request("192.0.2.3")); var d = cupos.descarga(request("192.0.2.4"))) {
            lleno(() -> cupos.descarga(request("192.0.2.5")));
            try (var s1 = cupos.integracion(); var s2 = cupos.integracion()) { lleno(cupos::integracion); }
        }
        try (var otra = cupos.descarga(request("192.0.2.1")); var siger = cupos.integracion()) { }
    }

    @Test void unOrigenNoPuedeOcuparMasDeSuCupoYLasCargasSonIndependientes() {
        var cupos = cupos(); var req = request("192.0.2.1");
        try (var a = cupos.carga(req); var b = cupos.descarga(req)) {
            lleno(() -> cupos.carga(req)); lleno(() -> cupos.descarga(req));
            try (var otro = cupos.carga(request("192.0.2.2"))) { }
        }
        try (var a = cupos.carga(req); var b = cupos.descarga(req)) { }
    }

    @Test void ipv6CompartePrefijoYNoConfiaEnCabecerasDelCliente() {
        var cupos = cupos(); var primero = request("2001:db8:1:2::1");
        var segundo = request("2001:db8:1:2::2"); segundo.addHeader("X-Forwarded-For", "192.0.2.5");
        try (var a = cupos.carga(primero)) { lleno(() -> cupos.carga(segundo)); }
    }

    @Test void soloAceptaOrigenInternoDesdeProxyConfiable() {
        env.withProperty("formulario.origen.cabecera", "X-Ddn-Client-Ip")
                .withProperty("formulario.origen.proxies-confiables", "127.0.0.1/32");
        var cupos = cupos();
        var a = request("127.0.0.1"); a.addHeader("X-Ddn-Client-Ip", "192.0.2.1");
        var b = request("127.0.0.1"); b.addHeader("X-Ddn-Client-Ip", "192.0.2.2");
        try (var uno = cupos.carga(a); var dos = cupos.carga(b)) { lleno(() -> cupos.carga(a)); }
        a.setRemoteAddr("192.0.2.8"); b.setRemoteAddr("192.0.2.8");
        try (var uno = cupos.carga(a)) { lleno(() -> cupos.carga(b)); }
    }

    @Test void closeIdempotenteNoInflaElPoolTrasErrores() {
        var cupos = cupos(); var req = request("192.0.2.1");
        var permiso = cupos.carga(req); permiso.close(); permiso.close();
        try (var a = cupos.carga(req); var b = cupos.carga(request("192.0.2.2"))) {
            lleno(() -> cupos.carga(request("192.0.2.3")));
        }
    }

    @Test void configuracionValidaYPlazosIndependientes() {
        env.withProperty("formulario.transferencias.descargas-siger", "1")
                .withProperty("formulario.transferencias.plazo-descarga", "PT1S")
                .withProperty("formulario.transferencias.plazo-siger", "PT1H");
        var cupos = cupos();
        assertEquals(Duration.ofSeconds(1), cupos.plazoDescarga(false));
        assertEquals(Duration.ofHours(1), cupos.plazoDescarga(true));
        try (var uno = cupos.integracion()) { lleno(cupos::integracion); }
        env.withProperty("formulario.transferencias.cargas", "0");
        assertThrows(IllegalArgumentException.class, this::cupos);
    }
}
