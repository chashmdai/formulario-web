package cl.nexbyte.siger.formulario;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Servidor TCP controlado: valida INSTREAM; no afirma que exista una instalación antivirus real. */
class EscanerClamAvTest {
    private boolean consulta(String result)throws Exception {
        try(var server=new ServerSocket(0,1,InetAddress.getLoopbackAddress());var pool=Executors.newSingleThreadExecutor()){
            var future=pool.submit(()->{try(var socket=server.accept()){
                var input=new DataInputStream(socket.getInputStream());assertArrayEquals("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII),input.readNBytes(10));
                var bytes=new ByteArrayOutputStream();int size;while((size=input.readInt())!=0){assertTrue(size>0&&size<=8192);bytes.write(input.readNBytes(size));}
                assertEquals("Contenido sintético",bytes.toString(StandardCharsets.UTF_8));socket.getOutputStream().write(result.getBytes(StandardCharsets.UTF_8));return true;
            }});
            try{return new EscanerClamAv(server.getLocalPort()).limpio("Contenido sintético".getBytes(StandardCharsets.UTF_8));}
            finally{assertTrue(future.get(5,TimeUnit.SECONDS));}
        }
    }
    @Test void inspeccionLimpiaSoloAceptaConfirmacionCompleta()throws Exception{assertTrue(consulta("stream: OK\0"));}
    @Test void deteccionRechazaArchivo()throws Exception{assertFalse(consulta("stream: amenaza-sintetica FOUND\0"));}
    @Test void errorORespuestaTruncadaNoEquivaleAArchivoLimpio(){for(var value:new String[]{"stream: OK","stream: Size limit exceeded ERROR\0","x".repeat(513)+"\0"})assertThrows(IOException.class,()->consulta(value));}
}
