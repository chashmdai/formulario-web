package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.custodia.*;
import cl.nexbyte.siger.multimedia.AlmacenPrivado;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class MinimizacionBorradoresConfigurationTest {
    @TempDir Path temp;
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
            .withInitializer(c->c.getEnvironment().setActiveProfiles("jdbc-reception"))
            .withUserConfiguration(MinimizacionBorradoresConfiguration.class)
            .withPropertyValues("formulario.habilitado=true");
    }
    private ApplicationContextRunner enabled() {
        return runner().withBean(BaseJdbc.class,()->mock(BaseJdbc.class))
            .withBean(AlmacenPrivado.class,()->mock(AlmacenPrivado.class))
            .withPropertyValues("formulario.multimedia.habilitada=true","formulario.minimizacion-borradores.habilitada=true");
    }

    @Test void porDefectoNoHayBeanProgramadorNiAlmacen() {
        runner().run(c->{
            assertNull(c.getStartupFailure());assertEquals(0,c.getBeansOfType(MinimizacionBorradores.class).size());
            assertEquals(0,c.getBeansOfType(MinimizacionBorradoresConfiguration.Programador.class).size());
            assertEquals(0,c.getBeansOfType(AlmacenPrivado.class).size());
        });
    }

    @Test void flagFalseNoCreaElMinimizadorAunqueCicloDeVidaYEliminacionEstenEncendidos() {
        runner().withPropertyValues("formulario.minimizacion-borradores.habilitada=false",
            "formulario.multimedia.habilitada=true","ciclo-vida.online-habilitado=true",
            "ciclo-vida.eliminacion.habilitada=true").run(c->{
                assertNull(c.getStartupFailure());assertEquals(0,c.getBeansOfType(MinimizacionBorradores.class).size());
                assertEquals(0,c.getBeansOfType(MinimizacionBorradoresConfiguration.Programador.class).size());
            });
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void funcionaConOSinCicloDeVidaSinDependerDeEliminacion(boolean online) {
        enabled().withPropertyValues("ciclo-vida.online-habilitado="+online,"ciclo-vida.eliminacion.habilitada=false").run(c->{
            assertNull(c.getStartupFailure());assertEquals(1,c.getBeansOfType(MinimizacionBorradores.class).size());
            assertEquals(1,c.getBeansOfType(MinimizacionBorradoresConfiguration.Programador.class).size());
            assertEquals("PT48H",c.getBean(MinimizacionBorradores.class).estado().get("plazo"));
        });
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void sinMultimediaCreaUnSoloAlmacenCompartido(boolean online) {
        runner().withBean(BaseJdbc.class,()->mock(BaseJdbc.class)).withPropertyValues(
            "formulario.multimedia.habilitada=false","formulario.minimizacion-borradores.habilitada=true",
            "formulario.multimedia.directorio="+temp.resolve("store"),"ciclo-vida.online-habilitado="+online).run(c->{
                assertNull(c.getStartupFailure());assertEquals(1,c.getBeansOfType(AlmacenPrivado.class).size());
                assertEquals(1,c.getBeansOfType(MinimizacionBorradores.class).size());
            });
    }

    @Test void ambosMantenimientosArrancanConElMismoAlmacenSinMultimedia()throws Exception {
        var mapper=JsonMapper.builder().build();
        var key=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var institutional=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String publicPem="-----BEGIN PUBLIC KEY-----\n"+Base64.getEncoder().encodeToString(key.getPublic().getEncoded())+
            "\n-----END PUBLIC KEY-----\n";
        String institutionalPem="-----BEGIN PUBLIC KEY-----\n"+Base64.getEncoder().encodeToString(institutional.getPublic().getEncoded())+
            "\n-----END PUBLIC KEY-----\n";
        Path trust=temp.resolve("trust.json"),institutionalTrust=temp.resolve("institutional.json");
        Path signer=temp.resolve("signer.pem"),owner=temp.resolve("owner.key");
        Files.writeString(trust,mapper.writeValueAsString(Map.of("sintetico",publicPem)));
        Files.writeString(institutionalTrust,mapper.writeValueAsString(Map.of("institucion",institutionalPem)));
        Files.writeString(signer,"-----BEGIN PRIVATE KEY-----\n"+Base64.getEncoder().encodeToString(key.getPrivate().getEncoded())+
            "\n-----END PRIVATE KEY-----\n");
        Files.write(owner,new byte[32]);
        runner().withUserConfiguration(CustodiaTransitoriaConfiguration.class)
            .withBean(BaseJdbc.class,()->mock(BaseJdbc.class)).withBean(JsonMapper.class,()->mapper)
            .withPropertyValues("formulario.multimedia.habilitada=false","formulario.minimizacion-borradores.habilitada=true",
                "formulario.multimedia.directorio="+temp.resolve("store"),"ciclo-vida.online-habilitado=true",
                "ciclo-vida.eliminacion.habilitada=false","ciclo-vida.modalidad=ACK_RETENCION_LIMITADA_APROBADA",
                "ciclo-vida.ack-riesgo-aceptado=true","ciclo-vida.aprobacion-ref=PRUEBA_SINTETICA",
                "publico.comprobante.trusted-keys="+trust,"publico.comprobante.institutional-trusted-keys="+institutionalTrust,
                "publico.comprobante.private-key="+signer,"publico.comprobante.key-id=sintetico",
                "formulario.abuso-key-path="+owner).run(c->{
                    assertNull(c.getStartupFailure());assertEquals(1,c.getBeansOfType(AlmacenPrivado.class).size());
                    assertEquals(1,c.getBeansOfType(CustodiaTransitoria.class).size());
                    assertEquals(1,c.getBeansOfType(MinimizacionBorradores.class).size());
                });
    }

    @ParameterizedTest @ValueSource(strings={"PT0S","-PT1H","PT59M","P91D","48 horas","9999999999999999999999"})
    void plazoInvalidoFallaElArranqueConNombreDePropiedad(String value) {
        invalid("plazo",value);
    }

    @ParameterizedTest @ValueSource(strings={"PT0S","-PT1M","PT59S","P2D","cinco minutos"})
    void intervaloInvalidoFallaElArranqueConNombreDePropiedad(String value) {
        invalid("intervalo",value);
    }
    private void invalid(String property,String value) {
        enabled().withPropertyValues("formulario.minimizacion-borradores."+property+"="+value).run(c->{
            assertNotNull(c.getStartupFailure());
            // El mensaje del bean conserva el nombre incluso cuando la causa es DateTimeParseException.
            String chain="";for(Throwable error=c.getStartupFailure();error!=null;error=error.getCause())chain+=error.getMessage()+"\n";
            assertTrue(chain.contains("formulario.minimizacion-borradores."+property),chain);
        });
    }
}
