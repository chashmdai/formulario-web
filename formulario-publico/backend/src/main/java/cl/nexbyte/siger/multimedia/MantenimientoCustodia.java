package cl.nexbyte.siger.multimedia;

import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.robustez.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** CLI privado offline. No lo inicia Spring, no se programa ni se expone por HTTP. */
public final class MantenimientoCustodia {
    private static String required(Properties p,String key) {
        var v=p.getProperty(key);if(v==null||v.isBlank())throw new IllegalArgumentException("Configuración incompleta");return v;
    }
    private static boolean flag(Properties p,String key) { return "true".equals(p.getProperty(key,"false")); }
    public static void main(String[] args) {
        try { System.exit(run(args)); }
        catch(Exception error) {System.err.println("Mantenimiento bloqueado; revisar configuración, locks, firmas, evidencia y estado en el entorno privado. No se imprimen datos ni excepciones JDBC.");System.exit(1);}
    }
    static int run(String[] args) throws Exception {
        Map<String,String> options=new HashMap<>();
        for(int i=0;i<args.length;i++) {
            if(!Set.of("--config","--accion","--id","--salida","--aplicar").contains(args[i]) || options.containsKey(args[i]))throw new IllegalArgumentException("Argumento inválido");
            String key=args[i];options.put(key,key.equals("--aplicar")?"true":args[++i]);
        }
        var config=Path.of(Objects.requireNonNull(options.get("--config"))).toRealPath();
        if(Files.size(config)>65536)throw new IllegalArgumentException("Configuración excesiva");
        var p=new Properties();try(var in=Files.newBufferedReader(config,StandardCharsets.UTF_8)){p.load(in);}
        String action=options.getOrDefault("--accion","simular");boolean apply=options.containsKey("--aplicar");
        if(!Set.of("simular","ejecutar","abandonados","compactar","retener","levantar","recuperar").contains(action))throw new IllegalArgumentException("Acción inválida");
        if((action.equals("ejecutar")||action.equals("abandonados")||action.equals("compactar")) && apply && !flag(p,"ciclo-vida.eliminacion.habilitada"))throw new IllegalArgumentException("Eliminación cerrada");
        String url=required(p,"publico.db.url");ConfiguracionDatos.validar(url,flag(p,"datos.mysql-local")&&"127.0.0.1".equals(p.getProperty("server.address")));
        var mapper=JsonMapper.builder().build();
        var trust=JdbcRecepcionConfiguration.publicKeys(mapper,Path.of(required(p,"publico.comprobante.trusted-keys")));
        var institutional=JdbcRecepcionConfiguration.publicKeys(mapper,Path.of(required(p,"publico.comprobante.institutional-trusted-keys")));
        // Instancia exclusiva: falla si el receptor multimedia está activo. No detiene ningún proceso.
        try(var store=new AlmacenPrivado(Path.of(required(p,"formulario.multimedia.directorio")));
            var db=new BaseJdbc(url,required(p,"publico.db.user"),required(p,"publico.db.password"),2)) {
            // Sin migrate implícito: V9 debe haberse aplicado explícitamente con la cuenta de migración.
            db.sql.queryForObject("SELECT COUNT(*) FROM custody_lifecycle",Long.class);
            var controls=new ControlesOperacion(db).estado();
            if(controls.aceptarNuevos()||controls.recuperar())throw new IllegalStateException("Cerrar recepción/recuperación antes del mantenimiento");
            ArchivoCustodiaIndependiente archive=null;
            if(p.containsKey("ciclo-vida.archivo-institucional")&&p.containsKey("ciclo-vida.restauracion"))archive=new ArchivoCustodiaIndependiente(Path.of(required(p,"ciclo-vida.archivo-institucional")),Path.of(required(p,"ciclo-vida.restauracion")),store.raiz());
            if(action.equals("ejecutar")&&apply){if(archive==null)throw new IllegalStateException("Faltan evidencias");archive.calificarMontajes(store.raiz());}
            var policy=new CicloVidaCustodia.Politica(flag(p,"ciclo-vida.eliminacion.habilitada"),Duration.parse(p.getProperty("ciclo-vida.retencion-post-ack","PT72H")),Duration.parse(p.getProperty("ciclo-vida.retencion-abandono","PT48H")));
            var service=new CicloVidaCustodia(db,store,archive,mapper,trust,institutional,Clock.systemUTC(),policy);
            switch(action) {
                case "simular" -> System.out.println(mapper.writeValueAsString(service.procesar(20,false)));
                case "ejecutar" -> System.out.println(mapper.writeValueAsString(service.procesar(20,apply)));
                case "abandonados" -> System.out.println("{\"candidatos\":"+service.abandonar(apply)+",\"aplicado\":"+apply+"}");
                case "compactar" -> System.out.println("{\"revisados\":"+service.compactarReservas(apply)+",\"aplicado\":"+apply+"}");
                case "retener","levantar" -> {service.conservar(UUID.fromString(options.get("--id")),action.equals("retener"));System.out.println("{\"retencionActualizada\":true}");}
                case "recuperar" -> {
                    if(archive==null)throw new IllegalStateException("Falta restauración independiente");
                    UUID id=UUID.fromString(options.get("--id"));service.verificar(id);
                    var e=mapper.readValue(db.sql.queryForObject("SELECT body FROM citizen_submission WHERE id=?",String.class,id.toString()),EntregaFormulario.class);
                    var dest=Path.of(Objects.requireNonNull(options.get("--salida"))).toAbsolutePath().normalize();
                    if(Files.exists(dest,LinkOption.NOFOLLOW_LINKS))throw new IOException("Destino nuevo requerido");
                    for(var parent=dest.getParent();parent!=null;parent=parent.getParent())if(Files.isSymbolicLink(parent)||Files.exists(parent.resolve(".git")))throw new IOException("Destino privado requerido");
                    if(dest.startsWith(store.raiz()))throw new IOException("Exportar fuera del SSD de custodia pública");
                    Files.createDirectory(dest,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));
                    for(var d:e.comunicacion().adjuntos())archive.recuperar(id,d,dest.resolve(d.id()+".bin"));
                    db.event("ALMACENAMIENTO",id.toString(),"RECUPERACION_INDEPENDIENTE_VERIFICADA");System.out.println("{\"recuperados\":"+e.comunicacion().adjuntos().size()+"}");
                }
                default -> throw new IllegalArgumentException("Acción inválida");
            }
            return 0;
        }
    }
}