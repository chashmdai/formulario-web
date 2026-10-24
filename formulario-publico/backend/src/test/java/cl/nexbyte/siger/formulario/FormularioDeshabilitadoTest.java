package cl.nexbyte.siger.formulario;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"siger.claveunica.client-id=test","siger.claveunica.client-secret=test"})
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class FormularioDeshabilitadoTest {
    @Autowired MockMvc mvc;
    @Test void sinAlmacenRealNoHayRecorridoNiFallbackEnMemoria()throws Exception {
        mvc.perform(get("/api/formulario/capacidades")).andExpect(status().isOk()).andExpect(jsonPath("$.disponible").value(false)).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(post("/api/formulario/recorridos").secure(true).with(csrf()).contentType("application/json")
            .content("{\"tipo\":\"NNA\",\"aceptaCanalDigital\":false,\"avisoVersion\":\"NNA_LAB_1\"}" )).andExpect(status().isServiceUnavailable());
    }
}
