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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import static cl.nexbyte.siger.formulario.IdentificacionCiudadana.*;
import static org.junit.jupiter.api.Assertions.*;

/** Solo datos/proveedores y claves institucionales sintéticos; no habilita adultos en el laboratorio compartido. */
class AdultosFormularioJdbcTest {
    @TempDir Path directory;BaseJdbc db;RecorridosJdbc journeys;RecepcionFormularioJdbc service;AdjuntosFormulario files;
    final JsonMapper mapper=JsonMapper.builder().build();final Clock clock=Clock.systemUTC();final String session="a".repeat(64);
    Function<RunConsulta,ResultadoSrcei> provider;KeyPair signer,institution;UUID journey;
    @BeforeEach void setup()throws Exception {
        db=TestDatabase.open(directory);provider=r->ResultadoSrcei.fallo(ResultadoSrcei.Estado.VACIO,clock.instant(),UUID.randomUUID());
        journeys=new RecorridosJdbc(db,mapper,r->provider.apply(r),r->true,clock,new byte[32],"ADULTO_LAB_1","NNA_LAB_1");
        files=new AdjuntosFormulario(db,journeys,b->true);signer=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();institution=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        service=crear(true);var control=new ControlesOperacion(db);control.cambiar(control.estado().revision(),true,true,"NORMAL","PRUEBA_LOCAL");
    }
    RecepcionFormularioJdbc crear(boolean adults)throws Exception{return new RecepcionFormularioJdbc(db,journeys,files,mapper,new ValidacionFormulario(new TerritorioFormulario(mapper)),"test-publico",signer.getPrivate(),Map.of("test-publico",signer.getPublic()),Map.of("test-institucion",institution.getPublic()),clock,true,adults);}
    @AfterEach void close(){if(db!=null){db.sql.update("DELETE FROM citizen_submission");db.sql.update("DELETE FROM citizen_attachment");db.sql.update("DELETE FROM citizen_identity_history");db.sql.update("DELETE FROM citizen_person");db.sql.update("DELETE FROM citizen_journey");db.sql.update("DELETE FROM citizen_abuse_bucket");db.close();}}
    void iniciar(Tipo kind){
        journey=journeys.iniciar(kind,true,"ADULTO_LAB_1",session,"127.0.0.1").id();AutenticacionCiudadana auth=null;
        if(kind==Tipo.ADULTO_CU){var identity=new IdentidadVerificada("12345678-5","NOMBRE CU","APELLIDO CU",List.of("NOMBRE","CU"),List.of("APELLIDO","CU"));auth=new AutenticacionCiudadana(identity,EvidenciaAutenticacion.de(identity));}
        journeys.identificar(journey,"presentador-adulto",Rol.PRESENTADOR,kind==Tipo.ADULTO_CU?null:"12345678",kind==Tipo.ADULTO_CU?null:"5",session,"127.0.0.1",auth);
        var v=journeys.persona(journey,"presentador-adulto",session);var declarations=new HashMap<String,String>();if(v.camposFaltantes().contains("primerNombre"))declarations.put("primerNombre","DECLARADO");if(v.camposFaltantes().contains("primerApellido"))declarations.put("primerApellido","APELLIDO");
        journeys.declarar(journey,"presentador-adulto",declarations,session);
    }
    Map<String,String> general(){return new HashMap<>(Map.of("motivo","consulta","tema","general","consulta-confirmada","true","consulta-general-detalle","Consulta sintética","adult-region","13"));}
    Map<String,String> promotion(){return new HashMap<>(Map.of("motivo","promocion","organizacion","no","actividad","Taller","publico","Adolescentes","relato","Relato sintético","adult-region","13"));}
    Map<String,String> violation(){return new HashMap<>(Map.of("motivo","vulneracion","riesgo","no","relacion","testigo","relato","Relato sintético","adult-region","13"));}
    DatosFormulario data(Map<String,String> fields,List<DatosFormulario.Relacion> people,List<DatosFormulario.Institucion> institutions){return new DatosFormulario(fields,people,institutions,List.of());}
    RecepcionFormularioJdbc.Estado send(Map<String,String> fields){return service.recibir(UUID.randomUUID(),journey,session,data(fields,List.of(),List.of()));}
    void person(String id,Rol role,Map<String,String> declarations){journeys.identificar(journey,id,role,null,null,session,"127.0.0.1",null);if(!declarations.isEmpty())journeys.declarar(journey,id,declarations,session);}
    DatosFormulario.Relacion unknown(){person("afectado-1",Rol.AFECTADO_NNA,Map.of());return new DatosFormulario.Relacion("afectado-1",Map.of("datosDesconocidos","true"));}
    @Test void consultaDeclaradaConservaTextoCompletoSinAtribuirClaveUnica(){
        iniciar(Tipo.ADULTO_DECLARADO);var c=general();c.put("consulta-general-detalle","ñ".repeat(5000));c.put("email","sintetico@example.test");c.put("telefono","+56 9 1234 5678");var received=send(c);
        var delivery=service.reclamar(1).getFirst().entrega();assertEquals(c,delivery.comunicacion().datos().campos());var p=delivery.comunicacion().personas().getFirst();
        assertFalse(p.autenticada());assertTrue(p.modulo11());assertEquals(Fuente.DECLARACION_CIUDADANA,p.atributos().get("runNumero").fuente());assertFalse(mapper.writeValueAsString(received).contains("sintetico@example.test"));assertEquals(0,db.sql.queryForObject("SELECT COUNT(*) FROM public_delivery",Integer.class));
    }
    @ParameterizedTest @ValueSource(strings={"estudiante","independiente","prensa","otro"}) void entrevistaCuRespetaPerfilesYCamposActivos(String role){
        iniciar(Tipo.ADULTO_CU);var c=general();c.remove("consulta-general-detalle");c.putAll(Map.of("tema","entrevista","entrevista-motivo","Entrevista sintética","entrevista-rol",role));if(role.equals("otro"))c.put("entrevista-rol-otro","Otro perfil");if(Set.of("prensa","estudiante").contains(role))c.put("entrevista-entidad","Entidad sintética");
        send(c);var p=service.reclamar(1).getFirst().entrega().comunicacion().personas().getFirst();assertTrue(p.autenticada());assertEquals(Fuente.CLAVE_UNICA_USERINFO,p.atributos().get("primerNombre").fuente());
    }
    @Test void estadisticasExigeOrientacionYRevisionDeFuentes(){
        iniciar(Tipo.ADULTO_DECLARADO);var c=general();c.remove("consulta-general-detalle");c.putAll(Map.of("tema","estadisticas","stats-descripcion","Antecedentes no publicados","stats-periodo","2025","stats-desglose","Región"));
        assertThrows(IllegalArgumentException.class,()->send(c));c.put("stats-confirm","true");c.remove("consulta-confirmada");assertThrows(IllegalArgumentException.class,()->send(c));c.put("consulta-confirmada","true");send(c);
    }
    @Test void empleoNoSeRecibeComoPostulacionNiPermiteInyectarCamposDeOtrasRutas(){
        iniciar(Tipo.ADULTO_DECLARADO);var c=general();c.put("tema","empleo");assertThrows(IllegalArgumentException.class,()->send(c));c.put("tema","general");
        for(var patch:List.of(Map.of("riesgo","si"),Map.of("autenticada","true"),Map.of("stats-descripcion","Campo oculto"),Map.of("entrevista-entidad","Oculta"),Map.of("nna-story","Ajeno"))){var changed=new HashMap<>(c);changed.putAll(patch);assertThrows(IllegalArgumentException.class,()->send(changed));}
        assertEquals("BORRADOR",journeys.consultar(journey,session).estado());
    }
    @Test void promocionSinOrganizacionYConOrganizacionMantieneCondiciones(){
        iniciar(Tipo.ADULTO_DECLARADO);var c=promotion();c.put("organizacion","si");assertThrows(IllegalArgumentException.class,()->send(c));c.putAll(Map.of("tipo-organizacion","ONG o fundación","nombre-organizacion","Organización sintética","fecha-actividad","2027-01-15","promocion-region","13","promocion-commune","Santiago"));send(c);
        iniciar(Tipo.ADULTO_DECLARADO);var no=promotion();no.put("nombre-organizacion","Campo inactivo");assertThrows(IllegalArgumentException.class,()->send(no));no.remove("nombre-organizacion");send(no);
    }
    @Test void ubicacionesFechasContactoYRelatoSeValidanEnServidor(){
        iniciar(Tipo.ADULTO_DECLARADO);for(var patch:List.of(Map.of("adult-region","00"),Map.of("adult-commune","Comuna inexistente"),Map.of("email","correo-invalido"),Map.of("telefono","123"),Map.of("fecha-actividad","2027-02-30"),Map.of("promocion-commune","Santiago"),Map.of("relato","x".repeat(5001)),Map.of("actividad","Actividad inventada"))){var c=promotion();c.putAll(patch);assertThrows(IllegalArgumentException.class,()->send(c));}
        for(var key:List.of("relato","publico","actividad","organizacion")){var c=promotion();c.remove(key);assertThrows(IllegalArgumentException.class,()->send(c));}
    }
    @Test void vulneracionPermiteDatosNnaDesconocidosSinRelajarMinimosDelPresentador(){
        iniciar(Tipo.ADULTO_DECLARADO);var r=unknown();var c=violation();service.recibir(UUID.randomUUID(),journey,session,data(c,List.of(r),List.of()));
        var delivery=service.reclamar(1).getFirst().entrega();assertEquals(2,delivery.comunicacion().personas().size());assertTrue(delivery.comunicacion().personas().stream().filter(p->p.rol()==Rol.AFECTADO_NNA).findFirst().orElseThrow().atributos().isEmpty());
    }
    @Test void vulneracionExigeRiesgoAntecedenteODatoDesconocidoYParentescoCondicional(){
        iniciar(Tipo.ADULTO_DECLARADO);person("afectado-1",Rol.AFECTADO_NNA,Map.of());var c=violation();var incomplete=new DatosFormulario.Relacion("afectado-1",Map.of());assertThrows(IllegalArgumentException.class,()->service.recibir(UUID.randomUUID(),journey,session,data(c,List.of(incomplete),List.of())));
        var r=new DatosFormulario.Relacion("afectado-1",Map.of("datosDesconocidos","true"));c.remove("riesgo");assertThrows(IllegalArgumentException.class,()->service.recibir(UUID.randomUUID(),journey,session,data(c,List.of(r),List.of())));c.put("riesgo","si");c.put("relacion","otro-familiar");assertThrows(IllegalArgumentException.class,()->service.recibir(UUID.randomUUID(),journey,session,data(c,List.of(r),List.of())));c.put("relacion-familiar","Abuela o abuelo");service.recibir(UUID.randomUUID(),journey,session,data(c,List.of(r),List.of()));
    }
    @Test void personasMultiplesAliasInstitucionYAdultoRelacionadoSeCustodianSinAutenticarlos(){
        iniciar(Tipo.ADULTO_CU);var affected=unknown();person("afectado-2",Rol.AFECTADO_NNA,Map.of("primerNombre","NOMBRE","edad","12"));person("relacionado-1",Rol.ADULTO_RELACIONADO,Map.of());person("senalado-1",Rol.PERSONA_SENALADA,Map.of());
        var c=violation();c.put("identifica-responsables","true");var people=List.of(affected,new DatosFormulario.Relacion("afectado-2",Map.of("region","13","comuna","Santiago")),new DatosFormulario.Relacion("relacionado-1",Map.of("relacion","Testigo")),new DatosFormulario.Relacion("senalado-1",Map.of("alias","Apodo sintético","rol","Vínculo relatado")));
        var institutions=List.of(new DatosFormulario.Institucion("Entidad sintética","Organismo público","Vínculo relatado","13","Santiago"));service.recibir(UUID.randomUUID(),journey,session,data(c,people,institutions));
        var content=service.reclamar(1).getFirst().entrega().comunicacion();assertEquals(5,content.personas().size());assertEquals(people,content.datos().personas());assertEquals(institutions,content.datos().instituciones());assertEquals(1,content.personas().stream().filter(Persona::autenticada).count());
    }
    @Test void senaladosEInstitucionesNoSeAceptanComoCamposInactivosNiFuentesAutoafirmadas(){
        iniciar(Tipo.ADULTO_DECLARADO);var affected=unknown();person("senalado-1",Rol.PERSONA_SENALADA,Map.of());var c=violation();var signaled=new DatosFormulario.Relacion("senalado-1",Map.of("alias","Apodo"));
        assertThrows(IllegalArgumentException.class,()->service.recibir(UUID.randomUUID(),journey,session,data(c,List.of(affected,signaled),List.of())));
        c.put("identifica-responsables","true");var forged=new DatosFormulario.Relacion("senalado-1",Map.of("alias","Apodo","fuente","CLAVE_UNICA_USERINFO"));assertThrows(IllegalArgumentException.class,()->service.recibir(UUID.randomUUID(),journey,session,data(c,List.of(affected,forged),List.of())));
        assertThrows(IllegalArgumentException.class,()->service.recibir(UUID.randomUUID(),journey,session,data(c,List.of(affected,new DatosFormulario.Relacion("senalado-1",Map.of())),List.of())));
        assertThrows(IllegalArgumentException.class,()->service.recibir(UUID.randomUUID(),journey,session,data(c,List.of(affected),List.of(new DatosFormulario.Institucion("","","","","")))));
    }
    @Test void personasNoSeleccionadasNoSeFiltranEnOtraConsulta(){
        iniciar(Tipo.ADULTO_DECLARADO);person("relacionado-1",Rol.ADULTO_RELACIONADO,Map.of("primerNombre","NO INCLUIR"));send(general());var content=service.reclamar(1).getFirst().entrega().comunicacion();assertEquals(1,content.personas().size());assertFalse(mapper.writeValueAsString(content).contains("NO INCLUIR"));
    }
    @Test void procedenciaMixtaMantieneRunDeclaradoYAtributosRegistralesSinAutenticar(){
        provider=run->{var ref=UUID.randomUUID();return new ResultadoSrcei(ResultadoSrcei.Estado.PARCIAL,Map.of("nombres",new ResultadoSrcei.Atributo("NOMBRE REGISTRAL PRIVADO",null,"SRCEI","CONTRASTADO_EN_FUENTE",clock.instant(),ref)),clock.instant(),ref);};iniciar(Tipo.ADULTO_DECLARADO);var state=send(general());assertFalse(mapper.writeValueAsString(state).contains("REGISTRAL"));
        var p=service.reclamar(1).getFirst().entrega().comunicacion().personas().getFirst();assertEquals(Fuente.SRCEI,p.atributos().get("nombres").fuente());assertEquals(Fuente.DECLARACION_CIUDADANA,p.atributos().get("primerApellido").fuente());assertFalse(p.autenticada());
    }
    @Test void recepcionAdultaPermaneceCerradaSinBanderaExplicita()throws Exception{
        iniciar(Tipo.ADULTO_DECLARADO);service=crear(false);assertFalse(service.adultosDisponibles());assertTrue(service.disponible());assertEquals(503,assertThrows(FalloTecnico.class,()->send(general())).status);assertEquals("BORRADOR",journeys.consultar(journey,session).estado());
    }
    @Test void adultoCuRequiereEvidenciaDeSesionYNoSeAdmiteComoDeclaradoSinRun(){
        journey=journeys.iniciar(Tipo.ADULTO_CU,true,"ADULTO_LAB_1",session,"127.0.0.1").id();assertEquals(401,assertThrows(FalloTecnico.class,()->journeys.identificar(journey,"presentador-adulto",Rol.PRESENTADOR,null,null,session,"127.0.0.1",null)).status);
        assertThrows(IllegalArgumentException.class,()->send(general()));journey=journeys.iniciar(Tipo.ADULTO_DECLARADO,true,"ADULTO_LAB_1",session,"127.0.0.1").id();assertEquals(400,assertThrows(FalloTecnico.class,()->journeys.identificar(journey,"presentador-adulto",Rol.PRESENTADOR,null,null,session,"127.0.0.1",null)).status);
    }
    @Test void ackAdultoCompruebaFirmaCanalYAmbasHuellas(){
        iniciar(Tipo.ADULTO_CU);var received=send(general());var reserved=service.reclamar(1).getFirst();var delivery=reserved.entrega();
        var record=new ComprobanteInstitucional.Contenido("1.0","SIGER_DDN","COMUNICACION_REGISTRADA",received.id(),UUID.randomUUID(),1,"COM-1",clock.instant(),"FORMULARIO_WEB_PUBLICO",delivery.sha256(),delivery.huellaProcedencia());
        var ack=new RegistroInstitucional(record,ComprobanteInstitucional.firmar(record,"test-institucion",institution.getPrivate(),mapper));service.confirmar(received.id(),reserved.reserva(),ack);assertEquals("REGISTRADA_SIGER",service.consultar(received.id(),session).estado());
    }
}
