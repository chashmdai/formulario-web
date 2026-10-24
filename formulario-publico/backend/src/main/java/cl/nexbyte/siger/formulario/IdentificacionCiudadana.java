package cl.nexbyte.siger.formulario;

import cl.nexbyte.siger.identidad.IdentidadVerificada;
import cl.nexbyte.siger.identidad.EvidenciaAutenticacion;
import cl.nexbyte.siger.srcei.*;
import java.time.*;
import java.util.*;

/** Modelo interno. Las respuestas registrales nunca se serializan como respuesta ciudadana. */
public final class IdentificacionCiudadana {
    private IdentificacionCiudadana() {}
    public enum Tipo { ADULTO_CU, ADULTO_DECLARADO, NNA }
    public enum Rol { PRESENTADOR, AFECTADO_NNA, ADULTO_RELACIONADO, PERSONA_SENALADA }
    public enum Fuente { CLAVE_UNICA_USERINFO, SRCEI, DECLARACION_CIUDADANA, SIN_DATO }
    public record AutenticacionCiudadana(IdentidadVerificada identidad,EvidenciaAutenticacion prueba) {
        public AutenticacionCiudadana {
            if(identidad==null||prueba==null||!prueba.corresponde(identidad))throw new IllegalArgumentException("Evidencia de sesión requerida");
        }
        @Override public String toString(){return "AutenticacionCiudadana[protegida]";}
    }
    public record Valor(String valor, String codigo, Fuente fuente, String nivel, Instant observadoEn, UUID evidencia) {
        public Valor {
            Objects.requireNonNull(fuente); Objects.requireNonNull(nivel);
            Objects.requireNonNull(observadoEn); Objects.requireNonNull(evidencia);
            if (valor != null && (valor.length() > 1009 || valor.codePoints().anyMatch(Character::isISOControl)))
                throw new IllegalArgumentException("Atributo inválido");
        }
        @Override public String toString() { return "ValorIdentificacion[protegido]"; }
    }
    public record Persona(String id, Rol rol, String runNumero, String runDv, boolean modulo11,
            boolean autenticada, ResultadoSrcei.Estado estadoConsulta, Instant consultadaEn,
            UUID consultaRef, Map<String, Valor> atributos, Map<String, Valor> discrepancias) {
        public Persona { atributos = Map.copyOf(atributos); discrepancias = Map.copyOf(discrepancias); }
        @Override public String toString() { return "PersonaFormulario[protegida]"; }
    }
    public record Vista(String personaId, String estado, boolean autenticada, boolean runInformado, Set<String> camposFaltantes,
            Set<String> camposObligatorios, Map<String,String> valoresDeclarados, String mensaje) {}
    public static final Set<String> CAMPOS = Set.of("primerNombre", "segundoNombre", "primerApellido", "segundoApellido", "fechaNacimiento", "sexo", "edad");
    private static final Set<String> NOMBRES = Set.of("primerNombre", "segundoNombre", "primerApellido", "segundoApellido");

    public static Persona crear(String id, Rol rol, RunConsulta run, AutenticacionCiudadana autenticacion, ResultadoSrcei resultado, Clock clock) {
        var cu=autenticacion==null?null:autenticacion.identidad();
        var now = autenticacion==null?clock.instant():autenticacion.prueba().verificadaEn();
        var evidence = autenticacion==null?UUID.randomUUID():autenticacion.prueba().id(); var attrs = new HashMap<String,Valor>();
        if (cu != null) {
            if (rol != Rol.PRESENTADOR) throw new IllegalArgumentException("CU corresponde solo al presentador");
            put(attrs,"runNumero",cu.run().split("-")[0],null,Fuente.CLAVE_UNICA_USERINFO,now,evidence);
            put(attrs,"runDv",cu.run().split("-")[1],null,Fuente.CLAVE_UNICA_USERINFO,now,evidence);
            // Los agregados históricos se conservan: nunca dividir nombres por espacios.
            put(attrs,"nombres",cu.nombres(),null,Fuente.CLAVE_UNICA_USERINFO,now,evidence);
            put(attrs,"apellidos",cu.apellidos(),null,Fuente.CLAVE_UNICA_USERINFO,now,evidence);
            if(cu.nombresComponentes()!=null) {
                put(attrs,"primerNombre",cu.nombresComponentes().getFirst(),null,Fuente.CLAVE_UNICA_USERINFO,now,evidence);
                if(cu.nombresComponentes().size()>1) put(attrs,"segundoNombre",String.join(" ",cu.nombresComponentes().subList(1,cu.nombresComponentes().size())),null,Fuente.CLAVE_UNICA_USERINFO,now,evidence);
            }
            if(cu.apellidosComponentes()!=null) {
                put(attrs,"primerApellido",cu.apellidosComponentes().getFirst(),null,Fuente.CLAVE_UNICA_USERINFO,now,evidence);
                if(cu.apellidosComponentes().size()>1) put(attrs,"segundoApellido",String.join(" ",cu.apellidosComponentes().subList(1,cu.apellidosComponentes().size())),null,Fuente.CLAVE_UNICA_USERINFO,now,evidence);
            }
        } else if(run!=null) {
            put(attrs,"runNumero",run.numero(),null,Fuente.DECLARACION_CIUDADANA,now,evidence);
            put(attrs,"runDv",run.dv(),null,Fuente.DECLARACION_CIUDADANA,now,evidence);
        }
        var discrepancies = new HashMap<String,Valor>();
        if(resultado!=null) resultado.atributos().forEach((key,value)->{
            var incoming=new Valor(value.valor(),value.codigo(),Fuente.SRCEI,"CONTRASTADO_EN_FUENTE",value.observadoEn(),value.evidencia());
            if(attrs.containsKey(key)) {
                if(!Objects.equals(attrs.get(key).valor(),incoming.valor())) discrepancies.put(key,incoming);
            } else attrs.put(key,incoming);
        });
        String number=cu!=null?cu.run().split("-")[0]:run==null?null:run.numero();
        String dv=cu!=null?cu.run().split("-")[1]:run==null?null:run.dv();
        return new Persona(id,rol,number,dv,run!=null,cu!=null,resultado==null?ResultadoSrcei.Estado.NO_HABILITADO:resultado.estado(),
            resultado==null?null:resultado.consultadoEn(),resultado==null?null:resultado.evidencia(),attrs,discrepancies);
    }
    private static void put(Map<String,Valor> attrs,String key,String value,String code,Fuente source,Instant at,UUID evidence) {
        attrs.put(key,new Valor(value,code,source,source==Fuente.CLAVE_UNICA_USERINFO?"IDENTIDAD_AUTENTICADA":"DECLARADO_NO_CONTRASTADO",at,evidence));
    }
    public static Vista vista(Persona p) {
        var missing=new HashSet<>(CAMPOS);
        if(p.rol()==Rol.AFECTADO_NNA || (p.rol()==Rol.PRESENTADOR && p.id().equals("presentador-nna"))) {
            missing.remove("fechaNacimiento"); missing.remove("sexo");
        } else missing.remove("edad");
        p.atributos().forEach((key,value)->{if(util(value))missing.remove(key);});
        if(p.atributos().containsKey("nombres")) missing.removeAll(Set.of("primerNombre","segundoNombre"));
        if(p.atributos().containsKey("apellidos")) missing.removeAll(Set.of("primerApellido","segundoApellido"));
        // La edad puede inferirse únicamente en servidor a partir de fecha completa, sin publicar la fecha.
        if(util(p.atributos().get("fechaNacimiento"))) missing.remove("edad");
        var declared=new HashMap<String,String>();
        p.atributos().forEach((key,value)->{if(value.fuente()==Fuente.DECLARACION_CIUDADANA && CAMPOS.contains(key)) declared.put(key,value.valor());});
        // Los campos declarados siguen siendo editables, los recuperados nunca se exponen.
        missing.addAll(declared.keySet());
        var required=new HashSet<>(Set.of("primerNombre","primerApellido")); required.retainAll(missing);
        return new Vista(p.id(),"REVISADO",p.autenticada(),p.runNumero()!=null,Set.copyOf(missing),Set.copyOf(required),Map.copyOf(declared),
                "Completa los datos solicitados. La revisión del RUN no acredita tu identidad.");
    }
    public static Persona declarar(Persona p,Map<String,String> values,Clock clock) {
        if(values==null||values.size()>CAMPOS.size()||!CAMPOS.containsAll(values.keySet())) throw new IllegalArgumentException("Campos de identificación inválidos");
        var allowed=vista(p).camposFaltantes(); var attrs=new HashMap<>(p.atributos());var discrepancies=new HashMap<>(p.discrepancias());
        if(!allowed.containsAll(values.keySet())) throw new IllegalArgumentException("No sustituir antecedentes de otra fuente");
        for(var entry:values.entrySet()) {
            String key=entry.getKey(),value=entry.getValue()==null?"":entry.getValue().strip();
            if(value.isEmpty()) { if(attrs.get(key)!=null && attrs.get(key).fuente()==Fuente.DECLARACION_CIUDADANA) attrs.remove(key); continue; }
            validar(key,value,clock);
            if(attrs.containsKey(key)&&attrs.get(key).fuente()==Fuente.SRCEI)discrepancies.put(key,attrs.get(key));
            put(attrs,key,value,null,Fuente.DECLARACION_CIUDADANA,clock.instant(),UUID.randomUUID());
        }
        return new Persona(p.id(),p.rol(),p.runNumero(),p.runDv(),p.modulo11(),p.autenticada(),p.estadoConsulta(),p.consultadaEn(),p.consultaRef(),attrs,discrepancies);
    }
    public static void validar(String key,String value,Clock clock) {
        if(value.length()>500||value.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Atributo inválido");
        if(NOMBRES.contains(key) && (!value.matches("[\\p{L}\\p{M} .'-]+")||value.codePoints().noneMatch(Character::isLetter))) throw new IllegalArgumentException("Nombre inválido");
        if(key.equals("fechaNacimiento"))try {
            if(!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")||LocalDate.parse(value).isAfter(LocalDate.now(clock))||LocalDate.parse(value).isBefore(LocalDate.now(clock).minusYears(130)))throw new IllegalArgumentException("Fecha inválida");
        }catch(DateTimeException error){throw new IllegalArgumentException("Fecha inválida");}
        if(key.equals("sexo")&&!Set.of("F","M","I","U").contains(value)) throw new IllegalArgumentException("Sexo inválido");
        if(key.equals("edad")&&(!value.matches("[0-9]{1,2}")||Integer.parseInt(value)>17)) throw new IllegalArgumentException("Edad inválida");
    }
    public static boolean completa(Persona p) {
        return (util(p.atributos().get("primerNombre"))||util(p.atributos().get("nombres")))
            &&(util(p.atributos().get("primerApellido"))||util(p.atributos().get("apellidos")));
    }
    private static boolean util(Valor value){return value!=null&&value.valor()!=null&&!value.valor().isBlank()&&!"Desconocido".equals(value.valor())&&!"U".equals(value.codigo());}
}
