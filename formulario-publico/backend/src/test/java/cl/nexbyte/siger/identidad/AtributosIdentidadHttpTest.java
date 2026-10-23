package cl.nexbyte.siger.identidad;
import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;import org.springframework.security.core.authority.SimpleGrantedAuthority;import java.util.List;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"siger.claveunica.client-id=test","siger.claveunica.client-secret=test"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class AtributosIdentidadHttpTest {
 @Autowired MockMvc mvc;
 @Test void sesionVistaPreviaYProyeccionPreservanLosMismosComponentes()throws Exception{
  var identity=IdentidadVerificadaTest.identidad(List.of("  María José  ","De los ángeles","Tercero","Cuarto"),List.of("Del Río","González de la Fuente"));
  var auth=UsernamePasswordAuthenticationToken.authenticated(identity,null,List.of(new SimpleGrantedAuthority("ROLE_CIUDADANO")));auth.setDetails(EvidenciaAutenticacion.de(identity));
  mvc.perform(get("/api/session").with(authentication(auth))).andExpect(status().isOk())
   .andExpect(jsonPath("$.identity.runNumero").value("44444444")).andExpect(jsonPath("$.identity.runDv").value("4"))
   .andExpect(jsonPath("$.identity.nombresComponentes[0]").value("  María José  "))
   .andExpect(jsonPath("$.identity.nombresComponentes[3]").value("Cuarto"))
   .andExpect(jsonPath("$.identity.apellidosComponentes[0]").value("Del Río"));
  mvc.perform(get("/api/identidad/atributos").with(authentication(auth))).andExpect(status().isOk())
   .andExpect(jsonPath("$.atributos.nombres[0].componentes[0]").value("  María José  "))
   .andExpect(jsonPath("$.atributos.nombres[0].fuente").value("CLAVE_UNICA_USERINFO"))
   .andExpect(jsonPath("$.atributos.runNumero[0].valor").value("44444444"))
   .andExpect(jsonPath("$.atributos.runDv[0].fuente").value("CLAVE_UNICA_USERINFO"))
   .andExpect(jsonPath("$.disponibilidadFuentes.SRCEI").value("NO_HABILITADO"));
  mvc.perform(post("/api/comunicaciones/preview").with(authentication(auth)).with(csrf()).contentType("application/json").content("{\"mensaje\":\"Prueba sintética\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.identidad.nombresComponentes[1]").value("De los ángeles"));
 }
 @Test void noExponeSRCeISinteticoYExigeSesionVerificada()throws Exception{
  mvc.perform(get("/api/identidad/atributos")).andExpect(status().isUnauthorized());
  var identity=new IdentidadVerificada("44444444-4","Nombre sintético","Apellido sintético");
  var auth=UsernamePasswordAuthenticationToken.authenticated(identity,null,List.of(new SimpleGrantedAuthority("ROLE_CIUDADANO")));auth.setDetails(EvidenciaAutenticacion.de(identity));
  mvc.perform(get("/api/identidad/atributos").with(authentication(auth))).andExpect(status().isOk())
   .andExpect(jsonPath("$.disponibilidadFuentes.SRCEI").value("NO_HABILITADO"))
   .andExpect(jsonPath("$.atributos.run[0].valor").value("44444444-4"))
   .andExpect(jsonPath("$.atributos.run[0].fuente").value("CLAVE_UNICA_USERINFO"));
 }
}
