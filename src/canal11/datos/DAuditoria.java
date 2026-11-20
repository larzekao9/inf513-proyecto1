package canal11.datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * CU7 - Capa de datos de la tabla auditoria.
 * Solo INSERT y SELECT: la auditoria nunca se modifica ni se borra.
 */
public class DAuditoria {

    /** Maximo de registros que devuelve listar (los mas recientes primero). */
    public static final int MAXIMO = 100;

    /**
     * Historial de cambios, mas recientes primero. entidad != null: solo esa entidad. fecha != null: solo ese dia.
     * Columnas: 0 id, 1 fecha y hora, 2 usuario, 3 accion, 4 entidad, 5 id de la entidad, 6 detalle, 7 comando
     */
    public java.util.List<String[]> listar(String entidad, java.time.LocalDate fecha) throws SQLException {
        String sql = "SELECT a.id_auditoria, to_char(a.fecha, 'DD/MM/YYYY HH24:MI:SS'),"
                + " coalesce(u.nombre || ' ' || u.apellido, '(sistema)'), a.accion, a.entidad, coalesce(a.id_entidad::text, ''),"
                + " coalesce(a.detalle, ''), coalesce(a.comando, '')"
                + " FROM auditoria a LEFT JOIN usuario u ON u.id_usuario = a.id_usuario WHERE true"
                + (entidad != null ? " AND lower(a.entidad) = lower(?)" : "")
                + (fecha != null ? " AND a.fecha >= ? AND a.fecha < ?" : "")
                + " ORDER BY a.fecha DESC, a.id_auditoria DESC LIMIT " + MAXIMO;
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            if (entidad != null) {
                ps.setString(i++, entidad);
            }
            if (fecha != null) {
                ps.setTimestamp(i++, java.sql.Timestamp.valueOf(fecha.atStartOfDay()));
                ps.setTimestamp(i, java.sql.Timestamp.valueOf(fecha.plusDays(1).atStartOfDay()));
            }
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                return Conexion.leerFilas(rs);
            }
        }
    }

    public void insertar(Integer idUsuario, String accion, String entidad, Integer idEntidad, String detalle, String comando)
            throws SQLException {
        String sql = "INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, comando) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (idUsuario == null) {
                ps.setNull(1, Types.INTEGER); // accion automatica del sistema: se muestra como "(sistema)"
            } else {
                ps.setInt(1, idUsuario);
            }
            ps.setString(2, accion);
            ps.setString(3, entidad);
            if (idEntidad == null) {
                ps.setNull(4, Types.INTEGER);
            } else {
                ps.setInt(4, idEntidad);
            }
            ps.setString(5, recortar(detalle));
            ps.setString(6, recortar(comando));
            ps.executeUpdate();
        }
    }

    private static String recortar(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 497) + "...";
    }
}
