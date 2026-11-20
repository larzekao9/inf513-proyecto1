package canal11.presentacion;

import canal11.negocio.Usuario;

import java.util.List;

/** Lo que hace un comando. Los parametros llegan ya con la cantidad correcta. */
@FunctionalInterface
public interface Accion {

    /** Devuelve el contenido HTML (sin la plantilla) y el titulo de la respuesta. */
    Respuesta ejecutar(Usuario usuario, List<String> parametros) throws Exception;
}
