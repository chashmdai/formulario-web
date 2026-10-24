package cl.nexbyte.siger.multimedia;
import java.text.Normalizer;
import java.util.*;
/** Límites efectivos del camino de flujo, independientes del multipart histórico. */
public record PoliticaMultimedia(int cantidad,long total,long bloque,long imagen,long documento,long audio,long video,long global,long libreMinimo,long venceMs){
 public static final long MIB=1024*1024;
 public record Formato(String extension,String tipo,String categoria,long maximo){}
 public PoliticaMultimedia{
  if(cantidad<1||cantidad>12||total<1||total>1024*MIB||bloque<1||bloque>8*MIB||imagen<1||documento<1||audio<1||video<1||Math.max(Math.max(imagen,documento),Math.max(audio,video))>150*MIB||Math.max(Math.max(imagen,documento),Math.max(audio,video))>bloque*1024||global<2*total||libreMinimo<0||venceMs<60000)throw new IllegalArgumentException("Política multimedia inválida");
 }
 public static PoliticaMultimedia propuesta(){return new PoliticaMultimedia(12,250*MIB,8*MIB,25*MIB,50*MIB,75*MIB,150*MIB,10*1024*MIB,512*MIB,24*3600000L);}
 public List<Formato> formatos(){return List.of(new Formato("pdf","application/pdf","documento",documento),new Formato("txt","text/plain","documento",documento),new Formato("docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document","documento",documento),new Formato("odt","application/vnd.oasis.opendocument.text","documento",documento),new Formato("xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","documento",documento),new Formato("pptx","application/vnd.openxmlformats-officedocument.presentationml.presentation","documento",documento),new Formato("jpg","image/jpeg","imagen",imagen),new Formato("jpeg","image/jpeg","imagen",imagen),new Formato("png","image/png","imagen",imagen),new Formato("webp","image/webp","imagen",imagen),new Formato("mp3","audio/mpeg","audio",audio),new Formato("m4a","audio/mp4","audio",audio),new Formato("wav","audio/wav","audio",audio),new Formato("ogg","audio/ogg","audio",audio),new Formato("mp4","video/mp4","video",video),new Formato("mov","video/quicktime","video",video),new Formato("webm","video/webm","video",video));}
 public String nombre(String name){
  if(name==null)throw new IllegalArgumentException("Nombre requerido");name=Normalizer.normalize(name,Normalizer.Form.NFC);
  if(name.isBlank()||name.length()>180||!name.equals(name.strip())||name.startsWith(".")||name.endsWith(".")||name.codePoints().anyMatch(c->Character.isISOControl(c)||c=='/'||c=='\\'||c==':'))throw new IllegalArgumentException("Nombre no admitido");
  String[] parts=name.toLowerCase(Locale.ROOT).split("\\.");var dangerous=Set.of("exe","com","dll","bat","cmd","ps1","js","vbs","scr","jar","msi","docm","xlsm","pptm");for(var part:parts)if(dangerous.contains(part))throw new IllegalArgumentException("Extensión no admitida");
  formato(name);return name;
 }
 public Formato formato(String name){String ext=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);return formatos().stream().filter(f->f.extension().equals(ext)).findFirst().orElseThrow(()->new IllegalArgumentException("Formato no habilitado"));}
 public void tamanio(String name,long size){if(size<=0||size>formato(name).maximo()||size>total)throw new cl.nexbyte.siger.robustez.FalloTecnico(413);}
}
