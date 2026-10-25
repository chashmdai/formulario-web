package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.custodia.*;
import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.multimedia.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Conexiones y transacciones simultáneas: no se usa el monitor Java como frontera entre instancias. */
class CierreV10CustodiaTest {
    @TempDir Path temp;
    ReceptorTransitorioTest r;
    @BeforeEach void setup()throws Exception{r=new ReceptorTransitorioTest();r.temp=temp;r.setup();}
    @AfterEach void close()throws Exception{r.close();}
    void prepared()throws Exception{r.receive(false);r.app=r.service(true);r.proofs.aceptar(r.proof(r.t.communication,"COMPLETO"));r.app.ejecutar();r.t.due();r.proofs.aceptar(r.proof(r.t.communication,"COMPLETO"));}
    BaseJdbc connection()throws Exception{
        String url;try(var c=r.t.f.db.sql.getDataSource().getConnection()){url=c.getMetaData().getURL();}
        return new BaseJdbc(url,System.getenv("ROBUST_DB_URL")==null?"sa":System.getenv("ROBUST_DB_USER"),System.getenv("ROBUST_DB_URL")==null?"":System.getenv("ROBUST_DB_PASSWORD"),4);
    }
    CicloVidaCustodia motor(BaseJdbc db,BiConsumer<String,UUID> pause){return new CicloVidaCustodia(db,r.t.f.store,r.t.archive,r.t.f.mapper,Map.of("publico",r.t.publicKey.getPublic()),Map.of("institucion",r.t.institutional.getPublic()),r.t.clock,new CicloVidaCustodia.Politica(true,Duration.ofHours(72),Duration.ofHours(48)),pause);}
    String phase(){return r.t.f.db.sql.queryForObject("SELECT phase FROM custody_lifecycle WHERE submission_id=?",String.class,r.t.communication.toString());}
    static void await(CountDownLatch latch){try{assertTrue(latch.await(3,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
    @Test void holdTransaccionalPrevaleceMientrasOtraInstanciaEsperaLaFila()throws Exception{
        prepared();var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var attempted=new CountDownLatch(1);
        try(var other=connection();var pool=Executors.newFixedThreadPool(2)){
            var holder=motor(other,(p,id)->{});var worker=motor(r.t.f.db,(p,id)->{});
            var hold=pool.submit(()->other.transaction(()->{
                other.sql.queryForMap("SELECT id FROM citizen_submission WHERE id=? FOR UPDATE",r.t.communication.toString());
                holder.conservar(r.t.communication,true);locked.countDown();await(release);return null;
            }));
            await(locked);
            var retire=pool.submit(()->{attempted.countDown();assertThrows(IOException.class,()->worker.retirar(r.t.communication));});
            try{await(attempted);assertThrows(TimeoutException.class,()->retire.get(150,TimeUnit.MILLISECONDS));}
            finally{release.countDown();}
            hold.get(3,TimeUnit.SECONDS);retire.get(3,TimeUnit.SECONDS);r.t.intact();assertTrue(r.t.reserved()>0);assertNotEquals("RETIRANDO",phase());
        }
    }
    @Test void holdGanaDespuesDeVerificarPeroAntesDeLaFronteraIrreversible()throws Exception{
        prepared();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var other=connection();var pool=Executors.newSingleThreadExecutor()){
            var worker=motor(r.t.f.db,(p,id)->{if(p.equals("ANTES_RETIRANDO")){entered.countDown();await(release);}});
            var future=pool.submit(()->assertThrows(IOException.class,()->worker.retirar(r.t.communication)));
            try{await(entered);motor(other,(p,id)->{}).conservar(r.t.communication,true);}finally{release.countDown();}
            future.get(3,TimeUnit.SECONDS);r.t.intact();assertTrue(r.t.reserved()>0);
        }
    }
    @Test void holdEnLaMismaInstanciaNoEsperaUnMonitorDuranteLaVerificacion()throws Exception{
        prepared();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var worker=motor(r.t.f.db,(p,id)->{if(p.equals("ANTES_RETIRANDO")){entered.countDown();await(release);}});
        try(var pool=Executors.newFixedThreadPool(2)){
            var retire=pool.submit(()->assertThrows(IOException.class,()->worker.retirar(r.t.communication)));
            try{await(entered);var hold=pool.submit(()->worker.conservar(r.t.communication,true));hold.get(2,TimeUnit.SECONDS);}finally{release.countDown();}
            retire.get(3,TimeUnit.SECONDS);r.t.intact();
        }
    }
    @Test void fronteraConfirmadaRechazaHoldAntesDelPrimerBorradoSinTransaccionLarga()throws Exception{
        prepared();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var other=connection();var pool=Executors.newSingleThreadExecutor()){
            var worker=motor(r.t.f.db,(p,id)->{if(p.equals("RETIRANDO")){assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());entered.countDown();await(release);}});
            var future=pool.submit(()->{worker.retirar(r.t.communication);return null;});
            try{await(entered);assertTrue(Files.exists(r.t.f.store.original(r.t.attachment)));assertEquals("RETIRANDO",phase());assertEquals(409,assertThrows(FalloTecnico.class,()->motor(other,(p,id)->{}).conservar(r.t.communication,true)).status);}
            finally{release.countDown();}
            future.get(3,TimeUnit.SECONDS);assertEquals("LIBERADA",phase());assertEquals(409,assertThrows(FalloTecnico.class,()->motor(other,(p,id)->{}).conservar(r.t.communication,true)).status);
            motor(other,(p,id)->{}).verificar(r.t.communication);assertEquals("LIBERADA",phase());assertEquals(0,r.t.reserved());
        }
    }
    @Test void falloTrasUnlinkYReinicioConVerificacionNoRetrocedeNiDuplicaReserva()throws Exception{
        prepared();long reserved=r.t.reserved();
        var interrupted=motor(r.t.f.db,(p,id)->{if(p.equals("ARCHIVOS_RETIRADOS"))throw new IllegalStateException("Caída sintética");});
        assertThrows(IllegalStateException.class,()->interrupted.retirar(r.t.communication));assertEquals("RETIRANDO",phase());assertEquals(reserved,r.t.reserved());assertFalse(Files.exists(r.t.f.store.original(r.t.attachment)));
        try(var other=connection()){
            var restarted=motor(other,(p,id)->{});restarted.verificar(r.t.communication);assertEquals("RETIRANDO",phase());assertEquals(409,assertThrows(FalloTecnico.class,()->restarted.conservar(r.t.communication,true)).status);
            restarted.retirar(r.t.communication);restarted.retirar(r.t.communication);assertEquals("LIBERADA",phase());assertEquals(0,r.t.reserved());
            assertEquals(1,r.t.f.db.sql.queryForObject("SELECT COUNT(*) FROM operational_event WHERE code='RETIRADA_FINALIZADA'",Integer.class));
        }
        var expected=r.t.reception.consultar(r.t.communication,r.t.f.session);assertEquals(1,r.app.ejecutar().minimizadas());
        assertEquals(expected,r.t.reception.consultar(r.t.communication,r.t.f.session));assertEquals(expected,r.t.reception.recibir(r.t.communication,r.t.f.journey,r.t.f.session,r.data()));
        motor(r.t.f.db,(p,id)->{}).verificar(r.t.communication);motor(r.t.f.db,(p,id)->{}).retirar(r.t.communication);assertEquals("MINIMIZADA",phase());
        assertEquals(409,assertThrows(FalloTecnico.class,()->r.app.conservar("COMPLETO",r.t.communication,true)).status);
    }
    @Test void garantiaQueVenceEnLaFronteraNoIniciaRetirada()throws Exception{
        prepared();
        var worker=motor(r.t.f.db,(p,id)->{if(p.equals("ANTES_RETIRANDO"))r.t.now.addAndGet(Duration.ofHours(25).toMillis());}).prueba((e,a)->r.proofs.comprobar(r.t.signed,r.t.ackText,"COMPLETO"));
        assertThrows(IOException.class,()->worker.retirar(r.t.communication));assertEquals("VERIFICADA",phase());r.t.intact();
    }
    @Test void cuarentenaNoSeRetiraAunqueTengaAckYGarantia()throws Exception{
        prepared();r.t.f.db.sql.update("UPDATE citizen_submission SET state='CUARENTENA' WHERE id=?",r.t.communication.toString());assertEquals(0,r.app.ejecutar().minimizadas());r.t.intact();
    }
    @Test void verificacionFallidaDuranteRetiradaMantieneLaFaseYLaCopiaPendiente()throws Exception{
        prepared();assertThrows(IllegalStateException.class,()->motor(r.t.f.db,(p,id)->{if(p.equals("RETIRANDO"))throw new IllegalStateException("Interrupción sintética");}).retirar(r.t.communication));
        Files.writeString(r.t.external(r.t.restored),"alterado");assertThrows(IOException.class,()->motor(r.t.f.db,(p,id)->{}).verificar(r.t.communication));assertEquals("RETIRANDO",phase());r.t.intact();
    }
    UUID historical()throws Exception{
        var id=UUID.randomUUID();var identity=RecepcionJdbcTest.IDENTITY;var legacy=new RecepcionJdbc(r.t.f.db,r.t.f.mapper,"TEST",true,"publico",r.t.publicKey.getPrivate(),Map.of("publico",r.t.publicKey.getPublic()),r.t.clock,300000,Map.of("institucion",r.t.institutional.getPublic()),r.ownerKey);
        var body=legacy.recibirVerificada(id,identity,new cl.nexbyte.siger.identidad.EvidenciaAutenticacion(UUID.randomUUID(),r.t.clock.instant(),cl.nexbyte.siger.identidad.EvidenciaAutenticacion.de(identity).identidadHash()),"Relato histórico sintético","TEST");var lease=legacy.reclamar(1).getFirst();var e=r.t.f.mapper.readValue(r.t.f.db.sql.queryForObject("SELECT body FROM public_delivery WHERE id=?",String.class,id.toString()),EntregaVerificada.class);
        var content=new ComprobanteInstitucional.Contenido("1.0","SIGER_DDN","COMUNICACION_REGISTRADA",id,UUID.randomUUID(),1,"COM-1",r.t.clock.instant(),"FORMULARIO_WEB_PUBLICO",body.sha256(),EntregaVerificada.huella(e.procedencia()));legacy.confirmarRegistro(id,lease.reserva(),new RegistroInstitucional(content,ComprobanteInstitucional.firmar(content,"institucion",r.t.institutional.getPrivate(),r.t.f.mapper)));return id;
    }
    @Test void historicoHoldConcurrenteSerializaMinimizacionYDespuesDevuelveConflicto()throws Exception{
        var id=historical();r.app=r.service(true);r.app.ejecutar();r.t.due();r.proofs.aceptar(r.proof(id,"HISTORICO"));var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var attempted=new CountDownLatch(1);
        try(var other=connection();var pool=Executors.newFixedThreadPool(2)){
            var control=new CustodiaTransitoria(other,r.t.f.store,false,r.t.f.mapper,r.proofs,Map.of("publico",r.t.publicKey.getPublic()),Map.of("institucion",r.t.institutional.getPublic()),"publico",r.t.publicKey.getPrivate(),r.ownerKey,r.t.clock,new CicloVidaCustodia.Politica(true,Duration.ofHours(72),Duration.ofHours(48)));
            var hold=pool.submit(()->other.transaction(()->{other.sql.queryForMap("SELECT id FROM public_delivery WHERE id=? FOR UPDATE",id.toString());control.conservar("HISTORICO",id,true);locked.countDown();await(release);return null;}));await(locked);
            var retire=pool.submit(()->{attempted.countDown();return r.app.ejecutar();});
            try{await(attempted);assertThrows(TimeoutException.class,()->retire.get(150,TimeUnit.MILLISECONDS));}finally{release.countDown();}
            hold.get(3,TimeUnit.SECONDS);assertEquals(0,retire.get(3,TimeUnit.SECONDS).minimizadas());assertNotNull(r.t.f.db.sql.queryForObject("SELECT body FROM public_delivery WHERE id=?",String.class,id.toString()));
            control.conservar("HISTORICO",id,false);assertEquals(1,r.app.ejecutar().minimizadas());assertEquals(409,assertThrows(FalloTecnico.class,()->control.conservar("HISTORICO",id,true)).status);
        }
    }
}