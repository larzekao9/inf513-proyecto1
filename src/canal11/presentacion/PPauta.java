package canal11.presentacion;

import canal11.negocio.NPauta;
import canal11.negocio.NSpot;
import canal11.negocio.Usuario;

import java.util.ArrayList;
import java.util.List;

/**
 * CU6 - Capa de presentacion de pautas y spots: lee los parametros, llama a NPauta / NSpot y arma el HTML.
 * Sin SQL ni reglas de negocio. Cada respuesta trae los comandos siguientes ya rellenados.
 */
public class PPauta {

    /** Columnas que devuelve DPauta (mismo orden). */
    private static final String[] COLUMNAS = {"ID", "Nombre", "Anunciante", "Fecha de inicio", "Fecha de fin", "Máx. pases",
            "Activa", "Pases usados", "Pases disponibles"};

    private final NPauta pautas = new NPauta();
    private final NSpot spots = new NSpot();

    /** lisPau["*"] | lisPau["anunciante"] */
    public Respuesta listar(Usuario u, List<String> p) throws Exception {
        List<String[]> filas = pautas.listar(p.get(0));
        List<String[]> tabla = new ArrayList<>();
        for (String[] f : filas) {
            tabla.add(new String[]{
                    Html.esc(f[1]),
                    Html.esc(f[2]),
                    Html.esc(f[3] + " a " + f[4]),
                    Html.esc(f[7] + " / " + f[5]),
                    Html.esc(f[6]),
                    Html.codigo("mosPau[\"" + f[1] + "\"]")});
        }
        String filtro = p.get(0).trim().equals("*") ? "Todas las pautas." : "Filtro: " + p.get(0).trim();
        return new Respuesta("Pautas publicitarias", Html.parrafo(Html.esc(filtro))
                + Html.tablaHtml(new String[]{"Pauta", "Anunciante", "Vigencia", "Pases (usados / máx.)", "Activa", "Ver detalle"}, tabla)
                + Html.total(filas.size()));
    }

    /** mosPau["nombre"] */
    public Respuesta mostrar(Usuario u, List<String> p) throws Exception {
        String[] f = pautas.mostrar(p.get(0));
        return new Respuesta("Pauta " + f[1], vista(f));
    }

    /** insPau["nombre","anunciante","fecha_inicio","fecha_fin","max_pases"] */
    public Respuesta insertar(Usuario u, List<String> p) throws Exception {
        int id = pautas.insertar(u, p);
        String[] f = pautas.mostrar(String.valueOf(id));
        return new Respuesta("Pauta registrada",
                Html.exito("La pauta <b>" + Html.esc(f[1]) + "</b> fue registrada. Ahora ubique sus spots con insSpo.") + vista(f));
    }

    /** eliPau["nombre"] */
    public Respuesta eliminar(Usuario u, List<String> p) throws Exception {
        int id = pautas.eliminar(u, p.get(0));
        String[] f = pautas.mostrar(String.valueOf(id));
        return new Respuesta("Pauta dada de baja",
                Html.exito("La pauta <b>" + Html.esc(f[1]) + "</b> fue dada de baja.") + Html.ficha(COLUMNAS, f));
    }

    /** insSpo["pauta","material","fecha","hora_pausa","hora_spot"] */
    public Respuesta insertarSpot(Usuario u, List<String> p) throws Exception {
        spots.insertar(u, p);
        String[] f = pautas.mostrar(p.get(0));
        return new Respuesta("Spot insertado",
                Html.exito("El spot fue insertado en la pausa comercial del <b>" + Html.esc(p.get(2).trim()) + "</b> a las <b>"
                        + Html.esc(p.get(4).trim()) + "</b>.") + vista(f));
    }

    /** eliSpo["fecha","hora_spot"] */
    public Respuesta eliminarSpot(Usuario u, List<String> p) throws Exception {
        String[] spot = spots.eliminar(u, p);
        String[] f = pautas.mostrar(spot[3]);
        return new Respuesta("Spot quitado",
                Html.exito("El spot <b>" + Html.esc(spot[4]) + "</b> de las <b>" + hora(spot[2]) + "</b> del <b>" + spot[1]
                        + "</b> fue quitado de la pauta <b>" + Html.esc(spot[3]) + "</b>.") + vista(f));
    }

    // ------------------------------------------------------------------ vista comun

    /** Ficha de la pauta + tabla de spots + acciones sugeridas. */
    private String vista(String[] f) throws Exception {
        List<String[]> lista = pautas.spots(f[1]);
        boolean activa = "Sí".equals(f[6]);

        List<String[]> tabla = new ArrayList<>();
        for (String[] s : lista) {
            tabla.add(new String[]{
                    Html.esc(s[1]),
                    Html.esc(hora(s[2])),
                    Html.esc(s[4] + " - " + s[5]),
                    Html.esc(s[6]),
                    Html.esc(horario(s[8])),
                    Html.esc(s[7]),
                    "PROGRAMADO".equals(s[7]) ? Html.codigo("eliSpo[\"" + s[1] + "\",\"" + hora(s[2]) + "\"]") : ""});
        }
        StringBuilder sb = new StringBuilder(Html.ficha(COLUMNAS, f));
        sb.append(Html.subtitulo("Spots programados"));
        sb.append(Html.tablaHtml(new String[]{"Fecha", "Hora", "Material", "Duración (seg)", "Pausa", "Estado", "Quitar"}, tabla));
        sb.append(Html.parrafo("Pases usados: <b>" + Html.esc(f[7]) + "</b> de " + Html.esc(f[5]) + " &middot; disponibles: <b>"
                + Html.esc(f[8]) + "</b>"));
        if (activa) {
            sb.append(Html.subtitulo("Acciones (copie, cambie lo necesario y envíe)"));
            sb.append(Html.parrafo("Insertar un spot: " + Html.codigo("insSpo[\"" + f[1] + "\",\"CODIGO-SPOT\",\"" + f[3]
                    + "\",\"20:30\",\"20:30:00\"]")));
            sb.append(Html.parrafo("Ver spots disponibles: " + Html.codigo("lisMat[\"SPOT\"]")));
            sb.append(Html.parrafo("Dar de baja la pauta: " + Html.codigo("eliPau[\"" + f[1] + "\"]")));
        }
        return sb.toString();
    }

    /** "20:00:00" -> "20:00"; "20:00:30" se deja igual. */
    private static String hora(String h) {
        return h.endsWith(":00") && h.length() == 8 ? h.substring(0, 5) : h;
    }

    /** "20:00:00-20:10:00" -> "20:00-20:10". */
    private static String horario(String h) {
        String[] partes = h.split("-");
        return partes.length == 2 ? hora(partes[0]) + "-" + hora(partes[1]) : h;
    }
}
