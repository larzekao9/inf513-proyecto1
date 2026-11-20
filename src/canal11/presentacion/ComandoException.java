package canal11.presentacion;

/** Error en la forma del comando (formato, cantidad de parametros, permisos). */
public class ComandoException extends Exception {

    public ComandoException(String mensaje) {
        super(mensaje);
    }
}
