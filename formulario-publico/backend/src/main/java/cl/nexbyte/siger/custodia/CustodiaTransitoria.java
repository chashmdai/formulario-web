package cl.nexbyte.siger.custodia;
import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.multimedia.*;
import cl.nexbyte.siger.robustez.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import tools.jackson.databind.json.JsonMapper;

/** Receptor transitorio: mantenimiento online acotado por recorrido. Nunca consulta ni monta SIGER. */
public final class CustodiaTransitoria implements AutoCloseable {
 public record Resultado(int minimizadas,int conservadas,int ciclosAuxiliares,boolean cerrada,boolean solapada){}
 private final BaseJdbc db;private final AlmacenPrivado store;private final boolean ownStore;
 private final JsonMapper mapper;private final GarantiasCustodiaJdbc guarantees;private final CicloVidaCustodia motor;
 private final Map<String,PublicKey> publicKeys,institution;private final PrivateKey signer;private final String keyId;
 private final Clock clock;private final CicloVidaCustodia.Politica policy;private final byte[] ownerKey;
 private Duration draftMetadata=Duration.ofDays(7),technicalAudit=Duration.ofDays(30);
 public CustodiaTransitoria retencionesTecnicas(Duration drafts,Duration audit){if(drafts.compareTo(policy.abandono())<0||drafts.compareTo(Duration.ofDays(90))>0||audit.compareTo(Duration.ofDays(7))<0||audit.compareTo(Duration.ofDays(365))>0)throw new IllegalArgumentException("Retención técnica fuera de rango");draftMetadata=drafts;technicalAudit=audit;return this;}
 private final AtomicBoolean running=new AtomicBoolean();private volatile boolean stopped;
 public CustodiaTransitoria(BaseJdbc db,AlmacenPrivado store,boolean ownStore,JsonMapper mapper,GarantiasCustodiaJdbc guarantees,
   Map<String,PublicKey> publicKeys,Map<String,PublicKey> institution,String keyId,PrivateKey signer,byte[] ownerKey,Clock clock,CicloVidaCustodia.Politica policy){
  this.db=db;this.store=store;this.ownStore=ownStore;this.mapper=mapper;this.guarantees=guarantees;this.publicKeys=Map.copyOf(publicKeys);this.institution=Map.copyOf(institution);this.keyId=keyId;this.signer=signer;this.ownerKey=ownerKey.clone();this.clock=clock;this.policy=policy;
  if(ownerKey.length<32)throw new IllegalArgumentException("Clave de minimización requerida");
  motor=new CicloVidaCustodia(db,store,null,mapper,publicKeys,institution,clock,policy).lotesAcotados(20).prueba((e,a)->guarantees.comprobar(mapper.writeValueAsString(e),mapper.writeValueAsString(a),"COMPLETO"));
 }
 public GarantiasCustodiaJdbc garantias(){return guarantees;}
 private static long n(Map<String,Object> r,String k){return ((Number)r.get(k)).longValue();}
 private final Map<String,String> witnessCursor=new HashMap<>();private boolean preferLegacy;
 public synchronized List<Map<String,Object>> candidatos(int limit){
  if(limit<1||limit>100)throw new IllegalArgumentException("Límite inválido");var result=new ArrayList<Map<String,Object>>();
  var queues=preferLegacy?List.of("HISTORICO","COMPLETO"):List.of("COMPLETO","HISTORICO");preferLegacy=!preferLegacy;
  for(String queue:queues){String table=queue.equals("COMPLETO")?"citizen_submission":"public_delivery";String cursor=witnessCursor.getOrDefault(queue,"");
   String query="SELECT s.id FROM "+table+" s LEFT JOIN custody_guarantee g ON g.operation_id=s.id AND g.queue_kind=? WHERE s.state='CONFIRMADA' AND s.content_retired=FALSE AND (g.expires_ms IS NULL OR g.expires_ms<?) AND s.id>? ORDER BY s.id LIMIT ?";
   var rows=db.sql.queryForList(query,queue,clock.millis()+3600000,cursor,limit-result.size());
   if(rows.isEmpty()&&!cursor.isEmpty())rows=db.sql.queryForList(query,queue,clock.millis()+3600000,"",limit-result.size());
   for(var row:rows)result.add(Map.of("id",row.get("id"),"cola",queue));
   witnessCursor.put(queue,rows.isEmpty()?"":rows.getLast().get("id").toString());if(result.size()==limit)break;
  }return List.copyOf(result);
 }
 public Resultado ejecutar(){
  if(stopped||!running.compareAndSet(false,true))return new Resultado(0,0,0,!policy.eliminar(),true);
  UUID lease=UUID.randomUUID();boolean claimed=false;int done=0,blocked=0,drafts=0;
  try {
   claimed=db.transaction(()->{
    var r=db.sql.queryForMap("SELECT * FROM transient_maintenance WHERE id=1 FOR UPDATE");
    if(n(r,"lease_until_ms")>clock.millis())return false;
    db.sql.update("UPDATE transient_maintenance SET lease_id=?,lease_until_ms=? WHERE id=1",lease.toString(),clock.millis()+600000);return true;
   });
   if(!claimed)return new Resultado(0,0,0,!policy.eliminar(),true);
   for(String queue:List.of("COMPLETO","HISTORICO")) {
    String table=queue.equals("COMPLETO")?"citizen_submission":"public_delivery";
    var rows=db.sql.queryForList("SELECT id FROM "+table+" WHERE state='CONFIRMADA' AND content_retired=FALSE ORDER BY retention_checked_ms,created_ms,id LIMIT 20",String.class);
    for(var value:rows) {
     if(stopped)break;renew(lease);db.sql.update("UPDATE "+table+" SET retention_checked_ms=? WHERE id=?",clock.millis(),value);UUID id=UUID.fromString(value);
     try {
      if(queue.equals("COMPLETO")) {
       var r=db.sql.queryForMap("SELECT journey_id FROM citizen_submission WHERE id=?",value);UUID journey=UUID.fromString(r.get("journey_id").toString());
       try(var gate=store.mantenimiento(journey);var backup=store.guardia()) {
        if(gate==null){blocked++;continue;}
        if(!policy.eliminar()){motor.verificar(id);continue;}
        motor.retirar(id);minimizarCompleta(id,journey);done++;
       }
      } else if(minimizarHistorica(id))done++;
     }catch(IOException|RuntimeException preserve){blocked++;}
    }
   }
   if(policy.eliminar()&&!stopped) {
    try(var backup=store.guardia()) {motor.abandonar(true);limpiarBorradores();motor.compactarReservas(true);depurarMetadatosTecnicos();var temporales=store.limpiarIncoming(clock.millis()-Duration.ofHours(24).toMillis(),20);if(temporales.archivos()>0)db.event("TRANSITORIO_TECNICO",clock.instant().toString().substring(0,10),"TEMPORALES_HUERFANOS_RETIRADOS");drafts=1;}
   }
   db.sql.update("UPDATE transient_maintenance SET last_ms=?,last_code=? WHERE id=1 AND lease_id=?",clock.millis(),policy.eliminar()?(blocked>0?"CONSERVADOS_POR_CONTROL":"CORRECTO"):"ELIMINACION_CERRADA",lease.toString());
   return new Resultado(done,blocked,drafts,!policy.eliminar(),false);
  }catch(IOException|RuntimeException failure) {db.sql.update("UPDATE transient_maintenance SET last_ms=?,last_code='INCIDENCIA_CONSERVAR' WHERE id=1 AND lease_id=?",clock.millis(),lease.toString());return new Resultado(done,blocked+1,drafts,!policy.eliminar(),false);}
  finally {try{if(claimed)db.sql.update("UPDATE transient_maintenance SET lease_until_ms=0,lease_id=NULL WHERE id=1 AND lease_id=?",lease.toString());}finally{running.set(false);if(stopped&&ownStore)try{store.close();}catch(IOException incomplete){throw new IllegalStateException("Cierre pendiente");}}}
 }
 private void renew(UUID lease){if(db.sql.update("UPDATE transient_maintenance SET lease_until_ms=? WHERE id=1 AND lease_id=?",clock.millis()+600000,lease.toString())!=1)throw new IllegalStateException("Lease perdido");}
 private void minimizarCompleta(UUID id,UUID journey) {
  db.transaction(()->{
   var r=db.sql.queryForMap("SELECT * FROM citizen_submission WHERE id=? FOR UPDATE",id.toString());
   if(Boolean.TRUE.equals(r.get("content_retired")))return null;
   var life=db.sql.queryForMap("SELECT * FROM custody_lifecycle WHERE submission_id=? FOR UPDATE",id.toString());
   if(!"LIBERADA".equals(life.get("phase"))||Boolean.TRUE.equals(life.get("hold_active")))throw new IllegalStateException("Custodia no liberable");
   try{guarantees.comprobar(r.get("body").toString(),r.get("ack").toString(),"COMPLETO");}catch(IOException e){throw new IllegalStateException("Conservar payload");}
   var e=mapper.readValue(r.get("body").toString(),EntregaFormulario.class);e.validar(publicKeys,mapper);var ack=mapper.readValue(r.get("ack").toString(),RegistroInstitucional.class);e.validarRegistro(ack,institution,mapper);
   if(db.sql.queryForObject("SELECT COUNT(*) FROM citizen_attachment WHERE journey_id=? AND bytes_retired=FALSE",Integer.class,journey.toString())!=0)throw new IllegalStateException("Bytes pendientes");
   var files=e.comunicacion().adjuntos().stream().map(a->new ReciboMinimo.Archivo(a.id(),a.tamanio(),a.sha256())).toList();
   var minimum=new ReciboMinimo.Contenido(id,journey,"COMPLETO",e.custodia(),ack,files);
   String sealed=mapper.writeValueAsString(FirmaOperativa.firmar(minimum,ReciboMinimo.DOMAIN,keyId,signer,mapper));
   ReciboMinimo.validar(sealed,id,publicKeys,institution,mapper);
   db.sql.queryForMap("SELECT id FROM citizen_journey WHERE id=? FOR UPDATE",journey.toString());
   scrubJourney(journey);
   db.sql.update("UPDATE citizen_submission SET body=NULL,minimal_receipt=?,content_retired=TRUE,lease_id=NULL,last_error=NULL,next_attempt_ms=0 WHERE id=?",sealed,id.toString());
   db.sql.update("UPDATE custody_lifecycle SET phase='MINIMIZADA' WHERE submission_id=?",id.toString());
   db.event("CUSTODIA_TRANSITORIA",id.toString(),"PAYLOAD_Y_IDENTIDAD_RETIRADOS");return null;
  });
 }
 private void scrubJourney(UUID journey){
  db.sql.update("DELETE FROM citizen_person WHERE journey_id=?",journey.toString());
  db.sql.update("DELETE FROM citizen_identity_history WHERE journey_id=?",journey.toString());
  db.sql.update("UPDATE citizen_attachment SET name='retirado' WHERE journey_id=? AND bytes_retired=TRUE",journey.toString());
  db.sql.update("UPDATE media_upload SET name='retirado',error_code=NULL WHERE journey_id=? AND released=TRUE",journey.toString());
  db.sql.update("UPDATE citizen_journey SET state='MINIMIZADO' WHERE id=?",journey.toString());
 }
 private boolean minimizarHistorica(UUID id)throws IOException {
  var r=db.sql.queryForMap("SELECT * FROM public_delivery WHERE id=?",id.toString());
  if(Boolean.TRUE.equals(r.get("retention_hold")))return false;
  // No retrofechar históricos y comprobar firma antes de iniciar el plazo.
  GarantiaCustodia.contexto(r.get("body").toString(),r.get("ack").toString(),"HISTORICO",publicKeys,institution,mapper);
  if(n(r,"retire_after_ms")==0){db.sql.update("UPDATE public_delivery SET retire_after_ms=? WHERE id=? AND retire_after_ms=0",clock.millis()+policy.postAck().toMillis(),id.toString());return false;}
  if(!policy.eliminar()||n(r,"retire_after_ms")>clock.millis())return false;
  guarantees.comprobar(r.get("body").toString(),r.get("ack").toString(),"HISTORICO");
  return db.transaction(()->{
   var current=db.sql.queryForMap("SELECT * FROM public_delivery WHERE id=? FOR UPDATE",id.toString());if(Boolean.TRUE.equals(current.get("content_retired")))return false;
   if(Boolean.TRUE.equals(current.get("retention_hold"))||!Objects.equals(current.get("body"),r.get("body"))||!Objects.equals(current.get("ack"),r.get("ack")))throw new IllegalStateException("Conservar histórico");
   var e=mapper.readValue(current.get("body").toString(),EntregaVerificada.class);e.validar(publicKeys,mapper);var ack=mapper.readValue(current.get("ack").toString(),RegistroInstitucional.class);ack.validar(institution,mapper,e);
   var minimum=new ReciboMinimo.Contenido(id,null,"HISTORICO",e.comprobante(),ack,List.of());String sealed=mapper.writeValueAsString(FirmaOperativa.firmar(minimum,ReciboMinimo.DOMAIN,keyId,signer,mapper));ReciboMinimo.validar(sealed,id,publicKeys,institution,mapper);
   String owner=current.get("owner_run")==null?null:ReciboMinimo.propietario(current.get("owner_run").toString(),ownerKey);
   db.sql.update("UPDATE public_delivery SET body=NULL,owner_run=NULL,owner_fingerprint=?,legacy_ack=NULL,minimal_receipt=?,content_retired=TRUE,lease_id=NULL,last_error=NULL,next_attempt_ms=0 WHERE id=?",owner,sealed,id.toString());
   db.event("CUSTODIA_TRANSITORIA",id.toString(),"PAYLOAD_HISTORICO_RETIRADO");return true;
  });
 }
 private void limpiarBorradores()throws IOException {
  for(String id:db.sql.queryForList("SELECT j.id FROM citizen_journey j WHERE j.state IN ('BORRADOR','CANCELADO') AND j.expires_ms<? AND NOT EXISTS(SELECT 1 FROM citizen_submission s WHERE s.journey_id=j.id) LIMIT 50",String.class,clock.millis()-policy.abandono().toMillis())) {
   UUID journey=UUID.fromString(id);try(var gate=store.mantenimiento(journey)){if(gate==null)continue;
    db.transaction(()->{
     var row=db.sql.queryForMap("SELECT state FROM citizen_journey WHERE id=? FOR UPDATE",id);
     if(!Set.of("BORRADOR","CANCELADO").contains(row.get("state"))||db.sql.queryForObject("SELECT COUNT(*) FROM citizen_submission WHERE journey_id=?",Integer.class,id)>0)return null;
     if(db.sql.queryForObject("SELECT COUNT(*) FROM media_upload WHERE journey_id=? AND released=FALSE",Integer.class,id)>0||db.sql.queryForObject("SELECT COUNT(*) FROM citizen_attachment WHERE journey_id=? AND bytes_retired=FALSE",Integer.class,id)>0)return null;
     scrubJourney(journey);db.sql.update("UPDATE citizen_journey SET owner_session=? WHERE id=?","0".repeat(64),id);db.event("CUSTODIA_TRANSITORIA",id,"BORRADOR_PERSONAL_RETIRADO");return null;
    });
   }
  }
  db.sql.update("UPDATE media_upload SET name='retirado' WHERE released=TRUE AND name<>'retirado' AND journey_id IN (SELECT journey_id FROM citizen_submission WHERE content_retired=TRUE)");
 }
 private void depurarMetadatosTecnicos()throws IOException {
  for(String id:db.sql.queryForList("SELECT j.id FROM citizen_journey j WHERE j.state='MINIMIZADO' AND j.expires_ms<? AND NOT EXISTS(SELECT 1 FROM citizen_submission s WHERE s.journey_id=j.id) ORDER BY j.expires_ms,j.id LIMIT 20",String.class,clock.millis()-draftMetadata.toMillis())) {
   UUID journey=UUID.fromString(id);try(var gate=store.mantenimiento(journey)){if(gate==null)continue;
    var ids=new HashSet<String>(db.sql.queryForList("SELECT id FROM media_upload WHERE journey_id=?",String.class,id));ids.addAll(db.sql.queryForList("SELECT id FROM citizen_attachment WHERE journey_id=?",String.class,id));
    for(String resource:ids){UUID key=UUID.fromString(resource);if(Files.exists(store.original(key),LinkOption.NOFOLLOW_LINKS)||Files.exists(store.derivada(key),LinkOption.NOFOLLOW_LINKS))throw new IOException("Conservar metadatos de objeto inesperado");}
    db.transaction(()->{
     db.sql.queryForMap("SELECT id FROM citizen_journey WHERE id=? FOR UPDATE",id);
     if(db.sql.queryForObject("SELECT COUNT(*) FROM citizen_submission WHERE journey_id=?",Integer.class,id)>0||db.sql.queryForObject("SELECT COUNT(*) FROM media_upload WHERE journey_id=? AND released=FALSE",Integer.class,id)>0||db.sql.queryForObject("SELECT COUNT(*) FROM citizen_attachment WHERE journey_id=? AND bytes_retired=FALSE",Integer.class,id)>0)return null;
     db.sql.update("DELETE FROM media_access WHERE attachment_id IN (SELECT id FROM citizen_attachment WHERE journey_id=?)",id);
     db.sql.update("DELETE FROM media_chunk WHERE upload_id IN (SELECT id FROM media_upload WHERE journey_id=?)",id);
     db.sql.update("DELETE FROM storage_retirement WHERE journey_id=? AND submission_id IS NULL",id);
     db.sql.update("DELETE FROM citizen_attachment WHERE journey_id=?",id);db.sql.update("DELETE FROM media_upload WHERE journey_id=?",id);
     db.sql.update("DELETE FROM citizen_identity_history WHERE journey_id=?",id);db.sql.update("DELETE FROM citizen_person WHERE journey_id=?",id);db.sql.update("DELETE FROM citizen_journey WHERE id=?",id);
     db.event("TRANSITORIO_TECNICO",clock.instant().toString().substring(0,10),"METADATOS_BORRADOR_VENCIDOS");return null;
    });
   }
  }
  db.sql.update("DELETE FROM citizen_abuse_bucket WHERE window_ms<?",clock.millis()-Duration.ofDays(1).toMillis());
  db.sql.update("DELETE FROM media_access WHERE at_ms<? AND attachment_id IN (SELECT id FROM citizen_attachment WHERE bytes_retired=TRUE)",clock.millis()-technicalAudit.toMillis());
  db.sql.update("DELETE FROM operational_event WHERE at_ms<? AND category IN ('MULTIMEDIA','CUSTODIA_TRANSITORIA','ALMACENAMIENTO','TRANSITORIO_TECNICO') AND code NOT IN ('RETIRADA_FINALIZADA','PAYLOAD_Y_IDENTIDAD_RETIRADOS','PAYLOAD_HISTORICO_RETIRADO','CONSERVACION_BLOQUEADA','CONSERVACION_DESBLOQUEADA','HOLD_ACTIVO','HOLD_RETIRADO')",clock.millis()-technicalAudit.toMillis());
 }
 public void conservar(String queue,UUID id,boolean hold){if(queue.equals("COMPLETO"))motor.conservar(id,hold);else if(queue.equals("HISTORICO")){if(db.sql.update("UPDATE public_delivery SET retention_hold=? WHERE id=? AND content_retired=FALSE",hold,id.toString())!=1)throw new FalloTecnico(409);db.event("CUSTODIA_TRANSITORIA",id.toString(),hold?"HOLD_ACTIVO":"HOLD_RETIRADO");}else throw new IllegalArgumentException("Cola inválida");}
 public Map<String,Object> estado(){return Map.of("eliminacionHabilitada",policy.eliminar(),"ejecutando",running.get(),"mantenimiento",db.sql.queryForMap("SELECT last_ms,last_code FROM transient_maintenance WHERE id=1"),"payloads",db.sql.queryForList("SELECT content_retired,COUNT(*) cantidad,COALESCE(SUM(OCTET_LENGTH(body)),0) bytes FROM citizen_submission GROUP BY content_retired"),"historicos",db.sql.queryForList("SELECT content_retired,COUNT(*) cantidad,COALESCE(SUM(OCTET_LENGTH(body)),0) bytes FROM public_delivery GROUP BY content_retired"));}
 public void close()throws IOException{stopped=true;if(ownStore&&!running.get())store.close();}
}