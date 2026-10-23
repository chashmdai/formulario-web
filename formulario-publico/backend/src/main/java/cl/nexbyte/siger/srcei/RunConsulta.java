package cl.nexbyte.siger.srcei;

/** Identificador declarado válido matemáticamente; no acredita identidad. */
public record RunConsulta(String numero, String dv) {
    public RunConsulta {
        if (numero == null || !numero.matches("[1-9][0-9]{0,7}") || dv == null || !dv.matches("[0-9Kk]"))
            throw new IllegalArgumentException("Formato RUN inválido");
        dv = dv.toUpperCase(java.util.Locale.ROOT);
        int sum = 0, multiplier = 2;
        for (int i = numero.length() - 1; i >= 0; i--) {
            sum += (numero.charAt(i) - '0') * multiplier;
            multiplier = multiplier == 7 ? 2 : multiplier + 1;
        }
        int value = 11 - sum % 11;
        if (!dv.equals(value == 11 ? "0" : value == 10 ? "K" : Integer.toString(value)))
            throw new IllegalArgumentException("DV inválido");
    }
    @Override public String toString() { return "RunConsulta[protegido]"; }
}
