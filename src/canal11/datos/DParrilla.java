package canal11.datos;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

/**
 * CU3 - Capa de datos de la tabla parrilla.
 * Solo SQL (siempre con PreparedStatement). Las reglas de negocio van en NParrilla.
 */
public class DParrilla {

    /**
     * Columnas de listados y detalle (en este orden):
     * 0 id, 1 fecha (AAAA-MM-DD), 2 estado, 3 creado por, 4 fecha de creacion, 5 fecha de publicacion, 6 cantidad de bloques
     */
    private static final String SELECT =
            "SELECT p.id_parrilla, to_char(p.fecha, 'YYYY-MM-DD'), p.estado, u.nombre || ' ' || u.apellido,"
            + " to_char(p.fecha_creacion, 'DD/MM/YYYY HH24:MI'), coalesce(to_char(p.fecha_publicacion, 'DD/MM/YYYY HH24:MI'), ''),"
            + " (SELECT count(*) FROM bloque b WHERE b.id_parrilla = p.id_parrilla)"
            + " FROM parrilla p JOIN usuario u ON u.id_usuario = p.creado_por";

    /** mes (AAAA-MM) != null: solo ese mes. null: todas. */
    public List<String[]> listar(String mes) throws SQLException {
        String sql = SELECT + (mes != null ? " WHERE to_char(p.fecha, 'YYYY-MM') = ?" : "") + " ORDER BY p.fecha";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (mes != null) {
                ps.setString(1, mes);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return Conexion.leerFilas(rs);
            }
        }
    }

    /** Fila de la parrilla (mismas columnas que listar) o null si no existe. */
    public String[] obtener(int id) throws SQLException {
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(SELECT + " WHERE p.id_parrilla = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                List<String[]> filas = Conexion.leerFilas(rs);
                return filas.isEmpty() ? null : filas.get(0);
            }
        }
    }

    /** Id de la parrilla de esa fecha o null si no existe. */
    public Integer buscarId(LocalDate fecha) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement("SELECT id_parrilla FROM parrilla WHERE fecha = ?")) {
            ps.setDate(1, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    /** Estado de la parrilla (BORRADOR o PUBLICADA). */
    public String estado(int id) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement("SELECT estado FROM parrilla WHERE id_parrilla = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Inserta una parrilla en BORRADOR y devuelve el id generado. */
    public int insertar(LocalDate fecha, int creadoPor) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement("INSERT INTO parrilla (fecha, creado_por) VALUES (?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setDate(1, Date.valueOf(fecha));
            ps.setInt(2, creadoPor);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Pasa la parrilla a PUBLICADA y registra la fecha de publicacion. */
    public int publicar(int id) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE parrilla SET estado = 'PUBLICADA', fecha_publicacion = now() WHERE id_parrilla = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate();
        }
    }

    /** Borra la parrilla (sus bloques se borran en cascada). Solo se usa con parrillas en BORRADOR. */
    public int eliminar(int id) throws SQLException {
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement("DELETE FROM parrilla WHERE id_parrilla = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate();
        }
    }
}
