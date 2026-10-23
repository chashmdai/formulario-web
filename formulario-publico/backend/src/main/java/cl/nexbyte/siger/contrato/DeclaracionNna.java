package cl.nexbyte.siger.contrato;
import java.util.*;
/** Datos provisionales declarados. Ninguna comprobación aquí autentica una identidad. */
public record DeclaracionNna(String primerNombre,String segundoNombre,String primerApellido,String segundoApellido,
 String runNumero,String runDv,String direccion,String comuna,String region){
 public DeclaracionNna{
  texto(primerNombre,100,true);texto(segundoNombre,100,false);texto(primerApellido,100,true);texto(segundoApellido,100,false);
  texto(direccion,300,false);texto(comuna,100,false);texto(region,100,true);
  if((runNumero==null)!=(runDv==null))throw new IllegalArgumentException("RUN incompleto");
  if(runNumero!=null){
   if(!runNumero.matches("[1-9][0-9]{0,7}")||!runDv.matches("[0-9Kk]"))throw new IllegalArgumentException("Formato RUN inválido");
   int sum=0,m=2;for(int i=runNumero.length()-1;i>=0;i--){sum+=(runNumero.charAt(i)-'0')*m;m=m==7?2:m+1;}
   int value=11-sum%11;String expected=value==11?"0":value==10?"K":Integer.toString(value);
   if(!expected.equalsIgnoreCase(runDv))throw new IllegalArgumentException("DV inválido");
  }
 }
 public static void texto(String s,int max,boolean required){
  if(s==null){if(required)throw new IllegalArgumentException("Campo obligatorio");return;}
  if(s.isBlank()||s.length()>max)throw new IllegalArgumentException("Campo inválido");
  for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isHighSurrogate(c)){if(i+1>=s.length()||!Character.isLowSurrogate(s.charAt(++i)))throw new IllegalArgumentException("Texto inválido");}else if(Character.isLowSurrogate(c)||Character.isISOControl(c))throw new IllegalArgumentException("Texto inválido");}
 }
 public static String relato(String s){
  if(s==null||s.isBlank()||s.length()>2000)throw new IllegalArgumentException("Relato inválido");
  texto(s.replace("\r", " ").replace("\n"," ").replace("\t"," "),2000,true);return s;
 }
 public Set<String> campos(){var fields=new HashSet<>(Set.of("primerNombre","primerApellido","region","mensaje"));
  if(segundoNombre!=null)fields.add("segundoNombre");if(segundoApellido!=null)fields.add("segundoApellido");
  if(runNumero!=null){fields.add("runNumero");fields.add("runDv");}if(direccion!=null)fields.add("direccion");if(comuna!=null)fields.add("comuna");return Set.copyOf(fields);
 }
 @Override public String toString(){return "DeclaracionNna[datos protegidos]";}
}
