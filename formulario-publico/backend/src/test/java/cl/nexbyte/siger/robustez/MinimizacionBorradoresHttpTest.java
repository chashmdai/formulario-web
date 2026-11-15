package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.custodia.MinimizacionBorradores;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"siger.claveunica.client-id=test","siger.claveunica.client-secret=test",
    "operacion.token=operacion-sintetica-minimizacion-2026","ciclo-vida.online-habilitado=false"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE) @Import(MinimizacionBorradoresHttpTest.Fixture.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class MinimizacionBorradoresHttpTest {
    @Autowired MockMvc mvc;
    @TestConfiguration static class Fixture {
        @Bean MinimizacionBorradores minimizacion() {
            var service=mock(MinimizacionBorradores.class);
            when(service.estado()).thenReturn(Map.of("minimizadas",2,"conservadas",1,"incidencias",0));
            return service;
        }
    }
    @Test void observabilidadDisponibleConCicloDeVidaApagadoYNoStore()throws Exception {
        mvc.perform(get("/operacion/v1/custodia").secure(true)
            .header("Authorization","Bearer operacion-sintetica-minimizacion-2026"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(content().json("{\"minimizacionBorradores\":{\"minimizadas\":2,\"conservadas\":1,\"incidencias\":0}}"));
    }
    @Test void contadoresRequierenTokenYTls()throws Exception {
        mvc.perform(get("/operacion/v1/custodia").secure(true)).andExpect(status().isUnauthorized());
        mvc.perform(get("/operacion/v1/custodia")
            .header("Authorization","Bearer operacion-sintetica-minimizacion-2026")).andExpect(status().isForbidden());
    }
}
