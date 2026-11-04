package cl.nexbyte.siger.identidad;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

/** Acceso de campaña, independiente de la identidad ciudadana y de las cadenas técnicas. */
public final class AccesoTemporal {
    static final String SESSION = AccesoTemporal.class.getName()+".grant";
    static final String PREFIX = "pbkdf2-v5_8:";
    public static final String EXPIRATION = "2026-12-15T00:00:00-03:00[America/Santiago]";
    enum Mode { OPEN, PASSWORD, UNAVAILABLE }
    enum Result { ACCEPTED, REJECTED, LIMITED, UNAVAILABLE }
    record Grant(String version, Instant until) implements java.io.Serializable {}
    private final Environment env;
    private final Clock clock;
    private final BooleanSupplier receptionApproved;
    private final JsonMapper mapper;
    private final Pbkdf2PasswordEncoder encoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final boolean enabled;
    private final Instant expires;

    public AccesoTemporal(Environment env, Clock clock, BooleanSupplier receptionApproved, JsonMapper mapper) {
        this.env=env;this.clock=clock;this.receptionApproved=receptionApproved;this.mapper=mapper;
        enabled=env.getProperty("formulario.acceso-temporal.habilitado",Boolean.class,false);
        var date=ZonedDateTime.parse(env.getProperty("formulario.acceso-temporal.expira",EXPIRATION));
        if(!date.getZone().equals(ZoneId.of("America/Santiago")))throw new IllegalArgumentException("Zona de acceso temporal requerida");
        expires=date.toInstant();
    }
    Instant expires(){return expires;}
    Instant now(){return clock.instant();}
    Mode mode() {
        if(!enabled)return Mode.OPEN;
        if(!now().isBefore(expires))return approved()?Mode.OPEN:Mode.UNAVAILABLE;
        try{credential();return Mode.PASSWORD;}catch(Exception e){return Mode.UNAVAILABLE;}
    }
    private boolean approved() {
        try {
            Path p=Path.of(env.getRequiredProperty("formulario.acceso-temporal.apertura-path"));
            if(Files.size(p)>4096)return false;
            var value=mapper.readTree(Files.readString(p));
            for(String flag:List.of("authorized","receiptSignerApproved","windowsReady","physicalE2eVerified","noticeApproved"))
                if(!value.path(flag).asBoolean(false))return false;
            return receptionApproved.getAsBoolean();
        } catch(Exception e){return false;}
    }
    private String credential() throws IOException {
        Path p=Path.of(env.getRequiredProperty("formulario.acceso-temporal.hash-path"));
        if(Files.size(p)>256)throw new IOException("Credencial inválida");
        String value=Files.readString(p).strip();
        if(!value.startsWith(PREFIX)||!value.substring(PREFIX.length()).matches("[0-9a-f]{96}"))throw new IOException("Credencial inválida");
        return value;
    }
    static String version(String credential) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(credential.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException("Algoritmo requerido");}
    }
    boolean granted(HttpSession session) {
        if(session==null)return false;
        try {
            if(session.getAttribute(SESSION) instanceof Grant g && now().isBefore(g.until()) && now().isBefore(expires)
                    && MessageDigest.isEqual(g.version().getBytes(StandardCharsets.US_ASCII),version(credential()).getBytes(StandardCharsets.US_ASCII)))return true;
        } catch(Exception ignored) { /* Ausencia/rotación revoca, nunca concede. */ }
        session.removeAttribute(SESSION);return false;
    }
    /** Ventana durable: 5 fallos/origen y 100 globales/15 min. No confía en X-Forwarded-For. */
    private boolean attempt(String remote, String credential, boolean record) throws Exception {
        Path path=Path.of(env.getRequiredProperty("formulario.acceso-temporal.limites-path"));
        String custody=env.getProperty("formulario.multimedia.directorio");
        if(custody!=null&&path.toAbsolutePath().normalize().startsWith(Path.of(custody).toAbsolutePath().normalize()))
            throw new IOException("Estado de acceso separado de la custodia requerido");
        Files.createDirectories(path.getParent());
        long end=now().plusSeconds(900).getEpochSecond();int global=0;Map<String,Integer> clients=new TreeMap<>();
        if(Files.exists(path)) {
            if(Files.size(path)>32768)throw new IOException("Estado inválido");
            var stored=mapper.readTree(Files.readString(path));
            if(!stored.path("windowEnds").isNumber()||!stored.path("global").isNumber()||!stored.path("clients").isObject())throw new IOException("Estado inválido");
            if(stored.path("windowEnds").asLong()>now().getEpochSecond()) {
                end=stored.path("windowEnds").asLong();global=stored.path("global").asInt();
                for(var entry:stored.path("clients").properties())clients.put(entry.getKey(),entry.getValue().asInt());
                if(global<0||global>100||clients.size()>100||clients.values().stream().anyMatch(v->v<0||v>5))throw new IOException("Estado inválido");
            }
        }
        var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(version(credential).getBytes(StandardCharsets.US_ASCII),"HmacSHA256"));
        String key=HexFormat.of().formatHex(mac.doFinal(remote.getBytes(StandardCharsets.UTF_8)));
        int count=clients.getOrDefault(key,0);
        if(global>=100||count>=5)return false;
        if(!record)return true;
        clients.put(key,count+1);
        Path tmp=Files.createTempFile(path.getParent(),".limites-",".tmp");
        try {
            Files.setPosixFilePermissions(tmp,PosixFilePermissions.fromString("rw-------"));
            Files.writeString(tmp,mapper.writeValueAsString(Map.of("windowEnds",end,"global",global+1,"clients",clients)));
            Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(tmp);}
        return true;
    }
    synchronized Result authenticate(String password, String remote, HttpSession session) {
        if(mode()!=Mode.PASSWORD)return Result.UNAVAILABLE;
        try {
            String credential=credential();
            if(!attempt(remote,credential,false))return Result.LIMITED;
            if(password==null||password.length()<16||password.length()>256||!encoder.matches(password,credential.substring(PREFIX.length()))) {
                attempt(remote,credential,true);return Result.REJECTED;
            }
            session.setAttribute(SESSION,new Grant(version(credential),now().plusSeconds(8*3600).isBefore(expires)?now().plusSeconds(8*3600):expires));
            return Result.ACCEPTED;
        } catch(Exception e){return Result.UNAVAILABLE;}
    }
}
