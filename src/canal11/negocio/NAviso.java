package canal11.negocio;

import canal11.datos.DAviso;
import canal11.datos.DUsuario;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Apoyo de los avisos automaticos: a quien se le envia (por rol o por usuario) y que avisos ya se enviaron.
 * Solo se avisa a usuarios activos y sin repetir correos.
 */
public class NAviso {

    private final DUsuario usuarios = new DUsuario();
    private final DAviso enviados = new DAviso();

    /** Correos de los usuarios activos de esos roles, sin repetir y sin el correo de quien origino el cambio (excluir puede ser null). */
    public List<String> emailsDeRoles(String excluir, Rol... roles) throws SQLException {
        Set<String> emails = new LinkedHashSet<>();
        for (Rol r : roles) {
            for (String e : usuarios.emailsActivosPorRol(r.name())) {
                if (excluir == null || !e.equalsIgnoreCase(excluir)) {
                    emails.add(e);
                }
            }
        }
        return new ArrayList<>(emails);
    }

    /** Correo del usuario (por su id) o null si esta de baja. */
    public String emailDeUsuario(int idUsuario) throws SQLException {
        return usuarios.emailActivo(idUsuario);
    }

    public boolean yaEnviado(String tipo, String clave) throws SQLException {
        return enviados.yaEnviado(tipo, clave);
    }

    public void marcarEnviado(String tipo, String clave) throws SQLException {
        enviados.marcar(tipo, clave);
    }
}
