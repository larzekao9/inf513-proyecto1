package canal11.presentacion;

import canal11.config.Registro;
import canal11.negocio.NAviso;
import canal11.negocio.NLead;
import canal11.negocio.NParrilla;
import canal11.negocio.Rol;
import canal11.negocio.Usuario;

import java.util.ArrayList;
import java.util.List;

/**
 * CU3 - Capa de presentacion de la parrilla: lee los parametros, llama a NParrilla y arma el HTML.
 * Sin SQL ni reglas de negocio. Cada respuesta trae los comandos siguientes ya rellenados.
 */
public class PParrilla {

    /** Columnas de la ficha de la parrilla (mismo orden que DParrilla). */
    private static final String[] COLUMNAS = {"ID", "Fecha", "Estado", "Creada por", "Fecha de creación", "Fecha de publicación", "Bloques"};

    private final NParrilla negocio = new NParrilla();
    private final NAviso avisos = new NAviso();
    private final NLead escaletas = new NLead();

    /** lisPar["*"] | lisPar["2026-11"] */
    public Respuesta listar(Usuario u, List<String> p) throws Exception {
        List<String[]> filas = negocio.listar(p.get(0));
        List<String[]> tabla = new ArrayList<>();
        for (String[] f : filas) {
            tabla.add(new String[]{
                    Html.esc(f[1]),
                    Html.esc(f[2]),
                    Html.esc(f[6]),
                    Html.esc(f[3]),
                    Html.esc(f[5]),
                    Html.codigo("mosPar[\"" + f[1] + "\"]")});
        }
        String filtro = p.get(0).trim().equals("*") ? "Todas las parrillas." : "Parrillas del mes " + p.get(0).trim() + ".";
        return new Respuesta("Parrillas", Html.parrafo(Html.esc(filtro))
                + Html.tablaHtml(new String[]{"Fecha", "Estado", "Bloques", "Creada por", "Publicada", "Ver parrilla"}, tabla)
                + Html.total(filas.size()));
    }

    /** mosPar["fecha"] */
    public Respuesta mostrar(Usuario u, List<String> p) throws Exception {
        String[] f = negocio.mostrar(p.get(0));
        return new Respuesta("Parrilla del " + f[1], vista(f, p.get(0)));
    }

    /** insPar["fecha"] */
    public Respuesta insertar(Usuario u, List<String> p) throws Exception {
        negocio.insertar(u, p.get(0));
        String[] f = negocio.mostrar(p.get(0));
        return new Respuesta("Parrilla creada",
                Html.exito("La parrilla del <b>" + f[1] + "</b> fue creada en <b>BORRADOR</b>. Agregue sus bloques con insBlo.")
                        + vista(f, p.get(0)));
    }

    /** eliPar["fecha"] */
    public Respuesta eliminar(Usuario u, List<String> p) throws Exception {
        String[] f = negocio.mostrar(p.get(0));
        negocio.eliminar(u, p.get(0));
        return new Respuesta("Parrilla eliminada",
                Html.exito("La parrilla del <b>" + f[1] + "</b> y sus " + f[6] + " bloque(s) fueron eliminados.")
                        + Html.parrafo("Crear de nuevo: " + Html.codigo("insPar[\"" + f[1] + "\"]")));
    }

    /** insBlo["fecha","hora_inicio","hora_fin","tipo","programa","material"] */
    public Respuesta insertarBloque(Usuario u, List<String> p) throws Exception {
        negocio.insertarBloque(u, p);
        String[] f = negocio.mostrar(p.get(0));
        return new Respuesta("Bloque agregado",
                Html.exito("El bloque fue agregado a la parrilla del <b>" + f[1] + "</b>.") + vista(f, p.get(0)));
    }

    /** eliBlo["fecha","hora_inicio"] */
    public Respuesta eliminarBloque(Usuario u, List<String> p) throws Exception {
        negocio.eliminarBloque(u, p);
        String[] f = negocio.mostrar(p.get(0));
        return new Respuesta("Bloque quitado",
                Html.exito("El bloque de las <b>" + Html.esc(p.get(1).trim()) + "</b> fue quitado de la parrilla.") + vista(f, p.get(0)));
    }

    /** pubPar["fecha"] */
    public Respuesta publicar(Usuario u, List<String> p) throws Exception {
        List<String> avisosDePublicacion = negocio.publicar(u, p.get(0));
        String[] f = negocio.mostrar(p.get(0));
        String html = Html.exito("La parrilla del <b>" + f[1] + "</b> fue <b>PUBLICADA</b>. "
                + "Es ahora la única fuente del orden de transmisión.");
        if (!avisosDePublicacion.isEmpty()) {
            html += Html.info("<b>Avisos</b> (no impidieron publicar):" + Html.lista(escapar(avisosDePublicacion)));
        }
        if (Notificador.activo()) {
            try {
                // El orden de transmision llega solo a Produccion y Prensa: ya no hace falta mandarlo por WhatsApp.
                Notificador.enviar(avisos.emailsDeRoles(u.getEmail(), Rol.PRODUCCION, Rol.ENCARGADO), "Parrilla publicada: " + f[1],
                        Html.parrafo(Html.esc(u.getNombre()) + " publicó la parrilla del <b>" + f[1] + "</b>. Es la <b>única fuente</b> del orden "
                                + "de transmisión: si cambia, recibirá un nuevo aviso.") + correo(p.get(0), avisosDePublicacion));
            } catch (Exception e) { // la parrilla ya se publico: un aviso que falla no debe convertirla en error
                Registro.errorSistema("Aviso de parrilla publicada " + f[1], e);
            }
        }
        return new Respuesta("Parrilla publicada", html + vista(f, p.get(0)));
    }

    /** camBlo["fecha","hora_inicio","material"] */
    public Respuesta cambiarMaterial(Usuario u, List<String> p) throws Exception {
        String[] r = negocio.cambiarMaterialBloque(u, p); // {anterior, nuevo, estado parrilla, programa, inicio, fin}
        String[] f = negocio.mostrar(p.get(0));
        String cambio = "El bloque de <b>" + Html.esc(r[3]) + "</b> (" + Html.esc(hora(r[4]) + " - " + hora(r[5])) + ") pasó del material <b>"
                + Html.esc(r[0]) + "</b> a <b>" + Html.esc(r[1]) + "</b>.";
        if (Notificador.activo() && "PUBLICADA".equals(r[2])) {
            try {
                Notificador.enviar(avisos.emailsDeRoles(u.getEmail(), Rol.PRODUCCION, Rol.ENCARGADO),
                        "Cambio en la parrilla del " + f[1],
                        Html.parrafo(Html.esc(u.getNombre()) + " (" + Html.esc(u.getRol().getNombre()) + ") hizo un cambio en la parrilla publicada del <b>"
                                + f[1] + "</b>.") + Html.info(cambio + " Este es el material que debe salir al aire.") + correo(p.get(0), List.of()));
            } catch (Exception e) { // el cambio ya se hizo: un aviso que falla no lo convierte en error
                Registro.errorSistema("Aviso de cambio de parrilla " + f[1], e);
            }
        }
        String aviso = "PUBLICADA".equals(r[2]) ? Html.info("La parrilla ya está publicada: se avisó del cambio a Producción y Prensa.") : "";
        return new Respuesta("Material del bloque cambiado", Html.exito(cambio) + aviso + vista(f, p.get(0)));
    }

    // ------------------------------------------------------------------ vista comun

    /** Ficha de la parrilla + tabla de bloques + acciones sugeridas. */
    private String vista(String[] f, String fechaTxt) throws Exception {
        List<String[]> bloques = negocio.bloques(fechaTxt);
        boolean borrador = "BORRADOR".equals(f[2]);
        String fecha = f[1];

        List<String[]> tabla = new ArrayList<>();
        for (String[] b : bloques) {
            int notas = Integer.parseInt(b[9]);
            String material = notas > 0 ? "ESCALETA (" + notas + " nota" + (notas == 1 ? "" : "s") + ")"
                    : (b[5].isEmpty() ? ("PROGRAMA".equals(b[3]) ? "SIN MATERIAL" : "") : b[5]);
            String estado = notas > 0 ? "" : (b[5].isEmpty() ? "" : ("No".equals(b[7]) ? b[6] + " (material de baja)" : b[6]));
            tabla.add(new String[]{
                    Html.esc(hora(b[1]) + " - " + hora(b[2])),
                    Html.esc(b[3]),
                    Html.esc(b[4]),
                    Html.esc(material),
                    Html.esc(estado),
                    borrador ? Html.codigo("eliBlo[\"" + fecha + "\",\"" + hora(b[1]) + "\"]") : ""});
        }
        String[] cabecera = borrador
                ? new String[]{"Horario", "Tipo", "Programa", "Material", "Estado del material", "Quitar"}
                : new String[]{"Horario", "Tipo", "Programa", "Material", "Estado del material", ""};

        StringBuilder sb = new StringBuilder(Html.ficha(COLUMNAS, f));
        sb.append(Html.subtitulo("Bloques"));
        sb.append(Html.tablaHtml(cabecera, tabla));
        if (borrador) {
            String siguiente = bloques.isEmpty() ? "20:00" : hora(bloques.get(bloques.size() - 1)[2]);
            sb.append(Html.subtitulo("Acciones (copie, cambie lo necesario y envíe)"));
            sb.append(Html.parrafo("Agregar bloque de programa: "
                    + Html.codigo("insBlo[\"" + fecha + "\",\"" + siguiente + "\",\"HH:MM\",\"PROGRAMA\",\"nombre del programa\",\"CODIGO\"]")));
            sb.append(Html.parrafo("Agregar pausa comercial: "
                    + Html.codigo("insBlo[\"" + fecha + "\",\"" + siguiente + "\",\"HH:MM\",\"PAUSA_COMERCIAL\",\"\",\"\"]")));
            sb.append(Html.parrafo("Revisar y publicar: " + Html.codigo("pubPar[\"" + fecha + "\"]")));
            sb.append(Html.parrafo("Eliminar la parrilla: " + Html.codigo("eliPar[\"" + fecha + "\"]")));
        } else {
            sb.append(Html.subtitulo("Siguientes pasos"));
            sb.append(Html.parrafo("Estado de emisión: " + Html.codigo("estPar[\"" + fecha + "\"]")));
            sb.append(Html.parrafo("Cambiar el material de un bloque que aún no empieza: "
                    + Html.codigo("camBlo[\"" + fecha + "\",\"HH:MM\",\"CODIGO\"]")));
        }
        return sb.toString();
    }

    /** Cuerpo del correo de la parrilla: bloques en orden, con la escaleta de los noticieros. Los avisos de publicacion van al final. */
    private String correo(String fechaTxt, List<String> avisosDePublicacion) throws Exception {
        List<String[]> bloques = negocio.bloques(fechaTxt);
        List<String[]> tabla = new ArrayList<>();
        StringBuilder escaleta = new StringBuilder();
        for (String[] b : bloques) {
            int notas = Integer.parseInt(b[9]);
            tabla.add(new String[]{Html.esc(hora(b[1]) + " - " + hora(b[2])), Html.esc(b[3]), Html.esc(b[4]),
                    Html.esc(notas > 0 ? "ESCALETA (" + notas + ")" : b[5])});
            if (notas > 0) {
                escaleta.append(Html.subtitulo("Escaleta de " + b[4] + " (" + hora(b[1]) + ")"))
                        .append(PLead.tabla(escaletas.listar(fechaTxt, b[1])));
            }
        }
        String html = Html.tablaHtml(new String[]{"Horario", "Tipo", "Programa", "Material"}, tabla) + escaleta;
        if (!avisosDePublicacion.isEmpty()) {
            html += Html.info("<b>Avisos:</b>" + Html.lista(escapar(avisosDePublicacion)));
        }
        return html;
    }

    /** "20:00:00" -> "20:00"; "20:00:30" se deja igual. */
    private static String hora(String h) {
        if (h.equals("23:59:59")) {
            return "24:00"; // fin del dia
        }
        return h.endsWith(":00") && h.length() == 8 ? h.substring(0, 5) : h;
    }

    private static List<String> escapar(List<String> textos) {
        List<String> r = new ArrayList<>();
        for (String t : textos) {
            r.add(Html.esc(t));
        }
        return r;
    }
}
