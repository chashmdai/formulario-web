package cl.nexbyte.siger.custodia;
import java.io.*;import java.nio.file.*;import java.sql.*;import java.util.*;import java.net.*;import tools.jackson.databind.json.JsonMapper;
/** Herramienta QA/administrativa explícita; no forma parte del agente ni del JAR. */
public final class MysqlTlsLab {
 public record Credencial(String usuario,String password){@Override public String toString(){return "Credencial[protegida]";}}
 public static Credencial secretos()throws Exception{
  var input=System.in.readNBytes(16385);if(input.length>16384)throw new IllegalArgumentException();
  try{var n=JsonMapper.builder().build().readTree(input);return new Credencial(n.get("usuario").asString(),n.get("password").asString());}finally{Arrays.fill(input,(byte)0);}
 }
 public static Connection connect(String url)throws Exception{return connect(url,secretos());}
 public static Connection connect(String url,Credencial secret)throws Exception{
  return DriverManager.getConnection(validarUrl(url),secret.usuario(),secret.password());
 }
 /** Validación pura, previa a autenticación; el bootstrap conserva su hostname real ante Connector/J. */
 static String validarUrl(String url){
  String bootstrap="jdbc:mysql://MySQL_Server_8.0.46_Auto_Generated_Server_Certificate:3306/";
  String checked=url.startsWith(bootstrap)?"jdbc:mysql://localhost:3306/"+url.substring(bootstrap.length()):url;
  var p=new Properties();p.setProperty("qa.url",checked);EmisorGarantiaCustodia.localUrl(p,"qa");
  var uri=URI.create(checked.substring(5));
  if(uri.getPort()!=3306||!Set.of("localhost","127.0.0.1").contains(uri.getHost())||
     !Set.of("/","/siger_custodia_lab").contains(uri.getRawPath()))throw new IllegalArgumentException("Solo motor y esquema de laboratorio");
  var parameters=new HashMap<String,String>();
  for(String pair:uri.getRawQuery().split("&")){var parts=pair.split("=",2);parameters.put(URLDecoder.decode(parts[0],java.nio.charset.StandardCharsets.UTF_8),URLDecoder.decode(parts[1],java.nio.charset.StandardCharsets.UTF_8));}
  if(!"false".equals(parameters.get("fallbackToSystemTrustStore")))throw new IllegalArgumentException("Confianza explícita requerida");
  return url;
 }
 public static void main(String[] a)throws Exception{
  try(var c=connect(a[1]);var s=c.createStatement()){
   if(a[0].equals("inspect")){
    try(var r=s.executeQuery("SHOW VARIABLES WHERE Variable_name IN ('version','datadir','ssl_ca','ssl_cert','ssl_key','tls_version','require_secure_transport','named_pipe')")){while(r.next())System.out.println(r.getString(1)+"="+r.getString(2));}
    try(var r=s.executeQuery("SELECT COUNT(*) FROM information_schema.processlist WHERE ID<>CONNECTION_ID() AND COMMAND<>'Daemon'")){r.next();System.out.println("otrasSesiones="+r.getInt(1));}
    try(var r=s.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='siger_custodia_lab'")){r.next();System.out.println("tablasLab="+r.getInt(1));}
    try(var r=s.executeQuery("SHOW SESSION STATUS WHERE Variable_name IN ('Ssl_cipher','Ssl_version')")){while(r.next())System.out.println(r.getString(1)+"="+r.getString(2));}
   }else if(Set.of("reload","rollback").contains(a[0])){
    try(var r=s.executeQuery("SELECT COUNT(*) FROM information_schema.processlist WHERE ID<>CONNECTION_ID() AND COMMAND<>'Daemon'")){r.next();if(r.getInt(1)!=0)throw new IOException("Sesiones activas: intervención cancelada");}
    var previous=new LinkedHashMap<String,String>();for(String name:List.of("ssl_ca","ssl_cert","ssl_key"))try(var r=s.executeQuery("SHOW GLOBAL VARIABLES LIKE '"+name+"'")){r.next();previous.put(name,r.getString(2));}
    Map<String,String> target;
    if(a[0].equals("rollback"))target=Map.of("ssl_ca","ca.pem","ssl_cert","server-cert.pem","ssl_key","server-key.pem");
    else {String base=Path.of(a[2]).toAbsolutePath().toString().replace((char)92,'/');if(!base.matches("C:/agente/config/mysql-tls-lab-[A-Za-z0-9-]+"))throw new IOException("Destino TLS de laboratorio requerido");target=Map.of("ssl_ca",base+"/ca.pem","ssl_cert",base+"/server-cert.pem","ssl_key",base+"/server-key.pem");}
    try{for(var v:target.entrySet())s.execute("SET GLOBAL "+v.getKey()+"='"+v.getValue().replace("'","''")+"'");
     s.execute("ALTER INSTANCE RELOAD TLS");
    }catch(Exception failure){for(var v:previous.entrySet())s.execute("SET GLOBAL "+v.getKey()+"='"+v.getValue().replace("'","''")+"'");
     s.execute("ALTER INSTANCE RELOAD TLS");throw failure;}
    System.out.println("TLS_RELOAD_OK_SIN_REINICIO rollback="+a[0].equals("rollback"));
   }else throw new IllegalArgumentException();
  }catch(Exception e){System.out.println("MYSQL_LAB_FALLO tipo="+e.getClass().getSimpleName());if(e instanceof SQLException q)System.out.println("SQLSTATE="+q.getSQLState()+" codigo="+q.getErrorCode());System.exit(1);}
 }
}