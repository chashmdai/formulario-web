package cl.nexbyte.siger.robustez;
import cl.nexbyte.siger.entrega.SobreComunicacion;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** Contrato v2: las afirmaciones por atributo quedan vinculadas al comprobante firmado. */
public record EntregaVerificada(String version,SobreComunicacion comunicacion,Map<String,Atributo> procedencia,ComprobanteDigital comprobante){
    public record Atributo(String fuente,String nivel,Instant observadoEn,UUID evidenciaRef){public Atributo{if(fuente==null||nivel==null||observadoEn==null||evidenciaRef==null||fuente.length()>64||nivel.length()>64)throw new IllegalArgumentException("Atributo inválido");}}
    public EntregaVerificada{
        if((!"2.0".equals(version)&&!"2.1".equals(version))||comunicacion==null||procedencia==null||comprobante==null)throw new IllegalArgumentException("Entrega inválida");
        if("2.1".equals(version)!= "2.0".equals(comunicacion.version()))throw new IllegalArgumentException("Variante de entrega incoherente");
        procedencia=Map.copyOf(procedencia);
    }
    public static Map<String,Atributo> procedencia(Instant verificada,UUID auth,Instant recibida,UUID ingreso){
        var identity=new Atributo("CLAVE_UNICA_USERINFO","IDENTIDAD_AUTENTICADA",verificada,auth);
        return Map.of("run",identity,"nombres",identity,"apellidos",identity,"mensaje",new Atributo("CIUDADANO","DECLARADO_NO_CONTRASTADO",recibida,ingreso));
    }
    public static Map<String,Atributo> procedenciaNna(SobreComunicacion sobre){
        var attr=new Atributo("CIUDADANO","DECLARADO_NO_CONTRASTADO",sobre.recibidaEn(),sobre.id());
        var result=new HashMap<String,Atributo>();for(var field:sobre.declaracionNna().campos())result.put(field,attr);return Map.copyOf(result);
    }
    public static String huella(Map<String,Atributo> attributes){
        try{
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            var fields=attributes.containsKey("primerNombre")?attributes.keySet().stream().sorted().toList():List.of("run","nombres","apellidos","mensaje");
            if(attributes.containsKey("primerNombre"))out.writeUTF("PROCEDENCIA_NNA_1");
            for(var field:fields){
                var value=attributes.get(field);if(value==null)throw new IllegalArgumentException("Atributo sin procedencia");
                for(var text:List.of(field,value.fuente(),value.nivel(),value.observadoEn().toString(),value.evidenciaRef().toString())){
                    var encoded=text.getBytes(StandardCharsets.UTF_8);out.writeInt(encoded.length);out.write(encoded);
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        }catch(IOException|NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public void validar(Map<String,PublicKey> trusted,JsonMapper mapper){
        boolean nna="2.1".equals(version);
        if(!comunicacion.integra()||!procedencia.keySet().equals(nna?comunicacion.declaracionNna().campos():Set.of("run","nombres","apellidos","mensaje")))throw new IllegalArgumentException("Integridad inválida");
        var content=comprobante.verificar(trusted,mapper);
        if(!content.id().equals(comunicacion.id())||!content.recibidaEn().equals(comunicacion.recibidaEn())
            ||!content.huellaContenido().equals(comunicacion.sha256())||!content.huellaProcedencia().equals(huella(procedencia))
            ||!content.avisoVersion().equals(comunicacion.avisoPrivacidadVersion()))throw new IllegalArgumentException("Comprobante no corresponde");
        if(nna){
            if(!procedencia.equals(procedenciaNna(comunicacion)))throw new IllegalArgumentException("Procedencia declarada inválida");
            return;
        }
        for(var field:List.of("run","nombres","apellidos")){
            var a=procedencia.get(field);
            if(!"CLAVE_UNICA_USERINFO".equals(a.fuente())||!"IDENTIDAD_AUTENTICADA".equals(a.nivel())
                ||a.observadoEn().isAfter(comunicacion.recibidaEn())||a.evidenciaRef()==null)throw new IllegalArgumentException("Procedencia inválida");
        }
        if(!procedencia.get("run").equals(procedencia.get("nombres"))||!procedencia.get("run").equals(procedencia.get("apellidos")))throw new IllegalArgumentException("Evidencia incoherente");
        var message=procedencia.get("mensaje");
        if(!"CIUDADANO".equals(message.fuente())||!"DECLARADO_NO_CONTRASTADO".equals(message.nivel())
            ||!message.observadoEn().equals(comunicacion.recibidaEn())||!message.evidenciaRef().equals(comunicacion.id()))throw new IllegalArgumentException("Verificación de mensaje inválida");
    }
    @Override public String toString(){return "EntregaVerificada[id="+comunicacion.id()+"]";}
}
