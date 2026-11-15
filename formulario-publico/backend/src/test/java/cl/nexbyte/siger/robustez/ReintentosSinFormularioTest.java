package cl.nexbyte.siger.robustez;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(properties={"operacion.token=operacion-sintetica-reintentos-2026"})
@ContextConfiguration(classes={ReintentosController.class,OperacionSecurity.class,ErroresTecnicos.class,
    ReintentosHttpTest.Seguridad.class})
@ActiveProfiles("jdbc-reception")
class ReintentosSinFormularioTest {
    @Autowired MockMvc mvc;
    @MockitoBean RecepcionJdbc legado;
    @Test void formularioDeshabilitadoNoActuaSobreLaColaLegada()throws Exception {
        mvc.perform(post("/operacion/v1/formulario-completo/entregas/"+UUID.randomUUID()+"/reintentar").secure(true)
            .header("Authorization","Bearer operacion-sintetica-reintentos-2026").contentType("application/json")
            .content("{\"intentosEsperados\":10,\"motivo\":\"RECEPTOR_RECUPERADO\"}"))
            .andExpect(status().isServiceUnavailable()).andExpect(header().string("Cache-Control","no-store"));
        verifyNoInteractions(legado);
    }
}
