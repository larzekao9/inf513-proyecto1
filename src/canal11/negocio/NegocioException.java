package canal11.negocio;

import java.util.List;

/**
 * Error de validacion o de regla de negocio. Puede traer varios errores a la vez
 * (el Validador junta todos los problemas de los parametros y los informa juntos).
 * Los mensajes pueden traer HTML simple (<b>) y se muestran tal cual al usuario.
 */
public class NegocioException extends Exception {

    private final List<String> errores;

    public NegocioException(String mensaje) {
        super(mensaje);
        this.errores = List.of(mensaje);
    }

    public NegocioException(List<String> errores) {
        super(String.join(" | ", errores));
        this.errores = List.copyOf(errores);
    }

    public List<String> getErrores() {
        return errores;
    }
}
