package cl.nexbyte.siger.formulario;

import cl.nexbyte.siger.config.OrigenCliente;
import cl.nexbyte.siger.robustez.FalloTecnico;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import static cl.nexbyte.siger.formulario.IdentificacionCiudadana.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Una respuesta 410 perdida o una recarga no abren otro recorrido NNA: la sesión propietaria recibe la señal una vez. */
class DerivacionSesionHttpTest {
    private final RecorridosJdbc journeys=mock(RecorridosJdbc.class);
    private final MockHttpSession session=new MockHttpSession();
    private final FormularioController controller;
    private final UUID id=UUID.randomUUID();
    DerivacionSesionHttpTest(){
        @SuppressWarnings("unchecked") ObjectProvider<RecorridosJdbc> provider=mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(journeys);
        controller=new FormularioController(provider,new OrigenCliente(new MockEnvironment()));
        when(journeys.iniciar(any(),anyBoolean(),anyString(),anyString(),anyString())).thenReturn(new RecorridosJdbc.Recorrido(id,Tipo.NNA,"NNA_LAB_1",false,"BORRADOR",Instant.now()));
    }
    private MockHttpServletRequest request(String method,String path){
        var request=new MockHttpServletRequest(method,path);request.setSecure(true);request.setRemoteAddr("127.0.0.1");request.setSession(session);return request;
    }
    private int status(Runnable action){return assertThrows(FalloTecnico.class,action::run).status;}

    @Test void derivacionPerdidaSeSenalaUnaSolaVezAlRecargar(){
        controller.iniciar(new FormularioController.Inicio(Tipo.NNA,false,"NNA_LAB_1"),request("POST","/api/formulario/recorridos"));
        when(journeys.identificar(eq(id),eq("presentador-nna"),any(),any(),any(),anyString(),anyString(),any())).thenThrow(new FalloTecnico(410));
        assertEquals(410,status(()->controller.identificar(id,"presentador-nna",new FormularioController.Identificacion(Rol.PRESENTADOR,"12345678","5"),request("POST","/x"),null)));
        assertEquals(410,status(()->controller.actual(request("GET","/api/formulario/actual"))));
        // Consumida: otra visita al formulario NNA desde el mismo navegador puede abrir un recorrido nuevo.
        assertEquals(404,status(()->controller.actual(request("GET","/api/formulario/actual"))));
        verify(journeys,never()).consultar(any(),anyString());
    }
    @Test void solo410DelRecorridoVigenteMarcaLaSesion(){
        controller.iniciar(new FormularioController.Inicio(Tipo.NNA,false,"NNA_LAB_1"),request("POST","/api/formulario/recorridos"));
        var ajeno=UUID.randomUUID();
        when(journeys.persona(eq(ajeno),anyString(),anyString())).thenThrow(new FalloTecnico(410));
        when(journeys.persona(eq(id),anyString(),anyString())).thenThrow(new FalloTecnico(404));
        assertEquals(410,status(()->controller.persona(ajeno,"presentador-nna",request("GET","/x"))));
        assertEquals(404,status(()->controller.persona(id,"presentador-nna",request("GET","/x"))));
        var current=new RecorridosJdbc.Recorrido(id,Tipo.NNA,"NNA_LAB_1",false,"BORRADOR",Instant.now());
        when(journeys.consultar(eq(id),anyString())).thenReturn(current);
        assertEquals(current,controller.actual(request("GET","/api/formulario/actual")).getBody());
    }
}
