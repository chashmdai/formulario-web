package cl.nexbyte.siger.robustez;
import java.util.*;
public final class ControlesOperacion{
    private final BaseJdbc db;
    public record Control(long revision,boolean aceptarNuevos,boolean recuperar,String contingencia){}
    public ControlesOperacion(BaseJdbc db){this.db=db;}
    public Control estado(){return db.sql.queryForObject("SELECT revision,accept_new,pull_enabled,contingency FROM operation_control WHERE id=1",(rs,n)->new Control(rs.getLong(1),rs.getBoolean(2),rs.getBoolean(3),rs.getString(4)));}
    public Control cambiar(long version,boolean aceptar,boolean recuperar,String contingencia,String motivo){
        if(contingencia==null||motivo==null||!Set.of("NORMAL","SOLO_CONSULTA").contains(contingencia)||!Set.of("MANTENIMIENTO","INCIDENTE","PRUEBA_LOCAL","REANUDACION","LIMITE_CAPACIDAD").contains(motivo)
            ||("SOLO_CONSULTA".equals(contingencia)&&(aceptar||recuperar)))throw new FalloTecnico(400);
        return db.transaction(()->{
            var before=db.sql.queryForObject("SELECT revision,accept_new,pull_enabled,contingency FROM operation_control WHERE id=1 FOR UPDATE",(rs,n)->new Control(rs.getLong(1),rs.getBoolean(2),rs.getBoolean(3),rs.getString(4)));
            if(db.sql.update("UPDATE operation_control SET revision=revision+1,accept_new=?,pull_enabled=?,contingency=? WHERE id=1 AND revision=?",aceptar,recuperar,contingencia,version)!=1)throw new FalloTecnico(412);
            var after=estado();
            db.sql.update("INSERT INTO control_audit(revision,at_ms,actor,before_accept,before_pull,before_mode,after_accept,after_pull,after_mode,reason) VALUES(?,?,?,?,?,?,?,?,?,?)",after.revision(),System.currentTimeMillis(),"credencial-operacion",before.aceptarNuevos(),before.recuperar(),before.contingencia(),after.aceptarNuevos(),after.recuperar(),after.contingencia(),motivo);
            db.event("CONTROL","control","REV_"+after.revision()+"_"+motivo);
            return after;
        });
    }
}
