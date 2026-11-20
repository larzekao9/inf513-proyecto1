package canal11.presentacion;

import canal11.negocio.NEmision;
import canal11.negocio.Usuario;

import java.util.ArrayList;
import java.util.List;

/**
 * CU7 - Capa de presentacion de la emision real: lee los parametros, llama a NEmision y arma el HTML.
 * Sin SQL ni reglas de negocio. Las respuestas traen los comandos siguientes ya rellenados.
 */
public class PEmision {

    private final NEmision negocio = new NEmision();

    /** insEmi["fecha","hora_inicio","inicio_real","fin_real","estado"] */
    public Respuesta insertar(Usuario u, List<String> p) throws Exception {
        negocio.insertar(u, p);
        return new Respuesta("Emisión registrada",
                Html.exito("Se registró la emisión del bloque de las <b>" + Html.esc(p.get(1).trim()) + "</b> del <b>"
                        + Html.esc(p.get(0).trim()) + "</b> como <b>" + Html.esc(p.get(4).trim().toUpperCase()) + "</b>.")
                        + vista(p.get(0)));
    }

    /** conPar["fecha"] */
    public Respuesta conciliar(Usuario u, List<String> p) throws Exception {
        return new Respuesta("Conciliación " + p.get(0).trim(), vista(p.get(0)));
    }

    // ------------------------------------------------------------------ vista comun

    /** Resumen de cumplimiento + tabla programado vs. emitido por bloque. */
    private String vista(String fechaTxt) throws Exception {
        List<String[]> bloques = negocio.conciliar(fechaTxt);
        String fecha = fechaTxt.trim();

        int emitidos = 0;
        int parciales = 0;
        int noEmitidos = 0;
        int sinRegistro = 0;
        List<String[]> tabla = new ArrayList<>();
        for (String[] b : bloques) {
            switch (b[5]) {
                case "EMITIDO":
                    emitidos++;
                    break;
                case "PARCIAL":
                    parciales++;
                    break;
                case "NO_EMITIDO":
                    noEmitidos++;
                    break;
                default:
                    sinRegistro++;
            }
            tabla.add(new String[]{
                    Html.esc(hora(b[1]) + " - " + hora(b[2])),
                    Html.esc(b[3]),
                    Html.esc(b[4]),
                    Html.esc(b[6].isEmpty() ? "" : hora(b[6]) + " - " + hora(b[7])),
                    Html.esc(desviacion(b[8])),
                    insignia(b[5]),
                    "SIN REGISTRO".equals(b[5])
                            ? Html.codigo("insEmi[\"" + fecha + "\",\"" + hora(b[1]) + "\",\"" + b[1] + "\",\"" + b[2] + "\",\"EMITIDO\"]")
                            : ""});
        }

        StringBuilder sb = new StringBuilder();
        if (bloques.isEmpty()) {
            sb.append(Html.info("La parrilla del <b>" + Html.esc(fecha) + "</b> no tiene bloques."));
        } else {
            double pct = NEmision.cumplimiento(bloques);
            String resumen = "Cumplimiento del día: <b>" + pct + " %</b> &mdash; " + emitidos + " de " + bloques.size()
                    + " bloques emitidos completos &middot; " + parciales + " parcial(es) &middot; " + noEmitidos
                    + " no emitido(s) &middot; " + sinRegistro + " sin registro.";
            sb.append(pct >= 100 ? Html.exito(resumen) : (sinRegistro == bloques.size() ? Html.info(resumen) : Html.error(resumen)));
        }
        sb.append(Html.tablaHtml(new String[]{"Programado", "Tipo", "Programa", "Emitido (real)", "Desviación del inicio", "Estado",
                "Registrar emisión (ejemplo)"}, tabla));
        sb.append(Html.parrafo("<span style='font-size:12px;'>Cumplimiento = bloques EMITIDO / bloques programados "
                + "(un bloque PARCIAL no cuenta como cumplido). Para un bloque que no salió al aire use "
                + Html.codigo("insEmi[\"" + Html.esc(fecha) + "\",\"HH:MM\",\"\",\"\",\"NO_EMITIDO\"]") + ".</span>"));
        return sb.toString();
    }

    /** Segundos de desviacion -> "+5 s", "-3 s", "puntual". Vacio si no hay dato. */
    private static String desviacion(String segundos) {
        if (segundos.isEmpty()) {
            return "";
        }
        long s = Long.parseLong(segundos);
        return s == 0 ? "puntual" : (s > 0 ? "+" : "") + s + " s";
    }

    private static String insignia(String estado) {
        String color;
        switch (estado) {
            case "EMITIDO":
                color = "#2e7d32";
                break;
            case "PARCIAL":
                color = "#b26a00";
                break;
            case "SIN REGISTRO":
                color = "#6b7785";
                break;
            default:
                color = "#c62828";
        }
        return "<b style='color:" + color + ";'>" + Html.esc(estado) + "</b>";
    }

    /** "20:00:00" -> "20:00"; "20:00:30" se deja igual. */
    private static String hora(String h) {
        return h.endsWith(":00") && h.length() == 8 ? h.substring(0, 5) : h;
    }
}
