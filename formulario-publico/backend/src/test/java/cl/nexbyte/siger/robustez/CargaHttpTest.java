package cl.nexbyte.siger.robustez;
import cl.nexbyte.siger.identidad.*;import cl.nexbyte.siger.entrega.RecepcionPublica;
import jakarta.servlet.http.*;import java.net.*;import java.net.http.*;import java.nio.file.*;import java.security.*;import java.time.*;import java.util.*;import java.util.concurrent.*;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;import org.springframework.context.annotation.*;import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.SecurityFilterChain;import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.web.bind.annotation.*;import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
/** Identidad sintética únicamente en test; ninguna ruta de esta configuración llega al JAR. */
@EnabledIfSystemProperty(named="carga.real",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"siger.claveunica.client-id=test-carga","siger.claveunica.client-secret=test-carga-secret","server.address=127.0.0.1","server.servlet.session.cookie.secure=false"})
@Import(CargaHttpTest.Fixture.class)
class CargaHttpTest {
 @LocalServerPort int port;@org.springframework.beans.factory.annotation.Autowired RecepcionJdbc store;@org.springframework.beans.factory.annotation.Autowired BaseJdbc db;
 static final IdentidadVerificada ID=new IdentidadVerificada("44444444-4","Ciudadana","Carga");static final String NOTICE="laboratorio-v1";
 final JsonMapper mapper=JsonMapper.builder().build();
 @TestConfiguration static class Fixture {
  @Bean(destroyMethod="close") BaseJdbc testDb()throws Exception{return TestDatabase.open(Files.createTempDirectory("siger-carga-"));}
  @Bean @Primary RecepcionJdbc testStore(BaseJdbc db)throws Exception{
   var keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();var controls=new ControlesOperacion(db);controls.cambiar(controls.estado().revision(),true,true,"NORMAL","PRUEBA_LOCAL");
   return new RecepcionJdbc(db,JsonMapper.builder().build(),NOTICE,true,"carga-v1",keys.getPrivate(),Map.of("carga-v1",keys.getPublic()),Clock.systemUTC(),300000);
  }
  @Bean @Order(-1) SecurityFilterChain fixtureChain(HttpSecurity http)throws Exception{return http.securityMatcher("/__test/session").authorizeHttpRequests(a->a.anyRequest().permitAll()).build();}
  @Bean LoginFixture fixtureController(){return new LoginFixture();}
 }
 @RestController static class LoginFixture {
  @GetMapping("/__test/session") String session(HttpServletRequest request){
   if(!Set.of("127.0.0.1","0:0:0:0:0:0:0:1").contains(request.getRemoteAddr()))throw new IllegalArgumentException();
   var auth=UsernamePasswordAuthenticationToken.authenticated(ID,null,List.of(new SimpleGrantedAuthority("ROLE_CIUDADANO")));auth.setDetails(EvidenciaAutenticacion.de(ID));
   var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(auth);request.getSession().setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);return "test-only";
  }
 }
 @Test void midePersistenciaHttpConcurrenciaYValidaciones()throws Exception{
  int count=Integer.getInteger("carga.registros",1000);var results=new LinkedHashMap<String,Object>();
  for(int i=0;i<30;i++)store.recibirVerificada(UUID.randomUUID(),ID,EvidenciaAutenticacion.de(ID),"Calentamiento",NOTICE);
  results.put("persistencia16",run(count,16,i->{store.recibirVerificada(UUID.randomUUID(),ID,EvidenciaAutenticacion.de(ID),"Carga sintética "+i,NOTICE);return 202;}));
  var cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
  try(var client=HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(3)).build()){
   assertThat(client.send(request("/__test/session").GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
   var csrfResponse=client.send(request("/api/csrf").GET().build(),HttpResponse.BodyHandlers.ofString());var csrf=mapper.readTree(csrfResponse.body());var header=csrf.get("headerName").asString();var token=csrf.get("token").asString();
   results.put("http64",httpPhase(client,count,64,header,token));
   var same=UUID.randomUUID();results.put("duplicados64",run(200,64,i->post(client,same,"Mismo ingreso HTTP",header,token).statusCode()));
   assertThat(db.sql.queryForObject("SELECT COUNT(*) FROM public_delivery WHERE id=?",Long.class,same.toString())).isEqualTo(1);
   assertThat(post(client,same,"Manipulación del mismo ingreso",header,token).statusCode()).isEqualTo(409);
   var invalid=client.send(request("/api/comunicaciones").header("Content-Type","application/json").header(header,token).header("Idempotency-Key",UUID.randomUUID().toString()).POST(HttpRequest.BodyPublishers.ofString("{\"mensaje\":\"uno\",\"mensaje\":\"dos\",\"avisoPrivacidadLeido\":true,\"avisoVersion\":\"laboratorio-v1\"}")).build(),HttpResponse.BodyHandlers.discarding());assertThat(invalid.statusCode()).isEqualTo(400);
   var large=client.send(request("/api/comunicaciones").header("Content-Type","application/json").header(header,token).header("Idempotency-Key",UUID.randomUUID().toString()).POST(HttpRequest.BodyPublishers.ofString("x".repeat(9000))).build(),HttpResponse.BodyHandlers.discarding());assertThat(large.statusCode()).isEqualTo(413);
   assertThat(post(client,UUID.randomUUID(),"Sin CSRF",header,"invalido").statusCode()).isEqualTo(403);
   results.put("http256",httpPhase(client,1000,256,header,token));
  }
  results.put("base",System.getenv("ROBUST_DB_URL")==null?"H2-file":"MySQL 8.4.11 loopback, fsync commit, pool12");results.put("at",Instant.now().toString());results.put("cpus",Runtime.getRuntime().availableProcessors());results.put("java",System.getProperty("java.version"));
  Files.writeString(Path.of("target/carga-http.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(results));
 }
 HttpRequest.Builder request(String path){return HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20));}
 HttpResponse<String> post(HttpClient client,UUID key,String text,String header,String token)throws Exception{
  return client.send(request("/api/comunicaciones").header("Content-Type","application/json").header("Idempotency-Key",key.toString()).header(header,token).POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(Map.of("mensaje",text,"avisoPrivacidadLeido",true,"avisoVersion",NOTICE)))).build(),HttpResponse.BodyHandlers.ofString());
 }
 interface Work{int execute(int i)throws Exception;}
 Map<String,Object> httpPhase(HttpClient client,int count,int concurrency,String header,String token)throws Exception{
  var ids=new UUID[count];for(int i=0;i<count;i++)ids[i]=UUID.randomUUID();
  var result=run(count,concurrency,i->post(client,ids[i],"Carga HTTP "+i,header,token).statusCode());
  int retried=0;for(int round=0;round<6;round++){
   var persisted=new HashSet<>(db.sql.queryForList("SELECT id FROM public_delivery",String.class));var missing=new ArrayList<Integer>();
   for(int i=0;i<count;i++)if(!persisted.contains(ids[i].toString()))missing.add(i);
   if(missing.isEmpty()){result.put("reintentos",retried);return result;}
   retried+=missing.size();Thread.sleep(200);run(missing.size(),16,i->{int index=missing.get(i);return post(client,ids[index],"Carga HTTP "+index,header,token).statusCode();});
  }
  throw new AssertionError("Quedaron solicitudes sin confirmar tras reintentos acotados");
 }
 Map<String,Object> run(int count,int concurrency,Work work)throws Exception{
  var gate=new CountDownLatch(1);var statuses=new ConcurrentHashMap<Integer,java.util.concurrent.atomic.LongAdder>();var times=new long[count];long start;
  try(var threads=Executors.newFixedThreadPool(concurrency)){
   var tasks=new ArrayList<Future<?>>();for(int i=0;i<count;i++){final int index=i;tasks.add(threads.submit(()->{gate.await();var t=System.nanoTime();int status;try{status=work.execute(index);}catch(java.io.IOException e){status=0;}times[index]=System.nanoTime()-t;statuses.computeIfAbsent(status,k->new java.util.concurrent.atomic.LongAdder()).increment();return null;}));}
   start=System.nanoTime();gate.countDown();for(var task:tasks)task.get(120,TimeUnit.SECONDS);
  }
  var seconds=(System.nanoTime()-start)/1e9;Arrays.sort(times);var summary=new TreeMap<String,Object>();summary.put("registros",count);summary.put("concurrencia",concurrency);summary.put("segundos",seconds);summary.put("solicitudesPorSegundo",count/seconds);summary.put("p50ms",times[count/2]/1e6);summary.put("p95ms",times[Math.max(0,(int)(count*.95)-1)]/1e6);summary.put("p99ms",times[Math.max(0,(int)(count*.99)-1)]/1e6);var codes=new TreeMap<String,Long>();statuses.forEach((k,v)->codes.put(k.toString(),v.sum()));summary.put("estados",codes);assertThat(codes.keySet()).allMatch(k->Set.of("0","202","429","503").contains(k));return summary;
 }
}
