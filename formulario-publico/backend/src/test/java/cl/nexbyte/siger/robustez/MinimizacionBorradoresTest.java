package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.custodia.MinimizacionBorradores;
import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.multimedia.AlmacenPrivado;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Sólo H2/almacén sintético: no consulta proveedores ni activa retirada de custodia. */
class MinimizacionBorradoresTest {
    @TempDir Path temp;
    private BaseJdbc db;
    private AlmacenPrivado store;
    private MinimizacionBorradores service;
    private final AtomicLong now=new AtomicLong(Instant.parse("2026-11-15T12:00:00Z").toEpochMilli());
    private final Clock clock=new Clock() {
        public ZoneId getZone() {return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone) {return this;}
        public Instant instant() {return Instant.ofEpochMilli(now.get());}
    };
    private final String session="a".repeat(64);
    private static final Duration PLAZO=Duration.ofHours(48);
    private static final String BODY="{\"nombres\":\"IDENTIDAD SINTETICA\",\"fuente\":\"SRCEI\"}";

    @BeforeEach void setup()throws Exception {
        db=TestDatabase.open(temp);store=new AlmacenPrivado(temp.resolve("store"));
        service=new MinimizacionBorradores(db,store,clock,PLAZO);
    }

    @AfterEach void close()throws Exception {
        if(store!=null)store.close();
        if(db!=null) {
            for(String table:List.of("citizen_submission","citizen_attachment","media_upload",
                    "citizen_identity_history","citizen_person","citizen_journey"))db.sql.update("DELETE FROM "+table);
            db.close();
        }
    }

    private UUID recorrido(String state,long expiry) {
        UUID id=UUID.randomUUID();
        db.sql.update("""
            INSERT INTO citizen_journey(id,owner_session,kind,notice_version,accepted_channel,state,created_ms,expires_ms)
            VALUES(?,?,'ADULTO_DECLARADO','SINTETICO',TRUE,?,?,?)
            """,id.toString(),session,state,expiry-14400000,expiry);
        db.sql.update("INSERT INTO citizen_person(journey_id,person_id,role,body,query_state) VALUES(?,'presentador-adulto','PRESENTADOR',?,'REVISADO')",
            id.toString(),BODY);
        db.sql.update("INSERT INTO citizen_identity_history(id,journey_id,person_id,body,query_state,retired_ms) VALUES(?,?,'presentador-adulto',?,'REVISADO',?)",
            UUID.randomUUID().toString(),id.toString(),BODY,expiry-1000);
        return id;
    }

    private UUID vencido(String state) {return recorrido(state,now.get()-PLAZO.toMillis());}
    private int filas(String table,UUID id) {
        return db.sql.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE journey_id=?",Integer.class,id.toString());
    }
    private String estado(UUID id) {
        return db.sql.queryForObject("SELECT state FROM citizen_journey WHERE id=?",String.class,id.toString());
    }
    private void identidad(UUID id,int expected) {
        assertEquals(expected,filas("citizen_person",id));assertEquals(expected,filas("citizen_identity_history",id));
    }
    private RecorridosJdbc recorridos(boolean enabled) {
        var recorridos=new RecorridosJdbc(db,JsonMapper.builder().build(),r->{throw new AssertionError("Sin proveedor");},
            r->false,clock,new byte[32],"SINTETICO","NNA_SINTETICO");
        if(enabled)recorridos.minimizacionBorradores(service::alCancelar);
        return recorridos;
    }
    private UUID carga(UUID journey,boolean released) {
        UUID id=UUID.randomUUID();
        db.sql.update("""
            INSERT INTO media_upload(id,journey_id,name,expected_size,expected_sha256,state,created_ms,
                touched_ms,expires_ms,reserved_bytes,released)
            VALUES(?,?,'archivo sintetico',1,?,'EXPIRADO',?,?,?,0,?)
            """,id.toString(),journey.toString(),"0".repeat(64),now.get(),now.get(),now.get(),released);
        return id;
    }
    private UUID adjunto(UUID journey,boolean retired) {
        UUID id=UUID.randomUUID();
        db.sql.update("""
            INSERT INTO citizen_attachment(id,journey_id,name,size_bytes,sha256,media_type,content,bytes_retired)
            VALUES(?,?,'adjunto sintetico',1,?,'text/plain',?,?)
            """,id.toString(),journey.toString(),"0".repeat(64),retired?null:new byte[]{42},retired);
        return id;
    }
    private void recepcion(UUID journey) {
        db.sql.update("""
            INSERT INTO citizen_submission(id,journey_id,owner_session,request_hash,body,state,created_ms,next_attempt_ms,ack,minimal_receipt)
            VALUES(?,?,?,?,'documento sintetico','CONFIRMADA',?,0,'ack sintetico','recibo sintetico')
            """,UUID.randomUUID().toString(),journey.toString(),session,"0".repeat(64),now.get());
    }

    @ParameterizedTest @ValueSource(strings={"BORRADOR","CANCELADO"})
    void vencidosSeMinimizanSinCicloDeVidaYUnaSolaVez(String state) {
        UUID id=vencido(state);carga(id,true);adjunto(id,true);
        assertEquals(1,service.ejecutar().minimizadas());identidad(id,0);assertEquals("MINIMIZADO",estado(id));
        assertEquals("0".repeat(64),db.sql.queryForObject("SELECT owner_session FROM citizen_journey WHERE id=?",String.class,id.toString()));
        assertEquals(0,service.ejecutar().minimizadas());
        assertEquals(1,db.sql.queryForObject("SELECT COUNT(*) FROM operational_event WHERE category='MINIMIZACION_BORRADORES' AND code='BORRADOR_PERSONAL_RETIRADO'",Integer.class));
        assertEquals(1L,service.estado().get("minimizadas"));assertFalse(service.estado().toString().contains("SINTETICA"));
        assertEquals("retirado",db.sql.queryForObject("SELECT name FROM media_upload WHERE journey_id=?",String.class,id.toString()));
        assertEquals(0,db.sql.queryForObject("SELECT COUNT(*) FROM storage_retirement",Integer.class));
    }

    @Test void plazoNoAutorizaBorrarAntesDeLaFrontera() {
        UUID id=recorrido("BORRADOR",now.get()-PLAZO.toMillis()+1);
        assertEquals(0,service.ejecutar().minimizadas());identidad(id,1);
        now.incrementAndGet();assertEquals(1,service.ejecutar().minimizadas());identidad(id,0);
    }

    @Test void cancelarConFlagApagadaConservaIdentidad() {
        UUID id=recorrido("BORRADOR",now.get()+10000);
        recorridos(false).cancelar(id,session);identidad(id,1);assertEquals("CANCELADO",estado(id));
    }

    @Test void cancelarConFlagEncendidaMinimizaInmediatamente() {
        UUID id=recorrido("BORRADOR",now.get()+10000);
        recorridos(true).cancelar(id,session);identidad(id,0);assertEquals("MINIMIZADO",estado(id));
    }

    @Test void cancelacionSinPropiedadNoMinimiza() {
        UUID id=recorrido("BORRADOR",now.get()+10000);
        assertThrows(FalloTecnico.class,()->recorridos(true).cancelar(id,"b".repeat(64)));identidad(id,1);
    }

    @Test void canceladoConCargaPendienteQuedaElegibleAlLiberarse() {
        UUID id=recorrido("BORRADOR",now.get()+10000);UUID upload=carga(id,false);
        recorridos(true).cancelar(id,session);identidad(id,0);assertEquals("CANCELADO",estado(id));
        assertEquals(1L,service.estado().get("minimizadas"));
        now.addAndGet(PLAZO.plusHours(1).toMillis());
        assertEquals(0,service.ejecutar().minimizadas());identidad(id,0);
        db.sql.update("UPDATE media_upload SET released=TRUE WHERE id=?",upload.toString());
        var finalizada=service.ejecutar();
        assertEquals(0,finalizada.minimizadas());assertEquals(1,finalizada.completadas());
        assertEquals("MINIMIZADO",estado(id));
        assertEquals(1L,service.estado().get("minimizadas"));
    }

    @ParameterizedTest @ValueSource(strings={"BORRADOR","CANCELADO"})
    void bytesPendientesConservanRecursosPeroRetiranIdentidadUnaSolaVez(String state)throws Exception {
        UUID id=vencido(state);UUID attachment=adjunto(id,false);UUID upload=carga(id,false);
        // Una carga validada puede conservar original y derivada mientras otra sigue pendiente.
        db.sql.update("UPDATE media_upload SET state='VALIDADO',error_code='SINTETICO' WHERE id=?",upload.toString());
        Files.write(store.original(attachment),new byte[]{42});
        Files.write(store.original(upload),new byte[]{43});
        Files.write(store.derivada(upload),new byte[]{44});
        var journeyBefore=db.sql.queryForMap("SELECT * FROM citizen_journey WHERE id=?",id.toString());
        var uploadBefore=db.sql.queryForMap("SELECT * FROM media_upload WHERE id=?",upload.toString());
        var attachmentBefore=db.sql.queryForMap("SELECT * FROM citizen_attachment WHERE id=?",attachment.toString());
        byte[] content=(byte[])attachmentBefore.remove("content");
        assertEquals(1,service.ejecutar().minimizadas());identidad(id,0);
        assertEquals(0,service.ejecutar().minimizadas());
        assertEquals(1L,service.estado().get("minimizadas"));
        assertEquals(journeyBefore,db.sql.queryForMap("SELECT * FROM citizen_journey WHERE id=?",id.toString()));
        assertEquals(uploadBefore,db.sql.queryForMap("SELECT * FROM media_upload WHERE id=?",upload.toString()));
        var attachmentAfter=db.sql.queryForMap("SELECT * FROM citizen_attachment WHERE id=?",attachment.toString());
        assertArrayEquals(content,(byte[])attachmentAfter.remove("content"));assertEquals(attachmentBefore,attachmentAfter);
        assertArrayEquals(new byte[]{42},Files.readAllBytes(store.original(attachment)));
        assertArrayEquals(new byte[]{43},Files.readAllBytes(store.original(upload)));
        assertArrayEquals(new byte[]{44},Files.readAllBytes(store.derivada(upload)));
        assertArrayEquals(new byte[]{42},db.sql.queryForObject("SELECT content FROM citizen_attachment WHERE id=?",byte[].class,attachment.toString()));
        assertEquals(1,db.sql.queryForObject("SELECT COUNT(*) FROM operational_event WHERE category='MINIMIZACION_BORRADORES' AND code='IDENTIDAD_BORRADOR_RETIRADA'",Integer.class));
        assertEquals(0,db.sql.queryForObject("SELECT COUNT(*) FROM storage_retirement",Integer.class));
        // Simula la retirada por su mecanismo autorizado, sin activarlo en esta unidad.
        db.sql.update("UPDATE citizen_attachment SET bytes_retired=TRUE,content=NULL WHERE id=?",attachment.toString());
        db.sql.update("UPDATE media_upload SET released=TRUE WHERE id=?",upload.toString());
        assertEquals(0,service.ejecutar().minimizadas());identidad(id,0);assertEquals("MINIMIZADO",estado(id));
        assertEquals(1L,service.estado().get("minimizadas"));
        assertTrue(Files.exists(store.original(attachment))); // El minimizador nunca toca bytes.
    }

    @ParameterizedTest @ValueSource(strings={"citizen_person","citizen_identity_history"})
    void retiraIdentidadAunqueSoloQuedeUnaDeLasTablas(String table) {
        UUID id=vencido("BORRADOR");carga(id,false);
        db.sql.update("DELETE FROM "+table+" WHERE journey_id=?",id.toString());
        assertEquals(1,service.ejecutar().minimizadas());identidad(id,0);
        assertEquals(0,service.ejecutar().minimizadas());
        assertEquals(1,db.sql.queryForObject("SELECT COUNT(*) FROM operational_event WHERE code='IDENTIDAD_BORRADOR_RETIRADA'",Integer.class));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void cadaTipoDePendientePorSiSoloPermiteRetirarIdentidad(boolean soloCarga) {
        UUID id=vencido("BORRADOR");
        if(soloCarga)carga(id,false);else adjunto(id,false);
        assertEquals(1,service.ejecutar().minimizadas());identidad(id,0);
        assertEquals("BORRADOR",estado(id));assertEquals(0,service.ejecutar().minimizadas());
        service.alCancelar(id);assertEquals(1L,service.estado().get("minimizadas"));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void cancelacionConPendientesRetiraSoloIdentidadYNoRepiteEvento(boolean soloCarga) {
        UUID id=recorrido("BORRADOR",now.get()+10000);
        if(soloCarga)carga(id,false);else adjunto(id,false);
        recorridos(true).cancelar(id,session);identidad(id,0);assertEquals("CANCELADO",estado(id));
        service.alCancelar(id);assertEquals(1L,service.estado().get("minimizadas"));
        assertEquals(session,db.sql.queryForObject("SELECT owner_session FROM citizen_journey WHERE id=?",String.class,id.toString()));
        assertEquals(1,db.sql.queryForObject("SELECT COUNT(*) FROM operational_event WHERE code='IDENTIDAD_BORRADOR_RETIRADA'",Integer.class));
    }

    @ParameterizedTest @CsvSource({"BORRADOR,NNA","CANCELADO,NNA","BORRADOR,ADULTO_DECLARADO","CANCELADO,ADULTO_DECLARADO"})
    void vencidoSinIdentidadRechazaFlujosCiudadanosYRecepcion(String state,String kind)throws Exception {
        UUID id=vencido(state);adjunto(id,false);
        db.sql.update("UPDATE citizen_journey SET kind=? WHERE id=?",kind,id.toString());
        assertEquals(1,service.ejecutar().minimizadas());identidad(id,0);
        var journeys=recorridos(true);
        assertEquals(404,assertThrows(FalloTecnico.class,()->journeys.consultar(id,session)).status);
        assertEquals(404,assertThrows(FalloTecnico.class,()->journeys.persona(id,"presentador-nna",session)).status);
        assertEquals(404,assertThrows(FalloTecnico.class,()->journeys.personasInternas(id,session)).status);
        assertEquals(404,assertThrows(FalloTecnico.class,()->journeys.exigirVigenciaNna(id,session)).status);
        assertEquals(404,assertThrows(FalloTecnico.class,()->journeys.identificar(id,"presentador-nna",
            IdentificacionCiudadana.Rol.PRESENTADOR,null,null,session,"127.0.0.1",null)).status);
        var files=new AdjuntosFormulario(db,journeys,b->{throw new AssertionError("Sin inspección");});
        assertEquals(404,assertThrows(FalloTecnico.class,()->files.listar(id,session)).status);
        assertEquals(404,assertThrows(FalloTecnico.class,()->files.reemplazar(id,session,List.of())).status);
        var key=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var mapper=JsonMapper.builder().build();
        var receptor=new RecepcionFormularioJdbc(db,journeys,files,mapper,
            new ValidacionFormulario(new TerritorioFormulario(mapper)),"sintetico",key.getPrivate(),
            Map.of("sintetico",key.getPublic()),Map.of(),clock,true,true);
        var datos=new DatosFormulario(Map.of(),List.of(),List.of(),List.of());
        assertEquals(404,assertThrows(FalloTecnico.class,()->receptor.recibir(UUID.randomUUID(),id,session,datos)).status);
        assertEquals(0,filas("citizen_submission",id));identidad(id,0);
    }

    @ParameterizedTest @ValueSource(strings={"RECIBIDO","CONFIRMADO","BORRADOR","CANCELADO"})
    void unaRecepcionSiempreProtegeDocumentoAckReciboIdentidadYOriginal(String state)throws Exception {
        UUID id=vencido(state);recepcion(id);UUID attachment=adjunto(id,false);
        Files.write(store.original(attachment),new byte[]{42});
        var before=db.sql.queryForMap("SELECT * FROM citizen_submission WHERE journey_id=?",id.toString());
        assertEquals(0,service.ejecutar().minimizadas());service.alCancelar(id);identidad(id,1);
        assertEquals(state,estado(id));assertEquals(before,db.sql.queryForMap("SELECT * FROM citizen_submission WHERE journey_id=?",id.toString()));
        assertArrayEquals(new byte[]{42},Files.readAllBytes(store.original(attachment)));
        assertEquals("adjunto sintetico",db.sql.queryForObject("SELECT name FROM citizen_attachment WHERE id=?",String.class,attachment.toString()));
    }

    @Test void recibidoInclusoSinSubmissionNoSeToca() {
        UUID id=vencido("RECIBIDO");assertEquals(0,service.ejecutar().minimizadas());service.alCancelar(id);identidad(id,1);
        assertThrows(FalloTecnico.class,()->recorridos(true).cancelar(id,session));identidad(id,1);
    }

    @Test void gateOcupadoConservaYReintenta()throws Exception {
        UUID id=vencido("BORRADOR");
        try(var read=store.lectura(id)) {assertEquals(1,service.ejecutar().conservadas());identidad(id,1);}
        assertEquals(1,service.ejecutar().minimizadas());identidad(id,0);
    }

    @Test void falloSqlRevierteTodoYPermiteReintento() {
        UUID id=vencido("BORRADOR");
        TestDatabase.migration(db,sql->sql.execute("ALTER TABLE citizen_journey ADD CONSTRAINT test_min_draft CHECK (state<>'MINIMIZADO')"));
        try {assertEquals(1,service.ejecutar().incidencias());identidad(id,1);assertEquals("BORRADOR",estado(id));}
        finally {TestDatabase.migration(db,sql->sql.execute("ALTER TABLE citizen_journey DROP "+
            (System.getenv("ROBUST_DB_URL")==null?"CONSTRAINT":"CHECK")+" test_min_draft"));}
        assertEquals(0,db.sql.queryForObject("SELECT COUNT(*) FROM operational_event WHERE category='MINIMIZACION_BORRADORES'",Integer.class));
        assertEquals(1,service.ejecutar().minimizadas());identidad(id,0);
    }

    @Test void dosEjecutoresNoDuplicanBorradoNiEvento()throws Exception {
        UUID id=vencido("BORRADOR");var other=new MinimizacionBorradores(db,store,clock,PLAZO);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var a=pool.submit(service::ejecutar);var b=pool.submit(other::ejecutar);
            assertEquals(1,a.get(5,TimeUnit.SECONDS).minimizadas()+b.get(5,TimeUnit.SECONDS).minimizadas());
        }
        identidad(id,0);
        assertEquals(1,db.sql.queryForObject("SELECT COUNT(*) FROM operational_event WHERE category='MINIMIZACION_BORRADORES'",Integer.class));
    }

    @Test void primerLoteBloqueadoNoImpideMinimizarElSiguiente()throws Exception {
        for(int i=0;i<51;i++)carga(vencido("BORRADOR"),false);
        var ids=db.sql.queryForList("SELECT id FROM citizen_journey ORDER BY id",String.class);
        try(var gate=store.lectura(UUID.fromString(ids.getFirst()))) {
            assertEquals(49,service.ejecutar().minimizadas());assertEquals(1,service.ejecutar().minimizadas());
            identidad(UUID.fromString(ids.getLast()),0);
        }
        assertEquals(1,service.ejecutar().minimizadas());
    }
}
