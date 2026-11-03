package cl.nexbyte.siger.entrega;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** API preparada sin habilitar recepción ni simular confianza institucional. */
@SpringBootTest(properties={"siger.claveunica.habilitada=false", "siger.claveunica.callback-approved=false",
    "publico.integracion.habilitada=false", "publico.integracion.preparada=true",
    "publico.integracion.token=synthetic-productor-preparado-token-2026"})
@AutoConfigureMockMvc
class ProductorPreparadoTest {
    @Autowired MockMvc mvc;
    @Test void sinBearerOFalsoRechazadosAunConApiPreparada()throws Exception{
        mvc.perform(get("/integracion/recurso-inexistente").secure(true)).andExpect(status().isUnauthorized());
        mvc.perform(get("/integracion/recurso-inexistente").secure(true).header("Authorization","Bearer INVALID"))
            .andExpect(status().isUnauthorized());
    }
    @Test void bearerValidoNoCreaRecursosNiExigeClaveAckFicticia()throws Exception{
        mvc.perform(get("/integracion/recurso-inexistente").secure(true)
            .header("Authorization","Bearer synthetic-productor-preparado-token-2026"))
            .andExpect(status().isNotFound());
    }
    @Test void preparacionNoPermiteTransporteSinTls()throws Exception{
        mvc.perform(get("/integracion/recurso-inexistente")
            .header("Authorization","Bearer synthetic-productor-preparado-token-2026"))
            .andExpect(status().isForbidden());
    }
}
