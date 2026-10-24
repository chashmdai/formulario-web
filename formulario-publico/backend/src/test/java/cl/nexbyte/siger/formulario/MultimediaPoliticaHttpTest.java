package cl.nexbyte.siger.formulario;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MultipartFile;
import cl.nexbyte.siger.robustez.FalloTecnico;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MultimediaPoliticaHttpTest{
 @Test void multipartHistoricoNoPuedeEludirInspeccionMultimedia()throws Exception{
  var files=mock(AdjuntosFormulario.class);when(files.multimediaDisponible()).thenReturn(true);
  @SuppressWarnings("unchecked") ObjectProvider<AdjuntosFormulario> provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(files);
  var controller=new RecepcionFormularioController(null,provider);var request=new MockHttpServletRequest();request.setSecure(true);var part=mock(MultipartFile.class);
  assertEquals(409,assertThrows(FalloTecnico.class,()->controller.cargar(UUID.randomUUID(),List.of(part),request)).status);verify(part,never()).getBytes();verify(files,never()).reemplazar(any(),any(),any());
 }
}
