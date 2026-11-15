package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.custodia.MinimizacionBorradores;
import cl.nexbyte.siger.formulario.RecorridosJdbc;
import cl.nexbyte.siger.multimedia.AlmacenPrivado;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Regresión transaccional H2: gates simulados; el almacén real se prueba por separado. */
class MinimizacionBorradoresSqlTest {
    @TempDir Path temp;
    private BaseJdbc db;
    private AlmacenPrivado store;
    private MinimizacionBorradores service;
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"),ZoneOffset.UTC);
    private final String session="a".repeat(64);
    private UUID journey,resource;

    @BeforeEach void setup()throws Exception {
        db=TestDatabase.open(temp);store=mock(AlmacenPrivado.class);
        when(store.mantenimiento(any())).thenAnswer(call->(AlmacenPrivado.Guardia)()->{});
        when(store.guardia()).thenAnswer(call->(AlmacenPrivado.Guardia)()->{});
        service=new MinimizacionBorradores(db,store,clock,Duration.ofHours(48));
        journey=UUID.randomUUID();resource=UUID.randomUUID();
    }

    @AfterEach void close() {
        if(db==null)return;
        for(String table:List.of("citizen_attachment","media_upload","citizen_identity_history",
                "citizen_person","citizen_journey"))db.sql.update("DELETE FROM "+table);
        db.close();
    }

    @ParameterizedTest @CsvSource({"BORRADOR,true","BORRADOR,false","CANCELADO,true","CANCELADO,false"})
    void pendienteRetiraIdentidadUnaVezYCompletaDespuesSinVolverAContarla(String state,boolean upload)throws Exception {
        preparar(state,upload);
        var journeyBefore=db.sql.queryForMap("SELECT * FROM citizen_journey WHERE id=?",journey.toString());
        String table=upload?"media_upload":"citizen_attachment";
        var resourceBefore=db.sql.queryForMap("SELECT * FROM "+table+" WHERE id=?",resource.toString());
        byte[] content=(byte[])resourceBefore.remove("content");

        var first=service.ejecutar();assertEquals(1,first.minimizadas());assertEquals(0,first.completadas());
        assertEquals(journeyBefore,db.sql.queryForMap("SELECT * FROM citizen_journey WHERE id=?",journey.toString()));
        var resourceAfter=db.sql.queryForMap("SELECT * FROM "+table+" WHERE id=?",resource.toString());
        assertArrayEquals(content,(byte[])resourceAfter.remove("content"));assertEquals(resourceBefore,resourceAfter);
        for(String identity:List.of("citizen_person","citizen_identity_history"))
            assertEquals(0,db.sql.queryForObject("SELECT COUNT(*) FROM "+identity+" WHERE journey_id=?",Integer.class,journey.toString()));
        assertEquals(0,service.ejecutar().minimizadas());
        service.alCancelar(journey);assertEquals(1L,service.estado().get("minimizadas"));
        assertEquals(1,db.sql.queryForObject("SELECT COUNT(*) FROM operational_event WHERE code='IDENTIDAD_BORRADOR_RETIRADA'",Integer.class));
        assertEquals(0,db.sql.queryForObject("SELECT COUNT(*) FROM storage_retirement",Integer.class));

        if(upload)db.sql.update("UPDATE media_upload SET released=TRUE WHERE id=?",resource.toString());
        else db.sql.update("UPDATE citizen_attachment SET bytes_retired=TRUE,content=NULL WHERE id=?",resource.toString());
        var last=service.ejecutar();assertEquals(0,last.minimizadas());assertEquals(1,last.completadas());
        assertEquals(1L,service.estado().get("minimizadas"));assertEquals(1L,service.estado().get("completadas"));
        assertEquals("MINIMIZADO",db.sql.queryForObject("SELECT state FROM citizen_journey WHERE id=?",String.class,journey.toString()));
        assertEquals("0".repeat(64),db.sql.queryForObject("SELECT owner_session FROM citizen_journey WHERE id=?",String.class,journey.toString()));
        assertEquals(0,service.ejecutar().completadas());
        verify(store,atLeastOnce()).mantenimiento(journey);verify(store,atLeastOnce()).guardia();
        verifyNoMoreInteractions(store); // Ninguna API de lectura, escritura o retirada de bytes.
    }

    @ParameterizedTest @ValueSource(strings={"BORRADOR","CANCELADO"})
    void sinPendientesConservaCaminoCompletoYContadores(String state) {
        preparar(state,true);
        db.sql.update("UPDATE media_upload SET released=TRUE WHERE id=?",resource.toString());
        var result=service.ejecutar();assertEquals(1,result.minimizadas());assertEquals(1,result.completadas());
        assertEquals("MINIMIZADO",db.sql.queryForObject("SELECT state FROM citizen_journey WHERE id=?",String.class,journey.toString()));
        assertEquals("0".repeat(64),db.sql.queryForObject("SELECT owner_session FROM citizen_journey WHERE id=?",String.class,journey.toString()));
        assertEquals("retirado",db.sql.queryForObject("SELECT name FROM media_upload WHERE id=?",String.class,resource.toString()));
        assertEquals(0,db.sql.queryForObject("SELECT COUNT(*) FROM citizen_person",Integer.class));
        assertEquals(0,db.sql.queryForObject("SELECT COUNT(*) FROM citizen_identity_history",Integer.class));
        assertEquals(0,service.ejecutar().minimizadas());assertEquals(0,service.ejecutar().completadas());
        assertEquals(1,db.sql.queryForObject("SELECT COUNT(*) FROM operational_event WHERE code='BORRADOR_PERSONAL_RETIRADO'",Integer.class));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void cancelarAntesDeVencerRetiraIdentidadConCadaTipoDePendiente(boolean upload) {
        preparar("BORRADOR",upload);
        db.sql.update("UPDATE citizen_journey SET expires_ms=? WHERE id=?",clock.millis()+10000,journey.toString());
        var journeys=new RecorridosJdbc(db,JsonMapper.builder().build(),r->{throw new AssertionError("Sin proveedor");},
            r->false,clock,new byte[32],"SINTETICO","SINTETICO").minimizacionBorradores(service::alCancelar);
        journeys.cancelar(journey,session);
        assertEquals("CANCELADO",db.sql.queryForObject("SELECT state FROM citizen_journey WHERE id=?",String.class,journey.toString()));
        assertEquals(session,db.sql.queryForObject("SELECT owner_session FROM citizen_journey WHERE id=?",String.class,journey.toString()));
        assertEquals(0,db.sql.queryForObject("SELECT COUNT(*) FROM citizen_person",Integer.class));
        assertEquals(0,db.sql.queryForObject("SELECT COUNT(*) FROM citizen_identity_history",Integer.class));
        assertEquals(404,assertThrows(FalloTecnico.class,()->journeys.persona(journey,"presentador-nna",session)).status);
        assertEquals(404,assertThrows(FalloTecnico.class,()->journeys.exigirVigenciaNna(journey,session)).status);
        service.alCancelar(journey);assertEquals(1L,service.estado().get("minimizadas"));
        assertEquals(1,db.sql.queryForObject("SELECT COUNT(*) FROM operational_event WHERE code='IDENTIDAD_BORRADOR_RETIRADA'",Integer.class));
    }

    private void preparar(String state,boolean upload) {
        db.sql.update("""
            INSERT INTO citizen_journey(id,owner_session,kind,notice_version,accepted_channel,state,created_ms,expires_ms)
            VALUES(?,?,'NNA','SINTETICO',FALSE,?,?,?)
            """,journey.toString(),session,state,clock.millis()-Duration.ofDays(3).toMillis(),
            clock.millis()-Duration.ofHours(48).toMillis());
        db.sql.update("INSERT INTO citizen_person(journey_id,person_id,role,body,query_state) VALUES(?,'presentador-nna','PRESENTADOR','{}','REVISADO')",
            journey.toString());
        db.sql.update("""
            INSERT INTO citizen_identity_history(id,journey_id,person_id,body,query_state,retired_ms)
            VALUES(?,?,'presentador-nna','{}','REVISADO',?)
            """,UUID.randomUUID().toString(),journey.toString(),clock.millis());
        if(upload)db.sql.update("""
            INSERT INTO media_upload(id,journey_id,name,expected_size,expected_sha256,state,created_ms,
                touched_ms,expires_ms,reserved_bytes,released)
            VALUES(?,?,'sintetico.txt',1,?,'EXPIRADO',?,?,?,0,FALSE)
            """,resource.toString(),journey.toString(),"0".repeat(64),clock.millis(),clock.millis(),clock.millis());
        else db.sql.update("""
            INSERT INTO citizen_attachment(id,journey_id,name,size_bytes,sha256,media_type,content,bytes_retired)
            VALUES(?,?,'sintetico.txt',1,?,'text/plain',?,FALSE)
            """,resource.toString(),journey.toString(),"0".repeat(64),new byte[]{42});
    }
}
