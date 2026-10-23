package cl.nexbyte.siger.entrega;

public final class EntregaException extends RuntimeException {
    private final int status;
    public EntregaException(int status) { super("No se pudo completar la entrega"); this.status = status; }
    public int status() { return status; }
}
