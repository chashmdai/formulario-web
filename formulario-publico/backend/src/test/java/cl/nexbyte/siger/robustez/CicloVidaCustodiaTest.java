package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.multimedia.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Custodia, restauración, bytes y firmantes exclusivamente sintéticos. No conecta al SIGER real. */
class CicloVidaCustodiaTest {
    @TempDir Path temp;
    MultimediaJdbcTest f;
    Path institution, restored;
    ArchivoCustodiaIndependiente archive;
    KeyPair publicKey,institutional;
    UUID communication, attachment;
    RecepcionFormularioJdbc reception;
    RecepcionFormularioJdbc.Reserva lease;
    String signed,ackText;
    AtomicLong now=new AtomicLong(Instant.parse("2026-10-25T00:00:00Z").toEpochMilli());
    final Clock clock=new Clock() {
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return Instant.ofEpochMilli(now.get());}
    };
    @BeforeEach void setup() throws Exception {
        f=new MultimediaJdbcTest(); f.temp=temp; f.setup();
        f.db.sql.update("DELETE FROM storage_retirement");f.db.sql.update("DELETE FROM custody_lifecycle");
        institution=Files.createDirectory(temp.resolve("siger"));Files.createDirectory(institution.resolve("objects"));
        restored=Files.createDirectory(temp.resolve("restauracion"));Files.createDirectory(restored.resolve("objects"));
        archive=new ArchivoCustodiaIndependiente(institution,restored,f.store.raiz());
        publicKey=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();institutional=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }
    @AfterEach void close() {
        if(f!=null && f.db!=null){f.db.sql.update("DELETE FROM storage_retirement");f.db.sql.update("DELETE FROM custody_lifecycle");f.close();}
    }
    CicloVidaCustodia service(boolean enabled) {return service(enabled,(p,id)->{});}
    CicloVidaCustodia service(boolean enabled, java.util.function.BiConsumer<String,UUID> checkpoint) {
        f.media.close();
        return new CicloVidaCustodia(f.db,f.store,archive,f.mapper,Map.of("publico",publicKey.getPublic()),Map.of("institucion",institutional.getPublic()),clock,
            new CicloVidaCustodia.Politica(enabled,Duration.ofHours(72),Duration.ofHours(48)),checkpoint);
    }
    void receive(boolean blob) throws Exception {
        var files=new AdjuntosFormulario(f.db,f.journeys,b->true,f.store);
        if(blob) attachment=files.reemplazar(f.journey,f.session,List.of(new AdjuntosFormulario.Archivo("sintetico.txt",f.text()))).getFirst().id();
        else {var upload=f.upload(f.text());f.publish(upload.id());attachment=upload.id();}
        reception=new RecepcionFormularioJdbc(f.db,f.journeys,files,f.mapper,new ValidacionFormulario(new TerritorioFormulario(f.mapper)),"publico",publicKey.getPrivate(),Map.of("publico",publicKey.getPublic()),Map.of("institucion",institutional.getPublic()),f.clock,true);
        communication=UUID.randomUUID();var data=new DatosFormulario(Map.of("nna-region","13","nna-primary-method","unsafe","nna-story","Relato sintético"),List.of(),List.of(),List.of(attachment));
        reception.recibir(communication,f.journey,f.session,data);
        lease=reception.reclamar(1,Set.of("1.0","1.1")).getFirst();
        for(var root:List.of(institution,restored)) {
            var dir=Files.createDirectory(root.resolve("objects").resolve(communication.toString()));Files.write(dir.resolve(attachment+".bin"),f.text());
        }
        var registered=new ComprobanteInstitucional.Contenido("1.1","SIGER_DDN","COMUNICACION_REGISTRADA",communication,UUID.randomUUID(),1,"COM-1",f.clock.instant(),"FORMULARIO_WEB_NNA",lease.entrega().sha256(),lease.entrega().huellaProcedencia());
        var ack=new RegistroInstitucional(registered,ComprobanteInstitucional.firmar(registered,"institucion",institutional.getPrivate(),f.mapper));
        reception.confirmar(communication,lease.reserva(),ack);
        signed=f.db.sql.queryForObject("SELECT body FROM citizen_submission WHERE id=?",String.class,communication.toString());
        ackText=f.db.sql.queryForObject("SELECT ack FROM citizen_submission WHERE id=?",String.class,communication.toString());
    }
    void due() {now.addAndGet(Duration.ofHours(73).toMillis());}
    Path external(Path root){return root.resolve("objects").resolve(communication.toString()).resolve(attachment+".bin");}
    long reserved(){return f.db.sql.queryForObject("SELECT reserved_bytes FROM media_capacity WHERE id=1",Long.class);}
    void intact() throws Exception {
        assertArrayEquals(f.text(),Files.readAllBytes(f.store.original(attachment)));
        assertEquals(signed,f.db.sql.queryForObject("SELECT body FROM citizen_submission WHERE id=?",String.class,communication.toString()));
        assertEquals(ackText,f.db.sql.queryForObject("SELECT ack FROM citizen_submission WHERE id=?",String.class,communication.toString()));
    }
    @Test void ackSoloNoAutorizaYLaEliminacionEstaCerradaPorDefecto() throws Exception {
        receive(false);service(false).verificar(communication);due();
        assertThrows(IllegalStateException.class,()->service(false).retirar(communication));intact();assertTrue(reserved()>0);
        Files.delete(external(restored));assertThrows(IOException.class,()->service(true).retirar(communication));intact();
    }
    @Test void cumpleRetencionYConservaMetadatosFirmasYRecuperacionIndependiente() throws Exception {
        receive(false);long before=reserved();service(true).verificar(communication);
        assertThrows(IOException.class,()->service(true).retirar(communication));assertEquals(before,reserved());
        due();service(true).retirar(communication);assertFalse(Files.exists(f.store.original(attachment)));assertEquals(0,reserved());
        service(true).retirar(communication);assertEquals(0,reserved());
        assertEquals(signed,f.db.sql.queryForObject("SELECT body FROM citizen_submission WHERE id=?",String.class,communication.toString()));
        assertEquals(ackText,f.db.sql.queryForObject("SELECT ack FROM citizen_submission WHERE id=?",String.class,communication.toString()));
        assertTrue(f.db.sql.queryForObject("SELECT bytes_retired FROM citizen_attachment WHERE id=?",Boolean.class,attachment.toString()));
        assertEquals(1,f.db.sql.queryForObject("SELECT COUNT(*) FROM operational_event WHERE code='RETIRADA_FINALIZADA'",Integer.class));
        // No se necesita el original público: ambos archivos externos y exportación siguen íntegros.
        var descriptor=lease.entrega().comunicacion().adjuntos().getFirst();var destination=temp.resolve("recuperado.bin");
        archive.recuperar(communication,descriptor,destination);assertArrayEquals(f.text(),Files.readAllBytes(destination));
        assertArrayEquals(f.text(),Files.readAllBytes(external(institution)));
        assertEquals(410,assertThrows(FalloTecnico.class,()->new AdjuntosFormulario(f.db,f.journeys,b->true,f.store).recursoInterno(f.journey,attachment)).status);
        assertEquals("REGISTRADA_SIGER",reception.consultar(communication,f.session).estado());
    }
    @Test void firmasAjenasEIdentidadDeReferenciaBloqueanAntesDeBorrar() throws Exception {
        receive(false);var other=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var wrong=new CicloVidaCustodia(f.db,f.store,archive,f.mapper,Map.of("publico",publicKey.getPublic()),Map.of("institucion",other.getPublic()),clock,new CicloVidaCustodia.Politica(true,Duration.ofDays(3),Duration.ofDays(2)));
        assertThrows(IllegalArgumentException.class,()->wrong.verificar(communication));intact();
        f.db.sql.update("UPDATE citizen_attachment SET storage_key=? WHERE id=?",UUID.randomUUID().toString(),attachment.toString());
        assertThrows(IOException.class,()->service(true).verificar(communication));assertTrue(Files.exists(f.store.original(attachment)));
    }
    @Test void corrupcionInstitucionalORestauracionInvalidaBloquean() throws Exception {
        receive(false);service(true).verificar(communication);due();Files.writeString(external(institution),"corrupto");
        assertThrows(IOException.class,()->service(true).retirar(communication));intact();
        Files.write(external(institution),f.text());Files.writeString(external(restored),"corrupto");
        assertThrows(IOException.class,()->service(true).retirar(communication));intact();
    }
    @Test void hardlinksYAliasNoAcreditanIndependencia() throws Exception {
        receive(false);Files.delete(external(restored));Files.createLink(external(restored),f.store.original(attachment));
        assertThrows(IOException.class,()->service(true).verificar(communication));intact();
        assertThrows(IOException.class,()->new ArchivoCustodiaIndependiente(institution,institution,f.store.raiz()));
        assertThrows(IOException.class,()->archive.calificarMontajes(f.store.raiz())); // temporales de test, no medios reales calificados
    }
    @Test void interrupcionTrasBorrarArchivosReanudaYLiberaReservaUnaSolaVez() throws Exception {
        receive(false);service(true).verificar(communication);due();long before=reserved();
        var failing=service(true,(point,id)->{if(point.equals("ARCHIVOS_RETIRADOS"))throw new IllegalStateException("Caída sintética");});
        assertThrows(IllegalStateException.class,()->failing.retirar(communication));assertFalse(Files.exists(f.store.original(attachment)));assertEquals(before,reserved());
        assertEquals("INTENCION",f.db.sql.queryForObject("SELECT phase FROM storage_retirement WHERE attachment_id=?",String.class,attachment.toString()));
        // Recrea servicio: intención persistida en BD, no una bandera en memoria.
        service(true).retirar(communication);service(true).retirar(communication);assertEquals(0,reserved());
    }
    @Test void ausenciaLocalSinIntencionNoSeConfundeConRetiradaCorrecta() throws Exception {
        receive(false);service(true).verificar(communication);due();Files.delete(f.store.original(attachment));
        assertThrows(IOException.class,()->service(true).retirar(communication));assertTrue(reserved()>0);
        assertEquals(0,f.db.sql.queryForObject("SELECT COUNT(*) FROM storage_retirement",Integer.class));
    }
    @Test void retencionExcepcionalBloqueaYSeAudita() throws Exception {
        receive(false);service(true).verificar(communication);due();service(true).conservar(communication,true);
        assertThrows(IOException.class,()->service(true).retirar(communication));intact();service(true).conservar(communication,false);service(true).retirar(communication);assertEquals(0,reserved());
    }
    @Test void blobHistoricoSeLiberaSoloDespuesDeCopiasVerificadasSinCambiarDocumento() throws Exception {
        receive(true);service(true).verificar(communication);due();service(true).retirar(communication);
        assertNull(f.db.sql.queryForObject("SELECT content FROM citizen_attachment WHERE id=?",byte[].class,attachment.toString()));
        assertEquals(signed,f.db.sql.queryForObject("SELECT body FROM citizen_submission WHERE id=?",String.class,communication.toString()));
        assertEquals(ackText,f.db.sql.queryForObject("SELECT ack FROM citizen_submission WHERE id=?",String.class,communication.toString()));
        assertArrayEquals(f.text(),Files.readAllBytes(external(restored)));assertEquals(0,reserved());
    }
    @Test void contabilidadInconsistenteNoProvocaBorradoNiCorreccionSilenciosa() throws Exception {
        receive(false);service(true).verificar(communication);due();f.db.sql.update("UPDATE media_capacity SET reserved_bytes=reserved_bytes+1 WHERE id=1");
        assertThrows(IllegalStateException.class,()->service(true).retirar(communication));intact();
    }
    @Test void compactaReservaSinCambiarOriginalYLuegoRetiraExactamenteSaldo() throws Exception {
        receive(false);long before=reserved();assertEquals(1,service(true).compactarReservas(false));assertEquals(before,reserved());
        service(true).compactarReservas(true);assertEquals(f.text().length,reserved());service(true).compactarReservas(true);assertEquals(f.text().length,reserved());intact();
        service(true).verificar(communication);due();service(true).retirar(communication);assertEquals(0,reserved());
    }
    @Test void abandonadosIncluyenValidadoYAnalisisCaidoSinTocarRecibidos() throws Exception {
        var upload=f.upload(f.text());f.publish(upload.id());attachment=upload.id();long before=reserved();
        now.addAndGet(Duration.ofDays(4).toMillis());assertEquals(1,service(true).abandonar(false));assertEquals(before,reserved());assertTrue(Files.exists(f.store.original(attachment)));
        f.db.sql.update("UPDATE media_upload SET state='ANALIZANDO',lease_until_ms=1 WHERE id=?",attachment.toString());
        assertEquals(1,service(true).abandonar(true));assertEquals(0,reserved());assertFalse(Files.exists(f.store.original(attachment)));assertEquals(0,service(true).abandonar(true));
        assertTrue(f.db.sql.queryForObject("SELECT bytes_retired FROM citizen_attachment WHERE id=?",Boolean.class,attachment.toString()));
    }
    @Test void recibidosNoSeEliminanComoBorradoresAunqueNoHayaAck() throws Exception {
        receive(false);f.db.sql.update("UPDATE citizen_submission SET ack=NULL,state='PENDIENTE' WHERE id=?",communication.toString());
        due();assertEquals(0,service(true).abandonar(true));assertTrue(Files.exists(f.store.original(attachment)));assertTrue(reserved()>0);
    }
    @Test void verificaTamanioYNoSobrescribeUnaRecuperacionPrevia() throws Exception {
        receive(false);var d=lease.entrega().comunicacion().adjuntos().getFirst();var dest=temp.resolve("existente.bin");Files.writeString(dest,"conservar");
        assertThrows(IOException.class,()->archive.recuperar(communication,d,dest));assertEquals("conservar",Files.readString(dest));
        Files.delete(external(restored));Files.createSymbolicLink(external(restored),external(institution));assertThrows(IOException.class,()->service(true).verificar(communication));intact();
    }
    @Test void ciclosSucesivosMantienenCeroBytesBinariosYReservasTrasCadaTransferencia() throws Exception {
        long received=0;
        for(int cycle=0;cycle<6;cycle++) {
            if(cycle>0) {
                f.journey=f.journeys.iniciar(IdentificacionCiudadana.Tipo.NNA,false,"NNA_TEST_1",f.session,"127.0.0."+(cycle+2)).id();
                f.journeys.identificar(f.journey,"presentador-nna",IdentificacionCiudadana.Rol.PRESENTADOR,null,null,f.session,"127.0.0.1",null);
                f.journeys.declarar(f.journey,"presentador-nna",Map.of("primerNombre","SINTETICO","primerApellido","PRUEBA"),f.session);
                // El worker se recrea solo para la nueva carga sintética, fuera del mantenimiento.
                f.media=f.service();
            }
            receive(false);received+=f.text().length;service(true).verificar(communication);due();service(true).retirar(communication);
            assertEquals(0,reserved());
            for(var folder:List.of("objects","chunks","incoming","derived"))try(var entries=Files.list(f.store.raiz().resolve(folder))){assertEquals(0,entries.count());}
        }
        assertEquals(6,f.db.sql.queryForObject("SELECT COUNT(*) FROM citizen_submission",Integer.class));
        assertEquals(6,f.db.sql.queryForObject("SELECT COUNT(*) FROM citizen_attachment WHERE bytes_retired=TRUE",Integer.class));
        assertTrue(received>0);
    }
    @Test void interrupcionAntesDeBorrarYDespuesDelCommitNoDuplicaLaLiberacion() throws Exception {
        receive(false);service(true).verificar(communication);due();long before=reserved();
        assertThrows(IllegalStateException.class,()->service(true,(point,id)->{if(point.equals("INTENCION"))throw new IllegalStateException("Caída sintética");}).retirar(communication));
        assertEquals(before,reserved());intact();
        assertThrows(IllegalStateException.class,()->service(true,(point,id)->{if(point.equals("FINALIZADA"))throw new IllegalStateException("Caída sintética");}).retirar(communication));
        assertEquals(0,reserved());service(true).retirar(communication);assertEquals(0,reserved());
    }

    @Test void recorridoRecibidoConFilaPerdidaYMarcaRetiradaSinIntencionSeConservan() throws Exception {
        receive(false);service(true).verificar(communication);due();
        f.db.sql.update("UPDATE citizen_attachment SET bytes_retired=TRUE WHERE id=?",attachment.toString());
        assertThrows(IOException.class,()->service(true).retirar(communication));assertTrue(Files.exists(f.store.original(attachment)));
        f.db.sql.update("UPDATE citizen_attachment SET bytes_retired=FALSE WHERE id=?",attachment.toString());
        f.db.sql.update("DELETE FROM citizen_submission WHERE id=?",communication.toString());
        assertEquals(0,service(true).abandonar(true));assertTrue(Files.exists(f.store.original(attachment)));assertTrue(reserved()>0);
    }

}