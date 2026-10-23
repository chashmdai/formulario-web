package cl.nexbyte.siger.identidad;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import static org.assertj.core.api.Assertions.*;

class IdentidadVerificadaTest {
    @Test void componentesRunSeObtienenDelProveedorYNoSeInfierenParaIdentidadesAnteriores() {
        var identity=identidad(List.of("Nombre"),List.of("Apellido"));
        assertThat(identity.runNumero()).isEqualTo("44444444");assertThat(identity.runDv()).isEqualTo("4");
        var legacy=new IdentidadVerificada(identity.run(),identity.nombres(),identity.apellidos());
        assertThat(legacy.runNumero()).isNull();assertThat(legacy.runDv()).isNull();
        assertThatThrownBy(()->new IdentidadVerificada(identity.run(),identity.nombres(),identity.apellidos(),identity.nombresComponentes(),identity.apellidosComponentes(),"55555555","4")).isInstanceOf(IllegalArgumentException.class);
        var lower=IdentidadVerificada.from(Map.of("RolUnico",Map.of("numero",12345678,"DV","k","tipo","RUN"),"name",Map.of("nombres",List.of("Sintético"),"apellidos",List.of("Prueba"))));
        assertThat(lower.runDv()).isEqualTo("k");assertThat(lower.run()).isEqualTo("12345678-K");
    }
    static IdentidadVerificada identidad(List<String> nombres,List<String> apellidos) {
        return IdentidadVerificada.from(Map.of("RolUnico",Map.of("numero",44444444,"DV","4","tipo","RUN"),"name",Map.of("nombres",nombres,"apellidos",apellidos)));
    }
    @Test void conservaComponentesCompuestosEspaciosUnicodeYOrdenSinNormalizar() {
        var nombres=new java.util.ArrayList<>(List.of("  María José  ","De los ángeles","Ma\u0301ria","Cuarto"));
        var apellidos=List.of("Del Río","González de la Fuente","Tercero");
        var original=List.copyOf(nombres);var identity=identidad(nombres,apellidos);nombres.set(0,"Manipulado");
        assertThat(identity.nombresComponentes()).containsExactlyElementsOf(original);
        assertThat(identity.apellidosComponentes()).containsExactlyElementsOf(apellidos);
        assertThat(identity.nombres()).isEqualTo(String.join(" ",original));
        assertThat(identity.nombresComponentes().get(2)).isEqualTo("Ma\u0301ria").isNotEqualTo("Mária");
        assertThatThrownBy(()->identity.nombresComponentes().add("Otro")).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void unSoloNombreYApellidoNoInventaOpcionalesYLaEvidenciaDistingueLimites() {
        assertThat(identidad(List.of("María José"),List.of("Del Río")).nombresComponentes()).containsExactly("María José");
        var a=identidad(List.of("María Carmen","De los ángeles"),List.of("Del Río","González"));
        var b=identidad(List.of("María","Carmen De los ángeles"),List.of("Del Río","González"));
        assertThat(a.nombres()).isEqualTo(b.nombres());
        assertThat(EvidenciaAutenticacion.de(a).corresponde(b)).isFalse();
        var legacy=new IdentidadVerificada(a.run(),a.nombres(),a.apellidos());
        assertThat(legacy.nombresComponentes()).isNull();assertThat(legacy.apellidosComponentes()).isNull();
        assertThat(EvidenciaAutenticacion.de(a).corresponde(legacy)).isFalse();
    }
    @Test void rechazaArreglosInvalidosSinCorregirSilenciosamente() {
        for(var names:List.of(List.<String>of(),List.of(" "),java.util.Arrays.asList("Nombre",null),java.util.Collections.nCopies(11,"Nombre"),List.of("a".repeat(101)),List.of("Nombre\n"),List.of("\uD800")))
            assertThatThrownBy(()->identidad(names,List.of("Apellido"))).isInstanceOf(OAuth2AuthenticationException.class);
    }
    @Test void usesRolUnicoAndStructuredNameNeverSubject() {
        var identity = IdentidadVerificada.from(Map.of("sub", "not-the-run", "RolUnico", Map.of("numero", 44444444, "DV", "4", "tipo", "RUN"),
                "name", Map.of("nombres", List.of("Nombre", "Segundo"), "apellidos", List.of("Apellido"))));
        assertThat(identity.run()).isEqualTo("44444444-4"); assertThat(identity.nombres()).isEqualTo("Nombre Segundo");
        assertThat(identity.toString()).doesNotContain(identity.run(), identity.nombres());
    }
    @Test void rejectsMissingOrMalformedIdentity() {
        assertThatThrownBy(() -> IdentidadVerificada.from(Map.of("sub", "12345678"))).isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(() -> IdentidadVerificada.from(Map.of("RolUnico", Map.of("numero", "malicious", "DV", "4", "tipo", "RUN"),
                "name", Map.of("nombres", List.of("Nombre"), "apellidos", List.of("Apellido"))))).isInstanceOf(OAuth2AuthenticationException.class);
    }
}
