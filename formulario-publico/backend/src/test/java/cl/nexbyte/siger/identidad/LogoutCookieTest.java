package cl.nexbyte.siger.identidad;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"siger.claveunica.client-id=test","siger.claveunica.client-secret=test","server.servlet.session.cookie.name=SIGERNNA2026"})
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class LogoutCookieTest {
 @Autowired MockMvc mvc;
 @Test void cierreDeSesionEliminaLaCookieConfiguradaYExigeCsrf()throws Exception {
  mvc.perform(post("/auth/logout").secure(true)).andExpect(status().isForbidden());
  mvc.perform(post("/auth/logout").secure(true).with(csrf())).andExpect(status().is3xxRedirection()).andExpect(cookie().maxAge("SIGERNNA2026",0));
 }
}
