package cl.nexbyte.siger.robustez;
import cl.nexbyte.siger.multimedia.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
/** Opt-in: solo sockets locales aislados y fixtures sintéticos; no proveedores gubernamentales. */
@EnabledIfEnvironmentVariable(named="MEDIA_REAL_CHECK",matches="true")
class MultimediaWorkersRealTest{
 @Test void transporteJavaInspeccionaRepresentacionesYAntivirusReales()throws Exception{
  var inspector=new InspectorLocal(Path.of(System.getenv("MEDIA_INSPECTOR_SOCKET")));var av=new AntivirusLocal(Path.of(System.getenv("MEDIA_AV_SOCKET")),Path.of(System.getenv("MEDIA_AV_CONFIG")),150*1024*1024L);var root=Path.of(System.getenv("MEDIA_SYNTHETIC_DIR"));
  for(var name:List.of("imagen.png","documento.pdf","oficina.docx","audio.mp3","video.mp4")){
   var p=root.resolve(name);assertTrue(av.limpio(p));var format=PoliticaMultimedia.propuesta().formato(name);var view=inspector.examinar(p,format);assertEquals(format.tipo(),view.tipo());if(name.endsWith("png")||name.endsWith("pdf"))assertNotNull(view.png());
  }
  assertThrows(IllegalArgumentException.class,()->inspector.examinar(root.resolve("falso.png"),PoliticaMultimedia.propuesta().formato("falso.png")));
 }
}
