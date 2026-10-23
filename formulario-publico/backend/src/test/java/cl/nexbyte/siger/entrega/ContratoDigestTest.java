package cl.nexbyte.siger.entrega;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
class ContratoDigestTest {
    @Test void consumeFixtureVersionadoYVerificaDigestIndependiente() throws Exception {
        try (var input = getClass().getResourceAsStream("/comunicacion-v1.json")) {
            var value = JsonMapper.builder().build().readValue(input, SobreComunicacion.class);
            assertThat(value.sha256()).isEqualTo("f3726415c9fd5a12875c2e2a7772451e8c1ad1f95bbcbecef50c4e5209fa1671");
            assertThat(value.integra()).isTrue();
        }
    }
}
