package canal11.datos;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * CU6 - Capa de datos de la tabla spot (spots insertados en las pausas comerciales).
 * Solo SQL (siempre con PreparedStatement). Las reglas de negocio van en NSpot.
 */
public class DSpot {

    /**
     * Columnas de los spots (en este orden):
     * 0 id, 1 fecha de la parrilla (AAAA-MM-DD), 2 hora del spot (HH:MM:SS), 3 pauta, 4 codigo del material,
     * 5 titulo del material, 6 duracion del material (seg), 7 estado, 8 pausa comercial ("HH:MM:SS-HH:MM:SS")
     */
    private static final String SELECT =
            "SELECT s.id_spot, to_char(pr.fecha, 'YYYY-MM-DD'), to_char(s.hora, 'HH24:MI:SS'), pa.nombre, m.codigo, m.titulo,"
            + " m.duracion_seg, s.estado, to_char(b.hora_inicio, 'HH24:MI:SS') || '-' || to_char(b.hora_fin, 'HH24:MI:SS')"
            + " FROM spot s"
            + " JOIN pauta pa ON pa.id_pauta = s.id_pauta"
            + " JOIN material m ON m.id_material = s.id_material"
            + " JOIN bloque b ON b.id_bloque = s.id_bloque"
            + " JOIN parrilla pr ON pr.id_parrilla = b.id_parrilla";

    public List<String[]> listarPorPauta(int idPauta) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement(SELECT + " WHERE s.id_pauta = ? ORDER BY pr.fecha, s.hora")) {
            ps.setInt(1, idPauta);
            try (ResultSet rs = ps.executeQuery()) {
                return Conexion.leerFilas(rs);
            }
        }
    }

    /** Fila del spot (mismas columnas que listarPorPauta) o null si no existe. */
    public String[] obtener(int id) throws SQLException {
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(SELECT + " WHERE s.id_spot = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                List<String[]> filas = Conexion.leerFilas(rs);
                return filas.isEmpty() ? null : filas.get(0);
            }
        }
    }

    /** Id del spot de la parrilla de esa fecha que esta a esa hora, o null. */
    public Integer buscarId(LocalDate fecha, LocalTime hora) throws SQLException {
        String sql = "SELECT s.id_spot FROM spot s JOIN bloque b ON b.id_bloque = s.id_bloque"
                + " JOIN parrilla pr ON pr.id_parrilla = b.id_parrilla WHERE pr.fecha = ? AND s.hora = ?";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fecha));
            ps.setTime(2, Time.valueOf(hora));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    public boolean existeHoraEnBloque(int idBloque, LocalTime hora) throws SQLException {
        try (Connection c = Conexion.abrir();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM spot WHERE id_bloque = ? AND hora = ?")) {
            ps.setInt(1, idBloque);
            ps.setTime(2, Time.valueOf(hora));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Pases usados de la pauta: spots que no estan marcados NO_EMITIDO. */
    public int contarPases(int idPauta) throws SQLException {
        return contar("SELECT count(*) FROM spot WHERE id_pauta = ? AND estado <> 'NO_EMITIDO'", idPauta);
    }

    /** Spots de la pauta que siguen PROGRAMADOS. */
    public int contarProgramados(int idPauta) throws SQLException {
        return contar("SELECT count(*) FROM spot WHERE id_pauta = ? AND estado = 'PROGRAMADO'", idPauta);
    }

    /** Segundos que ya ocupan los spots (no NO_EMITIDO) de un bloque. */
    public int segundosOcupados(int idBloque) throws SQLException {
        return contar("SELECT coalesce(sum(m.duracion_seg), 0) FROM spot s JOIN material m ON m.id_material = s.id_material"
                + " WHERE s.id_bloque = ? AND s.estado <> 'NO_EMITIDO'", idBloque);
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

    /** Inserta el spot (estado PROGRAMADO) y devuelve el id generado. */
    public int insertar(int idPauta, int idMaterial, int idBloque, LocalTime hora) throws SQLException {
        String sql = "INSERT INTO spot (id_pauta, id_material, id_bloque, hora) VALUES (?, ?, ?, ?)";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, idPauta);
            ps.setInt(2, idMaterial);
            ps.setInt(3, idBloque);
            ps.setTime(4, Time.valueOf(hora));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public int eliminar(int id) throws SQLException {
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement("DELETE FROM spot WHERE id_spot = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate();
        }
    }
}
