package cl.nexbyte.siger.robustez;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;

final class TestDatabase {
    static BaseJdbc open(Path directory) {
        var supplied=System.getenv("ROBUST_DB_URL");
        String url=supplied==null?"jdbc:h2:file:"+directory.resolve("publico").toAbsolutePath().toString().replace('\\','/')+";DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0":supplied;
        if(supplied!=null&&!url.startsWith("jdbc:mysql://127.0.0.1:13306/robust_public_test?"))throw new IllegalStateException("Las pruebas solo admiten MySQL loopback de pruebas");
        var user=supplied==null?"sa":System.getenv("ROBUST_DB_USER");var password=supplied==null?"":System.getenv("ROBUST_DB_PASSWORD");
        try(var migration=new BaseJdbc(url,supplied==null?user:System.getenv("ROBUST_DB_MIGRATION_USER"),supplied==null?password:System.getenv("ROBUST_DB_MIGRATION_PASSWORD"),2)){migration.migrate("publico");}
        var db=new BaseJdbc(url,user,password,12);
        if(supplied!=null){
            db.sql.update("DELETE FROM control_audit");db.sql.update("DELETE FROM public_delivery");db.sql.update("DELETE FROM operational_event");db.sql.update("DELETE FROM external_operation");
            db.sql.update("UPDATE operation_control SET revision=0,accept_new=FALSE,pull_enabled=FALSE,contingency='SOLO_CONSULTA'");
        }
        return db;
    }
    static void migration(BaseJdbc app,Consumer<JdbcTemplate> action){
        var supplied=System.getenv("ROBUST_DB_URL");
        if(supplied==null){action.accept(app.sql);return;}
        try(var db=new BaseJdbc(supplied,System.getenv("ROBUST_DB_MIGRATION_USER"),System.getenv("ROBUST_DB_MIGRATION_PASSWORD"),2)){action.accept(db.sql);}
    }
}
