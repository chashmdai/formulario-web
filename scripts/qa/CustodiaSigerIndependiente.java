package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.multimedia.*;
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
        System.out.println("CUSTODIA_SIGER_INDEPENDIENTE_OK: 1.0/1.1, COMMIT, ACK, retirada pública, reinicio institucional y recuperación íntegra");
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
        UUID id=UUID.randomUUID();receiver.recibir(id,f.journey,f.session,new DatosFormulario(Map.of("nna-region","13","nna-primary-method","unsafe","nna-story","Relato sintético"),List.of(),List.of(),List.of(attachment)));
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
        var proof=new ArchivoCustodiaIndependiente(institutional,restoration,f.store.raiz());
        var policy=new CicloVidaCustodia.Politica(true,Duration.ofHours(72),Duration.ofHours(48));
        var initial=new CicloVidaCustodia(f.db,f.store,proof,f.mapper,Map.of("publico",pub.getPublic()),Map.of("institucion",ins.getPublic()),f.clock,policy);
        initial.verificar(id);
        var later=Clock.offset(f.clock,Duration.ofDays(4));
        new CicloVidaCustodia(f.db,f.store,proof,f.mapper,Map.of("publico",pub.getPublic()),Map.of("institucion",ins.getPublic()),later,policy).retirar(id);
        require(!Files.exists(f.store.original(attachment)));
        require(f.db.sql.queryForObject("SELECT content FROM citizen_attachment WHERE id=?",byte[].class,attachment.toString())==null);
        // Se cierra y vacía la BD pública sintética; SIGER no depende de ella para recuperar nada.
        f.close();archive.close();idb.close();
        idb=new cl.nexbyte.siger.institucional.robustez.BaseJdbc(dbUrl,"sa","",4);archive=new ArchivoInstitucional(institutional);
        institutionalReceiver=new RecepcionInstitucionalJdbc(idb,mapper,Map.of("publico",pub.getPublic()),"institucion",ins.getPrivate(),Map.of("institucion",ins.getPublic()),"COM",6,f.clock);
        var stored=institutionalReceiver.completaRegistrada(receipt.registro().comunicacionId());
        require(stored.sha256().equals(e.sha256()));require(institutionalReceiver.registroCompletoConservado(e,archive).equals(receipt));
        for(var d:stored.adjuntos()) {require(archive.disponible(id,d));require(Arrays.equals(f.text(),Files.readAllBytes(archive.verificar(archive.key(id,d.id()),d))));}
        archive.close();idb.close();
    }
}