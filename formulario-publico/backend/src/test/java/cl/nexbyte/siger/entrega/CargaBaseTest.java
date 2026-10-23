package cl.nexbyte.siger.entrega;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/** Medición reproducible del adaptador anterior. Nunca llama ClaveÚnica ni servicios externos. */
@EnabledIfSystemProperty(named="carga.base", matches="true")
class CargaBaseTest {
    @TempDir Path directory;
    @Test void medir() throws Exception {
        int total=Integer.getInteger("carga.registros",1000), workers=16;
        var samples=new ConcurrentLinkedQueue<Double>();
        double elapsed;
        double exportMs;
        try(var store=new RecepcionArchivosDemo(directory,JsonMapper.builder().build());var executor=Executors.newFixedThreadPool(workers)) {
            long start=System.nanoTime();
            var futures=new ArrayList<Future<?>>();
            for(int i=0;i<total;i++) futures.add(executor.submit(()->{
                long before=System.nanoTime();
                store.recibir(UUID.randomUUID(),new SobreComunicacion.Identidad("44444444-4","Ciudadana","Prueba"),"Carga local sintética");
                samples.add((System.nanoTime()-before)/1e6);
            }));
            for(var f:futures)f.get(120,TimeUnit.SECONDS);
            elapsed=(System.nanoTime()-start)/1e9;
            start=System.nanoTime();
            for(int i=0;i<30;i++)assertThat(store.pendientes(20)).hasSize(20);
            exportMs=(System.nanoTime()-start)/1e6/30;
        }
        var sorted=samples.stream().sorted().toList();
        var result=Map.of("adapter","archivos-base","registros",total,"concurrencia",workers,"segundos",elapsed,
            "operacionesSegundo",total/elapsed,"p50ms",sorted.get(total/2),"p95ms",sorted.get((int)(total*.95)),
            "p99ms",sorted.get((int)(total*.99)),"exportar20PromedioMs",exportMs);
        Path output=Path.of("target/carga-base.json"); Files.writeString(output,JsonMapper.builder().build().writeValueAsString(result));
        System.out.println("CARGA_BASE "+JsonMapper.builder().build().writeValueAsString(result));
    }
}
