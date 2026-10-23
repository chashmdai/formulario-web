package cl.nexbyte.siger.identidad;
import org.junit.jupiter.api.Test;import java.util.*;import java.time.Instant;import static org.assertj.core.api.Assertions.*;
class ProyeccionAtributosTest {
 @Test void contratoRealSoloCUyExtensionSRCeIExclusivamenteSinteticaConAutorizacion(){
  var identity=new IdentidadVerificada("44444444-4","Nombre sintético","Apellido sintético");
  var current=ProyeccionAtributos.claveUnica(identity,EvidenciaAutenticacion.de(identity));
  assertThat(current.disponibilidadFuentes().get("SRCEI")).isEqualTo("NO_HABILITADO");
  var synthetic=Map.of("nombres",List.of(new ProyeccionAtributos.Atributo("Nombre sintético contrastado","SRCEI","CONTRASTADO_EN_FUENTE",Instant.now(),UUID.randomUUID())));
  assertThatThrownBy(()->ProyeccionAtributos.incorporar(current,synthetic,Set.of())).isInstanceOf(IllegalArgumentException.class);
  var extended=ProyeccionAtributos.incorporar(current,synthetic,Set.of("nombres"));
  assertThat(extended.atributos().get("nombres")).hasSize(2);assertThat(extended.atributos().get("nombres").getFirst().fuente()).isEqualTo("CLAVE_UNICA_USERINFO");
  assertThat(current.disponibilidadFuentes().get("SRCEI")).isEqualTo("NO_HABILITADO");
 }
}
