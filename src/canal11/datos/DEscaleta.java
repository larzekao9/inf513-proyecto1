package canal11.datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Capa de datos de la escaleta: el orden de las notas dentro de un bloque de noticiero o revista.
 * Solo SQL (siempre con PreparedStatement). Las reglas de negocio van en NLead.
 */
public class DEscaleta {

    /**
     * Notas de un bloque, ordenadas (en este orden):
     * 0 orden, 1 codigo, 2 titulo, 3 duracion_seg, 4 estado del material, 5 activo (Sí/No), 6 registrado por
     */
    public List<String[]> listar(int idBloque) throws SQLException {
        String sql = "SELECT e.orden, m.codigo, m.titulo, m.duracion_seg, m.estado, CASE WHEN m.activo THEN 'Sí' ELSE 'No' END,"
                + " u.nombre || ' ' || u.apellido"
                + " FROM escaleta e JOIN material m ON m.id_material = e.id_material"
                + " JOIN usuario u ON u.id_usuario = e.registrado_por"
                + " WHERE e.id_bloque = ? ORDER BY e.orden";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idBloque);
            try (ResultSet rs = ps.executeQuery()) {
                return Conexion.leerFilas(rs);
            }
        }
    }

    /** Orden de ese material en la escaleta del bloque, o null si no esta. */
    public Integer ordenDe(int idBloque, int idMaterial) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement("SELECT orden FROM escaleta WHERE id_bloque = ? AND id_material = ?")) {
            ps.setInt(1, idBloque);
            ps.setInt(2, idMaterial);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    /** Suma de las duraciones (segundos) de las notas del bloque. */
    public int duracionTotal(int idBloque) throws SQLException {
        String sql = "SELECT coalesce(sum(m.duracion_seg), 0) FROM escaleta e JOIN material m ON m.id_material = e.id_material"
                + " WHERE e.id_bloque = ?";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idBloque);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Notas del bloque cuyo material esta de baja o no esta APROBADO, como "CODIGO (ESTADO)". */
    public List<String> notasNoListas(int idBloque) throws SQLException {
        String sql = "SELECT m.codigo || ' (' || CASE WHEN NOT m.activo THEN 'DE BAJA' ELSE m.estado END || ')'"
                + " FROM escaleta e JOIN material m ON m.id_material = e.id_material"
                + " WHERE e.id_bloque = ? AND (m.estado <> 'APROBADO' OR NOT m.activo) ORDER BY e.orden";
        List<String> r = new ArrayList<>();
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idBloque);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    r.add(rs.getString(1));
                }
            }
        }
        return r;
    }

    /** Inserta la nota en esa posicion y corre hacia abajo las que estaban desde ahi, todo en una transaccion. */
    public int insertar(int idBloque, int orden, int idMaterial, int registradoPor) throws SQLException {
        try (Connection c = Conexion.abrir()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE escaleta SET orden = orden + 1 WHERE id_bloque = ? AND orden >= ?")) {
                    ps.setInt(1, idBloque);
                    ps.setInt(2, orden);
                    ps.executeUpdate();
                }
                int id;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO escaleta (id_bloque, orden, id_material, registrado_por) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, idBloque);
                    ps.setInt(2, orden);
                    ps.setInt(3, idMaterial);
                    ps.setInt(4, registradoPor);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        rs.next();
                        id = rs.getInt(1);
                    }
                }
                c.commit();
                return id;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** Mueve la nota de la posicion actual a la nueva, recorriendo las que quedan en medio. Una sola sentencia. */
    public void mover(int idBloque, int idMaterial, int actual, int nuevo) throws SQLException {
        int desde = Math.min(actual, nuevo);
        int hasta = Math.max(actual, nuevo);
        int paso = nuevo < actual ? 1 : -1;
        String sql = "UPDATE escaleta SET orden = CASE WHEN id_material = ? THEN ? ELSE orden + ? END"
                + " WHERE id_bloque = ? AND orden BETWEEN ? AND ?";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idMaterial);
            ps.setInt(2, nuevo);
            ps.setInt(3, paso);
            ps.setInt(4, idBloque);
            ps.setInt(5, desde);
            ps.setInt(6, hasta);
            ps.executeUpdate();
        }
    }

    /** Quita la nota y sube las siguientes para no dejar huecos en el orden. */
    public void eliminar(int idBloque, int idMaterial, int orden) throws SQLException {
        try (Connection c = Conexion.abrir()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM escaleta WHERE id_bloque = ? AND id_material = ?")) {
                    ps.setInt(1, idBloque);
                    ps.setInt(2, idMaterial);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE escaleta SET orden = orden - 1 WHERE id_bloque = ? AND orden > ?")) {
                    ps.setInt(1, idBloque);
                    ps.setInt(2, orden);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }
}
