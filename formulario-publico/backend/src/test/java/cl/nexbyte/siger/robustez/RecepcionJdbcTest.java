package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.identidad.*;
import cl.nexbyte.siger.entrega.SobreComunicacion;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class RecepcionJdbcTest {
    @TempDir Path directory;
    BaseJdbc db;RecepcionJdbc store;KeyPair keys,institutionKeys;ControlesOperacion controls;
    final JsonMapper mapper=JsonMapper.builder().build();
    static final IdentidadVerificada IDENTITY=new IdentidadVerificada("44444444-4","Ciudadana","Prueba");
    static final String AVISO="laboratorio-v1";
    @BeforeEach void prepare()throws Exception {
        institutionKeys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        db=TestDatabase.open(directory);keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();controls=new ControlesOperacion(db);
        store=new RecepcionJdbc(db,mapper,AVISO,true,"test-ed25519-v1",keys.getPrivate(),Map.of("test-ed25519-v1",keys.getPublic()),Clock.systemUTC(),300000,Map.of("institution-test",institutionKeys.getPublic()));
        controls.cambiar(controls.estado().revision(),true,true,"NORMAL","PRUEBA_LOCAL");
    }
    @AfterEach void close(){if(db!=null)db.close();}
    @Test void persisteComponentesFirmadosYConservaComprobanteTrasReintentoYLectura(){
        var identity=IdentidadVerificada.from(Map.of("RolUnico",Map.of("numero",44444444,"DV","4","tipo","RUN"),"name",Map.of("nombres",List.of("María José","De los ángeles","Tercero","Cuarto"),"apellidos",List.of("Del Río","González de la Fuente"))));
        var proof=EvidenciaAutenticacion.de(identity);var id=UUID.randomUUID();
        var sobre=store.recibirVerificada(id,identity,proof,"Componentes sintéticos",AVISO);
        assertThat(sobre.version()).isEqualTo("1.1");assertThat(sobre.identidad().nombresComponentes()).isEqualTo(identity.nombresComponentes());
        var saved=mapper.readValue(db.sql.queryForObject("SELECT body FROM public_delivery WHERE id=?",String.class,id.toString()),EntregaVerificada.class);
        saved.validar(Map.of("test-ed25519-v1",keys.getPublic()),mapper);
        assertThat(saved.comunicacion().identidad().apellidosComponentes()).isEqualTo(identity.apellidosComponentes());
        var lease=store.reclamar(1).getFirst();var receipt=registration(sobre);store.confirmarRegistro(id,lease.reserva(),receipt);
        assertThat(store.recibirVerificada(id,identity,proof,"Componentes sintéticos",AVISO)).isEqualTo(sobre);
        assertThat(store.comprobante(id,identity.run())).isEqualTo(receipt.comprobante());
    }
    SobreComunicacion receive(UUID id,String text){return store.recibirVerificada(id,IDENTITY,EvidenciaAutenticacion.de(IDENTITY),text,AVISO);}
    ComprobanteDigital custody(UUID id){return mapper.readValue(db.sql.queryForObject("SELECT body FROM public_delivery WHERE id=?",String.class,id.toString()),EntregaVerificada.class).comprobante();}
    RegistroInstitucional registration(SobreComunicacion sobre){
        var delivery=mapper.readValue(db.sql.queryForObject("SELECT body FROM public_delivery WHERE id=?",String.class,sobre.id().toString()),EntregaVerificada.class);
        var content=new ComprobanteInstitucional.Contenido("1.0","SIGER_DDN","COMUNICACION_REGISTRADA",sobre.id(),UUID.randomUUID(),1,"COM-000001",Instant.now(),"FORMULARIO_WEB_PUBLICO",sobre.sha256(),EntregaVerificada.huella(delivery.procedencia()));
        return new RegistroInstitucional(content,ComprobanteInstitucional.firmar(content,"institution-test",institutionKeys.getPrivate(),mapper));
    }
    @Test void soloElRegistroInstitucionalFirmadoHabilitaComprobanteYFolio(){
        var id=UUID.randomUUID();var sobre=receive(id,"Aún sin SIGER");var pending=store.consultar(id,IDENTITY.run());
        assertThat(pending.estado()).isEqualTo("PENDIENTE_REGISTRO_SIGER");assertThat(pending.comprobante()).isNull();assertThat(pending.registroInstitucional()).isNull();
        assertThatThrownBy(()->store.comprobante(id,IDENTITY.run())).isInstanceOf(FalloTecnico.class).extracting(x->((FalloTecnico)x).status).isEqualTo(409);
        var lease=store.reclamar(1).getFirst();var ack=registration(sobre);
        var forged=new RegistroInstitucional(ack.registro(),ComprobanteInstitucional.firmar(ack.registro(),"institution-test",keys.getPrivate(),mapper));
        assertThatThrownBy(()->store.confirmarRegistro(id,lease.reserva(),forged)).isInstanceOf(FalloTecnico.class);
        assertThatThrownBy(()->store.confirmarReserva(id,lease.reserva(),new SobreComunicacion.Confirmacion(UUID.randomUUID(),Instant.now(),sobre.sha256()))).isInstanceOf(FalloTecnico.class);
        store.confirmarRegistro(id,lease.reserva(),ack);store.confirmarRegistro(id,UUID.randomUUID(),ack);
        assertThat(store.consultar(id,IDENTITY.run()).registroInstitucional()).isEqualTo(ack);
        assertThat(store.comprobante(id,IDENTITY.run())).isEqualTo(ack.comprobante());
        assertThat(receive(id,"Aún sin SIGER")).isEqualTo(sobre);
        var another=UUID.randomUUID();receive(another,"Otra");var anotherLease=store.reclamar(1).getFirst();
        assertThatThrownBy(()->store.confirmarRegistro(another,anotherLease.reserva(),ack)).isInstanceOf(FalloTecnico.class);
    }
    @Test void idempotenciaConcurrenteUnSoloRegistroEventoYComprobante()throws Exception {
        var id=UUID.randomUUID();var gate=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(24)){
            var futures=new ArrayList<Future<ComprobanteDigital>>();
            for(int i=0;i<72;i++)futures.add(executor.submit(()->{gate.await();receive(id,"Mismo ingreso");return custody(id);}));
            gate.countDown();var expected=futures.getFirst().get(30,TimeUnit.SECONDS);
            for(var f:futures)assertThat(f.get(30,TimeUnit.SECONDS)).isEqualTo(expected);
        }
        assertThat(db.sql.queryForObject("SELECT COUNT(*) FROM public_delivery WHERE id=?",Long.class,id.toString())).isEqualTo(1);
        assertThat(db.sql.queryForObject("SELECT COUNT(*) FROM operational_event WHERE aggregate_id=? AND code='CUSTODIA_CONFIRMADA'",Long.class,id.toString())).isEqualTo(1);
        var original=custody(id);
        assertThat(original.verificar(Map.of("test-ed25519-v1",keys.getPublic()),mapper).tipoRecepcion()).isEqualTo("CUSTODIA_PUBLICA");
        assertThatThrownBy(()->receive(id,"Alterado")).isInstanceOf(FalloTecnico.class).extracting(e->((FalloTecnico)e).status).isEqualTo(409);
    }
    @Test void cierreDeIngresoNoRompeReintentosConsultaNiComprobantes() {
        var id=UUID.randomUUID();var original=receive(id,"  café\r\nMensaje  ");
        assertThat(original.mensaje()).isEqualTo("café\nMensaje");
        var receipt=custody(id);controls.cambiar(controls.estado().revision(),false,false,"SOLO_CONSULTA","INCIDENTE");
        assertThat(store.disponible()).isFalse();assertThat(receive(id,"cafe\u0301\nMensaje")).isEqualTo(original);
        assertThat(custody(id)).isEqualTo(receipt);
        assertThatThrownBy(()->receive(UUID.randomUUID(),"Nuevo")).isInstanceOf(FalloTecnico.class).extracting(e->((FalloTecnico)e).status).isEqualTo(503);
        assertThatThrownBy(()->store.consultar(id,"11111111-1")).isInstanceOf(FalloTecnico.class).extracting(e->((FalloTecnico)e).status).isEqualTo(404);
        var rotated=new RecepcionJdbc(db,mapper,"laboratorio-v2",true,"test-ed25519-v1",keys.getPrivate(),Map.of("test-ed25519-v1",keys.getPublic()),Clock.systemUTC(),300000,Map.of("institution-test",institutionKeys.getPublic()));
        assertThat(rotated.recibirVerificada(id,IDENTITY,EvidenciaAutenticacion.de(IDENTITY),"café\nMensaje",AVISO)).isEqualTo(original);
    }
    @Test void integridadProcedenciaYFirmaNoAdmitenAtributosElevadosNiClienteSuplantado() {
        var id=UUID.randomUUID();var sobre=receive(id,"Mensaje declarado");
        var saved=mapper.readValue(db.sql.queryForObject("SELECT body FROM public_delivery WHERE id=?",String.class,id.toString()),EntregaVerificada.class);
        saved.validar(Map.of("test-ed25519-v1",keys.getPublic()),mapper);
        assertThat(saved.procedencia().get("mensaje").nivel()).isEqualTo("DECLARADO_NO_CONTRASTADO");
        var changed=new HashMap<>(saved.procedencia());changed.put("mensaje",changed.get("run"));
        assertThatThrownBy(()->new EntregaVerificada("2.0",sobre,changed,saved.comprobante()).validar(Map.of("test-ed25519-v1",keys.getPublic()),mapper)).isInstanceOf(IllegalArgumentException.class);
        var other=new IdentidadVerificada("11111111-1","Otra","Persona");
        assertThatThrownBy(()->store.recibirVerificada(UUID.randomUUID(),other,EvidenciaAutenticacion.de(IDENTITY),"Mensaje",AVISO)).isInstanceOf(FalloTecnico.class);
        assertThatThrownBy(()->receive(UUID.randomUUID(),"\uD800")).isInstanceOf(FalloTecnico.class);
        assertThatThrownBy(()->receive(UUID.randomUUID(),"Texto\u0000inyectado")).isInstanceOf(FalloTecnico.class);
        assertThatThrownBy(()->saved.comprobante().verificar(Map.of(),mapper)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void reservaVencidaNoPuedeConfirmarNuevaReservaYACKEsIdempotente(){
        var id=UUID.randomUUID();var sobre=receive(id,"Reservas");
        var first=store.reclamar(1).getFirst();
        assertThat(store.reclamar(1)).isEmpty();
        db.sql.update("UPDATE public_delivery SET lease_until_ms=0 WHERE id=?",id.toString());
        var second=store.reclamar(1).getFirst();assertThat(second.reserva()).isNotEqualTo(first.reserva());
        var ack=registration(sobre);
        assertThatThrownBy(()->store.confirmarRegistro(id,first.reserva(),ack)).isInstanceOf(FalloTecnico.class);
        controls.cambiar(controls.estado().revision(),false,false,"SOLO_CONSULTA","MANTENIMIENTO");
        store.confirmarRegistro(id,second.reserva(),ack);store.confirmarRegistro(id,second.reserva(),ack);
        assertThat(store.consultar(id,IDENTITY.run()).estado()).isEqualTo("REGISTRADA_SIGER");
    }
    @Test void corrupcionSeAislaYReintentosNoDuplicanEventos(){
        var damaged=UUID.randomUUID();receive(damaged,"Alterar este mensaje");
        var healthy=UUID.randomUUID();receive(healthy,"Mensaje intacto");
        var json=db.sql.queryForObject("SELECT body FROM public_delivery WHERE id=?",String.class,damaged.toString());
        db.sql.update("UPDATE public_delivery SET body=? WHERE id=?",json.replace("Alterar este mensaje","Manipulado"),damaged.toString());
        var claims=store.reclamar(20);assertThat(claims).hasSize(1);assertThat(claims.getFirst().entrega().comunicacion().id()).isEqualTo(healthy);
        assertThat(db.sql.queryForObject("SELECT state FROM public_delivery WHERE id=?",String.class,damaged.toString())).isEqualTo("CUARENTENA");
        assertThatThrownBy(()->store.reponer(damaged,1,"CORRECCION_ADAPTADOR")).isInstanceOf(FalloTecnico.class);
        var lease=claims.getFirst().reserva();store.fallo(healthy,lease,"RECEPTOR_NO_DISPONIBLE");store.fallo(healthy,lease,"RECEPTOR_NO_DISPONIBLE");
        assertThat(db.sql.queryForObject("SELECT attempts FROM public_delivery WHERE id=?",Integer.class,healthy.toString())).isEqualTo(1);
        assertThat(store.reclamar(20)).isEmpty();
    }
    @Test void reintentoOperacionalSoloConservaDatosIntegrosYNoConfirmaEntrega(){
        var id=UUID.randomUUID();receive(id,"Reponer después de revisar contrato");var item=store.reclamar(1).getFirst();
        store.fallo(id,item.reserva(),"CONTRATO_INVALIDO");store.reponer(id,1,"CORRECCION_ADAPTADOR");
        assertThat(store.consultar(id,IDENTITY.run()).estado()).isEqualTo("PENDIENTE_REGISTRO_SIGER");
        assertThat(store.reclamar(1)).hasSize(1);
        assertThatThrownBy(()->store.reponer(id,1,"CORRECCION_ADAPTADOR")).isInstanceOf(FalloTecnico.class);
    }
    @Test void registroFirmadoDeOtraFilaNoPuedeSustituirLaRecepcion(){
        var first=UUID.randomUUID();var second=UUID.randomUUID();receive(first,"Primero");receive(second,"Segundo");
        var body=db.sql.queryForObject("SELECT body FROM public_delivery WHERE id=?",String.class,second.toString());
        db.sql.update("UPDATE public_delivery SET body=? WHERE id=?",body,first.toString());
        assertThatThrownBy(()->store.consultar(first,IDENTITY.run())).isInstanceOf(FalloTecnico.class);
        assertThatThrownBy(()->receive(first,"Primero")).isInstanceOf(FalloTecnico.class);
    }
    @Test void reclamacionesConcurrentesNoEntreganLaMismaReservaADosTrabajadores()throws Exception{
        for(int i=0;i<40;i++)receive(UUID.randomUUID(),"Concurrente "+i);
        var all=new ArrayList<RecepcionDurable.EntregaReservada>();
        try(var threads=Executors.newFixedThreadPool(4)){
            var gate=new CountDownLatch(1);var tasks=new ArrayList<Future<List<RecepcionDurable.EntregaReservada>>>();
            for(int i=0;i<4;i++)tasks.add(threads.submit(()->{gate.await();return store.reclamar(20);}));gate.countDown();
            for(var task:tasks)all.addAll(task.get(30,TimeUnit.SECONDS));
        }
        assertThat(all).hasSize(40);assertThat(all.stream().map(x->x.entrega().comunicacion().id()).distinct().count()).isEqualTo(40);
        assertThat(all).allMatch(x->x.intento()==1);
    }
    @Test void ningunaConfirmacionPublicaCuandoLaTransaccionFallaAntesDelCommit(){
        var id=UUID.randomUUID();
        TestDatabase.migration(db,sql->sql.execute("ALTER TABLE operational_event ADD CONSTRAINT reject_event CHECK(code <> 'CUSTODIA_CONFIRMADA')"));
        try{
            assertThatThrownBy(()->receive(id,"Debe deshacerse completo")).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(db.sql.queryForObject("SELECT COUNT(*) FROM public_delivery WHERE id=?",Long.class,id.toString())).isZero();
        }finally{TestDatabase.migration(db,sql->sql.execute("ALTER TABLE operational_event DROP "+(System.getenv("ROBUST_DB_URL")==null?"CONSTRAINT":"CHECK")+" reject_event"));}
        receive(id,"Debe deshacerse completo");assertThat(custody(id)).isNotNull();
    }
    @Test void controlesConCASYNotificacionAmbiguaNuncaSeReenviaCiegamente(){
        var version=controls.estado().revision();controls.cambiar(version,false,false,"SOLO_CONSULTA","INCIDENTE");
        assertThatThrownBy(()->controls.cambiar(version,true,true,"NORMAL","REANUDACION")).isInstanceOf(FalloTecnico.class);
        var registry=new RegistroOperacionesExternas(db);var id=UUID.randomUUID();
        var prepared=registry.preparar(id,"NOTIFICACION");assertThat(registry.preparar(id,"NOTIFICACION")).isEqualTo(prepared);
        var requested=registry.transicion(id,prepared.revision(),"SOLICITADA",null,"ENVIO_INICIADO");
        var unknown=registry.transicion(id,requested.revision(),"DESCONOCIDA",null,"TIMEOUT_AMBIGUO");
        assertThatThrownBy(()->registry.transicion(id,unknown.revision(),"SOLICITADA",null,"ENVIO_INICIADO")).isInstanceOf(FalloTecnico.class);
        var reconciled=registry.transicion(id,unknown.revision(),"CONFIRMADA","proveedor-test-1","CONSULTA_CONFIRMADA");
        assertThat(reconciled.estado()).isEqualTo("CONFIRMADA");
    }
    @Test void generaVectorDeContratoV2SinVersionarClavePrivada()throws Exception{
        var id=UUID.fromString("22222222-2222-4222-8222-222222222222");receive(id,"Vector sintético de contrato v2");
        var json=db.sql.queryForObject("SELECT body FROM public_delivery WHERE id=?",String.class,id.toString());
        Files.writeString(Path.of("target/entrega-v2-generada.json"),json);
        var pem="-----BEGIN PUBLIC KEY-----\n"+Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(keys.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----\n";
        Files.writeString(Path.of("target/receipt-public-test.json"),mapper.writeValueAsString(Map.of("test-ed25519-v1",pem)));
    }

 static final cl.nexbyte.siger.contrato.DeclaracionNna NNA=new cl.nexbyte.siger.contrato.DeclaracionNna("María José",null,"Del Río",null,null,null," Calle  sintética ",null,"Región sintética");
 @Test void nnaConcurrenteSinRunYRollbackConservaIdempotencia()throws Exception{
  var id=UUID.randomUUID();String owner="a".repeat(64);
  TestDatabase.migration(db,sql->sql.execute("ALTER TABLE operational_event ADD CONSTRAINT reject_nna CHECK(code <> 'CUSTODIA_CONFIRMADA')"));
  try{assertThatThrownBy(()->store.recibirNna(id,NNA,"Relato NNA",AVISO,owner)).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(db.sql.queryForObject("SELECT COUNT(*) FROM public_delivery",Long.class)).isZero();}
  finally{TestDatabase.migration(db,sql->sql.execute("ALTER TABLE operational_event DROP "+(System.getenv("ROBUST_DB_URL")==null?"CONSTRAINT":"CHECK")+" reject_nna"));}
  try(var threads=Executors.newFixedThreadPool(12)){
   var tasks=new ArrayList<Future<SobreComunicacion>>();for(int i=0;i<36;i++)tasks.add(threads.submit(()->store.recibirNna(id,NNA,"Relato NNA",AVISO,owner)));
   var first=tasks.getFirst().get(30,TimeUnit.SECONDS);for(var t:tasks)assertThat(t.get(30,TimeUnit.SECONDS)).isEqualTo(first);
  }
  assertThat(db.sql.queryForObject("SELECT COUNT(*) FROM public_delivery",Long.class)).isEqualTo(1);
  assertThatThrownBy(()->store.recibirNna(id,NNA,"Cambio",AVISO,owner)).isInstanceOf(FalloTecnico.class);
  assertThatThrownBy(()->store.consultarSesion(id,"b".repeat(64))).isInstanceOf(FalloTecnico.class);
  assertThat(store.reclamar(1).getFirst().entrega().comunicacion().declaracionNna()).isEqualTo(NNA);
 }
 @Test void nnaProcedenciaNoPuedeElevarseYExportaVectorInteroperable()throws Exception{
  var id=UUID.fromString("33333333-3333-4333-8333-333333333333");var nnaStore=new RecepcionJdbc(db,mapper,AVISO,true,"nna-fixture-v1",keys.getPrivate(),Map.of("nna-fixture-v1",keys.getPublic()),Clock.systemUTC(),300000);var body=nnaStore.recibirNna(id,NNA,"Relato  sintético\ncon tildes",AVISO,"a".repeat(64));
  var saved=mapper.readValue(db.sql.queryForObject("SELECT body FROM public_delivery WHERE id=?",String.class,id.toString()),EntregaVerificada.class);
  saved.validar(Map.of("nna-fixture-v1",keys.getPublic()),mapper);
  var changed=new HashMap<>(saved.procedencia());changed.put("primerNombre",new EntregaVerificada.Atributo("CLAVE_UNICA_USERINFO","IDENTIDAD_AUTENTICADA",body.recibidaEn(),id));
  assertThatThrownBy(()->new EntregaVerificada("2.1",body,changed,saved.comprobante()).validar(Map.of("nna-fixture-v1",keys.getPublic()),mapper)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new EntregaVerificada("2.0",body,saved.procedencia(),saved.comprobante())).isInstanceOf(IllegalArgumentException.class);
  Files.writeString(Path.of("target/entrega-nna-v2.1.json"),mapper.writeValueAsString(saved));
  Files.writeString(Path.of("target/public-nna-test.json"),mapper.writeValueAsString(Map.of("nna-fixture-v1","-----BEGIN PUBLIC KEY-----\n"+Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(keys.getPublic().getEncoded())+"\n-----END PUBLIC KEY-----\n")));
 }
}
