package cl.nexbyte.siger.srcei;

import java.nio.file.*;
import java.util.*;
import org.springframework.core.env.*;
import tools.jackson.databind.json.JsonMapper;

/** Sonda sin Spring/BD/Comunicaciones: usa exactamente el cliente y política del JAR candidato. */
public final class SondaAdaptadorPisee {
    public static void main(String[] args) {
        int exit=1;
        try {
            if(args.length!=1)throw new IllegalArgumentException();
            if(org.slf4j.LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext context)
                context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(ch.qos.logback.classic.Level.OFF);
            Path config=Path.of(args[0]);
            if(Files.isSymbolicLink(config)||Files.size(config)>16384)throw new IllegalArgumentException();
            var permissions=Files.getPosixFilePermissions(config);
            if(permissions.stream().anyMatch(p->p.name().startsWith("GROUP_")||p.name().startsWith("OTHERS_")))throw new IllegalArgumentException();
            var values=new Properties();try(var reader=Files.newBufferedReader(config)){values.load(reader);}
            var env=new StandardEnvironment();
            env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            env.getPropertySources().addFirst(new PropertiesPropertySource("campana-privada",values));
            if(!env.getProperty("PISEE_SRCEI_ENABLED",Boolean.class,false)||!"PRODUCTION".equals(PoliticaPisee.validar(env)))throw new IllegalArgumentException();
            var json=new JsonMapper();var authorized=PoliticaPisee.autorizados(env,json);
            var tree=json.readTree(Files.readString(Path.of(env.getRequiredProperty("PISEE_ALLOWED_RUNS_PATH"))));
            if(tree.isArray()){if(tree.size()!=1)throw new IllegalArgumentException();tree=tree.get(0);}
            var run=new RunConsulta(tree.path("numero").asString(),tree.path("dv").asString());
            if(!authorized.test(run))throw new IllegalArgumentException();
            try(var client=new PiseeConfiguration().clientePisee(env)) {
                var result=client.consultar(run);
                System.out.println(json.writeValueAsString(Map.of("estado",result.estado(),"motivo",result.motivo(),
                    "cantidadAtributosNormalizados",result.atributos().size(),"modo","PRODUCTION","consultaIntentada",true,"persistencia",false)));
                exit=result.estado()==ResultadoSrcei.Estado.COMPLETO?0:2;
            }
        } catch(Exception rejected) {
            // No imprimir causas, rutas privadas, identidad, XML ni valores registrales.
            System.out.println("{\"estado\":\"RECHAZADO\",\"persistencia\":false}");
        }
        if(exit!=0)System.exit(exit);
    }
}
