package cl.nexbyte.siger.formulario;

import java.util.*;

/** Entrada de captura completa. No admite identidad, autenticación o procedencia autoafirmadas. */
public record DatosFormulario(Map<String,String> campos,List<Relacion> personas,List<Institucion> instituciones,List<UUID> adjuntos) {
    public record Relacion(String personaId,Map<String,String> detalles) {
        public Relacion {if(personaId==null||!personaId.matches("(afectado|relacionado|senalado)-[1-9][0-9]?"))throw new IllegalArgumentException("Persona relacionada inválida");detalles=map(detalles,10);}
        @Override public String toString(){return "RelacionFormulario[protegida]";}
    }
    public record Institucion(String nombre,String tipo,String rol,String region,String comuna) {
        public Institucion {for(var v:new String[]{nombre,tipo,rol,region,comuna})if(v!=null&&(v.length()>500||v.codePoints().anyMatch(Character::isISOControl)))throw new IllegalArgumentException("Institución inválida");}
        @Override public String toString(){return "InstitucionFormulario[protegida]";}
    }
    public DatosFormulario {
        campos=map(campos,80);personas=personas==null?List.of():List.copyOf(personas);instituciones=instituciones==null?List.of():List.copyOf(instituciones);adjuntos=adjuntos==null?List.of():List.copyOf(adjuntos);
        if(personas.size()>29||instituciones.size()>20||adjuntos.size()>5||new HashSet<>(adjuntos).size()!=adjuntos.size()||personas.stream().map(Relacion::personaId).distinct().count()!=personas.size())throw new IllegalArgumentException("Límites de comunicación excedidos");
    }
    private static Map<String,String> map(Map<String,String> value,int max) {
        if(value==null)return Map.of();if(value.size()>max||value.entrySet().stream().anyMatch(e->e.getKey()==null||e.getValue()==null))throw new IllegalArgumentException("Campos inválidos");
        return Collections.unmodifiableMap(new TreeMap<>(value));
    }
    @Override public String toString(){return "DatosFormulario[protegidos]";}
}
