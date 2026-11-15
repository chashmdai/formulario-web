package cl.nexbyte.siger.robustez;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties={"operacion.token=operacion-sintetica-reintentos-2026"})
@ContextConfiguration(classes={ReintentosController.class,OperacionSecurity.class,ErroresTecnicos.class,
    ReintentosHttpTest.Seguridad.class})
class ReintentosPerfilTest {
    @Autowired MockMvc mvc;
    @Test void sinPerfilJdbcNoExponeNingunaReposicion()throws Exception {
        for(var prefix:new String[]{"/operacion/v1/entregas/","/operacion/v1/formulario-completo/entregas/"}){
            mvc.perform(post(prefix+UUID.randomUUID()+"/reintentar").secure(true)
                .header("Authorization","Bearer operacion-sintetica-reintentos-2026").contentType("application/json")
                .content("{\"intentosEsperados\":10,\"motivo\":\"RECEPTOR_RECUPERADO\"}"))
                .andExpect(status().isNotFound());
        }
    }
}
