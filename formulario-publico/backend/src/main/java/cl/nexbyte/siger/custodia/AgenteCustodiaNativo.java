package cl.nexbyte.siger.custodia;

import java.io.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import cl.nexbyte.siger.robustez.BaseJdbc;

/** Entrada auxiliar sin Spring, servidor web, migraciones o reglas de negocio institucionales. */
public final class AgenteCustodiaNativo {
    public static final String VERSION="1.1.0-windows-lab";
    public static final class Ocupado extends IOException{}
    public static final class Guardia implements AutoCloseable {
        private final FileChannel channel;private final FileLock lock;
        public Guardia(Path scratch)throws IOException{
            var root=ArchivoAgente.raiz(scratch);var path=root.resolve("agent.lock");
            if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))try{ArchivoAgente.escribir(path,new byte[0]);}catch(FileAlreadyExistsException competing){}
            ArchivoAgente.ruta(path);ArchivoAgente.permisos(path);channel=FileChannel.open(path,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
            FileLock acquired;try{acquired=channel.tryLock();}catch(OverlappingFileLockException e){channel.close();throw new Ocupado();}if(acquired==null){channel.close();throw new Ocupado();}lock=acquired;
        }
        public void close()throws IOException{try{lock.release();}finally{channel.close();}}
    }
    static void secretos(Properties config,InputStream input,JsonMapper mapper)throws IOException{
        byte[] bytes=input.readNBytes(16385);if(bytes.length>16384)throw new IOException("Secreto fuera de límite");
        try{
            var node=mapper.readTree(bytes);if(!node.isObject()||node.size()!=2||!node.has("usuario")||!node.has("password")||node.get("password").asString().isEmpty()||config.containsKey("garantia.origen.db.password"))throw new IOException("Canal secreto inválido");
            config.setProperty("garantia.origen.db.user",node.get("usuario").asString());config.setProperty("garantia.origen.db.password",node.get("password").asString());
        }finally{Arrays.fill(bytes,(byte)0);}
    }
    public static void main(String[] args){int code=0;try{code=run(args);}catch(Ocupado e){code=12;System.out.println("AGENTE_OCUPADO");}catch(Exception e){code=20;System.out.println("AGENTE_FALLO_SEGURO: revisar diagnóstico privado; no se muestran SQL, rutas de documentos ni secretos");}if(code!=0)System.exit(code);}
    static int run(String[] args)throws Exception{
        if(Arrays.equals(args,new String[]{"--version"})){System.out.println("AGENTE_CUSTODIA "+VERSION+" Java="+Runtime.version().feature()+" Windows="+ArchivoAgente.windows());return 0;}
        var opts=new HashMap<String,String>();for(int i=0;i<args.length;i++){String key=args[i];if(!Set.of("--config","--secretos-stdin","--ensayo","--id","--cola","--verificar-tls","--enviar","--inspeccionar").contains(key)||opts.containsKey(key))throw new IllegalArgumentException();opts.put(key,Set.of("--config","--id","--cola").contains(key)?args[++i]:"true");}
        var config=new Properties();var configFile=Path.of(opts.get("--config"));ArchivoAgente.ruta(configFile);ArchivoAgente.permisos(configFile);try(var in=Files.newBufferedReader(configFile)){config.load(in);}
        var mapper=JsonMapper.builder().build();if(opts.containsKey("--secretos-stdin"))secretos(config,System.in,mapper);else if(ArchivoAgente.windows()&&!opts.containsKey("--inspeccionar"))throw new IOException("Windows exige entrega anónima por stdin");
        var sourceRoot=ArchivoAgente.raiz(Path.of(config.getProperty("garantia.origen.archivo")));var backupRoot=ArchivoAgente.raiz(Path.of(config.getProperty("garantia.respaldo.directorio")));var scratch=ArchivoAgente.raiz(Path.of(config.getProperty("garantia.restauracion.scratch")));
        try(var guard=new Guardia(scratch)){
            if(opts.containsKey("--inspeccionar")){var accreditation=ArchivoAgente.windows()?AcreditacionAlmacenWindows.inspeccionar(config,sourceRoot,backupRoot,java.time.Clock.systemUTC(),mapper).codigo():"NO_APLICA";System.out.println("RUTAS_ACL_OK independencia="+ArchivoAgente.independiente(sourceRoot,backupRoot)+" durabilidad="+ArchivoAgente.durabilidadAcreditada(backupRoot)+" acreditacion="+accreditation);return 0;}
            String url=EmisorGarantiaCustodia.localUrl(config,"garantia.origen.db");
            if(opts.containsKey("--enviar")){
                AcreditacionAlmacenWindows.Permiso permiso=null;
                try{if(ArchivoAgente.windows())permiso=AcreditacionAlmacenWindows.autorizar(config,sourceRoot,backupRoot);
                    if(!ArchivoAgente.independiente(sourceRoot,backupRoot,permiso)||!ArchivoAgente.durabilidadAcreditada(backupRoot))throw new IOException();
                }catch(IOException denied){System.out.println("CUSTODIA_NO_ACREDITADA: no se contacta al Formulario ni se emite garantía");return 42;}
                EmisorGarantiaCustodia.ejecutar(config,Map.of("--enviar","true"),permiso);return 0;
            }
            try(var db=new BaseJdbc(url,config.getProperty("garantia.origen.db.user"),config.getProperty("garantia.origen.db.password"),2,true)){
                if(opts.containsKey("--verificar-tls")){
                    try{var cipher=db.sql.queryForList("SHOW SESSION STATUS LIKE 'Ssl_cipher'");if(cipher.isEmpty()||cipher.getFirst().get("Value")==null||cipher.getFirst().get("Value").toString().isBlank())throw new IOException("Sin cifrado acreditado");System.out.println("MYSQL_TLS_IDENTIDAD_OK");return 0;}catch(Exception failure){System.out.println("MYSQL_TLS_IDENTIDAD_NO_VERIFICADA");return 40;}
                }
                if(!opts.containsKey("--ensayo")||!"LABORATORIO".equals(config.getProperty("garantia.modo")))throw new IllegalArgumentException("Ensayo explícito requerido");
                long cutoff=System.currentTimeMillis()-java.time.Duration.ofHours(24).toMillis();ArchivoAgente.limpiarVencidos(scratch,cutoff);ArchivoAgente.limpiarVencidos(backupRoot,cutoff);
                var result=RespaldoIngresoVerificado.ensayar(db.sql,sourceRoot,backupRoot,scratch,UUID.fromString(opts.get("--id")),opts.getOrDefault("--cola","COMPLETO"),cl.nexbyte.siger.robustez.JdbcRecepcionConfiguration.publicKeys(mapper,Path.of(config.getProperty("garantia.public-keys"))),cl.nexbyte.siger.robustez.JdbcRecepcionConfiguration.publicKeys(mapper,Path.of(config.getProperty("garantia.institution-keys"))),mapper);
                System.out.println("ENSAYO_RESTAURADO_SIN_FIRMA independencia="+result.independenciaAcreditada()+" durabilidad="+result.durabilidadAcreditada());return 0;
            }
        }finally{config.remove("garantia.origen.db.password");}
    }
    private AgenteCustodiaNativo(){}
}