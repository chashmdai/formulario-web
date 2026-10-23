package cl.nexbyte.siger.srcei;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Modelo interno: nunca usar como DTO ciudadano ni registrar su contenido. */
public record ResultadoSrcei(Estado estado, Map<String, Atributo> atributos, Instant consultadoEn, UUID evidencia) {
    public enum Estado { NO_HABILITADO, COMPLETO, PARCIAL, VACIO, NEGATIVA, RED, TIMEOUT, AUTORIZACION,
        INDISPONIBLE, XML_INVALIDO, RESPUESTA_EXCESIVA, SATURADO, RUN_DISCREPANTE }
    public record Atributo(String valor, String codigo, String fuente, String nivel, Instant observadoEn, UUID evidencia) {
        public Atributo {
            if (valor == null || valor.isBlank() || valor.length() > 500 || !"SRCEI".equals(fuente)
                    || !"CONTRASTADO_EN_FUENTE".equals(nivel) || observadoEn == null || evidencia == null)
                throw new IllegalArgumentException("Atributo registral inválido");
        }
        @Override public String toString() { return "AtributoSrcei[protegido]"; }
    }
    public ResultadoSrcei { atributos = Map.copyOf(atributos); }
    public static ResultadoSrcei fallo(Estado estado, Instant at, UUID id) { return new ResultadoSrcei(estado, Map.of(), at, id); }
    @Override public String toString() { return "ResultadoSrcei[estado=" + estado + ", cantidad=" + atributos.size() + "]"; }
}
