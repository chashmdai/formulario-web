package cl.nexbyte.siger.multimedia;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.util.*;
import java.util.function.LongSupplier;
/** UUID como claves. Originales durables e inmutables; ningún nombre ciudadano forma una ruta. */
public final class AlmacenPrivado implements AutoCloseable{
 public interface Guardia extends AutoCloseable{void close()throws IOException;}
 private final Path root;private final LongSupplier space;private FileChannel backupChannel;private FileLock backupLock;private int writers;private FileChannel instanceChannel;private FileLock instanceLock;
 private static final class Candado {final java.util.concurrent.locks.ReentrantReadWriteLock lock=new java.util.concurrent.locks.ReentrantReadWriteLock(true);int users;}
 private final java.util.concurrent.ConcurrentHashMap<UUID,Candado> journeyLocks=new java.util.concurrent.ConcurrentHashMap<>();
 private Candado registrar(UUID id){return journeyLocks.compute(id,(k,v)->{if(v==null)v=new Candado();v.users++;return v;});}
 private void soltar(UUID id,Candado e){journeyLocks.compute(id,(k,v)->{if(v!=e||e.users<1)throw new IllegalStateException("Guardia inconsistente");return --e.users==0?null:e;});}
 public Guardia lectura(UUID journey){var e=registrar(journey);e.lock.readLock().lock();return ()->{e.lock.readLock().unlock();soltar(journey,e);};}
 public Guardia mantenimiento(UUID journey){var e=registrar(journey);if(!e.lock.writeLock().tryLock()){soltar(journey,e);return null;}return ()->{e.lock.writeLock().unlock();soltar(journey,e);};}
 public InputStream abrir(UUID journey,Path file)throws IOException{
  var guard=lectura(journey);
  try {var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS);return new FilterInputStream(in){private boolean closed;public void close()throws IOException{if(!closed){closed=true;try{super.close();}finally{guard.close();}}}};}
  catch(IOException|RuntimeException fail){guard.close();throw fail;}
 }
 private final Map<Path,String> verified=new java.util.concurrent.ConcurrentHashMap<>();
 public AlmacenPrivado(Path root)throws IOException{this(root,null);}
 public AlmacenPrivado(Path root,LongSupplier free)throws IOException{
  this.root=root.toAbsolutePath().normalize();
  for(Path p=this.root;p!=null;p=p.getParent())if(Files.isSymbolicLink(p)||Files.exists(p.resolve(".git")))throw new IOException("Almacén fuera de Git y sin enlaces requerido");
  if(Files.exists(this.root)){try(var entries=Files.newDirectoryStream(this.root)){for(var entry:entries)if(Files.isSymbolicLink(entry)||!Set.of("objects","chunks","incoming","derived","instance.lock","backup.lock").contains(entry.getFileName().toString()))throw new IOException("Directorio ajeno al almacén");}}
  Files.createDirectories(this.root);if(Files.getFileAttributeView(this.root,PosixFileAttributeView.class)==null)throw new IOException("Almacén POSIX requerido");
  for(var name:List.of("","objects","chunks","incoming","derived")){var path=name.isEmpty()?this.root:this.root.resolve(name);Files.createDirectories(path);Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rwx------"));}
  instanceChannel=FileChannel.open(this.root.resolve("instance.lock"),StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE);try{instanceLock=instanceChannel.tryLock();if(instanceLock==null)throw new IOException("Almacén ya abierto");}catch(Exception e){instanceChannel.close();throw new IOException("Un solo escritor por almacén",e);}
  sincronizar(this.root);sincronizar(this.root.getParent());
  this.space=free==null?()->{try{return Files.getFileStore(this.root).getUsableSpace();}catch(IOException e){return 0;}}:free;
  try(var guard=guardia()){} // comprueba bloqueo compartido real del filesystem
 }
 public Path raiz(){return root;}
 public long espacio(){return space.getAsLong();}
 public synchronized Guardia guardia()throws IOException{
  if(writers==0){backupChannel=FileChannel.open(root.resolve("backup.lock"),StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE);try{backupLock=backupChannel.tryLock(0,Long.MAX_VALUE,true);if(backupLock==null)throw new IOException("Respaldo en curso");}catch(Exception e){backupChannel.close();throw new IOException("Almacén ocupado",e);}}
  writers++;return ()->{synchronized(this){if(--writers==0){backupLock.release();backupChannel.close();}}};
 }
 private final Set<Path> activeTemps=java.util.concurrent.ConcurrentHashMap.newKeySet();
 public synchronized Path temporal()throws IOException{var p=Files.createTempFile(root.resolve("incoming"),"carga-",".part");activeTemps.add(p);return p;}
 public synchronized void liberarTemporal(Path p)throws IOException{try{Files.deleteIfExists(p);}finally{activeTemps.remove(p);}}
 public record Temporales(int archivos,long bytes){}
 public synchronized Temporales limpiarIncoming(long before,int limit)throws IOException{
  if(limit<1||limit>100)throw new IllegalArgumentException("Límite de temporales inválido");int count=0;long bytes=0;
  try(var entries=Files.newDirectoryStream(root.resolve("incoming"))){for(var p:entries){if(activeTemps.contains(p)||!Files.exists(p,LinkOption.NOFOLLOW_LINKS))continue;
   if(!p.getFileName().toString().matches("carga-[A-Za-z0-9_-]+\\.part")||!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("Temporal desconocido; conservar");
   if(Files.getLastModifiedTime(p,LinkOption.NOFOLLOW_LINKS).toMillis()<before){long size=Files.size(p);Files.delete(p);bytes+=size;if(++count==limit)break;}
  }}if(count>0)sincronizar(root.resolve("incoming"));return new Temporales(count,bytes);
 }
 private Path object(String folder,UUID id)throws IOException{Path p=root.resolve(folder).resolve(id.toString());if(Files.isSymbolicLink(p))throw new IOException("Objeto inválido");return p;}
 public Path original(UUID id)throws IOException{return object("objects",id);}
 public Path derivada(UUID id)throws IOException{return object("derived",id);}
 public Path bloque(UUID id,int index)throws IOException{if(index<0||index>1024)throw new IOException("Bloque inválido");var dir=object("chunks",id);boolean fresh=!Files.exists(dir,LinkOption.NOFOLLOW_LINKS);Files.createDirectories(dir);if(fresh){Files.setPosixFilePermissions(dir,PosixFilePermissions.fromString("rwx------"));sincronizar(dir.getParent());}if(Files.isSymbolicLink(dir))throw new IOException("Directorio inválido");return dir.resolve(Integer.toString(index));}
 public static String huella(Path path)throws IOException{
  try{var digest=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}return HexFormat.of().formatHex(digest.digest());}catch(GeneralSecurityException e){throw new IllegalStateException(e);}
 }
 public String recibir(InputStream input,long size,Path target)throws IOException{
  if(size<=0)throw new IOException("Tamaño inválido");try{var digest=MessageDigest.getInstance("SHA-256");
   try(var out=FileChannel.open(target,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){byte[] bytes=new byte[65536];long remaining=size;while(remaining>0){int n=input.read(bytes,0,(int)Math.min(bytes.length,remaining));if(n<0)throw new IOException("Carga incompleta");digest.update(bytes,0,n);var b=ByteBuffer.wrap(bytes,0,n);while(b.hasRemaining())out.write(b);remaining-=n;}if(input.read()!=-1)throw new IOException("Carga excedida");out.force(true);}return HexFormat.of().formatHex(digest.digest());
  }catch(GeneralSecurityException e){throw new IllegalStateException(e);}
 }
 public void publicar(Path source,Path target)throws IOException{
  if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)){if(!huella(source).equals(huella(target)))throw new IOException("Objeto distinto");Files.delete(source);activeTemps.remove(source);return;}
  Files.move(source,target,StandardCopyOption.ATOMIC_MOVE);Files.setPosixFilePermissions(target,PosixFilePermissions.fromString("r--------"));sincronizar(target.getParent());activeTemps.remove(source);
 }
 public Path ensamblar(UUID id,long size,long chunk)throws IOException{
  var target=original(id);if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))return target;
  var temp=temporal();boolean done=false;
  try{try(var out=FileChannel.open(temp,StandardOpenOption.WRITE)){long copied=0;for(int i=0;copied<size;i++){var p=bloque(id,i);long expected=Math.min(chunk,size-copied);if(Files.isSymbolicLink(p)||Files.size(p)!=expected)throw new IOException("Bloque incompleto");try(var in=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){var data=ByteBuffer.wrap(b,0,n);while(data.hasRemaining())out.write(data);copied+=n;}}}out.force(true);}publicar(temp,target);done=true;return target;}finally{if(!done)liberarTemporal(temp);}
 }
 public void verificar(Path path,long size,String hash)throws IOException{
  if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)!=size)throw new IOException("Integridad inválida");
  var attrs=Files.readAttributes(path,"unix:ino,ctime,size,lastModifiedTime",LinkOption.NOFOLLOW_LINKS);String stamp=attrs.toString()+hash;
  if(stamp.equals(verified.get(path)))return;if(!huella(path).equals(hash))throw new IOException("Integridad inválida");if(verified.size()>128)verified.clear();verified.put(path,stamp);
 }
 public void borrarBloques(UUID id)throws IOException{
  var dir=object("chunks",id);if(!Files.exists(dir,LinkOption.NOFOLLOW_LINKS))return;
  try(var files=Files.newDirectoryStream(dir)){for(var file:files){if(!file.getFileName().toString().matches("[0-9]{1,4}")||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Temporal inesperado");Files.delete(file);}}Files.delete(dir);sincronizar(dir.getParent());
 }
 public void borrarNoRecibido(UUID id)throws IOException{borrarBloques(id);Files.deleteIfExists(original(id));Files.deleteIfExists(derivada(id));sincronizar(root.resolve("objects"));sincronizar(root.resolve("derived"));verified.clear();}
 public synchronized void close()throws IOException{if(writers>0)throw new IOException("Almacén todavía en uso");if(instanceLock!=null){instanceLock.release();instanceChannel.close();instanceLock=null;}}

 public static void sincronizar(Path directory)throws IOException{try(var channel=FileChannel.open(directory,StandardOpenOption.READ)){channel.force(true);}}
}
