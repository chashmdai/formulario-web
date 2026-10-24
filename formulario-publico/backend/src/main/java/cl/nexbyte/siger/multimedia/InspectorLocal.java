package cl.nexbyte.siger.multimedia;
import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import tools.jackson.databind.json.JsonMapper;
/** Transporte local por Unix socket. Solo se transmite el archivo actual, nunca el almacén completo. */
public final class InspectorLocal implements CargasMultimedia.Inspector{
 private final Path socket;private final JsonMapper mapper;
 private static final ScheduledExecutorService TIMER=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"inspector-deadline");t.setDaemon(true);return t;});
 public InspectorLocal(Path socket)throws IOException{this.socket=socket;
  this.mapper=JsonMapper.builder(tools.jackson.core.json.JsonFactory.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(tools.jackson.core.StreamReadConstraints.builder().maxNestingDepth(4).maxStringLength(3*1024*1024).maxNumberLength(20).build()).build()).build();if(Files.isSymbolicLink(socket))throw new IOException("Socket inválido");var health=intercambiar(Map.of("salud",true),null);if(!"LANDLOCK3_SECCOMP".equals(health.path("aislamiento").asString()))throw new IOException("Inspección aislada no disponible");}
 private tools.jackson.databind.JsonNode intercambiar(Map<String,Object> header,Path file)throws IOException{
  try(var channel=SocketChannel.open(StandardProtocolFamily.UNIX)){
   var deadline=TIMER.schedule(()->{try{channel.close();}catch(IOException ignored){}},75,TimeUnit.SECONDS);
   try{channel.connect(UnixDomainSocketAddress.of(socket));var out=Channels.newOutputStream(channel);out.write(mapper.writeValueAsBytes(header));out.write(10);if(file!=null)try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){in.transferTo(out);}out.flush();var input=Channels.newInputStream(channel);var response=new ByteArrayOutputStream();int b;while((b=input.read())!=-1&&b!=10){if(response.size()>=3*1024*1024)throw new IOException("Respuesta de inspección excesiva");response.write(b);}if(b!=10)throw new IOException("Inspección incompleta");return mapper.readTree(response.toByteArray());}finally{deadline.cancel(false);}
  }
 }
 public CargasMultimedia.Inspeccion examinar(Path file,PoliticaMultimedia.Formato format)throws IOException{
  var node=intercambiar(Map.of("extension",format.extension(),"tamanio",Files.size(file)),file);
  if(node.has("error"))throw new IllegalArgumentException("Contenido no admitido");
  var type=node.path("tipo").asString();var view=node.path("vista").asString();if(!format.tipo().equals(type)||!Set.of("DESCARGA","IMAGEN_DERIVADA","AUDIO","VIDEO").contains(view))throw new IOException("Resultado de inspección inválido");
  byte[] png=null;if(node.has("png")){try{png=Base64.getDecoder().decode(node.path("png").asString());}catch(IllegalArgumentException e){throw new IOException("Representación inválida");}if(png.length>2*1024*1024||png.length<8||png[0]!=(byte)137||png[1]!=80||png[2]!=78||png[3]!=71)throw new IOException("Representación inválida");}
  if(view.equals("IMAGEN_DERIVADA")&&png==null)throw new IOException("Representación incompleta");return new CargasMultimedia.Inspeccion(type,view,png);
 }
}
