package cl.nexbyte.siger.robustez;
public final class FalloTecnico extends RuntimeException{
    public final int status;
    public FalloTecnico(int status){super("Operación técnica no completada");this.status=status;}
}
