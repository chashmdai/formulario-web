package cl.nexbyte.siger.formulario;

import cl.nexbyte.siger.robustez.*;
import java.security.PublicKey;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import static cl.nexbyte.siger.formulario.IdentificacionCiudadana.*;

/** Documento completo para un consumidor ampliado; nunca se introduce en la cola histórica V3. */
public record EntregaFormulario(Contenido comunicacion,String sha256,String huellaProcedencia,ComprobanteDigital custodia) {
    public record Contenido(String version,UUID id,UUID recorridoId,Tipo tipo,Instant recibidaEn,String avisoVersion,
            boolean aceptaCanalDigital,DatosFormulario datos,List<Persona> personas,List<AdjuntosFormulario.Descriptor> adjuntos) {
        public Contenido {personas=List.copyOf(personas);adjuntos=List.copyOf(adjuntos);}
        @Override public String toString(){return "ComunicacionCompleta[protegida]";}
    }
    public static String procedencia(Contenido content,JsonMapper mapper){
        var sources=new TreeMap<String,Object>();content.personas().forEach(p->sources.put(p.id(),List.of(p.atributos(),p.discrepancias())));
        return CanonFormulario.huella("PROCEDENCIA",sources,mapper);
    }
    public void validar(Map<String,PublicKey> trust,JsonMapper mapper){
        var c=comunicacion;
        if(c==null||!"1.0".equals(c.version())||c.id()==null||c.recorridoId()==null||c.tipo()==null||c.recibidaEn()==null||c.avisoVersion()==null||c.datos()==null||custodia==null
                ||!CanonFormulario.huella("CONTENIDO",c,mapper).equals(sha256)||!procedencia(c,mapper).equals(huellaProcedencia))throw new IllegalArgumentException("Integridad de comunicación inválida");
        var proof=custodia.verificar(trust,mapper);
        if(!proof.id().equals(c.id())||!proof.recibidaEn().equals(c.recibidaEn())||!proof.avisoVersion().equals(c.avisoVersion())||!proof.huellaContenido().equals(sha256)||!proof.huellaProcedencia().equals(huellaProcedencia))throw new IllegalArgumentException("Custodia no corresponde");
    }
    public void validarRegistro(RegistroInstitucional ack,Map<String,PublicKey> trust,JsonMapper mapper) {
        if(ack==null)throw new IllegalArgumentException("ACK requerido");
        var r=ack.comprobante().verificar(trust,mapper);
        if(r==null||!r.equals(ack.registro())||!r.operacionPublicaId().equals(comunicacion.id())||!r.sha256().equals(sha256)||!r.huellaProcedencia().equals(huellaProcedencia)
                ||!(comunicacion.tipo()==Tipo.NNA?"FORMULARIO_WEB_NNA":"FORMULARIO_WEB_PUBLICO").equals(r.canalOrigen()))throw new IllegalArgumentException("ACK no corresponde a comunicación completa");
    }
    @Override public String toString(){return "EntregaFormulario[protegida]";}
}
