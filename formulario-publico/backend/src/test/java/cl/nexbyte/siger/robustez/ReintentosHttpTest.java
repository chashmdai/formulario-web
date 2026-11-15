package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.formulario.RecepcionFormularioJdbc;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(properties={"operacion.token=operacion-sintetica-reintentos-2026"})
@ContextConfiguration(classes={ReintentosController.class,OperacionSecurity.class,ErroresTecnicos.class,
    ReintentosHttpTest.Seguridad.class})
@ActiveProfiles("jdbc-reception")
class ReintentosHttpTest {
    @TestConfiguration @EnableWebSecurity static class Seguridad {}
    @Autowired MockMvc mvc;
    @MockitoBean RecepcionJdbc legado;
    @MockitoBean RecepcionFormularioJdbc completo;
    private static final String TOKEN="Bearer operacion-sintetica-reintentos-2026";
    private static final String BODY="{\"intentosEsperados\":10,\"motivo\":\"RECEPTOR_RECUPERADO\"}";
    private static String ruta(UUID id){return "/operacion/v1/formulario-completo/entregas/"+id+"/reintentar";}

    @Test void reposicionExigeTokenOperacionalYHttpsAunqueExistaSesionConRol()throws Exception {
        var path=ruta(UUID.randomUUID());
        mvc.perform(post(path).secure(true).contentType("application/json").content(BODY))
            .andExpect(status().isUnauthorized());
        mvc.perform(post(path).secure(true).with(user("ciudadano").roles("OPERACION"))
            .contentType("application/json").content(BODY)).andExpect(status().isUnauthorized());
        for(var token:new String[]{"Bearer token-invalido-sintetico-2026","Bearer integracion-sintetica-reintentos-2026"}){
            mvc.perform(post(path).secure(true).header("Authorization",token).contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
        }
        mvc.perform(post(path).header("Authorization",TOKEN).contentType("application/json").content(BODY))
            .andExpect(status().isForbidden());
        verifyNoInteractions(completo,legado);
    }
    @Test void tokenOperacionalReponeLaColaCompletaSinDesviarALaLegada()throws Exception {
        var id=UUID.randomUUID();
        mvc.perform(post(ruta(id)).secure(true).header("Authorization",TOKEN).contentType("application/json").content(BODY))
            .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control","no-store"));
        verify(completo).reponer(id,10,"RECEPTOR_RECUPERADO");verifyNoInteractions(legado);
    }
    @Test void conservaRutaLegadaYErrores404Y409EnLaCompleta()throws Exception {
        var id=UUID.randomUUID();
        mvc.perform(post("/operacion/v1/entregas/"+id+"/reintentar").secure(true).header("Authorization",TOKEN)
            .contentType("application/json").content(BODY)).andExpect(status().isNoContent());
        verify(legado).reponer(id,10,"RECEPTOR_RECUPERADO");
        for(int status:new int[]{404,409}){
            doThrow(new FalloTecnico(status)).when(completo).reponer(id,10,"RECEPTOR_RECUPERADO");
            mvc.perform(post(ruta(id)).secure(true).header("Authorization",TOKEN).contentType("application/json").content(BODY))
                .andExpect(status().is(status)).andExpect(header().string("Cache-Control","no-store"));
        }
    }
}
