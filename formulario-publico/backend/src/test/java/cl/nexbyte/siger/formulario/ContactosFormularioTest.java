package cl.nexbyte.siger.formulario;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
class ContactosFormularioTest {
 final tools.jackson.databind.JsonNode cases=JsonMapper.builder().build().readTree(read());
 static String read(){try{return Files.readString(Path.of("../../contratos/fixtures/contactos-formulario-v1.json"));}catch(Exception e){throw new IllegalStateException(e);}}
 @Test void movilesValidosCompartenCanonConReact(){for(var row:cases.get("movilesValidos"))assertEquals(row.get(1).asText(),ContactosFormulario.celular(row.get(0).asText()));}
 @Test void movilesInvalidosNoSeConviertenSilenciosamente(){for(var row:cases.get("movilesInvalidos"))assertThrows(IllegalArgumentException.class,()->ContactosFormulario.celular(row.asText()));}
 @Test void correosAceptanAliasSubdominiosYSufijosSinCambiarParteLocal(){for(var row:cases.get("correosValidos")){assertTrue(ContactosFormulario.correoValido(row.asText()));assertEquals(row.asText().strip(),ContactosFormulario.correo(row.asText()));}}
 @Test void correosInvalidosSeRechazan(){for(var row:cases.get("correosInvalidos"))assertFalse(ContactosFormulario.correoValido(row.asText()));}
 @Test void normalizarNoMutaLaEntradaNiLosCamposAjenos(){var original=new DatosFormulario(Map.of("email","  Nombre+Alias@Sub.correo.algo  ","telefono","912345678","relato","  texto original  "),List.of(),List.of(),List.of());var canonical=ContactosFormulario.normalizar(original);assertEquals("Nombre+Alias@Sub.correo.algo",canonical.campos().get("email"));assertEquals("+56912345678",canonical.campos().get("telefono"));assertEquals("  texto original  ",canonical.campos().get("relato"));assertEquals("912345678",original.campos().get("telefono"));}
}
