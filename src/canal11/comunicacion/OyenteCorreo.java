package canal11.comunicacion;

import java.util.List;

/**
 * Lo implementa quien quiera enterarse de los correos nuevos (App).
 * HiloCorreo lo llama cada vez que descarga correos del buzon.
 */
public interface OyenteCorreo {

    void alRecibirCorreos(List<Correo> correos);
}
