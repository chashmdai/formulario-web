package cl.nexbyte.siger.formulario;

import cl.nexbyte.siger.robustez.*;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.json.JsonMapper;
import static cl.nexbyte.siger.formulario.IdentificacionCiudadana.*;

/** Custodia atómica del formulario completo. El consumidor histórico no recibe un sobre truncado. */
public final class RecepcionFormularioJdbc {
    public record Estado(UUID id,String estado,ComprobanteDigital custodia,RegistroInstitucional registro){}
    public record Reserva(UUID reserva,long venceEn,int intento,EntregaFormulario entrega){}
    private final BaseJdbc db;private final RecorridosJdbc recorridos;private final AdjuntosFormulario adjuntos;
    private final JsonMapper mapper;private final ValidacionFormulario validation;private final ControlesOperacion controles;
    private final String keyId;private final PrivateKey key;private final Map<String,PublicKey> trust,institutional;
    private final Clock clock;private final boolean enabled,adultos;
    public RecepcionFormularioJdbc(BaseJdbc db,RecorridosJdbc recorridos,AdjuntosFormulario adjuntos,JsonMapper mapper,ValidacionFormulario validation,
            String keyId,PrivateKey key,Map<String,PublicKey> trust,Map<String,PublicKey> institutional,Clock clock,boolean enabled){
        this(db,recorridos,adjuntos,mapper,validation,keyId,key,trust,institutional,clock,enabled,false);
    }
    public RecepcionFormularioJdbc(BaseJdbc db,RecorridosJdbc recorridos,AdjuntosFormulario adjuntos,JsonMapper mapper,ValidacionFormulario validation,
            String keyId,PrivateKey key,Map<String,PublicKey> trust,Map<String,PublicKey> institutional,Clock clock,boolean enabled,boolean adultos){
        this.db=db;this.recorridos=recorridos;this.adjuntos=adjuntos;this.mapper=mapper;this.validation=validation;this.controles=new ControlesOperacion(db);
        this.keyId=keyId;this.key=key;this.trust=Map.copyOf(trust);this.institutional=Map.copyOf(institutional);this.clock=clock;this.enabled=enabled;this.adultos=adultos;
        if(institutional.values().stream().anyMatch(trust.values()::contains))throw new IllegalArgumentException("Separar firmantes públicos e institucionales");
        ComprobanteDigital.firmar(new ComprobanteDigital.Contenido("1.0",UUID.randomUUID(),clock.instant(),"CUSTODIA_PUBLICA","0".repeat(64),"0".repeat(64),"FORMULARIO_PUBLICO_DDN","VERIFICACION"),keyId,key,mapper).verificar(trust,mapper);
    }
    public boolean disponible(){try{var c=controles.estado();return enabled&&c.aceptarNuevos()&&c.contingencia().equals("NORMAL");}catch(org.springframework.dao.DataAccessException e){return false;}}
    public boolean multimediaDisponible(){return adjuntos.multimediaDisponible();}
    public boolean adultosDisponibles(){return adultos&&disponible();}
    public Estado recibir(UUID id,UUID journey,String session,DatosFormulario datos){
        if(id==null||journey==null||datos==null)throw new FalloTecnico(400);sesion(session);
        String hash=CanonFormulario.huella("SOLICITUD",List.of(journey,session,datos),mapper);
        var previous=previa(id,journey,session,hash);if(previous!=null)return previous;
        var verifiedFiles=adjuntos.prepararRecepcion(journey,session);
        try{return db.transaction(()->{
            var route=recorridos.bloquearBorrador(journey,session);
            // Otro hilo con la misma clave puede haber confirmado mientras esperábamos el lock del recorrido.
            var repeated=previa(id,journey,session,hash);if(repeated!=null)return repeated;
            var canonical=ContactosFormulario.normalizar(datos);
            if(!disponible())throw new FalloTecnico(503);
            if(route.tipo()!=Tipo.NNA&&!adultos)throw new FalloTecnico(503);
            var selected=new HashSet<String>();selected.add(route.tipo()==Tipo.NNA?"presentador-nna":"presentador-adulto");canonical.personas().forEach(p->selected.add(p.personaId()));
            var people=recorridos.personasInternas(journey,session,selected);validation.validar(route.tipo(),canonical,people);
            var files=adjuntos.internos(journey);
            if(!new HashSet<>(canonical.adjuntos()).equals(new HashSet<>(files.stream().map(AdjuntosFormulario.Descriptor::id).toList())))throw new IllegalArgumentException("Adjuntos no corresponden al recorrido");
            if(!files.equals(verifiedFiles))throw new FalloTecnico(409);
            String documentVersion=adjuntos.multimedia(files)?"1.1":"1.0";
            var content=new EntregaFormulario.Contenido(documentVersion,id,journey,route.tipo(),clock.instant(),route.avisoVersion(),route.aceptaCanal(),canonical,people,files);
            String bodyHash=CanonFormulario.huella("CONTENIDO",content,mapper),sourceHash=EntregaFormulario.procedencia(content,mapper);
            var proof=ComprobanteDigital.firmar(new ComprobanteDigital.Contenido("1.0",id,content.recibidaEn(),"CUSTODIA_PUBLICA",bodyHash,sourceHash,"FORMULARIO_PUBLICO_DDN",route.avisoVersion()),keyId,key,mapper);
            var delivery=new EntregaFormulario(content,bodyHash,sourceHash,proof);delivery.validar(trust,mapper);
            db.sql.update("INSERT INTO citizen_submission(id,journey_id,owner_session,request_hash,body,state,created_ms,next_attempt_ms,document_version) VALUES(?,?,?,?,?,'PENDIENTE',?,?,?)",id.toString(),journey.toString(),session,hash,mapper.writeValueAsString(delivery),clock.millis(),clock.millis(),documentVersion);
            recorridos.recibida(journey,session);db.event("FORMULARIO_COMPLETO",id.toString(),"CUSTODIA_CONFIRMADA");
            return new Estado(id,"PENDIENTE_REGISTRO_SIGER",proof,null);
        });}catch(DuplicateKeyException conflict){var winner=previa(id,journey,session,hash);if(winner==null)throw new FalloTecnico(409);return winner;}
        catch(FalloTecnico conflict){if(conflict.status==409){var winner=previa(id,journey,session,hash);if(winner!=null)return winner;}throw conflict;}
    }
    private Estado previa(UUID id,UUID journey,String session,String hash){
        var rows=db.sql.queryForList("SELECT * FROM citizen_submission WHERE id=?",id.toString());if(rows.isEmpty())return null;var row=rows.getFirst();
        if(!session.equals(row.get("owner_session"))||!journey.toString().equals(row.get("journey_id"))||!hash.equals(row.get("request_hash")))throw new FalloTecnico(409);
        return estado(id,row);
    }
    public Estado consultar(UUID id,String session){
        sesion(session);var rows=db.sql.queryForList("SELECT * FROM citizen_submission WHERE id=? AND owner_session=?",id.toString(),session);
        if(rows.isEmpty())throw new FalloTecnico(404);return estado(id,rows.getFirst());
    }
    public Estado consultarRecorrido(UUID journey,String session){
        sesion(session);var rows=db.sql.queryForList("SELECT * FROM citizen_submission WHERE journey_id=? AND owner_session=?",journey.toString(),session);
        if(rows.isEmpty())throw new FalloTecnico(404);var row=rows.getFirst();return estado(UUID.fromString(row.get("id").toString()),row);
    }
    private Estado estado(UUID id,Map<String,Object> row){
        var delivery=leer(row.get("body").toString(),id);
        if(row.get("ack")==null)return new Estado(id,"PENDIENTE_REGISTRO_SIGER",delivery.custodia(),null);
        try{var ack=mapper.readValue(row.get("ack").toString(),RegistroInstitucional.class);delivery.validarRegistro(ack,institutional,mapper);
            if(!"CONFIRMADA".equals(row.get("state")))throw new FalloTecnico(503);return new Estado(id,"REGISTRADA_SIGER",delivery.custodia(),ack);
        }catch(RuntimeException error){throw new FalloTecnico(503);}
    }
    private EntregaFormulario leer(String text,UUID id){
        try{var delivery=mapper.readValue(text,EntregaFormulario.class);delivery.validar(trust,mapper);if(!id.equals(delivery.comunicacion().id()))throw new FalloTecnico(503);return delivery;}
        catch(RuntimeException error){throw new FalloTecnico(503);}
    }
    public List<Reserva> reclamar(int limit){return reclamar(limit,Set.of("1.0"));}
    public List<Reserva> reclamar(int limit,Set<String> versions){
        if(versions==null||versions.isEmpty()||!Set.of("1.0","1.1").containsAll(versions))throw new FalloTecnico(400);
        if(limit<1||limit>10)throw new FalloTecnico(400);var control=controles.estado();
        if(!enabled||!control.recuperar()||!control.contingencia().equals("NORMAL"))throw new FalloTecnico(503);
        return db.transaction(()->{
            long now=clock.millis();var result=new ArrayList<Reserva>();
            var ids=db.sql.queryForList("SELECT id FROM citizen_submission WHERE ((state='PENDIENTE' AND next_attempt_ms<=?) OR (state='RESERVADA' AND lease_until_ms<=?)) AND ((document_version='1.0' AND ?=TRUE) OR (document_version='1.1' AND ?=TRUE)) ORDER BY created_ms,id LIMIT ?",String.class,now,now,versions.contains("1.0"),versions.contains("1.1"),limit*4);
            for(var id:ids){var lease=UUID.randomUUID();
                if(db.sql.update("UPDATE citizen_submission SET state='RESERVADA',lease_id=?,lease_until_ms=?,attempts=attempts+1 WHERE id=? AND ((state='PENDIENTE' AND next_attempt_ms<=?) OR (state='RESERVADA' AND lease_until_ms<=?))",lease.toString(),now+300000L,id,now,now)!=1)continue;
                var row=db.sql.queryForMap("SELECT body,attempts FROM citizen_submission WHERE id=?",id);int attempts=((Number)row.get("attempts")).intValue();
                try{if(attempts>10)throw new FalloTecnico(503);var delivery=leer(row.get("body").toString(),UUID.fromString(id));
                    if(!versions.contains(delivery.comunicacion().version()))throw new FalloTecnico(503);
                    result.add(new Reserva(lease,now+300000L,attempts,delivery));db.event("FORMULARIO_COMPLETO",id,"RESERVADA");
                }catch(FalloTecnico corrupt){db.sql.update("UPDATE citizen_submission SET state='CUARENTENA',last_error='INTEGRIDAD_O_LIMITE' WHERE id=?",id);db.event("FORMULARIO_COMPLETO",id,"CUARENTENA");}
                if(result.size()==limit)break;
            }
            return List.copyOf(result);
        });
    }
    public AdjuntosFormulario.Archivo adjuntoReservado(UUID id,UUID lease,UUID file){
        var rows=db.sql.queryForList("SELECT body,state,lease_id,lease_until_ms FROM citizen_submission WHERE id=?",id.toString());
        if(rows.isEmpty())throw new FalloTecnico(404);var row=rows.getFirst();
        if(!"RESERVADA".equals(row.get("state"))||lease==null||!lease.toString().equals(row.get("lease_id"))||((Number)row.get("lease_until_ms")).longValue()<=clock.millis())throw new FalloTecnico(409);
        var delivery=leer(row.get("body").toString(),id);var d=delivery.comunicacion().adjuntos().stream().filter(f->f.id().equals(file)).findFirst().orElseThrow(()->new FalloTecnico(404));
        var bytes=adjuntos.contenido(delivery.comunicacion().recorridoId(),file);if(!AdjuntosFormulario.hash(bytes.contenido()).equals(d.sha256())||bytes.contenido().length!=d.tamanio()||!bytes.nombre().equals(d.nombre()))throw new FalloTecnico(503);return bytes;
    }
    public cl.nexbyte.siger.multimedia.RecursoPrivado recursoReservado(UUID id,UUID lease,UUID file){
        var rows=db.sql.queryForList("SELECT body,state,lease_id,lease_until_ms FROM citizen_submission WHERE id=?",id.toString());if(rows.isEmpty())throw new FalloTecnico(404);var row=rows.getFirst();
        if(!"RESERVADA".equals(row.get("state"))||lease==null||!lease.toString().equals(row.get("lease_id"))||((Number)row.get("lease_until_ms")).longValue()<=clock.millis())throw new FalloTecnico(409);
        var delivery=leer(row.get("body").toString(),id);var d=delivery.comunicacion().adjuntos().stream().filter(f->f.id().equals(file)).findFirst().orElseThrow(()->new FalloTecnico(404));var resource=adjuntos.recursoInterno(delivery.comunicacion().recorridoId(),file);
        if(resource.tamanio()!=d.tamanio()||!resource.sha256().equals(d.sha256())||!resource.nombre().equals(d.nombre()))throw new FalloTecnico(503);db.sql.update("INSERT INTO media_access(id,attachment_id,at_ms,principal_kind,principal_ref,representation,event_code) VALUES(?,?,?,'INTEGRACION',?,'ORIGINAL','LECTURA_SOLICITADA')",UUID.randomUUID().toString(),file.toString(),clock.millis(),cl.nexbyte.siger.multimedia.AuditoriaMedia.referencia(lease.toString()));return resource;
    }
    public void confirmar(UUID id,UUID lease,RegistroInstitucional ack){
        if(id==null||lease==null||ack==null)throw new FalloTecnico(400);
        db.transaction(()->{
            var rows=db.sql.queryForList("SELECT * FROM citizen_submission WHERE id=? FOR UPDATE",id.toString());if(rows.isEmpty())throw new FalloTecnico(404);var row=rows.getFirst();var delivery=leer(row.get("body").toString(),id);
            try{delivery.validarRegistro(ack,institutional,mapper);}catch(IllegalArgumentException invalid){throw new FalloTecnico(409);}
            if(row.get("ack")!=null){var previous=mapper.readValue(row.get("ack").toString(),RegistroInstitucional.class);delivery.validarRegistro(previous,institutional,mapper);if(!previous.equals(ack))throw new FalloTecnico(409);return null;}
            if(!"RESERVADA".equals(row.get("state"))||!lease.toString().equals(row.get("lease_id"))||((Number)row.get("lease_until_ms")).longValue()<=clock.millis())throw new FalloTecnico(409);
            db.sql.update("UPDATE citizen_submission SET state='CONFIRMADA',ack=?,lease_until_ms=0,last_error=NULL WHERE id=?",mapper.writeValueAsString(ack),id.toString());db.event("FORMULARIO_COMPLETO",id.toString(),"REGISTRO_SIGER_CONFIRMADO");return null;
        });
    }
    public void fallo(UUID id,UUID lease,String code){
        if(lease==null||!Set.of("TRANSITORIO","CONTRATO_INVALIDO","RECEPTOR_NO_DISPONIBLE","CONFIRMACION_AMBIGUA").contains(code==null?"":code))throw new FalloTecnico(400);
        db.transaction(()->{
            var rows=db.sql.queryForList("SELECT state,lease_id,attempts,last_error FROM citizen_submission WHERE id=? FOR UPDATE",id.toString());if(rows.isEmpty())throw new FalloTecnico(404);var row=rows.getFirst();
            if(!lease.toString().equals(row.get("lease_id")))throw new FalloTecnico(409);
            if(!"RESERVADA".equals(row.get("state"))){if(code.equals(row.get("last_error")))return null;throw new FalloTecnico(409);}
            int n=((Number)row.get("attempts")).intValue();boolean terminal=code.equals("CONTRATO_INVALIDO")||n>=10;
            db.sql.update("UPDATE citizen_submission SET state=?,last_error=?,next_attempt_ms=?,lease_until_ms=0 WHERE id=?",terminal?"CUARENTENA":"PENDIENTE",code,clock.millis()+Math.min(300000L,1000L<<Math.min(n,9)),id.toString());
            db.event("FORMULARIO_COMPLETO",id.toString(),terminal?"CUARENTENA":"REINTENTO_DIFERIDO");return null;
        });
    }
    private static void sesion(String s){if(s==null||!s.matches("[a-f0-9]{64}"))throw new FalloTecnico(401);}
}
