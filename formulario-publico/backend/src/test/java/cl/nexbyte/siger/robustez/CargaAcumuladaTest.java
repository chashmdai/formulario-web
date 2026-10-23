package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.identidad.*;
import jakarta.servlet.*;import jakarta.servlet.http.*;
import java.nio.file.*;import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;import org.springframework.context.annotation.*;
import org.springframework.test.context.*;import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/** Laboratorio explícito: Tomcat público real + otro JVM SIGER real, dos MySQL de pruebas y HTTP loopback. */
@EnabledIfSystemProperty(named="carga.acumulada",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.profiles.active=jdbc-reception","siger.claveunica.client-id=test-acumuladas","siger.claveunica.client-secret=test-acumuladas",
    "server.address=127.0.0.1","datos.mysql-local=true","publico.integracion.http-local=true",
    "publico.recepcion.habilitada=true","publico.recepcion.aviso-validado=true","publico.recepcion.aviso-version=laboratorio-v1"})
@Import(CargaAcumuladaTest.PerderUnAck.class)
class CargaAcumuladaTest {
    @LocalServerPort int port;
    @Autowired RecepcionJdbc store;@Autowired BaseJdbc db;@Autowired JsonMapper mapper;
    static final AtomicBoolean DROP=new AtomicBoolean(true);
    @DynamicPropertySource static void config(DynamicPropertyRegistry r){
        var url=System.getenv("ROBUST_DB_URL");if(url==null||!url.startsWith("jdbc:mysql://127.0.0.1:13306/robust_public_test?"))throw new IllegalStateException("Solo MySQL de pruebas");
        r.add("publico.db.url",()->url);r.add("publico.db.user",()->System.getenv("ROBUST_DB_USER"));r.add("publico.db.password",()->System.getenv("ROBUST_DB_PASSWORD"));
        r.add("publico.comprobante.key-id",()->"local-ed25519-v1");
        r.add("publico.comprobante.private-key",()->System.getenv("BATCH_PUBLIC_PRIVATE"));r.add("publico.comprobante.trusted-keys",()->System.getenv("BATCH_PUBLIC_TRUST"));
        r.add("publico.comprobante.institutional-trusted-keys",()->System.getenv("BATCH_INSTITUTION_TRUST"));
        r.add("publico.integracion.token",()->System.getenv("BATCH_INTEGRATION_TOKEN"));
    }
    @TestConfiguration static class PerderUnAck {
        @Bean OncePerRequestFilter ackPerdido(){return new OncePerRequestFilter(){
            protected void doFilterInternal(HttpServletRequest q,HttpServletResponse r,FilterChain chain)throws java.io.IOException,ServletException{
                if(!q.getRequestURI().matches("/integracion/v3/entregas/[^/]+/confirmacion")){chain.doFilter(q,r);return;}
                var status=new int[]{200};
                var buffer=new HttpServletResponseWrapper(r){@Override public void setStatus(int code){status[0]=code;}};
                chain.doFilter(q,buffer);
                // El COMMIT público ocurrió; solo se pierde la respuesta de una confirmación.
                r.setStatus(status[0]==204&&DROP.compareAndSet(true,false)?503:status[0]);
            }
        };}
    }
    @Test void acumularSinSIGERYDrenarConProcesoInstitucionalReal()throws Exception{
        var output=Path.of(System.getenv("BATCH_DIRECTORY"));
        try(var initialized=TestDatabase.open(Files.createTempDirectory("batch-schema-"))){
            new ControlesOperacion(initialized).cambiar(0,true,true,"NORMAL","PRUEBA_LOCAL");
        }
        int count=Integer.getInteger("carga.cantidad",5000);if(count<100||count>20000)throw new IllegalArgumentException("Volumen de pruebas fuera de límites");
        var identity=new IdentidadVerificada("44444444-4","Ciudadana sintética","Carga acumulada");var proof=EvidenciaAutenticacion.de(identity);
        var ids=new ArrayList<UUID>();for(int i=0;i<count;i++)ids.add(UUID.randomUUID());
        long generation=System.nanoTime();
        try(var executor=Executors.newFixedThreadPool(16)){
            var tasks=new ArrayList<Future<?>>();for(int i=0;i<count;i++){int index=i;tasks.add(executor.submit(()->store.recibirVerificada(ids.get(index),identity,proof,"Comunicación sintética acumulada "+index,"laboratorio-v1")));}
            for(var task:tasks)task.get(120,TimeUnit.SECONDS);
        }
        double generationSeconds=(System.nanoTime()-generation)/1e9;
        assertThat(db.sql.queryForObject("SELECT COUNT(*) FROM public_delivery WHERE state='PENDIENTE'",Long.class)).isEqualTo(count);
        assertThat(store.consultar(ids.getFirst(),identity.run()).comprobante()).isNull();
        assertThatThrownBy(()->store.comprobante(ids.getFirst(),identity.run())).isInstanceOf(FalloTecnico.class);
        Files.writeString(output.resolve("publico-listo.json"),mapper.writeValueAsString(Map.of("origen","http://127.0.0.1:"+port,"cantidad",count,"pendientesAntesDeSIGER",count)));
        waitFor(output.resolve("siger-iniciado.signal"),120);
        long start=System.nanoTime(),deadline=start+Duration.ofSeconds(Integer.getInteger("carga.plazo-segundos",600)).toNanos();long confirmed=0;double first=-1;
        while(confirmed<count){
            if(System.nanoTime()>deadline)throw new AssertionError("SIGER no drenó la custodia pública dentro del plazo");
            confirmed=db.sql.queryForObject("SELECT COUNT(*) FROM public_delivery WHERE state='CONFIRMADA'",Long.class);
            if(confirmed>0&&first<0)first=(System.nanoTime()-start)/1e9;
            Files.writeString(output.resolve("progreso.json"),mapper.writeValueAsString(Map.of("confirmadas",confirmed,"total",count,"segundos",(System.nanoTime()-start)/1e9)));
            Thread.sleep(200);
        }
        var seconds=(System.nanoTime()-start)/1e9;
        assertThat(DROP.get()).isFalse();
        var folios=new HashSet<String>();var communications=new HashSet<UUID>();
        for(var id:ids){var state=store.consultar(id,identity.run());assertThat(state.estado()).isEqualTo("REGISTRADA_SIGER");folios.add(state.registroInstitucional().registro().folio());communications.add(state.registroInstitucional().registro().comunicacionId());}
        assertThat(folios).hasSize(count);assertThat(communications).hasSize(count);
        var firstReceipt=store.comprobante(ids.getFirst(),identity.run());
        assertThat(store.recibirVerificada(ids.getFirst(),identity,proof,"Comunicación sintética acumulada 0","laboratorio-v1").id()).isEqualTo(ids.getFirst());
        assertThat(store.comprobante(ids.getFirst(),identity.run())).isEqualTo(firstReceipt);
        var report=new LinkedHashMap<String,Object>();report.put("fecha",Instant.now().toString());report.put("volumen",count);report.put("sigERDetenidoDuranteGeneracion",true);
        report.put("generacionSegundos",generationSeconds);report.put("generacionPorSegundo",count/generationSeconds);report.put("primeraConfirmacionSegundos",first);
        report.put("recuperacionIncluyendoArranqueSegundos",seconds);report.put("confirmacionesPorSegundo",count/seconds);report.put("foliosUnicos",folios.size());report.put("comunicacionesUnicas",communications.size());
        report.put("respuestaAckPerdidaDespuesDelCommit",true);report.put("comprobanteEstableTrasReintento",true);report.put("fuentesExternasContactadas",false);
        Files.writeString(Path.of("target/carga-acumulada.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        Files.writeString(output.resolve("resultado-publico.json"),mapper.writeValueAsString(report));
        Files.writeString(Path.of("target/registro-institucional-v3.json"),mapper.writeValueAsString(store.consultar(ids.getFirst(),identity.run()).registroInstitucional()));
        waitFor(output.resolve("fin.signal"),120); // Coordinador comprueba inbox/ACK antes de cerrar Tomcat.
    }
    static void waitFor(Path signal,int seconds)throws Exception{long until=System.nanoTime()+Duration.ofSeconds(seconds).toNanos();while(!Files.exists(signal)){if(System.nanoTime()>until)throw new AssertionError("Falta señal del coordinador de pruebas");Thread.sleep(100);}}
}
