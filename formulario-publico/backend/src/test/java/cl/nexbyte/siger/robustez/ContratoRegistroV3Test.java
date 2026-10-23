package cl.nexbyte.siger.robustez;
import org.junit.jupiter.api.Test;import java.util.*;import java.security.PublicKey;import tools.jackson.databind.json.JsonMapper;import static org.assertj.core.api.Assertions.*;
class ContratoRegistroV3Test {
 @Test void documentoInstitucionalCompartidoEsVerificableSinPrivada()throws Exception{
  var mapper=JsonMapper.builder().build();var value=mapper.readValue(getClass().getResourceAsStream("/contratos/registro-institucional-v3.json"),RegistroInstitucional.class);
  var trust=new HashMap<String,PublicKey>();var tree=mapper.readTree(getClass().getResourceAsStream("/contratos/institution-public-test-v3.json"));
  tree.properties().forEach(e->trust.put(e.getKey(),ComprobanteDigital.publicPem(e.getValue().asString())));
  assertThat(value.comprobante().verificar(trust,mapper)).isEqualTo(value.registro());
  assertThat(value.registro().hecho()).isEqualTo("COMUNICACION_REGISTRADA");
  assertThatThrownBy(()->value.comprobante().verificar(Map.of(),mapper)).isInstanceOf(IllegalArgumentException.class);
 }
}
