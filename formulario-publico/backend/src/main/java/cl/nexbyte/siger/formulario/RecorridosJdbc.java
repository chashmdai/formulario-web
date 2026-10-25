package cl.nexbyte.siger.formulario;

import cl.nexbyte.siger.robustez.*;
import cl.nexbyte.siger.srcei.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.*;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.json.JsonMapper;
import static cl.nexbyte.siger.formulario.IdentificacionCiudadana.*;

/** Propiedad, cuotas y consultas confirmadas en MySQL. No mantiene una copia de datos en memoria. */
public final class RecorridosJdbc {
    public record Recorrido(UUID id,Tipo tipo,String avisoVersion,boolean aceptaCanal,String estado,Instant venceEn) {}
    private record Reserva(UUID lease,Persona persona,RunConsulta run,boolean consultar) {}
    private java.util.function.BooleanSupplier admision=()->true;
    public RecorridosJdbc admision(java.util.function.BooleanSupplier guard){this.admision=Objects.requireNonNull(guard);return this;}
    private void capacidad(){if(!admision.getAsBoolean())throw new FalloTecnico(507);}
    private final BaseJdbc db;
    private final JsonMapper mapper;
    private final Function<RunConsulta,ResultadoSrcei> proveedor;
    private final Predicate<RunConsulta> autorizado;
    private final Clock clock;
    private final byte[] abusoKey;
    private final String avisoAdulto,avisoNna;
    public RecorridosJdbc(BaseJdbc db,JsonMapper mapper,Function<RunConsulta,ResultadoSrcei> proveedor,
            Predicate<RunConsulta> autorizado,Clock clock,byte[] abusoKey,String avisoAdulto,String avisoNna) {
        this.db=Objects.requireNonNull(db);this.mapper=mapper;this.proveedor=proveedor;this.autorizado=autorizado;this.clock=clock;
        if(abusoKey==null||abusoKey.length<32||abusoKey.length>128)throw new IllegalArgumentException("Clave antiabuso requerida");
        for(var notice:List.of(avisoAdulto,avisoNna)) if(!notice.matches("[A-Za-z0-9_.-]{1,100}")) throw new IllegalArgumentException("Aviso inválido");
        this.abusoKey=abusoKey.clone();this.avisoAdulto=avisoAdulto;this.avisoNna=avisoNna;
    }
    public String aviso(Tipo tipo){return tipo==Tipo.NNA?avisoNna:avisoAdulto;}
    public Recorrido iniciar(Tipo tipo,boolean acepta,String aviso,String session,String address) {
        capacidad();validarSession(session);if(tipo==null)throw new FalloTecnico(400);
        if(!aviso(tipo).equals(aviso)||(tipo!=Tipo.NNA&&!acepta))throw new FalloTecnico(409);
        limitar(address,true);
        var id=UUID.randomUUID();long now=clock.millis(),expires=now+Duration.ofHours(4).toMillis();
        db.sql.update("INSERT INTO citizen_journey(id,owner_session,kind,notice_version,accepted_channel,state,query_count,created_ms,expires_ms) VALUES(?,?,?,?,?,'BORRADOR',0,?,?)",
            id.toString(),session,tipo.name(),aviso,acepta,now,expires);
        return new Recorrido(id,tipo,aviso,acepta,"BORRADOR",Instant.ofEpochMilli(expires));
    }
    public Recorrido consultar(UUID id,String session) {return recorrido(fila(id,session,false));}
    // Solo dentro de la transacción de custodia/adjuntos; comparte el lock con las declaraciones.
    public Recorrido bloquearBorrador(UUID id,String session) {var row=fila(id,session,true);editable(row);return recorrido(row);}
    void recibida(UUID id,String session) {
        bloquearBorrador(id,session);
        if(db.sql.update("UPDATE citizen_journey SET state='RECIBIDO' WHERE id=? AND state='BORRADOR'",id.toString())!=1)throw new FalloTecnico(409);
    }
    public void cancelar(UUID id,String session) {
        db.transaction(()->{fila(id,session,true);db.sql.update("UPDATE citizen_journey SET state='CANCELADO' WHERE id=? AND state='BORRADOR'",id.toString());return null;});
    }
    private Map<String,Object> fila(UUID id,String session,boolean lock) {
        validarSession(session);if(id==null)throw new FalloTecnico(404);
        var rows=db.sql.queryForList("SELECT * FROM citizen_journey WHERE id=? AND owner_session=?"+(lock?" FOR UPDATE":""),id.toString(),session);
        if(rows.isEmpty()||((Number)rows.getFirst().get("expires_ms")).longValue()<=clock.millis()||Set.of("CANCELADO","MINIMIZADO").contains(rows.getFirst().get("state")))throw new FalloTecnico(404);
        return rows.getFirst();
    }
    private static Recorrido recorrido(Map<String,Object> row) {
        var accepted=row.get("accepted_channel");
        return new Recorrido(UUID.fromString(row.get("id").toString()),Tipo.valueOf(row.get("kind").toString()),row.get("notice_version").toString(),
            accepted instanceof Boolean b?b:((Number)accepted).intValue()!=0,row.get("state").toString(),Instant.ofEpochMilli(((Number)row.get("expires_ms")).longValue()));
    }
    private static void editable(Map<String,Object> journey) {if(!"BORRADOR".equals(journey.get("state")))throw new FalloTecnico(409);}
    private static void validarSession(String s) {if(s==null||!s.matches("[a-f0-9]{64}"))throw new FalloTecnico(401);}
    private static void validarPersona(String id,Rol role,Tipo kind) {
        if(id==null||role==null)throw new FalloTecnico(400);
        String expected=switch(role){case PRESENTADOR->kind==Tipo.NNA?"presentador-nna":"presentador-adulto";
            case AFECTADO_NNA->"afectado-[1-9][0-9]?";case ADULTO_RELACIONADO->"relacionado-[1-9][0-9]?";case PERSONA_SENALADA->"senalado-[1-9][0-9]?";};
        if(!id.matches(expected)||(kind==Tipo.NNA&&role!=Rol.PRESENTADOR))throw new FalloTecnico(400);
    }
    private Persona leer(String body) {try{return mapper.readValue(body,Persona.class);}catch(RuntimeException error){throw new FalloTecnico(503);}}
    private Persona interna(UUID journey,String person) {
        var rows=db.sql.queryForList("SELECT body FROM citizen_person WHERE journey_id=? AND person_id=?",journey.toString(),person);
        if(rows.isEmpty())throw new FalloTecnico(404);return leer(rows.getFirst().get("body").toString());
    }
    public Vista persona(UUID journey,String person,String session) {
        fila(journey,session,false);
        var rows=db.sql.queryForList("SELECT body,query_state,lease_until_ms FROM citizen_person WHERE journey_id=? AND person_id=?",journey.toString(),person);
        if(rows.isEmpty())throw new FalloTecnico(404);
        if("CONSULTANDO".equals(rows.getFirst().get("query_state"))&&((Number)rows.getFirst().get("lease_until_ms")).longValue()<=clock.millis()) {
            // Una recarga no conoce el RUN privado: resolver la reserva vencida desde su estado persistido.
            return db.transaction(()->{
                var row=fila(journey,session,true);editable(row);
                var current=db.sql.queryForMap("SELECT body,query_state,lease_until_ms FROM citizen_person WHERE journey_id=? AND person_id=?",journey.toString(),person);
                var old=leer(current.get("body").toString());
                if("CONSULTANDO".equals(current.get("query_state"))&&((Number)current.get("lease_until_ms")).longValue()<=clock.millis()) {
                    old=new Persona(old.id(),old.rol(),old.runNumero(),old.runDv(),old.modulo11(),old.autenticada(),ResultadoSrcei.Estado.TIMEOUT,clock.instant(),UUID.randomUUID(),old.atributos(),old.discrepancias());
                    db.sql.update("UPDATE citizen_person SET body=?,query_state='REVISADO',query_lease=NULL,lease_until_ms=0 WHERE journey_id=? AND person_id=?",mapper.writeValueAsString(old),journey.toString(),person);
                }
                return "REVISADO".equals(current.get("query_state"))||old.estadoConsulta()==ResultadoSrcei.Estado.TIMEOUT?vista(old):new Vista(old.id(),"CONSULTANDO",old.autenticada(),old.runNumero()!=null,Set.of(),Set.of(),Map.of(),"Estamos revisando el RUN.");
            });
        }
        var p=leer(rows.getFirst().get("body").toString());
        if("CONSULTANDO".equals(rows.getFirst().get("query_state")))return new Vista(p.id(),"CONSULTANDO",p.autenticada(),p.runNumero()!=null,Set.of(),Set.of(),Map.of(),"Estamos revisando el RUN. Puedes volver a intentar con el mismo recorrido.");
        return vista(p);
    }

    /** Reinicio explícito por cambio de RUN: conserva historial privado y las cuotas consumidas. */
    public void reiniciarPersona(UUID journey,String person,String session) {
        db.transaction(()->{
            var row=fila(journey,session,true);editable(row);
            var existing=db.sql.queryForList("SELECT body,query_state FROM citizen_person WHERE journey_id=? AND person_id=?",journey.toString(),person);
            if(existing.isEmpty())return null;
            db.sql.update("INSERT INTO citizen_identity_history(id,journey_id,person_id,body,query_state,retired_ms) VALUES(?,?,?,?,?,?)",
                UUID.randomUUID().toString(),journey.toString(),person,existing.getFirst().get("body").toString(),existing.getFirst().get("query_state").toString(),clock.millis());
            db.sql.update("DELETE FROM citizen_person WHERE journey_id=? AND person_id=?",journey.toString(),person);
            return null;
        });
    }

    /** Único identificador hasta reinicio explícito; corregir un formato inválido no consume consulta. */
    public Vista identificar(UUID journey,String person,Rol role,String numero,String dv,String session,String address,AutenticacionCiudadana authentication) {
        var head=consultar(journey,session);validarPersona(person,role,head.tipo());
        var identity=authentication==null?null:authentication.identidad();
        boolean cu=head.tipo()==Tipo.ADULTO_CU&&role==Rol.PRESENTADOR;
        RunConsulta run=null;
        if(cu) {
            if(identity==null||authentication.prueba().verificadaEn().isAfter(clock.instant()))throw new FalloTecnico(401);
            if((numero!=null&&!numero.equals(identity.run().split("-")[0]))||(dv!=null&&!dv.equalsIgnoreCase(identity.run().split("-")[1])))throw new FalloTecnico(400);
            try{run=new RunConsulta(identity.run().split("-")[0],identity.run().split("-")[1]);}
            catch(IllegalArgumentException sandboxSynthetic){/* La identidad CU verificada no depende de módulo 11. */}
        } else if(numero!=null&&!numero.isBlank()) run=new RunConsulta(numero,dv);
        else {
            if(dv!=null&&!dv.isBlank())throw new FalloTecnico(400);
            if(head.tipo()==Tipo.ADULTO_DECLARADO&&role==Rol.PRESENTADOR)throw new FalloTecnico(400);
        }
        var finalRun=run;var finalIdentity=cu?authentication:null;
        // Se reserva en una transacción corta. Nunca se sostiene un lock durante la llamada gubernamental.
        capacidad();var reservation=db.transaction(()->{
            var row=fila(journey,session,true);editable(row);
            var existing=db.sql.queryForList("SELECT body,query_state,lease_until_ms FROM citizen_person WHERE journey_id=? AND person_id=?",journey.toString(),person);
            if(!existing.isEmpty()) {
                var old=leer(existing.getFirst().get("body").toString());
                String number=cu?identity.run().split("-")[0]:finalRun==null?null:finalRun.numero();
                String digit=cu?identity.run().split("-")[1]:finalRun==null?null:finalRun.dv();
                if(old.rol()!=role||!Objects.equals(number,old.runNumero())||!Objects.equals(digit,old.runDv())||old.autenticada()!=cu)throw new FalloTecnico(409);
                if("CONSULTANDO".equals(existing.getFirst().get("query_state"))) {
                    if(((Number)existing.getFirst().get("lease_until_ms")).longValue()>clock.millis())throw new FalloTecnico(409);
                    var timeout=crear(person,role,finalRun,finalIdentity,ResultadoSrcei.fallo(ResultadoSrcei.Estado.TIMEOUT,clock.instant(),UUID.randomUUID()),clock);
                    db.sql.update("UPDATE citizen_person SET body=?,query_state='REVISADO',query_lease=NULL,lease_until_ms=0 WHERE journey_id=? AND person_id=?",mapper.writeValueAsString(timeout),journey.toString(),person);
                    return new Reserva(null,timeout,null,false);
                }
                return new Reserva(null,old,null,false);
            }
            if(db.sql.queryForObject("SELECT COUNT(*) FROM citizen_person WHERE journey_id=?",Integer.class,journey.toString())>=30)throw new FalloTecnico(429);
            if(finalRun!=null&&((Number)row.get("query_count")).intValue()>=12)throw new FalloTecnico(429);
            var initial=crear(person,role,finalRun,finalIdentity,null,clock);
            // La allowlist de RUN no autoriza búsquedas de terceros. El canal adulto no se exige a NNA.
            // Esta unidad solo tiene finalidad de identificación del propio presentador.
            boolean query=role==Rol.PRESENTADOR&&(head.tipo()==Tipo.NNA||head.aceptaCanal())&&finalRun!=null&&autorizado.test(finalRun);
            var lease=query?UUID.randomUUID():null;
            db.sql.update("INSERT INTO citizen_person(journey_id,person_id,role,body,query_state,query_lease,lease_until_ms) VALUES(?,?,?,?,?,?,?)",
                journey.toString(),person,role.name(),mapper.writeValueAsString(initial),query?"CONSULTANDO":"REVISADO",lease==null?null:lease.toString(),query?clock.millis()+30000L:0L);
            if(finalRun!=null)db.sql.update("UPDATE citizen_journey SET query_count=query_count+1 WHERE id=?",journey.toString());
            return new Reserva(lease,initial,finalRun,query);
        });
        if(!reservation.consultar())return vista(reservation.persona());
        ResultadoSrcei result;
        try {
            limitar(address,false);
            result=proveedor.apply(reservation.run());
            if(result==null)result=ResultadoSrcei.fallo(ResultadoSrcei.Estado.INDISPONIBLE,clock.instant(),UUID.randomUUID());
        } catch(FalloTecnico limited) {
            // Una cuota agotada no deja una consulta atascada ni dispara el proveedor.
            result=ResultadoSrcei.fallo(ResultadoSrcei.Estado.SATURADO,clock.instant(),UUID.randomUUID());
        } catch(RuntimeException unavailable) {
            result=ResultadoSrcei.fallo(ResultadoSrcei.Estado.INDISPONIBLE,clock.instant(),UUID.randomUUID());
        }
        var finalResult=result;
        return db.transaction(()->{
            fila(journey,session,true);
            var resolved=crear(person,role,finalRun,finalIdentity,finalResult,clock);
            int changed=db.sql.update("UPDATE citizen_person SET body=?,query_state='REVISADO',query_lease=NULL,lease_until_ms=0 WHERE journey_id=? AND person_id=? AND query_state='CONSULTANDO' AND query_lease=?",
                mapper.writeValueAsString(resolved),journey.toString(),person,reservation.lease().toString());
            if(changed!=1)throw new FalloTecnico(409);
            return vista(resolved);
        });
    }
    public Vista declarar(UUID journey,String person,Map<String,String> values,String session) {
        capacidad();return db.transaction(()->{
            var row=fila(journey,session,true);editable(row);
            var busy=db.sql.queryForList("SELECT query_state FROM citizen_person WHERE journey_id=? AND person_id=?",journey.toString(),person);
            if(busy.isEmpty())throw new FalloTecnico(404);
            if(!"REVISADO".equals(busy.getFirst().get("query_state")))throw new FalloTecnico(409);
            var declared=IdentificacionCiudadana.declarar(interna(journey,person),values,clock);
            db.sql.update("UPDATE citizen_person SET body=? WHERE journey_id=? AND person_id=?",mapper.writeValueAsString(declared),journey.toString(),person);
            return vista(declared);
        });
    }
    /** Solo para construir el agregado en servidor; no conectar esta salida directamente a un controller. */
    public List<Persona> personasInternas(UUID journey,String session) {
        return personasInternas(journey,session,null);
    }
    List<Persona> personasInternas(UUID journey,String session,Set<String> seleccionadas) {
        fila(journey,session,false);
        var rows=db.sql.queryForList("SELECT person_id,body,query_state FROM citizen_person WHERE journey_id=? ORDER BY person_id",journey.toString()).stream()
            .filter(row->seleccionadas==null||seleccionadas.contains(row.get("person_id").toString())).toList();
        if(rows.stream().anyMatch(r->!"REVISADO".equals(r.get("query_state"))))throw new FalloTecnico(409);
        return rows.stream().map(r->leer(r.get("body").toString())).toList();
    }
    private String bucket(String address) {
        if(address==null||address.isBlank()||address.length()>256)throw new FalloTecnico(400);
        try {
            var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(abusoKey,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(address.getBytes(StandardCharsets.UTF_8)));
        } catch(GeneralSecurityException e){throw new IllegalStateException("Control de abuso no disponible");}
    }
    private void limitar(String address,boolean start) {
        String id=bucket(address);long window=clock.millis()/3600000L*3600000L;
        for(int attempt=0;attempt<2;attempt++) {
            try {
                db.transaction(()->{
                    var rows=db.sql.queryForList("SELECT * FROM citizen_abuse_bucket WHERE bucket_id=? FOR UPDATE",id);
                    if(rows.isEmpty())db.sql.update("INSERT INTO citizen_abuse_bucket(bucket_id,window_ms,starts,lookups) VALUES(?,?,?,?)",id,window,start?1:0,start?0:1);
                    else {
                        var row=rows.getFirst();boolean same=((Number)row.get("window_ms")).longValue()==window;
                        int starts=same?((Number)row.get("starts")).intValue():0,lookups=same?((Number)row.get("lookups")).intValue():0;
                        if(start?starts>=12:lookups>=12)throw new FalloTecnico(429);
                        db.sql.update("UPDATE citizen_abuse_bucket SET window_ms=?,starts=?,lookups=? WHERE bucket_id=?",window,starts+(start?1:0),lookups+(start?0:1),id);
                    }
                    db.sql.update("DELETE FROM citizen_abuse_bucket WHERE window_ms<?",window-Duration.ofDays(1).toMillis());return null;
                });return;
            } catch(DuplicateKeyException race){if(attempt==1)throw new FalloTecnico(429);}
        }
    }
}
