package cl.nexbyte.siger.multimedia;

import cl.nexbyte.siger.config.OrigenCliente;
import cl.nexbyte.siger.robustez.FalloTecnico;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Cupos independientes, sin espera ni colas; los orígenes sólo viven mientras hay transferencias. */
@Component
public final class CuposTransferencia {
    private final OrigenCliente origen;
    private final Pool cargas, ciudadanas, siger;
    private final Duration plazoCarga, plazoCiudadano, plazoSiger;

    public CuposTransferencia(Environment env, OrigenCliente origen) {
        this.origen = origen;
        // Lectura/escritura no bloqueantes: un cupo cuesta memoria acotada (≤ 8 MiB por bloque), no un hilo.
        cargas = new Pool(numero(env, "cargas", 6), numero(env, "cargas-por-origen", 1));
        ciudadanas = new Pool(numero(env, "descargas", 8), numero(env, "descargas-por-origen", 2));
        siger = new Pool(numero(env, "descargas-siger", 2), 0);
        // 8 MiB en 3 min ≈ 47 KB/s: conexiones móviles lentas siguen pudiendo cargar.
        plazoCarga = plazo(env, "plazo-carga", "PT3M");
        plazoCiudadano = plazo(env, "plazo-descarga", "PT10M");
        plazoSiger = plazo(env, "plazo-siger", "PT15M");
    }

    public Permiso carga(HttpServletRequest request) { return cargas.tomar(origen.de(request)); }
    public Permiso descarga(HttpServletRequest request) { return ciudadanas.tomar(origen.de(request)); }
    public Permiso integracion() { return siger.tomar(null); }
    public Duration plazoCarga() { return plazoCarga; }
    public Duration plazoDescarga(boolean integracion) { return integracion ? plazoSiger : plazoCiudadano; }

    private static int numero(Environment env, String key, int fallback) {
        int value = env.getProperty("formulario.transferencias." + key, Integer.class, fallback);
        if (value < 1 || value > 16) throw new IllegalArgumentException("Cupo de transferencia inválido: " + key);
        return value;
    }

    private static Duration plazo(Environment env, String key, String fallback) {
        var value = Duration.parse(env.getProperty("formulario.transferencias." + key, fallback));
        if (value.toMillis() < 100 || value.compareTo(Duration.ofDays(1)) > 0)
            throw new IllegalArgumentException("Plazo de transferencia inválido: " + key);
        return value;
    }

    private static final class Pool {
        private final int capacidad, porOrigen;
        private int activos;
        private final Map<String, Integer> origenes = new HashMap<>();
        Pool(int capacidad, int porOrigen) { this.capacidad = capacidad; this.porOrigen = porOrigen; }
        synchronized Permiso tomar(String origen) {
            int usados = origenes.getOrDefault(origen, 0);
            if (activos >= capacidad || porOrigen > 0 && usados >= porOrigen) throw new FalloTecnico(429);
            activos++;
            if (porOrigen > 0) origenes.put(origen, usados + 1);
            return new Permiso(() -> liberar(origen));
        }
        private synchronized void liberar(String origen) {
            activos--;
            if (porOrigen > 0) origenes.compute(origen, (key, usados) -> usados == 1 ? null : usados - 1);
        }
    }

    /** Idempotente: timeout, error y finalización pueden competir por la misma liberación. */
    public static final class Permiso implements AutoCloseable {
        private Runnable liberar;
        private Permiso(Runnable liberar) { this.liberar = liberar; }
        @Override public synchronized void close() {
            if (liberar != null) { liberar.run(); liberar = null; }
        }
    }
}
