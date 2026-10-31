package cl.nexbyte.siger.formulario;

import java.util.Set;
import java.util.HashSet;
import tools.jackson.databind.json.JsonMapper;

/** Catálogo local compartido con React; no consulta servicios ni modifica antecedentes registrales. */
final class NacionalidadesFormulario {
    private static final Set<String> PAISES;
    private static final String ESPECIAL;
    static {
        try(var stream=NacionalidadesFormulario.class.getResourceAsStream("/catalogos/paises.json")) {
            if(stream==null)throw new IllegalStateException("Catálogo de países requerido");
            var root=JsonMapper.builder().build().readTree(stream);var names=new HashSet<String>();
            for(var entry:root.get("paises").properties())names.add(entry.getValue().asString());
            if(names.size()!=249)throw new IllegalStateException("Catálogo de países incompleto");
            PAISES=Set.copyOf(names);ESPECIAL=root.get("especial").asString();
        } catch(Exception error) {throw new ExceptionInInitializerError(error);}
    }
    private NacionalidadesFormulario() {}
    static boolean valida(String value) {
        if(PAISES.contains(value))return true;
        if(!value.startsWith(ESPECIAL))return false;
        var detail=value.substring(ESPECIAL.length());
        return detail.length()<=120&&!detail.isBlank()&&detail.codePoints().anyMatch(Character::isLetter)
            &&detail.codePoints().noneMatch(Character::isISOControl);
    }
}
