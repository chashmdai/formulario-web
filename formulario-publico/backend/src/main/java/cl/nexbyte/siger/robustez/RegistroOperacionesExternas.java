package cl.nexbyte.siger.robustez;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;

/** Preparación durable para proveedores: un timeout ambiguo exige consulta o revisión, nunca reenvío ciego. */
public final class RegistroOperacionesExternas{
    private final BaseJdbc db;
    public record Operacion(UUID id,String tipo,String estado,String referenciaProveedor,String codigo,long revision){}
    public RegistroOperacionesExternas(BaseJdbc db){this.db=db;}
    public Operacion preparar(UUID id,String tipo){
        if(id==null||tipo==null||!Set.of("NOTIFICACION","INTEGRACION").contains(tipo))throw new FalloTecnico(400);
        try{db.transaction(()->{db.sql.update("INSERT INTO external_operation(id,kind,state,last_code,updated_ms,revision) VALUES(?,?,'PREPARADA','NUEVA',?,0)",id.toString(),tipo,System.currentTimeMillis());db.event(tipo,id.toString(),"PREPARADA");return null;});}
        catch(DuplicateKeyException e){if(!consultar(id).tipo().equals(tipo))throw new FalloTecnico(409);}
        return consultar(id);
    }
    public Operacion consultar(UUID id){if(id==null)throw new FalloTecnico(400);try{return db.sql.queryForObject("SELECT id,kind,state,provider_ref,last_code,revision FROM external_operation WHERE id=?",(rs,n)->new Operacion(UUID.fromString(rs.getString(1)),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getLong(6)),id.toString());}catch(org.springframework.dao.EmptyResultDataAccessException e){throw new FalloTecnico(404);}}
    public Operacion transicion(UUID id,long version,String estado,String referencia,String codigo){
        if(id==null||version<0||estado==null||codigo==null||!Set.of("SOLICITADA","DESCONOCIDA","CONFIRMADA","REQUIERE_REVISION").contains(estado)
            ||!Set.of("ENVIO_INICIADO","RESPUESTA_ACEPTADA","TIMEOUT_AMBIGUO","CONSULTA_CONFIRMADA","ERROR_PERMANENTE").contains(codigo)
            ||(referencia!=null&&!referencia.matches("[A-Za-z0-9_-]{1,120}")))throw new FalloTecnico(400);
        if((estado.equals("SOLICITADA")&&!codigo.equals("ENVIO_INICIADO"))||(estado.equals("DESCONOCIDA")&&!codigo.equals("TIMEOUT_AMBIGUO"))||(estado.equals("CONFIRMADA")&&(referencia==null||!Set.of("RESPUESTA_ACEPTADA","CONSULTA_CONFIRMADA").contains(codigo)))||(estado.equals("REQUIERE_REVISION")&&!codigo.equals("ERROR_PERMANENTE")))throw new FalloTecnico(400);
        return db.transaction(()->{
            var old=consultar(id);
            boolean allowed=switch(old.estado()){
                case "PREPARADA"->estado.equals("SOLICITADA");
                case "SOLICITADA"->Set.of("DESCONOCIDA","CONFIRMADA","REQUIERE_REVISION").contains(estado);
                case "DESCONOCIDA"->Set.of("CONFIRMADA","REQUIERE_REVISION").contains(estado);
                default->false;
            };
            if(!allowed||old.revision()!=version||("CONFIRMADA".equals(estado)&&referencia==null))throw new FalloTecnico(409);
            if(db.sql.update("UPDATE external_operation SET state=?,provider_ref=?,last_code=?,updated_ms=?,revision=revision+1 WHERE id=? AND revision=?",estado,referencia,codigo,System.currentTimeMillis(),id.toString(),version)!=1)throw new FalloTecnico(409);
            db.event(old.tipo(),id.toString(),estado);return consultar(id);
        });
    }
}
