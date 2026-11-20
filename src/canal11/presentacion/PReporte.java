package canal11.presentacion;

import canal11.negocio.NReporte;
import canal11.negocio.NReporte.Rango;
import canal11.negocio.Usuario;

import java.util.ArrayList;
import java.util.List;

/**
 * CU8 - Capa de presentacion de los reportes: lee desde / hasta, llama a NReporte y arma una tabla con el TOTAL al final.
 * Sin SQL ni reglas de negocio.
 */
public class PReporte {

    private final NReporte negocio = new NReporte();

    /** repPro["desde","hasta"] */
    public Respuesta horasPorPrograma(Usuario u, List<String> p) throws Exception {
        Rango r = negocio.rango(p.get(0), p.get(1));
        return reporte("Horas programadas por tipo de programa", r,
                "Suma de la duración de los bloques de tipo PROGRAMA de las parrillas del rango (en borrador o publicadas).",
                new String[]{"Tipo de programa", "Bloques", "Horas programadas", "Horas (decimal)"},
                negocio.horasPorTipoDePrograma(r), "No hay bloques de programa en parrillas de ese rango.");
    }

    /** repMat["desde","hasta"] */
    public Respuesta materiales(Usuario u, List<String> p) throws Exception {
        Rango r = negocio.rango(p.get(0), p.get(1));
        return reporte("Materiales por estado y tipo", r,
                "Materiales registrados dentro del rango (incluye los dados de baja).",
                new String[]{"Tipo", "Estado", "Cantidad"},
                negocio.materialesPorTipoYEstado(r), "No se registraron materiales en ese rango.");
    }

    /** repPau["desde","hasta"] */
    public Respuesta spotsPorAnunciante(Usuario u, List<String> p) throws Exception {
        Rango r = negocio.rango(p.get(0), p.get(1));
        return reporte("Spots por anunciante", r,
                "Spots ubicados en parrillas del rango. Programados = total; los pendientes siguen en estado PROGRAMADO "
                        + "(aún no se concilió su bloque).",
                new String[]{"Anunciante", "Programados", "Emitidos", "No emitidos", "Pendientes"},
                negocio.spotsPorAnunciante(r), "No hay spots en parrillas de ese rango.");
    }

    /** repCum["desde","hasta"] */
    public Respuesta cumplimiento(Usuario u, List<String> p) throws Exception {
        Rango r = negocio.rango(p.get(0), p.get(1));
        return reporte("Cumplimiento: programado vs. emitido", r,
                "Solo parrillas PUBLICADAS. Cumplimiento = bloques EMITIDO / bloques programados "
                        + "(un bloque PARCIAL no cuenta como cumplido). Registre lo emitido con insEmi.",
                new String[]{"Fecha", "Programados", "Emitidos", "Parciales", "No emitidos", "Sin registro", "Cumplimiento"},
                negocio.cumplimientoPorDia(r), "No hay parrillas publicadas en ese rango.");
    }

    // ------------------------------------------------------------------ vista comun

    private Respuesta reporte(String titulo, Rango r, String nota, String[] columnas, List<String[]> filas, String sinDatos) {
        String encabezado = Html.parrafo("Del <b>" + r.desde + "</b> al <b>" + r.hasta + "</b>.")
                + Html.parrafo("<span style='font-size:12px;'>" + Html.esc(nota) + "</span>");
        if (filas.isEmpty()) {
            return new Respuesta(titulo, encabezado + Html.info(Html.esc(sinDatos)));
        }
        List<String[]> tabla = new ArrayList<>();
        for (int i = 0; i < filas.size(); i++) {
            boolean total = i == filas.size() - 1;
            String[] celdas = new String[filas.get(i).length];
            for (int j = 0; j < celdas.length; j++) {
                String v = Html.esc(filas.get(i)[j]);
                celdas[j] = total ? "<b>" + v + "</b>" : v;
            }
            tabla.add(celdas);
        }
        return new Respuesta(titulo, encabezado + Html.tablaHtml(columnas, tabla) + otrosReportes(r));
    }

    private static String otrosReportes(Rango r) {
        String rango = "\"" + r.desde + "\",\"" + r.hasta + "\"";
        return Html.subtitulo("Otros reportes del mismo rango")
                + Html.parrafo(Html.codigo("repPro[" + rango + "]") + " " + Html.codigo("repMat[" + rango + "]") + " "
                + Html.codigo("repPau[" + rango + "]") + " " + Html.codigo("repCum[" + rango + "]"));
    }
}
