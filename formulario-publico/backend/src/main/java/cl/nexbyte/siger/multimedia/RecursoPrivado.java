package cl.nexbyte.siger.multimedia;
import java.io.*;
/** Fuente acotada que permite abrir un recurso autorizado sin materializarlo en heap. */
public record RecursoPrivado(String nombre,long tamanio,String sha256,String tipo,Abrir abrir){
 public interface Abrir{InputStream get()throws IOException;}
 public void copiar(OutputStream out,long offset,long count)throws IOException{try(var in=abrir.get()){in.skipNBytes(offset);byte[] bytes=new byte[65536];while(count>0){int n=in.read(bytes,0,(int)Math.min(count,bytes.length));if(n<0)throw new EOFException("Objeto incompleto");out.write(bytes,0,n);count-=n;}}}
}
