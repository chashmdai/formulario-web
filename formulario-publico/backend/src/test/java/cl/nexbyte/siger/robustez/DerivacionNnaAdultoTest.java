package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.srcei.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import static cl.nexbyte.siger.formulario.IdentificacionCiudadana.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Derivación de un recorrido NNA cuando la fuente registral acredita mayoría de edad. Dobles controlados y RUN
 * sintéticos: nunca se consulta PISEE/SRCeI real.
 */
class DerivacionNnaAdultoTest {
    @TempDir Path directory;
    private BaseJdbc db;
    private final JsonMapper mapper=JsonMapper.builder().build();
    private final String session="a".repeat(64),other="b".repeat(64);
    // 2026-10-23 09:00 en Chile (UTC-3, horario de verano).
    private final AtomicReference<Instant> now=new AtomicReference<>(Instant.parse("2026-10-23T12:00:00Z"));
    private final Clock clock=new Clock(){
        public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now.get();}
    };
    private final AtomicInteger queries=new AtomicInteger();
    private Function<RunConsulta,ResultadoSrcei> provider;
    private RecorridosJdbc service;
    @BeforeEach void setup(){db=TestDatabase.open(directory);provider=r->registro(ResultadoSrcei.Estado.COMPLETO,"2000-01-01");service=crear();}
    private RecorridosJdbc crear(){return new RecorridosJdbc(db,mapper,r->{queries.incrementAndGet();return provider.apply(r);},r->true,clock,new byte[32],"ADULTO_LAB_1","NNA_LAB_1");}
    @AfterEach void close(){
        if(db!=null){db.sql.update("DELETE FROM citizen_identity_history");db.sql.update("DELETE FROM citizen_person");db.sql.update("DELETE FROM citizen_journey");
            db.sql.update("DELETE FROM citizen_abuse_bucket");db.sql.update("DELETE FROM operational_event WHERE category='RECORRIDO'");db.close();}
    }
    private ResultadoSrcei registro(ResultadoSrcei.Estado status,String birth){return registro(status,birth,"12345678");}
    /** Como XmlSrcei: el RUN solo figura cuando la respuesta lo contrasta con el consultado. */
    private ResultadoSrcei registro(ResultadoSrcei.Estado status,String birth,String run){
        var ref=UUID.randomUUID();var attrs=new HashMap<String,ResultadoSrcei.Atributo>();
        if(run!=null){attrs.put("runNumero",new ResultadoSrcei.Atributo(run,null,"SRCEI","CONTRASTADO_EN_FUENTE",clock.instant(),ref));
            attrs.put("runDv",new ResultadoSrcei.Atributo("5",null,"SRCEI","CONTRASTADO_EN_FUENTE",clock.instant(),ref));}
        attrs.put("nombres",new ResultadoSrcei.Atributo("NOMBRE SINTETICO",null,"SRCEI","CONTRASTADO_EN_FUENTE",clock.instant(),ref));
        attrs.put("primerApellido",new ResultadoSrcei.Atributo("APELLIDO SINTETICO",null,"SRCEI","CONTRASTADO_EN_FUENTE",clock.instant(),ref));
        if(birth!=null)attrs.put("fechaNacimiento",new ResultadoSrcei.Atributo(birth,null,"SRCEI","CONTRASTADO_EN_FUENTE",clock.instant(),ref));
        return new ResultadoSrcei(status,attrs,clock.instant(),ref);
    }
    private UUID nna(){return service.iniciar(Tipo.NNA,false,"NNA_LAB_1",session,"127.0.0.1").id();}
    private Vista identificar(UUID id){return service.identificar(id,"presentador-nna",Rol.PRESENTADOR,"12345678","5",session,"127.0.0.1",null);}
    private int derivado(UUID id){return assertThrows(FalloTecnico.class,()->identificar(id)).status;}
    private String estado(UUID id){return db.sql.queryForObject("SELECT state FROM citizen_journey WHERE id=?",String.class,id.toString());}
    private int personas(UUID id){return db.sql.queryForObject("SELECT COUNT(*) FROM citizen_person WHERE journey_id=?",Integer.class,id.toString())
        +db.sql.queryForObject("SELECT COUNT(*) FROM citizen_identity_history WHERE journey_id=?",Integer.class,id.toString());}
    private void invalidado(UUID id){
        assertEquals("CANCELADO",estado(id));assertEquals(0,personas(id),"Ningún antecedente registral del adulto queda en el recorrido NNA");
        assertEquals(1,db.sql.queryForObject("SELECT COUNT(*) FROM operational_event WHERE category='RECORRIDO' AND aggregate_id=? AND code='NNA_DERIVADO_ADULTO_REGISTRAL'",Integer.class,id.toString()));
        // La derivación no crea ni prepara un recorrido adulto: solo la aceptación expresa lo hará.
        assertEquals(0,db.sql.queryForObject("SELECT COUNT(*) FROM citizen_journey WHERE kind<>'NNA'",Integer.class));
    }

    @Test void menorDe18ContinuaEnElRecorridoNna(){
        provider=r->registro(ResultadoSrcei.Estado.COMPLETO,"2008-10-24");var id=nna();
        var view=identificar(id);assertEquals("REVISADO",view.estado());assertTrue(view.runInformado());
        assertEquals("BORRADOR",estado(id));assertEquals(1,personas(id));
        assertEquals(view,service.persona(id,"presentador-nna",session));
        service.declarar(id,"presentador-nna",Map.of("segundoApellido","DECLARADO"),session);
    }
    @Test void mayorDe18DerivaSinExponerFechaNiEdadNiCrearRecorridoAdulto(){
        var id=nna();var error=assertThrows(FalloTecnico.class,()->identificar(id));
        assertEquals(RecorridosJdbc.DERIVADO_ADULTO,error.status);assertEquals(410,error.status);
        assertFalse(error.getMessage().contains("2000"));assertFalse(error.getMessage().matches(".*\\d.*"));
        invalidado(id);assertEquals(1,queries.get());
    }
    @Test void recorridoDerivadoNoPuedeReutilizarseNiEnviarse(){
        var id=nna();assertEquals(410,derivado(id));
        for(var attempt:List.<Runnable>of(()->service.consultar(id,session),()->service.persona(id,"presentador-nna",session),
                ()->identificar(id),()->service.identificar(id,"presentador-nna",Rol.PRESENTADOR,null,null,session,"127.0.0.1",null),
                ()->service.declarar(id,"presentador-nna",Map.of("primerNombre","OTRO","edad","12"),session),
                ()->service.reiniciarPersona(id,"presentador-nna",session),()->service.bloquearBorrador(id,session),()->service.cancelar(id,session)))
            assertEquals(404,assertThrows(FalloTecnico.class,attempt::run).status);
        assertEquals(1,queries.get(),"Un recorrido invalidado no vuelve a consultar el registro");invalidado(id);
    }
    @Test void cumpleanosSeEvaluaConLaFechaLocalDeChile(){
        provider=r->registro(ResultadoSrcei.Estado.COMPLETO,"2008-11-15");
        // 2026-11-15 02:59 UTC = 2026-11-14 23:59 en Chile: aún tiene 17 años aunque en UTC ya sea su cumpleaños.
        now.set(Instant.parse("2026-11-15T02:59:59Z"));var minor=nna();assertEquals("REVISADO",identificar(minor).estado());assertEquals("BORRADOR",estado(minor));
        // 00:00 en Chile del día del cumpleaños: cumple 18.
        now.set(Instant.parse("2026-11-15T03:00:00Z"));var adult=service.iniciar(Tipo.NNA,false,"NNA_LAB_1",other,"127.0.0.2").id();
        assertEquals(410,assertThrows(FalloTecnico.class,()->service.identificar(adult,"presentador-nna",Rol.PRESENTADOR,"12345678","5",other,"127.0.0.2",null)).status);
        assertEquals("CANCELADO",estado(adult));
        // El recorrido del menor no se ve afectado por la derivación de otra sesión.
        assertEquals("BORRADOR",estado(minor));
    }
    @Test void nacidoUn29DeFebreroCumple18El1DeMarzoEnAnoNoBisiesto(){
        provider=r->registro(ResultadoSrcei.Estado.COMPLETO,"2008-02-29");
        now.set(Instant.parse("2026-03-01T02:59:59Z"));var minor=nna();identificar(minor);assertEquals("BORRADOR",estado(minor));
        now.set(Instant.parse("2026-03-01T03:00:00Z"));
        assertEquals(410,assertThrows(FalloTecnico.class,()->service.persona(minor,"presentador-nna",session)).status);invalidado(minor);
    }
    @Test void cumpleanosDuranteElRecorridoDerivaAlConsultarDeclararOReidentificar(){
        provider=r->registro(ResultadoSrcei.Estado.COMPLETO,"2008-10-24");
        now.set(Instant.parse("2026-10-24T00:00:00Z"));var view=nna();identificar(view);now.set(Instant.parse("2026-10-24T03:00:00Z"));
        assertEquals(410,assertThrows(FalloTecnico.class,()->service.persona(view,"presentador-nna",session)).status);invalidado(view);
        now.set(Instant.parse("2026-10-24T00:00:00Z"));var declared=nna();identificar(declared);now.set(Instant.parse("2026-10-24T03:00:00Z"));
        assertEquals(410,assertThrows(FalloTecnico.class,()->service.declarar(declared,"presentador-nna",Map.of("segundoApellido","X"),session)).status);
        now.set(Instant.parse("2026-10-24T00:00:00Z"));var repeated=nna();identificar(repeated);now.set(Instant.parse("2026-10-24T03:00:00Z"));
        assertEquals(410,derivado(repeated),"El mismo RUN ya revisado no vuelve a consultar, pero sí revalida la edad");
        assertEquals(3,queries.get());
    }
    @Test void reanudarElMismoRunTrasReinicioDerivaSinReconsultar(){
        provider=r->registro(ResultadoSrcei.Estado.COMPLETO,"2008-10-24");
        now.set(Instant.parse("2026-10-24T00:00:00Z"));var id=nna();identificar(id);service.reiniciarPersona(id,"presentador-nna",session);now.set(Instant.parse("2026-10-24T03:00:00Z"));
        assertEquals(410,derivado(id));assertEquals(1,queries.get());invalidado(id);
    }
    @Test void fechaAusenteOTruncadaMantieneElRecorridoNna(){
        for(var result:List.of(registro(ResultadoSrcei.Estado.PARCIAL,null),registro(ResultadoSrcei.Estado.COMPLETO,null))){
            provider=r->result;var id=nna();assertEquals("REVISADO",identificar(id).estado());assertEquals("BORRADOR",estado(id));
        }
        var ref=UUID.randomUUID();
        provider=r->new ResultadoSrcei(ResultadoSrcei.Estado.PARCIAL,Map.of("fechaNacimientoTruncada",new ResultadoSrcei.Atributo("1990-00-00","TRUNCADA","SRCEI","CONTRASTADO_EN_FUENTE",clock.instant(),ref)),clock.instant(),ref);
        var truncated=nna();identificar(truncated);assertEquals("BORRADOR",estado(truncated));
    }
    @ParameterizedTest @ValueSource(strings={"2030-01-01","1800-01-01","2000-02-30","00-01-01","01/01/2000","2000-1-1"})
    void fechaNoConfiableMantieneElRecorridoNna(String birth){
        provider=r->registro(ResultadoSrcei.Estado.COMPLETO,birth);var id=nna();identificar(id);assertEquals("BORRADOR",estado(id));
    }
    @ParameterizedTest @EnumSource(value=ResultadoSrcei.Estado.class,names={"COMPLETO","PARCIAL"},mode=EnumSource.Mode.EXCLUDE)
    void consultaFallidaMantieneElRecorridoNnaAunqueTraigaUnaFechaAdulta(ResultadoSrcei.Estado status){
        provider=r->registro(status,"1990-01-01");var id=nna();assertEquals("REVISADO",identificar(id).estado());assertEquals("BORRADOR",estado(id));
    }
    @Test void proveedorCaidoONuloMantieneElRecorridoNna(){
        provider=r->{throw new IllegalStateException("caída simulada");};var down=nna();identificar(down);assertEquals("BORRADOR",estado(down));
        provider=r->null;var empty=nna();identificar(empty);assertEquals("BORRADOR",estado(empty));
    }
    @Test void edadODeclaracionesDelNavegadorNoDecidenLaDerivacion(){
        // Sin RUN no hay consulta: la edad declarada nunca deriva ni puede superar 17.
        var id=nna();service.identificar(id,"presentador-nna",Rol.PRESENTADOR,null,null,session,"127.0.0.1",null);
        service.declarar(id,"presentador-nna",Map.of("primerNombre","NOMBRE","primerApellido","APELLIDO","edad","17"),session);
        assertThrows(IllegalArgumentException.class,()->service.declarar(id,"presentador-nna",Map.of("edad","18"),session));
        assertThrows(IllegalArgumentException.class,()->service.declarar(id,"presentador-nna",Map.of("fechaNacimiento","1990-01-01"),session));
        assertEquals("BORRADOR",estado(id));assertEquals(0,queries.get());
        // Un menor según la fuente no puede forzar la derivación declarando una fecha adulta.
        provider=r->registro(ResultadoSrcei.Estado.COMPLETO,"2010-05-05");var minor=nna();identificar(minor);
        assertThrows(IllegalArgumentException.class,()->service.declarar(minor,"presentador-nna",Map.of("fechaNacimiento","1990-01-01"),session));
        assertEquals("BORRADOR",estado(minor));
        // Otra sesión no puede derivar ni conocer un recorrido ajeno.
        provider=r->registro(ResultadoSrcei.Estado.COMPLETO,"2000-01-01");var foreign=nna();
        assertEquals(404,assertThrows(FalloTecnico.class,()->service.identificar(foreign,"presentador-nna",Rol.PRESENTADOR,"12345678","5",other,"127.0.0.1",null)).status);
        assertEquals("BORRADOR",estado(foreign));
    }
    @Test void soloLaFechaSrceiCuenta(){
        var declared=new Valor("1990-01-01",null,Fuente.DECLARACION_CIUDADANA,"DECLARADO_NO_CONTRASTADO",clock.instant(),UUID.randomUUID());
        var cu=new Valor("1990-01-01",null,Fuente.CLAVE_UNICA_USERINFO,"IDENTIDAD_AUTENTICADA",clock.instant(),UUID.randomUUID());
        for(var value:List.of(declared,cu))
            assertFalse(mayorDeEdadRegistral(new Persona("presentador-nna",Rol.PRESENTADOR,"12345678","5",true,false,ResultadoSrcei.Estado.COMPLETO,clock.instant(),UUID.randomUUID(),Map.of("fechaNacimiento",value),Map.of()),clock));
        var srcei=new Valor("1990-01-01",null,Fuente.SRCEI,"CONTRASTADO_EN_FUENTE",clock.instant(),UUID.randomUUID());
        assertTrue(mayorDeEdadRegistral(new Persona("presentador-nna",Rol.PRESENTADOR,"12345678","5",true,false,ResultadoSrcei.Estado.COMPLETO,clock.instant(),UUID.randomUUID(),Map.of("fechaNacimiento",srcei),Map.of()),clock));
        // Sin RUN válido de la propia persona o sin referencia de consulta, el dato no se considera acreditado.
        assertFalse(mayorDeEdadRegistral(new Persona("presentador-nna",Rol.PRESENTADOR,null,null,false,false,ResultadoSrcei.Estado.COMPLETO,clock.instant(),UUID.randomUUID(),Map.of("fechaNacimiento",srcei),Map.of()),clock));
        assertFalse(mayorDeEdadRegistral(new Persona("presentador-nna",Rol.PRESENTADOR,"12345678","5",true,false,ResultadoSrcei.Estado.COMPLETO,clock.instant(),null,Map.of("fechaNacimiento",srcei),Map.of()),clock));
    }
    @Test void sinContrasteExplicitoDelRunLaFechaNoDecideNiSeConserva(){
        for(var run:Arrays.asList(null,"87654321")){
            provider=r->registro(ResultadoSrcei.Estado.COMPLETO,"1990-01-01",run);var id=nna();
            assertEquals("REVISADO",identificar(id).estado());assertEquals("BORRADOR",estado(id));
            var p=service.personasInternas(id,session).getFirst();assertNull(p.atributos().get("fechaNacimiento"));assertNull(p.discrepancias().get("fechaNacimiento"));
            assertEquals(Fuente.SRCEI,p.atributos().get("nombres").fuente());
            service.declarar(id,"presentador-nna",Map.of("segundoApellido","DECLARADO"),session);assertEquals("BORRADOR",estado(id));
        }
    }
    @Test void reiniciarTrasElCumpleanosNoEludeLaDerivacion(){
        provider=r->registro(ResultadoSrcei.Estado.COMPLETO,"2008-10-24");
        now.set(Instant.parse("2026-10-24T00:00:00Z"));var id=nna();identificar(id);now.set(Instant.parse("2026-10-24T03:00:00Z"));
        assertEquals(410,assertThrows(FalloTecnico.class,()->service.reiniciarPersona(id,"presentador-nna",session)).status);invalidado(id);
    }
    @Test void reiniciarAntesYContinuarSinRunNoOcultaElAntecedenteRegistral(){
        provider=r->registro(ResultadoSrcei.Estado.COMPLETO,"2008-10-24");
        now.set(Instant.parse("2026-10-24T00:00:00Z"));var id=nna();identificar(id);service.reiniciarPersona(id,"presentador-nna",session);
        service.identificar(id,"presentador-nna",Rol.PRESENTADOR,null,null,session,"127.0.0.1",null);
        service.declarar(id,"presentador-nna",Map.of("primerNombre","NOMBRE","primerApellido","APELLIDO"),session);
        now.set(Instant.parse("2026-10-24T03:00:00Z"));
        assertEquals(410,assertThrows(FalloTecnico.class,()->service.declarar(id,"presentador-nna",Map.of("edad","17"),session)).status);invalidado(id);
    }
    @Test void recorridosAdultosNoSeDerivanNiSeInvalidan(){
        var id=service.iniciar(Tipo.ADULTO_DECLARADO,true,"ADULTO_LAB_1",session,"127.0.0.1").id();
        assertEquals("REVISADO",service.identificar(id,"presentador-adulto",Rol.PRESENTADOR,"12345678","5",session,"127.0.0.1",null).estado());
        assertEquals("BORRADOR",estado(id));service.exigirVigenciaNna(id,session);assertEquals("BORRADOR",estado(id));
    }
}
