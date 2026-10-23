package cl.nexbyte.siger.robustez;
import com.zaxxer.hikari.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.function.Supplier;

/** Infraestructura técnica local a cada artefacto, sin dependencia de la otra aplicación. */
public final class BaseJdbc implements AutoCloseable {
    public final JdbcTemplate sql;
    public final TransactionTemplate tx;
    private final HikariDataSource pool;
    public BaseJdbc(String url,String user,String password,int max) {
        if(max<1||max>64)throw new IllegalArgumentException("Pool fuera de límites");
        var config=new HikariConfig(); config.setJdbcUrl(url); config.setUsername(user); config.setPassword(password);
        config.setMaximumPoolSize(max);config.setMinimumIdle(0);config.setConnectionTimeout(3000);
        config.setValidationTimeout(1000);config.setInitializationFailTimeout(-1);config.setAutoCommit(true);
        config.setPoolName("recepcion-db");
        pool=new HikariDataSource(config); sql=new JdbcTemplate(pool);sql.setQueryTimeout(4);
        tx=new TransactionTemplate(new JdbcTransactionManager(pool));tx.setIsolationLevelName("ISOLATION_READ_COMMITTED");tx.setTimeout(5);
    }
    public <T>T transaction(Supplier<T> action){return tx.execute(status->action.get());}
    public void migrate(String component) {
        String vendor=pool.getJdbcUrl().startsWith("jdbc:h2:")?"h2":"mysql";
        Flyway.configure().dataSource(pool).locations("classpath:db/"+component+"/"+vendor)
            .cleanDisabled(true).validateMigrationNaming(true).load().migrate();
    }
    public void event(String category,String id,String code){
        sql.update("INSERT INTO operational_event(at_ms,category,aggregate_id,code) VALUES(?,?,?,?)",System.currentTimeMillis(),category,id,code);
    }
    public void close(){pool.close();}
}
