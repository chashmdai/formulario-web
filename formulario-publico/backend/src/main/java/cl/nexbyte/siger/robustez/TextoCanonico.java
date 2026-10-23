package cl.nexbyte.siger.robustez;

import java.text.Normalizer;

public final class TextoCanonico {
    private TextoCanonico() {}
    public static String mensaje(String input) {
        if (input == null || input.length() > 2000) throw new FalloTecnico(400);
        for (int i=0;i<input.length();i++) {
            char value=input.charAt(i);
            if (Character.isHighSurrogate(value)) {
                if (i+1 >= input.length() || !Character.isLowSurrogate(input.charAt(++i))) throw new FalloTecnico(400);
            } else if (Character.isLowSurrogate(value) || (Character.isISOControl(value) && value!='\n' && value!='\r' && value!='\t')) throw new FalloTecnico(400);
        }
        var normalized=Normalizer.normalize(input.replace("\r\n","\n").replace('\r','\n'),Normalizer.Form.NFC).strip();
        if (normalized.isBlank() || normalized.length()>2000) throw new FalloTecnico(400);
        return normalized;
    }
}
