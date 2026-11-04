package cl.nexbyte.siger.identidad;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/** Solo se instala en la cadena ciudadana, después de CSRF y antes de OAuth. */
final class AccesoTemporalFilter extends OncePerRequestFilter {
    private final AccesoTemporal access;
    AccesoTemporalFilter(AccesoTemporal access){this.access=access;}
    @Override protected void doFilterInternal(HttpServletRequest q,HttpServletResponse r,FilterChain chain)throws IOException,ServletException {
        String path=q.getRequestURI().substring(q.getContextPath().length());
        boolean read=q.getMethod().equals("GET")||q.getMethod().equals("HEAD");
        r.setHeader("Cache-Control","no-store");r.setHeader("X-Robots-Tag","noindex, nofollow, noarchive");
        if(path.equals("/acceso-temporal/salud")&&read){r.setContentType("application/json");r.getWriter().write("{\"status\":\"UP\"}");return;}
        if(read&&(path.equals("/acceso-temporal/estilo.css")||path.equals("/acceso-temporal/logo.svg"))) {
            String resource=path.endsWith(".css")?"estilo.css":"logo.svg";
            try(var input=getClass().getResourceAsStream("/acceso-temporal/"+resource)) {
                if(input==null){r.setStatus(404);return;}
                r.setContentType(resource.endsWith("css")?"text/css;charset=UTF-8":"image/svg+xml");if(!q.getMethod().equals("HEAD"))input.transferTo(r.getOutputStream());return;
            }
        }
        var mode=access.mode();
        if(mode==AccesoTemporal.Mode.UNAVAILABLE){unavailable(q,r);return;}
        if(mode==AccesoTemporal.Mode.OPEN){if(path.equals("/acceso-temporal")){redirect(r);return;}chain.doFilter(q,r);return;}
        if(!q.isSecure()){r.setStatus(403);return;}
        if(path.equals("/acceso-temporal")) {
            if(q.getMethod().equals("POST")) {
                if(q.getContentType()==null||!q.getContentType().startsWith("application/x-www-form-urlencoded")||q.getContentLengthLong()>4096){r.setStatus(400);return;}
                var result=access.authenticate(q.getParameter("password"),q.getRemoteAddr(),q.getSession());
                if(result==AccesoTemporal.Result.ACCEPTED){q.changeSessionId();new HttpSessionCsrfTokenRepository().saveToken(null,q,r);redirect(r);return;}
                if(result==AccesoTemporal.Result.UNAVAILABLE){unavailable(q,r);return;}
                r.setStatus(result==AccesoTemporal.Result.LIMITED?429:401);
                if(result==AccesoTemporal.Result.LIMITED)r.setHeader("Retry-After","900");
                page(q,r,result==AccesoTemporal.Result.LIMITED?"Se alcanzó el límite de intentos. Inténtalo más tarde.":"No fue posible validar el acceso. Revisa la contraseña e inténtalo nuevamente.",false);return;
            }
            if(read){if(access.granted(q.getSession(false))){redirect(r);return;}page(q,r,"",false);return;}
            r.setStatus(405);return;
        }
        if(access.granted(q.getSession(false))){chain.doFilter(q,r);return;}
        if(path.startsWith("/api/")||!read||path.startsWith("/assets/")) {
            r.setStatus(401);r.setContentType("application/problem+json");r.getWriter().write("{\"title\":\"Acceso temporal requerido\",\"status\":401}");return;
        }
        r.setStatus(303);r.setHeader("Location","/acceso-temporal");
    }
    private void unavailable(HttpServletRequest q,HttpServletResponse r)throws IOException {
        r.setStatus(503);r.setHeader("Retry-After","3600");
        if(q.getRequestURI().startsWith("/api/")){r.setContentType("application/problem+json");r.getWriter().write("{\"title\":\"Servicio temporalmente no disponible\",\"status\":503}");}
        else page(q,r,"",true);
    }
    private static void redirect(HttpServletResponse r){r.setStatus(303);r.setHeader("Location","/?vista=inicio");}
    private void page(HttpServletRequest q,HttpServletResponse r,String message,boolean closed)throws IOException {
        r.setContentType("text/html;charset=UTF-8");
        String form="";
        if(!closed) {
            var csrf=(CsrfToken)q.getAttribute(CsrfToken.class.getName());
            form="<form method=\"post\" action=\"/acceso-temporal\"><input type=\"hidden\" name=\""+csrf.getParameterName()+"\" value=\""+csrf.getToken()+"\"><label for=\"password\">Contraseña de acceso temporal</label><input id=\"password\" name=\"password\" type=\"password\" autocomplete=\"current-password\" required minlength=\"16\" maxlength=\"256\" aria-describedby=\"access-help access-error\"><p id=\"access-error\" role=\"alert\">"+message+"</p><button type=\"submit\">Ingresar al formulario</button></form>";
        }
        if(!q.getMethod().equals("HEAD"))r.getWriter().write("<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>"+(closed?"Servicio no disponible":"Acceso institucional")+" | Defensoría de la Niñez</title><link rel=\"stylesheet\" href=\"/acceso-temporal/estilo.css\"></head><body><header><img src=\"/acceso-temporal/logo.svg\" alt=\"Defensoría de la Niñez\"><span>FORMULARIO DE CONTACTO</span></header><main><section aria-labelledby=\"access-title\"><p class=\"tag\">"+(closed?"SERVICIO A LA CIUDADANÍA":"PRUEBAS INSTITUCIONALES")+"</p><h1 id=\"access-title\">"+(closed?"Servicio temporalmente no disponible":"Acceso temporal al formulario")+"</h1><p id=\"access-help\">"+(closed?"La recepción ciudadana todavía no está habilitada. Agradecemos tu comprensión.":"Este acceso está reservado para personas autorizadas durante las pruebas institucionales. Ingresa la contraseña que te entregó la institución.")+"</p>"+form+(!closed?"<p class=\"note\">Esta contraseña permite acceder a las pruebas. La identificación mediante ClaveÚnica se realiza por separado, únicamente en su sitio oficial.</p>":"")+"</section></main><footer>Defensoría de la Niñez · Formulario Público</footer></body></html>");
    }
}
