package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.identidad.*;
import cl.nexbyte.siger.srcei.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import static cl.nexbyte.siger.formulario.IdentificacionCiudadana.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Solo dobles y credenciales ficticias; no conecta CU ni PISEE reales. */
class ConfiguracionAmbientesIntegracionTest {
    @TempDir Path directory;
    BaseJdbc db;
    final JsonMapper mapper=JsonMapper.builder().build();
    final String session="a".repeat(64);
    @BeforeEach void setup(){db=TestDatabase.open(directory);}
    @AfterEach void close(){
        if(db!=null){
            db.sql.update("DELETE FROM citizen_identity_history");db.sql.update("DELETE FROM citizen_person");
            db.sql.update("DELETE FROM citizen_journey");db.sql.update("DELETE FROM citizen_abuse_bucket");db.close();
        }
    }
    MockEnvironment env(String cu,String node)throws Exception{
        var key=directory.resolve("abuso.key");Files.write(key,new byte[32]);
        var authorization=directory.resolve("pisee-fixture");Files.writeString(authorization,"CREDENCIAL_SINTETICA_NO_REAL");
        var allowed=directory.resolve("allowed.json");Files.writeString(allowed,"[]");
        var e=new MockEnvironment().withProperty("siger.claveunica.environment",cu)
            .withProperty("PISEE_SRCEI_ENABLED","true").withProperty("PISEE_ENVIRONMENT",node)
            .withProperty("PISEE_DEV_AUTHORIZED","true").withProperty("PISEE_LOCAL_AUTHORIZATION_PATH",authorization.toString())
            .withProperty("PISEE_BASE_URL","http://127.0.0.1:1").withProperty("PISEE_ALLOWED_RUNS_PATH",allowed.toString())
            .withProperty("formulario.abuso-key-path",key.toString())
            .withProperty("formulario.aviso-adulto","ADULTO_TEST").withProperty("formulario.aviso-nna","NNA_TEST");
        e.setActiveProfiles("jdbc-reception","production");return e;
    }
    RecorridosJdbc journeys(MockEnvironment e,ClientePisee client){
        return ReflectionTestUtils.invokeMethod(new FormularioConfiguration(),"recorridosJdbc",db,mapper,client,e);
    }
    ClientePisee fake(){
        var c=mock(ClientePisee.class);
        when(c.consultar(any())).thenAnswer(i->ResultadoSrcei.fallo(ResultadoSrcei.Estado.VACIO,Instant.now(),UUID.randomUUID()));
        return c;
    }
    Vista identify(RecorridosJdbc service,Tipo type,String person,Rol role){
        boolean nna=type==Tipo.NNA;
        var j=service.iniciar(type,!nna,nna?"NNA_TEST":"ADULTO_TEST",session,"127.0.0.1");
        var identity=type==Tipo.ADULTO_CU&&role==Rol.PRESENTADOR
            ?new IdentidadVerificada("12345678-5","NOMBRE","APELLIDO",List.of("NOMBRE"),List.of("APELLIDO")):null;
        var auth=identity==null?null:new AutenticacionCiudadana(identity,EvidenciaAutenticacion.de(identity));
        return service.identificar(j.id(),person,role,identity==null?"12345678":null,identity==null?"5":null,session,"127.0.0.1",auth);
    }
    @ParameterizedTest @ValueSource(strings={"SANDBOX","QA","PRODUCTION"})
    void ambientesCuNoBloqueanFormularioYDevConservaAllowlist(String cu)throws Exception{
        var e=env(cu,"DEV");var c=fake();var v=identify(journeys(e,c),Tipo.ADULTO_DECLARADO,"presentador-adulto",Rol.PRESENTADOR);
        assertEquals("REVISADO",v.estado());assertFalse(v.autenticada());verify(c,never()).consultar(any());
    }
    @Test void clienteCuProductivoConservaConsultaDevSoloAutorizada()throws Exception{
        var e=env("PRODUCTION","DEV");Files.writeString(Path.of(e.getProperty("PISEE_ALLOWED_RUNS_PATH")),"[{\"numero\":\"12345678\",\"dv\":\"5\"}]");
        var c=fake();assertTrue(identify(journeys(e,c),Tipo.ADULTO_CU,"presentador-adulto",Rol.PRESENTADOR).autenticada());
        verify(c,times(1)).consultar(any());
    }
    @ParameterizedTest @EnumSource(Tipo.class)
    void nodoProductivoAutorizaSoloPresentadorSinDependerDeAllowlistDev(Tipo type)throws Exception{
        var e=env("PRODUCTION","PRODUCTION");e.setProperty("PISEE_ALLOWED_RUNS_PATH",directory.resolve("no-debe-leerse").toString());
        var c=fake();var v=identify(journeys(e,c),type,type==Tipo.NNA?"presentador-nna":"presentador-adulto",Rol.PRESENTADOR);
        assertEquals(type==Tipo.ADULTO_CU,v.autenticada());verify(c,times(1)).consultar(any());
    }
    @ParameterizedTest @EnumSource(value=Rol.class,names={"AFECTADO_NNA","ADULTO_RELACIONADO","PERSONA_SENALADA"})
    void nodoProductivoNoHabilitaBusquedasDeTerceros(Rol role)throws Exception{
        var c=fake();String id=switch(role){case AFECTADO_NNA->"afectado-1";case ADULTO_RELACIONADO->"relacionado-1";default->"senalado-1";};
        var v=identify(journeys(env("PRODUCTION","PRODUCTION"),c),Tipo.ADULTO_DECLARADO,id,role);
        assertFalse(v.autenticada());verify(c,never()).consultar(any());
    }
    @ParameterizedTest @ValueSource(strings={"DEV","PRODUCTION"})
    void configuracionNodoAdmiteAmbosAmbientesSinSolicitarRed(String node)throws Exception{
        var e=env("PRODUCTION",node);if(node.equals("PRODUCTION"))e.setProperty("PISEE_DEV_AUTHORIZED","false");
        ClientePisee client=ReflectionTestUtils.invokeMethod(new PiseeConfiguration(),"clientePisee",e);
        assertNotNull(client);client.close();
    }
    @Test void devSinAutorizacionYAmbienteDesconocidoSeRechazan()throws Exception{
        var e=env("PRODUCTION","DEV");e.setProperty("PISEE_DEV_AUTHORIZED","false");
        assertThrows(IllegalStateException.class,()->ReflectionTestUtils.invokeMethod(new PiseeConfiguration(),"clientePisee",e));
        e.setProperty("PISEE_ENVIRONMENT","OTRO");
        assertThrows(IllegalArgumentException.class,()->ReflectionTestUtils.invokeMethod(new PiseeConfiguration(),"clientePisee",e));
    }
    @Test void nodoDeshabilitadoNoConsultaAunqueSeDeclareAmbienteProductivo()throws Exception{
        var e=env("PRODUCTION","PRODUCTION");e.setProperty("PISEE_SRCEI_ENABLED","false");
        ClientePisee client=ReflectionTestUtils.invokeMethod(new PiseeConfiguration(),"clientePisee",e);
        assertEquals(ResultadoSrcei.Estado.NO_HABILITADO,client.consultar(new RunConsulta("12345678","5")).estado());client.close();
    }
}
