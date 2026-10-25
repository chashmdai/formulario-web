package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.srcei.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import static cl.nexbyte.siger.formulario.IdentificacionCiudadana.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exclusivamente identidades sintéticas, scanner controlado y firma institucional de prueba. */
class FormularioCompletoJdbcTest {
    @TempDir Path directory;
    BaseJdbc db;final JsonMapper mapper=JsonMapper.builder().build();
    final String session="a".repeat(64),other="b".repeat(64);
    final AtomicReference<Instant> now=new AtomicReference<>(Instant.parse("2026-10-23T12:00:00Z"));
    final Clock clock=new Clock(){public java.time.ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now.get();}};
    RecorridosJdbc journeys;AdjuntosFormulario files;RecepcionFormularioJdbc service;UUID journey;
    KeyPair signer,institution;boolean scanClean=true,scanAvailable=true;
    @BeforeEach void setup()throws Exception {
        db=TestDatabase.open(directory);signer=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();institution=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        journeys=new RecorridosJdbc(db,mapper,r->{throw new AssertionError("No consultar proveedor");},r->false,clock,new byte[32],"ADULTO_LAB_1","NNA_LAB_1");
        files=new AdjuntosFormulario(db,journeys,b->{if(!scanAvailable)throw new IOException("Scanner de prueba caído");return scanClean;});service=crear();
        var control=new ControlesOperacion(db);control.cambiar(control.estado().revision(),true,true,"NORMAL","PRUEBA_LOCAL");
        journey=journeys.iniciar(Tipo.NNA,false,"NNA_LAB_1",session,"127.0.0.1").id();
        journeys.identificar(journey,"presentador-nna",Rol.PRESENTADOR,null,null,session,"127.0.0.1",null);
        journeys.declarar(journey,"presentador-nna",Map.of("primerNombre","NOMBRE","primerApellido","APELLIDO","edad","12"),session);
    }
    RecepcionFormularioJdbc crear()throws Exception{return new RecepcionFormularioJdbc(db,journeys,files,mapper,new ValidacionFormulario(new TerritorioFormulario(mapper)),"publico",signer.getPrivate(),Map.of("publico",signer.getPublic()),Map.of("institucion",institution.getPublic()),clock,true);}
    @AfterEach void close(){if(db!=null){db.sql.update("DELETE FROM citizen_submission");db.sql.update("DELETE FROM citizen_attachment");db.sql.update("DELETE FROM citizen_identity_history");db.sql.update("DELETE FROM citizen_person");db.sql.update("DELETE FROM citizen_journey");db.sql.update("DELETE FROM citizen_abuse_bucket");db.close();}}
    DatosFormulario datos(){return datos(Map.of("nna-region","13","nna-primary-method","unsafe","nna-story","Relato sintético completo"),List.of());}
    DatosFormulario datos(Map<String,String> fields,List<UUID> attachments){return new DatosFormulario(fields,List.of(),List.of(),attachments);}
    AdjuntosFormulario.Archivo texto(String name,String text){return new AdjuntosFormulario.Archivo(name,text.getBytes(StandardCharsets.UTF_8));}
    RecepcionFormularioJdbc.Estado enviar(){return service.recibir(UUID.randomUUID(),journey,session,datos());}
    RegistroInstitucional ack(EntregaFormulario delivery,KeyPair pair,String kid){
        var c=delivery.comunicacion();var r=new ComprobanteInstitucional.Contenido("1.1","SIGER_DDN","COMUNICACION_REGISTRADA",c.id(),UUID.randomUUID(),1,"COM-1",now.get(),"FORMULARIO_WEB_NNA",delivery.sha256(),delivery.huellaProcedencia());
        return new RegistroInstitucional(r,ComprobanteInstitucional.firmar(r,kid,pair.getPrivate(),mapper));
    }
    @Test void custodiaConserva5000CaracteresYNoEntregaDatosPersonalesAlNavegador()throws Exception {
        var data=datos(Map.of("nna-region","13","nna-primary-method","unsafe","nna-story","x".repeat(5000)),List.of());var key=UUID.randomUUID();var state=service.recibir(key,journey,session,data);
        assertEquals("PENDIENTE_REGISTRO_SIGER",state.estado());assertNull(state.registro());assertNotNull(state.custodia());
        var json=mapper.writeValueAsString(state);assertFalse(json.contains("APELLIDO"));assertFalse(json.contains("x".repeat(100)));assertFalse(json.contains("COM-"));
        service=crear();assertEquals(state,service.consultar(key,session));assertEquals(state,service.recibir(key,journey,session,data));
        var reserved=service.reclamar(1).getFirst();assertEquals(5000,reserved.entrega().comunicacion().datos().campos().get("nna-story").length());
        assertFalse(reserved.entrega().comunicacion().aceptaCanalDigital());assertFalse(reserved.entrega().comunicacion().personas().getFirst().autenticada());
        reserved.entrega().validar(Map.of("publico",signer.getPublic()),mapper);assertEquals(0,db.sql.queryForObject("SELECT COUNT(*) FROM public_delivery",Integer.class));
    }
    @Test void claveNoSeReutilizaParaOtroContenidoSesionNiRecorrido(){
        var key=UUID.randomUUID();service.recibir(key,journey,session,datos());
        assertEquals(409,assertThrows(FalloTecnico.class,()->service.recibir(key,journey,other,datos())).status);
        var changed=new HashMap<>(datos().campos());changed.put("nna-story","Otro relato");
        assertEquals(409,assertThrows(FalloTecnico.class,()->service.recibir(key,journey,session,datos(changed,List.of()))).status);
        assertEquals(409,assertThrows(FalloTecnico.class,()->service.recibir(UUID.randomUUID(),journey,session,datos())).status);
        assertEquals(404,assertThrows(FalloTecnico.class,()->service.consultar(key,other)).status);
    }
    @Test void idempotenciaConcurrenteConfirmaUnaSolaCustodia()throws Exception {
        var key=UUID.randomUUID();var ready=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<RecepcionFormularioJdbc.Estado> work=()->{ready.await();return service.recibir(key,journey,session,datos());};
            var first=pool.submit(work);var second=pool.submit(work);ready.countDown();assertEquals(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));
        }
        assertEquals(1,db.sql.queryForObject("SELECT COUNT(*) FROM citizen_submission",Integer.class));
    }
    @Test void congelarImpideModificarIdentificacionAdjuntosYNuevoEnvio(){
        enviar();assertEquals("RECIBIDO",journeys.consultar(journey,session).estado());
        assertEquals(409,assertThrows(FalloTecnico.class,()->journeys.declarar(journey,"presentador-nna",Map.of("edad","13"),session)).status);
        assertEquals(409,assertThrows(FalloTecnico.class,()->journeys.reiniciarPersona(journey,"presentador-nna",session)).status);
        assertEquals(409,assertThrows(FalloTecnico.class,()->files.reemplazar(journey,session,List.of())).status);
    }
    @Test void validacionCondicionalRechazaCamposInactivosYProcedenciasInyectadas(){
        for(var patch:List.of(Map.of("nna-region","00"),Map.of("nna-commune","COMUNA INVENTADA"),Map.of("nna-story","x".repeat(5001)),Map.of("nna-primary-email","oculto@example.test"),Map.of("autenticada","true"),Map.of("nna-secondary-method","email"),Map.of("nna-treatment-text","oculto"))){
            var fields=new HashMap<>(datos().campos());fields.putAll(patch);assertThrows(IllegalArgumentException.class,()->service.recibir(UUID.randomUUID(),journey,session,datos(fields,List.of())));
        }
        assertEquals("BORRADOR",journeys.consultar(journey,session).estado());assertEquals(0,db.sql.queryForObject("SELECT COUNT(*) FROM citizen_submission",Integer.class));
    }
    @Test void contactosPrimarioYSecundarioRespetanCanalesYValoresDelMockup(){
        var fields=new HashMap<>(datos().campos());fields.putAll(Map.of("nna-primary-method","social","nna-primary-network","Discord","nna-primary-handle","@sintetico","nna-safe","unsure","nna-treatment","noresponde","nna-extra-contact","true","nna-secondary-method","email","nna-secondary-email","sintetico@example.test"));
        service.recibir(UUID.randomUUID(),journey,session,datos(fields,List.of()));
        assertEquals(fields,service.reclamar(1).getFirst().entrega().comunicacion().datos().campos());
    }
    @Test void canalSinDatoNoEquivaleACorreoNiSeAceptaCelularInvalido(){
        var fields=new HashMap<>(datos().campos());fields.put("nna-primary-method","celular");fields.put("nna-primary-celular","123");
        assertThrows(IllegalArgumentException.class,()->service.recibir(UUID.randomUUID(),journey,session,datos(fields,List.of())));
        fields.put("nna-primary-celular","+56 9 1234 5678");service.recibir(UUID.randomUUID(),journey,session,datos(fields,List.of()));
    }
    @Test void caidaOContingenciaNoGeneraComprobanteYPermiteRepetirRecepcionConfirmada(){
        var control=new ControlesOperacion(db);control.cambiar(control.estado().revision(),false,false,"SOLO_CONSULTA","MANTENIMIENTO");
        var key=UUID.randomUUID();assertEquals(503,assertThrows(FalloTecnico.class,()->service.recibir(key,journey,session,datos())).status);
        assertEquals("BORRADOR",journeys.consultar(journey,session).estado());control.cambiar(control.estado().revision(),true,true,"NORMAL","REANUDACION");
        var receipt=service.recibir(key,journey,session,datos());control.cambiar(control.estado().revision(),false,false,"SOLO_CONSULTA","MANTENIMIENTO");assertEquals(receipt,service.recibir(key,journey,session,datos()));
    }
    @Test void recuperacionYRegistroSoloCambianEstadoConAckInstitucionalValido()throws Exception {
        var received=enviar();var reservation=service.reclamar(1).getFirst();var signed=ack(reservation.entrega(),institution,"institucion");
        assertEquals(409,assertThrows(FalloTecnico.class,()->service.confirmar(received.id(),UUID.randomUUID(),signed)).status);
        var falseAck=ack(reservation.entrega(),signer,"publico");assertEquals(409,assertThrows(FalloTecnico.class,()->service.confirmar(received.id(),reservation.reserva(),falseAck)).status);
        service.confirmar(received.id(),reservation.reserva(),signed);service.confirmar(received.id(),reservation.reserva(),signed);
        var state=service.consultar(received.id(),session);assertEquals("REGISTRADA_SIGER",state.estado());assertEquals("COM-1",state.registro().registro().folio());assertEquals(0,service.reclamar(1).size());
        service=crear();assertEquals(state,service.consultar(received.id(),session));
    }
    @Test void ackDeOtraComunicacionYReservaVencidaNoSeConfirman(){
        var received=enviar();var r=service.reclamar(1).getFirst();var signed=ack(r.entrega(),institution,"institucion");now.set(now.get().plusSeconds(301));
        assertEquals(409,assertThrows(FalloTecnico.class,()->service.confirmar(received.id(),r.reserva(),signed)).status);
        var retry=service.reclamar(1).getFirst();assertNotEquals(r.reserva(),retry.reserva());assertEquals(2,retry.intento());
        service.confirmar(received.id(),retry.reserva(),signed);assertEquals("REGISTRADA_SIGER",service.consultar(received.id(),session).estado());
    }
    @Test void alteracionDeCuerpoProduceCuarentenaSinDevolverContenido(){
        var received=enviar();db.sql.update("UPDATE citizen_submission SET body=REPLACE(body,?,?) WHERE id=?","Relato sintético completo","Relato alterado",received.id().toString());
        assertEquals(503,assertThrows(FalloTecnico.class,()->service.consultar(received.id(),session)).status);
        assertTrue(service.reclamar(1).isEmpty());assertEquals("CUARENTENA",db.sql.queryForObject("SELECT state FROM citizen_submission WHERE id=?",String.class,received.id().toString()));
    }
    @Test void falloTransitorioRespetaEsperaYContratoInvalidoQuedaEnCuarentena(){
        var received=enviar();var r=service.reclamar(1).getFirst();service.fallo(received.id(),r.reserva(),"TRANSITORIO");service.fallo(received.id(),r.reserva(),"TRANSITORIO");
        assertTrue(service.reclamar(1).isEmpty());now.set(now.get().plusSeconds(3));var retry=service.reclamar(1).getFirst();service.fallo(received.id(),retry.reserva(),"CONTRATO_INVALIDO");assertTrue(service.reclamar(1).isEmpty());
    }
    @Test void adjuntoRealSeRecuperaConIntegridadYSoloPropietarioOReservaActiva(){
        var list=files.reemplazar(journey,session,List.of(texto("respaldo.txt","Contenido sintético")));var file=list.getFirst();
        assertEquals("VALIDADO",file.estado());assertArrayEquals("Contenido sintético".getBytes(StandardCharsets.UTF_8),files.descargar(journey,file.id(),session).contenido());
        assertEquals(404,assertThrows(FalloTecnico.class,()->files.descargar(journey,file.id(),other)).status);
        var received=service.recibir(UUID.randomUUID(),journey,session,datos(datos().campos(),List.of(file.id())));var r=service.reclamar(1).getFirst();
        assertArrayEquals(files.descargar(journey,file.id(),session).contenido(),service.adjuntoReservado(received.id(),r.reserva(),file.id()).contenido());
        assertEquals(409,assertThrows(FalloTecnico.class,()->service.adjuntoReservado(received.id(),UUID.randomUUID(),file.id())).status);
        assertEquals(404,assertThrows(FalloTecnico.class,()->service.adjuntoReservado(received.id(),r.reserva(),UUID.randomUUID())).status);
        now.set(now.get().plusSeconds(301));assertEquals(409,assertThrows(FalloTecnico.class,()->service.adjuntoReservado(received.id(),r.reserva(),file.id())).status);
    }
    @Test void archivoInvalidoOInfectadoNoReemplazaLoteAnterior(){
        var original=files.reemplazar(journey,session,List.of(texto("original.txt","Original")));
        for(var f:List.of(texto("../fuera.txt","contenido"),texto("mal.exe","contenido"),texto("falso.pdf","contenido"),new AdjuntosFormulario.Archivo("vacio.txt",new byte[0]),new AdjuntosFormulario.Archivo("grande.txt",new byte[AdjuntosFormulario.MAX_FILE+1]))){assertThrows(IllegalArgumentException.class,()->files.reemplazar(journey,session,List.of(f)));assertEquals(original,files.listar(journey,session));}
        scanClean=false;assertThrows(IllegalArgumentException.class,()->files.reemplazar(journey,session,List.of(texto("malo.txt","contenido"))));assertEquals(original,files.listar(journey,session));
        scanAvailable=false;assertEquals(503,assertThrows(FalloTecnico.class,()->files.reemplazar(journey,session,List.of(texto("nuevo.txt","contenido")))).status);assertEquals(original,files.listar(journey,session));
    }
    @Test void alteracionDeBytesYAdjuntosNoSeleccionadosBloqueanCustodia(){
        var file=files.reemplazar(journey,session,List.of(texto("respaldo.txt","Original"))).getFirst();
        assertThrows(IllegalArgumentException.class,()->service.recibir(UUID.randomUUID(),journey,session,datos()));
        db.sql.update("UPDATE citizen_attachment SET content=? WHERE id=?","Alterado".getBytes(StandardCharsets.UTF_8),file.id().toString());
        assertEquals(503,assertThrows(FalloTecnico.class,()->service.recibir(UUID.randomUUID(),journey,session,datos(datos().campos(),List.of(file.id())))).status);
        assertEquals("BORRADOR",journeys.consultar(journey,session).estado());
    }
    @Test void zipConTravesiaOMacrosNoSeAdmiteYLimitesDeLoteSonReales()throws Exception {
        var output=new ByteArrayOutputStream();try(var zip=new java.util.zip.ZipOutputStream(output)){for(var name:List.of("[Content_Types].xml","word/document.xml","../escape.txt")){zip.putNextEntry(new java.util.zip.ZipEntry(name));zip.write("contenido".getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}
        assertThrows(IllegalArgumentException.class,()->files.reemplazar(journey,session,List.of(new AdjuntosFormulario.Archivo("peligroso.docx",output.toByteArray()))));
        assertThrows(IllegalArgumentException.class,()->files.reemplazar(journey,session,Collections.nCopies(6,texto("duplicado.txt","texto"))));
        assertThrows(IllegalArgumentException.class,()->files.reemplazar(journey,session,Collections.nCopies(3,new AdjuntosFormulario.Archivo("mucho.txt",new byte[9*1024*1024]))));
    }
    @Test void nuevoDocLegacySeRechazaAunqueAntivirusNoLoMarqueYConservaLote(){
        var original=files.reemplazar(journey,session,List.of(texto("original.txt","Original")));
        byte[] ole=new byte[]{(byte)208,(byte)207,17,(byte)224,(byte)161,(byte)177,26,(byte)225,1,2,3};
        assertThrows(IllegalArgumentException.class,()->files.reemplazar(journey,session,List.of(new AdjuntosFormulario.Archivo("documento.doc",ole))));
        assertEquals(original,files.listar(journey,session));assertArrayEquals("Original".getBytes(StandardCharsets.UTF_8),files.descargar(journey,original.getFirst().id(),session).contenido());
    }
    @Test void nuevoDocxLegacyConActiveXSeRechazaAunqueAntivirusNoLoMarqueYConservaLote()throws Exception{
        var original=files.reemplazar(journey,session,List.of(texto("original.txt","Original")));var output=new ByteArrayOutputStream();
        try(var zip=new java.util.zip.ZipOutputStream(output)){for(var name:List.of("[Content_Types].xml","word/document.xml","word/activeX/activeX1.xml")){zip.putNextEntry(new java.util.zip.ZipEntry(name));zip.write("<contenido>SINTETICO</contenido>".getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}
        assertThrows(IllegalArgumentException.class,()->files.reemplazar(journey,session,List.of(new AdjuntosFormulario.Archivo("documento.docx",output.toByteArray()))));assertEquals(original,files.listar(journey,session));
    }
    @Test void originalesOfficeHistoricosContinuanRecuperablesSinReescribirBytesNiHuellas()throws Exception{
        byte[] ole=new byte[]{(byte)208,(byte)207,17,(byte)224,(byte)161,(byte)177,26,(byte)225,1,2,3};String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ole));UUID attachment=UUID.randomUUID();
        db.sql.update("INSERT INTO citizen_attachment(id,journey_id,name,size_bytes,sha256,media_type,content) VALUES(?,?,?,?,?,?,?)",attachment.toString(),journey.toString(),"historico.doc",ole.length,sha,"application/msword",ole);
        assertArrayEquals(ole,files.descargar(journey,attachment,session).contenido());assertEquals(sha,files.listar(journey,session).getFirst().sha256());
        var out=new ByteArrayOutputStream();var resource=files.recursoInterno(journey,attachment);resource.copiar(out,0,resource.tamanio());assertArrayEquals(ole,out.toByteArray());assertEquals(sha,db.sql.queryForObject("SELECT sha256 FROM citizen_attachment WHERE id=?",String.class,attachment.toString()));
    }
    @Test void identidadIncompletaNoGeneraCustodiaNiFingeAutenticacion(){
        journeys.declarar(journey,"presentador-nna",Map.of("primerNombre",""),session);
        assertThrows(IllegalArgumentException.class,this::enviar);assertEquals(0,db.sql.queryForObject("SELECT COUNT(*) FROM citizen_submission",Integer.class));
    }
    @Test void canonicalizacionNoDependeDelOrdenDeMaps(){
        var first=new LinkedHashMap<String,Object>();first.put("b",Map.of("y",2,"x",1));first.put("a",List.of("NÑ",3));
        var second=new LinkedHashMap<String,Object>();second.put("a",List.of("NÑ",3));second.put("b",Map.of("x",1,"y",2));
        assertEquals(CanonFormulario.huella("PRUEBA",first,mapper),CanonFormulario.huella("PRUEBA",second,mapper));assertNotEquals(CanonFormulario.huella("PRUEBA",first,mapper),CanonFormulario.huella("OTRO",first,mapper));
    }
    @Test void personaRelacionadaNoImponeNombresObligatoriosQueElMockupDesconoce(){
        var person=IdentificacionCiudadana.crear("relacionado-1",Rol.ADULTO_RELACIONADO,null,null,null,clock);assertTrue(vista(person).camposObligatorios().isEmpty());assertFalse(completa(person));
    }
    @Test void respuestaRegistralParcialSeConservaSoloEnEntregaPrivadaYConSuProcedencia()throws Exception {
        var evidence=UUID.randomUUID();var registry=new ResultadoSrcei(ResultadoSrcei.Estado.PARCIAL,Map.of("nombres",new ResultadoSrcei.Atributo("NOMBRE REGISTRAL PRIVADO",null,"SRCEI","CONTRASTADO_EN_FUENTE",now.get(),evidence)),now.get(),evidence);
        var journeyService=new RecorridosJdbc(db,mapper,r->registry,r->true,clock,new byte[32],"ADULTO_LAB_1","NNA_LAB_1");
        var route=journeyService.iniciar(Tipo.NNA,false,"NNA_LAB_1",session,"127.0.0.1");
        var view=journeyService.identificar(route.id(),"presentador-nna",Rol.PRESENTADOR,"12345678","5",session,"127.0.0.1",null);
        assertFalse(mapper.writeValueAsString(view).contains("PRIVADO"));assertEquals(Set.of("primerApellido"),view.camposObligatorios());
        journeyService.declarar(route.id(),"presentador-nna",Map.of("primerApellido","DECLARADO"),session);
        var received=service.recibir(UUID.randomUUID(),route.id(),session,datos());assertFalse(mapper.writeValueAsString(received).contains("PRIVADO"));
        var p=service.reclamar(1).getFirst().entrega().comunicacion().personas().getFirst();assertFalse(p.autenticada());assertEquals(Fuente.SRCEI,p.atributos().get("nombres").fuente());assertEquals(evidence,p.atributos().get("nombres").evidencia());assertEquals(Fuente.DECLARACION_CIUDADANA,p.atributos().get("primerApellido").fuente());
    }
    @Test void firmaInstitucionalValidaConHuellasAjenasNoConstituyeAck(){
        var received=enviar();var r=service.reclamar(1).getFirst();
        var wrong=new ComprobanteInstitucional.Contenido("1.1","SIGER_DDN","COMUNICACION_REGISTRADA",received.id(),UUID.randomUUID(),1,"COM-1",now.get(),"FORMULARIO_WEB_NNA","0".repeat(64),r.entrega().huellaProcedencia());
        var ack=new RegistroInstitucional(wrong,ComprobanteInstitucional.firmar(wrong,"institucion",institution.getPrivate(),mapper));
        assertEquals(409,assertThrows(FalloTecnico.class,()->service.confirmar(received.id(),r.reserva(),ack)).status);assertNull(service.consultar(received.id(),session).registro());
    }
    @Test void contactosNnaSeCustodianCanonicosSinMutarSolicitudYConReintentoExacto()throws Exception {
        var fields=new HashMap<>(datos().campos());fields.putAll(Map.of("nna-primary-method","celular","nna-primary-celular","+56 9 1234 5678","nna-extra-contact","true","nna-secondary-method","email","nna-secondary-email","  Nombre+Alias@Sub.correo.algo  "));
        var raw=datos(fields,List.of());var id=UUID.randomUUID();var received=service.recibir(id,journey,session,raw);var body=db.sql.queryForObject("SELECT body FROM citizen_submission WHERE id=?",String.class,id.toString());
        service=crear();assertEquals(received,service.recibir(id,journey,session,raw));assertEquals(body,db.sql.queryForObject("SELECT body FROM citizen_submission WHERE id=?",String.class,id.toString()));
        var delivery=service.reclamar(1).getFirst().entrega();delivery.validar(Map.of("publico",signer.getPublic()),mapper);
        assertEquals("+56912345678",delivery.comunicacion().datos().campos().get("nna-primary-celular"));assertEquals("Nombre+Alias@Sub.correo.algo",delivery.comunicacion().datos().campos().get("nna-secondary-email"));
        assertEquals("+56 9 1234 5678",raw.campos().get("nna-primary-celular"));var changed=new HashMap<>(fields);changed.put("nna-primary-celular","+56912345678");assertEquals(409,assertThrows(FalloTecnico.class,()->service.recibir(id,journey,session,datos(changed,List.of()))).status);
    }
    @Test void custodiaHistoricaYReintentoNoSeNormalizanNiSeRevalidanConReglasNuevas(){
        var fields=new HashMap<>(datos().campos());fields.putAll(Map.of("nna-primary-method","celular","nna-primary-celular","+56 9 1234 5678","nna-extra-contact","true","nna-secondary-method","email","nna-secondary-email","antes..valido@correo.algo"));
        var raw=datos(fields,List.of());var id=UUID.randomUUID();var people=journeys.personasInternas(journey,session);
        var content=new EntregaFormulario.Contenido("1.0",id,journey,Tipo.NNA,clock.instant(),"NNA_LAB_1",false,raw,people,List.of());
        var hash=CanonFormulario.huella("CONTENIDO",content,mapper);var source=EntregaFormulario.procedencia(content,mapper);var proof=ComprobanteDigital.firmar(new ComprobanteDigital.Contenido("1.0",id,content.recibidaEn(),"CUSTODIA_PUBLICA",hash,source,"FORMULARIO_PUBLICO_DDN","NNA_LAB_1"),"publico",signer.getPrivate(),mapper);
        var delivery=new EntregaFormulario(content,hash,source,proof);String body=mapper.writeValueAsString(delivery);var requestHash=CanonFormulario.huella("SOLICITUD",List.of(journey,session,raw),mapper);
        db.sql.update("INSERT INTO citizen_submission(id,journey_id,owner_session,request_hash,body,state,created_ms,next_attempt_ms) VALUES(?,?,?,?,?,'PENDIENTE',?,?)",id.toString(),journey.toString(),session,requestHash,body,clock.millis(),clock.millis());db.sql.update("UPDATE citizen_journey SET state='RECIBIDO' WHERE id=?",journey.toString());
        assertFalse(ContactosFormulario.correoValido(fields.get("nna-secondary-email")));assertEquals(proof,service.consultar(id,session).custodia());assertEquals(proof,service.recibir(id,journey,session,raw).custodia());
        var recovered=service.reclamar(1).getFirst().entrega();assertEquals(raw,recovered.comunicacion().datos());assertEquals(proof,recovered.custodia());recovered.validar(Map.of("publico",signer.getPublic()),mapper);assertEquals(body,db.sql.queryForObject("SELECT body FROM citizen_submission WHERE id=?",String.class,id.toString()));
    }

}
