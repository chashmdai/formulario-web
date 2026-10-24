package cl.nexbyte.siger.multimedia;
/** Correlación irreversible: no duplicar capacidades de sesión/reserva en auditoría. */
public final class AuditoriaMedia{
 private AuditoriaMedia(){}
 public static String referencia(String value){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(("SIGER_MEDIA_AUDIT_V1:"+value).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
