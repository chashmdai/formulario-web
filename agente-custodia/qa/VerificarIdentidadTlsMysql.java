package cl.nexbyte.siger.custodia;
import java.io.*;import java.net.*;import java.nio.file.*;import java.security.*;import javax.net.ssl.*;
/** Prueba de identidad TLS SIN usuario/contraseña y SIN SQL, con el certificado público observado. */
public final class VerificarIdentidadTlsMysql {
 public static void main(String[] args)throws Exception{
  var trust=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(Path.of(args[0]))){trust.load(in,new char[0]);}var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(trust);var context=SSLContext.getInstance("TLS");context.init(null,tm.getTrustManagers(),null);
  try(var socket=new Socket("127.0.0.1",3306)){socket.setSoTimeout(5000);var h=socket.getInputStream().readNBytes(4);int n=(h[0]&255)|((h[1]&255)<<8)|((h[2]&255)<<16);if(n>65536||socket.getInputStream().readNBytes(n).length!=n)throw new IOException();int caps=0x200|0x800|0x8000;byte[] request=new byte[36];request[0]=32;request[3]=1;for(int i=0;i<4;i++)request[4+i]=(byte)(caps>>>(8*i));request[11]=1;request[12]=45;socket.getOutputStream().write(request);socket.getOutputStream().flush();
   try(var tls=(SSLSocket)context.getSocketFactory().createSocket(socket,"localhost",3306,false)){var parameters=tls.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");tls.setSSLParameters(parameters);tls.startHandshake();System.out.println("TLS_IDENTIDAD_LOCALHOST_OK");}
   catch(SSLHandshakeException e){boolean mismatch=false;for(Throwable t=e;t!=null;t=t.getCause())if(t.getMessage()!=null&&(t.getMessage().contains("localhost")||t.getMessage().contains("No name matching")))mismatch=true;if(!mismatch)throw new IOException("Fallo TLS distinto de identidad");System.out.println("TLS_RECHAZADO_IDENTIDAD_LOCALHOST_NO_COINCIDE: confianza pública cargada; cero autenticaciones/SQL");}
  }
 }
}