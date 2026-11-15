package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.identidad.*;
import cl.nexbyte.siger.srcei.*;
import java.nio.file.Path;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import static cl.nexbyte.siger.formulario.IdentificacionCiudadana.*;
import static org.junit.jupiter.api.Assertions.*;

/** Resumen legible del comprobante: identidades, proveedores y claves sintéticos; sin servicios gubernamentales. */
class ResumenComunicacionJdbcTest {
    @TempDir Path directory;BaseJdbc db;RecorridosJdbc journeys;RecepcionFormularioJdbc service;AdjuntosFormulario files;
    final JsonMapper mapper=JsonMapper.builder().build();final Clock clock=Clock.systemUTC();final String session="a".repeat(64),other="b".repeat(64);
    Function<RunConsulta,ResultadoSrcei> provider;KeyPair signer,institution;UUID journey;
    static final String REGISTRAL="NOMBRE REGISTRAL PRIVADO",FECHA_REGISTRAL="1980-02-03";
    @BeforeEach void setup()throws Exception {
        db=TestDatabase.open(directory);
        provider=r->{var ref=UUID.randomUUID();var at=clock.instant();return new ResultadoSrcei(ResultadoSrcei.Estado.PARCIAL,Map.of(
            "nombres",new ResultadoSrcei.Atributo(REGISTRAL,null,"SRCEI","CONTRASTADO_EN_FUENTE",at,ref),
            "fechaNacimiento",new ResultadoSrcei.Atributo(FECHA_REGISTRAL,null,"SRCEI","CONTRASTADO_EN_FUENTE",at,ref),
            "estadoCivil",new ResultadoSrcei.Atributo("CASADO REGISTRAL",null,"SRCEI","CONTRASTADO_EN_FUENTE",at,ref)),at,ref);};
        journeys=new RecorridosJdbc(db,mapper,r->provider.apply(r),r->true,clock,new byte[32],"ADULTO_LAB_1","NNA_LAB_1");
        files=new AdjuntosFormulario(db,journeys,b->true);signer=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();institution=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        service=new RecepcionFormularioJdbc(db,journeys,files,mapper,new ValidacionFormulario(new TerritorioFormulario(mapper),clock),"test-publico",signer.getPrivate(),Map.of("test-publico",signer.getPublic()),Map.of("test-institucion",institution.getPublic()),clock,true,true);
        var control=new ControlesOperacion(db);control.cambiar(control.estado().revision(),true,true,"NORMAL","PRUEBA_LOCAL");
    }
    @AfterEach void close(){if(db!=null){db.sql.update("DELETE FROM citizen_submission");db.sql.update("DELETE FROM citizen_attachment");db.sql.update("DELETE FROM citizen_identity_history");db.sql.update("DELETE FROM citizen_person");db.sql.update("DELETE FROM citizen_journey");db.sql.update("DELETE FROM citizen_abuse_bucket");db.close();}}
    void iniciar(Tipo kind){
        journey=journeys.iniciar(kind,true,"ADULTO_LAB_1",session,"127.0.0.1").id();AutenticacionCiudadana auth=null;
        if(kind==Tipo.ADULTO_CU){var identity=new IdentidadVerificada("12345678-5","NOMBRE CU","APELLIDO CU",List.of("NOMBRE","CU"),List.of("APELLIDO","CU"));auth=new AutenticacionCiudadana(identity,EvidenciaAutenticacion.de(identity));}
        journeys.identificar(journey,"presentador-adulto",Rol.PRESENTADOR,kind==Tipo.ADULTO_CU?null:"12345678",kind==Tipo.ADULTO_CU?null:"5",session,"127.0.0.1",auth);
        var v=journeys.persona(journey,"presentador-adulto",session);var declarations=new HashMap<String,String>();
        if(v.camposFaltantes().contains("primerApellido"))declarations.put("primerApellido","APELLIDO DECLARADO");
        if(!declarations.isEmpty())journeys.declarar(journey,"presentador-adulto",declarations,session);
    }
    Map<String,String> violation(String story){return new HashMap<>(Map.of("motivo","vulneracion","riesgo","no","relacion","testigo","relato",story,"adult-region","13","hechos-region","13","hechos-commune","Santiago","email","sintetico@example.test","telefono","987654321"));}
    RegistroInstitucional ack(EntregaFormulario delivery){
        var c=delivery.comunicacion();var r=new ComprobanteInstitucional.Contenido("1.0","SIGER_DDN","COMUNICACION_REGISTRADA",c.id(),UUID.randomUUID(),77,"COM-77",clock.instant(),"FORMULARIO_WEB_PUBLICO",delivery.sha256(),delivery.huellaProcedencia());
        return new RegistroInstitucional(r,ComprobanteInstitucional.firmar(r,"test-institucion",institution.getPrivate(),mapper));
    }
    /** Afectado con RUN informado y antecedentes SRCeI que no deben llegar al comprobante. */
    DatosFormulario conPersonas(String story){
        journeys.identificar(journey,"afectado-1",Rol.AFECTADO_NNA,"11111111","1",session,"127.0.0.1",null);
        journeys.declarar(journey,"afectado-1",Map.of("edad","12"),session);
        journeys.identificar(journey,"senalado-1",Rol.PERSONA_SENALADA,null,null,session,"127.0.0.1",null);
        journeys.declarar(journey,"senalado-1",Map.of("primerNombre","SEÑALADO DECLARADO"),session);
        var c=violation(story);c.put("identifica-responsables","true");
        return new DatosFormulario(c,List.of(new DatosFormulario.Relacion("afectado-1",Map.of("region","13")),new DatosFormulario.Relacion("senalado-1",Map.of("alias","Apodo sintético","rol","Vínculo relatado"))),
            List.of(new DatosFormulario.Institucion("Entidad sintética","Organismo público","Vínculo relatado","13","Santiago")),List.of());
    }

    @Test void declaradoMuestraLoEnviadoSinAntecedentesRegistralesNiRunDeTerceros(){
        iniciar(Tipo.ADULTO_DECLARADO);var story="Primera línea del relato.\n\n"+"ñ".repeat(4900);var data=conPersonas(story);
        var id=UUID.randomUUID();var state=service.recibir(id,journey,session,data);
        var summary=service.consultarResumen(id,session);var json=mapper.writeValueAsString(summary);
        assertEquals("PENDIENTE_REGISTRO_SIGER",summary.estado());assertNull(summary.registro());assertEquals(state.custodia(),summary.custodia());
        var c=summary.contenido();assertNotNull(c);assertEquals(Tipo.ADULTO_DECLARADO,c.tipo());assertEquals(story,c.campos().get("relato"));
        assertEquals("+56987654321",c.campos().get("telefono"),"Debe mostrar el contacto normalizado efectivamente custodiado");
        assertEquals(data.personas(),c.relaciones());assertEquals(data.instituciones(),c.instituciones());assertNull(c.identidadAutenticada());
        var presenter=c.personas().stream().filter(p->p.personaId().equals("presentador-adulto")).findFirst().orElseThrow();
        assertTrue(presenter.runInformado());assertFalse(presenter.autenticada());assertEquals(Map.of("primerApellido","APELLIDO DECLARADO"),presenter.valoresDeclarados());
        var affected=c.personas().stream().filter(p->p.personaId().equals("afectado-1")).findFirst().orElseThrow();assertTrue(affected.runInformado());assertEquals(Map.of("edad","12"),affected.valoresDeclarados());
        for(var secret:List.of(REGISTRAL,FECHA_REGISTRAL,"CASADO REGISTRAL","12345678","11111111","SRCEI","CONTRASTADO","DECLARADO_NO_CONTRASTADO","consultaRef","discrepancias","evidencia","owner","127.0.0.1"))
            assertFalse(json.contains(secret),"El resumen no debe exponer "+secret);
        assertTrue(json.contains("SEÑALADO DECLARADO"));assertTrue(json.contains("Apodo sintético"));
    }
    @Test void claveUnicaIncluyeSoloLaIdentidadAutenticadaDelPresentador(){
        iniciar(Tipo.ADULTO_CU);var id=UUID.randomUUID();service.recibir(id,journey,session,conPersonas("Relato sintético"));
        var c=service.consultarResumen(id,session).contenido();var json=mapper.writeValueAsString(c);
        assertEquals(new ResumenComunicacion.IdentidadAutenticada("12345678-5","NOMBRE CU","APELLIDO CU"),c.identidadAutenticada());
        assertTrue(c.personas().stream().filter(p->p.personaId().equals("presentador-adulto")).findFirst().orElseThrow().autenticada());
        assertFalse(json.contains("11111111"));assertFalse(json.contains("CLAVE_UNICA_USERINFO"));
    }
    @Test void confirmadoIncorporaElRegistroValidadoYConservaEvidenciasIntactas(){
        iniciar(Tipo.ADULTO_DECLARADO);var id=UUID.randomUUID();var pending=service.recibir(id,journey,session,conPersonas("Relato sintético"));
        assertFalse(mapper.writeValueAsString(service.consultarResumen(id,session)).contains("COM-"));
        var reserved=service.reclamar(1).getFirst();var registro=ack(reserved.entrega());service.confirmar(id,reserved.reserva(),registro);
        var summary=service.consultarResumen(id,session);var state=service.consultar(id,session);
        assertEquals("REGISTRADA_SIGER",summary.estado());assertEquals(state.registro(),summary.registro());assertEquals(registro,summary.registro());
        assertEquals(pending.custodia(),summary.custodia());assertEquals("COM-77",summary.registro().registro().folio());assertNotNull(summary.contenido());
    }
    @Test void soloLaSesionPropietariaConsultaYUnDocumentoAlteradoNoSeResume(){
        iniciar(Tipo.ADULTO_DECLARADO);var id=UUID.randomUUID();service.recibir(id,journey,session,conPersonas("Relato sintético"));
        assertEquals(404,assertThrows(FalloTecnico.class,()->service.consultarResumen(id,other)).status);
        assertEquals(404,assertThrows(FalloTecnico.class,()->service.consultarResumen(UUID.randomUUID(),session)).status);
        assertEquals(401,assertThrows(FalloTecnico.class,()->service.consultarResumen(id,"no-valida")).status);
        var body=db.sql.queryForObject("SELECT body FROM citizen_submission WHERE id=?",String.class,id.toString());
        db.sql.update("UPDATE citizen_submission SET body=? WHERE id=?",body.replace("Relato sintético","Relato alterado"),id.toString());
        assertEquals(503,assertThrows(FalloTecnico.class,()->service.consultarResumen(id,session)).status);
    }
    @Test void elResumenNoCreaCopiasNiModificaLaCustodia(){
        iniciar(Tipo.ADULTO_DECLARADO);var id=UUID.randomUUID();service.recibir(id,journey,session,conPersonas("Relato sintético"));
        var before=db.sql.queryForMap("SELECT * FROM citizen_submission WHERE id=?",id.toString());
        int events=db.sql.queryForObject("SELECT COUNT(*) FROM operational_event",Integer.class);
        service.consultarResumen(id,session);service.consultarResumen(id,session);
        assertEquals(before,db.sql.queryForMap("SELECT * FROM citizen_submission WHERE id=?",id.toString()));
        assertEquals(1,db.sql.queryForObject("SELECT COUNT(*) FROM citizen_submission",Integer.class));
        assertEquals(events,db.sql.queryForObject("SELECT COUNT(*) FROM operational_event",Integer.class));
    }
}
