package cl.nexbyte.siger.qa;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import cl.nexbyte.siger.robustez.BaseJdbc;
import org.flywaydb.core.Flyway;
import tools.jackson.databind.json.JsonMapper;

/** Opt-in: solo dos esquemas MySQL QA nuevos. Sin DROP/clean, datos reales, agente o servicios. */
public final class VerificarMigracionRetencion {
 private static void check(boolean value){if(!value)throw new IllegalStateException("Verificación de migración no satisfecha");}
 private static Flyway flyway(BaseJdbc db,String target){
  var c=Flyway.configure().dataSource(db.sql.getDataSource()).locations("classpath:db/publico/mysql").cleanDisabled(true).validateMigrationNaming(true);
  if(target!=null)c.target(target);return c.load();
 }
 private static List<Map<String,Object>> history(BaseJdbc db){return db.sql.queryForList("SELECT version,script,checksum,success FROM flyway_schema_history WHERE version<>'12' ORDER BY installed_rank");}
 public static void main(String[] args)throws Exception{
  var m=JsonMapper.builder().build();byte[] secret=System.in.readNBytes(8193);if(secret.length>8192)throw new IllegalArgumentException();
  var input=m.readTree(secret);Arrays.fill(secret,(byte)0);
  String user=input.path("user").asString(),pass=input.path("password").asString(),kind=args[0],schema=input.path(kind).asString();
  if(!Set.of("fresh","upgrade").contains(kind)||!user.matches("qa_ack72_[0-9]{10,14}")||!schema.matches("robust_public_ack12_"+kind+"_[0-9]{10,14}"))throw new IllegalArgumentException("Solo esquemas QA nuevos");
  String url="jdbc:mysql://127.0.0.1:13306/"+schema+"?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=3000&socketTimeout=15000";
  try(var db=new BaseJdbc(url,user,pass,2)){
   check(schema.equals(db.sql.queryForObject("SELECT DATABASE()",String.class)));
   check(db.sql.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=?",Integer.class,schema)==0);
   List<Map<String,Object>> before=List.of();Map<String,Object> original=null;UUID id=UUID.randomUUID();
   if(kind.equals("upgrade")){
    flyway(db,"11").migrate();before=history(db);check(before.size()==11);
    String journey=UUID.randomUUID().toString();
    db.sql.update("INSERT INTO citizen_journey(id,owner_session,kind,notice_version,accepted_channel,state,query_count,created_ms,expires_ms) VALUES(?,?,'NNA','QA_MIGRACION',FALSE,'RECIBIDO',0,1,2)",journey,"0".repeat(64));
    db.sql.update("INSERT INTO citizen_submission(id,journey_id,owner_session,request_hash,body,state,created_ms,next_attempt_ms,ack,document_version) VALUES(?,?,?,?,?,'CONFIRMADA',1,0,?,'1.0')",id.toString(),journey,"0".repeat(64),"0".repeat(64),"SENTINELA_SINTETICA_NO_ENVIAR","ACK_SINTETICO_NO_VALIDO");
    db.sql.update("INSERT INTO custody_lifecycle(submission_id,observed_ms,due_ms,ack_sha256,phase) VALUES(?,1,2,?,'RETIRANDO')",id.toString(),"0".repeat(64));
    db.sql.update("INSERT INTO public_delivery(id,request_hash,body,state,created_ms,next_attempt_ms,attempts,lease_until_ms) VALUES(?,?,'SENTINELA_HISTORICA','PENDIENTE',1,0,0,0)",id.toString(),"0".repeat(64));
    original=db.sql.queryForMap("SELECT * FROM citizen_submission WHERE id=?",id.toString());
   }
   flyway(db,null).migrate();flyway(db,null).validate();
   check(flyway(db,null).info().current().getVersion().toString().equals("12"));
   check(db.sql.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=TRUE AND version IS NOT NULL",Integer.class)==12);
   if(kind.equals("upgrade")){
    check(before.equals(history(db)));
    var row=db.sql.queryForMap("SELECT * FROM citizen_submission WHERE id=?",id.toString());
    for(var entry:original.entrySet())check(Objects.equals(entry.getValue(),row.get(entry.getKey())));
    check(row.get("first_ack_ms")==null);
    var state=db.sql.queryForMap("SELECT * FROM custody_lifecycle WHERE submission_id=?",id.toString());
    check(state.get("phase").equals("RETIRANDO"));check(state.get("retirement_mode").equals("GARANTIA_INDEPENDIENTE"));check(state.get("retirement_policy_ref").equals(""));
    var legacy=db.sql.queryForMap("SELECT * FROM public_delivery WHERE id=?",id.toString());
    check(legacy.get("first_ack_ms")==null);check(legacy.get("body").equals("SENTINELA_HISTORICA"));check(legacy.get("retirement_mode").equals("GARANTIA_INDEPENDIENTE"));
   } else {
    for(String table:List.of("citizen_submission","public_delivery"))check(db.sql.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class)==0);
   }
   System.out.println(m.writeValueAsString(Map.of("ok",true,"motor","MySQL","escenario",kind,"migraciones",12,"checksumsHistoricosConservados",true,"filasPreviasConservadas",true)));
  }
 }
}
