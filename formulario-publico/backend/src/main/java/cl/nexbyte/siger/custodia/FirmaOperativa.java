package cl.nexbyte.siger.custodia;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
/** Sellos operativos con dominio propio: jamás sustituyen firmas de documentos 1.0/1.1. */
public record FirmaOperativa(String keyId,String payload,String firma) {
 public FirmaOperativa {
  if(keyId==null||!keyId.matches("[A-Za-z0-9_-]{1,64}")||payload==null||payload.length()>32768||!payload.matches("[A-Za-z0-9_-]+")||firma==null||!firma.matches("[A-Za-z0-9_-]{86}"))throw new IllegalArgumentException("Sello operativo inválido");
 }
 public static FirmaOperativa firmar(Object value,String domain,String keyId,PrivateKey key,JsonMapper mapper){
  try {byte[] bytes=mapper.writeValueAsBytes(value);var s=Signature.getInstance("Ed25519");s.initSign(key);s.update((domain+"\0").getBytes(StandardCharsets.UTF_8));s.update(bytes);return new FirmaOperativa(keyId,Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign()));}
  catch(GeneralSecurityException e){throw new IllegalArgumentException("No se pudo sellar");}
 }
 public <T>T leer(String domain,Class<T> type,Map<String,PublicKey> keys,JsonMapper mapper){
  try {var key=keys.get(keyId);if(key==null)throw new IllegalArgumentException("Firmante operativo desconocido");byte[] bytes=Base64.getUrlDecoder().decode(payload);var s=Signature.getInstance("Ed25519");s.initVerify(key);s.update((domain+"\0").getBytes(StandardCharsets.UTF_8));s.update(bytes);if(!s.verify(Base64.getUrlDecoder().decode(firma)))throw new IllegalArgumentException("Sello operativo falso");var value=mapper.readValue(bytes,type);if(!Arrays.equals(bytes,mapper.writeValueAsBytes(value)))throw new IllegalArgumentException("Representación operativa ambigua");return value;}
  catch(GeneralSecurityException e){throw new IllegalArgumentException("Sello operativo no verificable");}
 }
 @Override public String toString(){return "FirmaOperativa[protegida]";}
}