package cl.nexbyte.siger.custodia;
import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.robustez.*;
import java.security.*;
import java.time.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
/** Evidencia técnica adicional; no altera ACK ni sobre documental. Solo contiene identificadores/huellas. */
public final class GarantiaCustodia {
 public static final String DOMAIN="SIGER_CUSTODIA_RESTAURADA_OPERATIVA_1";
 public record Contexto(UUID id,UUID comunicacionId,String cola,String contenido,String procedencia,String documentoIntegro,String manifiesto,String ack){
  public Contexto {if(id==null||comunicacionId==null||!Set.of("COMPLETO","HISTORICO").contains(cola))throw new IllegalArgumentException("Contexto inválido");for(String h:List.of(contenido,procedencia,documentoIntegro,manifiesto,ack))if(!h.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Huella inválida");}
 }
 public record Contenido(Contexto contexto,Instant verificadaEn,Instant venceEn,UUID restauracionRef) {
  public Contenido {if(contexto==null||verificadaEn==null||venceEn==null||restauracionRef==null||!venceEn.isAfter(verificadaEn)||Duration.between(verificadaEn,venceEn).compareTo(Duration.ofHours(24))>0)throw new IllegalArgumentException("Garantía inválida");}
 }
 public static Contexto contexto(String body,String ack,String queue,Map<String,PublicKey> publicKeys,Map<String,PublicKey> institution,JsonMapper mapper){
  var a=mapper.readValue(ack,RegistroInstitucional.class);String content,source;UUID id;Object files;
  if("COMPLETO".equals(queue)) {var d=mapper.readValue(body,EntregaFormulario.class);d.validar(publicKeys,mapper);d.validarRegistro(a,institution,mapper);content=d.sha256();source=d.huellaProcedencia();id=d.comunicacion().id();files=d.comunicacion().adjuntos();}
  else if("HISTORICO".equals(queue)) {var d=mapper.readValue(body,EntregaVerificada.class);d.validar(publicKeys,mapper);a.validar(institution,mapper,d);content=d.comunicacion().sha256();source=EntregaVerificada.huella(d.procedencia());id=d.comunicacion().id();files=List.of();}
  else throw new IllegalArgumentException("Cola no soportada");
  return new Contexto(id,a.registro().comunicacionId(),queue,content,source,CanonFormulario.huella("DOCUMENTO_ARCHIVADO",mapper.readTree(body),mapper),CanonFormulario.huella("MANIFIESTO_ARCHIVADO",files,mapper),CanonFormulario.huella("ACK_ARCHIVADO",a,mapper));
 }
 public static Contenido validar(FirmaOperativa signed,Map<String,PublicKey> trust,Clock clock,JsonMapper mapper){
  var value=signed.leer(DOMAIN,Contenido.class,trust,mapper);
  if(value.verificadaEn().isAfter(clock.instant().plusSeconds(120))||!value.venceEn().isAfter(clock.instant()))throw new IllegalArgumentException("Garantía vencida o futura");return value;
 }
 private GarantiaCustodia(){}
}