package cl.nexbyte.siger.formulario;

import cl.nexbyte.siger.robustez.BaseJdbc;
import cl.nexbyte.siger.srcei.*;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;

@Configuration @Profile("jdbc-reception") @ConditionalOnProperty(name="formulario.habilitado",havingValue="true")
public class FormularioConfiguration {
    @Bean RecorridosJdbc recorridosJdbc(BaseJdbc db,JsonMapper mapper,ClientePisee client,Environment env) throws java.io.IOException {
        if(env.matchesProfiles("production","prod")||!"SANDBOX".equals(env.getProperty("siger.claveunica.environment","SANDBOX")))
            throw new IllegalStateException("Recorridos nuevos aún limitados a laboratorio Sandbox");
        var keyPath=Path.of(env.getRequiredProperty("formulario.abuso-key-path"));
        if(Files.size(keyPath)<32||Files.size(keyPath)>128)throw new IllegalArgumentException("Clave antiabuso inválida");
        var allowed=new HashSet<RunConsulta>();
        String file=env.getProperty("PISEE_ALLOWED_RUNS_PATH");
        if(file!=null) {
            var path=Path.of(file);
            if(Files.size(path)>4096)throw new IllegalArgumentException("Autorizaciones DEV excesivas");
            var node=mapper.readTree(Files.readString(path));
            var entries=node.isArray()?node:mapper.createArrayNode().add(node);
            if(entries.size()>10)throw new IllegalArgumentException("Lista DEV acotada requerida");
            for(var entry:entries) {
                if(!entry.isObject()||entry.size()!=2||!entry.has("numero")||!entry.has("dv"))throw new IllegalArgumentException("Formato de autorizaciones DEV inválido");
                allowed.add(new RunConsulta(entry.path("numero").asString(),entry.path("dv").asString()));
            }
        }
        var permits=Set.copyOf(allowed);
        return new RecorridosJdbc(db,mapper,client::consultar,permits::contains,Clock.systemUTC(),Files.readAllBytes(keyPath),
            env.getRequiredProperty("formulario.aviso-adulto"),env.getRequiredProperty("formulario.aviso-nna"));
    }
}
