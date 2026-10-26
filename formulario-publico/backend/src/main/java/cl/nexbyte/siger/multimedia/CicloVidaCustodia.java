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

/** Motor reanudable: CLI offline V9 y coordinador online V10. Elegibilidad explícita; nunca cambia el significado de una garantía. */
public final class CicloVidaCustodia {
    public enum Modalidad { GARANTIA_INDEPENDIENTE, ACK_RETENCION_LIMITADA_APROBADA }
    public record Politica(boolean eliminar, Duration postAck, Duration abandono, Modalidad modalidad, String aprobacion) {
        public Politica(boolean eliminar, Duration postAck, Duration abandono) {
            this(eliminar,postAck,abandono,Modalidad.GARANTIA_INDEPENDIENTE,"");
        }
        public boolean independiente(){return modalidad==Modalidad.GARANTIA_INDEPENDIENTE;}
        public boolean coincide(Map<String,Object> row){return modalidad.name().equals(row.get("retirement_mode")) && aprobacion.equals(row.get("retirement_policy_ref"));}
        public Politica {
            Objects.requireNonNull(modalidad,"Modalidad requerida");
            if(modalidad==Modalidad.ACK_RETENCION_LIMITADA_APROBADA) {
                if(!Duration.ofHours(72).equals(postAck)||aprobacion==null||!aprobacion.matches("[A-Za-z0-9][A-Za-z0-9_.:/-]{2,99}"))
                    throw new IllegalArgumentException("Modalidad ACK requiere 72 horas y referencia de aprobación");
            } else aprobacion="";
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

    private void sinDiscrepancias(EntregaFormulario e)throws IOException {
        if(!policy.independiente() && e.comunicacion().personas().stream().anyMatch(p->!p.discrepancias().isEmpty()))
            throw new IOException("Discrepancias pendientes; conservar");
    }
    private String evidencia(EntregaFormulario e,String ackHash,long firstAck) {
        return policy.independiente()
            ?CanonFormulario.huella("CUSTODIA_Y_RESTAURACION",List.of(e.sha256(),ackHash,e.comunicacion().adjuntos()),mapper)
            :CanonFormulario.huella("ACK_RETENCION_LIMITADA_APROBADA_1",List.of(e.sha256(),ackHash,firstAck,policy.aprobacion(),e.comunicacion().adjuntos()),mapper);
    }

    /** Solo fechas del servidor. Sin fecha histórica: observar ahora, nunca usar la fecha declarada en el ACK. */
    public synchronized void verificar(UUID id) throws IOException {
        var row=submission(id);
        if(Boolean.TRUE.equals(row.get("content_retired"))) {validarMinimizada(id,row);return;}
        var e=verified(id,row);sinDiscrepancias(e);String ackHash=digest(row.get("ack").toString());
        long firstAck=db.transaction(()->{
            var locked=db.sql.queryForMap("SELECT * FROM citizen_submission WHERE id=? FOR UPDATE",id.toString());
            if(Boolean.TRUE.equals(locked.get("content_retired"))||!Objects.equals(row.get("body"),locked.get("body"))||!Objects.equals(row.get("ack"),locked.get("ack")))throw new IllegalStateException("Custodia cambió; reintentar");
            long first=locked.get("first_ack_ms")==null?clock.millis():number(locked,"first_ack_ms");
            if(first<=0||first>clock.millis())throw new IllegalStateException("Fecha de ACK no acreditada");
            if(locked.get("first_ack_ms")==null) {
                db.sql.update("UPDATE citizen_submission SET first_ack_ms=? WHERE id=?",first,id.toString());
                db.event("ALMACENAMIENTO",id.toString(),"ACK_HISTORICO_OBSERVADO_SIN_RETROFECHAR");
            }
            var current=db.sql.queryForList("SELECT * FROM custody_lifecycle WHERE submission_id=? FOR UPDATE",id.toString());
            long due=Math.addExact(policy.independiente()?clock.millis():first,policy.postAck().toMillis());
            if(current.isEmpty()) {
                db.sql.update("INSERT INTO custody_lifecycle(submission_id,observed_ms,due_ms,ack_sha256,phase,retirement_mode,retirement_policy_ref) VALUES(?,?,?,?,'OBSERVADA',?,?)",
                    id.toString(),clock.millis(),due,ackHash,policy.modalidad().name(),policy.aprobacion());
                db.event("ALMACENAMIENTO",id.toString(),"ACK_VALIDADO_OBSERVADO");
            } else {
                var state=current.getFirst();
                if(!ackHash.equals(state.get("ack_sha256")))throw new IllegalArgumentException("ACK cambió");
                if(!Set.of("OBSERVADA","VERIFICADA").contains(state.get("phase"))) {
                    if(!policy.coincide(state))throw new IllegalStateException("Reanudar con la política de la intención irreversible");
                } else {
                    if(policy.independiente())due=Math.max(number(state,"due_ms"),Math.addExact(number(state,"observed_ms"),policy.postAck().toMillis()));
                    boolean same=policy.coincide(state);
                    db.sql.update("UPDATE custody_lifecycle SET due_ms=?,retirement_mode=?,retirement_policy_ref=?,phase=?,verified_ms=?,evidence_sha256=? WHERE submission_id=?",
                        due,policy.modalidad().name(),policy.aprobacion(),same?state.get("phase"):"OBSERVADA",same?state.get("verified_ms"):null,same?state.get("evidence_sha256"):null,id.toString());
                }
            }
            return first;
        });
        db.sql.update("UPDATE custody_lifecycle SET checked_ms=? WHERE submission_id=?",clock.millis(),id.toString());
        try {
            var state=lifecycle(id);
            if(Boolean.TRUE.equals(state.get("hold_active")))throw new IOException("Conservación bloqueada");
            if(!policy.coincide(state))throw new IOException("Política cambió; conservar");
            if(policy.independiente()) {
                if(archive==null && prueba==null)throw new IOException("Falta verificación independiente");
                if(prueba!=null)prueba.verificar(e,mapper.readValue(row.get("ack").toString(),RegistroInstitucional.class));
            }
            var attachments=db.sql.queryForList("SELECT * FROM citizen_attachment WHERE journey_id=?",e.comunicacion().recorridoId().toString());
            if(attachments.size()!=e.comunicacion().adjuntos().size())throw new IOException("Referencias discrepantes");
            for(var d:e.comunicacion().adjuntos()) {
                var a=attachments.stream().filter(v->d.id().toString().equals(v.get("id"))).findFirst().orElseThrow(()->new IOException("Adjunto no corresponde"));
                if(!d.sha256().equals(a.get("sha256"))||d.tamanio()!=number(a,"size_bytes")||!d.nombre().equals(a.get("name"))||!d.tipo().equals(a.get("media_type")))
                    throw new IOException("Metadatos discrepantes");
                Path local="FILE".equals(a.get("storage_kind"))?store.original(d.id()):null;
                if(local!=null && !d.id().toString().equals(a.get("storage_key")))throw new IOException("Referencia no canónica");
                if(policy.independiente() && archive!=null)archive.verificar(id,d,local);
                var intents=db.sql.queryForList("SELECT phase,reason_code,submission_id FROM storage_retirement WHERE attachment_id=?",d.id().toString());
                boolean inProgress=!intents.isEmpty();
                if(inProgress && (!id.toString().equals(intents.getFirst().get("submission_id")) || !motivoRetirada().equals(intents.getFirst().get("reason_code"))))
                    throw new IOException("Intención no corresponde");
                boolean retired=Boolean.TRUE.equals(a.get("bytes_retired"));
                boolean finalized=inProgress && "FINALIZADA".equals(intents.getFirst().get("phase"));
                if(retired!=finalized)throw new IOException("Marca de retirada sin evidencia concordante");
                if(inProgress && !Set.of("RETIRANDO","LIBERADA").contains(state.get("phase")))throw new IOException("Intención sin frontera irreversible");
                if(!retired) {
                    // Ausencia local solo admisible con intención durable de la misma retirada irreversible.
                    if(local!=null) {if(Files.exists(local,LinkOption.NOFOLLOW_LINKS)||!inProgress)store.verificar(local,d.tamanio(),d.sha256());}
                    else verifyBlob(d);
                }
            }
            String evidence=evidencia(e,ackHash,firstAck);
            if(db.sql.update("UPDATE custody_lifecycle SET verified_ms=?,evidence_sha256=?,phase=CASE WHEN phase='OBSERVADA' THEN 'VERIFICADA' ELSE phase END,last_error=NULL WHERE submission_id=? AND phase<>'MINIMIZADA' AND retirement_mode=? AND retirement_policy_ref=?",
                clock.millis(),evidence,id.toString(),policy.modalidad().name(),policy.aprobacion())!=1)throw new IOException("Política cambió durante verificación");
            if(state.get("verified_ms")==null)db.event("ALMACENAMIENTO",id.toString(),policy.independiente()?"ARCHIVO_Y_RESTAURACION_VERIFICADOS":"ACK_RETENCION_LIMITADA_VERIFICADA");
        } catch(IOException | RuntimeException failure) {
            var previousError=lifecycle(id).get("last_error");
            db.sql.update("UPDATE custody_lifecycle SET last_error='VERIFICACION_NO_SATISFECHA' WHERE submission_id=? AND phase<>'MINIMIZADA'",id.toString());
            if(previousError==null)db.event("ALMACENAMIENTO",id.toString(),"VERIFICACION_BLOQUEADA");throw failure;
        }
    }
    private String motivoRetirada(){return policy.independiente()?"CUSTODIA_INSTITUCIONAL":"ACK_RETENCION_72H";}
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

    private void validarMinimizada(UUID id,Map<String,Object> row) {
        var receipt=cl.nexbyte.siger.custodia.ReciboMinimo.validar(row.get("minimal_receipt").toString(),id,publicKeys,institutionalKeys,mapper);
        if(!"COMPLETO".equals(receipt.cola())||!receipt.recorridoId().toString().equals(row.get("journey_id"))||!"CONFIRMADA".equals(row.get("state"))||!receipt.registro().equals(mapper.readValue(row.get("ack").toString(),RegistroInstitucional.class))||!"MINIMIZADA".equals(lifecycle(id).get("phase")))throw new IllegalStateException("Recibo mínimo incoherente");
    }

    /** Punto irreversible durable, serializado con hold por fila. Ningún filesystem dentro de la transacción. */
    public synchronized void retirar(UUID id) throws IOException {
        enabled(); accounting();
        var initial=submission(id);if(Boolean.TRUE.equals(initial.get("content_retired"))){validarMinimizada(id,initial);return;}
        verificar(id);var e=verified(id,submission(id));
        checkpoint.accept("ANTES_RETIRANDO",id);
        try {db.transaction(()->{
            var row=db.sql.queryForMap("SELECT * FROM citizen_submission WHERE id=? FOR UPDATE",id.toString());
            var state=db.sql.queryForMap("SELECT * FROM custody_lifecycle WHERE submission_id=? FOR UPDATE",id.toString());
            if(Boolean.TRUE.equals(row.get("content_retired"))){validarMinimizada(id,row);return null;}
            var current=verified(id,row);
            if(!e.equals(current)||!digest(row.get("ack").toString()).equals(state.get("ack_sha256")))throw new IllegalStateException("Custodia cambió");
            long due=policy.independiente()?Math.max(number(state,"due_ms"),Math.addExact(number(state,"observed_ms"),policy.postAck().toMillis())):Math.addExact(number(row,"first_ack_ms"),policy.postAck().toMillis());
            if(!policy.coincide(state)||Boolean.TRUE.equals(state.get("hold_active"))||due>clock.millis()||!Set.of("VERIFICADA","RETIRANDO","LIBERADA").contains(state.get("phase")))throw new IllegalStateException("Conservación temporal vigente");
            try{sinDiscrepancias(current);}catch(IOException mismatch){throw new IllegalStateException("Conservar por discrepancia");}
            if(!policy.independiente()&&!evidencia(current,digest(row.get("ack").toString()),number(row,"first_ack_ms")).equals(state.get("evidence_sha256")))throw new IllegalStateException("Evidencia de política distinta");
            // La garantía puede haber vencido durante la verificación de archivos; comprobar en la frontera.
            if(policy.independiente()&&prueba!=null)try{prueba.verificar(current,mapper.readValue(row.get("ack").toString(),RegistroInstitucional.class));}catch(IOException ex){throw new IllegalStateException("Conservar: garantía no vigente");}
            if("VERIFICADA".equals(state.get("phase"))) {
                db.sql.update("UPDATE custody_lifecycle SET phase='RETIRANDO' WHERE submission_id=?",id.toString());
                db.event("ALMACENAMIENTO",id.toString(),"RETIRADA_IRREVERSIBLE_INICIADA");
                if(!policy.independiente())db.event("ALMACENAMIENTO",id.toString(),"RETIRADA_ACK_72H_INICIADA");
            }
            return null;
        });
        }catch(IllegalStateException blocked){throw new IOException("Retirada conservada por controles",blocked);}
        checkpoint.accept("RETIRANDO",id);
        // Otra instancia puede haber terminado y minimizado durante la espera; no reconstruir datos retirados.
        if(Boolean.TRUE.equals(submission(id).get("content_retired")))return;
        for(var d:e.comunicacion().adjuntos()) retire(d.id(),e.comunicacion().recorridoId(),id,motivoRetirada());
        if(db.sql.update("UPDATE custody_lifecycle SET phase='LIBERADA',last_error=NULL WHERE submission_id=? AND phase='RETIRANDO'",id.toString())==1)
            db.event("ALMACENAMIENTO",id.toString(),"COPIA_TRANSITORIA_LIBERADA");
    }
    private void retire(UUID attachment, UUID journey, UUID submission, String reason) throws IOException {
        var previous=db.sql.queryForList("SELECT * FROM storage_retirement WHERE attachment_id=?",attachment.toString());
        if(!previous.isEmpty() && "FINALIZADA".equals(previous.getFirst().get("phase"))) return;
        if(previous.isEmpty()) try{db.transaction(()->{
            db.sql.update("INSERT INTO storage_retirement(attachment_id,journey_id,submission_id,reason_code,phase,started_ms) VALUES(?,?,?,?,'INTENCION',?)",attachment.toString(),journey.toString(),submission==null?null:submission.toString(),reason,clock.millis());
            db.event("ALMACENAMIENTO",attachment.toString(),"RETIRADA_INICIADA");return null;
        });
        }catch(org.springframework.dao.DuplicateKeyException concurrent){
            previous=db.sql.queryForList("SELECT * FROM storage_retirement WHERE attachment_id=?",attachment.toString());
            if(previous.isEmpty())throw concurrent;
            if(!reason.equals(previous.getFirst().get("reason_code"))||!Objects.equals(submission==null?null:submission.toString(),previous.getFirst().get("submission_id")))throw new IOException("Intención distinta");
        }
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
    public void conservar(UUID id, boolean hold) {
        if(id==null)throw new IllegalArgumentException("ID requerido");
        db.transaction(()->{
            var rows=db.sql.queryForList("SELECT content_retired FROM citizen_submission WHERE id=? FOR UPDATE",id.toString());
            if(rows.isEmpty())throw new FalloTecnico(409);
            var states=db.sql.queryForList("SELECT phase,hold_active FROM custody_lifecycle WHERE submission_id=? FOR UPDATE",id.toString());
            if(states.isEmpty())throw new FalloTecnico(409);
            var state=states.getFirst();
            if(hold&&(Boolean.TRUE.equals(rows.getFirst().get("content_retired"))||!Set.of("OBSERVADA","VERIFICADA").contains(state.get("phase"))))throw new FalloTecnico(409);
            if(!Boolean.valueOf(hold).equals(state.get("hold_active"))){
                db.sql.update("UPDATE custody_lifecycle SET hold_active=? WHERE submission_id=?",hold,id.toString());
                db.event("ALMACENAMIENTO",id.toString(),hold?"CONSERVACION_BLOQUEADA":"CONSERVACION_DESBLOQUEADA");
            }
            return null;
        });
    }
    public Resultado procesar(int limit, boolean apply) {
        if(limit<1 || limit>20) throw new IllegalArgumentException("Lote de 1 a 20 requerido");
        if(apply) enabled(); int checked=0,done=0,blocked=0;
        for(String value:db.sql.queryForList("SELECT s.id FROM citizen_submission s LEFT JOIN custody_lifecycle c ON c.submission_id=s.id WHERE s.state='CONFIRMADA' AND s.content_retired=FALSE AND (c.phase IS NULL OR c.phase NOT IN ('LIBERADA','MINIMIZADA')) ORDER BY COALESCE(c.checked_ms,0),s.created_ms,s.id LIMIT ?",String.class,limit)) {
            checked++;
            try { if(apply) {retirar(UUID.fromString(value));done++;} else verificar(UUID.fromString(value)); }
            catch(IOException | RuntimeException failure) {blocked++;} // el detalle queda en eventos/estado; jamás imprimir datos o excepción JDBC
        }
        return new Resultado(checked,done,blocked);
    }
}