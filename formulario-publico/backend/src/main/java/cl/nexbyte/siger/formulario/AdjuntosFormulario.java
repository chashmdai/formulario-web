package cl.nexbyte.siger.formulario;

import cl.nexbyte.siger.robustez.*;
import java.io.*;
import java.nio.charset.*;
import java.security.*;
import java.text.Normalizer;
import java.util.*;
import java.util.zip.ZipInputStream;

/** Contenido privado transaccional en la misma base; ninguna URL pública ni nombre de disco ciudadano. */
public final class AdjuntosFormulario {
    public static final int MAX_FILE=10*1024*1024,MAX_TOTAL=25*1024*1024;
    public interface Escaner { boolean limpio(byte[] contenido) throws IOException; }
    public record Archivo(String nombre,byte[] contenido) {
        @Override public String toString(){return "Archivo[protegido]";}
    }
    public record Descriptor(UUID id,String nombre,long tamanio,String sha256,String tipo,String estado) {
        @Override public String toString(){return "Adjunto[protegido]";}
    }
    private record Validado(Descriptor descriptor,byte[] bytes){}
    private final cl.nexbyte.siger.multimedia.AlmacenPrivado store;
    private final BaseJdbc db;private final RecorridosJdbc recorridos;private final Escaner scanner;
    public AdjuntosFormulario(BaseJdbc db,RecorridosJdbc recorridos,Escaner scanner){this(db,recorridos,scanner,null);}
    public AdjuntosFormulario(BaseJdbc db,RecorridosJdbc recorridos,Escaner scanner,cl.nexbyte.siger.multimedia.AlmacenPrivado store){this.db=db;this.recorridos=recorridos;this.scanner=scanner;this.store=store;}
    public List<Descriptor> reemplazar(UUID journey,String session,List<Archivo> archivos) {
        db.transaction(()->{recorridos.bloquearBorrador(journey,session);return null;});
        if(archivos==null||archivos.size()>5)throw new IllegalArgumentException("Límite de adjuntos");
        long size=0;for(var a:archivos){if(a==null||a.contenido()==null||a.contenido().length==0||a.contenido().length>MAX_FILE)throw new IllegalArgumentException("Tamaño de adjunto inválido");size+=a.contenido().length;}
        if(size>MAX_TOTAL)throw new IllegalArgumentException("Límite total de adjuntos");
        var result=new ArrayList<Validado>();
        // Validar todo el lote antes de reemplazar: un archivo inválido conserva el lote anterior íntegro.
        for(var a:archivos){
            var descriptor=validar(a);
            try{if(!scanner.limpio(a.contenido()))throw new IllegalArgumentException("Archivo rechazado por inspección");}
            catch(IOException unavailable){throw new FalloTecnico(503);}
            result.add(new Validado(descriptor,a.contenido()));
        }
        return db.transaction(()->{
            recorridos.bloquearBorrador(journey,session);
            db.sql.update("DELETE FROM citizen_attachment WHERE journey_id=?",journey.toString());
            for(var v:result){var d=v.descriptor();db.sql.update("INSERT INTO citizen_attachment(id,journey_id,name,size_bytes,sha256,media_type,content) VALUES(?,?,?,?,?,?,?)",d.id().toString(),journey.toString(),d.nombre(),d.tamanio(),d.sha256(),d.tipo(),v.bytes());}
            return result.stream().map(Validado::descriptor).toList();
        });
    }
    public List<Descriptor> listar(UUID journey,String session){recorridos.consultar(journey,session);return internos(journey);}
    List<Descriptor> internos(UUID journey){
        return db.sql.query("SELECT id,name,size_bytes,sha256,media_type FROM citizen_attachment WHERE journey_id=? ORDER BY id",(rs,n)->new Descriptor(UUID.fromString(rs.getString(1)),rs.getString(2),rs.getLong(3),rs.getString(4),rs.getString(5),"VALIDADO"),journey.toString());
    }
    public Archivo descargar(UUID journey,UUID attachment,String session){recorridos.consultar(journey,session);return contenido(journey,attachment);}
    Archivo contenido(UUID journey,UUID attachment){
        var rows=db.sql.query("SELECT name,size_bytes,sha256,content,storage_kind,bytes_retired FROM citizen_attachment WHERE journey_id=? AND id=?",(rs,n)->{
            if(rs.getBoolean(6))throw new FalloTecnico(410);
            if("FILE".equals(rs.getString(5)))throw new FalloTecnico(409);
            byte[] bytes=rs.getBytes(4);if(bytes.length!=rs.getLong(2)||!hash(bytes).equals(rs.getString(3)))throw new FalloTecnico(503);return new Archivo(rs.getString(1),bytes);
        },journey.toString(),attachment.toString());
        if(rows.isEmpty())throw new FalloTecnico(404);return rows.getFirst();
    }
    public boolean multimediaDisponible(){return store!=null;}
    public List<Descriptor> prepararRecepcion(UUID journey,String session){
        var files=listar(journey,session);for(var d:files){var kinds=db.sql.queryForMap("SELECT storage_kind,storage_key FROM citizen_attachment WHERE id=? AND journey_id=?",d.id().toString(),journey.toString());
            if("FILE".equals(kinds.get("storage_kind"))){try{if(store==null)throw new java.io.IOException("Almacén no configurado");store.verificar(store.original(UUID.fromString(kinds.get("storage_key").toString())),d.tamanio(),d.sha256());}catch(java.io.IOException e){throw new FalloTecnico(503);}}else contenido(journey,d.id());}
        return files;
    }
    public boolean multimedia(List<Descriptor> files){return files.stream().anyMatch(d->"FILE".equals(db.sql.queryForObject("SELECT storage_kind FROM citizen_attachment WHERE id=?",String.class,d.id().toString())));}
    public cl.nexbyte.siger.multimedia.RecursoPrivado recurso(UUID journey,UUID attachment,String session){recorridos.consultar(journey,session);var result=recursoInterno(journey,attachment);db.sql.update("INSERT INTO media_access(id,attachment_id,at_ms,principal_kind,principal_ref,representation,event_code) VALUES(?,?,?,'CIUDADANIA',?,'ORIGINAL','LECTURA_SOLICITADA')",UUID.randomUUID().toString(),attachment.toString(),System.currentTimeMillis(),cl.nexbyte.siger.multimedia.AuditoriaMedia.referencia(session));return result;}
    public cl.nexbyte.siger.multimedia.RecursoPrivado recursoInterno(UUID journey,UUID attachment){
        var rows=db.sql.queryForList("SELECT name,size_bytes,sha256,media_type,storage_kind,storage_key,bytes_retired FROM citizen_attachment WHERE journey_id=? AND id=?",journey.toString(),attachment.toString());if(rows.isEmpty())throw new FalloTecnico(404);var r=rows.getFirst();
        if(Boolean.TRUE.equals(r.get("bytes_retired")))throw new FalloTecnico(410);
        String name=r.get("name").toString(),hash=r.get("sha256").toString(),type=r.get("media_type").toString();long size=((Number)r.get("size_bytes")).longValue();
        if("FILE".equals(r.get("storage_kind"))){try{if(store==null)throw new java.io.IOException();var path=store.original(UUID.fromString(r.get("storage_key").toString()));store.verificar(path,size,hash);return new cl.nexbyte.siger.multimedia.RecursoPrivado(name,size,hash,type,()->java.nio.file.Files.newInputStream(path,java.nio.file.LinkOption.NOFOLLOW_LINKS));}catch(java.io.IOException e){throw new FalloTecnico(503);}}
        var legacy=contenido(journey,attachment);return new cl.nexbyte.siger.multimedia.RecursoPrivado(name,size,hash,type,()->new ByteArrayInputStream(legacy.contenido()));
    }
    private Descriptor validar(Archivo file){
        var name=file.nombre();if(name==null)throw new IllegalArgumentException("Nombre inválido");name=Normalizer.normalize(name,Normalizer.Form.NFC);
        if(name.length()>180||name.isBlank()||name.startsWith(".")||name.endsWith(".")||!name.equals(name.strip())||name.codePoints().anyMatch(c->Character.isISOControl(c)||c=='/'||c=='\\'||c==':'))throw new IllegalArgumentException("Nombre inválido");
        var bytes=file.contenido();var ext=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);String type;
        switch(ext) {
            case "pdf"->{if(!starts(bytes,new byte[]{37,80,68,70,45})||!new String(Arrays.copyOfRange(bytes,Math.max(0,bytes.length-1024),bytes.length),StandardCharsets.ISO_8859_1).contains("%%EOF"))invalid();type="application/pdf";}
            case "png"->{if(!starts(bytes,new byte[]{(byte)137,80,78,71,13,10,26,10}))invalid();type="image/png";}
            case "jpg","jpeg"->{if(bytes.length<4||bytes[0]!=(byte)255||bytes[1]!=(byte)216||bytes[2]!=(byte)255||bytes[bytes.length-2]!=(byte)255||bytes[bytes.length-1]!=(byte)217)invalid();type="image/jpeg";}
            case "webp"->{if(bytes.length<12||!new String(bytes,0,4,StandardCharsets.US_ASCII).equals("RIFF")||!new String(bytes,8,4,StandardCharsets.US_ASCII).equals("WEBP"))invalid();type="image/webp";}
            case "txt"->{try{var text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes));if(text.codePoints().anyMatch(cp->Character.isISOControl(cp)&&cp!=9&&cp!=10&&cp!=13))invalid();}catch(CharacterCodingException e){invalid();}type="text/plain";}
            case "doc"->{if(!starts(bytes,new byte[]{(byte)208,(byte)207,17,(byte)224,(byte)161,(byte)177,26,(byte)225}))invalid();type="application/msword";}
            case "docx"->{zip(bytes);type="application/vnd.openxmlformats-officedocument.wordprocessingml.document";}
            default->throw new IllegalArgumentException("Extensión no admitida");
        }
        return new Descriptor(UUID.randomUUID(),name,bytes.length,hash(bytes),type,"VALIDADO");
    }
    private static void zip(byte[] bytes){
        boolean types=false,document=false;long total=0;int count=0;var names=new HashSet<String>();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){
            java.util.zip.ZipEntry entry;byte[] buffer=new byte[8192];
            while((entry=zip.getNextEntry())!=null){
                String name=entry.getName();if(++count>2000||!names.add(name)||name.startsWith("/")||name.contains("..")||name.contains("\\")||name.toLowerCase(Locale.ROOT).contains("vbaproject"))invalid();
                types|=name.equals("[Content_Types].xml");document|=name.equals("word/document.xml");
                int read;while((read=zip.read(buffer))!=-1){total+=read;if(total>50L*1024*1024)invalid();}
            }
            if(!types||!document)invalid();
        }catch(IOException malformed){invalid();}
    }
    private static boolean starts(byte[] value,byte[] prefix){if(value.length<prefix.length)return false;for(int i=0;i<prefix.length;i++)if(value[i]!=prefix[i])return false;return true;}
    static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(GeneralSecurityException e){throw new IllegalStateException("Integridad no disponible");}}
    private static void invalid(){throw new IllegalArgumentException("Contenido de archivo inválido");}
}
