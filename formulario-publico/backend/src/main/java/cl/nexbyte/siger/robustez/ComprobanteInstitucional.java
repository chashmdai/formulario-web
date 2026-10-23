package cl.nexbyte.siger.robustez;
import java.security.*;import java.time.Instant;import java.util.*;import tools.jackson.databind.json.JsonMapper;

/** Documento emitido por SIGER; no es el sello de custodia del componente público. */
public record ComprobanteInstitucional(String algoritmo,String keyId,String payload,String firma) {
 public record Contenido(String version,String emisor,String hecho,UUID operacionPublicaId,UUID comunicacionId,
     long numeroFolio,String folio,Instant registradaEn,String canalOrigen,String sha256,String huellaProcedencia) {
  public Contenido {
   if((!"1.0".equals(version)&&!"1.1".equals(version))||!"SIGER_DDN".equals(emisor)||!"COMUNICACION_REGISTRADA".equals(hecho)
       ||operacionPublicaId==null||comunicacionId==null||numeroFolio<1||folio==null||!folio.matches("[A-Z][A-Z0-9]{1,15}-[0-9]{1,19}")
       ||registradaEn==null||!("1.0".equals(version)?"FORMULARIO_WEB_PUBLICO":"FORMULARIO_WEB_NNA").equals(canalOrigen)||sha256==null||!sha256.matches("[a-f0-9]{64}")
       ||huellaProcedencia==null||!huellaProcedencia.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Registro institucional inválido");
   try{if(Long.parseLong(folio.substring(folio.lastIndexOf('-')+1))!=numeroFolio)throw new IllegalArgumentException("Folio incoherente");}
   catch(NumberFormatException e){throw new IllegalArgumentException("Folio fuera de rango");}
  }
 }
 public ComprobanteInstitucional {
  if(!"Ed25519".equals(algoritmo)||keyId==null||!keyId.matches("[A-Za-z0-9_-]{1,64}")||payload==null||payload.length()>8192
      ||!payload.matches("[A-Za-z0-9_-]+")||firma==null||!firma.matches("[A-Za-z0-9_-]{86}"))throw new IllegalArgumentException("Comprobante institucional inválido");
 }
 public static ComprobanteInstitucional firmar(Contenido content,String id,PrivateKey key,JsonMapper mapper){
  try{var bytes=mapper.writeValueAsBytes(content);var signer=Signature.getInstance("Ed25519");signer.initSign(key);signer.update(bytes);
   return new ComprobanteInstitucional("Ed25519",id,Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
  }catch(GeneralSecurityException e){throw new IllegalStateException("No se pudo firmar el registro institucional");}
 }
 public Contenido verificar(Map<String,PublicKey> trust,JsonMapper mapper){
  try{var key=trust.get(keyId);if(key==null)throw new IllegalArgumentException("Firmante institucional desconocido");
   var bytes=Base64.getUrlDecoder().decode(payload);var verifier=Signature.getInstance("Ed25519");verifier.initVerify(key);verifier.update(bytes);
   if(!verifier.verify(Base64.getUrlDecoder().decode(firma)))throw new IllegalArgumentException("Firma institucional inválida");
   var result=mapper.readValue(bytes,Contenido.class);if(!Arrays.equals(bytes,mapper.writeValueAsBytes(result)))throw new IllegalArgumentException("Representación no canónica");return result;
  }catch(GeneralSecurityException|tools.jackson.core.JacksonException e){throw new IllegalArgumentException("Comprobante no verificable");}
 }
 @Override public String toString(){return "ComprobanteInstitucional[keyId="+keyId+"]";}
}
