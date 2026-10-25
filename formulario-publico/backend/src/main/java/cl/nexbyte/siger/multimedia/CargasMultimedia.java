package cl.nexbyte.siger.multimedia;
import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.robustez.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import tools.jackson.databind.json.JsonMapper;
/** Cargas privadas reanudables; no publica metadatos hasta completar hash, antivirus e inspección. */
public final class CargasMultimedia implements AutoCloseable{
 public interface Antivirus{boolean limpio(Path file)throws IOException;}
 public interface Inspector{Inspeccion examinar(Path file,PoliticaMultimedia.Formato format)throws IOException;}
 public record Inspeccion(String tipo,String vista,byte[] png){}
 public record Inicio(String nombre,long tamanio,String sha256){}
 public record Estado(UUID id,String nombre,long tamanio,String sha256,long recibidos,long bloque,String estado,String error,List<Integer> bloquesFaltantes,AdjuntosFormulario.Descriptor archivo){}
 public record Manifest(List<UUID> ids,String base){}
 public record Lote(String revision,List<AdjuntosFormulario.Descriptor> archivos){}
 private final BaseJdbc db;private final RecorridosJdbc journeys;private final AlmacenPrivado store;private final PoliticaMultimedia policy;private final Antivirus scanner;private final Inspector inspector;private final Clock clock;private final JsonMapper mapper;
 private final Set<UUID> running=ConcurrentHashMap.newKeySet();
 private final Semaphore analyzing=new Semaphore(1);private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"multimedia-inspeccion");t.setDaemon(true);return t;});
 public CargasMultimedia(BaseJdbc db,RecorridosJdbc journeys,AlmacenPrivado store,PoliticaMultimedia policy,Antivirus scanner,Inspector inspector,Clock clock,JsonMapper mapper){this.db=db;this.journeys=journeys;this.store=store;this.policy=policy;this.scanner=scanner;this.inspector=inspector;this.clock=clock;this.mapper=mapper;}
 public PoliticaMultimedia politica(){return policy;}
 private static UUID uuid(Object v){return v==null?null:UUID.fromString(v.toString());}
 private static long number(Map<String,Object> r,String key){return ((Number)r.get(key)).longValue();}
 private Map<String,Object> row(UUID journey,UUID id,String session,boolean lock){journeys.consultar(journey,session);var rows=db.sql.queryForList("SELECT * FROM media_upload WHERE id=? AND journey_id=?"+(lock?" FOR UPDATE":""),id.toString(),journey.toString());if(rows.isEmpty())throw new FalloTecnico(404);return rows.getFirst();}
 private <T>T guard(UUID journey,java.util.function.Supplier<T> work){try(var route=store.lectura(journey);var lock=store.guardia()){return work.get();}catch(IOException e){throw new FalloTecnico(503);}}
 private static void hash(String value){if(value==null||!value.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("SHA-256 requerido");}
 public Estado iniciar(UUID journey,String session,Inicio request){
  if(request==null)throw new IllegalArgumentException("Archivo requerido");var name=policy.nombre(request.nombre());policy.tamanio(name,request.tamanio());hash(request.sha256());
  return guard(journey,()->db.transaction(()->{
   journeys.bloquearBorrador(journey,session);var cap=db.sql.queryForMap("SELECT reserved_bytes FROM media_capacity WHERE id=1 FOR UPDATE");
   var same=db.sql.queryForList("SELECT id FROM media_upload WHERE journey_id=? AND expected_size=? AND expected_sha256=? AND state NOT IN ('CANCELADO','RECHAZADO','EXPIRADO')",journey.toString(),request.tamanio(),request.sha256());if(!same.isEmpty())return estado(journey,uuid(same.getFirst().get("id")),session);
   long attempts=db.sql.queryForObject("SELECT COUNT(*) FROM media_upload u JOIN citizen_journey j ON j.id=u.journey_id WHERE j.owner_session=? AND u.created_ms>?",Long.class,session,clock.millis()-86400000);
   long pending=db.sql.queryForObject("SELECT COUNT(*) FROM media_upload WHERE journey_id=? AND state NOT IN ('CANCELADO','RECHAZADO','EXPIRADO')",Long.class,journey.toString());
   if(attempts>=48||pending>=policy.cantidad()*2L)throw new FalloTecnico(429);
   long reserved=number(cap,"reserved_bytes"),needed=Math.addExact(Math.multiplyExact(request.tamanio(),2L),Math.min(2*1024*1024L,policy.total()));
   long own=db.sql.queryForObject("SELECT COALESCE(SUM(u.expected_size),0) FROM media_upload u JOIN citizen_journey j ON j.id=u.journey_id WHERE j.owner_session=? AND u.released=FALSE",Long.class,session);
   if(own+request.tamanio()>2*policy.total())throw new FalloTecnico(413);
   if(reserved+needed>policy.global()||store.espacio()<needed+policy.libreMinimo())throw new FalloTecnico(507);
   var id=UUID.randomUUID();long now=clock.millis();db.sql.update("UPDATE media_capacity SET reserved_bytes=reserved_bytes+? WHERE id=1",needed);
   db.sql.update("INSERT INTO media_upload(id,journey_id,name,expected_size,expected_sha256,state,created_ms,touched_ms,expires_ms,reserved_bytes) VALUES(?,?,?,?,?,'RECIBIENDO',?,?,?,?)",id.toString(),journey.toString(),name,request.tamanio(),request.sha256(),now,now,now+policy.venceMs(),needed);db.event("MULTIMEDIA",id.toString(),"CARGA_INICIADA");return estado(journey,id,session);
  }));
 }
 public List<Estado> listar(UUID journey,String session){journeys.consultar(journey,session);return db.sql.queryForList("SELECT id FROM media_upload WHERE journey_id=? AND state NOT IN ('CANCELADO','EXPIRADO') ORDER BY created_ms,id",String.class,journey.toString()).stream().map(id->estado(journey,UUID.fromString(id),session)).toList();}
 public Estado estado(UUID journey,UUID id,String session){try(var lock=store.lectura(journey)){return estadoProtegido(journey,id,session);}catch(IOException fail){throw new FalloTecnico(503);}}
 private Estado estadoProtegido(UUID journey,UUID id,String session){var r=row(journey,id,session,false);var missing=new ArrayList<Integer>();
  if(!"VALIDADO".equals(r.get("state")))for(var c:db.sql.queryForList("SELECT chunk_index,size_bytes FROM media_chunk WHERE upload_id=? ORDER BY chunk_index",id.toString())){int idx=((Number)c.get("chunk_index")).intValue();try{var file=store.bloque(id,idx);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)!=number(c,"size_bytes"))missing.add(idx);}catch(IOException e){missing.add(idx);}}
  AdjuntosFormulario.Descriptor descriptor="VALIDADO".equals(r.get("state"))?descriptor(r):null;
  return new Estado(id,r.get("name").toString(),number(r,"expected_size"),r.get("expected_sha256").toString(),number(r,"offset_bytes"),policy.bloque(),r.get("state").toString(),(String)r.get("error_code"),List.copyOf(missing),descriptor);
 }
 private AdjuntosFormulario.Descriptor descriptor(Map<String,Object> r){return new AdjuntosFormulario.Descriptor(uuid(r.get("id")),r.get("name").toString(),number(r,"expected_size"),r.get("expected_sha256").toString(),r.get("media_type").toString(),"VALIDADO");}
 public Estado bloque(UUID journey,UUID id,String session,int index,String checksum,long length,InputStream input){
  hash(checksum);var current=row(journey,id,session,false);journeys.consultar(journey,session);long total=number(current,"expected_size"),start=Math.multiplyExact((long)index,policy.bloque());if(index<0||start<0||start>=total||length>=0&&length!=Math.min(policy.bloque(),total-start))throw new FalloTecnico(413);
  if(!"RECIBIENDO".equals(current.get("state"))||number(current,"expires_ms")<=clock.millis())throw new FalloTecnico(409);
  final long blockLength=Math.min(policy.bloque(),total-start);
  return guard(journey,()->{
   Path temp;try{if(store.espacio()<blockLength+policy.libreMinimo())throw new FalloTecnico(507);temp=store.temporal();}catch(IOException e){throw new FalloTecnico(503);}
   try{if(!store.recibir(input,blockLength,temp).equals(checksum))throw new FalloTecnico(422);
    return db.transaction(()->{journeys.bloquearBorrador(journey,session);var r=row(journey,id,session,true);if(!"RECIBIENDO".equals(r.get("state"))||number(r,"expires_ms")<=clock.millis())throw new FalloTecnico(409);
     long offset=number(r,"offset_bytes");var existing=db.sql.queryForList("SELECT sha256 FROM media_chunk WHERE upload_id=? AND chunk_index=?",id.toString(),index);
     if(start>offset||start<offset&&(existing.isEmpty()||!checksum.equals(existing.getFirst().get("sha256"))))throw new FalloTecnico(409);
     try{store.publicar(temp,store.bloque(id,index));}catch(IOException e){throw new FalloTecnico(503);}
     if(existing.isEmpty()){db.sql.update("INSERT INTO media_chunk(upload_id,chunk_index,size_bytes,sha256) VALUES(?,?,?,?)",id.toString(),index,blockLength,checksum);db.sql.update("UPDATE media_upload SET offset_bytes=?,touched_ms=? WHERE id=?",start+blockLength,clock.millis(),id.toString());}
     return estado(journey,id,session);
    });
   }catch(IOException e){throw new FalloTecnico(503);}finally{try{store.liberarTemporal(temp);}catch(IOException ignored){}}
  });
 }
 public Estado finalizar(UUID journey,UUID id,String session){
  var before=row(journey,id,session,false);if("VALIDADO".equals(before.get("state")))return estado(journey,id,session);
  if("ANALIZANDO".equals(before.get("state"))&&number(before,"lease_until_ms")>clock.millis())return estado(journey,id,session);
  if(!analyzing.tryAcquire())throw new FalloTecnico(429);var lease=UUID.randomUUID();
  try{guard(journey,()->db.transaction(()->{journeys.bloquearBorrador(journey,session);var r=row(journey,id,session,true);
   if(number(r,"expires_ms")<=clock.millis()||!Set.of("RECIBIENDO","REINTENTABLE","ANALIZANDO").contains(r.get("state"))||"ANALIZANDO".equals(r.get("state"))&&number(r,"lease_until_ms")>clock.millis()||number(r,"offset_bytes")!=number(r,"expected_size")||!estado(journey,id,session).bloquesFaltantes().isEmpty())throw new FalloTecnico(409);
   db.sql.update("UPDATE media_upload SET state='ANALIZANDO',lease_id=?,lease_until_ms=?,error_code=NULL WHERE id=?",lease.toString(),clock.millis()+180000,id.toString());return null;
  }));running.add(id);executor.execute(()->{try{analizar(journey,id,session,lease);}finally{running.remove(id);analyzing.release();try{liberarCancelado(id);}catch(RuntimeException retained){/* Se conserva para saneamiento controlado. */}}});return estado(journey,id,session);}catch(RuntimeException e){running.remove(id);analyzing.release();throw e;}
 }
 private void analizar(UUID journey,UUID id,String session,UUID lease){
  String phase="ENSAMBLAR";try(var route=store.lectura(journey);var lock=store.guardia()){
   var r=row(journey,id,session,false);var file=store.ensamblar(id,number(r,"expected_size"),policy.bloque());if(Files.size(file)!=number(r,"expected_size")||!AlmacenPrivado.huella(file).equals(r.get("expected_sha256")))throw new IllegalArgumentException("Integridad completa inválida");
   phase="ANTIVIRUS";if(!scanner.limpio(file))throw new IllegalArgumentException("Archivo rechazado");phase="INSPECTOR";var result=inspector.examinar(file,policy.formato(r.get("name").toString()));
   if(!policy.formato(r.get("name").toString()).tipo().equals(result.tipo()))throw new IllegalArgumentException("Tipo distinto");String pngHash=null;long pngSize=0;
   phase="DERIVADA";if(result.png()!=null){if(result.png().length>2*1024*1024)throw new IOException("Representación excesiva");var temp=store.temporal();try{Files.write(temp,result.png(),StandardOpenOption.TRUNCATE_EXISTING);try(var c=java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE)){c.force(true);}store.publicar(temp,store.derivada(id));pngHash=AlmacenPrivado.huella(store.derivada(id));pngSize=Files.size(store.derivada(id));}finally{store.liberarTemporal(temp);}}
   phase="CONFIRMAR";final String derived=pngHash;final long derivedSize=pngSize;db.transaction(()->{journeys.bloquearBorrador(journey,session);var live=row(journey,id,session,true);if(!lease.toString().equals(live.get("lease_id"))||!"ANALIZANDO".equals(live.get("state")))return null;
    db.sql.update("UPDATE media_upload SET state='VALIDADO',media_type=?,view_kind=?,preview_sha256=?,preview_size=?,lease_until_ms=0,touched_ms=? WHERE id=?",result.tipo(),result.vista(),derived,derivedSize,clock.millis(),id.toString());db.event("MULTIMEDIA",id.toString(),"ANALISIS_COMPLETO");return null;});
   try{store.borrarBloques(id);}catch(IOException retained){/* Un temporal retenido no cambia la validez del original. */}
  }catch(Exception failure){db.event("MULTIMEDIA",id.toString(),"FALLO_"+phase+"_"+failure.getClass().getSimpleName());String state=failure instanceof IllegalArgumentException?"RECHAZADO":"REINTENTABLE";db.sql.update("UPDATE media_upload SET state=?,error_code=?,lease_until_ms=0 WHERE id=? AND lease_id=? AND state='ANALIZANDO'",state,state.equals("RECHAZADO")?"CONTENIDO_NO_ADMITIDO":"INSPECCION_NO_DISPONIBLE",id.toString(),lease.toString());}
 }
 public void cancelar(UUID journey,UUID id,String session){guard(journey,()->db.transaction(()->{journeys.bloquearBorrador(journey,session);var r=row(journey,id,session,true);if(db.sql.queryForObject("SELECT COUNT(*) FROM citizen_attachment WHERE id=? AND journey_id=?",Integer.class,id.toString(),journey.toString())>0)throw new FalloTecnico(409);if("EXPIRADO".equals(r.get("state")))throw new FalloTecnico(409);db.sql.update("UPDATE media_upload SET state='CANCELADO',lease_until_ms=0,expires_ms=? WHERE id=?",clock.millis(),id.toString());db.event("MULTIMEDIA",id.toString(),"CARGA_CANCELADA");return null;}));if(!running.contains(id))liberarCancelado(id);}
 private void liberarCancelado(UUID id){var owners=db.sql.queryForList("SELECT journey_id FROM media_upload WHERE id=?",String.class,id.toString());if(owners.isEmpty())return;UUID journey=UUID.fromString(owners.getFirst());guard(journey,()->{
  var rows=db.sql.queryForList("SELECT state,released FROM media_upload WHERE id=?",id.toString());if(rows.isEmpty()||!"CANCELADO".equals(rows.getFirst().get("state"))||Boolean.TRUE.equals(rows.getFirst().get("released")))return null;
  if(db.sql.queryForObject("SELECT COUNT(*) FROM citizen_attachment WHERE id=?",Integer.class,id.toString())>0)throw new FalloTecnico(409);
  try{store.borrarNoRecibido(id);}catch(IOException e){throw new FalloTecnico(503);}
  db.transaction(()->{db.sql.queryForMap("SELECT reserved_bytes FROM media_capacity WHERE id=1 FOR UPDATE");var live=db.sql.queryForMap("SELECT state,released,reserved_bytes FROM media_upload WHERE id=? FOR UPDATE",id.toString());if("CANCELADO".equals(live.get("state"))&&!Boolean.TRUE.equals(live.get("released"))){db.sql.update("UPDATE media_capacity SET reserved_bytes=reserved_bytes-? WHERE id=1",number(live,"reserved_bytes"));db.sql.update("UPDATE media_upload SET released=TRUE WHERE id=?",id.toString());}return null;});return null;
 });}
 private List<AdjuntosFormulario.Descriptor> activos(UUID journey){return db.sql.query("SELECT id,name,size_bytes,sha256,media_type FROM citizen_attachment WHERE journey_id=? ORDER BY id",(rs,n)->new AdjuntosFormulario.Descriptor(UUID.fromString(rs.getString(1)),rs.getString(2),rs.getLong(3),rs.getString(4),rs.getString(5),"VALIDADO"),journey.toString());}
 private String revision(List<AdjuntosFormulario.Descriptor> files){return CanonFormulario.huella("MANIFIESTO_ADJUNTOS",files,mapper);}
 public Lote lote(UUID journey,String session){journeys.consultar(journey,session);var files=activos(journey);return new Lote(revision(files),files);}
 public Lote confirmar(UUID journey,String session,Manifest request){
  if(request==null||request.ids()==null||request.ids().stream().anyMatch(Objects::isNull)||request.ids().size()>policy.cantidad()||new HashSet<>(request.ids()).size()!=request.ids().size())throw new FalloTecnico(413);
  return guard(journey,()->{
   journeys.consultar(journey,session);
   for(var id:request.ids()){
    var legacy=db.sql.queryForList("SELECT storage_kind FROM citizen_attachment WHERE id=? AND journey_id=?",id.toString(),journey.toString());
    if(!legacy.isEmpty()&&"MYSQL".equals(legacy.getFirst().get("storage_kind")))continue;
    var r=row(journey,id,session,false);if(!"VALIDADO".equals(r.get("state")))throw new FalloTecnico(409);
    try{store.verificar(store.original(id),number(r,"expected_size"),r.get("expected_sha256").toString());if(r.get("preview_sha256")!=null)store.verificar(store.derivada(id),number(r,"preview_size"),r.get("preview_sha256").toString());}catch(IOException e){throw new FalloTecnico(503);}
   }
   return db.transaction(()->{journeys.bloquearBorrador(journey,session);var current=activos(journey);if(!revision(current).equals(request.base()))throw new FalloTecnico(409);var old=new HashMap<UUID,AdjuntosFormulario.Descriptor>();current.forEach(d->old.put(d.id(),d));var selected=new ArrayList<AdjuntosFormulario.Descriptor>();
   for(var id:request.ids()){if(old.containsKey(id)){selected.add(old.get(id));continue;}var r=row(journey,id,session,true);if(!"VALIDADO".equals(r.get("state")))throw new FalloTecnico(409);selected.add(descriptor(r));}
   if(selected.stream().mapToLong(AdjuntosFormulario.Descriptor::tamanio).sum()>policy.total())throw new FalloTecnico(413);
   for(var d:current)if(!request.ids().contains(d.id()))db.sql.update("DELETE FROM citizen_attachment WHERE id=? AND journey_id=?",d.id().toString(),journey.toString());
   for(var d:selected)if(!old.containsKey(d.id()))db.sql.update("INSERT INTO citizen_attachment(id,journey_id,name,size_bytes,sha256,media_type,content,storage_kind,storage_key) VALUES(?,?,?,?,?,?,NULL,'FILE',?)",d.id().toString(),journey.toString(),d.nombre(),d.tamanio(),d.sha256(),d.tipo(),d.id().toString());
   db.event("MULTIMEDIA",journey.toString(),"MANIFIESTO_CONFIRMADO");return lote(journey,session);
  });});
 }
 public record Limpieza(UUID id,String estado,long reservado){}
 public List<Limpieza> sanear(boolean apply){
  var candidates=db.sql.queryForList("SELECT id,journey_id,state,reserved_bytes FROM media_upload u WHERE expires_ms<=? AND released=FALSE AND state IN ('RECIBIENDO','REINTENTABLE','RECHAZADO','CANCELADO','EXPIRADO') AND NOT EXISTS(SELECT 1 FROM citizen_attachment a WHERE a.id=u.id) AND NOT EXISTS(SELECT 1 FROM citizen_submission s WHERE s.journey_id=u.journey_id)",clock.millis());var result=new ArrayList<Limpieza>();
  if(apply&&analyzing.availablePermits()==0)throw new FalloTecnico(409);
  for(var c:candidates){UUID id=uuid(c.get("id")),journey=uuid(c.get("journey_id"));result.add(new Limpieza(id,c.get("state").toString(),number(c,"reserved_bytes")));if(!apply)continue;
   guard(journey,()->{boolean marked=db.transaction(()->{
    var owners=db.sql.queryForList("SELECT owner_session FROM citizen_journey WHERE id=?",journey.toString());if(owners.isEmpty())return false;String owner=owners.getFirst().get("owner_session").toString();journeys.bloquearBorrador(journey,owner);var live=row(journey,id,owner,true);
    if(number(live,"expires_ms")>clock.millis()||"ANALIZANDO".equals(live.get("state"))||Boolean.TRUE.equals(live.get("released"))||db.sql.queryForObject("SELECT COUNT(*) FROM citizen_attachment WHERE id=?",Integer.class,id.toString())>0)return false;
    db.sql.update("UPDATE media_upload SET state='EXPIRADO' WHERE id=?",id.toString());return true;});
    if(!marked)return null;try{store.borrarNoRecibido(id);}catch(IOException e){throw new FalloTecnico(503);}
    db.transaction(()->{db.sql.queryForMap("SELECT reserved_bytes FROM media_capacity WHERE id=1 FOR UPDATE");var live=db.sql.queryForMap("SELECT reserved_bytes,released FROM media_upload WHERE id=? FOR UPDATE",id.toString());if(!Boolean.TRUE.equals(live.get("released"))){db.sql.update("UPDATE media_capacity SET reserved_bytes=reserved_bytes-? WHERE id=1",number(live,"reserved_bytes"));db.sql.update("UPDATE media_upload SET released=TRUE WHERE id=?",id.toString());db.event("MULTIMEDIA",id.toString(),"TEMPORAL_EXPIRADO_ELIMINADO");}return null;});return null;
   });
  }return List.copyOf(result);
 }
 public record Vista(String version,String origen,AdjuntosFormulario.Descriptor original,String representacion,boolean originalInline,boolean descargable,String huellaDerivada,Long tamanioDerivada){}
 public Vista vista(UUID journey,UUID id,String session){journeys.consultar(journey,session);var all=activos(journey);var descriptor=all.stream().filter(d->d.id().equals(id)).findFirst().orElseThrow(()->new FalloTecnico(404));
  var rows=db.sql.queryForList("SELECT state,view_kind,preview_sha256,preview_size FROM media_upload WHERE journey_id=? AND id=?",journey.toString(),id.toString());String kind="DESCARGA";String preview=null;Long previewSize=null;
  if(!rows.isEmpty()&&"VALIDADO".equals(rows.getFirst().get("state"))){var v=rows.getFirst();kind=v.get("view_kind").toString();preview=(String)v.get("preview_sha256");previewSize=v.get("preview_size")==null?null:number(v,"preview_size");}
  db.sql.update("INSERT INTO media_access(id,attachment_id,at_ms,principal_kind,principal_ref,representation,event_code) VALUES(?,?,?,'CIUDADANIA',?,?,'METADATOS_SOLICITADOS')",UUID.randomUUID().toString(),id.toString(),clock.millis(),AuditoriaMedia.referencia(session),kind);
  return new Vista("1.0","INGRESO_CIUDADANO",descriptor,kind,Set.of("AUDIO","VIDEO").contains(kind),true,preview,previewSize);
 }
 public RecursoPrivado representacion(UUID journey,UUID id,String session,AdjuntosFormulario files){var view=vista(journey,id,session);
  if(Boolean.TRUE.equals(db.sql.queryForObject("SELECT bytes_retired FROM citizen_attachment WHERE id=? AND journey_id=?",Boolean.class,id.toString(),journey.toString())))throw new FalloTecnico(410);
  if(view.representacion().equals("IMAGEN_DERIVADA")){try{var p=store.derivada(id);store.verificar(p,view.tamanioDerivada(),view.huellaDerivada());return new RecursoPrivado("vista-"+id+".png",view.tamanioDerivada(),view.huellaDerivada(),"image/png",()->store.abrir(journey,p));}catch(IOException e){throw new FalloTecnico(503);}}
  if(view.originalInline())return files.recurso(journey,id,session);throw new FalloTecnico(415);
 }
 public Map<String,Object> capacidad(){return Map.of("reservados",db.sql.queryForObject("SELECT reserved_bytes FROM media_capacity WHERE id=1",Long.class),"limite",policy.global(),"libresFilesystem",store.espacio(),"minimoLibre",policy.libreMinimo(),"analisisActivos",1-analyzing.availablePermits(),"alertaCuota",db.sql.queryForObject("SELECT reserved_bytes FROM media_capacity WHERE id=1",Long.class)>=policy.global()*8/10,"alertaEspacio",store.espacio()<policy.libreMinimo()+2*policy.video());}
 public void close(){executor.shutdownNow();try{if(!executor.awaitTermination(5,TimeUnit.SECONDS))throw new IllegalStateException("Análisis aún activo; conservar lock de almacén");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Cierre de análisis interrumpido",e);}}
}
