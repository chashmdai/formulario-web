package cl.nexbyte.siger.formulario;

import java.util.*;
import static cl.nexbyte.siger.formulario.IdentificacionCiudadana.*;

/** Validación del agregado capturado; los atributos personales provienen únicamente de la sesión persistida. */
public final class ValidacionFormulario {
    private final TerritorioFormulario territorio;
    public ValidacionFormulario(TerritorioFormulario territorio){this.territorio=territorio;}
    public void validar(Tipo tipo,DatosFormulario datos,List<Persona> personas) {
        if(tipo!=Tipo.NNA)throw new cl.nexbyte.siger.robustez.FalloTecnico(503); // Habilitar adultos solo al completar sus reglas y pruebas.
        if(!datos.personas().isEmpty()||!datos.instituciones().isEmpty())invalid();
        if(personas.size()!=1||!personas.getFirst().id().equals("presentador-nna")||personas.getFirst().rol()!=Rol.PRESENTADOR||personas.getFirst().autenticada()||!completa(personas.getFirst()))invalid();
        var c=datos.campos();var allowed=new HashSet<>(Set.of("nna-region","nna-commune","nna-preferred","nna-treatment","nna-contact-instructions","nna-story","nna-primary-method","nna-extra-contact"));
        required(c,"nna-region");geo(c,"nna-region","nna-commune");required(c,"nna-story");
        choice(c,"nna-primary-method",Set.of("celular","email","social","unsafe"),true);
        choice(c,"nna-extra-contact",Set.of("true","false"),false);
        choice(c,"nna-treatment",Set.of("ella","el","elle","nombre","otra","noresponde"),false);
        if("otra".equals(c.get("nna-treatment")))allowed.add("nna-treatment-text");
        if(!"unsafe".equals(c.get("nna-primary-method"))) {allowed.add("nna-safe");choice(c,"nna-safe",Set.of("yes","unsure","no"),false);}
        contact(c,allowed,"nna-primary");
        if("true".equals(c.get("nna-extra-contact"))){choice(c,"nna-secondary-method",Set.of("celular","email","social"),true);contact(c,allowed,"nna-secondary");}
        if(!allowed.containsAll(c.keySet()))invalid();
        c.forEach((k,v)->{if(v.length()>(k.equals("nna-story")?5000:500)||v.codePoints().anyMatch(cp->Character.isISOControl(cp)&&cp!='\n'&&cp!='\r'&&cp!='\t'))invalid();});
    }
    private void geo(Map<String,String> c,String region,String commune){if(!territorio.region(c.get(region))||!territorio.comuna(c.get(region),c.get(commune)))invalid();}
    private static void contact(Map<String,String> c,Set<String> allowed,String prefix){
        allowed.add(prefix+"-method");var method=c.get(prefix+"-method");
        if("celular".equals(method)||"email".equals(method)) {
            var key=prefix+"-"+method;allowed.add(key);required(c,key);var value=c.get(key);
            if(method.equals("email")&&!value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))invalid();
            if(method.equals("celular")){var number=value.replaceAll("\\D","");if(number.startsWith("56")&&number.length()==11)number=number.substring(2);if(!number.matches("9[0-9]{8}"))invalid();}
        } else if("social".equals(method)) {
            allowed.add(prefix+"-network");allowed.add(prefix+"-handle");required(c,prefix+"-handle");
            choice(c,prefix+"-network",Set.of("Instagram","TikTok","Snapchat","Discord","Facebook","X (Twitter)","otra"),true);
            if("otra".equals(c.get(prefix+"-network"))){allowed.add(prefix+"-other-network");required(c,prefix+"-other-network");}
        }
    }
    private static void required(Map<String,String> c,String key){if(c.getOrDefault(key,"").isBlank())invalid();}
    private static void choice(Map<String,String> c,String key,Set<String> values,boolean mandatory){var value=c.get(key);if(value==null||value.isBlank()){if(mandatory)invalid();}else if(!values.contains(value))invalid();}
    private static void invalid(){throw new IllegalArgumentException("Revisa los campos del formulario");}
}
