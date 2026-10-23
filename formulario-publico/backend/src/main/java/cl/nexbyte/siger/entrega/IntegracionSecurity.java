package cl.nexbyte.siger.entrega;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class IntegracionSecurity {
    @Bean @Order(1)
    SecurityFilterChain machineSecurity(HttpSecurity http, Environment env) throws Exception {
        String token = env.getProperty("publico.integracion.token", "");
        boolean demo = env.matchesProfiles("local-integration-demo") || (env.matchesProfiles("jdbc-reception") && env.getProperty("publico.integracion.http-local",Boolean.class,false) && "127.0.0.1".equals(env.getProperty("server.address")));
        http.securityMatcher("/integracion/**").sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(c -> c.disable()).csrf(c -> c.disable())
                .authorizeHttpRequests(a -> a.anyRequest().hasRole("INTEGRACION"))
                .exceptionHandling(e -> e.authenticationEntryPoint((q,r,x) -> r.setStatus(401)))
                .addFilterBefore(new OncePerRequestFilter() {
                    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                            throws java.io.IOException, ServletException {
                        response.setHeader("Cache-Control", "no-store");
                        SecurityContextHolder.clearContext(); // Nunca heredar una sesión ciudadana.
                        boolean local = demo && java.util.Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(request.getRemoteAddr());
                        if (!request.isSecure() && !local) { response.setStatus(403); return; }
                        var auth = request.getHeader("Authorization");
                        if (token.length() >= 32 && auth != null && auth.length() < 512
                                && MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8), auth.getBytes(StandardCharsets.UTF_8))) {
                            var context = SecurityContextHolder.createEmptyContext();
                            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("siger-integracion", null,
                                    java.util.List.of(new SimpleGrantedAuthority("ROLE_INTEGRACION"))));
                            SecurityContextHolder.setContext(context);
                        }
                        chain.doFilter(request, response);
                    }
                }, AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
