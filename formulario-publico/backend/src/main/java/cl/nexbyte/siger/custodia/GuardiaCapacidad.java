package cl.nexbyte.siger.custodia;
import java.nio.file.*;
import java.util.function.BooleanSupplier;
import org.springframework.core.env.Environment;
import org.springframework.util.unit.DataSize;
/** Piso del filesystem compartido con Docker/MySQL en la VM: también cubre ingresos sin adjuntos. */
public final class GuardiaCapacidad {
 public static BooleanSupplier desde(Environment env){
  long min=DataSize.parse(env.getProperty("capacidad.minimo-libre","0B")).toBytes();if(min<0)throw new IllegalArgumentException("Mínimo de capacidad inválido");
  if(min==0)return ()->true;Path root=Path.of(env.getRequiredProperty("formulario.multimedia.directorio")).toAbsolutePath().normalize();
  return ()->{try{Path p=root;while(p!=null&&!Files.exists(p,LinkOption.NOFOLLOW_LINKS))p=p.getParent();return p!=null&&!Files.isSymbolicLink(p)&&Files.getFileStore(p).getUsableSpace()>=min;}catch(java.io.IOException unavailable){return false;}};
 }
 private GuardiaCapacidad(){}
}