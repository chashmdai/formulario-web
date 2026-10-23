package cl.nexbyte.siger.nna;
import cl.nexbyte.siger.contrato.DeclaracionNna;
import cl.nexbyte.siger.identidad.ClaveUnicaProperties;
import java.net.URI;import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
class NnaControlesTest {
    MockEnvironment env(){var e=new MockEnvironment().withProperty("publico.nna.habilitado","true").withProperty("server.address","127.0.0.1");e.setActiveProfiles("nna-lab","jdbc-reception");return e;}
    ClaveUnicaProperties cu(){var c=new ClaveUnicaProperties();c.setPublicOrigin(URI.create("https://localhost"));return c;}
    @Test void cierrePorDefectoYProduccionNoPuedeActivarPrototipo(){
        assertThat(new HabilitacionNna(new MockEnvironment(),cu()).habilitado()).isFalse();
        assertThat(new HabilitacionNna(env(),cu()).habilitado()).isTrue();
        var prod=cu();prod.setEnvironment(ClaveUnicaProperties.Stage.PRODUCTION);
        assertThatThrownBy(()->new HabilitacionNna(env(),prod)).isInstanceOf(IllegalStateException.class);
        var wrong=env();wrong.setActiveProfiles("prod","nna-lab","jdbc-reception");
        assertThatThrownBy(()->new HabilitacionNna(wrong,cu())).isInstanceOf(IllegalStateException.class);
        for(var change:new String[][]{{"server.address","0.0.0.0"},{"server.servlet.session.cookie.secure","false"}})
            assertThatThrownBy(()->new HabilitacionNna(env().withProperty(change[0],change[1]),cu())).isInstanceOf(IllegalStateException.class);
        var missing=env();missing.setActiveProfiles("jdbc-reception");assertThatThrownBy(()->new HabilitacionNna(missing,cu())).isInstanceOf(IllegalStateException.class);
    }
    @Test void moduloOnceOpcionalYDeclaracionNoNormalizaNiCompleta(){
        var data=new DeclaracionNna("María José",null,"Del Río",null,null,null," Calle  sintética ",null,"Región sintética");
        assertThat(data.runNumero()).isNull();assertThat(data.direccion()).isEqualTo(" Calle  sintética ");
        assertThatCode(()->new DeclaracionNna("A",null,"B",null,"44444444","4",null,null,"R")).doesNotThrowAnyException();
        assertThat(new DeclaracionNna("A",null,"B",null,"6","k",null,null,"R").runDv()).isEqualTo("k");
        for(String dv:new String[]{"5","",null})assertThatThrownBy(()->new DeclaracionNna("A",null,"B",null,"44444444",dv,null,null,"R")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new DeclaracionNna("A",null,"B",null,null,null,null,null," ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new DeclaracionNna("A\u0000",null,"B",null,null,null,null,null,"R")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void cuotaResisteRotacionDeSesionesYSeRecuperaPorVentana(){
        var limiter=new LimiteAbusoNna(Clock.fixed(Instant.EPOCH,ZoneOffset.UTC));
        for(int i=0;i<6;i++)assertThat(limiter.admitir("ip","sesion")).isTrue();
        assertThat(limiter.admitir("ip","sesion")).isFalse();
        for(int i=0;i<24;i++)assertThat(limiter.admitir("ip","rotada"+i)).isTrue();
        assertThat(limiter.admitir("ip","otra")).isFalse();
    }
}
