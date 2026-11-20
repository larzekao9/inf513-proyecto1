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
 * CU6 - Capa de datos de la tabla pauta (pautas publicitarias).
 * Solo SQL (siempre con PreparedStatement). Las reglas de negocio van en NPauta.
 */
public class DPauta {

    private static final String USADOS =
            "(SELECT count(*) FROM spot s WHERE s.id_pauta = pa.id_pauta AND s.estado <> 'NO_EMITIDO')";

    /**
     * Columnas de listados y detalle (en este orden):
     * 0 id, 1 nombre, 2 anunciante, 3 fecha inicio (AAAA-MM-DD), 4 fecha fin (AAAA-MM-DD), 5 max pases, 6 activo,
     * 7 pases usados (spots no marcados NO_EMITIDO), 8 pases disponibles
     */
    private static final String SELECT =
            "SELECT pa.id_pauta, pa.nombre, pa.anunciante, to_char(pa.fecha_inicio, 'YYYY-MM-DD'),"
            + " to_char(pa.fecha_fin, 'YYYY-MM-DD'), pa.max_pases, CASE WHEN pa.activo THEN 'Sí' ELSE 'No' END,"
            + " " + USADOS + ", pa.max_pases - " + USADOS + " FROM pauta pa";

    /** texto != null: busca en anunciante o nombre. null: todas. */
    public List<String[]> listar(String texto) throws SQLException {
        String sql = SELECT + (texto != null ? " WHERE pa.anunciante ILIKE ? OR pa.nombre ILIKE ?" : "") + " ORDER BY pa.fecha_inicio, pa.nombre";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (texto != null) {
                ps.setString(1, "%" + texto + "%");
                ps.setString(2, "%" + texto + "%");
            }
            try (ResultSet rs = ps.executeQuery()) {
                return Conexion.leerFilas(rs);
            }
        }
    }

    /** Fila de la pauta (mismas columnas que listar) o null si no existe. */
    public String[] obtener(int id) throws SQLException {
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(SELECT + " WHERE pa.id_pauta = ?")) {
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
            try (PreparedStatement ps = c.prepareStatement("SELECT id_pauta FROM pauta WHERE lower(nombre) = lower(?)")) {
                ps.setString(1, referencia);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            if (referencia.matches("\\d{1,9}")) {
                try (PreparedStatement ps = c.prepareStatement("SELECT id_pauta FROM pauta WHERE id_pauta = ?")) {
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

    public boolean existeNombre(String nombre) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM pauta WHERE lower(nombre) = lower(?)")) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Inserta y devuelve el id generado. */
    public int insertar(String nombre, String anunciante, LocalDate inicio, LocalDate fin, int maxPases) throws SQLException {
        String sql = "INSERT INTO pauta (nombre, anunciante, fecha_inicio, fecha_fin, max_pases) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.setString(2, anunciante);
            ps.setDate(3, Date.valueOf(inicio));
            ps.setDate(4, Date.valueOf(fin));
            ps.setInt(5, maxPases);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Baja logica (activo = false). */
    public int darDeBaja(int id) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement("UPDATE pauta SET activo = false WHERE id_pauta = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate();
        }
    }
}
