package cl.nexbyte.siger.robustez;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class ArranqueSinConfianzaTest {
    @Test void disabledIntegrationHasNoTrustedAckKeys() throws Exception {
        var env=new MockEnvironment().withProperty("publico.integracion.habilitada","false");
        assertThat(JdbcRecepcionConfiguration.institutionalKeys(new JsonMapper(),env)).isEmpty();
    }
    @Test void enabledIntegrationStillRequiresAuthenticTrust() {
        assertThatThrownBy(()->JdbcRecepcionConfiguration.institutionalKeys(new JsonMapper(),new MockEnvironment()))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test void cannotCombineMissingTrustWithReceptionOrMaintenance() {
        for(String flag: new String[]{"publico.recepcion.habilitada","publico.recepcion.aviso-validado","publico.nna.habilitado",
                "formulario.adultos.habilitado","formulario.recepcion.habilitada","ciclo-vida.online-habilitado","ciclo-vida.eliminacion.habilitada"}) {
            var env=new MockEnvironment().withProperty("publico.integracion.habilitada","false").withProperty(flag,"true");
            assertThatThrownBy(()->JdbcRecepcionConfiguration.institutionalKeys(new JsonMapper(),env)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void operationalTokenCannotOpenReceptionOrPullWithoutTrust() {
        var controls=new ControlesOperacion(null,false);
        assertThatThrownBy(()->controls.cambiar(0,true,false,"NORMAL","PRUEBA_LOCAL")).isInstanceOf(FalloTecnico.class);
        assertThatThrownBy(()->controls.cambiar(0,false,true,"NORMAL","PRUEBA_LOCAL")).isInstanceOf(FalloTecnico.class);
    }
}
