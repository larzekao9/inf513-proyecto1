package canal11.datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Avisos automaticos ya enviados (tabla aviso_enviado): evita repetir el mismo aviso en cada vuelta del hilo. */
public class DAviso {

    public boolean yaEnviado(String tipo, String clave) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM aviso_enviado WHERE tipo = ? AND clave = ?")) {
            ps.setString(1, tipo);
            ps.setString(2, clave);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void marcar(String tipo, String clave) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO aviso_enviado (tipo, clave) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            ps.setString(1, tipo);
            ps.setString(2, clave);
            ps.executeUpdate();
        }
    }
}
