package cl.nexbyte.siger.formulario;

import java.time.*;
import java.util.*;
import static cl.nexbyte.siger.formulario.IdentificacionCiudadana.*;

/** Reglas de las tres etapas adultas del mockup. No acepta identidad ni fuentes del navegador. */
final class ValidacionAdultos {
    private final TerritorioFormulario territorio;
    ValidacionAdultos(TerritorioFormulario territorio){this.territorio=territorio;}
    void validar(Tipo tipo,DatosFormulario datos,List<Persona> people) {
        var byId=new HashMap<String,Persona>();for(var p:people)if(byId.put(p.id(),p)!=null)invalid();
        var presenter=byId.get("presentador-adulto");
        if(presenter==null||presenter.rol()!=Rol.PRESENTADOR||!completa(presenter)||presenter.autenticada()!=(tipo==Tipo.ADULTO_CU))invalid();
        if(tipo==Tipo.ADULTO_DECLARADO&&(presenter.runNumero()==null||!presenter.modulo11()))invalid();
        var selected=new HashSet<String>();selected.add("presentador-adulto");datos.personas().forEach(p->selected.add(p.personaId()));
        if(!selected.equals(byId.keySet()))invalid();
        var c=datos.campos();var allowed=new HashSet<>(Set.of("motivo","adult-region","adult-commune","adult-address","email","telefono","relato"));
        choice(c,"motivo",Set.of("consulta","vulneracion","promocion"),true);required(c,"adult-region");geo(c.get("adult-region"),c.get("adult-commune"));
        var email=c.getOrDefault("email","");if(!email.isBlank()&&!ContactosFormulario.correoValido(email))invalid();
        var phone=c.getOrDefault("telefono","");if(!phone.isBlank()&&!ContactosFormulario.celularValido(phone))invalid();
        switch(c.get("motivo")){
            case "consulta"->{
                if(!datos.personas().isEmpty()||!datos.instituciones().isEmpty())invalid();
                allowed.addAll(Set.of("tema","consulta-confirmada"));equal(c,"consulta-confirmada","true");
                // Empleo se deriva al canal oficial: nunca se recibe una postulación como Comunicación.
                choice(c,"tema",Set.of("general","entrevista","estadisticas"),true);
                switch(c.get("tema")){
                    case "general"->{allowed.add("consulta-general-detalle");required(c,"consulta-general-detalle");}
                    case "entrevista"->{
                        allowed.addAll(Set.of("entrevista-motivo","entrevista-rol"));required(c,"entrevista-motivo");choice(c,"entrevista-rol",Set.of("estudiante","independiente","prensa","otro"),true);
                        if("otro".equals(c.get("entrevista-rol"))){allowed.add("entrevista-rol-otro");required(c,"entrevista-rol-otro");}
                        if(Set.of("estudiante","prensa").contains(c.get("entrevista-rol")))allowed.add("entrevista-entidad");
                    }
                    case "estadisticas"->{allowed.addAll(Set.of("stats-confirm","stats-descripcion","stats-periodo","stats-desglose"));equal(c,"stats-confirm","true");required(c,"stats-descripcion");}
                    default->invalid();
                }
            }
            case "promocion"->{
                if(!datos.personas().isEmpty()||!datos.instituciones().isEmpty())invalid();
                allowed.addAll(Set.of("organizacion","actividad","publico","fecha-actividad","promocion-region","promocion-commune"));
                choice(c,"organizacion",Set.of("si","no"),true);choice(c,"actividad",Set.of("Charla","Taller","Capacitación","Otra actividad"),true);required(c,"publico");required(c,"relato");
                if("si".equals(c.get("organizacion"))){allowed.addAll(Set.of("tipo-organizacion","nombre-organizacion"));required(c,"nombre-organizacion");choice(c,"tipo-organizacion",Set.of("ONG o fundación","Establecimiento educacional","Servicio u organismo público","Empresa u otra persona jurídica","Otra organización"),true);}
                geo(c.get("promocion-region"),c.get("promocion-commune"));
                var date=c.getOrDefault("fecha-actividad","");if(!date.isBlank())try{if(!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")||LocalDate.parse(date).getYear()<1)invalid();}catch(DateTimeException badDate){invalid();}
            }
            case "vulneracion"->{
                allowed.addAll(Set.of("riesgo","relacion","hechos-region","hechos-commune","lugar","momento","identifica-responsables"));required(c,"relato");
                choice(c,"riesgo",Set.of("si","no"),true);choice(c,"relacion",Set.of("madre","padre","cuidador","otro-familiar","docente","salud","testigo","organizacion","otro"),true);
                if("otro-familiar".equals(c.get("relacion"))){allowed.add("relacion-familiar");choice(c,"relacion-familiar",Set.of("Abuela o abuelo","Tía o tío","Hermana o hermano","Prima o primo","Madrastra o padrastro","Madrina o padrino","Otro parentesco familiar"),true);}
                geo(c.get("hechos-region"),c.get("hechos-commune"));choice(c,"momento",Set.of("Está ocurriendo actualmente","Ocurrió recientemente","Ocurrió tiempo atrás","No lo sé"),false);choice(c,"identifica-responsables",Set.of("true","false"),false);
                if(datos.personas().stream().noneMatch(r->r.personaId().startsWith("afectado-")))invalid();
                for(var r:datos.personas())related(r,byId.get(r.personaId()),"true".equals(c.get("identifica-responsables")));
                if(!datos.instituciones().isEmpty()&&!"true".equals(c.get("identifica-responsables")))invalid();
                for(var institution:datos.instituciones()){
                    if(institution.nombre()==null||institution.nombre().isBlank())invalid();
                    choice(Map.of("tipo",Objects.toString(institution.tipo(),"")),"tipo",Set.of("Establecimiento educativo","Organismo público","Empresa o entidad privada","Fundación u ONG","Otra institución"),false);
                    geo(institution.region(),institution.comuna());
                }
            }
            default->invalid();
        }
        if(!allowed.containsAll(c.keySet()))invalid();
        var narrative=Set.of("relato","entrevista-motivo","consulta-general-detalle","stats-descripcion");
        c.forEach((key,value)->{if(value.length()>(narrative.contains(key)?5000:500)||value.codePoints().anyMatch(cp->Character.isISOControl(cp)&&cp!=9&&cp!=10&&cp!=13))invalid();});
    }
    private void related(DatosFormulario.Relacion r,Persona p,boolean subjects){
        if(p==null||p.autenticada())invalid();var d=r.detalles();Set<String> allowed;
        switch(p.rol()){
            case AFECTADO_NNA->{
                if(!r.personaId().startsWith("afectado-"))invalid();allowed=Set.of("datosDesconocidos","region","comuna");choice(d,"datosDesconocidos",Set.of("true","false"),false);
                boolean known=p.runNumero()!=null||p.atributos().values().stream().anyMatch(v->v.valor()!=null&&!v.valor().isBlank())||!d.getOrDefault("region","").isBlank()||!d.getOrDefault("comuna","").isBlank();
                if(!known&&!"true".equals(d.get("datosDesconocidos")))invalid();geo(d.get("region"),d.get("comuna"));
            }
            case ADULTO_RELACIONADO->{if(!r.personaId().startsWith("relacionado-"))invalid();allowed=Set.of("relacion");choice(d,"relacion",Set.of("Familiar","Representante","Persona de contacto","Testigo","Otro adulto relacionado"),false);}
            case PERSONA_SENALADA->{
                if(!subjects||!r.personaId().startsWith("senalado-"))invalid();allowed=Set.of("alias","rol","region","comuna");geo(d.get("region"),d.get("comuna"));
                boolean known=p.runNumero()!=null||p.atributos().entrySet().stream().anyMatch(e->e.getValue().valor()!=null&&!e.getValue().valor().isBlank()&&(e.getValue().fuente()==Fuente.SRCEI||Set.of("primerNombre","primerApellido","nombres","apellidos").contains(e.getKey())))||!d.getOrDefault("alias","").isBlank()||!d.getOrDefault("rol","").isBlank();
                if(!known)invalid();
            }
            default->{invalid();return;}
        }
        if(!allowed.containsAll(d.keySet()))invalid();d.values().forEach(v->{if(v.length()>500||v.codePoints().anyMatch(Character::isISOControl))invalid();});
    }
    private void geo(String region,String commune){if(region==null||region.isBlank()){if(commune!=null&&!commune.isBlank())invalid();}else if(!territorio.region(region)||!territorio.comuna(region,commune))invalid();}
    private static void equal(Map<String,String> c,String key,String expected){if(!expected.equals(c.get(key)))invalid();}
    private static void required(Map<String,String> c,String key){if(c.getOrDefault(key,"").isBlank())invalid();}
    private static void choice(Map<String,String> c,String key,Set<String> options,boolean mandatory){var value=c.get(key);if(value==null||value.isBlank()){if(mandatory)invalid();}else if(!options.contains(value))invalid();}
    private static void invalid(){throw new IllegalArgumentException("Revisa los campos del formulario adulto");}
}
