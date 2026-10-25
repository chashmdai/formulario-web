package cl.nexbyte.siger.custodia;
import cl.nexbyte.siger.formulario.*;import cl.nexbyte.siger.robustez.*;
import java.io.*;import java.nio.file.*;import java.nio.file.attribute.*;import java.security.*;import java.time.*;import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;import tools.jackson.databind.json.JsonMapper;
/** Fixtures MySQL80 locales: ejecutor QA explícito distinto del agente de solo lectura. */
public final class PruebaMysqlNativo {
 static void check(boolean value){if(!value)throw new AssertionError();} static Path dir(Path p)throws IOException{ArchivoAgente.directorio(p);return p;}
 static String pem(PublicKey key){return "-----BEGIN PUBLIC KEY-----\n"+Base64.getEncoder().encodeToString(key.getEncoded())+"\n-----END PUBLIC KEY-----\n";}
 public static void main(String[] args)throws Exception{
  if(!args[1].startsWith("jdbc:mysql://localhost:3306/siger_custodia_lab?"))throw new IllegalArgumentException("Solo laboratorio explícito");
  var credential=MysqlTlsLab.secretos();try(var connection=MysqlTlsLab.connect(args[1],credential)){
   if(!"siger_custodia_lab".equals(connection.getCatalog()))throw new IllegalArgumentException();
   var sql=new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));
   if(args[0].equals("preparar")){
    int existing=sql.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('institutional_inbox','communication_registry','institution_document')",Integer.class);
    if(existing==0){String ddl=Files.readString(Path.of(args[4])).replaceAll("(?m)^--.*$","");for(String statement:ddl.split(";"))if(!statement.isBlank())sql.execute(statement);}
    else if(existing!=3)throw new IOException("Esquema parcial; no modificar");
    Path base=ArchivoAgente.raiz(Path.of(args[2]));Path work=dir(base.resolve("mysql-"+UUID.randomUUID())),source=dir(work.resolve("origen")),backup=dir(work.resolve("respaldo")),scratch=dir(work.resolve("scratch"));
    connection.setAutoCommit(false);
        UUID id=UUID.randomUUID(),com=UUID.randomUUID(),attachment=UUID.randomUUID();byte[] bytes="EVIDENCIA EXCLUSIVAMENTE SINTETICA SIN PERSONAS".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var object=dir(dir(source.resolve("objects")).resolve(id.toString())).resolve(attachment+".bin");ArchivoAgente.escribir(object,bytes);check(Files.getFileAttributeView(object,AclFileAttributeView.class).getAcl().size()>=1);
        var publicKey=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();var inst=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();var issuer=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();var mapper=JsonMapper.builder().build();var now=Instant.now();var clock=Clock.fixed(now,ZoneOffset.UTC);
        var descriptor=new AdjuntosFormulario.Descriptor(attachment,"sintetico.txt",bytes.length,ArchivoAgente.huella(object),"text/plain","VALIDADO");
        String version=args[3];if(!Set.of("1.0","1.1").contains(version))throw new IllegalArgumentException();var content=new EntregaFormulario.Contenido(version,id,UUID.randomUUID(),IdentificacionCiudadana.Tipo.NNA,now,"QA_WINDOWS",false,new DatosFormulario(Map.of("nna-story","PRUEBA SINTETICA"),List.of(),List.of(),List.of(attachment)),List.of(),List.of(descriptor));
        String sha=CanonFormulario.huella("CONTENIDO",content,mapper),provenance=EntregaFormulario.procedencia(content,mapper);
        var receipt=ComprobanteDigital.firmar(new ComprobanteDigital.Contenido("1.0",id,now,"CUSTODIA_PUBLICA",sha,provenance,"FORMULARIO_PUBLICO_DDN","QA_WINDOWS"),"sintetico-publico",publicKey.getPrivate(),mapper);var document=new EntregaFormulario(content,sha,provenance,receipt);
        var registered=new ComprobanteInstitucional.Contenido("1.1","SIGER_DDN","COMUNICACION_REGISTRADA",id,com,1,"COM-000001",now,"FORMULARIO_WEB_NNA",sha,provenance);var ack=new RegistroInstitucional(registered,ComprobanteInstitucional.firmar(registered,"sintetico-institucion",inst.getPrivate(),mapper));
        var db=new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));db.update("INSERT INTO institutional_inbox VALUES(?,?,?,?,?)",id.toString(),"FORMULARIO_PUBLICO_DDN",mapper.writeValueAsString(document),mapper.writeValueAsString(ack),"FORMULARIO_COMPLETO");db.update("INSERT INTO communication_registry VALUES(?,?,?,?,?,?,?,?,?,?)","FORMULARIO_PUBLICO_DDN",id.toString(),com.toString(),1,"FORMULARIO_WEB_NNA",sha,provenance,now.toEpochMilli(),"COM",6);db.update("INSERT INTO institution_document VALUES(?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),id.toString(),com.toString(),attachment.toString(),descriptor.nombre(),bytes.length,descriptor.sha256(),descriptor.tipo(),id+"/"+attachment,"INGRESO_CIUDADANO");
        ArchivoAgente.escribir(work.resolve("public-keys.json"),mapper.writeValueAsBytes(Map.of("sintetico-publico",pem(publicKey.getPublic()))));ArchivoAgente.escribir(work.resolve("institution-keys.json"),mapper.writeValueAsBytes(Map.of("sintetico-institucion",pem(inst.getPublic()))));
        Map<String,PublicKey> pub=Map.of("sintetico-publico",publicKey.getPublic()),institution=Map.of("sintetico-institucion",inst.getPublic());

    connection.commit();connection.setAutoCommit(true);
    var props=new Properties();props.setProperty("garantia.modo","LABORATORIO");props.setProperty("garantia.origen.db.url",args[1]);props.setProperty("garantia.origen.archivo",source.toString());props.setProperty("garantia.respaldo.directorio",backup.toString());props.setProperty("garantia.restauracion.scratch",scratch.toString());props.setProperty("garantia.public-keys",work.resolve("public-keys.json").toString());props.setProperty("garantia.institution-keys",work.resolve("institution-keys.json").toString());
    var config=new StringWriter();props.store(config,"Solo fixture sintetico");ArchivoAgente.escribir(work.resolve("agente.properties"),config.toString().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
    Files.writeString(base.resolve("ultima-prueba-"+version+".json"),mapper.writeValueAsString(Map.of("id",id.toString(),"work",work.toString(),"config",work.resolve("agente.properties").toString())));
    var result=RespaldoIngresoVerificado.ensayar(db,source,backup,scratch,id,"COMPLETO",pub,institution,mapper);
    if(result.independenciaAcreditada()||!result.durabilidadAcreditada())throw new AssertionError();
    var packet=backup.resolve("completo-"+id);var stamp=Files.getLastModifiedTime(packet.resolve("registro.json"));
    RespaldoIngresoVerificado.ensayar(db,source,backup,scratch,id,"COMPLETO",pub,institution,mapper);
    if(!stamp.equals(Files.getLastModifiedTime(packet.resolve("registro.json"))))throw new AssertionError();
    var backupFile=packet.resolve("objects").resolve(id.toString()).resolve(attachment+".bin");Files.setAttribute(backupFile,"dos:readonly",false);Files.writeString(backupFile,"CORRUPCION SINTETICA");
    boolean rejected=false;try{RespaldoIngresoVerificado.ensayar(db,source,backup,scratch,id,"COMPLETO",pub,institution,mapper);}catch(IOException e){rejected=true;}if(!rejected)throw new AssertionError();Files.write(backupFile,bytes);Files.setAttribute(backupFile,"dos:readonly",true);
    RespaldoIngresoVerificado.ensayar(db,source,backup,scratch,id,"COMPLETO",pub,institution,mapper);
    try(var readOnly=new BaseJdbc(args[1],credential.usuario(),credential.password(),1,true)){
     if(!sha.equals(EmisorGarantiaCustodia.metadata(readOnly.sql,id).get("content_hash")))throw new AssertionError();
     if(readOnly.sql.queryForObject("SELECT @@session.transaction_read_only",Integer.class)!=1)throw new AssertionError("Motor sin sesión read-only");for(String statement:List.of("UPDATE institutional_inbox SET source=source WHERE 1=0","DELETE FROM institutional_inbox WHERE 1=0","INSERT INTO institutional_inbox SELECT * FROM institutional_inbox WHERE 1=0")){
      boolean denied=false;try{readOnly.sql.execute(statement);}catch(org.springframework.dao.DataAccessException e){for(Throwable t=e;t!=null;t=t.getCause())if(t instanceof java.sql.SQLException q&&Set.of("25006","S1009").contains(q.getSQLState()))denied=true;}if(!denied)throw new AssertionError("Pool agente no bloqueó DML");
     }
    }
    sql.execute("SET SESSION TRANSACTION READ ONLY");
    boolean writeRejected=false;try{sql.update("UPDATE institutional_inbox SET source=source WHERE id=?",id.toString());}catch(org.springframework.dao.DataAccessException e){writeRejected=true;}if(!writeRejected)throw new AssertionError("El motor no bloqueó escritura");
    if(!sha.equals(EmisorGarantiaCustodia.metadata(db,id).get("content_hash")))throw new AssertionError();
    System.out.println("MYSQL_NATIVE_FIXTURE_OK version="+version+" transaccion=true integridad=true restauracion=true idempotencia=true corrupcionRechazada=true escrituraRechazada=true");
   }else if(args[0].equals("recuperar")){
    // Reconstruye desde paquete a tablas TEMPORARY de la sesión; no muta registros persistidos.
    var mapper=JsonMapper.builder().build();Path work=ArchivoAgente.raiz(Path.of(args[2]));Path packet;try(var entries=Files.list(work.resolve("respaldo"))){packet=entries.filter(p->p.getFileName().toString().startsWith("completo-")).findFirst().orElseThrow();}
    var saved=mapper.readValue(Files.readString(packet.resolve("registro.json")),RespaldoIngresoVerificado.Paquete.class);var m=saved.registro();
    var pub=JdbcRecepcionConfiguration.publicKeys(mapper,work.resolve("public-keys.json"));var inst=JdbcRecepcionConfiguration.publicKeys(mapper,work.resolve("institution-keys.json"));var context=EmisorGarantiaCustodia.verifyMetadata(m,"COMPLETO",pub,inst,mapper);
    String ddl=Files.readString(Path.of(args[3])).replaceAll("(?m)^--.*$","");for(String statement:ddl.split(";"))if(statement.trim().startsWith("CREATE TABLE"))sql.execute(statement.trim().replaceFirst("CREATE TABLE","CREATE TEMPORARY TABLE"));
    sql.update("INSERT INTO institutional_inbox VALUES(?,?,?,?,?)",context.id().toString(),"FORMULARIO_PUBLICO_DDN",m.get("body"),m.get("receipt"),m.get("document_kind"));
    sql.update("INSERT INTO communication_registry VALUES(?,?,?,?,?,?,?,?,?,?)","FORMULARIO_PUBLICO_DDN",context.id().toString(),m.get("comunicacion_id"),m.get("numero"),m.get("canal"),m.get("content_hash"),m.get("provenance_hash"),m.get("registrada_ms"),m.get("folio_prefix"),m.get("folio_width"));
    for(var d:saved.documentos())sql.update("INSERT INTO institution_document VALUES(?,?,?,?,?,?,?,?,?,?)",d.get("id"),d.get("operation_id"),d.get("communication_id"),d.get("attachment_id"),d.get("name"),d.get("size_bytes"),d.get("sha256"),d.get("media_type"),d.get("storage_key"),d.get("origin"));
    Path online=work.resolve("origen/objects"),offline=work.resolve("origen/objects-offline-qa");
    Files.move(online,offline);try{
     if(!context.equals(RespaldoIngresoVerificado.ensayar(sql,work.resolve("origen"),work.resolve("respaldo"),work.resolve("scratch"),context.id(),"COMPLETO",pub,inst,mapper).contexto()))throw new AssertionError();
    }finally{Files.move(offline,online);}
    System.out.println("MYSQL_NATIVE_RESTAURACION_NUEVO_PROCESO_OK tablasTemporales=true originalesOrigenDesconectados=true independenciaFisica=false");
   }else throw new IllegalArgumentException();
  }
 }
}
