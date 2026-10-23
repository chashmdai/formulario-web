package cl.nexbyte.siger.contrato;

import java.util.List;

/** Validación sin recortar, normalizar o deducir separaciones. Contrato independiente por artefacto. */
public final class ComponentesIdentidad {
    private ComponentesIdentidad() {}
    public static List<String> validar(Object value) {
        if (!(value instanceof List<?> items) || items.isEmpty() || items.size() > 10
                || items.stream().anyMatch(v -> !(v instanceof String s) || !valido(s)))
            throw new IllegalArgumentException("Componentes de identidad inválidos");
        return items.stream().map(String.class::cast).toList();
    }
    private static boolean valido(String value) {
        if (value.isBlank() || value.length() > 100) return false;
        for (int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i+1>=value.length() || !Character.isLowSurrogate(value.charAt(++i))) return false;
            } else if (Character.isLowSurrogate(c) || Character.isISOControl(c)) return false;
        }
        return true;
    }
    public static boolean validarRepresentacion(String nombres,String apellidos,List<String> nombresComponentes,List<String> apellidosComponentes) {
        if (nombres == null || nombres.isBlank() || apellidos == null || apellidos.isBlank()
                || nombres.length() > 1009 || apellidos.length() > 1009
                || (nombresComponentes == null) != (apellidosComponentes == null))
            throw new IllegalArgumentException("Identidad inválida");
        if (nombresComponentes == null) return false; // Histórico: nunca deducir listas desde las cadenas.
        if (!String.join(" ", validar(nombresComponentes)).equals(nombres)
                || !String.join(" ", validar(apellidosComponentes)).equals(apellidos))
            throw new IllegalArgumentException("Texto de identidad incoherente");
        return true;
    }
}
