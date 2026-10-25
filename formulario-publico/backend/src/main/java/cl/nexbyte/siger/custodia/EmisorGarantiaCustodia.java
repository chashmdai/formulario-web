package cl.nexbyte.siger.custodia;
import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.robustez.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Agente separado en el entorno institucional: SELECT local + originales/restauración; solo envía una prueba mínima al público. */
public final class EmisorGarantiaCustodia {
 static Path root(Path value)throws IOException{
  return ArchivoAgente.raiz(value);
 }
 static Map<String,Object> metadata(JdbcTemplate sql,UUID id){
  var rows=sql.queryForList("SELECT i.body,i.receipt,i.document_kind,c.comunicacion_id,c.numero,c.canal,c.content_hash,c.provenance_hash,c.registrada_ms,c.folio_prefix,c.folio_width FROM institutional_inbox i JOIN communication_registry c ON c.source=i.source AND c.operacion_id=i.id WHERE i.source='FORMULARIO_PUBLICO_DDN' AND i.id=?",id.toString());
  if(rows.size()!=1)throw new IllegalArgumentException("COM persistido no encontrado");return rows.getFirst();
 }
 static GarantiaCustodia.Contexto verifyMetadata(Map<String,Object> r,String queue,Map<String,PublicKey> publicKeys,Map<String,PublicKey> institution,JsonMapper mapper){
  var c=GarantiaCustodia.contexto(r.get("body").toString(),r.get("receipt").toString(),queue,publicKeys,institution,mapper);
  var ack=mapper.readValue(r.get("receipt").toString(),RegistroInstitucional.class).registro();
  if(!ack.comunicacionId().toString().equals(r.get("comunicacion_id"))||ack.numeroFolio()!=((Number)r.get("numero")).longValue()||!ack.folio().equals(r.get("folio_prefix")+"-"+String.format(Locale.ROOT,"%0"+((Number)r.get("folio_width")).intValue()+"d",ack.numeroFolio()))||!ack.canalOrigen().equals(r.get("canal"))||!ack.sha256().equals(r.get("content_hash"))||!ack.huellaProcedencia().equals(r.get("provenance_hash"))||ack.registradaEn().toEpochMilli()!=((Number)r.get("registrada_ms")).longValue()||("COMPLETO".equals(queue)!= "FORMULARIO_COMPLETO".equals(r.get("document_kind"))))throw new IllegalArgumentException("Registro y ACK discrepantes");return c;
 }
 static Path original(JdbcTemplate sql,Path root,UUID id,UUID com,AdjuntosFormulario.Descriptor a)throws IOException{
  var rows=sql.queryForList("SELECT name,size_bytes,sha256,media_type,storage_key,origin FROM institution_document WHERE operation_id=? AND attachment_id=? AND communication_id=?",id.toString(),a.id().toString(),com.toString());
  if(rows.size()!=1)throw new IOException("Documento no persistido");var r=rows.getFirst();String key=id+"/"+a.id();
  if(!a.nombre().equals(r.get("name"))||a.tamanio()!=((Number)r.get("size_bytes")).longValue()||!a.sha256().equals(r.get("sha256"))||!a.tipo().equals(r.get("media_type"))||!key.equals(r.get("storage_key"))||!"INGRESO_CIUDADANO".equals(r.get("origin")))throw new IOException("Referencia documental discrepante");
  var p=root.resolve("objects").resolve(id.toString()).resolve(a.id()+".bin");for(var part=p;part!=null&&part.startsWith(root);part=part.getParent())if(Files.isSymbolicLink(part))throw new IOException("Enlace no admitido");
  ArchivoAgente.ruta(p);ArchivoAgente.permisos(p);
  if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)!=a.tamanio()||!AlmacenHash.hash(p).equals(a.sha256()))throw new IOException("Original no recuperable");return p;
 }
 public static FirmaOperativa emitir(JdbcTemplate primary,Path primaryRoot,JdbcTemplate restored,Path restoredRoot,UUID id,String queue,Map<String,PublicKey> publicKeys,Map<String,PublicKey> institution,String issuer,PrivateKey issuerKey,Clock clock,JsonMapper mapper)throws IOException{
  if(ArchivoAgente.windows())throw new IOException("Custodia Windows aún sin independencia/durabilidad acreditadas");
  var a=root(primaryRoot);var b=root(restoredRoot);if(a.startsWith(b)||b.startsWith(a)||Files.isSameFile(a,b)||primary.getDataSource()==restored.getDataSource())throw new IOException("Restauración independiente requerida");
  var p=metadata(primary,id);var r=metadata(restored,id);var context=verifyMetadata(p,queue,publicKeys,institution,mapper);
  if(!id.equals(context.id())||!context.equals(verifyMetadata(r,queue,publicKeys,institution,mapper)))throw new IOException("Restauración de contenido completo discrepante");
  if(queue.equals("COMPLETO")) {
   var e=mapper.readValue(p.get("body").toString(),EntregaFormulario.class);
   if(primary.queryForObject("SELECT COUNT(*) FROM institution_document WHERE operation_id=? AND origin='INGRESO_CIUDADANO'",Integer.class,id.toString())!=e.comunicacion().adjuntos().size()||restored.queryForObject("SELECT COUNT(*) FROM institution_document WHERE operation_id=? AND origin='INGRESO_CIUDADANO'",Integer.class,id.toString())!=e.comunicacion().adjuntos().size())throw new IOException("Manifiesto incompleto");
   for(var d:e.comunicacion().adjuntos()){var x=original(primary,a,id,context.comunicacionId(),d);var y=original(restored,b,id,context.comunicacionId(),d);if(Files.isSameFile(x,y))throw new IOException("Copias no independientes");}
  }
  // Revalidar el registro después de las lecturas; ninguna transacción provisional acredita custodia.
  if(!context.equals(verifyMetadata(metadata(primary,id),queue,publicKeys,institution,mapper))||!context.equals(verifyMetadata(metadata(restored,id),queue,publicKeys,institution,mapper)))throw new IOException("Cambio concurrente en archivo");
  return FirmaOperativa.firmar(new GarantiaCustodia.Contenido(context,clock.instant(),clock.instant().plus(Duration.ofHours(24)),UUID.randomUUID()),GarantiaCustodia.DOMAIN,issuer,issuerKey,mapper);
 }
 private static final class AlmacenHash {static String hash(Path p)throws IOException{return ArchivoAgente.huella(p);}}
 private static String required(Properties p,String k){String v=p.getProperty(k);if(v==null||v.isBlank())throw new IllegalArgumentException("Configuración institucional incompleta");return v;}
 static String localUrl(Properties p,String prefix){
  String u=required(p,prefix+".url");if(!u.startsWith("jdbc:mysql://"))throw new IllegalArgumentException("MySQL local con TLS verificado requerido");var uri=URI.create(u.substring(5));
  if(uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getHost()==null||!Set.of("127.0.0.1","localhost","[::1]").contains(uri.getHost())||uri.getRawQuery()==null)throw new IllegalArgumentException("MySQL local requerido");
  var values=new HashMap<String,String>();var allowed=Set.of("sslMode","connectTimeout","socketTimeout","trustCertificateKeyStoreUrl","trustCertificateKeyStoreType","trustCertificateKeyStorePassword","fallbackToSystemTrustStore");
  for(String pair:uri.getRawQuery().split("&")){var parts=pair.split("=",2);if(parts.length!=2)throw new IllegalArgumentException("Parámetros TLS no ambiguos requeridos");String k=URLDecoder.decode(parts[0],java.nio.charset.StandardCharsets.UTF_8),v=URLDecoder.decode(parts[1],java.nio.charset.StandardCharsets.UTF_8);if(!allowed.contains(k)||values.putIfAbsent(k,v)!=null)throw new IllegalArgumentException("Parámetro JDBC no admitido o repetido");}
  if(!"VERIFY_IDENTITY".equals(values.get("sslMode")))throw new IllegalArgumentException("VERIFY_IDENTITY obligatorio");return u;
 }

 public static void main(String[] args){try{run(args);}catch(Exception invalid){System.err.println("Garantía no emitida: revisar custodia, COM, restauración, configuración y conectividad privadas. No se imprimen payloads ni excepciones SQL.");System.exit(1);}}
 static void run(String[] args)throws Exception{
  Map<String,String> opts=new HashMap<>();for(int i=0;i<args.length;i++){String k=args[i];if(!Set.of("--config","--enviar","--id","--cola").contains(k)||opts.containsKey(k))throw new IllegalArgumentException();opts.put(k,k.equals("--enviar")?"true":args[++i]);}
  var p=new Properties();try(var reader=Files.newBufferedReader(Path.of(opts.get("--config")))){p.load(reader);}
  ejecutar(p,opts);
 }
 static void ejecutar(Properties p,Map<String,String> opts)throws Exception{
  if(ArchivoAgente.windows()&&opts.containsKey("--enviar"))throw new IOException("Emisión Windows sin custodia/durabilidad acreditadas; sin conexión pública");
  String sourceUrl=localUrl(p,"garantia.origen.db");boolean automatic=p.containsKey("garantia.respaldo.directorio");String restoreUrl=automatic?null:localUrl(p,"garantia.restauracion.db");
  var originRoot=root(Path.of(required(p,"garantia.origen.archivo")));Path backupRoot=null,scratch=null;
  if(automatic){backupRoot=root(Path.of(required(p,"garantia.respaldo.directorio")));scratch=root(Path.of(required(p,"garantia.restauracion.scratch")));var fs=Files.getFileStore(backupRoot);if(fs.equals(Files.getFileStore(originRoot))||Set.of("tmpfs","ramfs","overlay").contains(fs.type())||fs.isReadOnly())throw new IOException("Respaldo durable independiente y escribible requerido");}
  else {var restoreRoot=root(Path.of(required(p,"garantia.restauracion.archivo")));if(!Files.getFileStore(restoreRoot).isReadOnly()||Files.getFileStore(originRoot).equals(Files.getFileStore(restoreRoot))||sourceUrl.split("\\?")[0].equals(restoreUrl.split("\\?")[0]))throw new IOException("Restauración independiente requerida");}
  var mapper=JsonMapper.builder().build();var publicKeys=JdbcRecepcionConfiguration.publicKeys(mapper,Path.of(required(p,"garantia.public-keys")));var institution=JdbcRecepcionConfiguration.publicKeys(mapper,Path.of(required(p,"garantia.institution-keys")));
  var privateKey=ComprobanteDigital.privatePem(Files.readString(Path.of(required(p,"garantia.private-key"))));String keyId=required(p,"garantia.key-id");
  try(var source=new BaseJdbc(sourceUrl,required(p,"garantia.origen.db.user"),required(p,"garantia.origen.db.password"),2);var restored=automatic?null:new BaseJdbc(restoreUrl,required(p,"garantia.restauracion.db.user"),required(p,"garantia.restauracion.db.password"),2)){
   // Cuentas SELECT del agente; ningún migrate/update ni acceso a la BD pública.
   List<Map<String,Object>> candidates;
   var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();URI origin=null;String token=null;
   if(opts.containsKey("--enviar")){
    origin=URI.create(required(p,"garantia.public.origin"));if(!"https".equals(origin.getScheme())||origin.getHost()==null||origin.getUserInfo()!=null||origin.getQuery()!=null||origin.getFragment()!=null||!Set.of("","/").contains(origin.getPath()))throw new IllegalArgumentException("Origen HTTPS exacto requerido");token=required(p,"garantia.public.integration-token");
    var get=HttpRequest.newBuilder(origin.resolve("/integracion/v3/custodia/candidatos?limite=20")).timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+token).GET().build();
    var response=client.send(get,HttpResponse.BodyHandlers.ofInputStream());byte[] data;try(var in=response.body()){data=in.readNBytes(65537);}if(response.statusCode()!=200||data.length>65536)throw new IOException("No se obtuvo lote de candidatos");
    var node=mapper.readTree(data);if(!node.isArray()||node.size()>20)throw new IOException("Lote inválido");candidates=new ArrayList<>();for(var n:node)candidates.add(Map.of("id",n.path("id").asString(),"cola",n.path("cola").asString()));
   }else candidates=List.of(Map.of("id",opts.get("--id"),"cola",opts.getOrDefault("--cola","COMPLETO")));
   int sent=0,blocked=0;
   for(var c:candidates)try{
    var proof=automatic?RespaldoIngresoVerificado.emitir(source.sql,originRoot,backupRoot,scratch,UUID.fromString(c.get("id").toString()),c.get("cola").toString(),publicKeys,institution,keyId,privateKey,Clock.systemUTC(),mapper):emitir(source.sql,originRoot,restored.sql,Path.of(required(p,"garantia.restauracion.archivo")),UUID.fromString(c.get("id").toString()),c.get("cola").toString(),publicKeys,institution,keyId,privateKey,Clock.systemUTC(),mapper);
    if(origin==null)System.out.println(mapper.writeValueAsString(proof));else{
     var request=HttpRequest.newBuilder(origin.resolve("/integracion/v3/custodia/garantias")).timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(proof))).build();
     var response=client.send(request,HttpResponse.BodyHandlers.discarding());if(response.statusCode()!=204)throw new IOException("Garantía no aceptada");
    }sent++;
   }catch(RuntimeException|IOException unavailable){blocked++;}
   System.err.println("Garantías emitidas="+sent+" conservadas_sin_prueba="+blocked);if(blocked>0)throw new IOException("Se conservaron candidatos sin prueba suficiente");
  }
 }
}