package canal11.datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

/**
 * CU1 - Capa de datos de la tabla usuario.
 * Solo SQL (siempre con PreparedStatement). Las reglas de negocio van en NUsuario.
 */
public class DUsuario {

    /**
     * Columnas que se muestran en los listados y en el detalle (en este orden):
     * 0 id, 1 ci, 2 nombre, 3 apellido, 4 email, 5 telefono, 6 rol, 7 activo, 8 registrado
     */
    private static final String SELECT =
            "SELECT id_usuario, ci, nombre, apellido, email, coalesce(telefono, ''), rol,"
            + " CASE WHEN activo THEN 'Sí' ELSE 'No' END, to_char(fecha_registro, 'DD/MM/YYYY HH24:MI')"
            + " FROM usuario";

    /** Devuelve {id_usuario, nombre completo, rol, activo} del email, o null si no existe. */
    public String[] buscarPorEmail(String email) throws SQLException {
        String sql = "SELECT id_usuario, nombre || ' ' || apellido, rol, activo FROM usuario WHERE lower(email) = lower(?)";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new String[]{rs.getString(1), rs.getString(2), rs.getString(3), String.valueOf(rs.getBoolean(4))};
            }
        }
    }

    /** Correo del usuario si esta activo; null si no existe o esta de baja. */
    public String emailActivo(int id) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement("SELECT email FROM usuario WHERE id_usuario = ? AND activo")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Correos de los usuarios activos con ese rol (para los avisos automaticos). */
    public List<String> emailsActivosPorRol(String rol) throws SQLException {
        String sql = "SELECT email FROM usuario WHERE rol = ? AND activo ORDER BY id_usuario";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, rol);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> emails = new java.util.ArrayList<>();
                while (rs.next()) {
                    emails.add(rs.getString(1));
                }
                return emails;
            }
        }
    }

    /** rol != null: filtra por rol. texto != null: busca en nombre, apellido o email. Ambos null: todos. */
    public List<String[]> listar(String rol, String texto) throws SQLException {
        String sql = SELECT;
        if (rol != null) {
            sql += " WHERE rol = ?";
        } else if (texto != null) {
            sql += " WHERE nombre ILIKE ? OR apellido ILIKE ? OR email ILIKE ?";
        }
        sql += " ORDER BY id_usuario";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (rol != null) {
                ps.setString(1, rol);
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

    /** Fila del usuario (mismas columnas que listar) o null si no existe. */
    public String[] obtener(int id) throws SQLException {
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(SELECT + " WHERE id_usuario = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                List<String[]> filas = Conexion.leerFilas(rs);
                return filas.isEmpty() ? null : filas.get(0);
            }
        }
    }

    /**
     * Busca el id de un usuario por su referencia: email, CI o ID (en ese orden).
     * Asi en los comandos se puede escribir mosUsu["7654321"] o mosUsu["ana@gmail.com"] sin conocer el ID.
     * Devuelve null si no existe.
     */
    public Integer buscarId(String referencia) throws SQLException {
        try (Connection c = Conexion.abrir()) {
            // 1) por email o CI
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id_usuario FROM usuario WHERE lower(email) = lower(?) OR upper(ci) = upper(?)")) {
                ps.setString(1, referencia);
                ps.setString(2, referencia);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            // 2) por ID (solo si es un numero)
            if (referencia.matches("\\d{1,9}")) {
                try (PreparedStatement ps = c.prepareStatement("SELECT id_usuario FROM usuario WHERE id_usuario = ?")) {
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

    /** {rol, activo} del usuario o null si no existe. */
    public String[] rolYEstado(int id) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement("SELECT rol, activo FROM usuario WHERE id_usuario = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new String[]{rs.getString(1), String.valueOf(rs.getBoolean(2))} : null;
            }
        }
    }

    /** true si otro usuario (distinto de excluirId) ya tiene ese valor en la columna (ci o email). */
    public boolean existe(String columna, String valor, int excluirId) throws SQLException {
        if (!columna.equals("ci") && !columna.equals("email")) {
            throw new IllegalArgumentException("Columna no permitida: " + columna);
        }
        String sql = "SELECT 1 FROM usuario WHERE lower(" + columna + ") = lower(?) AND id_usuario <> ?";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, valor);
            ps.setInt(2, excluirId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public int contarAdministradoresActivos() throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM usuario WHERE rol = 'ADMINISTRADOR' AND activo")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Inserta y devuelve el id generado. */
    public int insertar(String ci, String nombre, String apellido, String email, String telefono, String rol)
            throws SQLException {
        String sql = "INSERT INTO usuario (ci, nombre, apellido, email, telefono, rol) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, ci);
            ps.setString(2, nombre);
            ps.setString(3, apellido);
            ps.setString(4, email);
            textoONulo(ps, 5, telefono);
            ps.setString(6, rol);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public int modificar(int id, String ci, String nombre, String apellido, String email, String telefono, String rol)
            throws SQLException {
        String sql = "UPDATE usuario SET ci = ?, nombre = ?, apellido = ?, email = ?, telefono = ?, rol = ? WHERE id_usuario = ?";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ci);
            ps.setString(2, nombre);
            ps.setString(3, apellido);
            ps.setString(4, email);
            textoONulo(ps, 5, telefono);
            ps.setString(6, rol);
            ps.setInt(7, id);
            return ps.executeUpdate();
        }
    }

    /** Baja logica (activo = false). */
    public int darDeBaja(int id) throws SQLException {
        return cambiarEstado(id, false);
    }

    /** Reactiva un usuario dado de baja (activo = true). */
    public int reactivar(int id) throws SQLException {
        return cambiarEstado(id, true);
    }

    private int cambiarEstado(int id, boolean activo) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement("UPDATE usuario SET activo = ? WHERE id_usuario = ?")) {
            ps.setBoolean(1, activo);
            ps.setInt(2, id);
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
