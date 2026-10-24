package cl.nexbyte.siger.formulario;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** INSTREAM sobre servicio privado local. Sin scanner configurado no se aceptan archivos. */
public final class EscanerClamAv implements AdjuntosFormulario.Escaner {
    private final int port;
    public EscanerClamAv(int port){if(port<1024||port>65535)throw new IllegalArgumentException("Puerto de inspección inválido");this.port=port;}
    public boolean limpio(byte[] bytes)throws IOException {
        try(var socket=new Socket()){
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(),port),2000);socket.setSoTimeout(15000);
            var out=new DataOutputStream(socket.getOutputStream());out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            for(int i=0;i<bytes.length;i+=8192){int length=Math.min(8192,bytes.length-i);out.writeInt(length);out.write(bytes,i,length);}out.writeInt(0);out.flush();
            var input=socket.getInputStream();var response=new ByteArrayOutputStream();int b;
            while((b=input.read())!=-1&&b!=0){if(response.size()>=512)throw new IOException("Respuesta de inspección inválida");response.write(b);}
            if(b!=0)throw new IOException("Inspección incompleta");
            var result=response.toString(StandardCharsets.UTF_8);
            if(result.equals("stream: OK"))return true;
            if(result.startsWith("stream: ")&&result.endsWith(" FOUND"))return false;
            throw new IOException("Inspección no disponible");
        }
    }
}
