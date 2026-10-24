package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.identidad.IdentidadVerificada;
import cl.nexbyte.siger.srcei.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;
import static cl.nexbyte.siger.formulario.IdentificacionCiudadana.*;
import static org.junit.jupiter.api.Assertions.*;

/** Dobles controlados: estas pruebas nunca usan la red ni los identificadores autorizados reales. */
class RecorridosJdbcTest {
    @TempDir Path directory;
    private BaseJdbc db;
    private final JsonMapper mapper=JsonMapper.builder().build();
    private final Clock clock=Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"),ZoneOffset.UTC);
    private final String session="a".repeat(64),other="b".repeat(64);
    private final AtomicInteger queries=new AtomicInteger();
    private Function<RunConsulta,ResultadoSrcei> provider;
    private RecorridosJdbc service;
    private boolean allowed=true;
    @BeforeEach void setup(){
        db=TestDatabase.open(directory);provider=run->resultado(ResultadoSrcei.Estado.COMPLETO);
        service=create();
    }
    private RecorridosJdbc create(){return new RecorridosJdbc(db,mapper,r->{queries.incrementAndGet();return provider.apply(r);},r->allowed,clock,new byte[32],"ADULTO_LAB_1","NNA_LAB_1");}
    @AfterEach void close(){
        if(db!=null){db.sql.update("DELETE FROM citizen_identity_history");db.sql.update("DELETE FROM citizen_person");db.sql.update("DELETE FROM citizen_journey");db.sql.update("DELETE FROM citizen_abuse_bucket");db.close();}
    }
    private RecorridosJdbc.Recorrido iniciar(Tipo kind){return service.iniciar(kind,true,kind==Tipo.NNA?"NNA_LAB_1":"ADULTO_LAB_1",session,"127.0.0.1");}
    private Vista identificar(UUID id){return service.identificar(id,"presentador-adulto",Rol.PRESENTADOR,"12345678","5",session,"127.0.0.1",null);}
    private ResultadoSrcei resultado(ResultadoSrcei.Estado status){
        var attrs=new HashMap<String,ResultadoSrcei.Atributo>();var ref=UUID.randomUUID();
        if(status==ResultadoSrcei.Estado.COMPLETO||status==ResultadoSrcei.Estado.PARCIAL){
            attrs.put("nombres",new ResultadoSrcei.Atributo("NOMBRES SINTETICOS COMPUESTOS",null,"SRCEI","CONTRASTADO_EN_FUENTE",clock.instant(),ref));
            if(status==ResultadoSrcei.Estado.COMPLETO){
                attrs.put("primerApellido",new ResultadoSrcei.Atributo("APELLIDO SINTETICO",null,"SRCEI","CONTRASTADO_EN_FUENTE",clock.instant(),ref));
                attrs.put("fechaNacimiento",new ResultadoSrcei.Atributo("2000-01-01",null,"SRCEI","CONTRASTADO_EN_FUENTE",clock.instant(),ref));
                attrs.put("sexo",new ResultadoSrcei.Atributo("Femenino","F","SRCEI","CONTRASTADO_EN_FUENTE",clock.instant(),ref));
                attrs.put("nacionalidad",new ResultadoSrcei.Atributo("Chilena","C","SRCEI","CONTRASTADO_EN_FUENTE",clock.instant(),ref));
            }
        }
        return new ResultadoSrcei(status,attrs,clock.instant(),ref);
    }
    @Test void completoNoRevelaDatosNiInfiereComponentesYPersisteSinReconsultar(){
        var journey=iniciar(Tipo.ADULTO_DECLARADO);var view=identificar(journey.id());
        assertFalse(view.autenticada());assertTrue(view.runInformado());assertTrue(view.camposObligatorios().isEmpty());
        assertFalse(view.camposFaltantes().contains("primerNombre"));assertFalse(view.camposFaltantes().contains("segundoNombre"));
        assertFalse(view.camposFaltantes().contains("primerApellido"));assertFalse(view.camposFaltantes().contains("fechaNacimiento"));
        assertTrue(view.valoresDeclarados().isEmpty());
        var json=mapper.writeValueAsString(view);assertFalse(json.contains("SINTETIC"));assertFalse(json.contains("2000-01-01"));assertFalse(json.contains("Femenino"));assertFalse(json.contains("12345678"));
        var p=service.personasInternas(journey.id(),session).getFirst();assertTrue(p.modulo11());assertFalse(p.autenticada());
        assertEquals(Fuente.SRCEI,p.atributos().get("nombres").fuente());assertFalse(p.atributos().containsKey("primerNombre"));
        service=create();assertEquals(view,identificar(journey.id()));assertEquals(1,queries.get());
    }
    @Test void parcialSoloPideFaltantesYRechazaProcedenciaFalsa(){
        provider=r->resultado(ResultadoSrcei.Estado.PARCIAL);var id=iniciar(Tipo.ADULTO_DECLARADO).id();
        var view=identificar(id);assertEquals(Set.of("primerApellido"),view.camposObligatorios());
        assertThrows(IllegalArgumentException.class,()->service.declarar(id,"presentador-adulto",Map.of("primerNombre","INVENTADO"),session));
        assertThrows(IllegalArgumentException.class,()->service.declarar(id,"presentador-adulto",Map.of("fuente","CLAVE_UNICA_USERINFO"),session));
        var declared=service.declarar(id,"presentador-adulto",Map.of("primerApellido","DECLARADO"),session);
        assertEquals("DECLARADO",declared.valoresDeclarados().get("primerApellido"));
        assertTrue(declared.camposObligatorios().contains("primerApellido"));
        var p=service.personasInternas(id,session).getFirst();assertTrue(completa(p));
        assertEquals(Fuente.DECLARACION_CIUDADANA,p.atributos().get("primerApellido").fuente());
        assertEquals(Fuente.SRCEI,p.atributos().get("nombres").fuente());
    }
    @ParameterizedTest @EnumSource(value=ResultadoSrcei.Estado.class,names={"VACIO","NEGATIVA","RED","TIMEOUT","AUTORIZACION","INDISPONIBLE","XML_INVALIDO","RESPUESTA_EXCESIVA","SATURADO","RUN_DISCREPANTE"})
    void fallosPermitenDeclaracionSinAutenticar(ResultadoSrcei.Estado status){
        provider=r->resultado(status);var id=iniciar(Tipo.ADULTO_DECLARADO).id();var view=identificar(id);
        assertEquals(Set.of("primerNombre","primerApellido"),view.camposObligatorios());assertFalse(view.autenticada());
        assertFalse(mapper.writeValueAsString(view).contains(status.name()));
        service.declarar(id,"presentador-adulto",Map.of("primerNombre","NOMBRE","primerApellido","APELLIDO"),session);
        var p=service.personasInternas(id,session).getFirst();assertEquals(status,p.estadoConsulta());assertTrue(completa(p));
        assertFalse(p.autenticada());assertNotNull(p.consultaRef());
    }
    @Test void nnaSinRunNoConsultaYConservaMinimosSinAutenticacion(){
        var id=iniciar(Tipo.NNA).id();var view=service.identificar(id,"presentador-nna",Rol.PRESENTADOR,null,null,session,"127.0.0.1",null);
        assertEquals(0,queries.get());assertFalse(view.runInformado());assertFalse(view.autenticada());
        assertEquals(Set.of("primerNombre","primerApellido"),view.camposObligatorios());
        service.declarar(id,"presentador-nna",Map.of("primerNombre","NOMBRE","primerApellido","APELLIDO","edad","12"),session);
        assertTrue(completa(service.personasInternas(id,session).getFirst()));
        assertThrows(IllegalArgumentException.class,()->service.declarar(id,"presentador-nna",Map.of("edad","18"),session));
        assertEquals(0,queries.get());
    }
    @Test void nnaRunVoluntarioSeValidaPeroPuedeCrearOtroRecorridoSinRun(){
        var id=iniciar(Tipo.NNA).id();
        assertThrows(IllegalArgumentException.class,()->service.identificar(id,"presentador-nna",Rol.PRESENTADOR,"12345678","0",session,"127.0.0.1",null));
        assertEquals(0,queries.get());
        var view=service.identificar(id,"presentador-nna",Rol.PRESENTADOR,null,null,session,"127.0.0.1",null);assertFalse(view.runInformado());
        assertFalse(view.camposFaltantes().contains("fechaNacimiento"));assertFalse(view.camposFaltantes().contains("sexo"));
    }
    @Test void sinAutorizacionDevNoLlamaAlProveedorNiRevelaLaLista(){
        allowed=false;var id=iniciar(Tipo.ADULTO_DECLARADO).id();var view=identificar(id);
        assertEquals(0,queries.get());assertEquals(Set.of("primerNombre","primerApellido"),view.camposObligatorios());
        assertEquals(ResultadoSrcei.Estado.NO_HABILITADO,service.personasInternas(id,session).getFirst().estadoConsulta());
        assertFalse(mapper.writeValueAsString(view).contains("NO_HABILITADO"));
    }
    @Test void propietarioAjenoNoLeeNiConsultaYOtraPersonaNoReutilizaConsulta(){
        var id=iniciar(Tipo.ADULTO_DECLARADO).id();identificar(id);
        assertEquals(404,assertThrows(FalloTecnico.class,()->service.persona(id,"presentador-adulto",other)).status);
        assertEquals(404,assertThrows(FalloTecnico.class,()->service.identificar(id,"presentador-adulto",Rol.PRESENTADOR,"12345678","5",other,"127.0.0.1",null)).status);
        assertEquals(409,assertThrows(FalloTecnico.class,()->service.identificar(id,"presentador-adulto",Rol.PRESENTADOR,"11111111","1",session,"127.0.0.1",null)).status);
        assertEquals(1,queries.get());
    }
    @Test void cuSoloUsaLaIdentidadDelServidorYConservaDiscrepancias(){
        var id=iniciar(Tipo.ADULTO_CU).id();var cu=new IdentidadVerificada("12345678-5","NOMBRE CU","APELLIDO CU",List.of("NOMBRE","CU"),List.of("APELLIDO","CU"));
        var proof=new cl.nexbyte.siger.identidad.EvidenciaAutenticacion(UUID.randomUUID(),clock.instant().minusSeconds(1),cl.nexbyte.siger.identidad.EvidenciaAutenticacion.de(cu).identidadHash());
        var auth=new AutenticacionCiudadana(cu,proof);
        assertEquals(401,assertThrows(FalloTecnico.class,()->service.identificar(id,"presentador-adulto",Rol.PRESENTADOR,null,null,session,"127.0.0.1",null)).status);
        assertEquals(400,assertThrows(FalloTecnico.class,()->service.identificar(id,"presentador-adulto",Rol.PRESENTADOR,"11111111","1",session,"127.0.0.1",auth)).status);
        var view=service.identificar(id,"presentador-adulto",Rol.PRESENTADOR,null,null,session,"127.0.0.1",auth);assertTrue(view.autenticada());
        var p=service.personasInternas(id,session).getFirst();assertEquals(Fuente.CLAVE_UNICA_USERINFO,p.atributos().get("nombres").fuente());
        assertEquals(Fuente.SRCEI,p.discrepancias().get("nombres").fuente());assertEquals(1,queries.get());
        assertEquals(proof.id(),p.atributos().get("nombres").evidencia());assertEquals(proof.verificadaEn(),p.atributos().get("nombres").observadoEn());
        assertFalse(mapper.writeValueAsString(view).contains("NOMBRE CU"));
    }
    @Test void codigoRegistralDesconocidoPideDeclaracionSinPerderElCodigo(){
        provider=r->{var initial=resultado(ResultadoSrcei.Estado.PARCIAL);var attrs=new HashMap<>(initial.atributos());attrs.put("sexo",new ResultadoSrcei.Atributo("Desconocido","Z","SRCEI","CONTRASTADO_EN_FUENTE",clock.instant(),initial.evidencia()));return new ResultadoSrcei(initial.estado(),attrs,initial.consultadoEn(),initial.evidencia());};
        var id=iniciar(Tipo.ADULTO_DECLARADO).id();assertTrue(identificar(id).camposFaltantes().contains("sexo"));
        service.declarar(id,"presentador-adulto",Map.of("sexo","F"),session);var p=service.personasInternas(id,session).getFirst();
        assertEquals(Fuente.DECLARACION_CIUDADANA,p.atributos().get("sexo").fuente());assertEquals("Z",p.discrepancias().get("sexo").codigo());
    }
    @Test void consultaConcurrenteNoRepiteProveedorNiSostieneBloqueoSql() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        provider=r->{entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new IllegalStateException();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException();}return resultado(ResultadoSrcei.Estado.COMPLETO);};
        var id=iniciar(Tipo.ADULTO_DECLARADO).id();
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var first=executor.submit(()->identificar(id));assertTrue(entered.await(3,TimeUnit.SECONDS));
            try {
                assertEquals(409,assertThrows(FalloTecnico.class,()->identificar(id)).status);
                var pending=service.persona(id,"presentador-adulto",session);
                assertEquals("CONSULTANDO",pending.estado());assertTrue(pending.camposFaltantes().isEmpty());
                // La misma sesión puede abrir otro recorrido mientras el proveedor responde.
                assertNotNull(iniciar(Tipo.NNA).id());
            } finally {release.countDown();}
            assertFalse(first.get(3,TimeUnit.SECONDS).autenticada());assertEquals(1,queries.get());
        }
    }
    @Test void cuotaPersistidaNoSeEludeRecreandoElServicioYCancelacionImpideAcceso(){
        for(int i=0;i<12;i++)iniciar(Tipo.NNA);
        service=create();assertEquals(429,assertThrows(FalloTecnico.class,()->iniciar(Tipo.NNA)).status);
        var id=service.iniciar(Tipo.NNA,false,"NNA_LAB_1",session,"127.0.0.2").id();service.cancelar(id,session);
        assertEquals(404,assertThrows(FalloTecnico.class,()->service.consultar(id,session)).status);
    }
    @Test void rechazaAvisoIncorrectoAusenciaDeAceptacionYFechasInvalidas(){
        assertEquals(409,assertThrows(FalloTecnico.class,()->service.iniciar(Tipo.ADULTO_DECLARADO,false,"ADULTO_LAB_1",session,"127.0.0.1")).status);
        assertEquals(409,assertThrows(FalloTecnico.class,()->service.iniciar(Tipo.ADULTO_DECLARADO,true,"ANTIGUO",session,"127.0.0.1")).status);
        provider=r->resultado(ResultadoSrcei.Estado.VACIO);var id=iniciar(Tipo.ADULTO_DECLARADO).id();identificar(id);
        assertThrows(IllegalArgumentException.class,()->service.declarar(id,"presentador-adulto",Map.of("fechaNacimiento","fecha arbitraria"),session));
        assertThrows(IllegalArgumentException.class,()->service.declarar(id,"presentador-adulto",Map.of("primerNombre","<script>"),session));
    }
    @Test void cambioDeRunConservaHistorialPeroNoReutilizaAtributosNiReiniciaCuota(){
        var id=iniciar(Tipo.ADULTO_DECLARADO).id();identificar(id);
        assertEquals(404,assertThrows(FalloTecnico.class,()->service.reiniciarPersona(id,"presentador-adulto",other)).status);
        service.reiniciarPersona(id,"presentador-adulto",session);
        assertEquals(1,db.sql.queryForObject("SELECT COUNT(*) FROM citizen_identity_history WHERE journey_id=?",Integer.class,id.toString()));
        assertEquals(404,assertThrows(FalloTecnico.class,()->service.persona(id,"presentador-adulto",session)).status);
        provider=r->resultado(ResultadoSrcei.Estado.VACIO);
        var next=service.identificar(id,"presentador-adulto",Rol.PRESENTADOR,"11111111","1",session,"127.0.0.1",null);
        assertEquals(Set.of("primerNombre","primerApellido"),next.camposObligatorios());assertEquals(2,queries.get());
        assertEquals(2,db.sql.queryForObject("SELECT query_count FROM citizen_journey WHERE id=?",Integer.class,id.toString()));
        assertFalse(service.personasInternas(id,session).getFirst().atributos().containsKey("nombres"));
    }
    @Test void persistenciaSobreviveCerrarYAbrirConexionesSinMigrarNiLimpiar(){
        var id=iniciar(Tipo.ADULTO_DECLARADO).id();var before=identificar(id);
        db.close();String supplied=System.getenv("ROBUST_DB_URL");
        String url=supplied==null?"jdbc:h2:file:"+directory.resolve("publico").toAbsolutePath().toString().replace('\\','/')+";DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0":supplied;
        db=new BaseJdbc(url,supplied==null?"sa":System.getenv("ROBUST_DB_USER"),supplied==null?"":System.getenv("ROBUST_DB_PASSWORD"),2);service=create();
        assertEquals(before,service.persona(id,"presentador-adulto",session));assertEquals(before,identificar(id));assertEquals(1,queries.get());
    }
    @Test void recorridoVencidoSeOcultaAunqueSeConserveLaSesion(){
        var id=iniciar(Tipo.NNA).id();
        var expired=new RecorridosJdbc(db,mapper,r->{throw new AssertionError("No consultar");},r->true,Clock.offset(clock,Duration.ofHours(4)),new byte[32],"ADULTO_LAB_1","NNA_LAB_1");
        assertEquals(404,assertThrows(FalloTecnico.class,()->expired.consultar(id,session)).status);
    }
    @Test void respuestaTardiaNoSustituyeFallbackTrasVencerLaReserva()throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        provider=r->{entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new IllegalStateException();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException();}return resultado(ResultadoSrcei.Estado.COMPLETO);};
        var id=iniciar(Tipo.ADULTO_DECLARADO).id();
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=executor.submit(()->identificar(id));assertTrue(entered.await(3,TimeUnit.SECONDS));
            var later=new RecorridosJdbc(db,mapper,r->{throw new AssertionError("No repetir proveedor");},r->true,Clock.offset(clock,Duration.ofSeconds(31)),new byte[32],"ADULTO_LAB_1","NNA_LAB_1");
            try {
                var fallback=later.identificar(id,"presentador-adulto",Rol.PRESENTADOR,"12345678","5",session,"127.0.0.1",null);
                assertEquals(Set.of("primerNombre","primerApellido"),fallback.camposObligatorios());
            } finally {release.countDown();}
            var failure=assertThrows(ExecutionException.class,()->first.get(3,TimeUnit.SECONDS));
            assertInstanceOf(FalloTecnico.class,failure.getCause());assertEquals(409,((FalloTecnico)failure.getCause()).status);
            assertEquals(ResultadoSrcei.Estado.TIMEOUT,service.personasInternas(id,session).getFirst().estadoConsulta());assertEquals(1,queries.get());
        }
    }
}
