package canal11.datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

/**
 * CU4 - Capa de datos de la tabla material.
 * Solo SQL (siempre con PreparedStatement). Las reglas de negocio van en NMaterial.
 */
public class DMaterial {

    /**
     * Columnas de listados y detalle (en este orden):
     * 0 id, 1 codigo, 2 titulo, 3 tipo, 4 programa, 5 duracion_seg, 6 formato, 7 ruta, 8 estado,
     * 9 observacion, 10 activo, 11 registrado por, 12 fecha de registro, 13 validado por, 14 fecha de validacion
     */
    private static final String SELECT =
            "SELECT m.id_material, m.codigo, m.titulo, m.tipo, coalesce(p.nombre, ''), m.duracion_seg, m.formato, m.ruta,"
            + " m.estado, coalesce(m.observacion, ''), CASE WHEN m.activo THEN 'Sí' ELSE 'No' END,"
            + " r.nombre || ' ' || r.apellido, to_char(m.fecha_registro, 'DD/MM/YYYY HH24:MI'),"
            + " coalesce(v.nombre || ' ' || v.apellido, ''), coalesce(to_char(m.fecha_validacion, 'DD/MM/YYYY HH24:MI'), '')"
            + " FROM material m"
            + " LEFT JOIN programa p ON p.id_programa = m.id_programa"
            + " JOIN usuario r ON r.id_usuario = m.registrado_por"
            + " LEFT JOIN usuario v ON v.id_usuario = m.validado_por";

    /**
     * estado != null: filtra por estado. tipo != null: filtra por tipo.
     * texto != null: busca en codigo, titulo o programa. Todos null: todos.
     */
    public List<String[]> listar(String estado, String tipo, String texto) throws SQLException {
        String sql = SELECT;
        if (estado != null) {
            sql += " WHERE m.estado = ?";
        } else if (tipo != null) {
            sql += " WHERE m.tipo = ?";
        } else if (texto != null) {
            sql += " WHERE m.codigo ILIKE ? OR m.titulo ILIKE ? OR p.nombre ILIKE ?";
        }
        sql += " ORDER BY m.codigo";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (estado != null) {
                ps.setString(1, estado);
            } else if (tipo != null) {
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

    /** Fila del material (mismas columnas que listar) o null si no existe. */
    public String[] obtener(int id) throws SQLException {
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(SELECT + " WHERE m.id_material = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                List<String[]> filas = Conexion.leerFilas(rs);
                return filas.isEmpty() ? null : filas.get(0);
            }
        }
    }

    /** Busca el id por codigo (sin distinguir mayusculas) o por ID numerico. null si no existe. */
    public Integer buscarId(String referencia) throws SQLException {
        try (Connection c = Conexion.abrir()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT id_material FROM material WHERE upper(codigo) = upper(?)")) {
                ps.setString(1, referencia);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            if (referencia.matches("\\d{1,9}")) {
                try (PreparedStatement ps = c.prepareStatement("SELECT id_material FROM material WHERE id_material = ?")) {
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

    /** true si otro material (distinto de excluirId) ya tiene ese codigo. */
    public boolean existeCodigo(String codigo, int excluirId) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM material WHERE upper(codigo) = upper(?) AND id_material <> ?")) {
            ps.setString(1, codigo);
            ps.setInt(2, excluirId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Codigo del otro material (distinto de excluirId) que ya usa esa ruta, o null si la ruta esta libre. */
    public String codigoConRuta(String ruta, int excluirId) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement("SELECT codigo FROM material WHERE lower(ruta) = lower(?) AND id_material <> ?")) {
            ps.setString(1, ruta);
            ps.setInt(2, excluirId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    public boolean estaActivo(int id) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement("SELECT activo FROM material WHERE id_material = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    /** Cantidad de bloques de parrillas PUBLICADAS que usan el material, directamente o en una escaleta. */
    public int contarEnParrillasPublicadas(int id) throws SQLException {
        String sql = "SELECT (SELECT count(*) FROM bloque b JOIN parrilla p ON p.id_parrilla = b.id_parrilla"
                + "           WHERE b.id_material = ? AND p.estado = 'PUBLICADA')"
                + "     + (SELECT count(*) FROM escaleta e JOIN bloque b ON b.id_bloque = e.id_bloque"
                + "           JOIN parrilla p ON p.id_parrilla = b.id_parrilla WHERE e.id_material = ? AND p.estado = 'PUBLICADA')";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Id del usuario que registro el material. */
    public int registradoPor(int id) throws SQLException {
        return contar("SELECT registrado_por FROM material WHERE id_material = ?", id);
    }

    /** Codigos de otros materiales activos con el mismo titulo, tipo y duracion (posibles duplicados con otra ruta). */
    public List<String> buscarSimilares(String titulo, String tipo, int duracionSeg, int excluirId) throws SQLException {
        String sql = "SELECT codigo FROM material WHERE lower(titulo) = lower(?) AND tipo = ? AND duracion_seg = ?"
                + " AND id_material <> ? AND activo ORDER BY codigo";
        List<String> codigos = new java.util.ArrayList<>();
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, titulo);
            ps.setString(2, tipo);
            ps.setInt(3, duracionSeg);
            ps.setInt(4, excluirId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    codigos.add(rs.getString(1));
                }
            }
        }
        return codigos;
    }

    /** Cantidad de spots PROGRAMADOS que usan el material. */
    public int contarSpotsProgramados(int id) throws SQLException {
        return contar("SELECT count(*) FROM spot WHERE id_material = ? AND estado = 'PROGRAMADO'", id);
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

    /** Inserta (estado inicial PENDIENTE) y devuelve el id generado. idPrograma puede ser null. */
    public int insertar(String codigo, Integer idPrograma, String titulo, String tipo, int duracionSeg, String formato,
                        String ruta, int registradoPor) throws SQLException {
        String sql = "INSERT INTO material (codigo, id_programa, titulo, tipo, duracion_seg, formato, ruta, registrado_por)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, codigo);
            enteroONulo(ps, 2, idPrograma);
            ps.setString(3, titulo);
            ps.setString(4, tipo);
            ps.setInt(5, duracionSeg);
            ps.setString(6, formato);
            ps.setString(7, ruta);
            ps.setInt(8, registradoPor);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Modifica los datos. Si volverAPendiente, borra tambien la validacion (estado PENDIENTE). */
    public int modificar(int id, Integer idPrograma, String titulo, String tipo, int duracionSeg, String formato, String ruta,
                         boolean volverAPendiente) throws SQLException {
        String sql = "UPDATE material SET id_programa = ?, titulo = ?, tipo = ?, duracion_seg = ?, formato = ?, ruta = ?"
                + (volverAPendiente ? ", estado = 'PENDIENTE', observacion = NULL, validado_por = NULL, fecha_validacion = NULL,"
                + " pendiente_desde = now(), ultimo_aviso = NULL, avisos = 0" : "")
                + " WHERE id_material = ?";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            enteroONulo(ps, 1, idPrograma);
            ps.setString(2, titulo);
            ps.setString(3, tipo);
            ps.setInt(4, duracionSeg);
            ps.setString(5, formato);
            ps.setString(6, ruta);
            ps.setInt(7, id);
            return ps.executeUpdate();
        }
    }

    /** CU5: guarda el resultado de la validacion (APROBADO o RECHAZADO) con quien la hizo y cuando. */
    public int validar(int id, String estado, String observacion, int validadoPor) throws SQLException {
        String sql = "UPDATE material SET estado = ?, observacion = ?, validado_por = ?, fecha_validacion = now()"
                + " WHERE id_material = ?";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, estado);
            if (observacion == null) {
                ps.setNull(2, Types.VARCHAR);
            } else {
                ps.setString(2, observacion);
            }
            ps.setInt(3, validadoPor);
            ps.setInt(4, id);
            return ps.executeUpdate();
        }
    }

    /** lisMat["VENCIDO"]: materiales PENDIENTES activos a los que ya se les envio aviso por falta de revision. */
    public List<String[]> listarVencidos() throws SQLException {
        String sql = SELECT + " WHERE m.estado = 'PENDIENTE' AND m.activo AND m.avisos > 0 ORDER BY m.pendiente_desde, m.codigo";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                return Conexion.leerFilas(rs);
            }
        }
    }

    /**
     * Materiales PENDIENTES y activos que deben recibir el aviso del nivel dado.
     * Nivel 1 (recordatorio): sin avisos y pendiente desde hace [minAviso, minEscalar) minutos.
     * Nivel 2 (escalamiento): con menos de 2 avisos y pendiente desde hace minEscalar minutos o mas.
     * Columnas: 0 id, 1 codigo, 2 titulo, 3 tipo, 4 registrado por, 5 pendiente desde, 6 minutos pendiente
     */
    public List<String[]> pendientesDeAviso(int nivel, int minAviso, int minEscalar) throws SQLException {
        String condicion = nivel == 2
                ? "m.avisos < 2 AND m.pendiente_desde <= now() - make_interval(mins => ?)"
                : "m.avisos = 0 AND m.pendiente_desde <= now() - make_interval(mins => ?)"
                  + " AND m.pendiente_desde > now() - make_interval(mins => ?)";
        String sql = "SELECT m.id_material, m.codigo, m.titulo, m.tipo, r.nombre || ' ' || r.apellido,"
                + " to_char(m.pendiente_desde, 'DD/MM/YYYY HH24:MI'),"
                + " (extract(epoch FROM now() - m.pendiente_desde) / 60)::int"
                + " FROM material m JOIN usuario r ON r.id_usuario = m.registrado_por"
                + " WHERE m.estado = 'PENDIENTE' AND m.activo AND " + condicion
                + " ORDER BY m.pendiente_desde, m.codigo";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (nivel == 2) {
                ps.setInt(1, minEscalar);
            } else {
                ps.setInt(1, minAviso);
                ps.setInt(2, minEscalar);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return Conexion.leerFilas(rs);
            }
        }
    }

    /** Marca que se envio el aviso del nivel dado (solo si sigue PENDIENTE y aun no tenia ese nivel). */
    public int marcarAviso(int id, int nivel) throws SQLException {
        String sql = "UPDATE material SET avisos = ?, ultimo_aviso = now()"
                + " WHERE id_material = ? AND estado = 'PENDIENTE' AND avisos < ?";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, nivel);
            ps.setInt(2, id);
            ps.setInt(3, nivel);
            return ps.executeUpdate();
        }
    }

    /** Baja logica (activo = false). */
    public int darDeBaja(int id) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement("UPDATE material SET activo = false WHERE id_material = ?")) {
            ps.setInt(1, id);
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
