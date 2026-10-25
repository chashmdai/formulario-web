package cl.nexbyte.siger.custodia;

import java.io.*;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.util.*;

/** Operaciones del agente, sin modificar el almacén POSIX del receptor público. */
public final class ArchivoAgente {
    public static boolean windows(){return System.getProperty("os.name").startsWith("Windows");}
    private static final Set<String> ADMINS=Set.of("BUILTIN\\Administrators","BUILTIN\\Administradores","NT AUTHORITY\\SYSTEM");
    private static UserPrincipal owner()throws IOException{return FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByName(new com.sun.security.auth.module.NTSystem().getDomain()+"\\"+System.getProperty("user.name"));}
    private static void lexical(Path p)throws IOException{
        if(!windows())return;
        String s=p.toString();
        if(!s.matches("[A-Za-z]:\\\\.*")||s.indexOf(':',2)>=0)throw new IOException("Solo rutas NTFS locales canónicas");
        for(Path part:p){String n=part.toString();if(n.equals(".")||n.equals("..")||n.endsWith(" ")||n.endsWith(".")||n.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?")||n.matches(".*[<>\"|?*].*"))throw new IOException("Ruta Windows no admitida");}
    }
    public static void ruta(Path path)throws IOException{
        Path p=path.toAbsolutePath();lexical(p);
        for(Path part=p;part!=null;part=part.getParent()){
            if(Files.exists(part,LinkOption.NOFOLLOW_LINKS)){
                if(Files.isSymbolicLink(part)||windows()&&Nativo.atributos(part,0x400))throw new IOException("Reparse point o enlace no admitido");
                if(Files.isRegularFile(part,LinkOption.NOFOLLOW_LINKS)&&windows()&&Nativo.enlaces(part)!=1)throw new IOException("Hardlink no admitido");
            }
            if(Files.exists(part.resolve(".git")))throw new IOException("Ruta privada fuera de Git requerida");
        }
    }
    public static Path raiz(Path path)throws IOException{
        ruta(path);Path p=path.toAbsolutePath().normalize();if(!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("Directorio privado requerido");
        permisos(p);return p.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }
    public static void permisos(Path p)throws IOException{
        if(windows()){
            if(!Files.getFileStore(p).type().equalsIgnoreCase("NTFS"))throw new IOException("NTFS requerido");
            var view=Files.getFileAttributeView(p,AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);if(view==null)throw new IOException("ACL requerida");
            String self=owner().getName();
            for(var acl:view.getAcl())if(acl.type()==AclEntryType.ALLOW&&!acl.principal().getName().equalsIgnoreCase(self)&&!ADMINS.contains(acl.principal().getName())&&!(Set.of("CREATOR OWNER","\\CREATOR OWNER").contains(acl.principal().getName())&&acl.flags().contains(AclEntryFlag.INHERIT_ONLY)))throw new IOException("ACL ajena al agente; no se modifica automáticamente");
            if(!Files.isReadable(p))throw new IOException("Lectura no permitida");
        }else{
            if(Files.getFileAttributeView(p,PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS)==null)throw new IOException("POSIX requerido");
            var mode=Files.getPosixFilePermissions(p,LinkOption.NOFOLLOW_LINKS);if(mode.contains(PosixFilePermission.OTHERS_WRITE)||mode.contains(PosixFilePermission.GROUP_WRITE))throw new IOException("Escritura ajena no admitida");
        }
    }
    private static FileAttribute<?> attribute(boolean directory)throws IOException{
        if(!windows())return PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(directory?"rwx------":"rw-------"));
        var entry=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner()).setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if(directory)entry.setFlags(AclEntryFlag.FILE_INHERIT,AclEntryFlag.DIRECTORY_INHERIT);
        var entries=List.of(entry.build());return new FileAttribute<List<AclEntry>>(){public String name(){return "acl:acl";}public List<AclEntry> value(){return entries;}};
    }
    public static void directorio(Path p)throws IOException{ruta(p);Files.createDirectory(p,attribute(true));permisos(p);}
    public static Path temporal(Path parent,String prefix)throws IOException{raiz(parent);return Files.createTempDirectory(parent,prefix,attribute(true));}
    public static void escribir(Path p,byte[] bytes)throws IOException{
        ruta(p);try(var out=FileChannel.open(p,Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE),attribute(false))){var b=ByteBuffer.wrap(bytes);while(b.hasRemaining())out.write(b);out.force(true);}permisos(p);
    }
    public static void copiar(Path source,Path target)throws IOException{
        ruta(source);ruta(target);permisos(source);if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))throw new IOException("Original no regular");
        try(var in=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS);var out=FileChannel.open(target,Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE),attribute(false))){byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1){var b=ByteBuffer.wrap(bytes,0,n);while(b.hasRemaining())out.write(b);}out.force(true);}
        if(windows())Files.setAttribute(target,"dos:readonly",true,LinkOption.NOFOLLOW_LINKS);else Files.setPosixFilePermissions(target,PosixFilePermissions.fromString("r--------"));
        sincronizar(target.getParent());
    }
    public static boolean sincronizar(Path directory)throws IOException{
        ruta(directory);
        if(windows()){Nativo.sincronizarDirectorio(directory);return true;}
        try(var handle=FileChannel.open(directory,StandardOpenOption.READ)){handle.force(true);return true;}
    }
    /** Acredita la secuencia de sincronización de la API; no certifica hardware frente a pérdida de energía. */
    public static boolean durabilidadAcreditada(Path directory)throws IOException{return sincronizar(directory);}
    public static boolean independiente(Path origin,Path backup)throws IOException{
        raiz(origin);raiz(backup);
        // Dos volúmenes NTFS no prueban dispositivos/medios independientes. Sin acreditación no emitir.
        return !windows()&&!Files.getFileStore(origin).equals(Files.getFileStore(backup));
    }
    public static void publicar(Path temporary,Path destination)throws IOException{
        ruta(temporary);ruta(destination);if(!Files.isDirectory(temporary,LinkOption.NOFOLLOW_LINKS))throw new IOException("Solo paquetes de directorio");if(Files.exists(destination,LinkOption.NOFOLLOW_LINKS))throw new FileAlreadyExistsException("Paquete ya publicado");
        Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE);sincronizar(destination.getParent());
    }
    /** Solo árbol temporal NUEVO de esta invocación, bajo padre aprobado. Nunca paquetes publicados/origen. */
    public static void limpiarTemporal(Path own,Path parent)throws IOException{
        if(own==null||!Files.exists(own,LinkOption.NOFOLLOW_LINKS))return;
        Path base=raiz(parent),p=own.toAbsolutePath().normalize();
        if(!base.equals(p.getParent())||!p.getFileName().toString().matches("(?:\\.ingreso-incompleto-|restauracion-ingreso-)[A-Za-z0-9-]+"))throw new IOException("Limpieza fuera de temporal propio");
        ruta(p);try(var entries=Files.walk(p)){for(var child:entries.sorted(Comparator.reverseOrder()).toList()){ruta(child);if(windows())Files.setAttribute(child,"dos:readonly",false,LinkOption.NOFOLLOW_LINKS);Files.delete(child);}}
    }
    public static int limpiarVencidos(Path parent,long cutoff)throws IOException{
        Path base=raiz(parent);int removed=0;
        try(var entries=Files.newDirectoryStream(base)){for(var p:entries){
            if(p.getFileName().toString().matches("(?:\\.ingreso-incompleto-|restauracion-ingreso-)[A-Za-z0-9-]+")&&Files.getLastModifiedTime(p,LinkOption.NOFOLLOW_LINKS).toMillis()<cutoff){limpiarTemporal(p,base);if(++removed==20)break;}
        }}return removed;
    }
    public static String huella(Path p)throws IOException{
        ruta(p);try{var hash=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)hash.update(b,0,n);}return HexFormat.of().formatHex(hash.digest());}catch(GeneralSecurityException e){throw new IllegalStateException(e);}
    }
    private static final class Nativo {
        private static final Arena ARENA=Arena.ofShared();
        private static final SymbolLookup KERNEL=SymbolLookup.libraryLookup("kernel32",ARENA);
        private static MethodHandle call(String name,FunctionDescriptor descriptor){return Linker.nativeLinker().downcallHandle(KERNEL.find(name).orElseThrow(),descriptor);}
        private static final MethodHandle ATTR=call("GetFileAttributesW",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
        private static final MethodHandle OPEN=call("CreateFileW",FunctionDescriptor.of(ValueLayout.ADDRESS,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
        private static final MethodHandle INFO=call("GetFileInformationByHandle",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.ADDRESS));
        private static final MethodHandle FLUSH=call("FlushFileBuffers",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
        private static final MethodHandle CLOSE=call("CloseHandle",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
        private static MemorySegment string(Arena arena,Path p){byte[] utf=(p.toString()+"\0").getBytes(java.nio.charset.StandardCharsets.UTF_16LE);return arena.allocateFrom(ValueLayout.JAVA_BYTE,utf);}
        static boolean atributos(Path p,int mask)throws IOException{
            try(var arena=Arena.ofConfined()){int attrs=(int)ATTR.invokeExact(string(arena,p));if(attrs==-1)throw new IOException("No se acreditan atributos Windows");return (attrs&mask)!=0;}catch(IOException e){throw e;}catch(Throwable e){throw new IOException("API Windows no disponible",e);}
        }
        static void sincronizarDirectorio(Path p)throws IOException{
            try(var arena=Arena.ofConfined()){
                var handle=(MemorySegment)OPEN.invokeExact(string(arena,p),0x40000000,7,MemorySegment.NULL,3,0x02200000,MemorySegment.NULL);
                if(handle.address()==-1L)throw new IOException("Directorio sin acceso de sincronización");
                try{var data=arena.allocate(52,4);int ok=(int)INFO.invokeExact(handle,data);int attrs=data.get(ValueLayout.JAVA_INT,0);if(ok==0||(attrs&0x400)!=0||(attrs&0x10)==0)throw new IOException("Handle de directorio no acreditado");int flushed=(int)FLUSH.invokeExact(handle);if(flushed==0)throw new IOException("Durabilidad de directorio no confirmada");}finally{int ignored=(int)CLOSE.invokeExact(handle);}
            }catch(IOException e){throw e;}catch(Throwable e){throw new IOException("Sincronización nativa no disponible",e);}
        }
        static int enlaces(Path p)throws IOException{
            try(var arena=Arena.ofConfined()){
                var handle=(MemorySegment)OPEN.invokeExact(string(arena,p),0x80000000,7,MemorySegment.NULL,3,0x02200000,MemorySegment.NULL);
                if(handle.address()==-1L)throw new IOException("No se puede abrir original sin reparse");
                try{var data=arena.allocate(52,4);int ok=(int)INFO.invokeExact(handle,data);if(ok==0||(data.get(ValueLayout.JAVA_INT,0)&0x400)!=0)throw new IOException("No se acredita original Windows");return data.get(ValueLayout.JAVA_INT,40);}finally{int ignored=(int)CLOSE.invokeExact(handle);}
            }catch(IOException e){throw e;}catch(Throwable e){throw new IOException("API Windows no disponible",e);}
        }
    }
    private ArchivoAgente(){}
}