package cl.nexbyte.siger.custodia;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.LINUX)
class ClavePrivadaAgenteTest {
 @TempDir Path temp;
 KeyPair key;
 Path privateFile;
 @BeforeEach void fixture()throws Exception{
  key=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
  privateFile=temp.resolve("operativa-qa.key");
  String pem="-----BEGIN PRIVATE KEY-----\n"+Base64.getEncoder().encodeToString(key.getPrivate().getEncoded())+"\n-----END PRIVATE KEY-----\n";
  Files.writeString(privateFile,pem,StandardCharsets.UTF_8);
  Files.setPosixFilePermissions(privateFile,PosixFilePermissions.fromString("rw-------"));
 }
 @Test void clave0600EsValidaYConservaSusBytes()throws Exception{
  var before=Files.readAllBytes(privateFile);
  assertArrayEquals(key.getPrivate().getEncoded(),EmisorGarantiaCustodia.privateKey(privateFile).getEncoded());
  assertArrayEquals(before,Files.readAllBytes(privateFile));
 }
 @Test void clave0400EsValida()throws Exception{
  Files.setPosixFilePermissions(privateFile,PosixFilePermissions.fromString("r--------"));
  assertArrayEquals(key.getPrivate().getEncoded(),EmisorGarantiaCustodia.privateKey(privateFile).getEncoded());
 }
 @Test void lecturaAjena0644OGrupoSeRechaza()throws Exception{
  for(String permissions:new String[]{"rw-r--r--","rw-r-----","rw----r--"}){
   Files.setPosixFilePermissions(privateFile,PosixFilePermissions.fromString(permissions));
   assertThrows(IOException.class,()->EmisorGarantiaCustodia.privateKey(privateFile));
  }
 }
 @Test void enlaceSimbolicoNoSeSigue()throws Exception{
  var link=temp.resolve("enlace.key");Files.createSymbolicLink(link,privateFile);
  assertThrows(IOException.class,()->EmisorGarantiaCustodia.privateKey(link));
  assertArrayEquals(key.getPrivate().getEncoded(),EmisorGarantiaCustodia.privateKey(privateFile).getEncoded());
 }
 @Test void directorioVacioYClaveExcesivaSeRechazan()throws Exception{
  assertThrows(IOException.class,()->EmisorGarantiaCustodia.privateKey(temp));
  Files.write(privateFile,new byte[0]);
  assertThrows(IOException.class,()->EmisorGarantiaCustodia.privateKey(privateFile));
  Files.write(privateFile,new byte[16385]);
  assertThrows(IOException.class,()->EmisorGarantiaCustodia.privateKey(privateFile));
 }
 @Test void sinLecturaDelPropietarioSeRechazaAntesDelParser()throws Exception{
  Files.setPosixFilePermissions(privateFile,PosixFilePermissions.fromString("-w-------"));
  assertThrows(IOException.class,()->EmisorGarantiaCustodia.privateKey(privateFile));
 }
}
