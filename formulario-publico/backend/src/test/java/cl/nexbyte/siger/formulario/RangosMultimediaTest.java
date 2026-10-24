package cl.nexbyte.siger.formulario;
import cl.nexbyte.siger.multimedia.TransferenciaPrivada;
import cl.nexbyte.siger.robustez.FalloTecnico;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RangosMultimediaTest{
 @Test void rangosCompletosParcialesYSufijos(){assertEquals(new TransferenciaPrivada.Rango(0,10,false),TransferenciaPrivada.rango(null,10));assertEquals(new TransferenciaPrivada.Rango(2,3,true),TransferenciaPrivada.rango("bytes=2-4",10));assertEquals(new TransferenciaPrivada.Rango(7,3,true),TransferenciaPrivada.rango("bytes=-3",10));assertEquals(new TransferenciaPrivada.Rango(3,7,true),TransferenciaPrivada.rango("bytes=3-",10));}
 @Test void multiplesOverflowYFueraDeLimitesSon416(){for(var value:new String[]{"bytes=0-1,3-4","bytes=10-","bytes=-0","bytes=-","bytes=5-2","bytes=999999999999999999999999-","otro=1-2"})assertEquals(416,assertThrows(FalloTecnico.class,()->TransferenciaPrivada.rango(value,10)).status);}
}
