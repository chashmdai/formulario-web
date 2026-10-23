package cl.nexbyte.siger.identidad;

import java.io.Serializable;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/** Only verified identity is kept in the HTTP session; no OAuth tokens. */
public record IdentidadVerificada(String run, String nombres, String apellidos,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) List<String> nombresComponentes,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) List<String> apellidosComponentes,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String runNumero,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String runDv) implements Principal, Serializable {
    public IdentidadVerificada {
        if (run == null || !run.matches("[1-9][0-9]{0,7}-[0-9K]")) throw new IllegalArgumentException("RUN inválido");
        if ((runNumero==null)!=(runDv==null) || (runNumero!=null && (!runNumero.matches("[1-9][0-9]{0,7}")
                || !runDv.matches("[0-9kK]") || !run.equals(runNumero+"-"+runDv.toUpperCase(java.util.Locale.ROOT)))))
            throw new IllegalArgumentException("Componentes de RUN incoherentes");
        if (cl.nexbyte.siger.contrato.ComponentesIdentidad.validarRepresentacion(nombres,apellidos,nombresComponentes,apellidosComponentes)) {
            nombresComponentes=List.copyOf(nombresComponentes);apellidosComponentes=List.copyOf(apellidosComponentes);
        }
    }
    public IdentidadVerificada(String run,String nombres,String apellidos,List<String> nombresComponentes,List<String> apellidosComponentes) {
        this(run,nombres,apellidos,nombresComponentes,apellidosComponentes,null,null);
    }
    /** Compatibilidad histórica; no implica conocer los componentes originales. */
    public IdentidadVerificada(String run,String nombres,String apellidos) { this(run,nombres,apellidos,null,null); }
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Override public String getName() { return run; }
    @Override public String toString() { return "IdentidadVerificada[datos protegidos]"; }

    public static IdentidadVerificada from(Map<String, Object> claims) {
        if (!(claims.get("RolUnico") instanceof Map<?, ?> run) || !(claims.get("name") instanceof Map<?, ?> name)
                || !"RUN".equals(run.get("tipo"))) throw invalid();
        String numero = String.valueOf(run.get("numero"));
        String dvOriginal = String.valueOf(run.get("DV"));
        String dv = dvOriginal.toUpperCase(java.util.Locale.ROOT);
        if (!numero.matches("[1-9][0-9]{0,7}") || !dv.matches("[0-9K]")) throw invalid();
        // Sandbox uses official synthetic RUNs; do not reject them by recalculating DV.
        try {
            var nombres=cl.nexbyte.siger.contrato.ComponentesIdentidad.validar(name.get("nombres"));
            var apellidos=cl.nexbyte.siger.contrato.ComponentesIdentidad.validar(name.get("apellidos"));
            return new IdentidadVerificada(numero + "-" + dv,String.join(" ",nombres),String.join(" ",apellidos),nombres,apellidos,numero,dvOriginal);
        } catch (IllegalArgumentException e) { throw invalid(); }
    }

    private static OAuth2AuthenticationException invalid() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info"), "Identidad del proveedor incompleta o inválida.");
    }
}
