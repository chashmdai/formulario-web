package cl.nexbyte.siger.multimedia;
import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
/** INSTREAM de archivo completo con deadline y límites de scanner comprobados. */
public final class AntivirusLocal implements CargasMultimedia.Antivirus{
 private final Path socket;private final long max;
 private static final ScheduledExecutorService TIMER=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"clamav-media-deadline");t.setDaemon(true);return t;});
 public AntivirusLocal(Path socket,Path configuration,long max)throws IOException{
  this.socket=socket;this.max=max;if(Files.isSymbolicLink(socket))throw new IOException("Socket inválido");
  var props=new HashMap<String,String>();for(var line:Files.readAllLines(configuration)){var p=line.strip().split("\\s+",2);if(p.length==2&&!p[0].startsWith("#"))props.put(p[0],p[1]);}
  if(!"yes".equalsIgnoreCase(props.get("AlertExceedsMax"))||size(props.get("StreamMaxLength"))<max||size(props.get("MaxFileSize"))<max||size(props.get("MaxScanSize"))<max*2)throw new IOException("Límites antivirus insuficientes");
  ping();
 }
 private void ping()throws IOException{
  try(var channel=SocketChannel.open(StandardProtocolFamily.UNIX)){
   var timeout=TIMER.schedule(()->{try{channel.close();}catch(IOException ignored){}},5,TimeUnit.SECONDS);
   try{channel.connect(UnixDomainSocketAddress.of(socket));var out=Channels.newOutputStream(channel);out.write("zPING\0".getBytes(StandardCharsets.US_ASCII));out.flush();var in=Channels.newInputStream(channel);var b=new ByteArrayOutputStream();int c;while((c=in.read())!=-1&&c!=0){if(b.size()>8)throw new IOException("Respuesta inválida");b.write(c);}if(c!=0||!b.toString(StandardCharsets.US_ASCII).equals("PONG"))throw new IOException("Antivirus no preparado");}finally{timeout.cancel(false);}
  }
 }
 private static long size(String text)throws IOException{if(text==null||!text.matches("[0-9]+[KMG]?"))throw new IOException("Límite antivirus inválido");char last=text.charAt(text.length()-1);return Character.isDigit(last)?Long.parseLong(text):Long.parseLong(text.substring(0,text.length()-1))*switch(last){case 'K'->1024L;case 'M'->1024L*1024;case 'G'->1024L*1024*1024;default->throw new IOException();};}
 public boolean limpio(Path file)throws IOException{
  if(Files.size(file)>max)throw new IOException("Tamaño no inspeccionable");
  try(var channel=SocketChannel.open(StandardProtocolFamily.UNIX)){
   var deadline=TIMER.schedule(()->{try{channel.close();}catch(IOException ignored){}},90,TimeUnit.SECONDS);
   try{channel.connect(UnixDomainSocketAddress.of(socket));var out=new DataOutputStream(Channels.newOutputStream(channel));out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
    try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){out.writeInt(n);out.write(bytes,0,n);}}out.writeInt(0);out.flush();
    var input=Channels.newInputStream(channel);var response=new ByteArrayOutputStream();int b;while((b=input.read())!=-1&&b!=0){if(response.size()>=512)throw new IOException("Respuesta antivirus inválida");response.write(b);}if(b!=0)throw new IOException("Inspección incompleta");var message=response.toString(StandardCharsets.UTF_8);if(message.equals("stream: OK"))return true;if(message.startsWith("stream: ")&&message.endsWith(" FOUND"))return false;throw new IOException("Inspección no disponible");
   }finally{deadline.cancel(false);}
  }
 }
}
