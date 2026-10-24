package cl.nexbyte.siger.robustez;
import jakarta.servlet.*;import jakarta.servlet.http.*;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.Semaphore;import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;import org.springframework.core.annotation.Order;import org.springframework.core.env.Environment;
import org.springframework.web.filter.OncePerRequestFilter;

/** Memoria y trabajo acotados antes de parsear JSON; ningún rechazo se presenta como recepción. */
@Component @Order(-200)
public final class LimitesCarga extends OncePerRequestFilter{
    private final Semaphore admission;private final int capacity;private final int maxBytes;
    private final LongAdder rejected=new LongAdder(),oversized=new LongAdder();
    public LimitesCarga(Environment env){
        capacity=env.getProperty("recepcion.limites.concurrentes",Integer.class,64);maxBytes=env.getProperty("recepcion.limites.bytes",Integer.class,8192);
        if(capacity<1||capacity>256||maxBytes<4096||maxBytes>65536)throw new IllegalArgumentException("Límites inválidos");
        admission=new Semaphore(capacity);
    }
    public Map<String,Number> metricas(){return Map.of("capacidad",capacity,"activas",capacity-admission.availablePermits(),"rechazadas429",rejected.sum(),"rechazadas413",oversized.sum());}
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
        var uri=request.getRequestURI();var type=request.getContentType();
        boolean guarded=Set.of("POST","PUT").contains(request.getMethod())&&(uri.startsWith("/api/comunicaciones")||uri.startsWith("/api/nna/")||uri.startsWith("/api/formulario/")||uri.startsWith("/integracion/")||uri.startsWith("/operacion/"));
        if(!guarded){chain.doFilter(request,response);return;}
        if(request.getContentLengthLong()>maxBytes){oversized.increment();reject(response,413);return;}
        if(!admission.tryAcquire()){rejected.increment();reject(response,429);return;}
        try{
            // Solo JSON: no consumir formularios de logout/CSRF ni cambiar su semántica.
            if(type!=null&&type.toLowerCase(Locale.ROOT).startsWith("application/json")){
                var bytes=request.getInputStream().readNBytes(maxBytes+1);
                if(bytes.length>maxBytes){oversized.increment();reject(response,413);return;}
                var wrapped=new HttpServletRequestWrapper(request){
                    public ServletInputStream getInputStream(){
                        var input=new ByteArrayInputStream(bytes);
                        return new ServletInputStream(){public int read(){return input.read();}public int read(byte[]b,int o,int n){return input.read(b,o,n);}
                            public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}
                            public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Lectura síncrona");}};
                    }
                    public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
                };
                chain.doFilter(wrapped,response);
            }else chain.doFilter(request,response);
        }finally{admission.release();}
    }
    private static void reject(HttpServletResponse response,int status)throws IOException{
        response.setStatus(status);response.setContentType("application/problem+json");response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control","no-store");response.setHeader("Retry-After","2");
        response.getWriter().write("{\"status\":"+status+",\"title\":\"Capacidad o tamaño de solicitud excedido\"}");
    }
}
