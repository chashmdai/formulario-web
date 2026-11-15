package cl.nexbyte.siger.custodia;

import cl.nexbyte.siger.multimedia.AlmacenPrivado;
import cl.nexbyte.siger.robustez.BaseJdbc;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Retira identidad de borradores sin recepción; nunca retira bytes ni custodia. */
public final class MinimizacionBorradores {
    public record Resultado(int minimizadas,int conservadas,int incidencias,int completadas) {}
    private enum Intento { IDENTIDAD, COMPLETA, COMPLETADA_SIN_IDENTIDAD, CONSERVADA, INCIDENCIA }
    private final BaseJdbc db;
    private final AlmacenPrivado store;
    private final Clock clock;
    private final Duration plazo;
    private final AtomicBoolean running=new AtomicBoolean();
    private final AtomicLong minimizadas=new AtomicLong(),conservadas=new AtomicLong(),incidencias=new AtomicLong();
    private final AtomicLong completadas=new AtomicLong();
    private volatile long ultimaPasada;
    private String cursor="";

    public MinimizacionBorradores(BaseJdbc db,AlmacenPrivado store,Clock clock,Duration plazo) {
        this.db=Objects.requireNonNull(db);this.store=Objects.requireNonNull(store);this.clock=Objects.requireNonNull(clock);
        this.plazo=validarPlazo(plazo);
    }

    public static Duration validarPlazo(Duration plazo) {
        if(plazo==null||plazo.compareTo(Duration.ofHours(1))<0||plazo.compareTo(Duration.ofDays(90))>0)
            throw new IllegalArgumentException("formulario.minimizacion-borradores.plazo debe estar entre PT1H y P90D");
        return plazo;
    }

    public Resultado ejecutar() {
        if(!running.compareAndSet(false,true))return new Resultado(0,0,0,0);
        int done=0,kept=0,failed=0,complete=0;
        try {
            long cutoff=clock.millis()-plazo.toMillis();
            var rows=candidatos(cutoff,cursor);
            if(rows.isEmpty()&&!cursor.isEmpty())rows=candidatos(cutoff,"");
            for(String id:rows) {
                switch(minimizar(UUID.fromString(id),false)) {
                    case IDENTIDAD -> done++;
                    case COMPLETA -> {done++;complete++;}
                    case COMPLETADA_SIN_IDENTIDAD -> complete++;
                    case CONSERVADA -> kept++;
                    case INCIDENCIA -> failed++;
                }
                cursor=id;
            }
            if(rows.isEmpty())cursor="";
        } catch(RuntimeException unavailable) {
            incidencias.incrementAndGet();failed++;
        } finally {
            ultimaPasada=clock.millis();running.set(false);
        }
        return new Resultado(done,kept,failed,complete);
    }

    private List<String> candidatos(long cutoff,String after) {
        return db.sql.queryForList("""
            SELECT j.id FROM citizen_journey j
            WHERE j.state IN ('BORRADOR','CANCELADO') AND j.expires_ms<=? AND j.id>?
              AND NOT EXISTS(SELECT 1 FROM citizen_submission s WHERE s.journey_id=j.id)
              AND (EXISTS(SELECT 1 FROM citizen_person p WHERE p.journey_id=j.id)
                OR EXISTS(SELECT 1 FROM citizen_identity_history h WHERE h.journey_id=j.id)
                OR (NOT EXISTS(SELECT 1 FROM media_upload u WHERE u.journey_id=j.id AND u.released=FALSE)
                  AND NOT EXISTS(SELECT 1 FROM citizen_attachment a WHERE a.journey_id=j.id AND a.bytes_retired=FALSE)))
            ORDER BY j.id LIMIT 50
            """,String.class,cutoff,after);
    }

    /** Se invoca después de confirmar la cancelación; un bloqueo o incidencia se reintenta en la pasada. */
    public void alCancelar(UUID journey) {minimizar(journey,true);}

    private Intento minimizar(UUID journey,boolean cancelacion) {
        try(var gate=store.mantenimiento(journey)) {
            if(gate==null)return conservar();
            try(var backup=store.guardia()) {
                Intento changed=db.transaction(()->{
                    String id=journey.toString();
                    var rows=db.sql.queryForList("SELECT state,expires_ms FROM citizen_journey WHERE id=? FOR UPDATE",id);
                    if(rows.isEmpty())return Intento.CONSERVADA;
                    var row=rows.getFirst();String state=row.get("state").toString();
                    boolean eligible=cancelacion?"CANCELADO".equals(state):Set.of("BORRADOR","CANCELADO").contains(state);
                    if(!eligible)return Intento.CONSERVADA;
                    if(!cancelacion&&((Number)row.get("expires_ms")).longValue()>clock.millis()-plazo.toMillis())return Intento.CONSERVADA;
                    if(db.sql.queryForObject("SELECT COUNT(*) FROM citizen_submission WHERE journey_id=?",Integer.class,id)>0)
                        return Intento.CONSERVADA;
                    // La identidad no depende de los bytes. U1/ciclo de vida necesitan conservar sus estados.
                    if(db.sql.queryForObject("SELECT COUNT(*) FROM media_upload WHERE journey_id=? AND released=FALSE",Integer.class,id)>0
                        ||db.sql.queryForObject("SELECT COUNT(*) FROM citizen_attachment WHERE journey_id=? AND bytes_retired=FALSE",Integer.class,id)>0) {
                        if(MinimizacionRecorrido.retirarIdentidad(db,journey)==0)return Intento.CONSERVADA;
                        db.event("MINIMIZACION_BORRADORES",id,"IDENTIDAD_BORRADOR_RETIRADA");
                        return Intento.IDENTIDAD;
                    }
                    int retiradas=MinimizacionRecorrido.retirar(db,journey);
                    db.sql.update("UPDATE citizen_journey SET owner_session=? WHERE id=?","0".repeat(64),id);
                    db.event("MINIMIZACION_BORRADORES",id,"BORRADOR_PERSONAL_RETIRADO");
                    return retiradas>0?Intento.COMPLETA:Intento.COMPLETADA_SIN_IDENTIDAD;
                });
                if(changed==Intento.CONSERVADA)return conservar();
                if(changed==Intento.IDENTIDAD||changed==Intento.COMPLETA)minimizadas.incrementAndGet();
                if(changed==Intento.COMPLETA||changed==Intento.COMPLETADA_SIN_IDENTIDAD)completadas.incrementAndGet();
                return changed;
            }
        } catch(IOException|RuntimeException unavailable) {
            incidencias.incrementAndGet();return Intento.INCIDENCIA;
        }
    }

    private Intento conservar() {conservadas.incrementAndGet();return Intento.CONSERVADA;}

    /** Contadores desde el arranque, sin identificadores ni contenido ciudadano. */
    public Map<String,Object> estado() {
        return Map.of("minimizadas",minimizadas.get(),"conservadas",conservadas.get(),"incidencias",incidencias.get(),
            "completadas",completadas.get(),"ultimaPasadaMs",ultimaPasada,"ejecutando",running.get(),"plazo",plazo.toString());
    }
}
