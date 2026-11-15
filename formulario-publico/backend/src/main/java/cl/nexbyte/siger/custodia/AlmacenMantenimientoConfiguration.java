package cl.nexbyte.siger.custodia;

import cl.nexbyte.siger.multimedia.AlmacenPrivado;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** Sin multimedia, ambos mantenimientos comparten una única instancia y sus gates. */
@Configuration @Profile("jdbc-reception")
@ConditionalOnProperty(name="formulario.habilitado",havingValue="true")
@ConditionalOnExpression("!${formulario.multimedia.habilitada:false} && (${ciclo-vida.online-habilitado:false} || ${formulario.minimizacion-borradores.habilitada:false})")
public class AlmacenMantenimientoConfiguration {
    @Bean(destroyMethod="close") AlmacenPrivado almacenMantenimiento(Environment env)throws IOException {
        return new AlmacenPrivado(Path.of(env.getRequiredProperty("formulario.multimedia.directorio")));
    }
}
