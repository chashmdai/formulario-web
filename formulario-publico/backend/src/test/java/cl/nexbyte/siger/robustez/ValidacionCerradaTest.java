package cl.nexbyte.siger.robustez;

import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Regresión DDN-UBUNTU 15/11/2026: la validación de formulario.py cerraba la integración por propiedad y, con PULL
 * persistido abierto (pull_enabled=1), la aplicación no arrancaba. Ahora la validación conserva los controles CAS
 * y la integración habilitada, y ValidacionCerradaFilter deja /integracion y /operacion inaccesibles.
 */
class ValidacionCerradaTest {
    static final String INTEGRACION = "integracion-sintetica-validacion-cerrada-2026";
    static final String OPERACION = "operacion-sintetica-validacion-cerrada-2026";

    /** Entorno de la validación sobre las propiedades productivas (integración y custodia autorizadas). */
    static MockEnvironment validacion(boolean integracion) {
        return new MockEnvironment().withProperty("publico.integracion.habilitada", Boolean.toString(integracion))
            .withProperty("publico.recepcion.custodia-durable-autorizada", "true")
            .withProperty("publico.recepcion.habilitada", "false").withProperty("formulario.recepcion.habilitada", "false")
            .withProperty("formulario.validacion.cerrada", "true");
    }

    static ControlesOperacion.Control arrancar(boolean aceptar, boolean pull, MockEnvironment env) throws Exception {
        try (var db = TestDatabase.open(Files.createTempDirectory("validacion-cerrada-"))) {
            TestDatabase.migration(db, sql -> sql.update(
                "UPDATE operation_control SET revision=27,accept_new=?,pull_enabled=?,contingency='NORMAL' WHERE id=1", aceptar, pull));
            var antes = new ControlesOperacion(db).estado();
            new JdbcRecepcionConfiguration().controles(db, env);
            var despues = new ControlesOperacion(db).estado();
            assertThat(despues).as("arrancar no modifica los controles CAS").isEqualTo(antes);
            return despues;
        }
    }

    @Test void conPullAbiertoArrancaConservandoLosControles() throws Exception {
        var control = arrancar(true, true, validacion(true));
        assertThat(control).isEqualTo(new ControlesOperacion.Control(27, true, true, "NORMAL"));
    }

    @Test void conPullCerradoTambienArranca() throws Exception {
        assertThat(arrancar(true, false, validacion(true)).recuperar()).isFalse();
        assertThat(arrancar(false, false, validacion(true)).aceptarNuevos()).isFalse();
    }

    @Test void laValidacionAnteriorConIntegracionCerradaNoArrancabaConPullAbierto() {
        assertThatThrownBy(() -> arrancar(true, true, validacion(false)))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("Controles persistidos incompatibles");
    }

    @Test void segmentoCerradoSinDistinguirMayusculasNiPrefijosParecidos() {
        for (String p : List.of("/integracion", "/integracion/", "/INTEGRACION/v3/x", "/Operacion/v1/control", "/operacion"))
            assertThat(ValidacionCerradaFilter.cerrado(p)).as(p).isTrue();
        for (String p : List.of("/", "", "/api/session", "/integraciones", "/api/integracion", "/acceso-temporal/salud"))
            assertThat(ValidacionCerradaFilter.cerrado(p)).as(p).isFalse();
    }

    static final List<String[]> RUTAS = List.of(
        new String[]{"POST", "/integracion/v3/formulario-completo/entregas/reclamar", INTEGRACION},
        new String[]{"POST", "/integracion/v3/formulario-completo/entregas/00000000-0000-0000-0000-000000000001/confirmacion", INTEGRACION},
        new String[]{"POST", "/integracion/v3/entregas/reclamar", INTEGRACION},
        new String[]{"POST", "/integracion/v2/entregas/00000000-0000-0000-0000-000000000001/confirmacion", INTEGRACION},
        new String[]{"GET", "/integracion/v1/comunicaciones/pendientes", INTEGRACION},
        new String[]{"GET", "/integracion/v3/custodia/candidatos", INTEGRACION},
        new String[]{"PUT", "/operacion/v1/control", OPERACION},
        new String[]{"POST", "/operacion/v1/externas", OPERACION},
        new String[]{"GET", "/INTEGRACION/v3/custodia/candidatos", INTEGRACION},
        new String[]{"GET", "//integracion/v3/custodia/candidatos", INTEGRACION},
        new String[]{"GET", "/integracion;x=1/v3/custodia/candidatos", INTEGRACION},
        new String[]{"GET", "/%69ntegracion/v3/custodia/candidatos", INTEGRACION});

    static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder solicitud(String[] r) {
        return request(org.springframework.http.HttpMethod.valueOf(r[0]), java.net.URI.create("https://localhost" + r[1]))
            .secure(true).header("Authorization", "Bearer " + r[2]).header("If-Match", "\"27\"")
            .contentType("application/json").content("{}");
    }

    @Nested
    @SpringBootTest(properties = {"siger.claveunica.client-id=test", "siger.claveunica.client-secret=test",
        "publico.integracion.token=" + INTEGRACION, "operacion.token=" + OPERACION, "formulario.validacion.cerrada=true"})
    @AutoConfigureMockMvc(print = MockMvcPrint.NONE)
    class Http {
        @Autowired MockMvc mvc;

        @Test void integracionYOperacionCerradasIncluidoConCredencialValida() throws Exception {
            for (String[] r : RUTAS)
                mvc.perform(solicitud(r)).andExpect(status().isServiceUnavailable())
                    .andExpect(header().string(ValidacionCerradaFilter.CABECERA, "cerrada"))
                    .andExpect(header().string("Cache-Control", "no-store"));
        }

        @Test void elRestoNoSeVeAfectado() throws Exception {
            mvc.perform(get("/api/session").secure(true)).andExpect(status().isOk())
                .andExpect(header().doesNotExist(ValidacionCerradaFilter.CABECERA));
        }
    }

    @Nested
    @SpringBootTest(properties = {"siger.claveunica.client-id=test", "siger.claveunica.client-secret=test",
        "publico.integracion.token=" + INTEGRACION, "operacion.token=" + OPERACION})
    @AutoConfigureMockMvc(print = MockMvcPrint.NONE)
    class SinValidacion {
        @Autowired MockMvc mvc;

        @Test void fueraDeValidacionElFiltroNoExisteYLaCredencialSeEvalua() throws Exception {
            // Sin credencial: 401 de la cadena de integración, no el cierre de validación.
            mvc.perform(get("/integracion/v3/custodia/candidatos").secure(true))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist(ValidacionCerradaFilter.CABECERA));
            mvc.perform(solicitud(RUTAS.get(5))).andExpect(header().doesNotExist(ValidacionCerradaFilter.CABECERA));
        }
    }
}
