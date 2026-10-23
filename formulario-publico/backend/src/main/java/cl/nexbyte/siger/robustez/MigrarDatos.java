package cl.nexbyte.siger.robustez;
public final class MigrarDatos{
 public static void ejecutar(){
  var url=System.getenv("MIGRATION_DB_URL");validarDestino(url,"true".equals(System.getenv("LOCAL_DB_ALLOWED")));
  try(var db=new BaseJdbc(url,required("MIGRATION_DB_USER"),required("MIGRATION_DB_PASSWORD"),2)){db.migrate("publico");}
 }
 static void validarDestino(String url,boolean local){ConfiguracionDatos.validar(url,local);}
 private static String required(String name){var value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalArgumentException("Falta configuración de migración");return value;}
}
