package cl.nexbyte.siger.custodia;
import com.sun.net.httpserver.*;import javax.net.ssl.*;import java.net.*;import java.net.http.*;import java.nio.file.*;import java.security.*;import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import tools.jackson.databind.json.JsonMapper;import cl.nexbyte.siger.robustez.JdbcRecepcionConfiguration;
/** Receptor HTTPS EFÍMERO y criptografía sintética: nunca invoca Emisor.enviar ni un Formulario real. */
public final class ProtocoloHttpsLab {
 public static void main(String[] args)throws Exception{
  Path work=ArchivoAgente.raiz(Path.of(args[0]));var mapper=JsonMapper.builder().build();Path packet;try(var entries=Files.list(work.resolve("respaldo"))){packet=entries.filter(p->p.getFileName().toString().startsWith("completo-")).findFirst().orElseThrow();}
  var saved=mapper.readValue(Files.readString(packet.resolve("registro.json")),RespaldoIngresoVerificado.Paquete.class);
  var pub=JdbcRecepcionConfiguration.publicKeys(mapper,work.resolve("public-keys.json"));var inst=JdbcRecepcionConfiguration.publicKeys(mapper,work.resolve("institution-keys.json"));
  var expected=EmisorGarantiaCustodia.verifyMetadata(saved.registro(),"COMPLETO",pub,inst,mapper);
  var keys=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(Path.of(args[1]))){keys.load(in,"synthetic-lab".toCharArray());}
  var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,"synthetic-lab".toCharArray());var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(keys);
  var tls=SSLContext.getInstance("TLS");tls.init(km.getKeyManagers(),tm.getTrustManagers(),null);
  var issuer=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();var trusted=Map.of("qa-no-institucional",issuer.getPublic());var clock=Clock.systemUTC();
  var content=new GarantiaCustodia.Contenido(expected,clock.instant(),clock.instant().plusSeconds(600),UUID.randomUUID());var proof=FirmaOperativa.firmar(content,GarantiaCustodia.DOMAIN,"qa-no-institucional",issuer.getPrivate(),mapper);
  var stored=new AtomicReference<String>();var retries=new AtomicInteger();var validated=new AtomicInteger();
  var server=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setHttpsConfigurator(new HttpsConfigurator(tls));
  try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
   server.setExecutor(executor);server.createContext("/integracion/v3/custodia/",exchange->{
    try(exchange){
     if(!"Bearer qa-solo-efimero".equals(exchange.getRequestHeaders().getFirst("Authorization"))){exchange.sendResponseHeaders(401,-1);return;}
     if(exchange.getRequestURI().getPath().endsWith("candidatos")){byte[] data=mapper.writeValueAsBytes(List.of(Map.of("id",expected.id(),"cola","COMPLETO")));exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,data.length);exchange.getResponseBody().write(data);return;}
     if(!exchange.getRequestURI().getPath().endsWith("garantias")||!"POST".equals(exchange.getRequestMethod())){exchange.sendResponseHeaders(404,-1);return;}
     if(retries.getAndIncrement()==0){exchange.sendResponseHeaders(503,-1);return;}
     byte[] body=exchange.getRequestBody().readNBytes(65537);if(body.length>65536){exchange.sendResponseHeaders(413,-1);return;}
     try{var sealed=mapper.readValue(body,FirmaOperativa.class);var decoded=GarantiaCustodia.validar(sealed,trusted,clock,mapper);if(!decoded.contexto().equals(expected))throw new IllegalArgumentException();String digest=sealed.payload();String old=stored.compareAndExchange(null,digest);if(old!=null&&!old.equals(digest))throw new IllegalArgumentException();validated.incrementAndGet();exchange.sendResponseHeaders(204,-1);}catch(RuntimeException bad){exchange.sendResponseHeaders(400,-1);}
    }catch(Exception ignored){}
   });
   server.createContext("/lento",ex->{try{Thread.sleep(250);ex.sendResponseHeaders(204,-1);}catch(Exception ignored){}finally{ex.close();}});
   server.start();URI origin=URI.create("https://localhost:"+server.getAddress().getPort());var client=HttpClient.newBuilder().sslContext(tls).connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
   int passed=0;
   var get=HttpRequest.newBuilder(origin.resolve("/integracion/v3/custodia/candidatos?limite=20")).header("Authorization","Bearer qa-solo-efimero").GET().build();var batch=client.send(get,HttpResponse.BodyHandlers.ofString());if(batch.statusCode()!=200||!mapper.readTree(batch.body()).get(0).get("id").asString().equals(expected.id().toString()))throw new AssertionError();passed++;
   byte[] sealed=mapper.writeValueAsBytes(proof);
   var post=HttpRequest.newBuilder(origin.resolve("/integracion/v3/custodia/garantias")).header("Authorization","Bearer qa-solo-efimero").header("Content-Type","application/json").timeout(Duration.ofSeconds(2)).POST(HttpRequest.BodyPublishers.ofByteArray(sealed)).build();
   if(client.send(post,HttpResponse.BodyHandlers.discarding()).statusCode()!=503)throw new AssertionError();passed++;
   for(int n=0;n<2;n++){if(client.send(post,HttpResponse.BodyHandlers.discarding()).statusCode()!=204)throw new AssertionError();passed++;}
   if(validated.get()!=2||stored.get()==null)throw new AssertionError();passed++;
   var invalid=FirmaOperativa.firmar(content,GarantiaCustodia.DOMAIN,"qa-no-institucional",KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPrivate(),mapper);
   var bad=HttpRequest.newBuilder(post.uri()).header("Authorization","Bearer qa-solo-efimero").POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(invalid))).build();if(client.send(bad,HttpResponse.BodyHandlers.discarding()).statusCode()!=400)throw new AssertionError();passed++;
   var unauth=HttpRequest.newBuilder(get.uri()).GET().build();if(client.send(unauth,HttpResponse.BodyHandlers.discarding()).statusCode()!=401)throw new AssertionError();passed++;
   try{HttpClient.newHttpClient().send(get,HttpResponse.BodyHandlers.discarding());throw new AssertionError();}catch(SSLHandshakeException expectedFailure){passed++;}
   try{client.send(HttpRequest.newBuilder(origin.resolve("/lento")).timeout(Duration.ofMillis(30)).GET().build(),HttpResponse.BodyHandlers.discarding());throw new AssertionError();}catch(HttpTimeoutException expectedFailure){passed++;}
   System.out.println("HTTPS_PROTOCOLO_SINTETICO_OK verificaciones="+passed+" candidatos=true ackVerificado=true garantiaEd25519=true reintento503=true duplicadoIdempotente=true timeout=true receptorReal=false emisionOperativa=false");
  }finally{server.stop(0);}
 }
}
