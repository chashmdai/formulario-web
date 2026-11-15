package cl.nexbyte.siger.custodia;

import cl.nexbyte.siger.robustez.BaseJdbc;
import java.util.UUID;

/** Borrado común; quien llama debe bloquear el recorrido y acreditar que puede minimizarlo. */
final class MinimizacionRecorrido {
    private MinimizacionRecorrido() {}

    static int retirarIdentidad(BaseJdbc db,UUID journey) {
        String id=journey.toString();
        int retiradas=db.sql.update("DELETE FROM citizen_person WHERE journey_id=?",id);
        return retiradas+db.sql.update("DELETE FROM citizen_identity_history WHERE journey_id=?",id);
    }

    static int retirar(BaseJdbc db,UUID journey) {
        String id=journey.toString();
        int retiradas=retirarIdentidad(db,journey);
        db.sql.update("UPDATE citizen_attachment SET name='retirado' WHERE journey_id=? AND bytes_retired=TRUE",id);
        db.sql.update("UPDATE media_upload SET name='retirado',error_code=NULL WHERE journey_id=? AND released=TRUE",id);
        db.sql.update("UPDATE citizen_journey SET state='MINIMIZADO' WHERE id=?",id);
        return retiradas;
    }
}
