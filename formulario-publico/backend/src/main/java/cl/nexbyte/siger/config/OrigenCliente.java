package cl.nexbyte.siger.config;

import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.time.Clock;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Origen del ciudadano para los límites antiabuso (nunca para autenticar ni registrar).
 *
 * <p>Detrás de Cloudflare Tunnel y Caddy, la conexión TCP del backend siempre proviene del proxy propio. Solo cuando
 * esa conexión llega desde un proxy configurado se acepta UNA cabecera interna, que el proxy sobrescribe en cada
 * solicitud con la dirección que él mismo validó. Nunca se usa X-Forwarded-For ni otra cabecera aportada por el
 * cliente. Ante cualquier duda (sin configuración, proxy no reconocido, cabecera ausente, repetida o inválida) se usa
 * la dirección TCP, que es el comportamiento conservador anterior.</p>
 *
 * <p>IPv6 se agrupa por /64: una misma conexión doméstica o móvil no puede eludir la cuota rotando direcciones.</p>
 */
@Component
public class OrigenCliente {
    private static final long CACHE_MS = 30_000;
    private final String header;
    private final List<String> hosts = new ArrayList<>();
    private final List<byte[][]> networks = new ArrayList<>();
    private final Clock clock;
    private volatile Set<InetAddress> resolved = Set.of();
    private volatile long resolvedAt = 0;

    @org.springframework.beans.factory.annotation.Autowired
    public OrigenCliente(Environment env) { this(env.getProperty("formulario.origen.cabecera", ""), env.getProperty("formulario.origen.proxies-confiables", ""), Clock.systemUTC()); }

    OrigenCliente(String header, String trusted, Clock clock) {
        this.clock = clock;
        header = header == null ? "" : header.strip();
        if (header.isEmpty()) { this.header = null; return; }
        if (!header.matches("X-[A-Za-z0-9-]{1,60}") || Set.of("x-forwarded-for", "x-real-ip", "x-forwarded-host").contains(header.toLowerCase(Locale.ROOT)))
            throw new IllegalStateException("Cabecera de origen inválida: debe ser una cabecera interna propia del proxy");
        for (var entry : trusted == null ? new String[0] : trusted.split(",")) {
            var value = entry.strip();
            if (value.isEmpty()) continue;
            if (value.matches("[0-9a-fA-F.:]+/[0-9]{1,3}")) networks.add(red(value));
            else if (value.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*")) hosts.add(value);
            else throw new IllegalStateException("Proxy confiable inválido: usar CIDR o nombre de servicio interno");
        }
        if (networks.isEmpty() && hosts.isEmpty()) throw new IllegalStateException("La cabecera de origen exige proxies confiables explícitos");
        this.header = header;
    }

    /** Clave de origen para los contadores antiabuso; nunca se almacena en claro. */
    public String de(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (header == null) return clave(peer);
        var values = Collections.list(request.getHeaders(header));
        if (values.size() != 1 || !confiable(peer)) return clave(peer);
        var client = literal(values.getFirst().strip());
        return client == null ? clave(peer) : clave(client.getHostAddress());
    }

    private boolean confiable(String peer) {
        var address = literal(peer);
        if (address == null) return false;
        for (var net : networks) if (contiene(net, address.getAddress())) return true;
        if (hosts.isEmpty()) return false;
        long now = clock.millis();
        if (now - resolvedAt > CACHE_MS || !resolved.contains(address) && now - resolvedAt > 1_000) {
            // DNS interno de Docker: solo resuelve servicios de la misma red privada.
            var fresh = new HashSet<InetAddress>();
            for (var host : hosts) try { fresh.addAll(List.of(InetAddress.getAllByName(host))); } catch (java.net.UnknownHostException ignored) { /* Sin confianza. */ }
            resolved = Set.copyOf(fresh); resolvedAt = now;
        }
        return resolved.contains(address);
    }

    /** Literal IP estricto, sin resolución DNS ni identificadores de zona. */
    static InetAddress literal(String value) {
        if (value == null || value.isEmpty() || value.length() > 45 || !value.matches("[0-9a-fA-F.:]+")) return null;
        try { return InetAddress.ofLiteral(value); } catch (IllegalArgumentException invalid) { return null; }
    }

    static String clave(String value) {
        var address = literal(value);
        if (address == null) return value;
        if (address instanceof Inet6Address) {
            var prefix = Arrays.copyOf(address.getAddress(), 16);
            Arrays.fill(prefix, 8, 16, (byte) 0);
            try { return InetAddress.getByAddress(prefix).getHostAddress() + "/64"; } catch (java.net.UnknownHostException impossible) { return value; }
        }
        return address.getHostAddress();
    }

    private static byte[][] red(String cidr) {
        var parts = cidr.split("/");
        var address = literal(parts[0]);
        int bits = Integer.parseInt(parts[1]);
        if (address == null || bits > address.getAddress().length * 8) throw new IllegalStateException("CIDR de proxy inválido");
        return new byte[][]{address.getAddress(), {(byte) bits}};
    }

    private static boolean contiene(byte[][] net, byte[] address) {
        var base = net[0]; int bits = net[1][0] & 0xff;
        if (base.length != address.length) return false;
        for (int i = 0; i < bits; i++) {
            int mask = 0x80 >>> (i % 8);
            if ((base[i / 8] & mask) != (address[i / 8] & mask)) return false;
        }
        return true;
    }

    boolean habilitado() { return header != null; }
}
