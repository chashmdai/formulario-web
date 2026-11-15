package cl.nexbyte.siger.formulario;

import cl.nexbyte.siger.custodia.ReciboMinimo;
import cl.nexbyte.siger.robustez.ComprobanteDigital;
import cl.nexbyte.siger.robustez.RegistroInstitucional;
import java.time.Instant;
import java.util.*;
import static cl.nexbyte.siger.formulario.IdentificacionCiudadana.*;

/**
 * Proyección ciudadana de una Comunicación ya custodiada, para el comprobante legible.
 * Se deriva en cada consulta del documento firmado y verificado; no se almacena.
 * Reutiliza {@link IdentificacionCiudadana#vista}: solo valores declarados y si se informó un RUN.
 * Nunca incluye antecedentes SRCeI, discrepancias, RUN de terceros, evidencias ni fechas de consulta.
 * Las evidencias {@code custodia} y {@code registro} se devuelven sin modificar.
 */
public record ResumenComunicacion(UUID id,String estado,ComprobanteDigital custodia,RegistroInstitucional registro,
        Contenido contenido,List<ArchivoMinimo> archivosRetirados) {
    /** Contenido disponible mientras el Formulario conserva el documento completo. */
    public record Contenido(String version,Tipo tipo,Instant recibidaEn,Map<String,String> campos,
            List<DatosFormulario.Relacion> relaciones,List<DatosFormulario.Institucion> instituciones,
            List<Vista> personas,IdentidadAutenticada identidadAutenticada,List<Adjunto> adjuntos) {
        @Override public String toString(){return "ResumenComunicacion.Contenido[protegido]";}
    }
    /** Identidad del presentador obtenida de ClaveÚnica; la misma que la revisión muestra desde la sesión. */
    public record IdentidadAutenticada(String run,String nombres,String apellidos) {
        @Override public String toString(){return "IdentidadAutenticada[protegida]";}
    }
    public record Adjunto(UUID id,String nombre,long tamanio,String sha256,String tipo) {
        @Override public String toString(){return "AdjuntoResumen[protegido]";}
    }
    /** Descriptor sin nombre de archivo: lo único que conserva el recibo mínimo tras la retirada. */
    public record ArchivoMinimo(UUID id,long tamanio,String sha256) {}

    public ResumenComunicacion {
        archivosRetirados=archivosRetirados==null?List.of():List.copyOf(archivosRetirados);
        if(contenido!=null&&!archivosRetirados.isEmpty())throw new IllegalArgumentException("Resumen incoherente");
    }

    static ResumenComunicacion completo(RecepcionFormularioJdbc.Estado estado,EntregaFormulario entrega){
        var c=entrega.comunicacion();
        var vistas=c.personas().stream().map(IdentificacionCiudadana::vista).toList();
        var adjuntos=c.adjuntos().stream().map(a->new Adjunto(a.id(),a.nombre(),a.tamanio(),a.sha256(),a.tipo())).toList();
        var contenido=new Contenido(c.version(),c.tipo(),c.recibidaEn(),c.datos().campos(),c.datos().personas(),c.datos().instituciones(),
            vistas,autenticada(c.personas()),adjuntos);
        return new ResumenComunicacion(estado.id(),estado.estado(),estado.custodia(),estado.registro(),contenido,List.of());
    }

    static ResumenComunicacion reducido(RecepcionFormularioJdbc.Estado estado,ReciboMinimo.Contenido minimo){
        var archivos=minimo.archivos().stream().map(a->new ArchivoMinimo(a.id(),a.tamanio(),a.sha256())).toList();
        return new ResumenComunicacion(estado.id(),estado.estado(),estado.custodia(),estado.registro(),null,archivos);
    }

    /** Solo atributos de ClaveÚnica del presentador autenticado; ningún valor registral. */
    private static IdentidadAutenticada autenticada(List<Persona> personas){
        for(var p:personas){
            if(p.rol()!=Rol.PRESENTADOR||!p.autenticada())continue;
            var numero=cu(p,"runNumero");var dv=cu(p,"runDv");var nombres=cu(p,"nombres");var apellidos=cu(p,"apellidos");
            if(numero==null||dv==null)return null;
            return new IdentidadAutenticada(numero+"-"+dv,nombres,apellidos);
        }
        return null;
    }
    private static String cu(Persona p,String key){
        var value=p.atributos().get(key);
        return value!=null&&value.fuente()==Fuente.CLAVE_UNICA_USERINFO?value.valor():null;
    }
    @Override public String toString(){return "ResumenComunicacion[id="+id+",estado="+estado+"]";}
}
