package cl.nexbyte.siger.custodia;
import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.robustez.*;
import cl.nexbyte.siger.multimedia.AlmacenPrivado;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
/** Respaldo INSTITUCIONAL de ingreso, fuera del servidor público. Publicación durable + restauración automática por Comunicación. */
public final class RespaldoIngresoVerificado {
 public record Paquete(Map<String,Object> registro,List<Map<String,Object>> documentos){}
 private static Map<String,Object> columns(Map<String,Object> source){var r=new TreeMap<String,Object>();source.forEach((k,v)->r.put(k.toLowerCase(Locale.ROOT),v));return r;}
 private static void directory(Path path)throws IOException{Files.createDirectory(path,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));}
 private static void copy(Path source,Path target)throws IOException{
  if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))throw new IOException("Fuente no regular");
  try(var in=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS);var out=FileChannel.open(target,Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))){
   byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){var bytes=java.nio.ByteBuffer.wrap(buffer,0,n);while(bytes.hasRemaining())out.write(bytes);}out.force(true);
  }Files.setPosixFilePermissions(target,PosixFilePermissions.fromString("r--------"));AlmacenPrivado.sincronizar(target.getParent());
 }
 private static Path original(Path root,UUID id,AdjuntosFormulario.Descriptor d)throws IOException{
  Path p=root.resolve("objects").resolve(id.toString()).resolve(d.id()+".bin");for(Path part=p;part!=null&&part.startsWith(root);part=part.getParent())if(Files.isSymbolicLink(part))throw new IOException("Enlace en respaldo");
  if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)!=d.tamanio()||!AlmacenPrivado.huella(p).equals(d.sha256()))throw new IOException("Original restaurado no íntegro");return p;
 }
 private static List<AdjuntosFormulario.Descriptor> files(Paquete snapshot,String queue,JsonMapper mapper){return queue.equals("COMPLETO")?mapper.readValue(snapshot.registro().get("body").toString(),EntregaFormulario.class).comunicacion().adjuntos():List.of();}
 private static GarantiaCustodia.Contexto verify(Paquete snapshot,Path root,UUID id,String queue,Map<String,PublicKey> pub,Map<String,PublicKey> inst,JsonMapper mapper)throws IOException{
  var c=EmisorGarantiaCustodia.verifyMetadata(snapshot.registro(),queue,pub,inst,mapper);if(!id.equals(c.id()))throw new IOException("Paquete de otra Comunicación");var descriptors=files(snapshot,queue,mapper);
  if(snapshot.documentos().size()!=descriptors.size())throw new IOException("Manifiesto de restauración incompleto");
  for(var d:descriptors){var rows=snapshot.documentos().stream().filter(r->d.id().toString().equals(r.get("attachment_id"))).toList();if(rows.size()!=1)throw new IOException("Documento duplicado o ausente");var r=rows.getFirst();
   if(!id.toString().equals(r.get("operation_id"))||!c.comunicacionId().toString().equals(r.get("communication_id"))||!"INGRESO_CIUDADANO".equals(r.get("origin"))||!d.nombre().equals(r.get("name"))||d.tamanio()!=((Number)r.get("size_bytes")).longValue()||!d.sha256().equals(r.get("sha256"))||!d.tipo().equals(r.get("media_type"))||!(id+"/"+d.id()).equals(r.get("storage_key")))throw new IOException("Metadatos documentales restaurados discrepantes");original(root,id,d);
  }return c;
 }
 private static Paquete read(Path path,JsonMapper mapper)throws IOException{
  if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>16*1024*1024)throw new IOException("Metadatos de respaldo inválidos");return mapper.readValue(Files.readString(path),Paquete.class);
 }
 private static void cleanup(Path ownTemporary)throws IOException{
  // Solo directorios nuevos creados por esta invocación, nunca origen ni paquetes publicados.
  if(ownTemporary==null||!Files.exists(ownTemporary,LinkOption.NOFOLLOW_LINKS))return;
  try(var paths=Files.walk(ownTemporary)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
 }
 public static FirmaOperativa emitir(JdbcTemplate source,Path sourceRoot,Path backupRoot,Path scratchRoot,UUID id,String queue,
  Map<String,PublicKey> publicKeys,Map<String,PublicKey> institution,String issuer,PrivateKey issuerKey,Clock clock,JsonMapper mapper)throws IOException{
  var origin=EmisorGarantiaCustodia.root(sourceRoot);var backup=EmisorGarantiaCustodia.root(backupRoot);var scratch=EmisorGarantiaCustodia.root(scratchRoot);
  for(var pair:List.of(List.of(origin,backup),List.of(origin,scratch),List.of(backup,scratch)))if(pair.get(0).startsWith(pair.get(1))||pair.get(1).startsWith(pair.get(0)))throw new IOException("Árboles de custodia, respaldo y scratch separados requeridos");
  if(Files.getFileStore(origin).equals(Files.getFileStore(backup)))throw new IOException("Respaldo en medio independiente requerido");
  var metadata=columns(EmisorGarantiaCustodia.metadata(source,id));var expected=EmisorGarantiaCustodia.verifyMetadata(metadata,queue,publicKeys,institution,mapper);if(!id.equals(expected.id()))throw new IOException("ID no corresponde");
  var documents=queue.equals("COMPLETO")?source.queryForList("SELECT * FROM institution_document WHERE operation_id=? AND origin='INGRESO_CIUDADANO' ORDER BY attachment_id",id.toString()).stream().map(RespaldoIngresoVerificado::columns).toList():List.<Map<String,Object>>of();
  var snapshot=new Paquete(metadata,documents);var destination=backup.resolve(queue.toLowerCase(Locale.ROOT)+"-"+id);Path building=null,restored=null;
  try {
   if(!Files.exists(destination,LinkOption.NOFOLLOW_LINKS)){
    building=Files.createTempDirectory(backup,".ingreso-incompleto-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));directory(building.resolve("objects"));directory(building.resolve("objects").resolve(id.toString()));
    byte[] bytes=mapper.writeValueAsBytes(snapshot);if(bytes.length>16*1024*1024)throw new IOException("Metadatos excesivos");var json=building.resolve("registro.json");
    try(var channel=FileChannel.open(json,Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))){var buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}
    for(var d:files(snapshot,queue,mapper)){var original=EmisorGarantiaCustodia.original(source,origin,id,expected.comunicacionId(),d);copy(original,building.resolve("objects").resolve(id.toString()).resolve(d.id()+".bin"));}
    if(!expected.equals(verify(read(json,mapper),building,id,queue,publicKeys,institution,mapper)))throw new IOException("Respaldo discrepante");
    AlmacenPrivado.sincronizar(building.resolve("objects").resolve(id.toString()));AlmacenPrivado.sincronizar(building.resolve("objects"));AlmacenPrivado.sincronizar(building);Files.move(building,destination,StandardCopyOption.ATOMIC_MOVE);AlmacenPrivado.sincronizar(backup);
   }
   if(Files.isSymbolicLink(destination)||!Files.isDirectory(destination,LinkOption.NOFOLLOW_LINKS))throw new IOException("Paquete inesperado");
   // Restaurar DESDE el paquete ya publicado, no desde el original ni desde bytes guardados en memoria.
   var saved=read(destination.resolve("registro.json"),mapper);if(!expected.equals(verify(saved,destination,id,queue,publicKeys,institution,mapper)))throw new IOException("No sustituir paquete histórico distinto");
   restored=Files.createTempDirectory(scratch,"restauracion-ingreso-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));directory(restored.resolve("objects"));directory(restored.resolve("objects").resolve(id.toString()));copy(destination.resolve("registro.json"),restored.resolve("registro.json"));
   for(var d:files(saved,queue,mapper))copy(original(destination,id,d),restored.resolve("objects").resolve(id.toString()).resolve(d.id()+".bin"));
   if(!expected.equals(verify(read(restored.resolve("registro.json"),mapper),restored,id,queue,publicKeys,institution,mapper))||!expected.equals(EmisorGarantiaCustodia.verifyMetadata(EmisorGarantiaCustodia.metadata(source,id),queue,publicKeys,institution,mapper)))throw new IOException("Restauración o registro cambiaron");
   return FirmaOperativa.firmar(new GarantiaCustodia.Contenido(expected,clock.instant(),clock.instant().plus(Duration.ofHours(24)),UUID.randomUUID()),GarantiaCustodia.DOMAIN,issuer,issuerKey,mapper);
  }finally{cleanup(building);cleanup(restored);}
 }
 private RespaldoIngresoVerificado(){}
}