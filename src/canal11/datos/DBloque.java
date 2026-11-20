package canal11.datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Types;
import java.time.LocalTime;
import java.util.List;

/**
 * CU3 - Capa de datos de la tabla bloque (bloques horarios de una parrilla).
 * Solo SQL (siempre con PreparedStatement). Las reglas de negocio van en NParrilla.
 */
public class DBloque {

    /**
     * Columnas de los bloques de una parrilla, ordenados por hora (en este orden):
     * 0 id, 1 hora inicio (HH:MM:SS), 2 hora fin (HH:MM:SS), 3 tipo, 4 programa, 5 codigo del material,
     * 6 estado del material (PENDIENTE, APROBADO, RECHAZADO; vacio si no tiene), 7 material activo (Sí/No; vacio si no tiene),
     * 8 observacion de la validacion del material, 9 cantidad de notas de su escaleta
     */
    private static final String SELECT =
            "SELECT b.id_bloque, to_char(b.hora_inicio, 'HH24:MI:SS'), to_char(b.hora_fin, 'HH24:MI:SS'), b.tipo,"
            + " coalesce(p.nombre, ''), coalesce(m.codigo, ''), coalesce(m.estado, ''),"
            + " CASE WHEN m.id_material IS NULL THEN '' WHEN m.activo THEN 'Sí' ELSE 'No' END,"
            + " coalesce(m.observacion, ''), (SELECT count(*) FROM escaleta e WHERE e.id_bloque = b.id_bloque)"
            + " FROM bloque b"
            + " LEFT JOIN programa p ON p.id_programa = b.id_programa"
            + " LEFT JOIN material m ON m.id_material = b.id_material";

    public List<String[]> listarPorParrilla(int idParrilla) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement(SELECT + " WHERE b.id_parrilla = ? ORDER BY b.hora_inicio")) {
            ps.setInt(1, idParrilla);
            try (ResultSet rs = ps.executeQuery()) {
                return Conexion.leerFilas(rs);
            }
        }
    }

    /** Fila del bloque (mismas columnas que listarPorParrilla) o null si no existe. */
    public String[] obtener(int id) throws SQLException {
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(SELECT + " WHERE b.id_bloque = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                List<String[]> filas = Conexion.leerFilas(rs);
                return filas.isEmpty() ? null : filas.get(0);
            }
        }
    }

    /** Id del bloque de esa parrilla que empieza a esa hora, o null. */
    public Integer buscarId(int idParrilla, LocalTime horaInicio) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement("SELECT id_bloque FROM bloque WHERE id_parrilla = ? AND hora_inicio = ?")) {
            ps.setInt(1, idParrilla);
            ps.setTime(2, Time.valueOf(horaInicio));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    /** Horario ("HH:MM:SS-HH:MM:SS") del primer bloque de la parrilla que se solapa con [inicio, fin), o null si no hay. */
    public String buscarSolape(int idParrilla, LocalTime inicio, LocalTime fin) throws SQLException {
        String sql = "SELECT to_char(hora_inicio, 'HH24:MI:SS') || '-' || to_char(hora_fin, 'HH24:MI:SS') FROM bloque"
                + " WHERE id_parrilla = ? AND hora_inicio < ? AND hora_fin > ? ORDER BY hora_inicio LIMIT 1";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idParrilla);
            ps.setTime(2, Time.valueOf(fin));
            ps.setTime(3, Time.valueOf(inicio));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Cantidad de spots programados dentro de un bloque. */
    public int contarSpotsDeBloque(int idBloque) throws SQLException {
        return contar("SELECT count(*) FROM spot WHERE id_bloque = ?", idBloque);
    }

    /** Cantidad de spots programados en todos los bloques de una parrilla. */
    public int contarSpotsDeParrilla(int idParrilla) throws SQLException {
        return contar("SELECT count(*) FROM spot s JOIN bloque b ON b.id_bloque = s.id_bloque WHERE b.id_parrilla = ?", idParrilla);
    }

    private int contar(String sql, int id) throws SQLException {
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Inserta el bloque y devuelve el id generado. idPrograma e idMaterial pueden ser null. */
    public int insertar(int idParrilla, Integer idPrograma, Integer idMaterial, LocalTime inicio, LocalTime fin, String tipo)
            throws SQLException {
        String sql = "INSERT INTO bloque (id_parrilla, id_programa, id_material, hora_inicio, hora_fin, tipo) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, idParrilla);
            enteroONulo(ps, 2, idPrograma);
            enteroONulo(ps, 3, idMaterial);
            ps.setTime(4, Time.valueOf(inicio));
            ps.setTime(5, Time.valueOf(fin));
            ps.setString(6, tipo);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Cambia el material del bloque. */
    public int cambiarMaterial(int idBloque, int idMaterial) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement("UPDATE bloque SET id_material = ? WHERE id_bloque = ?")) {
            ps.setInt(1, idMaterial);
            ps.setInt(2, idBloque);
            return ps.executeUpdate();
        }
    }

    public int eliminar(int idBloque) throws SQLException {
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement("DELETE FROM bloque WHERE id_bloque = ?")) {
            ps.setInt(1, idBloque);
            return ps.executeUpdate();
        }
    }

    private static void enteroONulo(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) {
            ps.setNull(i, Types.INTEGER);
        } else {
            ps.setInt(i, v);
        }
    }
}
