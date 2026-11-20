package canal11.datos;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

/**
 * CU8 - Capa de datos de los reportes. Todas las agrupaciones se hacen en SQL (GROUP BY).
 * Cada metodo recibe el rango [desde, hasta] (ambos inclusive) y devuelve una fila por grupo, todo como texto.
 * Los totales y los porcentajes los calcula NReporte.
 */
public class DReporte {

    /**
     * repPro: horas programadas por tipo de programa (solo bloques PROGRAMA, de parrillas en BORRADOR o PUBLICADAS).
     * Columnas: 0 tipo de programa, 1 cantidad de bloques, 2 segundos programados
     */
    public List<String[]> horasPorTipoDePrograma(LocalDate desde, LocalDate hasta) throws SQLException {
        String sql = "SELECT coalesce(p.tipo, 'SIN PROGRAMA'), count(*),"
                + " CAST(coalesce(sum(extract(epoch FROM (b.hora_fin - b.hora_inicio))), 0) AS bigint)"
                + " FROM bloque b JOIN parrilla pr ON pr.id_parrilla = b.id_parrilla"
                + " LEFT JOIN programa p ON p.id_programa = b.id_programa"
                + " WHERE b.tipo = 'PROGRAMA' AND pr.fecha BETWEEN ? AND ?"
                + " GROUP BY coalesce(p.tipo, 'SIN PROGRAMA') ORDER BY 3 DESC, 1";
        return consultarPorFechas(sql, desde, hasta);
    }

    /**
     * repMat: materiales registrados en el rango por tipo y estado (incluye los dados de baja).
     * Columnas: 0 tipo, 1 estado, 2 cantidad
     */
    public List<String[]> materialesPorTipoYEstado(LocalDate desde, LocalDate hasta) throws SQLException {
        String sql = "SELECT tipo, estado, count(*) FROM material WHERE fecha_registro >= ? AND fecha_registro < ?"
                + " GROUP BY tipo, estado ORDER BY tipo, estado";
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(desde.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                return Conexion.leerFilas(rs);
            }
        }
    }

    /**
     * repPau: spots de las parrillas del rango agrupados por anunciante.
     * Columnas: 0 anunciante, 1 programados (total), 2 emitidos, 3 no emitidos, 4 pendientes (aun PROGRAMADO)
     */
    public List<String[]> spotsPorAnunciante(LocalDate desde, LocalDate hasta) throws SQLException {
        String sql = "SELECT pa.anunciante, count(*),"
                + " count(*) FILTER (WHERE s.estado = 'EMITIDO'),"
                + " count(*) FILTER (WHERE s.estado = 'NO_EMITIDO'),"
                + " count(*) FILTER (WHERE s.estado = 'PROGRAMADO')"
                + " FROM spot s JOIN pauta pa ON pa.id_pauta = s.id_pauta"
                + " JOIN bloque b ON b.id_bloque = s.id_bloque JOIN parrilla pr ON pr.id_parrilla = b.id_parrilla"
                + " WHERE pr.fecha BETWEEN ? AND ? GROUP BY pa.anunciante ORDER BY 2 DESC, 1";
        return consultarPorFechas(sql, desde, hasta);
    }

    /**
     * repCum: cumplimiento por dia (solo parrillas PUBLICADAS con bloques).
     * Columnas: 0 fecha, 1 bloques programados, 2 emitidos, 3 parciales, 4 no emitidos, 5 sin registro
     */
    public List<String[]> cumplimientoPorDia(LocalDate desde, LocalDate hasta) throws SQLException {
        String sql = "SELECT to_char(pr.fecha, 'YYYY-MM-DD'), count(*),"
                + " count(*) FILTER (WHERE e.estado = 'EMITIDO'),"
                + " count(*) FILTER (WHERE e.estado = 'PARCIAL'),"
                + " count(*) FILTER (WHERE e.estado = 'NO_EMITIDO'),"
                + " count(*) FILTER (WHERE e.id_emision IS NULL)"
                + " FROM parrilla pr JOIN bloque b ON b.id_parrilla = pr.id_parrilla"
                + " LEFT JOIN emision e ON e.id_bloque = b.id_bloque"
                + " WHERE pr.estado = 'PUBLICADA' AND pr.fecha BETWEEN ? AND ?"
                + " GROUP BY pr.fecha ORDER BY pr.fecha";
        return consultarPorFechas(sql, desde, hasta);
    }

    private List<String[]> consultarPorFechas(String sql, LocalDate desde, LocalDate hasta) throws SQLException {
        try (Connection c = Conexion.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(desde));
            ps.setDate(2, Date.valueOf(hasta));
            try (ResultSet rs = ps.executeQuery()) {
                return Conexion.leerFilas(rs);
            }
        }
    }
}
