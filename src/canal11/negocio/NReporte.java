package canal11.negocio;

import canal11.datos.DReporte;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CU8 - Reportes y Estadisticas. Reglas de negocio:
 *  - Todos reciben desde y hasta (AAAA-MM-DD, ambos inclusive), con desde <= hasta y un maximo de 366 dias.
 *  - Las agrupaciones se hacen en SQL (GROUP BY, ver DReporte); aqui se validan los parametros y se calculan
 *    el formato de las horas, los porcentajes y la fila de TOTAL.
 *  - Cada reporte devuelve las filas del grupo y, si hay datos, una ultima fila con el TOTAL (primera celda "TOTAL").
 *    Sin datos devuelve una lista vacia.
 *  - repPro: horas programadas por tipo de programa (bloques PROGRAMA, parrillas en borrador o publicadas).
 *  - repMat: materiales registrados en el rango por tipo y estado.
 *  - repPau: spots por anunciante en las parrillas del rango (programados = total; emitidos, no emitidos y pendientes).
 *  - repCum: cumplimiento por dia = bloques EMITIDO / bloques programados, solo parrillas PUBLICADAS
 *    (un bloque PARCIAL no cuenta como cumplido, igual que en conPar).
 * Los reportes son solo de lectura: no se registran en la auditoria.
 */
public class NReporte {

    public static final String TOTAL = "TOTAL";
    private static final int MAXIMO_DIAS = 366;

    private final DReporte datos = new DReporte();

    /** Rango validado de un reporte. */
    public static class Rango {
        public final LocalDate desde;
        public final LocalDate hasta;

        Rango(LocalDate desde, LocalDate hasta) {
            this.desde = desde;
            this.hasta = hasta;
        }
    }

    /** Valida desde / hasta. Lanza NegocioException con todos los errores juntos. */
    public Rango rango(String desdeTxt, String hastaTxt) throws NegocioException {
        Validador v = new Validador();
        LocalDate desde = v.fecha(desdeTxt, "desde");
        LocalDate hasta = v.fecha(hastaTxt, "hasta");
        if (desde != null && hasta != null) {
            if (desde.isAfter(hasta)) {
                v.error("La fecha <b>desde</b> (" + desde + ") no puede ser posterior a <b>hasta</b> (" + hasta + ").");
            } else if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAXIMO_DIAS) {
                v.error("El rango no puede superar " + MAXIMO_DIAS + " días (pidió " + (ChronoUnit.DAYS.between(desde, hasta) + 1) + ").");
            }
        }
        v.verificar();
        return new Rango(desde, hasta);
    }

    /** repPro. Columnas: tipo de programa, bloques, horas programadas ("12 h 30 min"), horas en decimal. */
    public List<String[]> horasPorTipoDePrograma(Rango r) throws SQLException {
        List<String[]> filas = new ArrayList<>();
        long bloques = 0;
        long segundos = 0;
        for (String[] f : datos.horasPorTipoDePrograma(r.desde, r.hasta)) {
            long b = Long.parseLong(f[1]);
            long s = Long.parseLong(f[2]);
            bloques += b;
            segundos += s;
            filas.add(new String[]{f[0], String.valueOf(b), horas(s), decimal(s)});
        }
        if (!filas.isEmpty()) {
            filas.add(new String[]{TOTAL, String.valueOf(bloques), horas(segundos), decimal(segundos)});
        }
        return filas;
    }

    /** repMat. Columnas: tipo, estado, cantidad. */
    public List<String[]> materialesPorTipoYEstado(Rango r) throws SQLException {
        List<String[]> filas = new ArrayList<>();
        long total = 0;
        for (String[] f : datos.materialesPorTipoYEstado(r.desde, r.hasta)) {
            total += Long.parseLong(f[2]);
            filas.add(new String[]{f[0], f[1], f[2]});
        }
        if (!filas.isEmpty()) {
            filas.add(new String[]{TOTAL, "", String.valueOf(total)});
        }
        return filas;
    }

    /** repPau. Columnas: anunciante, programados, emitidos, no emitidos, pendientes. */
    public List<String[]> spotsPorAnunciante(Rango r) throws SQLException {
        List<String[]> filas = new ArrayList<>();
        long[] suma = new long[4];
        for (String[] f : datos.spotsPorAnunciante(r.desde, r.hasta)) {
            for (int i = 0; i < 4; i++) {
                suma[i] += Long.parseLong(f[i + 1]);
            }
            filas.add(new String[]{f[0], f[1], f[2], f[3], f[4]});
        }
        if (!filas.isEmpty()) {
            filas.add(new String[]{TOTAL, String.valueOf(suma[0]), String.valueOf(suma[1]), String.valueOf(suma[2]),
                    String.valueOf(suma[3])});
        }
        return filas;
    }

    /** repCum. Columnas: fecha, programados, emitidos, parciales, no emitidos, sin registro, % de cumplimiento. */
    public List<String[]> cumplimientoPorDia(Rango r) throws SQLException {
        List<String[]> filas = new ArrayList<>();
        long[] suma = new long[5];
        for (String[] f : datos.cumplimientoPorDia(r.desde, r.hasta)) {
            for (int i = 0; i < 5; i++) {
                suma[i] += Long.parseLong(f[i + 1]);
            }
            filas.add(new String[]{f[0], f[1], f[2], f[3], f[4], f[5], porcentaje(Long.parseLong(f[2]), Long.parseLong(f[1]))});
        }
        if (!filas.isEmpty()) {
            filas.add(new String[]{TOTAL, String.valueOf(suma[0]), String.valueOf(suma[1]), String.valueOf(suma[2]),
                    String.valueOf(suma[3]), String.valueOf(suma[4]), porcentaje(suma[1], suma[0])});
        }
        return filas;
    }

    // ------------------------------------------------------------------ formato

    /** 45000 s -> "12 h 30 min". */
    static String horas(long segundos) {
        long minutos = Math.round(segundos / 60.0);
        return (minutos / 60) + " h " + String.format(Locale.ROOT, "%02d", minutos % 60) + " min";
    }

    static String decimal(long segundos) {
        return String.format(Locale.ROOT, "%.2f", segundos / 3600.0);
    }

    static String porcentaje(long parte, long total) {
        return total == 0 ? "0.0 %" : String.format(Locale.ROOT, "%.1f %%", parte * 100.0 / total);
    }
}
