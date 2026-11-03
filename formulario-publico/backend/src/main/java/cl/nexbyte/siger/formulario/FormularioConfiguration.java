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
    @Bean TerritorioFormulario territorioFormulario(JsonMapper mapper)throws java.io.IOException{return new TerritorioFormulario(mapper);}
    @Bean ValidacionFormulario validacionFormulario(TerritorioFormulario territorio){return new ValidacionFormulario(territorio);}
    @Bean AdjuntosFormulario adjuntosFormulario(BaseJdbc db,RecorridosJdbc recorridos,Environment env,org.springframework.beans.factory.ObjectProvider<cl.nexbyte.siger.multimedia.AlmacenPrivado> store){
        int port=env.getProperty("formulario.adjuntos.clamav-port",Integer.class,0);
        AdjuntosFormulario.Escaner scanner=port==0?bytes->{throw new java.io.IOException("Inspección no configurada");}:new EscanerClamAv(env.getProperty("formulario.adjuntos.clamav-host","127.0.0.1"),port);
        return new AdjuntosFormulario(db,recorridos,scanner,store.getIfAvailable()).admision(cl.nexbyte.siger.custodia.GuardiaCapacidad.desde(env));
    }
    @Bean RecepcionFormularioJdbc recepcionFormularioJdbc(BaseJdbc db,RecorridosJdbc recorridos,AdjuntosFormulario files,JsonMapper mapper,ValidacionFormulario validation,Environment env)throws java.io.IOException {
        return new RecepcionFormularioJdbc(db,recorridos,files,mapper,validation,env.getRequiredProperty("publico.comprobante.key-id"),
            cl.nexbyte.siger.robustez.ComprobanteDigital.privatePem(Files.readString(Path.of(env.getRequiredProperty("publico.comprobante.private-key")))),
            cl.nexbyte.siger.robustez.JdbcRecepcionConfiguration.publicKeys(mapper,Path.of(env.getRequiredProperty("publico.comprobante.trusted-keys"))),
            cl.nexbyte.siger.robustez.JdbcRecepcionConfiguration.institutionalKeys(mapper,env),
            Clock.systemUTC(),env.getProperty("formulario.recepcion.habilitada",Boolean.class,false)&&env.getProperty("publico.recepcion.aviso-validado",Boolean.class,false),env.getProperty("formulario.adultos.habilitado",Boolean.class,false))
            .permiteDatosRegistrales(datosRegistralesPermitidos(env))
            .admision(cl.nexbyte.siger.custodia.GuardiaCapacidad.desde(env));
    }
    public static boolean datosRegistralesPermitidos(Environment env) {
        // Apagar el cliente no autoriza enviar antecedentes reales ya guardados en el recorrido.
        return env.getProperty("formulario.srcei.envio-autorizado",Boolean.class,false)
            || ("CONTROLLED_TEST".equals(env.getProperty("PISEE_ENVIRONMENT"))
                && env.getProperty("PISEE_CONTROLLED_TEST_AUTHORIZED",Boolean.class,false)
                && env.getProperty("formulario.srcei.envio-sintetico-autorizado",Boolean.class,false));
    }
    @Bean RecorridosJdbc recorridosJdbc(BaseJdbc db,JsonMapper mapper,ClientePisee client,Environment env) throws java.io.IOException {
        var keyPath=Path.of(env.getRequiredProperty("formulario.abuso-key-path"));
        if(Files.size(keyPath)<32||Files.size(keyPath)>128)throw new IllegalArgumentException("Clave antiabuso inválida");
        var permits=PoliticaPisee.autorizados(env,mapper);
        return new RecorridosJdbc(db,mapper,client::consultar,permits,Clock.systemUTC(),Files.readAllBytes(keyPath),
            env.getRequiredProperty("formulario.aviso-adulto"),env.getRequiredProperty("formulario.aviso-nna")).admision(cl.nexbyte.siger.custodia.GuardiaCapacidad.desde(env));
    }
}
