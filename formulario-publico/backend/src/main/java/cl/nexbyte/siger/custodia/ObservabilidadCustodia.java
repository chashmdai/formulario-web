package cl.nexbyte.siger.custodia;

import cl.nexbyte.siger.multimedia.AlmacenPrivado;
import cl.nexbyte.siger.robustez.BaseJdbc;
import java.time.*;
import java.util.*;

/** Instantánea operativa agregada. No lee cuerpos/evidencias firmadas ni proyecta identificadores o nombres. */
public final class ObservabilidadCustodia {
    private final BaseJdbc db;
    private final AlmacenPrivado store;
    private final Clock clock;
    private final Duration retention;
    private final Long quota;
    public ObservabilidadCustodia(BaseJdbc db,AlmacenPrivado store,Clock clock,Duration retention,Long quota){
        this.db=db;this.store=store;this.clock=clock;this.retention=retention;this.quota=quota;
    }
    private static long value(Map<String,Object> row,String key){Object v=row.get(key);return v==null?0:((Number)v).longValue();}
    private Long age(Object timestamp){return timestamp==null||((Number)timestamp).longValue()==0?null:Math.max(0,(clock.millis()-((Number)timestamp).longValue())/1000);}
    private Map<String,Object> queue(boolean full){
        String table=full?"citizen_submission":"public_delivery";
        String join=full?" LEFT JOIN custody_lifecycle c ON c.submission_id=s.id ":" ";
        String hold=full?"c.hold_active":"s.retention_hold";
        String due=full?"(c.due_ms>? OR c.observed_ms>?)":"s.retire_after_ms>?";
        var states=new LinkedHashMap<String,Long>();for(String state:List.of("PENDIENTE","RESERVADA","CONFIRMADA","CUARENTENA","OTROS"))states.put(state,0L);
        for(var row:db.sql.queryForList("SELECT state,COUNT(*) cantidad FROM "+table+" GROUP BY state")){
            String key=states.containsKey(row.get("state"))?row.get("state").toString():"OTROS";states.put(key,states.get(key)+value(row,"cantidad"));
        }
        var oldest=db.sql.queryForMap("SELECT MIN(created_ms) oldest FROM "+table+" WHERE state='PENDIENTE'");
        var args=new ArrayList<Object>();args.add(clock.millis());args.add(full?"COMPLETO":"HISTORICO");
        var guarantees=db.sql.queryForMap("SELECT COUNT(*) total,COALESCE(SUM(CASE WHEN g.operation_id IS NULL THEN 1 ELSE 0 END),0) ausentes,COALESCE(SUM(CASE WHEN g.expires_ms<=? THEN 1 ELSE 0 END),0) vencidas FROM "+table+" s LEFT JOIN custody_guarantee g ON g.operation_id=s.id AND g.queue_kind=? WHERE s.state='CONFIRMADA' AND s.content_retired=FALSE",args.toArray());
        long missing=value(guarantees,"ausentes"),expired=value(guarantees,"vencidas"),confirmed=value(guarantees,"total");
        long holds=db.sql.queryForObject("SELECT COUNT(*) FROM "+table+" s"+join+" WHERE "+hold+"=TRUE AND s.content_retired=FALSE",Long.class);
        var dueArgs=new ArrayList<Object>();dueArgs.add(clock.millis());if(full)dueArgs.add(clock.millis()-retention.toMillis());
        long waiting=db.sql.queryForObject("SELECT COUNT(*) FROM "+table+" s"+join+" WHERE s.state='CONFIRMADA' AND s.content_retired=FALSE AND "+due,Long.class,dueArgs.toArray());
        long unobserved=db.sql.queryForObject("SELECT COUNT(*) FROM "+table+" s"+join+" WHERE s.state='CONFIRMADA' AND s.content_retired=FALSE AND "+(full?"c.submission_id IS NULL":"s.retire_after_ms=0"),Long.class);
        long minimized=db.sql.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE content_retired=TRUE",Long.class);
        long withdrawing=0,released=0,failed=0;
        if(full){
            var phases=db.sql.queryForMap("SELECT COALESCE(SUM(CASE WHEN phase='RETIRANDO' THEN 1 ELSE 0 END),0) retirando,COALESCE(SUM(CASE WHEN phase IN ('LIBERADA','MINIMIZADA') THEN 1 ELSE 0 END),0) liberadas,COALESCE(SUM(CASE WHEN last_error IS NOT NULL THEN 1 ELSE 0 END),0) fallidas FROM custody_lifecycle");
            withdrawing=value(phases,"retirando");released=value(phases,"liberadas");failed=value(phases,"fallidas");
        }else released=minimized; // Histórico: retirada y minimización son una sola transacción, sin fase filesystem.
        var result=new LinkedHashMap<String,Object>();result.put("porEstado",states);result.put("pendienteMasAntiguaEdadSegundos",age(oldest.get("oldest")));
        result.put("confirmadasEsperandoGarantia",missing+expired);result.put("garantias",Map.of("vigentesPorVencimiento",confirmed-missing-expired,"vencidas",expired,"ausentes",missing));
        result.put("bajoHold",holds);result.put("esperandoRetencionTecnica",waiting);result.put("sinObservacionRetencion",unobserved);result.put("verificacionesNoSatisfechas",failed);
        result.put("retiradas",Map.of("iniciadasAcumuladas",withdrawing+released,"retirando",withdrawing,"bytesLiberados",released,"pendientesRecuperacion",withdrawing+Math.max(0,released-minimized)));
        result.put("minimizadas",minimized);return result;
    }
    public Map<String,Object> estado(boolean enabled,boolean running){
        // Consultas acotadas por esquema y categorías fijas. Fotografía de diagnóstico, no snapshot BD+filesystem.
        var maintenance=db.sql.queryForMap("SELECT last_ms,last_code,lease_until_ms FROM transient_maintenance WHERE id=1");
        String code=String.valueOf(maintenance.get("last_code"));if(!Set.of("CERRADO","CORRECTO","CONSERVADOS_POR_CONTROL","ELIMINACION_CERRADA","INCIDENCIA_CONSERVAR").contains(code))code="INCIDENCIA_CONSERVAR";
        var execution=new LinkedHashMap<String,Object>();execution.put("ultimaEjecucionMs",value(maintenance,"last_ms"));execution.put("edadSegundos",age(maintenance.get("last_ms")));execution.put("resultado",code);execution.put("leaseVigente",value(maintenance,"lease_until_ms")>clock.millis());
        long reserved=db.sql.queryForObject("SELECT reserved_bytes FROM media_capacity WHERE id=1",Long.class);
        long sum=db.sql.queryForObject("SELECT COALESCE(SUM(reserved_bytes),0) FROM media_upload WHERE released=FALSE",Long.class);
        var capacity=new LinkedHashMap<String,Object>();capacity.put("reservadosBytes",reserved);capacity.put("sumaReservasNoLiberadasBytes",sum);capacity.put("contabilidadConcordante",reserved>=0&&reserved==sum);capacity.put("cuotaConfiguradaBytes",quota);capacity.put("disponibleCuotaBytes",quota==null?null:Math.max(0,quota-reserved));capacity.put("filesystemDisponibleBytes",store.espacio());
        return Map.of("eliminacionHabilitada",enabled,"ejecutando",running,"mantenimiento",execution,"completo",queue(true),"historico",queue(false),"capacidadMultimedia",capacity);
    }
}