package canal11.datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

/**
 * CU2 - Capa de datos de la tabla programa.
 * Solo SQL (siempre con PreparedStatement). Las reglas de negocio van en NPrograma.
 */
public class DPrograma {

    /**
     * Columnas de listados y detalle (en este orden):
     * 0 id, 1 nombre, 2 descripcion, 3 tipo, 4 duracion_min, 5 genero, 6 clasificacion, 7 activo
     */
    private static final String SELECT =
            "SELECT id_programa, nombre, coalesce(descripcion, ''), tipo, duracion_min, coalesce(genero, ''),"
            + " coalesce(clasificacion, ''), CASE WHEN activo THEN 'Sí' ELSE 'No' END FROM programa";

    /** tipo != null: filtra por tipo. texto != null: busca en nombre, descripcion o genero. Ambos null: todos. */
    public List<String[]> listar(String tipo, String texto) throws SQLException {
        String sql = SELECT;
        if (tipo != null) {
            sql += " WHERE tipo = ?";
        } else if (texto != null) {
            sql += " WHERE nombre ILIKE ? OR descripcion ILIKE ? OR genero ILIKE ?";
        }
        sql += " ORDER BY nombre";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (tipo != null) {
                ps.setString(1, tipo);
            } else if (texto != null) {
                String like = "%" + texto + "%";
                ps.setString(1, like);
                ps.setString(2, like);
                ps.setString(3, like);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return Conexion.leerFilas(rs);
            }
        }
    }

    /** Fila del programa (mismas columnas que listar) o null si no existe. */
    public String[] obtener(int id) throws SQLException {
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(SELECT + " WHERE id_programa = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                List<String[]> filas = Conexion.leerFilas(rs);
                return filas.isEmpty() ? null : filas.get(0);
            }
        }
    }

    /** Busca el id por nombre (sin distinguir mayusculas) o por ID numerico. null si no existe. */
    public Integer buscarId(String referencia) throws SQLException {
        try (Connection c = Conexion.abrir()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT id_programa FROM programa WHERE lower(nombre) = lower(?)")) {
                ps.setString(1, referencia);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            if (referencia.matches("\\d{1,9}")) {
                try (PreparedStatement ps = c.prepareStatement("SELECT id_programa FROM programa WHERE id_programa = ?")) {
                    ps.setInt(1, Integer.parseInt(referencia));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt(1);
                        }
                    }
                }
            }
            return null;
        }
    }

    public boolean estaActivo(int id) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement("SELECT activo FROM programa WHERE id_programa = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    /** true si otro programa (distinto de excluirId) ya tiene ese nombre. */
    public boolean existeNombre(String nombre, int excluirId) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM programa WHERE lower(nombre) = lower(?) AND id_programa <> ?")) {
            ps.setString(1, nombre);
            ps.setInt(2, excluirId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Cantidad de bloques de parrillas de hoy en adelante que usan este programa. */
    public int contarBloquesDesdeHoy(int id) throws SQLException {
        String sql = "SELECT count(*) FROM bloque b JOIN parrilla p ON p.id_parrilla = b.id_parrilla"
                + " WHERE b.id_programa = ? AND p.fecha >= current_date";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Inserta y devuelve el id generado. */
    public int insertar(String nombre, String descripcion, String tipo, int duracionMin, String genero, String clasificacion)
            throws SQLException {
        String sql = "INSERT INTO programa (nombre, descripcion, tipo, duracion_min, genero, clasificacion) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            textoONulo(ps, 2, descripcion);
            ps.setString(3, tipo);
            ps.setInt(4, duracionMin);
            textoONulo(ps, 5, genero);
            textoONulo(ps, 6, clasificacion);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public int modificar(int id, String nombre, String descripcion, String tipo, int duracionMin, String genero,
                         String clasificacion) throws SQLException {
        String sql = "UPDATE programa SET nombre = ?, descripcion = ?, tipo = ?, duracion_min = ?, genero = ?, clasificacion = ?"
                + " WHERE id_programa = ?";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nombre);
            textoONulo(ps, 2, descripcion);
            ps.setString(3, tipo);
            ps.setInt(4, duracionMin);
            textoONulo(ps, 5, genero);
            textoONulo(ps, 6, clasificacion);
            ps.setInt(7, id);
            return ps.executeUpdate();
        }
    }

    /** Baja logica (activo = false). */
    public int darDeBaja(int id) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement("UPDATE programa SET activo = false WHERE id_programa = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate();
        }
    }

    private static void textoONulo(PreparedStatement ps, int i, String v) throws SQLException {
        if (v == null) {
            ps.setNull(i, Types.VARCHAR);
        } else {
            ps.setString(i, v);
        }
    }
}
