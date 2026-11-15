package cl.nexbyte.siger.soporte;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Informes y señales del coordinador, en el build efectivo de Maven (target por defecto). */
public final class SalidaPruebas {
    private SalidaPruebas() {}

    public static Path archivo(String nombre) throws IOException {
        var directorio = Path.of(System.getProperty("siger.build.directory"));
        Files.createDirectories(directorio);
        return directorio.resolve(nombre);
    }
}
