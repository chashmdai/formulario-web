package cl.nexbyte.siger.custodia;
import cl.nexbyte.siger.robustez.*;
import java.io.IOException;
import java.security.*;
import java.time.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
/** Recibe pruebas por conexión SALIENTE desde el entorno institucional. No consulta SIGER. */
public final class GarantiasCustodiaJdbc {
 private final BaseJdbc db;private final JsonMapper mapper;private final Map<String,PublicKey> publicKeys,institution,trust;private final Clock clock;
 public GarantiasCustodiaJdbc(BaseJdbc db,JsonMapper mapper,Map<String,PublicKey> publicKeys,Map<String,PublicKey> institution,Map<String,PublicKey> trust,Clock clock){
  this.db=db;this.mapper=mapper;this.publicKeys=Map.copyOf(publicKeys);this.institution=Map.copyOf(institution);this.trust=Map.copyOf(trust);this.clock=clock;
  if(trust.isEmpty()||trust.values().stream().anyMatch(k->publicKeys.containsValue(k)||institution.containsValue(k)))throw new IllegalArgumentException("Separar firmantes de garantía, ACK y custodia pública");
 }
 private static String table(String queue){return switch(queue){case "COMPLETO"->"citizen_submission";case "HISTORICO"->"public_delivery";default->throw new IllegalArgumentException("Cola inválida");};}
 public synchronized void aceptar(FirmaOperativa signed){
  var e=GarantiaCustodia.validar(signed,trust,clock,mapper);var c=e.contexto();String table=table(c.cola());
  db.transaction(()->{
   var row=db.sql.queryForMap("SELECT body,ack,state,content_retired FROM "+table+" WHERE id=? FOR UPDATE",c.id().toString());
   if(!"CONFIRMADA".equals(row.get("state"))||Boolean.TRUE.equals(row.get("content_retired")))throw new FalloTecnico(409);
   var expected=GarantiaCustodia.contexto(row.get("body").toString(),row.get("ack").toString(),c.cola(),publicKeys,institution,mapper);
   if(!c.equals(expected))throw new FalloTecnico(409);
   var old=db.sql.queryForList("SELECT checked_ms,evidence FROM custody_guarantee WHERE queue_kind=? AND operation_id=? FOR UPDATE",c.cola(),c.id().toString());
   String json=mapper.writeValueAsString(signed);
   if(!old.isEmpty()){
    long previous=((Number)old.getFirst().get("checked_ms")).longValue();
    if(previous>e.verificadaEn().toEpochMilli()||previous==e.verificadaEn().toEpochMilli()&&!json.equals(old.getFirst().get("evidence")))throw new FalloTecnico(409);
    if(json.equals(old.getFirst().get("evidence")))return null;
    db.sql.update("UPDATE custody_guarantee SET evidence=?,checked_ms=?,expires_ms=? WHERE queue_kind=? AND operation_id=?",json,e.verificadaEn().toEpochMilli(),e.venceEn().toEpochMilli(),c.cola(),c.id().toString());
   } else db.sql.update("INSERT INTO custody_guarantee(queue_kind,operation_id,evidence,checked_ms,expires_ms) VALUES(?,?,?,?,?)",c.cola(),c.id().toString(),json,e.verificadaEn().toEpochMilli(),e.venceEn().toEpochMilli());
   db.event("CUSTODIA_TRANSITORIA",c.id().toString(),"GARANTIA_INDEPENDIENTE_VERIFICADA");return null;
  });
 }
 public void comprobar(String body,String ack,String queue)throws IOException {
  try {
   var context=GarantiaCustodia.contexto(body,ack,queue,publicKeys,institution,mapper);
   var rows=db.sql.queryForList("SELECT evidence FROM custody_guarantee WHERE queue_kind=? AND operation_id=?",queue,context.id().toString());
   if(rows.isEmpty())throw new IOException("Falta garantía institucional independiente");
   var e=GarantiaCustodia.validar(mapper.readValue(rows.getFirst().get("evidence").toString(),FirmaOperativa.class),trust,clock,mapper);
   if(!context.equals(e.contexto()))throw new IOException("Garantía no corresponde");
  }catch(RuntimeException invalid){throw new IOException("Garantía inválida");}
 }
}