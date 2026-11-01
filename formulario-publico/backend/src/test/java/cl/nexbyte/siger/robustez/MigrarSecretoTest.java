package cl.nexbyte.siger.robustez;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
class MigrarSecretoTest {
 @TempDir Path temp;
 @Test void secretoMontadoNoRequiereVariableEnClaro()throws Exception{
  var file=temp.resolve("password");Files.writeString(file,"synthetic-password\n");
  assertThat(MigrarDatos.secreto(null,file.toString())).isEqualTo("synthetic-password");
  assertThat(MigrarDatos.secreto("legacy",null)).isEqualTo("legacy");
  assertThatThrownBy(()->MigrarDatos.secreto("another",file.toString())).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void rechazaAusenciaYTamanosInvalidos()throws Exception{
  var file=temp.resolve("password");Files.writeString(file,"a\nb");
  assertThatThrownBy(()->MigrarDatos.secreto(null,file.toString())).isInstanceOf(IllegalArgumentException.class);
  Files.writeString(file,"x".repeat(4097));
  assertThatThrownBy(()->MigrarDatos.secreto(null,file.toString())).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->MigrarDatos.secreto(null,null)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->MigrarDatos.secreto(null,temp.resolve("missing").toString())).isInstanceOf(IllegalArgumentException.class);
 }
}
