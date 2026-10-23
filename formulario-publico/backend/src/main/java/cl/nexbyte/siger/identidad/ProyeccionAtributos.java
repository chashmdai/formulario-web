package cl.nexbyte.siger.identidad;
import java.time.Instant;import java.util.*;

/** Contrato de proyección por fuente. Ningún dato SRCeI se inventa o sustituye la identidad de sesión CU. */
public final class ProyeccionAtributos {
    public record Atributo(String valor,String fuente,String nivel,Instant observadoEn,UUID evidenciaRef,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) List<String> componentes){
        public Atributo {
            if(valor==null||valor.length()>2000||fuente==null||nivel==null||observadoEn==null||evidenciaRef==null)throw new IllegalArgumentException("Atributo inválido");
            if(componentes!=null){componentes=cl.nexbyte.siger.contrato.ComponentesIdentidad.validar(componentes);if(!String.join(" ",componentes).equals(valor))throw new IllegalArgumentException("Componentes incoherentes");}
        }
        public Atributo(String valor,String fuente,String nivel,Instant observadoEn,UUID evidenciaRef){this(valor,fuente,nivel,observadoEn,evidenciaRef,null);}
        @Override public String toString(){return "Atributo[fuente="+fuente+",nivel="+nivel+"]";}
    }
    public record Vista(String version,Map<String,List<Atributo>> atributos,Map<String,String> disponibilidadFuentes){}
    public static Vista claveUnica(IdentidadVerificada id,EvidenciaAutenticacion proof){
        if(!proof.corresponde(id))throw new IllegalArgumentException("Evidencia no corresponde");
        var values=new LinkedHashMap<String,List<Atributo>>();
        Map.of("run",id.run(),"nombres",id.nombres(),"apellidos",id.apellidos()).forEach((key,value)->values.put(key,List.of(new Atributo(value,"CLAVE_UNICA_USERINFO","IDENTIDAD_AUTENTICADA",proof.verificadaEn(),proof.id(),key.equals("nombres")?id.nombresComponentes():key.equals("apellidos")?id.apellidosComponentes():null))));
        if(id.runNumero()!=null){
            values.put("runNumero",List.of(new Atributo(id.runNumero(),"CLAVE_UNICA_USERINFO","IDENTIDAD_AUTENTICADA",proof.verificadaEn(),proof.id())));
            values.put("runDv",List.of(new Atributo(id.runDv(),"CLAVE_UNICA_USERINFO","IDENTIDAD_AUTENTICADA",proof.verificadaEn(),proof.id())));
        }
        return new Vista("1.0",Map.copyOf(values),Map.of("CLAVE_UNICA","DISPONIBLE","SRCEI","NO_HABILITADO"));
    }
    /** Punto de extensión exclusivamente backend; un adaptador requerirá autorización y contrato confirmados. */
    public interface FuenteAutorizada {
        Map<String,List<Atributo>> consultar(IdentidadVerificada sujeto,Set<String> atributosAutorizados);
    }
    public static Vista incorporar(Vista actual,Map<String,List<Atributo>> obtenidos,Set<String> autorizados){
        var result=new LinkedHashMap<>(actual.atributos());
        obtenidos.forEach((name,entries)->{
            if(!autorizados.contains(name)||!name.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")||entries==null||entries.isEmpty()||entries.size()>8)throw new IllegalArgumentException("Atributo no autorizado");
            var all=new ArrayList<>(result.getOrDefault(name,List.of()));
            for(var entry:entries){if(!"SRCEI".equals(entry.fuente())||!Set.of("CONTRASTADO_EN_FUENTE","DECLARADO_NO_CONTRASTADO").contains(entry.nivel()))throw new IllegalArgumentException("Procedencia no admitida");all.add(entry);}
            result.put(name,List.copyOf(all));
        });
        return new Vista("1.0",Map.copyOf(result),Map.of("CLAVE_UNICA","DISPONIBLE","SRCEI",obtenidos.isEmpty()?"SIN_ATRIBUTOS_AUTORIZADOS":"DISPONIBLE"));
    }
}
