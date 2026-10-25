package cl.nexbyte.siger.custodia;
import java.util.List;
/** Prueba pura de la herramienta administrativa: no abre sockets, no lee credenciales ni ejecuta SQL. */
public final class PruebaConfiguracionTlsLab {
 public static void main(String[] args){
  String suffix="?sslMode=VERIFY_IDENTITY&fallbackToSystemTrustStore=false&connectTimeout=3000&socketTimeout=5000";
  int passed=0;
  for(String prefix:List.of("jdbc:mysql://localhost:3306/","jdbc:mysql://127.0.0.1:3306/siger_custodia_lab","jdbc:mysql://MySQL_Server_8.0.46_Auto_Generated_Server_Certificate:3306/")){
   String url=prefix+suffix;if(!MysqlTlsLab.validarUrl(url).equals(url))throw new AssertionError("Se alteró hostname o URL");passed++;
  }
  String valid="jdbc:mysql://localhost:3306/siger_custodia_lab"+suffix;
  for(String invalid:List.of(
   valid+"&sslMode=DISABLED",
   valid+"&%73slMode=REQUIRED",
   valid+"&fallbackToSystemTrustStore=true",
   valid+"&useSSL=false",
   valid+"&verifyServerCertificate=false",
   valid+"&allowPublicKeyRetrieval=true",
   valid+"&password=no-permitida",
   valid.replace("VERIFY_IDENTITY","VERIFY_CA"),
   valid.replace("&fallbackToSystemTrustStore=false",""),
   valid.replace("fallbackToSystemTrustStore=false","fallbackToSystemTrustStore=true"),
   valid.replace("localhost:3306","externo.invalid:3306"),
   valid.replace("localhost:3306","localhost:3307"),
   valid.replace("siger_custodia_lab","siger_institucional_windows_lab"),
   valid.replace("siger_custodia_lab","mysql"),
   valid.replace("/siger_custodia_lab?","/%73iger_custodia_lab?"),
   valid.replace("jdbc:mysql://localhost","jdbc:mysql://usuario@localhost"),
   valid+"#fragmento",
   valid+"&sslMode"
  )){
   boolean denied=false;try{MysqlTlsLab.validarUrl(invalid);}catch(IllegalArgumentException expected){denied=true;}
   if(!denied)throw new AssertionError("URL insegura aceptada");passed++;
  }
  System.out.println("QA_TLS_CONFIGURACION_ESTRICTA_OK verificaciones="+passed+" sockets=0 secretos=0 sql=0");
 }
}
