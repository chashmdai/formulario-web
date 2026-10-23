package cl.nexbyte.siger.robustez;

import cl.nexbyte.siger.entrega.*;
import cl.nexbyte.siger.identidad.*;
import java.util.List;
import java.util.UUID;

public interface RecepcionDurable extends RecepcionPublica {
    String avisoVersion();
    SobreComunicacion recibirVerificada(UUID clave, IdentidadVerificada identidad, EvidenciaAutenticacion evidencia, String mensaje, String avisoVersion);
    SobreComunicacion recibirVerificadaEnSesion(UUID clave, IdentidadVerificada identidad, EvidenciaAutenticacion evidencia, String mensaje, String avisoVersion,String sesion);
    SobreComunicacion recibirNna(UUID clave,cl.nexbyte.siger.contrato.DeclaracionNna datos,String mensaje,String avisoVersion,String sesion);
    Estado consultarSesion(UUID id,String sesion);
    ComprobanteInstitucional comprobante(UUID id, String run);
    List<EntregaReservada> reclamar(int limite);
    void confirmarReserva(UUID id, UUID reserva, SobreComunicacion.Confirmacion confirmacion);
    void confirmarRegistro(UUID id, UUID reserva, RegistroInstitucional registro);
    void fallo(UUID id, UUID reserva, String codigo);
    record EntregaReservada(UUID reserva, long venceEnEpochMs, int intento, EntregaVerificada entrega) {}
}
