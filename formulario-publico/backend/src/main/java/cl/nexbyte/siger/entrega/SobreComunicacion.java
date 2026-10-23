package cl.nexbyte.siger.entrega;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/** DTO del contrato HTTP v1. Sin procesos ni reglas institucionales. */
public record SobreComunicacion(String version, UUID id, Instant recibidaEn, Identidad identidad,
        String mensaje, String avisoPrivacidadVersion, String sha256,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) cl.nexbyte.siger.contrato.DeclaracionNna declaracionNna) {
    public SobreComunicacion(String version,UUID id,Instant recibidaEn,Identidad identidad,String mensaje,String avisoPrivacidadVersion,String sha256){this(version,id,recibidaEn,identidad,mensaje,avisoPrivacidadVersion,sha256,null); }
    public record Identidad(String run, String nombres, String apellidos,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) java.util.List<String> nombresComponentes,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) java.util.List<String> apellidosComponentes) {
        public Identidad {
            if (cl.nexbyte.siger.contrato.ComponentesIdentidad.validarRepresentacion(nombres,apellidos,nombresComponentes,apellidosComponentes)) {
                nombresComponentes=java.util.List.copyOf(nombresComponentes);apellidosComponentes=java.util.List.copyOf(apellidosComponentes);
            }
        }
        public Identidad(String run,String nombres,String apellidos) { this(run,nombres,apellidos,null,null); }
        @Override public String toString() { return "Identidad[datos protegidos]"; }
    }
    public record Confirmacion(UUID recepcionInstitucionalId, Instant registradaEn, String sha256) {
        public Confirmacion {
            if (recepcionInstitucionalId == null || registradaEn == null || sha256 == null || !sha256.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("Confirmación inválida");
        }
    }
    public SobreComunicacion {
        if("2.0".equals(version)){
            if(id==null||recibidaEn==null||identidad!=null||declaracionNna==null||avisoPrivacidadVersion==null||!avisoPrivacidadVersion.matches("[A-Za-z0-9_.-]{1,100}")||sha256==null||!sha256.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Sobre NNA inválido");
            cl.nexbyte.siger.contrato.DeclaracionNna.relato(mensaje);
        }else{
            if(declaracionNna!=null)throw new IllegalArgumentException("Declaración ajena al contrato histórico");

        if ((!"1.0".equals(version) && !"1.1".equals(version)) || id == null || recibidaEn == null || identidad == null
                || identidad.run() == null || !identidad.run().matches("[1-9][0-9]{0,7}-[0-9K]")
                || identidad.nombres() == null || identidad.nombres().isBlank() || identidad.nombres().length() > ("1.0".equals(version)?500:1009)
                || identidad.apellidos() == null || identidad.apellidos().isBlank() || identidad.apellidos().length() > ("1.0".equals(version)?500:1009)
                || mensaje == null || mensaje.isBlank() || mensaje.length() > 2000
                || avisoPrivacidadVersion == null || avisoPrivacidadVersion.isBlank() || avisoPrivacidadVersion.length() > 100
                || sha256 == null || !sha256.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Sobre inválido");
        if (("1.1".equals(version)) != (identidad.nombresComponentes()!=null)) throw new IllegalArgumentException("Versión de identidad incoherente");
            }
    }
    public String canal(){return "2.0".equals(version)?"FORMULARIO_WEB_NNA":"FORMULARIO_WEB_PUBLICO";}
    public static SobreComunicacion crearNna(UUID id,Instant fecha,cl.nexbyte.siger.contrato.DeclaracionNna datos,String mensaje,String aviso){
        return new SobreComunicacion("2.0",id,fecha,null,mensaje,aviso,huellaNna(id,fecha,datos,mensaje,aviso),datos);
    }
    private static String huellaNna(UUID id,Instant fecha,cl.nexbyte.siger.contrato.DeclaracionNna d,String mensaje,String aviso){
        // Prefijos de presencia distinguen null de vacío; orden fijo, longitudes UTF-8.
        var fields=new java.util.ArrayList<String>(java.util.List.of("2.0",id.toString(),fecha.toString()));
        for(String value:new String[]{d.primerNombre(),d.segundoNombre(),d.primerApellido(),d.segundoApellido(),d.runNumero(),d.runDv(),d.direccion(),d.comuna(),d.region()}){fields.add(value==null?"0":"1"+value);}
        fields.add(mensaje);fields.add(aviso);return digest(fields.toArray(String[]::new));
    }
    public static SobreComunicacion crear(UUID id, Instant fecha, Identidad identidad, String mensaje, String aviso) {
        String version=identidad.nombresComponentes()==null?"1.0":"1.1";
        return new SobreComunicacion(version,id,fecha,identidad,mensaje,aviso,huella(version,id,fecha,identidad,mensaje,aviso));
    }
    public boolean integra() {
        return MessageDigest.isEqual(sha256.getBytes(StandardCharsets.US_ASCII),
                ("2.0".equals(version)?huellaNna(id,recibidaEn,declaracionNna,mensaje,avisoPrivacidadVersion):huella(version,id,recibidaEn,identidad,mensaje,avisoPrivacidadVersion)).getBytes(StandardCharsets.US_ASCII));
    }
    private static String huella(String version,UUID id,Instant fecha,Identidad identidad,String mensaje,String aviso) {
        if ("1.0".equals(version)) return digest(version,id.toString(),fecha.toString(),identidad.run(),identidad.nombres(),identidad.apellidos(),mensaje,aviso);
        var fields=new java.util.ArrayList<String>(java.util.List.of(version,id.toString(),fecha.toString(),identidad.run(),identidad.nombres(),identidad.apellidos()));
        fields.add(Integer.toString(identidad.nombresComponentes().size()));fields.addAll(identidad.nombresComponentes());
        fields.add(Integer.toString(identidad.apellidosComponentes().size()));fields.addAll(identidad.apellidosComponentes());
        fields.add(mensaje);fields.add(aviso);
        return digest(fields.toArray(String[]::new));
    }
    private static String digest(String... fields) {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            for (var field : fields) { var value = field.getBytes(StandardCharsets.UTF_8); out.writeInt(value.length); out.write(value); }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @Override public String toString() { return "SobreComunicacion[id=" + id + "]"; }
}
