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
    private static final Set<String> CAMPOS_BASE = Set.of("primerNombre", "segundoNombre", "primerApellido", "segundoApellido", "fechaNacimiento", "sexo", "edad");
    public static final Set<String> DOCUMENTO_EXTRANJERO = Set.of("nacionalidad", "tipoDocumento", "numeroDocumento");
    public static final Set<String> CAMPOS;
    static { var fields=new HashSet<>(CAMPOS_BASE);fields.addAll(DOCUMENTO_EXTRANJERO);CAMPOS=Set.copyOf(fields); }
    public static boolean extranjera(Persona p) {
        var marker=p.atributos().get("tipoIdentificacion");
        return p.rol()==Rol.PRESENTADOR && p.id().equals("presentador-adulto") && !p.autenticada()
            && p.runNumero()==null && !p.modulo11() && marker!=null
            && marker.fuente()==Fuente.DECLARACION_CIUDADANA && "DOCUMENTO_EXTRANJERO".equals(marker.valor());
    }
    public static Persona crearExtranjera(Clock clock) {
        var attrs=new HashMap<String,Valor>();
        put(attrs,"tipoIdentificacion","DOCUMENTO_EXTRANJERO",null,Fuente.DECLARACION_CIUDADANA,clock.instant(),UUID.randomUUID());
        return new Persona("presentador-adulto",Rol.PRESENTADOR,null,null,false,false,
            ResultadoSrcei.Estado.NO_HABILITADO,null,null,attrs,Map.of());
    }
    public static boolean documentoExtranjeroCompleto(Persona p,Clock clock) {
        if(!extranjera(p))return false;
        for(var key:DOCUMENTO_EXTRANJERO) {
            var value=p.atributos().get(key);
            if(!util(value)||value.fuente()!=Fuente.DECLARACION_CIUDADANA)return false;
            validar(key,value.valor(),clock);
        }
        return true;
    }
    private static final Set<String> NOMBRES = Set.of("primerNombre", "segundoNombre", "primerApellido", "segundoApellido");
    // La respuesta completa se normaliza en memoria. No custodiar profesión, defunción ni circunscripción sin finalidad.
    private static final Set<String> REGISTRALES_PERMITIDOS = Set.of("runNumero", "runDv", "nombres", "primerApellido", "segundoApellido",
        "fechaNacimiento", "sexo", "nacionalidad", "estadoCivil");

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
        // Presentador NNA: si la fuente informa fecha de nacimiento, el RUN que la respuesta contrasta queda registrado con
        // procedencia SRCEI (mismo valor declarado), para que la derivación por edad pueda demostrar la correspondencia.
        boolean contrastarRun=cu==null&&presentadorNna(id,rol)&&resultado!=null&&resultado.atributos().containsKey("fechaNacimiento");
        if(resultado!=null) resultado.atributos().forEach((key,value)->{
            if(!REGISTRALES_PERMITIDOS.contains(key))return;
            var incoming=new Valor(value.valor(),value.codigo(),Fuente.SRCEI,"CONTRASTADO_EN_FUENTE",value.observadoEn(),value.evidencia());
            if(attrs.containsKey(key)) {
                if(!Objects.equals(attrs.get(key).valor(),incoming.valor())) discrepancies.put(key,incoming);
                else if(contrastarRun&&RUN_REGISTRAL.contains(key)&&attrs.get(key).fuente()==Fuente.DECLARACION_CIUDADANA) attrs.put(key,incoming);
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
        var missing=new HashSet<>(CAMPOS_BASE);
        if(extranjera(p))missing.addAll(DOCUMENTO_EXTRANJERO);
        if(p.rol()==Rol.AFECTADO_NNA || (p.rol()==Rol.PRESENTADOR && p.id().equals("presentador-nna"))) {
            missing.remove("fechaNacimiento"); missing.remove("sexo");
        } else missing.remove("edad");
        if(p.rol()!=Rol.PRESENTADOR){missing.remove("fechaNacimiento");missing.remove("sexo");}
        p.atributos().forEach((key,value)->{if(util(value))missing.remove(key);});
        if(util(p.atributos().get("nombres"))) missing.removeAll(Set.of("primerNombre","segundoNombre"));
        if(util(p.atributos().get("apellidos"))) missing.removeAll(Set.of("primerApellido","segundoApellido"));
        // La edad puede inferirse únicamente en servidor a partir de fecha completa, sin publicar la fecha.
        if(util(p.atributos().get("fechaNacimiento"))) missing.remove("edad");
        var declared=new HashMap<String,String>();
        p.atributos().forEach((key,value)->{if(value.fuente()==Fuente.DECLARACION_CIUDADANA && CAMPOS.contains(key)) declared.put(key,value.valor());});
        // Los campos declarados siguen siendo editables, los recuperados nunca se exponen.
        missing.addAll(declared.keySet());
        var required=new HashSet<>(p.rol()==Rol.PRESENTADOR?Set.of("primerNombre","primerApellido"):Set.<String>of()); if(extranjera(p))required.addAll(DOCUMENTO_EXTRANJERO); required.retainAll(missing);
        // Una declaración que desplazó un antecedente registral puede retirarse: declarar() lo restituye.
        required.removeIf(key->srceiUtil(p.discrepancias().get(key))||key.equals("primerNombre")&&srceiUtil(p.discrepancias().get("nombres")));
        return new Vista(p.id(),"REVISADO",p.autenticada(),p.runNumero()!=null,Set.copyOf(missing),Set.copyOf(required),Map.copyOf(declared),
                extranjera(p)?"Estos antecedentes son declarados y no autentican ni verifican tu identidad.":"Completa los datos solicitados. La revisión del RUN no acredita tu identidad.");
    }
    /**
     * Solo completa campos realmente faltantes o edita declaraciones propias previas. Los antecedentes obtenidos de
     * ClaveÚnica o SRCEI nunca se sustituyen por declaraciones ciudadanas, cualquiera sea la petición recibida.
     */
    public static Persona declarar(Persona p,Map<String,String> values,Clock clock) {
        if(values==null||values.size()>CAMPOS.size()||!CAMPOS.containsAll(values.keySet())) throw new IllegalArgumentException("Campos de identificación inválidos");
        var allowed=vista(p).camposFaltantes(); var attrs=new HashMap<>(p.atributos());var discrepancies=new HashMap<>(p.discrepancias());
        if(!allowed.containsAll(values.keySet())) throw new IllegalArgumentException("No sustituir antecedentes de otra fuente");
        for(var entry:values.entrySet()) {
            String key=entry.getKey(),value=entry.getValue()==null?"":entry.getValue().strip();
            var previous=attrs.get(key);
            // Defensa en profundidad: un valor útil de otra fuente nunca es sustituible aunque figure como faltante.
            if(previous!=null&&previous.fuente()!=Fuente.DECLARACION_CIUDADANA&&util(previous)) throw new IllegalArgumentException("No sustituir antecedentes de otra fuente");
            if(value.isEmpty()) {
                if(previous!=null && previous.fuente()==Fuente.DECLARACION_CIUDADANA) {
                    attrs.remove(key);
                    // Al retirar la declaración se restituye el antecedente registral que había desplazado.
                    var original=discrepancies.get(key);
                    if(original!=null&&original.fuente()==Fuente.SRCEI) attrs.put(key,discrepancies.remove(key));
                }
                continue;
            }
            validar(key,value,clock,p.rol()==Rol.PRESENTADOR&&p.id().equals("presentador-adulto"));
            if(previous!=null&&previous.fuente()==Fuente.SRCEI)discrepancies.put(key,previous);
            put(attrs,key,value,null,Fuente.DECLARACION_CIUDADANA,clock.instant(),UUID.randomUUID());
        }
        // Recorridos históricos: una corrección retirada por completo devuelve los nombres registrales agregados.
        var nombres=discrepancies.get("nombres");
        if(nombres!=null&&nombres.fuente()==Fuente.SRCEI&&!attrs.containsKey("nombres")&&!util(attrs.get("primerNombre"))&&!util(attrs.get("segundoNombre")))
            attrs.put("nombres",discrepancies.remove("nombres"));
        return new Persona(p.id(),p.rol(),p.runNumero(),p.runDv(),p.modulo11(),p.autenticada(),p.estadoConsulta(),p.consultadaEn(),p.consultaRef(),attrs,discrepancies);
    }
    /** Palabras separadas por un espacio, guion o apóstrofo; el punto admite abreviaturas («Ma. José», «Jr.»). Igual que personalData.ts. */
    private static final java.util.regex.Pattern FORMA_NOMBRE = java.util.regex.Pattern.compile(
        "\\p{L}[\\p{L}\\p{M}]*(?:(?:[ '’\\-]|\\. ?)\\p{L}[\\p{L}\\p{M}]*)*\\.?");
    private static final java.util.regex.Pattern DOCUMENTO = java.util.regex.Pattern.compile("[\\p{L}\\p{N} ./-]{1,40}");
    private static final java.util.regex.Pattern ALFANUMERICO = java.util.regex.Pattern.compile("[\\p{L}\\p{N}]");
    private static final int MAX_NOMBRE = 100;
    /** Escrituras en que un nombre de un solo carácter es habitual. */
    private static final Set<Character.UnicodeScript> ESCRITURAS_SILABICAS = EnumSet.of(Character.UnicodeScript.HAN,
        Character.UnicodeScript.HANGUL, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.YI);
    public static void validar(String key,String value,Clock clock) { validar(key,value,clock,false); }
    /** @param presentadorAdulto exige más de una inicial en nombres y apellidos de quien presenta sin ClaveÚnica. */
    public static void validar(String key,String value,Clock clock,boolean presentadorAdulto) {
        if(value.length()>500||value.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Atributo inválido");
        if(NOMBRES.contains(key)) {
            if(value.length()>MAX_NOMBRE||!FORMA_NOMBRE.matcher(value).matches()) throw new IllegalArgumentException("Nombre inválido");
            if(presentadorAdulto&&value.codePoints().filter(Character::isLetter).count()<2
                &&value.codePoints().noneMatch(c->ESCRITURAS_SILABICAS.contains(Character.UnicodeScript.of(c))))
                throw new IllegalArgumentException("Nombre inválido");
        }
        if(key.equals("nacionalidad")&&!NacionalidadesFormulario.valida(value))throw new IllegalArgumentException("Nacionalidad inválida");
        if(key.equals("tipoDocumento")&&!Set.of("PASAPORTE","IDENTIDAD_EXTRANJERA","OTRO").contains(value))throw new IllegalArgumentException("Tipo de documento inválido");
        if(key.equals("numeroDocumento")&&(!DOCUMENTO.matcher(value).matches()||ALFANUMERICO.matcher(value).results().count()<3))
            throw new IllegalArgumentException("Número de documento inválido");
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
    private static boolean srceiUtil(Valor value){return util(value)&&value.fuente()==Fuente.SRCEI;}
    /** Calendario civil aplicable: la edad se calcula con la fecha local de Chile, no con la del servidor. */
    public static final ZoneId ZONA_CHILE = ZoneId.of("America/Santiago");
    private static final java.util.regex.Pattern FECHA_REGISTRAL = java.util.regex.Pattern.compile("[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}");
    /**
     * Mayoría de edad acreditada exclusivamente por la fecha de nacimiento SRCEI de una consulta exitosa del RUN de la
     * propia persona. Nunca usa edades o fechas declaradas: ante dato ausente, truncado, futuro o inverosímil responde
     * {@code false} y el recorrido se mantiene. Un nacimiento el 29 de febrero cumple años el 1 de marzo en años no
     * bisiestos (criterio conservador: se mantiene el recorrido protector hasta completar el año).
     */
    public static boolean mayorDeEdadRegistral(Persona p, Clock clock) {
        if (p == null || p.runNumero() == null || p.runDv() == null || !p.modulo11() || p.consultaRef() == null || p.consultadaEn() == null
            || (p.estadoConsulta() != ResultadoSrcei.Estado.COMPLETO && p.estadoConsulta() != ResultadoSrcei.Estado.PARCIAL)) return false;
        // RUN, dígito y fecha deben provenir de la misma respuesta SRCEI (evidencia = referencia de consulta) y el RUN
        // contrastado debe ser el consultado. Un registro que no lo demuestra, incluido uno histórico, nunca deriva.
        var number = contrastado(p, "runNumero"); var digit = contrastado(p, "runDv"); var value = contrastado(p, "fechaNacimiento");
        if (number == null || digit == null || value == null || !p.runNumero().equals(number.valor()) || !p.runDv().equalsIgnoreCase(digit.valor())
            || !FECHA_REGISTRAL.matcher(value.valor()).matches()) return false;
        LocalDate birth;
        try { birth = LocalDate.parse(value.valor()); } catch (DateTimeException invalid) { return false; }
        var today = LocalDate.ofInstant(clock.instant(), ZONA_CHILE);
        if (birth.isAfter(today) || birth.isBefore(today.minusYears(130))) return false;
        return Period.between(birth, today).getYears() >= 18;
    }
    private static final Set<String> RUN_REGISTRAL = Set.of("runNumero", "runDv");
    private static boolean presentadorNna(String id, Rol rol) { return rol == Rol.PRESENTADOR && "presentador-nna".equals(id); }
    /**
     * Antecedente SRCEI de la consulta vigente para {@code key}, buscado en atributos y discrepancias. Responde {@code null}
     * si no existe, si alguno no está contrastado en fuente, trae código, pertenece a otra evidencia o si ambos difieren.
     */
    private static Valor contrastado(Persona p, String key) {
        Valor found = null;
        for (var value : Arrays.asList(p.atributos().get(key), p.discrepancias().get(key))) {
            if (value == null || value.fuente() != Fuente.SRCEI) continue;
            if (!"CONTRASTADO_EN_FUENTE".equals(value.nivel()) || value.codigo() != null || value.valor() == null
                || !p.consultaRef().equals(value.evidencia()) || (found != null && !mismoValor(key, found.valor(), value.valor()))) return null;
            found = value;
        }
        return found;
    }
    private static boolean mismoValor(String key, String a, String b) { return "runDv".equals(key) ? a.equalsIgnoreCase(b) : a.equals(b); }
    /**
     * Reidentificación con el mismo RUN dentro del recorrido: reincorpora los antecedentes SRCEI ya obtenidos, para que un
     * reinicio seguido de una consulta fallida no los convierta en campos faltantes declarables. No reconsulta.
     */
    public static Persona reanudar(Persona nueva,Persona anterior) {
        if(!Objects.equals(nueva.runNumero(),anterior.runNumero())||!Objects.equals(nueva.runDv(),anterior.runDv())||nueva.rol()!=anterior.rol()
            ||nueva.autenticada()!=anterior.autenticada()) throw new IllegalArgumentException("Identificación distinta");
        // La fusión conserva una sola copia por clave: un registro ambiguo no se reutiliza (perdería la copia en conflicto).
        if(!registralesConsistentes(anterior)) throw new IllegalArgumentException("Antecedentes registrales ambiguos");
        var registrales=new HashMap<String,Valor>();
        anterior.discrepancias().forEach((key,value)->{if(value.fuente()==Fuente.SRCEI)registrales.put(key,value);});
        anterior.atributos().forEach((key,value)->{if(value.fuente()==Fuente.SRCEI)registrales.put(key,value);});
        var attrs=new HashMap<>(nueva.atributos());var discrepancies=new HashMap<>(nueva.discrepancias());
        registrales.forEach((key,value)->{
            if(!attrs.containsKey(key))attrs.put(key,value);
            else if(!Objects.equals(attrs.get(key).valor(),value.valor()))discrepancies.put(key,value);
            // Conserva la procedencia SRCEI del RUN contrastado (ver crear); solo el presentador NNA la registra.
            else if(presentadorNna(nueva.id(),nueva.rol())&&RUN_REGISTRAL.contains(key)&&attrs.get(key).fuente()==Fuente.DECLARACION_CIUDADANA)attrs.put(key,value);
        });
        return new Persona(nueva.id(),nueva.rol(),nueva.runNumero(),nueva.runDv(),nueva.modulo11(),nueva.autenticada(),anterior.estadoConsulta(),
            anterior.consultadaEn(),anterior.consultaRef(),attrs,discrepancies);
    }
    /** Ninguna clave tiene en atributos y discrepancias dos antecedentes SRCEI distintos (valor, evidencia o metadatos). */
    public static boolean registralesConsistentes(Persona p) {
        for(var entry:p.discrepancias().entrySet()) {
            var kept=p.atributos().get(entry.getKey());
            if(entry.getValue().fuente()==Fuente.SRCEI&&kept!=null&&kept.fuente()==Fuente.SRCEI&&!kept.equals(entry.getValue()))return false;
        }
        return true;
    }
    /** Hay antecedentes SRCEI aprovechables (en atributos o desplazados a discrepancias). */
    public static boolean conRegistrales(Persona p) {
        return p.atributos().values().stream().anyMatch(IdentificacionCiudadana::srceiUtil)||p.discrepancias().values().stream().anyMatch(IdentificacionCiudadana::srceiUtil);
    }
    private static boolean util(Valor value){return value!=null&&value.valor()!=null&&!value.valor().isBlank()&&!"Desconocido".equals(value.valor())&&!"U".equals(value.codigo());}
}
