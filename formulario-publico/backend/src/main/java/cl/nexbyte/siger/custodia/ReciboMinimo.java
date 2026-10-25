package cl.nexbyte.siger.custodia;
import cl.nexbyte.siger.robustez.*;
import java.security.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
/** Recibo local mínimo: ni nombres, RUN, contactos, relatos ni nombres de archivos. */
public final class ReciboMinimo {
 public static final String DOMAIN="SIGER_RECIBO_MINIMO_LOCAL_1";
 public record Archivo(UUID id,long tamanio,String sha256){public Archivo {if(id==null||tamanio<1||sha256==null||!sha256.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Descriptor mínimo inválido");}}
 public record Contenido(UUID id,UUID recorridoId,String cola,ComprobanteDigital custodia,RegistroInstitucional registro,List<Archivo> archivos) {
  public Contenido {archivos=List.copyOf(archivos);if(archivos.size()>12||id==null||custodia==null||registro==null||!Set.of("COMPLETO","HISTORICO").contains(cola))throw new IllegalArgumentException("Recibo mínimo inválido");}
 }
 public static Contenido validar(String json,UUID id,Map<String,PublicKey> publicKeys,Map<String,PublicKey> institutional,JsonMapper mapper){
  var c=mapper.readValue(json,FirmaOperativa.class).leer(DOMAIN,Contenido.class,publicKeys,mapper);
  var p=c.custodia().verificar(publicKeys,mapper);var a=c.registro().comprobante().verificar(institutional,mapper);
  if(!id.equals(c.id())||!id.equals(p.id())||!c.registro().registro().equals(a)||!id.equals(a.operacionPublicaId())||!p.huellaContenido().equals(a.sha256())||!p.huellaProcedencia().equals(a.huellaProcedencia()))throw new IllegalArgumentException("Recibo mínimo no corresponde");
  return c;
 }
 public static String propietario(String run,byte[] secret){
  if(secret==null||secret.length<32)throw new IllegalArgumentException("Clave de seudonimización requerida");
  try {var h=javax.crypto.Mac.getInstance("HmacSHA256");h.init(new javax.crypto.spec.SecretKeySpec(secret,"HmacSHA256"));return HexFormat.of().formatHex(h.doFinal(("RECIBO_OWNER_1\0"+run).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}
 }
 private ReciboMinimo(){}
}