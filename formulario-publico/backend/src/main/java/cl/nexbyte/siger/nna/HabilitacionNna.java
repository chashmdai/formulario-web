package cl.nexbyte.siger.nna;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
import cl.nexbyte.siger.identidad.ClaveUnicaProperties;
/** El prototipo solo puede operar en un laboratorio Sandbox ligado a loopback y HTTPS. */
@Component
public final class HabilitacionNna {
    private final boolean enabled;
    public HabilitacionNna(Environment env,ClaveUnicaProperties cu){
        enabled=env.getProperty("publico.nna.habilitado",Boolean.class,false);
        if(enabled&&(!env.matchesProfiles("nna-lab")||!env.matchesProfiles("jdbc-reception")||env.matchesProfiles("production","prod")
            ||cu.getEnvironment()!=ClaveUnicaProperties.Stage.SANDBOX||!"127.0.0.1".equals(env.getProperty("server.address"))
            ||!"https".equals(cu.getPublicOrigin().getScheme())||!env.getProperty("server.servlet.session.cookie.secure",Boolean.class,true)))
            throw new IllegalStateException("Prototipo NNA permitido exclusivamente en laboratorio Sandbox HTTPS y loopback");
    }
    public boolean habilitado(){return enabled;}
}
