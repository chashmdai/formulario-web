package cl.nexbyte.siger.custodia;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import javax.net.ssl.*;
/** Sonda SIN autenticación ni SQL: observa certificados públicos; nunca entrega una conexión al agente. */
public final class SondaTlsMysql {
    private static byte[] packet(InputStream in)throws IOException{byte[] h=in.readNBytes(4);if(h.length!=4)throw new IOException();int n=(h[0]&255)|((h[1]&255)<<8)|((h[2]&255)<<16);if(n>65536)throw new IOException();byte[] b=in.readNBytes(n);if(b.length!=n)throw new IOException();return b;}
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("certificado y truststore NUEVOS requeridos");Path certFile=Path.of(args[0]),trustFile=Path.of(args[1]);ArchivoAgente.ruta(certFile);ArchivoAgente.ruta(trustFile);
        try(var socket=new Socket()){socket.connect(new InetSocketAddress("127.0.0.1",3306),3000);socket.setSoTimeout(5000);packet(socket.getInputStream());
            int caps=0x00000200|0x00000800|0x00008000;byte[] request=new byte[36];request[0]=32;request[3]=1;for(int i=0;i<4;i++)request[4+i]=(byte)(caps>>>(8*i));request[8]=0;request[9]=0;request[10]=0;request[11]=1;request[12]=45;socket.getOutputStream().write(request);socket.getOutputStream().flush();
            X509Certificate[][] observed=new X509Certificate[1][];var context=SSLContext.getInstance("TLS");context.init(null,new TrustManager[]{new X509TrustManager(){public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}public void checkClientTrusted(X509Certificate[] chain,String a){throw new UnsupportedOperationException();}public void checkServerTrusted(X509Certificate[] chain,String a){observed[0]=chain.clone();}}},null);
            try(var tls=(SSLSocket)context.getSocketFactory().createSocket(socket,"localhost",3306,false)){tls.setSoTimeout(5000);tls.startHandshake();var cert=observed[0][0];
                ArchivoAgente.escribir(certFile,cert.getEncoded());var trust=KeyStore.getInstance("PKCS12");trust.load(null,new char[0]);for(int i=0;i<observed[0].length;i++)trust.setCertificateEntry("lab-observado-"+i,observed[0][i]);var buffer=new ByteArrayOutputStream();trust.store(buffer,new char[0]);ArchivoAgente.escribir(trustFile,buffer.toByteArray());
                System.out.println("SONDA_NO_AUTENTICADA_SIN_CREDENCIALES protocolo="+tls.getSession().getProtocol());System.out.println("CERTIFICADO_PUBLICO subject="+cert.getSubjectX500Principal()+" SAN="+cert.getSubjectAlternativeNames()+" SHA256="+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded())));
            }
        }
    }
    private SondaTlsMysql(){}
}