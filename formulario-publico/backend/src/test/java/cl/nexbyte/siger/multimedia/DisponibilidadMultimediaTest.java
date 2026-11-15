package cl.nexbyte.siger.multimedia;

import cl.nexbyte.siger.config.OrigenCliente;
import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.robustez.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.json.JsonMapper;
import static cl.nexbyte.siger.formulario.IdentificacionCiudadana.Tipo.NNA;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Cuotas y leases sobre H2 real, almacén simulado; ningún servicio ni proveedor externo. */
class DisponibilidadMultimediaTest {
    @TempDir Path temp;
    BaseJdbc db;
    RecorridosJdbc journeys;
    AlmacenPrivado store;
    CargasMultimedia media;
    final JsonMapper mapper=JsonMapper.builder().build();
    final AtomicLong now=new AtomicLong(Instant.parse("2026-11-15T12:00:00Z").toEpochMilli());
    final Clock clock=new Clock(){
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return Instant.ofEpochMilli(now.get());}
    };
    final PoliticaMultimedia policy=new PoliticaMultimedia(12,1024,512,512,512,512,512,
            16384,0,3600000,4096,24,900000);
    final String session="a".repeat(64);
    final OrigenCliente origin=new OrigenCliente(new MockEnvironment()
            .withProperty("formulario.origen.cabecera","X-Ddn-Client-Ip")
            .withProperty("formulario.origen.proxies-confiables","172.20.0.0/16"));

    @BeforeEach void setup()throws Exception{
        db=new BaseJdbc("jdbc:h2:file:"+temp.resolve("db")+";DB_CLOSE_ON_EXIT=FALSE","sa","",12);
        db.migrate("publico");
        journeys=spy(new RecorridosJdbc(db,mapper,r->{throw new AssertionError("Sin proveedor");},
                r->false,clock,new byte[32],"ADULTO_TEST_1","NNA_TEST_1"));
        store=mock(AlmacenPrivado.class);
        when(store.espacio()).thenReturn(1L<<30);
        when(store.lectura(any())).thenAnswer(call->(AlmacenPrivado.Guardia)()->{});
        when(store.mantenimiento(any())).thenAnswer(call->(AlmacenPrivado.Guardia)()->{});
        when(store.guardia()).thenAnswer(call->(AlmacenPrivado.Guardia)()->{});
        when(store.original(any())).thenAnswer(call->temp.resolve(call.getArgument(0)+"-original"));
        when(store.derivada(any())).thenAnswer(call->temp.resolve(call.getArgument(0)+"-derivada"));
        when(store.bloque(any(),anyInt())).thenAnswer(call->temp.resolve(call.getArgument(0)+"-"+call.getArgument(1)));
        when(store.ensamblar(any(),anyLong(),anyLong())).thenAnswer(call->store.original(call.getArgument(0)));
        media=service(policy,null);
    }
    @AfterEach void close()throws Exception{
        if(media!=null)media.close();
        if(store!=null)store.close();
        if(db!=null)db.close();
    }
    CargasMultimedia service(PoliticaMultimedia p,ExecutorService executor){
        CargasMultimedia.Antivirus av=path->true;
        CargasMultimedia.Inspector inspector=(path,format)->new CargasMultimedia.Inspeccion(format.tipo(),"DESCARGA",null);
        return executor==null?new CargasMultimedia(db,journeys,store,p,av,inspector,clock,mapper)
                :new CargasMultimedia(db,journeys,store,p,av,inspector,clock,mapper,executor);
    }
    String address(String ip){
        var request=new MockHttpServletRequest();request.setRemoteAddr("172.20.0.5");
        request.addHeader("X-Ddn-Client-Ip",ip);request.addHeader("X-Forwarded-For","198.51.100.1");
        return origin.de(request);
    }
    UUID journey(String owner,String ip){return journeys.iniciar(NNA,false,"NNA_TEST_1",owner,address(ip)).id();}
    CargasMultimedia.Estado reserve(UUID journey,String owner,int size,int hash){
        return media.iniciar(journey,owner,new CargasMultimedia.Inicio("sintetico.txt",size,"%064x".formatted(hash)));
    }
    long reserved(){return db.sql.queryForObject("SELECT reserved_bytes FROM media_capacity WHERE id=1",Long.class);}
    String state(UUID id){return db.sql.queryForObject("SELECT state FROM media_upload WHERE id=?",String.class,id.toString());}
    boolean released(UUID id){return db.sql.queryForObject("SELECT released FROM media_upload WHERE id=?",Boolean.class,id.toString());}
    CargasMultimedia.Estado complete(UUID journey)throws Exception{
        byte[] bytes="dato sintético".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        var upload=media.iniciar(journey,session,new CargasMultimedia.Inicio("sintetico.txt",bytes.length,hash));
        // Simula bloques persistidos antes de inspeccionar; el flujo POSIX se prueba en MultimediaJdbcTest.
        Files.write(store.bloque(upload.id(),0),bytes);Files.write(store.original(upload.id()),bytes);
        db.sql.update("INSERT INTO media_chunk(upload_id,chunk_index,size_bytes,sha256) VALUES(?,0,?,?)",
                upload.id().toString(),bytes.length,hash);
        db.sql.update("UPDATE media_upload SET offset_bytes=? WHERE id=?",bytes.length,upload.id().toString());
        return media.estado(journey,upload.id(),session);
    }
    void block(UUID journey,UUID upload,int index)throws Exception{
        byte[] bytes=new byte[512];
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        when(store.temporal()).thenAnswer(call->Files.createTempFile(temp,"bloque-",".part"));
        doCallRealMethod().when(store).recibir(any(),anyLong(),any());
        doAnswer(call->{Files.move(call.getArgument(0),call.getArgument(1),StandardCopyOption.REPLACE_EXISTING);
            return null;}).when(store).publicar(any(),any());
        media.bloque(journey,upload,session,index,hash,bytes.length,new ByteArrayInputStream(bytes));
        assertEquals(now.get(),db.sql.queryForObject(
                "SELECT touched_ms FROM media_upload WHERE id=?",Long.class,upload.toString()));
    }

    @Test void onceSesionesIPv6DelMismoPrefijoNoAgotanCuotaYOtroOrigenReserva()throws Exception{
        var ids=new ArrayList<UUID>();var sessions=new ArrayList<String>();
        for(int i=0;i<11;i++){
            var owner="%064x".formatted(i+1);sessions.add(owner);
            ids.add(journey(owner,"2001:db8:1:2::"+Integer.toHexString(i+1)));
        }
        var pool=Executors.newFixedThreadPool(11);var start=new CountDownLatch(1);
        try{
            var tasks=new ArrayList<Future<Integer>>();
            for(int i=0;i<11;i++){
                final int index=i;
                tasks.add(pool.submit(()->{
                    start.await();
                    try{reserve(ids.get(index),sessions.get(index),512,index+1);return 201;}
                    catch(FalloTecnico fail){return fail.status;}
                }));
            }
            start.countDown();var statuses=new ArrayList<Integer>();
            for(var task:tasks)statuses.add(task.get(10,TimeUnit.SECONDS));
            assertEquals(2,Collections.frequency(statuses,201));
            assertEquals(9,Collections.frequency(statuses,429));
            assertEquals(policy.origenBytes(),reserved());
        }finally{pool.shutdownNow();}
        assertEquals(1,db.sql.queryForObject("SELECT COUNT(DISTINCT media_origin_ref) FROM citizen_journey",Integer.class));
        var ref=db.sql.queryForObject("SELECT media_origin_ref FROM citizen_journey LIMIT 1",String.class);
        assertTrue(ref.matches("[a-f0-9]{64}"));
        var other=journey(session,"2001:db8:1:3::1");
        assertNotNull(reserve(other,session,512,50));
        assertEquals(6144,reserved());assertTrue(reserved()<policy.global());
    }

    @Test void cantidadActivaPorOrigenSeLimitaYCancelarDevuelveElCupo(){
        media.close();
        var countPolicy=new PoliticaMultimedia(12,1024,512,512,512,512,512,16384,0,3600000,12000,2,900000);
        media=service(countPolicy,null);
        var a=journey(session,"203.0.113.1");var first=reserve(a,session,1,1);
        var b=journey("b".repeat(64),"203.0.113.1");reserve(b,"b".repeat(64),1,2);
        var c=journey("c".repeat(64),"203.0.113.1");
        assertEquals(429,assertThrows(FalloTecnico.class,()->reserve(c,"c".repeat(64),1,3)).status);
        assertNotNull(reserve(journey("d".repeat(64),"203.0.113.2"),"d".repeat(64),1,4));
        media.cancelar(a,first.id(),session);
        assertNotNull(reserve(c,"c".repeat(64),1,3));
    }

    @Test void reservaSinBloquesVencePerezosamenteSinCicloDeVidaYLiberaUnaSolaVez(){
        var id=journey(session,"203.0.113.1");var abandoned=reserve(id,session,512,1);
        assertEquals(2048,reserved());now.addAndGet(policy.sinProgresoMs()-1);
        reserve(id,session,512,2);assertFalse(released(abandoned.id()));
        now.incrementAndGet();var next=reserve(id,session,512,3);
        assertEquals("EXPIRADO",state(abandoned.id()));assertTrue(released(abandoned.id()));
        assertEquals(4096,reserved()); // La segunda reserva aún está dentro de su plazo.
        reserve(id,session,512,3);assertEquals(4096,reserved());
        assertEquals(next.id(),reserve(id,session,512,3).id());
    }

    @Test void falloDeRespuestaTrasExecuteNoLiberaPermisoDelWorker()throws Exception{
        var executor=mock(ExecutorService.class);var pending=new java.util.concurrent.atomic.AtomicReference<Runnable>();
        doAnswer(call->{
            pending.set(call.getArgument(0));
            doThrow(new FalloTecnico(503)).when(journeys).consultar(any(),any());return null;
        }).when(executor).execute(any());
        when(executor.awaitTermination(anyLong(),any())).thenReturn(true);
        media.close();media=service(policy,executor);
        var id=journey(session,"203.0.113.1");var upload=complete(id);
        assertEquals(503,assertThrows(FalloTecnico.class,()->media.finalizar(id,upload.id(),session)).status);
        assertEquals(1,media.capacidad().get("analisisActivos"));
        doCallRealMethod().when(journeys).consultar(any(),any());
        now.addAndGet(180001); // Aunque venza el lease, un worker vivo conserva su reserva.
        var second=reserve(id,session,1,2);
        assertFalse(released(upload.id()));verify(store,never()).borrarNoRecibido(upload.id());
        assertEquals(429,assertThrows(FalloTecnico.class,()->media.finalizar(id,second.id(),session)).status);
        pending.get().run();assertEquals(0,media.capacidad().get("analisisActivos"));
        assertEquals("VALIDADO",state(upload.id()));
        assertEquals(409,assertThrows(FalloTecnico.class,()->media.finalizar(id,second.id(),session)).status);
        assertEquals(0,media.capacidad().get("analisisActivos"));
    }

    @Test void rechazoDeExecutorDevuelvePermisoYDejaCargaReintentable()throws Exception{
        var executor=mock(ExecutorService.class);
        doThrow(new RejectedExecutionException("Rechazo sintético")).when(executor).execute(any());
        when(executor.awaitTermination(anyLong(),any())).thenReturn(true);
        media.close();media=service(policy,executor);
        var id=journey(session,"203.0.113.1");var upload=complete(id);
        for(int i=0;i<3;i++){
            assertThrows(RejectedExecutionException.class,()->media.finalizar(id,upload.id(),session));
            assertEquals(0,media.capacidad().get("analisisActivos"));
            assertEquals("REINTENTABLE",state(upload.id()));
            assertEquals(0L,db.sql.queryForObject("SELECT lease_until_ms FROM media_upload WHERE id=?",Long.class,upload.id().toString()));
        }
    }

    @Test void cerrarDescartaTrabajoEnColaSinRetenerNiDuplicarElPermiso()throws Exception{
        var executor=mock(ExecutorService.class);var pending=new java.util.concurrent.atomic.AtomicReference<Runnable>();
        doAnswer(call->{pending.set(call.getArgument(0));return null;}).when(executor).execute(any());
        when(executor.shutdownNow()).thenAnswer(call->pending.get()==null?List.of():List.of(pending.get()));
        when(executor.awaitTermination(anyLong(),any())).thenReturn(true);
        media.close();media=service(policy,executor);
        var id=journey(session,"203.0.113.1");var upload=complete(id);
        media.finalizar(id,upload.id(),session);assertEquals(1,media.capacidad().get("analisisActivos"));
        media.close();assertEquals(0,media.capacidad().get("analisisActivos"));
        assertEquals("REINTENTABLE",state(upload.id()));
        pending.get().run();assertEquals(0,media.capacidad().get("analisisActivos"));
        assertEquals("REINTENTABLE",state(upload.id()));
    }

    @Test void analizandoHuerfanoConLeaseVencidoSeRecuperaYFinalizaTrasRecrearServicio()throws Exception{
        var id=journey(session,"203.0.113.1");var upload=complete(id);
        store.ensamblar(upload.id(),upload.tamanio(),policy.bloque());
        db.sql.update("UPDATE media_upload SET state='ANALIZANDO',lease_id=?,lease_until_ms=? WHERE id=?",
                UUID.randomUUID().toString(),now.get()+1000,upload.id().toString());
        media.close();media=service(policy,null);
        now.addAndGet(999);assertTrue(media.sanear(true).isEmpty());
        now.incrementAndGet();var next=reserve(id,session,512,2);
        assertEquals("REINTENTABLE",state(upload.id()));assertFalse(released(upload.id()));
        assertEquals("INSPECCION_INTERRUMPIDA",media.estado(id,upload.id(),session).error());
        assertEquals(0L,db.sql.queryForObject("SELECT lease_until_ms FROM media_upload WHERE id=?",
                Long.class,upload.id().toString()));
        assertTrue(Files.exists(store.bloque(upload.id(),0)));
        verify(store,never()).borrarNoRecibido(upload.id());
        long before=reserved();assertTrue(media.sanear(true).isEmpty());
        assertEquals(next.id(),reserve(id,session,512,2).id());assertEquals(before,reserved());
        // Ejecución sincrónica controlada para comprobar el reintento sin esperas ni proveedor externo.
        var executor=mock(ExecutorService.class);
        doAnswer(call->{((Runnable)call.getArgument(0)).run();return null;}).when(executor).execute(any());
        when(executor.awaitTermination(anyLong(),any())).thenReturn(true);
        media.close();media=service(policy,executor);
        assertEquals("VALIDADO",media.finalizar(id,upload.id(),session).estado());
        assertEquals(before,reserved());assertFalse(released(upload.id()));
    }

    @ParameterizedTest @ValueSource(strings={"RECIBIENDO","REINTENTABLE","RECHAZADO","CANCELADO","ANALIZANDO"})
    void vencimientoAbsolutoLiberaAutomaticamenteSinCicloDeVida(String state)throws Exception{
        var id=journey(session,"203.0.113.1");var upload=complete(id);
        db.sql.update("UPDATE media_upload SET state=?,expires_ms=?,lease_until_ms=0 WHERE id=?",
                state,now.get(),upload.id().toString());
        var next=reserve(id,session,512,2);
        assertEquals("EXPIRADO",state(upload.id()));assertTrue(released(upload.id()));
        verify(store).borrarNoRecibido(upload.id());assertEquals(2048,reserved());
        assertEquals(next.id(),reserve(id,session,512,2).id());assertEquals(2048,reserved());
        assertTrue(media.sanear(true).isEmpty());
    }

    @Test void analizandoHuerfanoVencidoTambienSeLiberaManualmente()throws Exception{
        var id=journey(session,"203.0.113.1");var upload=complete(id);
        db.sql.update("UPDATE media_upload SET state='ANALIZANDO',expires_ms=?,lease_until_ms=0 WHERE id=?",
                now.get(),upload.id().toString());
        assertEquals(1,media.sanear(true).size());assertTrue(released(upload.id()));
        assertEquals(0,reserved());verify(store).borrarNoRecibido(upload.id());
        assertTrue(media.sanear(true).isEmpty());assertEquals(0,reserved());
    }

    @ParameterizedTest @ValueSource(ints={512,1024})
    void unBloqueInactivoVenceYDevuelveLaCuotaDelOrigen(int size)throws Exception{
        long needed=2L*size+1024;
        media.close();media=service(new PoliticaMultimedia(12,1024,512,1024,1024,1024,1024,
                32768,0,3600000,2*needed,24,900000),null);
        var id=journey(session,"203.0.113.1");var upload=reserve(id,session,size,1);
        now.addAndGet(1000);block(id,upload.id(),0);
        var other=journey("b".repeat(64),"203.0.113.1");reserve(other,"b".repeat(64),size,2);
        var third=journey("c".repeat(64),"203.0.113.1");
        assertEquals(429,assertThrows(FalloTecnico.class,()->reserve(third,"c".repeat(64),size,3)).status);
        now.addAndGet(30*60000L-1);
        // La segunda reserva sin bloques vence antes; la primera sigue justo dentro de inactividad.
        reserve(third,"c".repeat(64),size,3);assertFalse(released(upload.id()));
        now.incrementAndGet();reserve(other,"b".repeat(64),size,4);
        assertTrue(released(upload.id()));assertEquals("EXPIRADO",state(upload.id()));
        verify(store).borrarNoRecibido(upload.id());assertEquals(2*needed,reserved());
        reserve(other,"b".repeat(64),size,4);assertEquals(2*needed,reserved());
    }

    @Test void cadaBloqueRecienteMantieneActivaUnaReservaAntigua()throws Exception{
        var id=journey(session,"203.0.113.1");
        // Dos bloques, con suficiente cuota para admitir esta reserva de 3072 bytes.
        media.close();media=service(new PoliticaMultimedia(12,1024,512,1024,1024,1024,1024,
                16384,0,3600000,8192,24,900000),null);
        var upload=reserve(id,session,1024,1);
        now.addAndGet(1000);block(id,upload.id(),0);
        now.addAndGet(30*60000L-1);block(id,upload.id(),1);
        now.addAndGet(1000);reserve(journey(session,"203.0.113.2"),session,1,2);
        now.incrementAndGet();block(id,upload.id(),0); // Reenvío aceptado actualiza actividad sin retroceder offset.
        assertFalse(released(upload.id()));assertEquals("RECIBIENDO",state(upload.id()));
        assertEquals(1024,media.estado(id,upload.id(),session).recibidos());
        verify(store,never()).borrarNoRecibido(upload.id());
    }

    @Test void pasadaAutomaticaProcesaVeinteReservasPorAntiguedadYContinuaEnLaSiguiente(){
        media.close();media=service(new PoliticaMultimedia(12,1024,512,512,512,512,512,
                65536,0,3600000,8192,24,900000),null);
        var uploads=new ArrayList<UUID>();
        for(int i=0;i<25;i++){
            var owner="%064x".formatted(i+1);
            uploads.add(reserve(journey(owner,"198.51.100."+(i+1)),owner,1,i+1).id());
            now.incrementAndGet();
        }
        now.addAndGet(policy.sinProgresoMs());
        var id=journey(session,"203.0.113.1");reserve(id,session,1,50);
        assertEquals(20,db.sql.queryForObject("SELECT COUNT(*) FROM media_upload WHERE released=TRUE",Integer.class));
        for(int i=0;i<25;i++)assertEquals(i<20,released(uploads.get(i)));
        assertEquals(6*1026,reserved());
        reserve(id,session,1,50);
        assertEquals(25,db.sql.queryForObject("SELECT COUNT(*) FROM media_upload WHERE released=TRUE",Integer.class));
        assertEquals(1026,reserved());
    }

    @Test void limpiezaAutomaticaNoTocaBloquesConProgresoAdjuntosNiRecorridosRecibidos()throws Exception{
        var id=journey(session,"203.0.113.1");var attachment=complete(id);
        db.sql.update("UPDATE media_upload SET state='VALIDADO',media_type='text/plain',view_kind='DESCARGA' WHERE id=?",attachment.id().toString());
        store.ensamblar(attachment.id(),attachment.tamanio(),policy.bloque());
        media.confirmar(id,session,new CargasMultimedia.Manifest(List.of(attachment.id()),media.lote(id,session).revision()));
        // Incluso un lease antiguo incoherente no habilita el borrado de un adjunto custodiado.
        db.sql.update("UPDATE media_upload SET state='ANALIZANDO',lease_until_ms=0 WHERE id=?",attachment.id().toString());
        var progress=complete(journey(session,"203.0.113.2"));
        var received=journey(session,"203.0.113.3");var retained=reserve(received,session,1,7);
        db.sql.update("UPDATE citizen_journey SET state='RECIBIDO' WHERE id=?",received.toString());
        long before=reserved();now.addAndGet(policy.sinProgresoMs()+1);
        reserve(journey(session,"203.0.113.4"),session,1,8);
        assertEquals(before+1026,reserved());
        assertFalse(released(attachment.id()));assertTrue(Files.exists(store.original(attachment.id())));
        assertFalse(released(progress.id()));assertTrue(Files.exists(store.bloque(progress.id(),0)));
        assertFalse(released(retained.id()));assertEquals("RECIBIENDO",state(retained.id()));
        verify(store,never()).borrarNoRecibido(attachment.id());
        verify(store,never()).borrarNoRecibido(progress.id());
        verify(store,never()).borrarNoRecibido(retained.id());
    }

    @Test void configuracionAcotaOrigenYNoPermiteTodaLaCuotaGlobal(){
        var config=new MultimediaConfiguration();
        var env=new MockEnvironment().withProperty("formulario.multimedia.cuota-global","4GB")
                .withProperty("ciclo-vida.online-habilitado","false");
        var p=config.politicaMultimedia(env);
        assertEquals(p.global()/4,p.origenBytes());assertEquals(900000,p.sinProgresoMs());
        assertEquals(1800000,p.inactividadMs());
        env.withProperty("formulario.multimedia.origen-bytes","4GB");
        assertThrows(IllegalArgumentException.class,()->config.politicaMultimedia(env));
        env.withProperty("formulario.multimedia.origen-bytes","1GB")
                .withProperty("formulario.multimedia.origen-reservas","3")
                .withProperty("formulario.multimedia.sin-progreso-ms","60000");
        assertEquals(3,config.politicaMultimedia(env).origenReservas());
        assertEquals(60000,config.politicaMultimedia(env).sinProgresoMs());
    }

    @Test void inactividadConfiguradaRespetaAmbosLimitesInclusivos(){
        var config=new MultimediaConfiguration();
        var env=new MockEnvironment().withProperty("formulario.multimedia.sin-progreso-ms","60000");
        for(long invalid:List.of(-1L,59999L,86400001L)){
            env.withProperty("formulario.multimedia.inactividad-ms",Long.toString(invalid));
            assertThrows(IllegalArgumentException.class,()->config.politicaMultimedia(env));
        }
        for(long valid:List.of(60000L,86400000L)){
            env.withProperty("formulario.multimedia.inactividad-ms",Long.toString(valid));
            assertEquals(valid,config.politicaMultimedia(env).inactividadMs());
        }
        var incompatible=new MockEnvironment().withProperty("formulario.multimedia.sin-progreso-ms","1800001");
        assertThrows(IllegalArgumentException.class,()->config.politicaMultimedia(incompatible));
    }

    @Test void interrupcionAlBorrarReintentaSinLiberarDosVeces()throws Exception{
        var id=journey(session,"203.0.113.1");var abandoned=reserve(id,session,512,1);
        now.addAndGet(policy.sinProgresoMs());
        doThrow(new IOException("Interrupción sintética")).doNothing().when(store).borrarNoRecibido(abandoned.id());
        assertEquals(503,assertThrows(FalloTecnico.class,()->reserve(id,session,512,2)).status);
        assertEquals("EXPIRADO",state(abandoned.id()));assertFalse(released(abandoned.id()));assertEquals(2048,reserved());
        reserve(id,session,512,2);assertTrue(released(abandoned.id()));assertEquals(2048,reserved());
        reserve(id,session,512,2);assertEquals(2048,reserved());
        verify(store,times(2)).borrarNoRecibido(abandoned.id());
    }

    @Test void recepcionEsperaElBorradoYLaLiberacionDelRecorridoBorrador()throws Exception{
        var id=journey(session,"203.0.113.1");var abandoned=reserve(id,session,512,1);
        var other=journey("b".repeat(64),"203.0.113.2");now.addAndGet(policy.sinProgresoMs());
        var pool=Executors.newSingleThreadExecutor();var attempted=new CountDownLatch(1);
        var reception=new java.util.concurrent.atomic.AtomicReference<Future<Integer>>();
        doAnswer(call->{
            reception.set(pool.submit(()->{
                attempted.countDown();
                return db.sql.update("UPDATE citizen_journey SET state='RECIBIDO' WHERE id=?",id.toString());
            }));
            assertTrue(attempted.await(1,TimeUnit.SECONDS));
            assertThrows(TimeoutException.class,()->reception.get().get(100,TimeUnit.MILLISECONDS));
            return null;
        }).when(store).borrarNoRecibido(abandoned.id());
        try{
            reserve(other,"b".repeat(64),512,2);
            assertEquals(1,reception.get().get(3,TimeUnit.SECONDS));
            assertTrue(released(abandoned.id()));assertEquals(2048,reserved());
        }finally{pool.shutdownNow();}
    }

    @Test void migracionDesdeV12ConservaReservasYAgrupaOrigenDesconocido()throws Exception{
        String url="jdbc:h2:file:"+temp.resolve("legacy")+";DB_CLOSE_ON_EXIT=FALSE";
        org.flywaydb.core.Flyway.configure().dataSource(url,"sa","")
                .locations("classpath:db/publico/h2").target("12").load().migrate();
        try(var legacy=new BaseJdbc(url,"sa","",2)){
            var id=UUID.randomUUID();var upload=UUID.randomUUID();
            legacy.sql.update("""
                INSERT INTO citizen_journey(id,owner_session,kind,notice_version,accepted_channel,state,created_ms,expires_ms)
                VALUES(?,?,'NNA','NNA_TEST_1',FALSE,'BORRADOR',?,?)
                """,id.toString(),session,now.get(),now.get()+3600000);
            legacy.sql.update("""
                INSERT INTO media_upload(id,journey_id,name,expected_size,expected_sha256,state,
                    created_ms,touched_ms,expires_ms,reserved_bytes)
                VALUES(?,?,'sintetico.txt',512,?,'RECIBIENDO',?,?,?,2048)
                """,upload.toString(),id.toString(),"0".repeat(64),now.get(),now.get(),now.get()+3600000);
            legacy.sql.update("UPDATE media_capacity SET reserved_bytes=2048 WHERE id=1");
            legacy.migrate("publico");
            assertEquals("0".repeat(64),legacy.sql.queryForObject(
                    "SELECT media_origin_ref FROM citizen_journey WHERE id=?",String.class,id.toString()));
            assertEquals(2048,legacy.sql.queryForObject("SELECT reserved_bytes FROM media_capacity WHERE id=1",Long.class));
            assertEquals("RECIBIENDO",legacy.sql.queryForObject("SELECT state FROM media_upload WHERE id=?",String.class,upload.toString()));
        }
        try(var h2=getClass().getResourceAsStream("/db/publico/h2/V13__origen_reserva_multimedia.sql");
            var mysql=getClass().getResourceAsStream("/db/publico/mysql/V13__origen_reserva_multimedia.sql")){
            assertNotNull(h2);assertNotNull(mysql);assertArrayEquals(h2.readAllBytes(),mysql.readAllBytes());
        }
    }
}
