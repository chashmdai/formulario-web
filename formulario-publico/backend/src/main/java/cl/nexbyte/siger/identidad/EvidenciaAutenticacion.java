package cl.nexbyte.siger.identidad;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/** Referencia de auditoría creada después de userinfo. No contiene tokens ni es un mecanismo de autenticación. */
public record EvidenciaAutenticacion(UUID id, Instant verificadaEn, String identidadHash) implements Serializable {
    public static EvidenciaAutenticacion de(IdentidadVerificada identity) {
        return new EvidenciaAutenticacion(UUID.randomUUID(), Instant.now(), hash(identity));
    }
    public boolean corresponde(IdentidadVerificada identity) {
        return id != null && verificadaEn != null && identidadHash != null && identidadHash.equals(hash(identity));
    }
    private static String hash(IdentidadVerificada identity) {
        try {
            String value = identity.run().length()+":"+identity.run()+identity.nombres().length()+":"+identity.nombres()+identity.apellidos().length()+":"+identity.apellidos();
            if (identity.nombresComponentes()!=null) {
                value+="|componentes-v1|"+identity.nombresComponentes().size()+":";
                for(var part:identity.nombresComponentes())value+=part.length()+":"+part;
                value+=identity.apellidosComponentes().size()+":";
                for(var part:identity.apellidosComponentes())value+=part.length()+":"+part;
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @Override public String toString() { return "EvidenciaAutenticacion[datos protegidos]"; }
}
