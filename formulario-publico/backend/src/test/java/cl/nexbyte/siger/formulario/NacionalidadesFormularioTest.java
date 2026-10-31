package cl.nexbyte.siger.formulario;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NacionalidadesFormularioTest {
 @Test void permitePaisesEstandarizadosSinCambiarProcedencia(){
  var p=IdentificacionCiudadana.crearExtranjera(Clock.systemUTC());
  for(var value:new String[]{"Perú","Chile","Venezuela","Canadá","Estados Unidos","Palestina, Estado de"}){
   var declared=IdentificacionCiudadana.declarar(p,Map.of("nacionalidad",value),Clock.systemUTC());
   assertEquals(value,declared.atributos().get("nacionalidad").valor());
   assertEquals(IdentificacionCiudadana.Fuente.DECLARACION_CIUDADANA,declared.atributos().get("nacionalidad").fuente());assertFalse(declared.autenticada());
  }
 }
 @Test void rechazaTextoLibreOCodigoQueNoFueSeleccionadoDelCatalogo(){
  var p=IdentificacionCiudadana.crearExtranjera(Clock.systemUTC());
  for(var value:new String[]{"SINTÉTICA","Venezolana","PE","Otra / situación especial:","Otra / situación especial:  ","Otra / situación especial: "+"x".repeat(121),"Otra / situación especial: Apá\ntrida"})
   assertThrows(IllegalArgumentException.class,()->IdentificacionCiudadana.declarar(p,Map.of("nacionalidad",value),Clock.systemUTC()),value);
 }
 @Test void permiteExcepcionExplicitaAcotadaSinFabricarIdentidadVerificada(){
  var p=IdentificacionCiudadana.crearExtranjera(Clock.systemUTC());
  for(var value:new String[]{"Otra / situación especial: Apátrida","Otra / situación especial: "+"x".repeat(120)}){
   var declared=IdentificacionCiudadana.declarar(p,Map.of("nacionalidad",value),Clock.systemUTC());
   assertEquals(value,declared.atributos().get("nacionalidad").valor());assertFalse(declared.autenticada());assertNull(declared.runNumero());
  }
 }
}
