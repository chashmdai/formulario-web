package cl.nexbyte.siger.robustez;
import java.net.URI;
import java.util.*;
/** Gramática deliberadamente estricta: sin propiedades ambiguas ni aliases TLS. */
public final class ConfiguracionDatos {
 private ConfiguracionDatos(){}
 public static void validar(String url,boolean local){
  if(url==null||!url.startsWith("jdbc:mysql://"))throw new IllegalArgumentException("URL MySQL inválida");
  var uri=URI.create(url.substring(5));
  if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getRawPath()==null||!uri.getRawPath().matches("/[A-Za-z0-9_]+"))throw new IllegalArgumentException("URL MySQL inválida");
  boolean test=local&&"127.0.0.1".equals(uri.getHost())&&uri.getPort()==13306&&Set.of("/robust_public_test","/robust_inst_test","/robust_public_local","/robust_inst_local").contains(uri.getPath());
  var params=new HashMap<String,String>();
  var allowed=Set.of("sslMode","connectTimeout","socketTimeout","allowPublicKeyRetrieval");
  if(uri.getRawQuery()!=null)for(var part:uri.getRawQuery().split("&",-1)){
   var pair=part.split("=",-1);
   if(pair.length!=2||!allowed.contains(pair[0])||!pair[1].matches("[A-Za-z0-9_]+")||params.putIfAbsent(pair[0],pair[1])!=null)throw new IllegalArgumentException("Parámetros MySQL ambiguos o no admitidos");
  }
  var mode=params.get("sslMode");
  if(!"VERIFY_IDENTITY".equals(mode)&&!(test&&"DISABLED".equals(mode)))throw new IllegalArgumentException("MySQL requiere TLS VERIFY_IDENTITY");
  if(params.containsKey("allowPublicKeyRetrieval")&&!(test&&"DISABLED".equals(mode)&&"true".equals(params.get("allowPublicKeyRetrieval"))))throw new IllegalArgumentException("Intercambio RSA solo en laboratorio");
  for(var key:List.of("connectTimeout","socketTimeout"))if(params.containsKey(key)&&!params.get(key).matches("[1-9][0-9]{0,5}"))throw new IllegalArgumentException("Timeout inválido");
 }
}
