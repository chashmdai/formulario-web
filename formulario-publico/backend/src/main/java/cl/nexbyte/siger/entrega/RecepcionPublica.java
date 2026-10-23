package cl.nexbyte.siger.entrega;

import java.util.List;
import java.util.UUID;

/** Custodia transitoria pública. Su confirmación NO es un ingreso institucional. */
public interface RecepcionPublica {
    boolean disponible();
    SobreComunicacion recibir(UUID clave, SobreComunicacion.Identidad identidad, String mensaje);
    Estado consultar(UUID id, String run);
    List<SobreComunicacion> pendientes(int limite);
    void confirmar(UUID id, SobreComunicacion.Confirmacion confirmacion);

    record Estado(UUID id, String estado, SobreComunicacion.Confirmacion confirmacion,
            cl.nexbyte.siger.robustez.ComprobanteInstitucional comprobante, cl.nexbyte.siger.robustez.RegistroInstitucional registroInstitucional) {
        public Estado(UUID id,String estado,SobreComunicacion.Confirmacion confirmacion) { this(id,estado,confirmacion,null,null); }
    }
}
