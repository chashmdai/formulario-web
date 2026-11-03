package cl.nexbyte.siger.srcei;

import java.nio.file.Path;

/** X509 efímero del test: nunca contiene material institucional ni depende de un certificado versionado. */
public final class CertificadoPrueba {
    private CertificadoPrueba() {}
    public static Path crear(Path dir) throws Exception {
        Path store=dir.resolve("synthetic-test.p12"),cert=dir.resolve("synthetic-test.cer");
        String keytool=Path.of(System.getProperty("java.home"),"bin","keytool").toString();
        for(var command:java.util.List.of(
            new String[]{keytool,"-genkeypair","-alias","test","-keyalg","RSA","-keysize","2048","-dname","CN=Synthetic Test","-validity","1","-keystore",store.toString(),"-storepass","synthetic-test-password","-noprompt"},
            new String[]{keytool,"-exportcert","-alias","test","-keystore",store.toString(),"-storepass","synthetic-test-password","-file",cert.toString()})) {
            var p=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if(p.waitFor()!=0)throw new IllegalStateException("No se pudo generar certificado sintético del test");
        }
        return cert;
    }
}
