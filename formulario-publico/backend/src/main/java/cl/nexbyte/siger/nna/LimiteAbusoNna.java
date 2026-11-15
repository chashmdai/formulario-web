package cl.nexbyte.siger.nna;
import cl.nexbyte.siger.config.OrigenCliente;
import jakarta.servlet.*;import jakarta.servlet.http.*;
import java.io.IOException;import java.time.Clock;import java.util.*;
import org.springframework.stereotype.Component;import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
/** Cuotas independientes de concurrencia; memoria acotada. No confiar en X-Forwarded-For del cliente. */
@Component @Order(-190)
public final class LimiteAbusoNna extends OncePerRequestFilter {
    private final Map<String,Window> windows=new HashMap<>();private final Clock clock;
    private final OrigenCliente origen;
    private record Window(long minuto,int solicitudes){}
    @org.springframework.beans.factory.annotation.Autowired
    public LimiteAbusoNna(OrigenCliente origen){this(Clock.systemUTC(),origen);}
    LimiteAbusoNna(Clock clock,OrigenCliente origen){this.clock=clock;this.origen=origen;}
    synchronized boolean admitir(String ip,String session){
        long now=clock.millis()/60000;windows.entrySet().removeIf(e->e.getValue().minuto()!=now);
        if(windows.size()>4096)return false;
        var keys=new String[]{"global","ip:"+ip,"session:"+session};var limits=new int[]{120,30,6};
        for(int i=0;i<keys.length;i++)if(windows.getOrDefault(keys[i],new Window(now,0)).solicitudes()>=limits[i])return false;
        for(var key:keys)windows.put(key,new Window(now,windows.getOrDefault(key,new Window(now,0)).solicitudes()+1));return true;
    }
    protected void doFilterInternal(HttpServletRequest q,HttpServletResponse r,FilterChain chain)throws IOException,ServletException{
        if(q.getMethod().equals("POST")&&q.getRequestURI().startsWith("/api/nna/")){
            var session=q.getSession(false);
            var ip=origen.de(q);
            if(!admitir(ip,session==null?"SIN_SESION:"+ip:session.getId())){
                r.setStatus(429);r.setHeader("Retry-After","60");r.setHeader("Cache-Control","no-store");return;
            }
        }
        chain.doFilter(q,r);
    }
}
