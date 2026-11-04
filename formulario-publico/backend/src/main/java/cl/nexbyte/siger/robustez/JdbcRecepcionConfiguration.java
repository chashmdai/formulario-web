package cl.nexbyte.siger.robustez;

import java.nio.file.*;
import java.security.PublicKey;
import java.time.Clock;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;

@Configuration @Profile("jdbc-reception")
public class JdbcRecepcionConfiguration {
    @Bean BaseJdbc recepcionDb(Environment env) {
        var url=env.getRequiredProperty("publico.db.url");
        ConfiguracionDatos.validar(url,env.getProperty("datos.mysql-local",Boolean.class,false)&&"127.0.0.1".equals(env.getProperty("server.address")));
        if(env.matchesProfiles("local-integration-demo"))throw new IllegalArgumentException("Perfiles de recepción incompatibles");
        return new BaseJdbc(url,env.getRequiredProperty("publico.db.user"),env.getRequiredProperty("publico.db.password"),env.getProperty("publico.db.pool",Integer.class,12));
    }
    @Bean ControlesOperacion controles(BaseJdbc db,Environment env){
        boolean enabled=env.getProperty("publico.integracion.habilitada",Boolean.class,true);
        boolean custody=env.getProperty("publico.recepcion.custodia-durable-autorizada",Boolean.class,false);
        var controls=new ControlesOperacion(db,enabled||custody,enabled);
        var state=controls.estado();
        if(state.aceptarNuevos()&&!enabled&&!custody||state.recuperar()&&!enabled)
            throw new IllegalStateException("Controles persistidos incompatibles con autorización de recepción/confianza ACK");
        return controls;
    }
    @Bean RegistroOperacionesExternas operaciones(BaseJdbc db){return new RegistroOperacionesExternas(db);}
    @Bean RecepcionJdbc recepcionJdbc(BaseJdbc db,JsonMapper mapper,Environment env)throws java.io.IOException {
        var keyId=env.getRequiredProperty("publico.comprobante.key-id");
        var privateKey=ComprobanteDigital.privatePem(Files.readString(Path.of(env.getRequiredProperty("publico.comprobante.private-key"))));
        var trust=publicKeys(mapper,Path.of(env.getRequiredProperty("publico.comprobante.trusted-keys")));
        return new RecepcionJdbc(db,mapper,env.getRequiredProperty("publico.recepcion.aviso-version"),
                env.getProperty("publico.recepcion.habilitada",Boolean.class,false)&&env.getProperty("publico.recepcion.aviso-validado",Boolean.class,false),
                keyId,privateKey,trust,Clock.systemUTC(),env.getProperty("publico.recepcion.lease-ms",Long.class,300000L),
                institutionalKeys(mapper,env),env.getProperty("formulario.abuso-key-path")==null?null:Files.readAllBytes(Path.of(env.getProperty("formulario.abuso-key-path")))).admision(cl.nexbyte.siger.custodia.GuardiaCapacidad.desde(env));
    }
    public static Map<String,PublicKey> institutionalKeys(JsonMapper mapper,Environment env)throws java.io.IOException {
        if(env.getProperty("publico.integracion.habilitada",Boolean.class,true))
            return publicKeys(mapper,Path.of(env.getRequiredProperty("publico.comprobante.institutional-trusted-keys")));
        boolean custody=env.getProperty("publico.recepcion.custodia-durable-autorizada",Boolean.class,false);
        for(String flag:List.of("ciclo-vida.online-habilitado","ciclo-vida.eliminacion.habilitada"))
            if(env.getProperty(flag,Boolean.class,false))throw new IllegalStateException("Sin confianza ACK se prohíbe retirada/mantenimiento: "+flag);
        if(!custody)for(String flag:List.of("publico.recepcion.habilitada","publico.recepcion.aviso-validado","publico.nna.habilitado",
                "formulario.adultos.habilitado","formulario.recepcion.habilitada","ciclo-vida.online-habilitado","ciclo-vida.eliminacion.habilitada"))
            if(env.getProperty(flag,Boolean.class,false))throw new IllegalStateException("Sin confianza ACK se exige recepción y custodia cerradas: "+flag);
        return Map.of(); // Ninguna clave confiada: ninguna firma ACK puede validarse.
    }
    public static Map<String,PublicKey> publicKeys(JsonMapper mapper,Path path)throws java.io.IOException {
        if(Files.size(path)>65536)throw new IllegalArgumentException("Archivo de claves demasiado grande");
        var value=mapper.readTree(Files.readString(path));var keys=new HashMap<String,PublicKey>();
        for(var entry:value.properties()){
            if(!entry.getKey().matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Identificador de clave inválido");
            keys.put(entry.getKey(),ComprobanteDigital.publicPem(entry.getValue().asString()));
        }
        if(keys.isEmpty()||keys.size()>20)throw new IllegalArgumentException("Claves de confianza requeridas");return Map.copyOf(keys);
    }
}
