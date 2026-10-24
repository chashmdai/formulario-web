package cl.nexbyte.siger.multimedia;
import cl.nexbyte.siger.robustez.FalloTecnico;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.regex.*;
import org.springframework.http.*;
/** Range único, validación previa y cuatro respuestas simultáneas; sin executor asíncrono ilimitado. */
public final class TransferenciaPrivada{
 private static final Semaphore DOWNLOADS=new Semaphore(4);
 public record Rango(long inicio,long longitud,boolean parcial){}
 public static Rango rango(String header,long size){
  if(header==null)return new Rango(0,size,false);var m=Pattern.compile("bytes=([0-9]*)-([0-9]*)").matcher(header);if(!m.matches()||m.group(1).isEmpty()&&m.group(2).isEmpty())throw new FalloTecnico(416);
  try{long begin,end;if(m.group(1).isEmpty()){long suffix=Long.parseLong(m.group(2));if(suffix<=0)throw new FalloTecnico(416);begin=Math.max(0,size-suffix);end=size-1;}else{begin=Long.parseLong(m.group(1));end=m.group(2).isEmpty()?size-1:Math.min(size-1,Long.parseLong(m.group(2)));}if(begin<0||begin>=size||end<begin)throw new FalloTecnico(416);return new Rango(begin,end-begin+1,true);}catch(NumberFormatException e){throw new FalloTecnico(416);}
 }
 public static void enviar(RecursoPrivado source,boolean inline,HttpServletRequest req,HttpServletResponse res)throws IOException{
  if(!DOWNLOADS.tryAcquire())throw new FalloTecnico(429);
  try{
   String tag="\""+source.sha256()+"\"",range="GET".equals(req.getMethod())?req.getHeader("Range"):null,condition=req.getHeader("If-Range");if(condition!=null&&!tag.equals(condition))range=null;
   Rango r;try{r=rango(range,source.tamanio());}catch(FalloTecnico invalid){res.setStatus(416);res.setHeader("Content-Range","bytes */"+source.tamanio());res.setHeader("Cache-Control","no-store");return;}
   res.setStatus(r.parcial()?206:200);res.setHeader("Cache-Control","no-store");res.setHeader("Accept-Ranges","bytes");res.setHeader("ETag",tag);res.setHeader("X-Content-Type-Options","nosniff");res.setHeader("Content-Security-Policy","sandbox; default-src 'none'; frame-ancestors 'self'");res.setHeader("Cross-Origin-Resource-Policy","same-origin");res.setContentType(inline?source.tipo():"application/octet-stream");
   var disposition=inline?ContentDisposition.inline():ContentDisposition.attachment();res.setHeader("Content-Disposition",disposition.filename(source.nombre(),StandardCharsets.UTF_8).build().toString());res.setContentLengthLong(r.longitud());if(r.parcial())res.setHeader("Content-Range","bytes "+r.inicio()+"-"+(r.inicio()+r.longitud()-1)+"/"+source.tamanio());
   if(!"HEAD".equals(req.getMethod()))source.copiar(res.getOutputStream(),r.inicio(),r.longitud());
  }finally{DOWNLOADS.release();}
 }
}
