package cl.nexbyte.siger.custodia;
import java.nio.file.*;import java.sql.*;import java.util.*;import tools.jackson.databind.json.JsonMapper;
public final class PruebaTlsConector {
 static int passed;
 static void ok(String url,String user,String pass)throws Exception{try(var c=DriverManager.getConnection(url,user,pass);var s=c.createStatement();var r=s.executeQuery("SHOW SESSION STATUS LIKE 'Ssl_cipher'")){if(!r.next()||r.getString(2).isBlank())throw new AssertionError();passed++;}}
 static void reject(String url,String user,String pass,boolean tls)throws Exception{try(var c=DriverManager.getConnection(url,user,pass)){throw new AssertionError("Conexión inválida aceptada");}catch(SQLException e){boolean isTls=false;for(Throwable t=e;t!=null;t=t.getCause())if(t instanceof javax.net.ssl.SSLException||t instanceof java.security.cert.CertificateException)isTls=true;if(tls!=isTls||!tls&&!"28000".equals(e.getSQLState()))throw new AssertionError("Fallo inesperado",e);passed++;}}
 public static void main(String[] args)throws Exception{
  byte[] data=System.in.readNBytes(16385);if(data.length>16384)throw new IllegalArgumentException();var n=JsonMapper.builder().build().readTree(data);Arrays.fill(data,(byte)0);String user=n.get("usuario").asString(),pass=n.get("password").asString();
  String url=args[1];ok(url,user,pass);ok(url.replace("localhost:","127.0.0.1:"),user,pass);
  reject(url.replace("localhost:","nombre-incorrecto.invalid:"),user,pass,true);
  reject(url.replace("mysql-lab-ca-20261025.jks","mysql-bootstrap-publico.jks"),user,pass,true);
  reject(url,user,UUID.randomUUID().toString(),false);
  System.out.println("CONNECTOR_TLS_NATIVE_OK verificaciones="+passed+" localhost=true ip=true identidadIncorrectaRechazada=true caIncorrectaRechazada=true credencialIncorrectaRechazada=true");
 }
}
