package cl.nexbyte.siger.custodia;

import java.io.*;import java.nio.file.*;import java.security.*;import java.time.*;import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import cl.nexbyte.siger.robustez.JdbcRecepcionConfiguration;

/**
 * Acreditación de preparación, separada de la autorización para emitir.
 * Una firma de TI no sustituye topología vigente, restauración ni controles por ingreso.
 */
public final class AcreditacionAlmacenWindows {
 public static final String DOMAIN="DDN-ACREDITACION-ALMACEN-WINDOWS-1";
 public record Topologia(String proveedor,String equipo,String volumenOrigen,String discoOrigen,String volumenRespaldo,String discoRespaldo){}
 public record Evidencia(String version,Topologia topologia,String raizOrigen,String raizRespaldo,String dominioOrigen,String dominioRespaldo,
                         String referenciaTI,String huellaInformeRecuperacion,Instant restauracionEn,Instant emitidaEn,Instant validaHasta){}
 public record Resultado(boolean acreditada,String codigo){}
 @FunctionalInterface interface Proveedor {Topologia obtener(Path origen,Path respaldo)throws Exception;}
 static Resultado verificar(FirmaOperativa signed,Map<String,PublicKey> keys,Path origin,Path backup,Clock clock,JsonMapper mapper,Proveedor provider){
  try{
   Evidencia e=signed.leer(DOMAIN,Evidencia.class,keys,mapper);Instant now=clock.instant();
   if(!"1.0".equals(e.version())||e.emitidaEn().isAfter(now)||!e.validaHasta().isAfter(now)||e.validaHasta().isAfter(e.emitidaEn().plus(Duration.ofDays(90)))||
      e.restauracionEn().isAfter(now)||e.restauracionEn().isBefore(now.minus(Duration.ofDays(30)))||e.restauracionEn().isAfter(e.emitidaEn())||
      !e.huellaInformeRecuperacion().matches("[a-f0-9]{64}")||e.referenciaTI().isBlank()||e.dominioOrigen().isBlank()||e.dominioRespaldo().isBlank()||e.dominioOrigen().equals(e.dominioRespaldo()))return new Resultado(false,"EVIDENCIA_INSUFICIENTE");
   Path a=ArchivoAgente.raiz(origin),b=ArchivoAgente.raiz(backup);
   if(a.startsWith(b)||b.startsWith(a)||!a.toString().equalsIgnoreCase(e.raizOrigen())||!b.toString().equalsIgnoreCase(e.raizRespaldo()))return new Resultado(false,"RUTAS_DISCREPANTES");
   Topologia t=provider.obtener(a,b);
   if(!t.equals(e.topologia())||!"NTFS_LOCAL_V1".equals(t.proveedor())||t.equipo().isBlank()||t.discoOrigen().isBlank()||t.discoRespaldo().isBlank()||
      t.discoOrigen().equalsIgnoreCase(t.discoRespaldo())||t.volumenOrigen().isBlank()||t.volumenRespaldo().isBlank()||t.volumenOrigen().equalsIgnoreCase(t.volumenRespaldo()))return new Resultado(false,"DOMINIO_FALLO_NO_ACREDITADO");
   if(!ArchivoAgente.durabilidadAcreditada(b))return new Resultado(false,"DURABILIDAD_NO_ACREDITADA");
   return new Resultado(true,"PREPARACION_ACREDITADA_EMISION_AUN_BLOQUEADA");
  }catch(Exception invalid){return new Resultado(false,"ACREDITACION_NO_VERIFICADA");}
 }
 public static Resultado inspeccionar(Properties p,Path origin,Path backup,Clock clock,JsonMapper mapper){
  if(!p.containsKey("garantia.acreditacion.documento"))return new Resultado(false,"NO_APORTADA");
  try{
   Path file=Path.of(p.getProperty("garantia.acreditacion.documento")),keyFile=Path.of(p.getProperty("garantia.acreditacion.claves-ti"));
   for(Path f:List.of(file,keyFile)){ArchivoAgente.ruta(f);ArchivoAgente.permisos(f);if(Files.size(f)>65536)throw new IOException();}
   return verificar(mapper.readValue(Files.readString(file),FirmaOperativa.class),JdbcRecepcionConfiguration.publicKeys(mapper,keyFile),origin,backup,clock,mapper,AcreditacionAlmacenWindows::topologia);
  }catch(Exception invalid){return new Resultado(false,"ACREDITACION_NO_VERIFICADA");}
 }
 /** Capacidad no construible por el llamador: configuración + firma TI + prueba actual. No existe override booleano. */
 public static final class Permiso {
  private final Properties config=new Properties();private final Path origin,backup;
  private Permiso(Properties p,Path a,Path b){origin=a;backup=b;for(String k:List.of("garantia.acreditacion.documento","garantia.acreditacion.claves-ti","garantia.windows.autorizacion-referencia"))config.setProperty(k,p.getProperty(k));}
  private Evidencia evidencia()throws IOException{
   try{var mapper=JsonMapper.builder().build();Path f=Path.of(config.getProperty("garantia.acreditacion.documento")),k=Path.of(config.getProperty("garantia.acreditacion.claves-ti"));
    for(Path path:List.of(f,k)){ArchivoAgente.ruta(path);ArchivoAgente.permisos(path);if(Files.size(path)>65536)throw new IOException();}
    var e=mapper.readValue(Files.readString(f),FirmaOperativa.class).leer(DOMAIN,Evidencia.class,JdbcRecepcionConfiguration.publicKeys(mapper,k),mapper);
    if(!e.referenciaTI().equals(config.getProperty("garantia.windows.autorizacion-referencia")))throw new IOException("Autorización no corresponde");return e;
   }catch(Exception invalid){throw new IOException("Autorización Windows no acreditada");}
  }
  void comprobar(Path a,Path b)throws IOException{
   if(!ArchivoAgente.windows()||!ArchivoAgente.raiz(a).equals(origin)||!ArchivoAgente.raiz(b).equals(backup))throw new IOException("Permiso de otro destino");
   evidencia();
   if(!inspeccionar(config,origin,backup,Clock.systemUTC(),JsonMapper.builder().build()).acreditada())throw new IOException("Acreditación revocada o cambiada");
  }
  public Instant limitarVencimiento(Instant requested)throws IOException{comprobar(origin,backup);var e=evidencia();Instant end=e.validaHasta().isBefore(e.restauracionEn().plus(Duration.ofDays(30)))?e.validaHasta():e.restauracionEn().plus(Duration.ofDays(30));return end.isBefore(requested)?end:requested;}
 }
 public static Permiso autorizar(Properties p,Path origin,Path backup)throws IOException{
  if(!ArchivoAgente.windows()||!"true".equals(p.getProperty("garantia.windows.emision-habilitada"))||
     p.getProperty("garantia.windows.autorizacion-referencia","").isBlank())throw new IOException("Emisión Windows cerrada");
  try{var permission=new Permiso(p,ArchivoAgente.raiz(origin),ArchivoAgente.raiz(backup));permission.comprobar(origin,backup);return permission;}
  catch(Exception invalid){throw new IOException("Emisión Windows sin garantías suficientes");}
 }
 static Topologia topologia(Path origin,Path backup)throws Exception{
  if(!ArchivoAgente.windows())throw new IOException("Solo Windows");
  ArchivoAgente.raiz(origin);ArchivoAgente.raiz(backup);
  String script="$ErrorActionPreference='Stop';$i=[Console]::In.ReadToEnd()|ConvertFrom-Json;$v=@();foreach($p in @($i.origen,$i.respaldo)){$letter=[IO.Path]::GetPathRoot($p).Substring(0,1);$part=Get-Partition -DriveLetter $letter;$disk=$part|Get-Disk;$vol=Get-Volume -DriveLetter $letter;if($vol.FileSystem -ne 'NTFS' -or -not $disk.UniqueId -or -not $vol.UniqueId){throw 'Topologia no acreditable'};$v+=@{volumen=$vol.UniqueId;disco=$disk.UniqueId}};@{proveedor='NTFS_LOCAL_V1';equipo=$env:COMPUTERNAME;volumenOrigen=$v[0].volumen;discoOrigen=$v[0].disco;volumenRespaldo=$v[1].volumen;discoRespaldo=$v[1].disco}|ConvertTo-Json -Compress";
  var process=new ProcessBuilder("C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe","-NoProfile","-NonInteractive","-Command",script).redirectError(ProcessBuilder.Redirect.DISCARD).start();
  var mapper=JsonMapper.builder().build();
  try{
   try(var out=process.getOutputStream()){out.write(mapper.writeValueAsBytes(Map.of("origen",origin.toString(),"respaldo",backup.toString())));}
   // Salida pequeña por contrato; lector concurrente para no bloquear waitFor.
   var read=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return process.getInputStream().readNBytes(8193);}catch(IOException e){throw new java.io.UncheckedIOException(e);}});
   if(!process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("Topologia timeout");}
   byte[] data=read.get(2,java.util.concurrent.TimeUnit.SECONDS);if(process.exitValue()!=0||data.length>8192)throw new IOException("Topologia no acreditada");
   return mapper.readValue(data,Topologia.class);
  }finally{process.destroy();}
 }
 private AcreditacionAlmacenWindows(){}
}
