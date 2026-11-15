package cl.nexbyte.siger.multimedia;
import cl.nexbyte.siger.robustez.FalloTecnico;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.*;
import java.time.Duration;
import org.springframework.stereotype.Component;
import java.util.regex.*;
import org.springframework.http.*;
/** Range único y escritura no bloqueante, con cupos independientes de ciudadanía e integración. */
@Component
public final class TransferenciaPrivada{
 private final CuposTransferencia cupos;
 public TransferenciaPrivada(CuposTransferencia cupos){this.cupos=cupos;}
 public record Rango(long inicio,long longitud,boolean parcial){}
 public static Rango rango(String header,long size){
  if(header==null)return new Rango(0,size,false);var m=Pattern.compile("bytes=([0-9]*)-([0-9]*)").matcher(header);if(!m.matches()||m.group(1).isEmpty()&&m.group(2).isEmpty())throw new FalloTecnico(416);
  try{long begin,end;if(m.group(1).isEmpty()){long suffix=Long.parseLong(m.group(2));if(suffix<=0)throw new FalloTecnico(416);begin=Math.max(0,size-suffix);end=size-1;}else{begin=Long.parseLong(m.group(1));end=m.group(2).isEmpty()?size-1:Math.min(size-1,Long.parseLong(m.group(2)));}if(begin<0||begin>=size||end<begin)throw new FalloTecnico(416);return new Rango(begin,end-begin+1,true);}catch(NumberFormatException e){throw new FalloTecnico(416);}
 }
 public void enviarCiudadano(RecursoPrivado source,boolean inline,HttpServletRequest req,HttpServletResponse res)throws IOException{
  enviar(source,inline,req,res,false);
 }
 public void enviarIntegracion(RecursoPrivado source,HttpServletRequest req,HttpServletResponse res)throws IOException{
  enviar(source,false,req,res,true);
 }
 private void enviar(RecursoPrivado source,boolean inline,HttpServletRequest req,HttpServletResponse res,boolean integracion)throws IOException{
  var permiso=integracion?cupos.integracion():cupos.descarga(req);
  boolean asincrona=false;
  try{
   String tag="\""+source.sha256()+"\"",range="GET".equals(req.getMethod())?req.getHeader("Range"):null,condition=req.getHeader("If-Range");if(condition!=null&&!tag.equals(condition))range=null;
   Rango r;try{r=rango(range,source.tamanio());}catch(FalloTecnico invalid){res.setStatus(416);res.setHeader("Content-Range","bytes */"+source.tamanio());res.setHeader("Cache-Control","no-store");return;}
   res.setStatus(r.parcial()?206:200);res.setHeader("Cache-Control","no-store");res.setHeader("Accept-Ranges","bytes");res.setHeader("ETag",tag);res.setHeader("X-Content-Type-Options","nosniff");res.setHeader("Content-Security-Policy","sandbox; default-src 'none'; frame-ancestors 'self'");res.setHeader("Cross-Origin-Resource-Policy","same-origin");res.setContentType(inline?source.tipo():"application/octet-stream");
   var disposition=inline?ContentDisposition.inline():ContentDisposition.attachment();res.setHeader("Content-Disposition",disposition.filename(source.nombre(),StandardCharsets.UTF_8).build().toString());res.setContentLengthLong(r.longitud());if(r.parcial())res.setHeader("Content-Range","bytes "+r.inicio()+"-"+(r.inicio()+r.longitud()-1)+"/"+source.tamanio());
   if(!"HEAD".equals(req.getMethod())){
    Escritura.iniciar(source,r,req,res,cupos.plazoDescarga(integracion),permiso);
    asincrona=true;
   }
  }finally{if(!asincrona)permiso.close();}
 }

 /** Nunca espera a que el socket esté escribible; el timeout del contenedor cancela al cliente detenido. */
 private static final class Escritura implements WriteListener, AsyncListener {
  private final AsyncContext async;
  private final InputStream input;
  private final ServletOutputStream output;
  private final CuposTransferencia.Permiso permiso;
  private final long vence;
  private long restantes;
  private boolean terminada;
  private final byte[] buffer=new byte[16384];

  static void iniciar(RecursoPrivado source,Rango rango,HttpServletRequest req,HttpServletResponse res,
                      Duration plazo,CuposTransferencia.Permiso permiso)throws IOException{
   if(!req.isAsyncSupported())throw new IOException("Transferencia requiere Servlet async");
   var input=source.abrir().get();
   try{
    input.skipNBytes(rango.inicio());
    var output=res.getOutputStream();
    var async=req.startAsync();
    var escritura=new Escritura(async,input,output,rango.longitud(),plazo,permiso);
    async.addListener(escritura);
    async.setTimeout(plazo.toMillis());
    try{output.setWriteListener(escritura);}
    catch(UnsupportedOperationException sinNoBloqueante){
     // Contenedores sin escritura no bloqueante (p. ej. MockMvc): copia acotada por el mismo plazo y cupo.
     escritura.copiarBloqueante();
    }
    catch(RuntimeException error){escritura.terminar();throw error;}
   }catch(IOException|RuntimeException error){input.close();throw error;}
  }
  private Escritura(AsyncContext async,InputStream input,ServletOutputStream output,long restantes,
                    Duration plazo,CuposTransferencia.Permiso permiso){
   this.async=async;this.input=input;this.output=output;this.restantes=restantes;this.permiso=permiso;
   vence=System.nanoTime()+plazo.toNanos();
  }
  @Override public synchronized void onWritePossible()throws IOException{
   try{
    while(!terminada&&output.isReady()){
     if(System.nanoTime()>=vence){terminar();return;}
     if(restantes==0){terminar();return;}
     int n=input.read(buffer,0,(int)Math.min(restantes,buffer.length));
     if(n<0)throw new EOFException("Objeto incompleto");
     output.write(buffer,0,n);
     restantes-=n;
    }
   }catch(IOException|RuntimeException error){terminar();throw error;}
  }
  synchronized void copiarBloqueante()throws IOException{
   try{
    while(!terminada&&restantes>0){
     if(System.nanoTime()>=vence)throw new IOException("Plazo de transferencia vencido");
     int n=input.read(buffer,0,(int)Math.min(restantes,buffer.length));
     if(n<0)throw new EOFException("Objeto incompleto");
     output.write(buffer,0,n);
     restantes-=n;
    }
    output.flush();
   }finally{terminar();}
  }
  private synchronized void terminar(){
   if(terminada)return;
   terminada=true;
   try{input.close();}catch(IOException ignored){/* La conexión ya se cancela. */}
   finally{permiso.close();async.complete();}
  }
  @Override public void onError(Throwable error){terminar();}
  @Override public void onTimeout(AsyncEvent event){terminar();}
  @Override public void onError(AsyncEvent event){terminar();}
  @Override public void onComplete(AsyncEvent event){terminar();}
  @Override public void onStartAsync(AsyncEvent event){event.getAsyncContext().addListener(this);}
 }
}
