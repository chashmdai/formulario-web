package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.entrega.*;
import cl.nexbyte.siger.identidad.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.json.JsonMapper;

/** Custodia pública transaccional: datos, procedencia, comprobante y evento se confirman juntos. */
public final class RecepcionJdbc implements RecepcionDurable {
    private final BaseJdbc db;
    private final ControlesOperacion controles;
    private final JsonMapper mapper;
    private final String aviso;
    private final boolean habilitacion;
    private final String keyId;
    private final PrivateKey signer;
    private final Map<String,PublicKey> trusted;
    private final Map<String,PublicKey> institutionalTrust;
    private final Clock clock;
    private java.util.function.BooleanSupplier admision=()->true;
    public RecepcionJdbc admision(java.util.function.BooleanSupplier guard){this.admision=Objects.requireNonNull(guard);return this;}
    private final long leaseMs;
    private final byte[] ownerKey;
    public RecepcionJdbc(BaseJdbc db,JsonMapper mapper,String aviso,boolean habilitacion,String keyId,PrivateKey signer,
            Map<String,PublicKey> trusted,Clock clock,long leaseMs) {
        this(db,mapper,aviso,habilitacion,keyId,signer,trusted,clock,leaseMs,Map.of());
    }
    public RecepcionJdbc(BaseJdbc db,JsonMapper mapper,String aviso,boolean habilitacion,String keyId,PrivateKey signer,
            Map<String,PublicKey> trusted,Clock clock,long leaseMs,Map<String,PublicKey> institutionalTrust) {
        this(db,mapper,aviso,habilitacion,keyId,signer,trusted,clock,leaseMs,institutionalTrust,null);
    }
    public RecepcionJdbc(BaseJdbc db,JsonMapper mapper,String aviso,boolean habilitacion,String keyId,PrivateKey signer,
            Map<String,PublicKey> trusted,Clock clock,long leaseMs,Map<String,PublicKey> institutionalTrust,byte[] ownerKey) {
        this.ownerKey=ownerKey==null?null:ownerKey.clone();
        if(aviso==null||!aviso.matches("[A-Za-z0-9_.-]{1,100}")||leaseMs<1000||leaseMs>900000)throw new IllegalArgumentException("Configuración de recepción inválida");
        this.db=db;this.controles=new ControlesOperacion(db);this.mapper=mapper;this.aviso=aviso;this.habilitacion=habilitacion;
        this.institutionalTrust=Map.copyOf(institutionalTrust);
        if(institutionalTrust.values().stream().anyMatch(trusted.values()::contains))throw new IllegalArgumentException("Separar claves públicas e institucionales");
        this.keyId=keyId;this.signer=signer;this.trusted=Map.copyOf(trusted);this.clock=clock;this.leaseMs=leaseMs;
        ComprobanteDigital.firmar(new ComprobanteDigital.Contenido("1.0",UUID.randomUUID(),clock.instant(),"CUSTODIA_PUBLICA","0".repeat(64),"0".repeat(64),"FORMULARIO_PUBLICO_DDN",aviso),keyId,signer,mapper).verificar(trusted,mapper);
    }
    public void reponer(UUID id,int intentosEsperados,String motivo){
        if(id==null||intentosEsperados<0||!Set.of("CORRECCION_ADAPTADOR","RECEPTOR_RECUPERADO").contains(motivo==null?"":motivo))throw new FalloTecnico(400);
        db.transaction(()->{
            var rows=db.sql.queryForList("SELECT state,attempts,body FROM public_delivery WHERE id=? FOR UPDATE",id.toString());
            if(rows.isEmpty())throw new FalloTecnico(404);var row=rows.getFirst();
            if(!"CUARENTENA".equals(row.get("state"))||((Number)row.get("attempts")).intValue()!=intentosEsperados)throw new FalloTecnico(409);
            try{read(row.get("body").toString(),id);}catch(RuntimeException e){throw new FalloTecnico(409);}
            db.sql.update("UPDATE public_delivery SET state='PENDIENTE',attempts=0,next_attempt_ms=?,lease_id=NULL,lease_until_ms=0,last_error=NULL WHERE id=?",clock.millis(),id.toString());
            db.event("ENTREGA",id.toString(),"REINTENTO_"+motivo);return null;
        });
    }
    public String avisoVersion(){return aviso;}
    public SobreComunicacion recibirVerificadaEnSesion(UUID key,IdentidadVerificada identity,EvidenciaAutenticacion evidence,String message,String avisoVersion,String session){
        validarSesion(session);
        return db.transaction(()->{
            var rows=db.sql.queryForList("SELECT owner_session FROM public_delivery WHERE id=?",key.toString());
            if(!rows.isEmpty()&&!session.equals(rows.getFirst().get("owner_session")))throw new FalloTecnico(409);
            var sobre=recibirVerificada(key,identity,evidence,message,avisoVersion);
            int changed=db.sql.update("UPDATE public_delivery SET owner_session=? WHERE id=? AND (owner_session IS NULL OR owner_session=?)",session,key.toString(),session);
            if(changed!=1)throw new FalloTecnico(409);return sobre;
        });
    }
    public SobreComunicacion recibirNna(UUID key,cl.nexbyte.siger.contrato.DeclaracionNna datos,String message,String avisoVersion,String session){
        validarSesion(session);if(key==null||datos==null)throw new FalloTecnico(400);
        cl.nexbyte.siger.contrato.DeclaracionNna.relato(message);
        var requestHash=requestHashNna(session,datos,message,avisoVersion);
        var prior=existing(key,requestHash);if(prior!=null)return prior.comunicacion();
        if(!aviso.equals(avisoVersion))throw new FalloTecnico(409);
        try{return db.transaction(()->{
            if(!disponible())throw new FalloTecnico(503);
            var now=clock.instant();var sobre=SobreComunicacion.crearNna(key,now,datos,message,aviso);
            var attrs=EntregaVerificada.procedenciaNna(sobre);
            var custody=new ComprobanteDigital.Contenido("1.0",key,now,"CUSTODIA_PUBLICA",sobre.sha256(),EntregaVerificada.huella(attrs),"FORMULARIO_PUBLICO_DDN",aviso);
            var delivery=new EntregaVerificada("2.1",sobre,attrs,ComprobanteDigital.firmar(custody,keyId,signer,mapper));delivery.validar(trusted,mapper);
            db.sql.update("INSERT INTO public_delivery(id,owner_run,owner_session,request_hash,body,state,created_ms,next_attempt_ms,attempts,lease_until_ms) VALUES(?,NULL,?,?,?,'PENDIENTE',?,?,0,0)",key.toString(),session,requestHash,mapper.writeValueAsString(delivery),now.toEpochMilli(),now.toEpochMilli());
            db.event("ENTREGA",key.toString(),"CUSTODIA_CONFIRMADA");return sobre;
        });}catch(DuplicateKeyException e){var winner=existing(key,requestHash);if(winner==null)throw new FalloTecnico(503);return winner.comunicacion();}
    }
    private static void validarSesion(String session){if(session==null||!session.matches("[a-f0-9]{64}"))throw new FalloTecnico(401);}
    private String requestHashNna(String session,cl.nexbyte.siger.contrato.DeclaracionNna datos,String mensaje,String aviso){
        return hash(mapper.writeValueAsString(List.of("NNA_1",session,datos,mensaje,Objects.requireNonNull(aviso))));
    }
    public boolean disponible(){try{var c=controles.estado();return admision.getAsBoolean()&&habilitacion&&c.aceptarNuevos()&&c.contingencia().equals("NORMAL");}catch(org.springframework.dao.DataAccessException e){return false;}}
    public SobreComunicacion recibir(UUID key,SobreComunicacion.Identidad identity,String message){throw new FalloTecnico(401);}
    public SobreComunicacion recibirVerificada(UUID key,IdentidadVerificada identity,EvidenciaAutenticacion evidence,String message,String avisoVersion){
        if(key==null||identity==null||evidence==null||!evidence.corresponde(identity)||evidence.verificadaEn().isAfter(clock.instant()))throw new FalloTecnico(401);
        var canonical=TextoCanonico.mensaje(message);
        if(avisoVersion==null||!avisoVersion.matches("[A-Za-z0-9_.-]{1,100}"))throw new FalloTecnico(409);
        var requestHash=hash(identity.run()+"\u0000"+canonical+"\u0000"+avisoVersion);
        var prior=existing(key,requestHash);if(prior!=null)return prior.comunicacion();
        if(!aviso.equals(avisoVersion))throw new FalloTecnico(409);
        try {
            return db.transaction(()->{
                if(!disponible())throw new FalloTecnico(503);
                var now=clock.instant();
                var sobre=SobreComunicacion.crear(key,now,new SobreComunicacion.Identidad(identity.run(),identity.nombres(),identity.apellidos(),identity.nombresComponentes(),identity.apellidosComponentes()),canonical,aviso);
                var attributes=EntregaVerificada.procedencia(evidence.verificadaEn(),evidence.id(),now,key);
                var content=new ComprobanteDigital.Contenido("1.0",key,now,"CUSTODIA_PUBLICA",sobre.sha256(),EntregaVerificada.huella(attributes),"FORMULARIO_PUBLICO_DDN",aviso);
                var receipt=ComprobanteDigital.firmar(content,keyId,signer,mapper);
                var delivery=new EntregaVerificada("2.0",sobre,attributes,receipt);delivery.validar(trusted,mapper);
                db.sql.update("INSERT INTO public_delivery(id,owner_run,request_hash,body,state,created_ms,next_attempt_ms,attempts,lease_until_ms) VALUES(?,?,?,?,'PENDIENTE',?,?,0,0)",
                    key.toString(),identity.run(),requestHash,mapper.writeValueAsString(delivery),now.toEpochMilli(),now.toEpochMilli());
                db.event("ENTREGA",key.toString(),"CUSTODIA_CONFIRMADA");
                return sobre;
            });
        }catch(DuplicateKeyException e){var winner=existing(key,requestHash);if(winner==null)throw new FalloTecnico(503);return winner.comunicacion();}
    }
    private EntregaVerificada existing(UUID key,String requestHash){
        var rows=db.sql.queryForList("SELECT request_hash,body,owner_session,content_retired FROM public_delivery WHERE id=?",key.toString());
        if(rows.isEmpty())return null;
        if(!requestHash.equals(rows.getFirst().get("request_hash")))throw new FalloTecnico(409);
        if(Boolean.TRUE.equals(rows.getFirst().get("content_retired")))throw new FalloTecnico(410); // no reconstruir el sobre personal ni repetir el ingreso histórico
        var delivery=read(rows.getFirst().get("body").toString(),key);
        var body=delivery.comunicacion();
        var actual="2.0".equals(body.version())?requestHashNna((String)rows.getFirst().get("owner_session"),body.declaracionNna(),body.mensaje(),body.avisoPrivacidadVersion()):hash(body.identidad().run()+"\u0000"+body.mensaje()+"\u0000"+body.avisoPrivacidadVersion());
        if(!requestHash.equals(actual))throw new FalloTecnico(503);
        return delivery;
    }
    private EntregaVerificada read(String json,UUID expected){
        try{var e=mapper.readValue(json,EntregaVerificada.class);e.validar(trusted,mapper);if(!e.comunicacion().id().equals(expected))throw new FalloTecnico(503);return e;}
        catch(IllegalArgumentException|tools.jackson.core.JacksonException e){throw new FalloTecnico(503);}
    }
    private Map<String,Object> owned(UUID id,String run){
        var rows=db.sql.queryForList("SELECT * FROM public_delivery WHERE id=? AND (owner_run=? OR owner_fingerprint=?)",id.toString(),run,ownerKey==null?null:cl.nexbyte.siger.custodia.ReciboMinimo.propietario(run,ownerKey));
        if(rows.isEmpty())throw new FalloTecnico(404);
        var row=rows.getFirst();if(Boolean.TRUE.equals(row.get("content_retired")))return row;var delivery=read(row.get("body").toString(),id);
        if(delivery.comunicacion().identidad()==null||!delivery.comunicacion().identidad().run().equals(run))throw new FalloTecnico(503);
        return row;
    }
    public Estado consultar(UUID id,String run){
        return estado(id,owned(id,run));
    }
    public Estado consultarSesion(UUID id,String session){
        validarSesion(session);
        var rows=db.sql.queryForList("SELECT * FROM public_delivery WHERE id=? AND owner_session=?",id.toString(),session);
        if(rows.isEmpty())throw new FalloTecnico(404);return estado(id,rows.getFirst());
    }
    private Estado estado(UUID id,Map<String,Object> row){
        if(Boolean.TRUE.equals(row.get("content_retired"))) {
            try {var c=cl.nexbyte.siger.custodia.ReciboMinimo.validar(row.get("minimal_receipt").toString(),id,trusted,institutionalTrust,mapper);
                if(!"HISTORICO".equals(c.cola())||!"CONFIRMADA".equals(row.get("state"))||!c.registro().equals(mapper.readValue(row.get("ack").toString(),RegistroInstitucional.class)))throw new IllegalArgumentException();
                return new Estado(id,"REGISTRADA_SIGER",null,c.registro().comprobante(),c.registro());
            }catch(RuntimeException invalid){throw new FalloTecnico(503);}
        }
        var delivery=read(row.get("body").toString(),id);
        if(row.get("ack")==null)return new Estado(id,"PENDIENTE_REGISTRO_SIGER",null,null,null);
        try{
            var ack=mapper.readValue(row.get("ack").toString(),RegistroInstitucional.class);ack.validar(institutionalTrust,mapper,delivery);
            if(!"CONFIRMADA".equals(row.get("state")))throw new FalloTecnico(503);
            return new Estado(id,"REGISTRADA_SIGER",null,ack.comprobante(),ack);
        }catch(IllegalArgumentException|tools.jackson.core.JacksonException error){throw new FalloTecnico(503);}
    }
    public ComprobanteInstitucional comprobante(UUID id,String run){
        var state=consultar(id,run);if(state.comprobante()==null)throw new FalloTecnico(409);return state.comprobante();
    }
    public List<SobreComunicacion> pendientes(int limite){throw new FalloTecnico(409);}
    public void confirmar(UUID id,SobreComunicacion.Confirmacion receipt){throw new FalloTecnico(409);}
    public List<EntregaReservada> reclamar(int limite){
        if(limite<1||limite>100)throw new FalloTecnico(400);
        var control=controles.estado();if(!habilitacion||!control.recuperar()||!control.contingencia().equals("NORMAL"))throw new FalloTecnico(503);
        return db.transaction(()->{
            long now=clock.millis();
            var ids=db.sql.queryForList("SELECT id FROM public_delivery WHERE (state='PENDIENTE' AND next_attempt_ms<=?) OR (state='RESERVADA' AND lease_until_ms<=?) ORDER BY created_ms,id LIMIT ?",String.class,now,now,Math.min(400,limite*4));
            var result=new ArrayList<EntregaReservada>();
            for(var id:ids){
                var reservation=UUID.randomUUID();
                int changed=db.sql.update("UPDATE public_delivery SET state='RESERVADA',lease_id=?,lease_until_ms=?,attempts=attempts+1 WHERE id=? AND ((state='PENDIENTE' AND next_attempt_ms<=?) OR (state='RESERVADA' AND lease_until_ms<=?))",reservation.toString(),now+leaseMs,id,now,now);
                if(changed==0)continue;
                var row=db.sql.queryForMap("SELECT body,attempts FROM public_delivery WHERE id=?",id);
                try{
                    var delivery=read(row.get("body").toString(),UUID.fromString(id));
                    if(((Number)row.get("attempts")).intValue()>10){db.sql.update("UPDATE public_delivery SET state='CUARENTENA',last_error='RESERVAS_AGOTADAS' WHERE id=?",id);db.event("ENTREGA",id,"CUARENTENA_RESERVAS");continue;}
                    result.add(new EntregaReservada(reservation,now+leaseMs,((Number)row.get("attempts")).intValue(),delivery));
                    db.event("ENTREGA",id,"RESERVADA");
                }catch(FalloTecnico error){db.sql.update("UPDATE public_delivery SET state='CUARENTENA',last_error='INTEGRIDAD' WHERE id=?",id);db.event("ENTREGA",id,"CUARENTENA_INTEGRIDAD");}
                if(result.size()==limite)break;
            }
            return List.copyOf(result);
        });
    }
    /** ACK antiguo no acredita un registro con folio. */
    public void confirmarReserva(UUID id,UUID lease,SobreComunicacion.Confirmacion receipt){throw new FalloTecnico(409);}
    public void confirmarRegistro(UUID id,UUID lease,RegistroInstitucional receipt){
        if(id==null||lease==null||receipt==null)throw new FalloTecnico(400);
        db.transaction(()->{
            var rows=db.sql.queryForList("SELECT * FROM public_delivery WHERE id=? FOR UPDATE",id.toString());
            if(rows.isEmpty())throw new FalloTecnico(404);
            var row=rows.getFirst();if(Boolean.TRUE.equals(row.get("content_retired"))){if(!estado(id,row).registroInstitucional().equals(receipt))throw new FalloTecnico(409);return null;}var delivery=read(row.get("body").toString(),id);
            try{receipt.validar(institutionalTrust,mapper,delivery);}catch(IllegalArgumentException|tools.jackson.core.JacksonException invalid){throw new FalloTecnico(409);}
            if(row.get("ack")!=null){
                var existing=mapper.readValue(row.get("ack").toString(),RegistroInstitucional.class);existing.validar(institutionalTrust,mapper,delivery);
                if(!existing.equals(receipt))throw new FalloTecnico(409);return null;
            }
            if(!"RESERVADA".equals(row.get("state"))||!lease.toString().equals(row.get("lease_id")))throw new FalloTecnico(409);
            db.sql.update("UPDATE public_delivery SET state='CONFIRMADA',ack=?,first_ack_ms=COALESCE(first_ack_ms,?),lease_until_ms=0,last_error=NULL WHERE id=?",mapper.writeValueAsString(receipt),clock.millis(),id.toString());
            db.event("ENTREGA",id.toString(),"REGISTRO_SIGER_CONFIRMADO");return null;
        });
    }
    public void fallo(UUID id,UUID lease,String code){
        if(lease==null||code==null||!Set.of("TRANSITORIO","CONTRATO_INVALIDO","RECEPTOR_NO_DISPONIBLE","CONFIRMACION_AMBIGUA").contains(code))throw new FalloTecnico(400);
        db.transaction(()->{
            var rows=db.sql.queryForList("SELECT state,lease_id,attempts,last_error FROM public_delivery WHERE id=? FOR UPDATE",id.toString());
            if(rows.isEmpty())throw new FalloTecnico(404);var row=rows.getFirst();
            if(!lease.toString().equals(row.get("lease_id")))throw new FalloTecnico(409);
            if(!"RESERVADA".equals(row.get("state"))){if(code.equals(row.get("last_error")))return null;throw new FalloTecnico(409);}
            int attempts=((Number)row.get("attempts")).intValue();boolean terminal=code.equals("CONTRATO_INVALIDO")||attempts>=10;
            long delay=Math.min(300000,1000L<<(Math.min(attempts,9)))+java.util.concurrent.ThreadLocalRandom.current().nextLong(1,1000);
            db.sql.update("UPDATE public_delivery SET state=?,last_error=?,next_attempt_ms=?,lease_until_ms=0 WHERE id=?",terminal?"CUARENTENA":"PENDIENTE",code,clock.millis()+delay,id.toString());
            db.event("ENTREGA",id.toString(),terminal?"CUARENTENA":"REINTENTO_DIFERIDO");return null;
        });
    }
    public Map<String,Long> indicadores(){
        var result=new HashMap<String,Long>();
        db.sql.query("SELECT state,COUNT(*) total FROM public_delivery GROUP BY state",rs->{result.put(rs.getString(1),rs.getLong(2));});
        return Map.copyOf(result);
    }
    private static String hash(String input){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
