package cl.nexbyte.siger.robustez;
public final class MigrarDatos{
 public static void ejecutar(){
  var url=System.getenv("MIGRATION_DB_URL");validarDestino(url,"true".equals(System.getenv("LOCAL_DB_ALLOWED")));
  try(var db=new BaseJdbc(url,required("MIGRATION_DB_USER"),secreto(System.getenv("MIGRATION_DB_PASSWORD"),System.getenv("MIGRATION_DB_PASSWORD_FILE")),2)){db.migrate("publico");}
 }
 static String secreto(String value,String file){
  if(file==null||file.isBlank()){if(value==null||value.isBlank())throw new IllegalArgumentException("Falta configuración de migración");return value;}
  if(value!=null&&!value.isBlank())throw new IllegalArgumentException("Configurar solo una fuente del secreto");
  try{
   var path=java.nio.file.Path.of(file);
   if(java.nio.file.Files.isSymbolicLink(path)||!java.nio.file.Files.isRegularFile(path)||java.nio.file.Files.size(path)>4096)
    throw new IllegalArgumentException("Archivo de secreto inválido");
   var secret=java.nio.file.Files.readString(path).strip();
   if(secret.isBlank()||secret.indexOf('\n')>=0||secret.indexOf('\r')>=0)throw new IllegalArgumentException("Archivo de secreto inválido");
   return secret;
  }catch(java.io.IOException error){throw new IllegalArgumentException("Secreto de migración no disponible");}
 }
 static void validarDestino(String url,boolean local){ConfiguracionDatos.validar(url,local);}
 private static String required(String name){var value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalArgumentException("Falta configuración de migración");return value;}
}
