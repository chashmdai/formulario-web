package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.multimedia.*;
import cl.nexbyte.siger.custodia.*;
import cl.nexbyte.siger.institucional.formulario.ArchivoInstitucional;
import cl.nexbyte.siger.institucional.formulario.EntregaCompleta;
import cl.nexbyte.siger.institucional.robustez.RecepcionInstitucionalJdbc;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;

/** QA opt-in: usa clases existentes del JAR SIGER, sin editar ni arrancar ese repositorio/servicio. */
public final class CustodiaSigerIndependiente {
    public static void main(String[] args) throws Exception {
        if(System.getenv("ROBUST_DB_URL")!=null)throw new IllegalStateException("Solo H2 sintético privado");
        for(boolean blob:List.of(false,true)) run(blob);
        System.out.println("CUSTODIA_SIGER_INDEPENDIENTE_OK: 1.0/1.1, COMMIT, ACK, restauracion SQL, respaldo-restauracion automatica, garantia firmada, minimizacion publica, recibo y recuperacion institucional integra");
    }
    private static void require(boolean ok){if(!ok)throw new AssertionError("Garantía de custodia no satisfecha");}
    private static void run(boolean blob) throws Exception {
        Path root=Files.createTempDirectory("siger-retencion-sintetica-");
        var f=new MultimediaJdbcTest();f.temp=root;f.setup();
        Path institutional=Files.createDirectory(root.resolve("institucional"));Path restoration=Files.createDirectory(root.resolve("restaurado"));Files.createDirectory(restoration.resolve("objects"));
        var pub=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();var ins=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String dbUrl="jdbc:h2:file:"+root.resolve("institucional-db")+";DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0";
        var idb=new cl.nexbyte.siger.institucional.robustez.BaseJdbc(dbUrl,"sa","",4);idb.migrate("institucional");
        idb.sql.update("UPDATE operation_control SET accept_new=TRUE,pull_enabled=TRUE,contingency='NORMAL'");
        var archive=new ArchivoInstitucional(institutional);
        UUID attachment;
        var publicFiles=new AdjuntosFormulario(f.db,f.journeys,b->true,f.store);
        if(blob)attachment=publicFiles.reemplazar(f.journey,f.session,List.of(new AdjuntosFormulario.Archivo("sintetico.txt",f.text()))).getFirst().id();
        else {var state=f.upload(f.text());f.publish(state.id());attachment=state.id();}
        var receiver=new RecepcionFormularioJdbc(f.db,f.journeys,publicFiles,f.mapper,new ValidacionFormulario(new TerritorioFormulario(f.mapper)),"publico",pub.getPrivate(),Map.of("publico",pub.getPublic()),Map.of("institucion",ins.getPublic()),f.clock,true);
        UUID id=UUID.randomUUID();receiver.recibir(id,f.journey,f.session,new DatosFormulario(Map.of("nna-region","13","nna-primary-method","unsafe","nna-story","S".repeat(5000)),List.of(),List.of(),List.of(attachment)));
        var lease=receiver.reclamar(1,Set.of("1.0","1.1")).getFirst();
        var mapper=EntregaCompleta.lector();var e=mapper.readValue(f.mapper.writeValueAsString(lease.entrega()),EntregaCompleta.class);
        var institutionalReceiver=new RecepcionInstitucionalJdbc(idb,mapper,Map.of("publico",pub.getPublic()),"institucion",ins.getPrivate(),Map.of("institucion",ins.getPublic()),"COM",6,f.clock);
        // Transferencia desde la API de recurso público; SIGER conserva su propio original durable.
        for(var d:e.adjuntos()) {
            var resource=receiver.recursoReservado(id,lease.reserva(),d.id());
            var bytes=new ByteArrayOutputStream();resource.copiar(bytes,0,resource.tamanio());
            archive.guardar(id,d,new ByteArrayInputStream(bytes.toByteArray()));
        }
        var receipt=institutionalReceiver.guardarCompleta(e,archive); // vuelve después del COMMIT
        receipt=institutionalReceiver.registroCompletoConservado(e,archive);
        receiver.confirmar(id,lease.reserva(),f.mapper.readValue(mapper.writeValueAsString(receipt),RegistroInstitucional.class));
        var directory=Files.createDirectory(restoration.resolve("objects").resolve(id.toString()));
        for(var d:e.adjuntos())Files.copy(archive.verificar(archive.key(id,d.id()),d),directory.resolve(d.id()+".bin"));
        // Restauración SQL real (H2) del registro COM, documento completo y ACK, además de originales.
        var snapshot=root.resolve("institucional-snapshot.sql");idb.sql.execute("SCRIPT TO '"+snapshot+"'");
        var restoredDb=new BaseJdbc("jdbc:h2:file:"+root.resolve("restored-db")+";DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0","sa","",4);
        restoredDb.sql.execute("RUNSCRIPT FROM '"+snapshot+"'");
        var issuer=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var autoBackup=Files.createTempDirectory(Path.of("/dev/shm"),"sigerguar-ingresos-qa-");var autoScratch=Files.createDirectory(root.resolve("scratch-verificador"));
        restoredDb.sql.update("UPDATE communication_registry SET content_hash=? WHERE operacion_id=?","0".repeat(64),id.toString());
        boolean rejected=false;
        try{EmisorGarantiaCustodia.emitir(idb.sql,institutional,restoredDb.sql,restoration,id,"COMPLETO",Map.of("publico",pub.getPublic()),Map.of("institucion",ins.getPublic()),"issuer",issuer.getPrivate(),f.clock,f.mapper);}catch(IllegalArgumentException|IOException expected){rejected=true;}
        require(rejected);restoredDb.sql.update("UPDATE communication_registry SET content_hash=? WHERE operacion_id=?",e.sha256(),id.toString());

        var policy=new CicloVidaCustodia.Politica(true,Duration.ofHours(72),Duration.ofHours(48));f.media.close();
        var guaranteeRepo=new GarantiasCustodiaJdbc(f.db,f.mapper,Map.of("publico",pub.getPublic()),Map.of("institucion",ins.getPublic()),Map.of("issuer",issuer.getPublic()),f.clock);
        guaranteeRepo.aceptar(RespaldoIngresoVerificado.emitir(idb.sql,institutional,autoBackup,autoScratch,id,"COMPLETO",Map.of("publico",pub.getPublic()),Map.of("institucion",ins.getPublic()),"issuer",issuer.getPrivate(),f.clock,f.mapper));
        var initial=new CustodiaTransitoria(f.db,f.store,false,f.mapper,guaranteeRepo,Map.of("publico",pub.getPublic()),Map.of("institucion",ins.getPublic()),"publico",pub.getPrivate(),new byte[32],f.clock,policy);
        initial.ejecutar();initial.close();var later=Clock.offset(f.clock,Duration.ofDays(4));
        guaranteeRepo=new GarantiasCustodiaJdbc(f.db,f.mapper,Map.of("publico",pub.getPublic()),Map.of("institucion",ins.getPublic()),Map.of("issuer",issuer.getPublic()),later);
        // La garantía anterior vence: nueva lectura de COM/restauración, no prolongación ciega del ACK.
        guaranteeRepo.aceptar(RespaldoIngresoVerificado.emitir(idb.sql,institutional,autoBackup,autoScratch,id,"COMPLETO",Map.of("publico",pub.getPublic()),Map.of("institucion",ins.getPublic()),"issuer",issuer.getPrivate(),later,f.mapper));
        var expectedState=receiver.consultar(id,f.session);
        try(var service=new CustodiaTransitoria(f.db,f.store,false,f.mapper,guaranteeRepo,Map.of("publico",pub.getPublic()),Map.of("institucion",ins.getPublic()),"publico",pub.getPrivate(),new byte[32],later,policy)) {require(service.ejecutar().minimizadas()==1);}
        require(f.db.sql.queryForObject("SELECT body FROM citizen_submission WHERE id=?",String.class,id.toString())==null);
        require(f.db.sql.queryForObject("SELECT COUNT(*) FROM citizen_person",Integer.class)==0);
        require(receiver.consultar(id,f.session).equals(expectedState));restoredDb.close();
        require(!Files.exists(f.store.original(attachment)));
        require(f.db.sql.queryForObject("SELECT content FROM citizen_attachment WHERE id=?",byte[].class,attachment.toString())==null);
        // Se cierra y vacía la BD pública sintética; SIGER no depende de ella para recuperar nada.
        f.close();archive.close();idb.close();
        idb=new cl.nexbyte.siger.institucional.robustez.BaseJdbc(dbUrl,"sa","",4);archive=new ArchivoInstitucional(institutional);
        institutionalReceiver=new RecepcionInstitucionalJdbc(idb,mapper,Map.of("publico",pub.getPublic()),"institucion",ins.getPrivate(),Map.of("institucion",ins.getPublic()),"COM",6,f.clock);
        var stored=institutionalReceiver.completaRegistrada(receipt.registro().comunicacionId());
        require(stored.sha256().equals(e.sha256()));require(stored.comunicacion().equals(e.comunicacion()));require(institutionalReceiver.registroCompletoConservado(e,archive).equals(receipt));
        for(var d:stored.adjuntos()) {require(archive.disponible(id,d));require(Arrays.equals(f.text(),Files.readAllBytes(archive.verificar(archive.key(id,d.id()),d))));}
        archive.close();idb.close();
    }
}