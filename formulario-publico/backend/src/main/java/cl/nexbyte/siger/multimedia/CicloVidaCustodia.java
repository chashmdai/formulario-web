package cl.nexbyte.siger.multimedia;

import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.robustez.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.BiConsumer;
import tools.jackson.databind.json.JsonMapper;

/** Motor reanudable: CLI offline V9 y coordinador online V10. Nunca retira por el mero ACK. */
public final class CicloVidaCustodia {
    public record Politica(boolean eliminar, Duration postAck, Duration abandono) {
        public Politica {
            if (postAck == null || abandono == null || postAck.compareTo(Duration.ofHours(24)) < 0 ||
                abandono.compareTo(Duration.ofHours(24)) < 0 || postAck.compareTo(Duration.ofDays(365)) > 0 || abandono.compareTo(Duration.ofDays(365)) > 0)
                throw new IllegalArgumentException("Retenciones entre 24 horas y 365 días requeridas");
        }
    }
    public record Resultado(int revisadas, int liberadas, int bloqueadas) {}
    private final BaseJdbc db;
    private final AlmacenPrivado store;
    public interface Prueba {void verificar(EntregaFormulario entrega,RegistroInstitucional ack)throws IOException;}
    private Prueba prueba;
    private int batch=1000;
    public CicloVidaCustodia lotesAcotados(int max){if(max<1||max>1000)throw new IllegalArgumentException("Lote inválido");batch=max;return this;}
    public CicloVidaCustodia prueba(Prueba p){this.prueba=Objects.requireNonNull(p);return this;}
    private final ArchivoCustodiaIndependiente archive;
    private final JsonMapper mapper;
    private final Map<String,PublicKey> publicKeys, institutionalKeys;
    private final Clock clock;
    private final Politica policy;
    private final BiConsumer<String,UUID> checkpoint;

    public CicloVidaCustodia(BaseJdbc db, AlmacenPrivado store, ArchivoCustodiaIndependiente archive, JsonMapper mapper,
            Map<String,PublicKey> publicKeys, Map<String,PublicKey> institutionalKeys, Clock clock, Politica policy) {
        this(db, store, archive, mapper, publicKeys, institutionalKeys, clock, policy, (p,id)->{});
    }
    public CicloVidaCustodia(BaseJdbc db, AlmacenPrivado store, ArchivoCustodiaIndependiente archive, JsonMapper mapper,
            Map<String,PublicKey> publicKeys, Map<String,PublicKey> institutionalKeys, Clock clock, Politica policy, BiConsumer<String,UUID> checkpoint) {
        this.db=db; this.store=store; this.archive=archive; this.mapper=mapper; this.publicKeys=Map.copyOf(publicKeys);
        this.institutionalKeys=Map.copyOf(institutionalKeys); this.clock=clock; this.policy=policy; this.checkpoint=checkpoint;
        if (institutionalKeys.isEmpty() || institutionalKeys.values().stream().anyMatch(publicKeys.values()::contains))
            throw new IllegalArgumentException("Confianza institucional separada requerida");
    }
    private static long number(Map<String,Object> row,String name) { return ((Number)row.get(name)).longValue(); }
    private void accounting() {
        db.transaction(()->{long counter=((Number)db.sql.queryForMap("SELECT reserved_bytes FROM media_capacity WHERE id=1 FOR UPDATE").get("reserved_bytes")).longValue();
        long sum=db.sql.queryForObject("SELECT COALESCE(SUM(reserved_bytes),0) FROM media_upload WHERE released=FALSE",Long.class);
        if(counter<0 || counter!=sum) throw new IllegalStateException("Contabilidad inconsistente; conservar bytes");return null;});
    }
    private void enabled() { if (!policy.eliminar()) throw new IllegalStateException("Eliminación deshabilitada"); }
    private String digest(String value) { return CanonFormulario.huella("CICLO_VIDA_LOCAL", value, mapper); }
    private Map<String,Object> submission(UUID id) { return db.sql.queryForMap("SELECT * FROM citizen_submission WHERE id=?",id.toString()); }
    private EntregaFormulario verified(UUID id, Map<String,Object> row) {
        var e=mapper.readValue(row.get("body").toString(),EntregaFormulario.class); e.validar(publicKeys,mapper);
        if (!id.equals(e.comunicacion().id()) || !"CONFIRMADA".equals(row.get("state")) || row.get("ack")==null)
            throw new IllegalArgumentException("Transferencia no confirmada");
        e.validarRegistro(mapper.readValue(row.get("ack").toString(),RegistroInstitucional.class),institutionalKeys,mapper);
        return e;
    }
    private Map<String,Object> lifecycle(UUID id) { return db.sql.queryForMap("SELECT * FROM custody_lifecycle WHERE submission_id=?",id.toString()); }

    /** Registra primera observación válida; nunca retrodata el plazo de documentos históricos. No elimina. */
    public synchronized void verificar(UUID id) throws IOException {
        var row=submission(id); var e=verified(id,row); String ackHash=digest(row.get("ack").toString());
        db.transaction(()->{
            var current=db.sql.queryForList("SELECT * FROM custody_lifecycle WHERE submission_id=? FOR UPDATE",id.toString());
            if(current.isEmpty()) {
                db.sql.update("INSERT INTO custody_lifecycle(submission_id,observed_ms,due_ms,ack_sha256,phase) VALUES(?,?,?,?,'OBSERVADA')",
                    id.toString(),clock.millis(),Math.addExact(clock.millis(),policy.postAck().toMillis()),ackHash);
                db.event("ALMACENAMIENTO",id.toString(),"ACK_VALIDADO_OBSERVADO");
            } else if (!ackHash.equals(current.getFirst().get("ack_sha256"))) throw new IllegalArgumentException("ACK cambió");
            return null;
        });
        db.sql.update("UPDATE custody_lifecycle SET checked_ms=? WHERE submission_id=?",clock.millis(),id.toString());
        try {
            var state=lifecycle(id);
            if (Boolean.TRUE.equals(state.get("hold_active"))) throw new IOException("Conservación bloqueada");
            if (archive==null && prueba==null) throw new IOException("Falta verificación independiente");
            if(prueba!=null)prueba.verificar(e,mapper.readValue(row.get("ack").toString(),RegistroInstitucional.class));
            var attachments=db.sql.queryForList("SELECT * FROM citizen_attachment WHERE journey_id=?",e.comunicacion().recorridoId().toString());
            if(attachments.size()!=e.comunicacion().adjuntos().size()) throw new IOException("Referencias discrepantes");
            for(var d:e.comunicacion().adjuntos()) {
                var a=attachments.stream().filter(v->d.id().toString().equals(v.get("id"))).findFirst().orElseThrow(()->new IOException("Adjunto no corresponde"));
                if(!d.sha256().equals(a.get("sha256")) || d.tamanio()!=number(a,"size_bytes") || !d.nombre().equals(a.get("name")) || !d.tipo().equals(a.get("media_type")))
                    throw new IOException("Metadatos discrepantes");
                Path local="FILE".equals(a.get("storage_kind"))?store.original(d.id()):null;
                if(local!=null && !d.id().toString().equals(a.get("storage_key"))) throw new IOException("Referencia no canónica");
                if(archive!=null)archive.verificar(id,d,local);
                var intents=db.sql.queryForList("SELECT phase FROM storage_retirement WHERE attachment_id=? AND submission_id=?",d.id().toString(),id.toString());
                boolean inProgress=!intents.isEmpty();
                boolean retired=Boolean.TRUE.equals(a.get("bytes_retired"));
                boolean finalized=inProgress && "FINALIZADA".equals(intents.getFirst().get("phase"));
                if(retired!=finalized) throw new IOException("Marca de retirada sin evidencia concordante");
                if(!retired) {
                    // Solo una intención ya durable permite ausencia local al reanudar una retirada interrumpida.
                    if(local!=null) { if(Files.exists(local,LinkOption.NOFOLLOW_LINKS) || !inProgress) store.verificar(local,d.tamanio(),d.sha256()); }
                    else verifyBlob(d);
                }
            }
            String evidence=CanonFormulario.huella("CUSTODIA_Y_RESTAURACION",List.of(e.sha256(),ackHash,e.comunicacion().adjuntos()),mapper);
            db.sql.update("UPDATE custody_lifecycle SET verified_ms=?,evidence_sha256=?,phase=CASE WHEN phase='LIBERADA' THEN phase ELSE 'VERIFICADA' END,last_error=NULL WHERE submission_id=?",clock.millis(),evidence,id.toString());
            if(state.get("verified_ms")==null)db.event("ALMACENAMIENTO",id.toString(),"ARCHIVO_Y_RESTAURACION_VERIFICADOS");
        } catch(IOException | RuntimeException failure) {
            var previousError=lifecycle(id).get("last_error");
            db.sql.update("UPDATE custody_lifecycle SET last_error='VERIFICACION_NO_SATISFECHA' WHERE submission_id=?",id.toString());
            if(previousError==null)db.event("ALMACENAMIENTO",id.toString(),"VERIFICACION_BLOQUEADA"); throw failure;
        }
    }
    private void verifyBlob(AdjuntosFormulario.Descriptor d) throws IOException {
        try {
            db.sql.query("SELECT content FROM citizen_attachment WHERE id=?",(org.springframework.jdbc.core.RowCallbackHandler)rs->{
                try(var input=rs.getBinaryStream(1)) {
                    if(input==null) throw new IOException("BLOB ausente");
                    var hash=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];long size=0;int n;
                    while((n=input.read(buffer))!=-1) { size+=n; if(size>d.tamanio()) throw new IOException("BLOB excesivo"); hash.update(buffer,0,n); }
                    if(size!=d.tamanio() || !HexFormat.of().formatHex(hash.digest()).equals(d.sha256())) throw new IOException("BLOB alterado");
                } catch(IOException | GeneralSecurityException error) { throw new java.sql.SQLException("BLOB no íntegro",error); }
            },d.id().toString());
        } catch(org.springframework.dao.DataAccessException failure) { throw new IOException("BLOB no íntegro"); }
    }

    /** Vuelve a leer TODOS los originales externos en cada intento, además de validar firmas y plazo. */
    public synchronized void retirar(UUID id) throws IOException {
        enabled(); accounting(); verificar(id); var s=lifecycle(id);
        if(Boolean.TRUE.equals(s.get("hold_active")) || Math.max(number(s,"due_ms"),Math.addExact(number(s,"observed_ms"),policy.postAck().toMillis()))>clock.millis()) throw new IOException("Conservación temporal vigente");
        var e=verified(id,submission(id));
        db.sql.update("UPDATE custody_lifecycle SET phase='RETIRANDO' WHERE submission_id=?",id.toString());
        for(var d:e.comunicacion().adjuntos()) retire(d.id(),e.comunicacion().recorridoId(),id,"CUSTODIA_INSTITUCIONAL");
        db.sql.update("UPDATE custody_lifecycle SET phase='LIBERADA',last_error=NULL WHERE submission_id=?",id.toString());
        db.event("ALMACENAMIENTO",id.toString(),"COPIA_TRANSITORIA_LIBERADA");
    }
    private void retire(UUID attachment, UUID journey, UUID submission, String reason) throws IOException {
        var previous=db.sql.queryForList("SELECT * FROM storage_retirement WHERE attachment_id=?",attachment.toString());
        if(!previous.isEmpty() && "FINALIZADA".equals(previous.getFirst().get("phase"))) return;
        if(previous.isEmpty()) db.transaction(()->{
            db.sql.update("INSERT INTO storage_retirement(attachment_id,journey_id,submission_id,reason_code,phase,started_ms) VALUES(?,?,?,?,'INTENCION',?)",attachment.toString(),journey.toString(),submission==null?null:submission.toString(),reason,clock.millis());
            db.event("ALMACENAMIENTO",attachment.toString(),"RETIRADA_INICIADA");return null;
        });
        else if(!reason.equals(previous.getFirst().get("reason_code")) || !Objects.equals(submission==null?null:submission.toString(),previous.getFirst().get("submission_id"))) throw new IOException("Intención distinta");
        checkpoint.accept("INTENCION",attachment);
        // Borrado lógico seguro del nombre interno y fsync; no se promete borrado físico de celdas SSD/snapshots.
        var ownedUploads=db.sql.queryForList("SELECT journey_id FROM media_upload WHERE id=?",attachment.toString());
        var ownedAttachments=db.sql.queryForList("SELECT journey_id,storage_kind,storage_key FROM citizen_attachment WHERE id=?",attachment.toString());
        for(var owned:ownedUploads) if(!journey.toString().equals(owned.get("journey_id"))) throw new IOException("Propiedad discrepante");
        for(var owned:ownedAttachments) if(!journey.toString().equals(owned.get("journey_id"))) throw new IOException("Propiedad discrepante");
        if(!ownedUploads.isEmpty() || !ownedAttachments.isEmpty() && "FILE".equals(ownedAttachments.getFirst().get("storage_kind"))) {
            if(!ownedAttachments.isEmpty() && "FILE".equals(ownedAttachments.getFirst().get("storage_kind")) && !attachment.toString().equals(ownedAttachments.getFirst().get("storage_key"))) throw new IOException("Referencia discrepante");
            store.borrarNoRecibido(attachment);
        }
        checkpoint.accept("ARCHIVOS_RETIRADOS",attachment);
        db.transaction(()->{
            var cap=db.sql.queryForMap("SELECT reserved_bytes FROM media_capacity WHERE id=1 FOR UPDATE");
            var intent=db.sql.queryForMap("SELECT phase FROM storage_retirement WHERE attachment_id=? FOR UPDATE",attachment.toString());
            if("FINALIZADA".equals(intent.get("phase"))) return null;
            var uploads=db.sql.queryForList("SELECT reserved_bytes,released FROM media_upload WHERE id=? FOR UPDATE",attachment.toString());
            if(!uploads.isEmpty() && !Boolean.TRUE.equals(uploads.getFirst().get("released"))) {
                long release=number(uploads.getFirst(),"reserved_bytes");
                if(number(cap,"reserved_bytes")<release) throw new IllegalStateException("Reserva inconsistente");
                db.sql.update("UPDATE media_capacity SET reserved_bytes=reserved_bytes-? WHERE id=1",release);
                db.sql.update("UPDATE media_upload SET released=TRUE WHERE id=?",attachment.toString());
            }
            db.sql.update("DELETE FROM media_chunk WHERE upload_id=?",attachment.toString());
            db.sql.update("UPDATE citizen_attachment SET content=NULL,bytes_retired=TRUE WHERE id=?",attachment.toString());
            db.sql.update("UPDATE storage_retirement SET phase='FINALIZADA',finished_ms=? WHERE attachment_id=?",clock.millis(),attachment.toString());
            db.event("ALMACENAMIENTO",attachment.toString(),"RETIRADA_FINALIZADA");return null;
        });
        checkpoint.accept("FINALIZADA",attachment);
    }

    /** Cancela borradores vencidos con margen adicional. Nunca toca archivos de un recorrido recibido. */
    public synchronized int abandonar(boolean apply) throws IOException {
        if(apply) {enabled(); accounting();} int total=0;
        var rows=db.sql.queryForList("SELECT id FROM citizen_journey j WHERE state IN ('BORRADOR','CANCELADO') AND expires_ms<=? AND NOT EXISTS(SELECT 1 FROM citizen_submission s WHERE s.journey_id=j.id) ORDER BY expires_ms,id LIMIT "+batch,clock.millis()-policy.abandono().toMillis());
        for(var row:rows) {
            UUID journey=UUID.fromString(row.get("id").toString());
            try(var exclusive=store.mantenimiento(journey)) {if(exclusive==null)continue;
            var uploads=db.sql.queryForList("SELECT id FROM media_upload WHERE journey_id=? AND released=FALSE",journey.toString());
            var attachments=db.sql.queryForList("SELECT id FROM citizen_attachment WHERE journey_id=? AND bytes_retired=FALSE",journey.toString());
            Set<UUID> ids=new HashSet<>(); for(var u:uploads) ids.add(UUID.fromString(u.get("id").toString())); for(var a:attachments) ids.add(UUID.fromString(a.get("id").toString()));
            total+=ids.size(); if(!apply || ids.isEmpty()) continue;
            db.transaction(()->{
                db.sql.queryForMap("SELECT id FROM citizen_journey WHERE id=? FOR UPDATE",journey.toString());
                if(db.sql.queryForObject("SELECT COUNT(*) FROM citizen_submission WHERE journey_id=?",Integer.class,journey.toString())!=0) throw new IllegalStateException("Recorrido recibido");
                db.sql.update("UPDATE citizen_journey SET state='CANCELADO' WHERE id=?",journey.toString());
                db.sql.update("UPDATE media_upload SET state='EXPIRADO',lease_until_ms=0 WHERE journey_id=? AND released=FALSE",journey.toString());
                return null;
            });
            for(var id:ids) retire(id,journey,null,"BORRADOR_VENCIDO");
            }
        }
        // Cargas retiradas/no incluidas, incluso si otro archivo del mismo recorrido ya fue recibido.
        for(var u:db.sql.queryForList("SELECT id,journey_id FROM media_upload u WHERE released=FALSE AND expires_ms<=? AND NOT EXISTS(SELECT 1 FROM citizen_attachment a WHERE a.id=u.id) AND EXISTS(SELECT 1 FROM citizen_submission s WHERE s.journey_id=u.journey_id) ORDER BY expires_ms,id LIMIT "+batch,clock.millis()-policy.abandono().toMillis())) {
            UUID id=UUID.fromString(u.get("id").toString()), journey=UUID.fromString(u.get("journey_id").toString());
            try(var exclusive=store.mantenimiento(journey)){if(exclusive==null)continue;
            boolean referenced=false;
            for(var received:db.sql.queryForList("SELECT id,body,minimal_receipt FROM citizen_submission WHERE journey_id=?",journey.toString())) {
                if(received.get("body")==null){
                    var minimal=cl.nexbyte.siger.custodia.ReciboMinimo.validar(received.get("minimal_receipt").toString(),UUID.fromString(received.get("id").toString()),publicKeys,institutionalKeys,mapper);
                    if(minimal.archivos().stream().anyMatch(d->id.equals(d.id())))referenced=true;
                    continue;
                }
                var e=mapper.readValue(received.get("body").toString(),EntregaFormulario.class); e.validar(publicKeys,mapper);
                if(e.comunicacion().adjuntos().stream().anyMatch(d->id.equals(d.id()))) referenced=true;
            }
            if(referenced) continue; // una referencia perdida no autoriza eliminación
            total++;
            if(apply) {
                db.sql.update("UPDATE media_upload SET state='EXPIRADO',lease_until_ms=0 WHERE id=?",id.toString());
                retire(id,journey,null,"CARGA_NO_INCLUIDA");
            }
            }
        }
        return total;
    }

    /** Bloques redundantes: original íntegro primero; ajusta reserva al original + derivada, sin tocar contenido. */
    public synchronized int compactarReservas(boolean apply) throws IOException {
        if(apply) {enabled(); accounting();} int count=0;
        for(var u:db.sql.queryForList("SELECT * FROM media_upload WHERE state='VALIDADO' AND released=FALSE AND reserved_bytes>expected_size+COALESCE(preview_size,0) ORDER BY created_ms,id LIMIT "+batch)) {
            UUID id=UUID.fromString(u.get("id").toString());
            try(var exclusive=store.mantenimiento(UUID.fromString(u.get("journey_id").toString()))){if(exclusive==null)continue;
            store.verificar(store.original(id),number(u,"expected_size"),u.get("expected_sha256").toString());
            long preview=u.get("preview_size")==null?0:number(u,"preview_size");
            if(preview>0) store.verificar(store.derivada(id),preview,u.get("preview_sha256").toString());
            long actual=Math.addExact(number(u,"expected_size"),preview), old=number(u,"reserved_bytes");
            if(old<actual) throw new IOException("Reserva menor que bytes durables");
            count++; if(!apply) continue;
            store.borrarBloques(id);
            db.transaction(()->{
                var cap=db.sql.queryForMap("SELECT reserved_bytes FROM media_capacity WHERE id=1 FOR UPDATE");
                var live=db.sql.queryForMap("SELECT reserved_bytes FROM media_upload WHERE id=? FOR UPDATE",id.toString());
                long release=number(live,"reserved_bytes")-actual;
                if(release<0 || number(cap,"reserved_bytes")<release) throw new IllegalStateException("Contabilidad inconsistente");
                if(release>0) {
                    db.sql.update("UPDATE media_capacity SET reserved_bytes=reserved_bytes-? WHERE id=1",release);
                    db.sql.update("UPDATE media_upload SET reserved_bytes=? WHERE id=?",actual,id.toString());
                    db.event("ALMACENAMIENTO",id.toString(),"RESERVA_AJUSTADA_A_BYTES_DURABLES");
                }
                return null;
            });
            }
        }
        return count;
    }
    public synchronized void conservar(UUID id, boolean hold) {
        if(db.sql.update("UPDATE custody_lifecycle SET hold_active=? WHERE submission_id=?",hold,id.toString())!=1) throw new IllegalArgumentException("Primero observar ACK válido");
        db.event("ALMACENAMIENTO",id.toString(),hold?"CONSERVACION_BLOQUEADA":"CONSERVACION_DESBLOQUEADA");
    }
    public Resultado procesar(int limit, boolean apply) {
        if(limit<1 || limit>20) throw new IllegalArgumentException("Lote de 1 a 20 requerido");
        if(apply) enabled(); int checked=0,done=0,blocked=0;
        for(String value:db.sql.queryForList("SELECT s.id FROM citizen_submission s LEFT JOIN custody_lifecycle c ON c.submission_id=s.id WHERE s.state='CONFIRMADA' AND (c.phase IS NULL OR c.phase<>'LIBERADA') ORDER BY COALESCE(c.checked_ms,0),s.created_ms,s.id LIMIT ?",String.class,limit)) {
            checked++;
            try { if(apply) {retirar(UUID.fromString(value));done++;} else verificar(UUID.fromString(value)); }
            catch(IOException | RuntimeException failure) {blocked++;} // el detalle queda en eventos/estado; jamás imprimir datos o excepción JDBC
        }
        return new Resultado(checked,done,blocked);
    }
}