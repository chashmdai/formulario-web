package cl.nexbyte.siger.custodia;

import cl.nexbyte.siger.multimedia.AlmacenPrivado;
import cl.nexbyte.siger.robustez.BaseJdbc;
import java.time.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.*;

@Configuration @Profile("jdbc-reception") @EnableScheduling
@Import(AlmacenMantenimientoConfiguration.class)
@ConditionalOnProperty(name={"formulario.habilitado","formulario.minimizacion-borradores.habilitada"},havingValue="true")
public class MinimizacionBorradoresConfiguration {
    private static final String PREFIX="formulario.minimizacion-borradores.";

    private static Duration duracion(Environment env,String key,String fallback) {
        try {return Duration.parse(env.getProperty(PREFIX+key,fallback));}
        catch(RuntimeException invalid) {throw new IllegalArgumentException(PREFIX+key+" debe ser una duración ISO-8601",invalid);}
    }

    @Bean MinimizacionBorradores minimizacionBorradores(BaseJdbc db,AlmacenPrivado store,Environment env) {
        var plazo=MinimizacionBorradores.validarPlazo(duracion(env,"plazo","PT48H"));
        var intervalo=duracion(env,"intervalo","PT5M");
        if(intervalo.compareTo(Duration.ofMinutes(1))<0||intervalo.compareTo(Duration.ofDays(1))>0)
            throw new IllegalArgumentException(PREFIX+"intervalo debe estar entre PT1M y P1D");
        return new MinimizacionBorradores(db,store,Clock.systemUTC(),plazo);
    }

    @Bean Programador programadorMinimizacionBorradores(MinimizacionBorradores service) {return new Programador(service);}

    public static final class Programador {
        private final MinimizacionBorradores service;
        public Programador(MinimizacionBorradores service) {this.service=service;}
        @Scheduled(fixedDelayString="${formulario.minimizacion-borradores.intervalo:PT5M}",initialDelayString="PT1M")
        public void ejecutar() {service.ejecutar();}
    }
}
