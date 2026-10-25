package cl.nexbyte.siger.custodia;
import cl.nexbyte.siger.formulario.*;
import cl.nexbyte.siger.robustez.*;
import java.io.*;import java.nio.file.*;import java.nio.file.attribute.*;import java.security.*;import java.time.*;import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

/** Harness sintético: distribuido separado del agente; H2 solo para QA, sin dependencia productiva. */
public final class PruebaAgenteNativo {
    static int passed;
    static void check(boolean condition){if(!condition)throw new AssertionError("Verificación sintética no satisfecha");passed++;}
    interface Checked{void run()throws Exception;}
    static void reject(Checked action)throws Exception{try{action.run();}catch(IOException|IllegalArgumentException expected){passed++;return;}throw new AssertionError("No rechazó el caso inseguro");}
    static Path dir(Path p)throws IOException{ArchivoAgente.directorio(p);return p;}
    static String java(){return Path.of(System.getProperty("java.home"),"bin",ArchivoAgente.windows()?"java.exe":"java").toString();}
    static Process child(String mode,Path base)throws IOException{return new ProcessBuilder(java(),"--enable-native-access=ALL-UNNAMED","-cp",System.getProperty("java.class.path"),PruebaAgenteNativo.class.getName(),mode,base.toString()).redirectErrorStream(true).start();}
    static void ready(Path p)throws Exception{long end=System.nanoTime()+10_000_000_000L;while(!Files.exists(p)&&System.nanoTime()<end)Thread.sleep(20);check(Files.exists(p));}
    static JdbcTemplate tables(){var ds=new DriverManagerDataSource("jdbc:h2:mem:agente_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");var db=new JdbcTemplate(ds);
        db.execute("CREATE TABLE institutional_inbox(id VARCHAR(36),source VARCHAR(40),body VARCHAR(1000000),receipt VARCHAR(100000),document_kind VARCHAR(40))");
        db.execute("CREATE TABLE communication_registry(source VARCHAR(40),operacion_id VARCHAR(36),comunicacion_id VARCHAR(36),numero BIGINT,canal VARCHAR(40),content_hash VARCHAR(64),provenance_hash VARCHAR(64),registrada_ms BIGINT,folio_prefix VARCHAR(20),folio_width INT)");
        db.execute("CREATE TABLE institution_document(id VARCHAR(36),operation_id VARCHAR(36),communication_id VARCHAR(36),attachment_id VARCHAR(36),name VARCHAR(180),size_bytes BIGINT,sha256 VARCHAR(64),media_type VARCHAR(100),storage_key VARCHAR(80),origin VARCHAR(40))");return db;
    }
    static String pem(PublicKey key){return "-----BEGIN PUBLIC KEY-----\n"+Base64.getEncoder().encodeToString(key.getEncoded())+"\n-----END PUBLIC KEY-----\n";}
    static void restoreInNewProcess(Path work)throws Exception{
        var mapper=JsonMapper.builder().build();var pub=JdbcRecepcionConfiguration.publicKeys(mapper,work.resolve("public-keys.json"));var inst=JdbcRecepcionConfiguration.publicKeys(mapper,work.resolve("institution-keys.json"));
        Path packet;try(var entries=Files.list(work.resolve("respaldo"))){packet=entries.filter(p->p.getFileName().toString().startsWith("completo-")).findFirst().orElseThrow();}
        var saved=mapper.readValue(Files.readString(packet.resolve("registro.json")),RespaldoIngresoVerificado.Paquete.class);var m=saved.registro();var context=EmisorGarantiaCustodia.verifyMetadata(m,"COMPLETO",pub,inst,mapper);var db=tables();
        db.update("INSERT INTO institutional_inbox VALUES(?,?,?,?,?)",context.id().toString(),"FORMULARIO_PUBLICO_DDN",m.get("body"),m.get("receipt"),m.get("document_kind"));
        db.update("INSERT INTO communication_registry VALUES(?,?,?,?,?,?,?,?,?,?)","FORMULARIO_PUBLICO_DDN",context.id().toString(),m.get("comunicacion_id"),m.get("numero"),m.get("canal"),m.get("content_hash"),m.get("provenance_hash"),m.get("registrada_ms"),m.get("folio_prefix"),m.get("folio_width"));
        for(var d:saved.documentos())db.update("INSERT INTO institution_document VALUES(?,?,?,?,?,?,?,?,?,?)",d.get("id"),d.get("operation_id"),d.get("communication_id"),d.get("attachment_id"),d.get("name"),d.get("size_bytes"),d.get("sha256"),d.get("media_type"),d.get("storage_key"),d.get("origin"));
        var restored=RespaldoIngresoVerificado.ensayar(db,work.resolve("origen"),work.resolve("respaldo"),work.resolve("scratch"),context.id(),"COMPLETO",pub,inst,mapper);if(!restored.contexto().equals(context)||restored.independenciaAcreditada())throw new AssertionError();System.out.println("RESTAURACION_TRAS_REINICIO_OK: solo fixtures y sin garantía");
    }
    public static void main(String[] args)throws Exception{
        String mode=args[0];Path base=Path.of(args[1]);
        if(mode.equals("--reanudar")){restoreInNewProcess(base);return;}
        if(mode.equals("--sostener")){try(var lock=new AgenteCustodiaNativo.Guardia(base)){ArchivoAgente.escribir(base.resolve("ready"),new byte[0]);long end=System.nanoTime()+20_000_000_000L;while(!Files.exists(base.resolve("stop"))&&System.nanoTime()<end)Thread.sleep(20);}return;}
        if(mode.equals("--intentar")){try(var lock=new AgenteCustodiaNativo.Guardia(base)){}catch(AgenteCustodiaNativo.Ocupado e){System.exit(12);}return;}
        if(mode.equals("--interrumpir")){try(var lock=new AgenteCustodiaNativo.Guardia(base)){var own=ArchivoAgente.temporal(base,"restauracion-ingreso-");ArchivoAgente.escribir(own.resolve("parcial"),new byte[]{1,2});Runtime.getRuntime().halt(27);}return;}
        if(!mode.equals("--pruebas"))throw new IllegalArgumentException();
        ArchivoAgente.raiz(base);Path work=dir(base.resolve("qa-"+UUID.randomUUID())),source=dir(work.resolve("origen")),backup=dir(work.resolve("respaldo")),scratch=dir(work.resolve("scratch"));
        check(ArchivoAgente.windows());check(Runtime.version().feature()==25);check(!ArchivoAgente.independiente(source,backup));check(ArchivoAgente.durabilidadAcreditada(backup));
        UUID id=UUID.randomUUID(),com=UUID.randomUUID(),attachment=UUID.randomUUID();byte[] bytes="EVIDENCIA EXCLUSIVAMENTE SINTETICA SIN PERSONAS".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var object=dir(dir(source.resolve("objects")).resolve(id.toString())).resolve(attachment+".bin");ArchivoAgente.escribir(object,bytes);check(Files.getFileAttributeView(object,AclFileAttributeView.class).getAcl().size()>=1);
        var publicKey=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();var inst=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();var issuer=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();var mapper=JsonMapper.builder().build();var now=Instant.now();var clock=Clock.fixed(now,ZoneOffset.UTC);
        var descriptor=new AdjuntosFormulario.Descriptor(attachment,"sintetico.txt",bytes.length,ArchivoAgente.huella(object),"text/plain","VALIDADO");
        String version=args.length>2?args[2]:"1.1";if(!Set.of("1.0","1.1").contains(version))throw new IllegalArgumentException();var content=new EntregaFormulario.Contenido(version,id,UUID.randomUUID(),IdentificacionCiudadana.Tipo.NNA,now,"QA_WINDOWS",false,new DatosFormulario(Map.of("nna-story","PRUEBA SINTETICA"),List.of(),List.of(),List.of(attachment)),List.of(),List.of(descriptor));
        String sha=CanonFormulario.huella("CONTENIDO",content,mapper),provenance=EntregaFormulario.procedencia(content,mapper);
        var receipt=ComprobanteDigital.firmar(new ComprobanteDigital.Contenido("1.0",id,now,"CUSTODIA_PUBLICA",sha,provenance,"FORMULARIO_PUBLICO_DDN","QA_WINDOWS"),"sintetico-publico",publicKey.getPrivate(),mapper);var document=new EntregaFormulario(content,sha,provenance,receipt);
        var registered=new ComprobanteInstitucional.Contenido("1.1","SIGER_DDN","COMUNICACION_REGISTRADA",id,com,1,"COM-000001",now,"FORMULARIO_WEB_NNA",sha,provenance);var ack=new RegistroInstitucional(registered,ComprobanteInstitucional.firmar(registered,"sintetico-institucion",inst.getPrivate(),mapper));
        var db=tables();db.update("INSERT INTO institutional_inbox VALUES(?,?,?,?,?)",id.toString(),"FORMULARIO_PUBLICO_DDN",mapper.writeValueAsString(document),mapper.writeValueAsString(ack),"FORMULARIO_COMPLETO");db.update("INSERT INTO communication_registry VALUES(?,?,?,?,?,?,?,?,?,?)","FORMULARIO_PUBLICO_DDN",id.toString(),com.toString(),1,"FORMULARIO_WEB_NNA",sha,provenance,now.toEpochMilli(),"COM",6);db.update("INSERT INTO institution_document VALUES(?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),id.toString(),com.toString(),attachment.toString(),descriptor.nombre(),bytes.length,descriptor.sha256(),descriptor.tipo(),id+"/"+attachment,"INGRESO_CIUDADANO");
        ArchivoAgente.escribir(work.resolve("public-keys.json"),mapper.writeValueAsBytes(Map.of("sintetico-publico",pem(publicKey.getPublic()))));ArchivoAgente.escribir(work.resolve("institution-keys.json"),mapper.writeValueAsBytes(Map.of("sintetico-institucion",pem(inst.getPublic()))));
        Map<String,PublicKey> pub=Map.of("sintetico-publico",publicKey.getPublic()),institution=Map.of("sintetico-institucion",inst.getPublic());
        var result=RespaldoIngresoVerificado.ensayar(db,source,backup,scratch,id,"COMPLETO",pub,institution,mapper);check(result.contexto().id().equals(id));check(!result.independenciaAcreditada()&&result.durabilidadAcreditada());
        Path packet=backup.resolve("completo-"+id),original=packet.resolve("objects").resolve(id.toString()).resolve(attachment+".bin");check(Arrays.equals(bytes,Files.readAllBytes(original)));var stamp=Files.getLastModifiedTime(packet.resolve("registro.json"));
        RespaldoIngresoVerificado.ensayar(db,source,backup,scratch,id,"COMPLETO",pub,institution,mapper);check(stamp.equals(Files.getLastModifiedTime(packet.resolve("registro.json"))));
        reject(()->RespaldoIngresoVerificado.emitir(db,source,backup,scratch,id,"COMPLETO",pub,institution,"sintetico-NO-CONFIAR",issuer.getPrivate(),clock,mapper));
        var synthetic=new GarantiaCustodia.Contenido(result.contexto(),now,now.plusSeconds(3600),UUID.randomUUID());var sealed=FirmaOperativa.firmar(synthetic,GarantiaCustodia.DOMAIN,"sintetico-NO-CONFIAR",issuer.getPrivate(),mapper);check(GarantiaCustodia.validar(sealed,Map.of("sintetico-NO-CONFIAR",issuer.getPublic()),clock,mapper).equals(synthetic));
        var invalid=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();reject(()->GarantiaCustodia.validar(sealed,Map.of("sintetico-NO-CONFIAR",invalid.getPublic()),clock,mapper));
        Files.setAttribute(original,"dos:readonly",false);Files.writeString(original,"CORRUPCION SINTETICA");reject(()->RespaldoIngresoVerificado.ensayar(db,source,backup,scratch,id,"COMPLETO",pub,institution,mapper));check(Files.readString(original).equals("CORRUPCION SINTETICA"));Files.write(original,bytes);Files.setAttribute(original,"dos:readonly",true);
        db.update("UPDATE communication_registry SET content_hash=?","0".repeat(64));reject(()->RespaldoIngresoVerificado.ensayar(db,source,backup,scratch,id,"COMPLETO",pub,institution,mapper));db.update("UPDATE communication_registry SET content_hash=?",sha);check(stamp.equals(Files.getLastModifiedTime(packet.resolve("registro.json"))));
        Path hard=work.resolve("hardlink");Files.createLink(hard,object);try{reject(()->ArchivoAgente.ruta(hard));}finally{Files.delete(hard);}check(ArchivoAgente.huella(object).equals(descriptor.sha256()));
        Path exposed=dir(work.resolve("acl-amplia"));var acl=Files.getFileAttributeView(exposed,AclFileAttributeView.class);var safe=acl.getAcl();UserPrincipal everyone;try{everyone=FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByName("Everyone");}catch(UserPrincipalNotFoundException e){everyone=FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByName("Todos");}var broad=new ArrayList<>(safe);broad.add(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(everyone).setPermissions(AclEntryPermission.READ_DATA).build());acl.setAcl(broad);try{reject(()->ArchivoAgente.raiz(exposed));}finally{acl.setAcl(safe);}
        reject(()->ArchivoAgente.ruta(work.resolve("invalido:ads")));reject(()->ArchivoAgente.ruta(Path.of("\\\\localhost\\c$\\agente")));
        Path junction=work.resolve("junction");var link=new ProcessBuilder("C:\\Program Files\\PowerShell\\7\\pwsh.exe","-NoProfile","-File","C:\\agente\\scripts\\New-LinkFixture.ps1","-Link",junction.toString(),"-Target",source.toString()).redirectErrorStream(true).start();link.getInputStream().readAllBytes();check(link.waitFor()==0);reject(()->ArchivoAgente.raiz(junction));Files.delete(junction);
        var hold=child("--sostener",scratch);ready(scratch.resolve("ready"));var overlapping=child("--intentar",scratch);overlapping.getInputStream().readAllBytes();check(overlapping.waitFor()==12);ArchivoAgente.escribir(scratch.resolve("stop"),new byte[0]);check(hold.waitFor()==0);
        var crashed=child("--interrumpir",scratch);crashed.getInputStream().readAllBytes();check(crashed.waitFor()==27);try(var guard=new AgenteCustodiaNativo.Guardia(scratch)){
            var stale=Files.list(scratch).filter(p->p.getFileName().toString().startsWith("restauracion-ingreso-")).toList();check(stale.size()==1);Files.setLastModifiedTime(stale.getFirst(),FileTime.fromMillis(System.currentTimeMillis()-Duration.ofHours(25).toMillis()));check(ArchivoAgente.limpiarVencidos(scratch,System.currentTimeMillis()-Duration.ofHours(24).toMillis())==1);
        }
        RespaldoIngresoVerificado.ensayar(db,source,backup,scratch,id,"COMPLETO",pub,institution,mapper);check(ArchivoAgente.huella(object).equals(descriptor.sha256()));check(stamp.equals(Files.getLastModifiedTime(packet.resolve("registro.json"))));
        var restarted=child("--reanudar",work);var restartOutput=new String(restarted.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);check(restarted.waitFor()==0);check(restartOutput.contains("RESTAURACION_TRAS_REINICIO_OK"));
        System.out.println("PRUEBAS_WINDOWS_NATIVAS_OK verificaciones="+passed+" version="+version+" mysql=false h2=solo-harness independencia=false sincronizacionAPI=true garantiasEnviadas=0");
    }
}