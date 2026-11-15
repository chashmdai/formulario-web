package cl.nexbyte.siger.config;

import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/** Sonda del contenedor, solo JRE: no arranca Spring ni sigue redirecciones. */
public final class SaludHttp {
    private SaludHttp() {}

    public static void main(String[] args) {
        try {
            if (args.length < 1 || args.length > 2
                    || !disponible(URI.create(args[0]), args.length == 2 ? Path.of(args[1]) : null)) {
                System.exit(1);
            }
        } catch (Exception fallo) {
            // Docker solo necesita el código de salida, sin cuerpos ni datos de sesión en sus logs.
            System.exit(1);
        }
    }

    static boolean disponible(URI uri, Path ca) throws Exception {
        var conexion = (HttpURLConnection) uri.toURL().openConnection(Proxy.NO_PROXY);
        conexion.setConnectTimeout(3000);
        conexion.setReadTimeout(3000);
        conexion.setInstanceFollowRedirects(false);
        try {
            if (ca != null) {
                var confianza = KeyStore.getInstance(KeyStore.getDefaultType());
                confianza.load(null, null);
                try (var input = Files.newInputStream(ca)) {
                    var certificados = CertificateFactory.getInstance("X.509").generateCertificates(input);
                    int indice = 0;
                    for (var certificado : certificados)
                        confianza.setCertificateEntry("ca-" + indice++, certificado);
                    if (indice == 0) throw new IllegalArgumentException("CA vacía");
                }
                var gestores = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                gestores.init(confianza);
                var tls = SSLContext.getInstance("TLS");
                tls.init(null, gestores.getTrustManagers(), null);
                ((HttpsURLConnection) conexion).setSSLSocketFactory(tls.getSocketFactory());
            }
            // Como curl --fail: los errores HTTP (>= 400) cierran el healthcheck.
            int estado = conexion.getResponseCode();
            return estado >= 200 && estado < 400;
        } finally {
            conexion.disconnect();
        }
    }
}
