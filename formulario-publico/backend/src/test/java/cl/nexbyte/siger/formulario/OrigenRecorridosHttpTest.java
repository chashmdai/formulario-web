package cl.nexbyte.siger.formulario;

import cl.nexbyte.siger.config.OrigenCliente;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** El controlador entrega al límite antiabuso el origen resuelto, no la conexión compartida del proxy. */
class OrigenRecorridosHttpTest {
    @Test void iniciarUsaElOrigenDelCiudadanoSoloDesdeElProxyConfiable() {
        var journeys = mock(RecorridosJdbc.class);
        @SuppressWarnings("unchecked") ObjectProvider<RecorridosJdbc> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(journeys);
        when(journeys.iniciar(any(), anyBoolean(), anyString(), anyString(), anyString())).thenReturn(mock(RecorridosJdbc.Recorrido.class));
        var env = new MockEnvironment().withProperty("formulario.origen.cabecera", "X-Ddn-Client-Ip").withProperty("formulario.origen.proxies-confiables", "172.20.0.0/16");
        var controller = new FormularioController(provider, new OrigenCliente(env));
        for (var c : List.of(new String[]{"172.20.0.5", "203.0.113.7", "203.0.113.7"}, new String[]{"172.20.0.5", "203.0.113.8", "203.0.113.8"},
                new String[]{"10.9.8.7", "203.0.113.9", "10.9.8.7"})) {
            var request = new MockHttpServletRequest("POST", "/api/formulario/recorridos");
            request.setSecure(true); request.setRemoteAddr(c[0]); request.addHeader("X-Ddn-Client-Ip", c[1]); request.addHeader("X-Forwarded-For", "198.51.100.1");
            controller.iniciar(new FormularioController.Inicio(IdentificacionCiudadana.Tipo.NNA, true, "NNA_LAB_1"), request);
            verify(journeys).iniciar(eq(IdentificacionCiudadana.Tipo.NNA), eq(true), eq("NNA_LAB_1"), anyString(), eq(c[2]));
        }
    }
}
