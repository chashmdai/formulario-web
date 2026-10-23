import cl.nexbyte.siger.srcei.*;
import java.nio.file.*;
import java.net.URI;
import java.time.*;
import java.util.regex.Pattern;

/** Verificador manual DEV de UNA consulta; no se ejecuta en la batería de tests. */
class SondaPisee {
    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !args[2].equals("--autorizar-dev"))
            throw new IllegalArgumentException("Uso: SondaPisee directorio-privado origen-pisee --autorizar-dev");
        Path dir = Path.of(args[0]);
        String privateJson = Files.readString(dir.resolve("run-autorizado.json"));
        var number = Pattern.compile("\"numero\"\\s*:\\s*\"([0-9]+)\"").matcher(privateJson);
        var dv = Pattern.compile("\"dv\"\\s*:\\s*\"([0-9Kk])\"").matcher(privateJson);
        if (!number.find() || !dv.find()) throw new IllegalArgumentException("Identificador DEV no configurado");
        try (var client = new ClientePisee(true, URI.create(args[1]), Files.readString(dir.resolve("local-authorization")).strip(),
                Duration.ofSeconds(20), 1, Clock.systemUTC())) {
            var result = client.consultar(new RunConsulta(number.group(1), dv.group(1)));
            System.out.println("estado=" + result.estado() + "; cantidad=" + result.atributos().size()
                + "; atributos=" + result.atributos().keySet().stream().sorted().toList());
            if (result.estado() != ResultadoSrcei.Estado.COMPLETO) System.exit(1);
        }
    }
}
