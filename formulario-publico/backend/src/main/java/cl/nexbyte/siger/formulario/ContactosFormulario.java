package cl.nexbyte.siger.formulario;

import java.util.*;
import java.util.regex.Pattern;

/** Solo entradas nuevas: nunca reescribe una custodia histórica o su huella de solicitud. */
public final class ContactosFormulario {
    private ContactosFormulario(){}
    private static final Pattern LOCAL_INVALID=Pattern.compile("[\\s@<>()\\[\\]:;,\"\\\\\\x00-\\x1f\\x7f]",Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern DOMAIN_LABEL=Pattern.compile("[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]{0,61}[\\p{L}\\p{N}])?");
    public static String correo(String raw){return raw.replaceAll("(?U)^\\s+|\\s+$","");}
    public static boolean correoValido(String raw){
        if(raw==null||raw.indexOf('\r')>=0||raw.indexOf('\n')>=0||raw.indexOf(0)>=0)return false;
        var value=correo(raw);var parts=value.split("@",-1);if(value.length()>254||parts.length!=2)return false;
        var local=parts[0];if(local.isEmpty()||local.length()>64||local.startsWith(".")||local.endsWith(".")||local.contains("..")||LOCAL_INVALID.matcher(local).find())return false;
        var labels=parts[1].split("\\.",-1);return labels.length>=2&&Arrays.stream(labels).allMatch(label->DOMAIN_LABEL.matcher(label).matches());
    }
    public static String celular(String raw){
        var value=raw.strip();if(value.isEmpty())return "";
        if(!value.matches("\\+?[0-9\\s().-]+"))throw invalido();
        var digits=value.replaceAll("\\D","");
        if(value.startsWith("+")){if(digits.matches("569[0-9]{8}"))return "+"+digits;throw invalido();}
        if(digits.matches("[0-9]{8}"))return "+569"+digits;
        if(digits.matches("9[0-9]{8}"))return "+56"+digits;
        if(digits.matches("569[0-9]{8}"))return "+"+digits;
        if(digits.matches("00569[0-9]{8}"))return "+"+digits.substring(2);
        throw invalido();
    }
    public static boolean celularValido(String value){try{return !celular(value).isEmpty();}catch(IllegalArgumentException e){return false;}}
    public static DatosFormulario normalizar(DatosFormulario data){
        var fields=new TreeMap<>(data.campos());
        fields.replaceAll((id,value)->{
            if(id.equals("email")||id.endsWith("-email")){if(!value.isBlank()&&!correoValido(value))throw invalido();return correo(value);}
            if(id.equals("telefono")||id.endsWith("-celular"))return celular(value);
            return value;
        });
        return new DatosFormulario(fields,data.personas(),data.instituciones(),data.adjuntos());
    }
    private static IllegalArgumentException invalido(){return new IllegalArgumentException("Revisa el formato del teléfono o correo electrónico");}
}
