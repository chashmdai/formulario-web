package cl.nexbyte.siger;

import cl.nexbyte.siger.identidad.ClaveUnicaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ClaveUnicaProperties.class)
public class FormularioPublicoApplication {
    public static void main(String[] args) {
        if (java.util.Arrays.asList(args).contains("--migrate")) { cl.nexbyte.siger.robustez.MigrarDatos.ejecutar(); return; }
        SpringApplication.run(FormularioPublicoApplication.class, args);
    }
}
