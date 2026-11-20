package canal11.datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/** Consultas de las alertas automaticas (solo lectura). Las reglas van en NAlerta. */
public class DAlerta {

    /**
     * Bloques de parrillas PUBLICADAS que ya terminaron (entre desde y limite) y no tienen emision registrada.
     * Columnas: 0 id del bloque, 1 fecha, 2 hora inicio, 3 hora fin, 4 tipo, 5 programa
     */
    public List<String[]> bloquesSinEmision(Timestamp desde, Timestamp limite) throws SQLException {
        String sql = "SELECT b.id_bloque, to_char(pr.fecha, 'YYYY-MM-DD'), to_char(b.hora_inicio, 'HH24:MI'),"
                + " to_char(b.hora_fin, 'HH24:MI'), b.tipo, coalesce(p.nombre, '')"
                + " FROM bloque b JOIN parrilla pr ON pr.id_parrilla = b.id_parrilla"
                + " LEFT JOIN programa p ON p.id_programa = b.id_programa"
                + " WHERE pr.estado = 'PUBLICADA' AND NOT EXISTS (SELECT 1 FROM emision e WHERE e.id_bloque = b.id_bloque)"
                + " AND (pr.fecha + b.hora_fin) >= ? AND (pr.fecha + b.hora_fin) <= ?"
                + " ORDER BY pr.fecha, b.hora_inicio";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, desde);
            ps.setTimestamp(2, limite);
            try (ResultSet rs = ps.executeQuery()) {
                return Conexion.leerFilas(rs);
            }
        }
    }

    /**
     * Materiales RECHAZADOS y activos que siguen sin corregirse pasados los minutos indicados desde el rechazo.
     * Columnas: 0 id, 1 codigo, 2 titulo, 3 observacion, 4 fecha del rechazo, 5 rechazo (epoch, para no repetir el aviso), 6 id de quien lo registro
     */
    public List<String[]> rechazadosSinCorregir(int minutos) throws SQLException {
        String sql = "SELECT m.id_material, m.codigo, m.titulo, coalesce(m.observacion, ''),"
                + " to_char(m.fecha_validacion, 'DD/MM/YYYY HH24:MI'), (extract(epoch FROM m.fecha_validacion))::bigint::text, m.registrado_por"
                + " FROM material m WHERE m.estado = 'RECHAZADO' AND m.activo"
                + " AND m.fecha_validacion <= now() - make_interval(mins => ?) ORDER BY m.fecha_validacion";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, minutos);
            try (ResultSet rs = ps.executeQuery()) {
                return Conexion.leerFilas(rs);
            }
        }
    }
}
