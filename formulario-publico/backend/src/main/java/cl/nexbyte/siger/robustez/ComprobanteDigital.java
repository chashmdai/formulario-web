package cl.nexbyte.siger.robustez;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** Comprobante técnico Ed25519; no afirma firma electrónica avanzada ni ingreso institucional. */
public record ComprobanteDigital(String algoritmo,String keyId,String payload,String firma) {
    public record Contenido(String version,UUID id,Instant recibidaEn,String tipoRecepcion,
        String huellaContenido,String huellaProcedencia,String emisor,String avisoVersion) {}
    public ComprobanteDigital {
        if(!"Ed25519".equals(algoritmo)||keyId==null||!keyId.matches("[A-Za-z0-9_-]{1,64}")
            ||payload==null||payload.length()>4096||firma==null||firma.length()>128)throw new IllegalArgumentException("Comprobante inválido");
    }
    public static ComprobanteDigital firmar(Contenido content,String keyId,PrivateKey key,JsonMapper mapper){
        try{
            var bytes=mapper.writeValueAsBytes(content);var signer=Signature.getInstance("Ed25519");signer.initSign(key);signer.update(bytes);
            return new ComprobanteDigital("Ed25519",keyId,Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
        }catch(GeneralSecurityException e){throw new IllegalStateException("Firma no disponible");}
    }
    public Contenido verificar(Map<String,PublicKey> trusted,JsonMapper mapper){
        try{
            var key=trusted.get(keyId);if(key==null)throw new IllegalArgumentException("Emisor no confiado");
            var bytes=Base64.getUrlDecoder().decode(payload);var verifier=Signature.getInstance("Ed25519");verifier.initVerify(key);verifier.update(bytes);
            if(!verifier.verify(Base64.getUrlDecoder().decode(firma)))throw new IllegalArgumentException("Firma inválida");
            var content=mapper.readValue(bytes,Contenido.class);
            if(!Arrays.equals(bytes,mapper.writeValueAsBytes(content))||!"1.0".equals(content.version())
                ||!"CUSTODIA_PUBLICA".equals(content.tipoRecepcion())||!"FORMULARIO_PUBLICO_DDN".equals(content.emisor())
                ||content.id()==null||content.recibidaEn()==null||content.huellaContenido()==null||!content.huellaContenido().matches("[a-f0-9]{64}")
                ||content.huellaProcedencia()==null||!content.huellaProcedencia().matches("[a-f0-9]{64}")||content.avisoVersion()==null)
                throw new IllegalArgumentException("Contenido de comprobante inválido");
            return content;
        }catch(GeneralSecurityException|tools.jackson.core.JacksonException e){throw new IllegalArgumentException("Comprobante no verificable");}
    }
    public static PrivateKey privatePem(String text){
        try{return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(pem(text)));}catch(GeneralSecurityException e){throw new IllegalArgumentException("Clave de firma inválida");}
    }
    public static PublicKey publicPem(String text){
        try{return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(pem(text)));}catch(GeneralSecurityException e){throw new IllegalArgumentException("Clave pública inválida");}
    }
    private static byte[]pem(String text){return Base64.getMimeDecoder().decode(text.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", ""));}
}
