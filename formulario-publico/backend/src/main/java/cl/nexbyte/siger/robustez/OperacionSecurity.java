package cl.nexbyte.siger.robustez;
import jakarta.servlet.*;import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.*;
import org.springframework.context.annotation.*;import org.springframework.core.annotation.Order;import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.*;import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
@Configuration
public class OperacionSecurity{
    @Bean @Order(0) SecurityFilterChain operacion(HttpSecurity http,Environment env)throws Exception{
        var token=env.getProperty("operacion.token","");
        var allowLocal=env.getProperty("operacion.http-local",Boolean.class,false)&&env.matchesProfiles("jdbc-reception")&&"127.0.0.1".equals(env.getProperty("server.address"));
        return http.securityMatcher("/operacion/**").csrf(c->c.disable()).requestCache(c->c.disable())
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a.anyRequest().hasRole("OPERACION"))
            .exceptionHandling(e->e.authenticationEntryPoint((q,r,x)->r.setStatus(401)))
            .addFilterBefore(new OncePerRequestFilter(){protected void doFilterInternal(HttpServletRequest q,HttpServletResponse r,FilterChain chain)throws java.io.IOException,ServletException{
                SecurityContextHolder.clearContext();r.setHeader("Cache-Control","no-store");
                if(!q.isSecure()&&!(allowLocal&&Set.of("127.0.0.1","::1","0:0:0:0:0:0:0:1").contains(q.getRemoteAddr()))){r.setStatus(403);return;}
                var auth=q.getHeader("Authorization");
                if(token.length()>=32&&token.length()<=400&&auth!=null&&auth.length()<512&&MessageDigest.isEqual(("Bearer "+token).getBytes(StandardCharsets.UTF_8),auth.getBytes(StandardCharsets.UTF_8))){
                    var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("credencial-operacion",null,List.of(new SimpleGrantedAuthority("ROLE_OPERACION"))));SecurityContextHolder.setContext(context);
                }
                chain.doFilter(q,r);
            }},AnonymousAuthenticationFilter.class).build();
    }
}
