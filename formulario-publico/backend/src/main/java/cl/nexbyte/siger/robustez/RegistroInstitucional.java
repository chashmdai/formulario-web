package cl.nexbyte.siger.robustez;
import java.security.PublicKey;import java.util.*;import tools.jackson.databind.json.JsonMapper;
public record RegistroInstitucional(ComprobanteInstitucional.Contenido registro,ComprobanteInstitucional comprobante){
 public RegistroInstitucional{if(registro==null||comprobante==null)throw new IllegalArgumentException("Registro requerido");}
 public void validar(Map<String,PublicKey> trust,JsonMapper mapper,EntregaVerificada entrega){
  if(!registro.canalOrigen().equals(entrega.comunicacion().canal())||!registro.equals(comprobante.verificar(trust,mapper))||!registro.operacionPublicaId().equals(entrega.comunicacion().id())
      ||!registro.sha256().equals(entrega.comunicacion().sha256())||!registro.huellaProcedencia().equals(EntregaVerificada.huella(entrega.procedencia())))throw new IllegalArgumentException("El registro no corresponde a esta entrega");
 }
 @Override public String toString(){return "RegistroInstitucional[id="+registro.comunicacionId()+",folio="+registro.folio()+"]";}
}
