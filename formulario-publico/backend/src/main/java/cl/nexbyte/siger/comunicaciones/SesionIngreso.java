package cl.nexbyte.siger.comunicaciones;
import jakarta.servlet.http.*;
import java.security.SecureRandom;
import java.util.HexFormat;
import cl.nexbyte.siger.robustez.FalloTecnico;
/** Capacidad de propiedad interna al servidor. Nunca se devuelve al navegador. */
public final class SesionIngreso {
    private static final String KEY=SesionIngreso.class.getName();
    private SesionIngreso(){}
    public static String iniciar(HttpServletRequest request){
        var session=request.getSession();
        synchronized(session){
            var value=(String)session.getAttribute(KEY);
            if(value==null){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);value=HexFormat.of().formatHex(bytes);session.setAttribute(KEY,value);}
            return value;
        }
    }
    public static String actual(HttpServletRequest request){
        var session=request.getSession(false);
        var value=session==null?null:(String)session.getAttribute(KEY);
        if(value==null)throw new FalloTecnico(404);return value;
    }
}
