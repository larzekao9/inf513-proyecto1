package canal11.datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

/**
 * CU7 - Capa de datos de la tabla emision (lo que realmente salio al aire en cada bloque).
 * Solo SQL (siempre con PreparedStatement). Las reglas de negocio van en NEmision.
 */
public class DEmision {

    /**
     * Conciliacion de una parrilla: un arreglo por bloque, ordenado por hora (en este orden):
     * 0 id del bloque, 1 hora inicio programada, 2 hora fin programada, 3 tipo, 4 programa,
     * 5 estado (EMITIDO, PARCIAL, NO_EMITIDO o SIN REGISTRO), 6 inicio real (HH:MM:SS), 7 fin real (HH:MM:SS),
     * 8 desviacion del inicio en segundos (real - programado; vacio si no salio al aire o no hay registro)
     */
    private static final String CONCILIACION =
            "SELECT b.id_bloque, to_char(b.hora_inicio, 'HH24:MI:SS'), to_char(b.hora_fin, 'HH24:MI:SS'), b.tipo,"
            + " coalesce(p.nombre, ''), coalesce(e.estado, 'SIN REGISTRO'),"
            + " CASE WHEN e.estado IN ('EMITIDO', 'PARCIAL') THEN to_char(e.inicio_real, 'HH24:MI:SS') ELSE '' END,"
            + " CASE WHEN e.estado IN ('EMITIDO', 'PARCIAL') AND e.fin_real IS NOT NULL THEN to_char(e.fin_real, 'HH24:MI:SS') ELSE '' END,"
            + " CASE WHEN e.estado IN ('EMITIDO', 'PARCIAL')"
            + "      THEN CAST(extract(epoch FROM (e.inicio_real - (pr.fecha + b.hora_inicio))) AS bigint)::text ELSE '' END"
            + " FROM bloque b"
            + " JOIN parrilla pr ON pr.id_parrilla = b.id_parrilla"
            + " LEFT JOIN programa p ON p.id_programa = b.id_programa"
            + " LEFT JOIN emision e ON e.id_bloque = b.id_bloque"
            + " WHERE b.id_parrilla = ? ORDER BY b.hora_inicio";

    public List<String[]> conciliar(int idParrilla) throws SQLException {
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(CONCILIACION)) {
            ps.setInt(1, idParrilla);
            try (ResultSet rs = ps.executeQuery()) {
                return Conexion.leerFilas(rs);
            }
        }
    }

    public boolean existeParaBloque(int idBloque) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM emision WHERE id_bloque = ?")) {
            ps.setInt(1, idBloque);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Registra la emision del bloque y actualiza el estado de los spots PROGRAMADOS de ese bloque, todo en una
     * transaccion: EMITIDO -> sus spots quedan EMITIDO; NO_EMITIDO -> NO_EMITIDO; PARCIAL -> EMITIDO los que
     * caen entre inicio_real y fin_real y NO_EMITIDO los demas. Devuelve el id de la emision.
     */
    public int registrar(int idBloque, Timestamp inicioReal, Timestamp finReal, String estado, int registradoPor)
            throws SQLException {
        try (Connection c = Conexion.abrir()) {
            c.setAutoCommit(false);
            try {
                int id;
                String sql = "INSERT INTO emision (id_bloque, inicio_real, fin_real, estado, registrado_por) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, idBloque);
                    ps.setTimestamp(2, inicioReal);
                    if (finReal == null) {
                        ps.setNull(3, Types.TIMESTAMP);
                    } else {
                        ps.setTimestamp(3, finReal);
                    }
                    ps.setString(4, estado);
                    ps.setInt(5, registradoPor);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        rs.next();
                        id = rs.getInt(1);
                    }
                }
                actualizarSpots(c, idBloque, inicioReal, finReal, estado);
                c.commit();
                return id;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private void actualizarSpots(Connection c, int idBloque, Timestamp inicioReal, Timestamp finReal, String estado)
            throws SQLException {
        if ("PARCIAL".equals(estado)) {
            String sql = "UPDATE spot SET estado = CASE WHEN hora >= ? AND hora < ? THEN 'EMITIDO' ELSE 'NO_EMITIDO' END"
                    + " WHERE id_bloque = ? AND estado = 'PROGRAMADO'";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setTime(1, Time.valueOf(inicioReal.toLocalDateTime().toLocalTime()));
                ps.setTime(2, Time.valueOf(finReal.toLocalDateTime().toLocalTime()));
                ps.setInt(3, idBloque);
                ps.executeUpdate();
            }
        } else {
            try (PreparedStatement ps = c.prepareStatement("UPDATE spot SET estado = ? WHERE id_bloque = ? AND estado = 'PROGRAMADO'")) {
                ps.setString(1, estado);
                ps.setInt(2, idBloque);
                ps.executeUpdate();
            }
        }
    }
}
