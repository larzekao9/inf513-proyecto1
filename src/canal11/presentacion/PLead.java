package canal11.presentacion;

import canal11.config.Registro;
import canal11.negocio.NAviso;
import canal11.negocio.NLead;
import canal11.negocio.Rol;
import canal11.negocio.Usuario;

import java.util.ArrayList;
import java.util.List;

/**
 * Presentacion de la escaleta del noticiero (lisLea, insLea, modLea, eliLea): lee los parametros, llama a NLead y arma el HTML.
 * Si la parrilla ya esta PUBLICADA, cada cambio se avisa por correo a Produccion y Programacion (reemplaza al WhatsApp).
 */
public class PLead {

    private static final String[] COLUMNAS = {"Orden", "Entrada estimada", "Código", "Título", "Duración (seg)", "Estado", "Registrado por"};

    private final NLead negocio = new NLead();
    private final NAviso avisos = new NAviso();

    /** lisLea["fecha","hora_bloque"] */
    public Respuesta listar(Usuario u, List<String> p) throws Exception {
        return new Respuesta("Escaleta del noticiero", vista(p.get(0), p.get(1)));
    }

    /** insLea["fecha","hora_bloque","orden","material"] */
    public Respuesta insertar(Usuario u, List<String> p) throws Exception {
        int orden = negocio.insertar(u, p);
        avisar(u, p.get(0), p.get(1), "agregó la nota <b>" + Html.esc(p.get(3).trim()) + "</b> en el orden " + orden);
        return new Respuesta("Nota agregada a la escaleta",
                Html.exito("La nota <b>" + Html.esc(p.get(3).trim()) + "</b> quedó en el orden <b>" + orden + "</b>.") + vista(p.get(0), p.get(1)));
    }

    /** modLea["fecha","hora_bloque","material","nuevo_orden"] */
    public Respuesta mover(Usuario u, List<String> p) throws Exception {
        int antes = negocio.mover(u, p);
        avisar(u, p.get(0), p.get(1), "movió la nota <b>" + Html.esc(p.get(2).trim()) + "</b> del orden " + antes + " al " + p.get(3).trim());
        return new Respuesta("Escaleta reordenada",
                Html.exito("La nota <b>" + Html.esc(p.get(2).trim()) + "</b> pasó del orden " + antes + " al <b>" + Html.esc(p.get(3).trim())
                        + "</b>.") + vista(p.get(0), p.get(1)));
    }

    /** eliLea["fecha","hora_bloque","material"] */
    public Respuesta eliminar(Usuario u, List<String> p) throws Exception {
        int orden = negocio.eliminar(u, p);
        avisar(u, p.get(0), p.get(1), "quitó la nota <b>" + Html.esc(p.get(2).trim()) + "</b> (era la " + orden + ")");
        return new Respuesta("Nota quitada de la escaleta",
                Html.exito("La nota <b>" + Html.esc(p.get(2).trim()) + "</b> fue quitada; las siguientes subieron un lugar.")
                        + vista(p.get(0), p.get(1)));
    }

    // ------------------------------------------------------------------ apoyo

    /** Tabla de la escaleta (tambien se usa en el correo de la parrilla publicada). */
    static String tabla(List<String[]> filas) {
        List<String[]> t = new ArrayList<>();
        for (String[] f : filas) {
            t.add(new String[]{Html.esc(f[0]), Html.esc(f[1]), Html.esc(f[2]), Html.esc(f[3]), Html.esc(f[4]), Html.esc(f[5]), Html.esc(f[6])});
        }
        return Html.tablaHtml(COLUMNAS, t);
    }

    private String vista(String fecha, String hora) throws Exception {
        String[] b = negocio.bloque(fecha, hora);
        List<String[]> filas = negocio.listar(fecha, hora);
        int total = 0;
        for (String[] f : filas) {
            total += Integer.parseInt(f[4]);
        }
        String f5 = b[1].substring(0, 5);
        return Html.parrafo("Bloque de <b>" + Html.esc(b[4]) + "</b>, " + Html.esc(fecha.trim()) + " de " + Html.esc(b[1]) + " a " + Html.esc(b[2])
                + ". Las notas salen en este orden; la hora de entrada es una estimación (inicio del bloque + duración de las notas anteriores).")
                + tabla(filas) + Html.total(filas.size())
                + Html.parrafo("Duración total de las notas: <b>" + total + " s</b>.")
                + Html.subtitulo("Acciones (copie, cambie lo necesario y envíe)")
                + Html.parrafo("Agregar una nota: " + Html.codigo("insLea[\"" + fecha.trim() + "\",\"" + f5 + "\",\"" + (filas.size() + 1) + "\",\"CODIGO\"]"))
                + Html.parrafo("Mover una nota: " + Html.codigo("modLea[\"" + fecha.trim() + "\",\"" + f5 + "\",\"CODIGO\",\"1\"]"))
                + Html.parrafo("Quitar una nota: " + Html.codigo("eliLea[\"" + fecha.trim() + "\",\"" + f5 + "\",\"CODIGO\"]"));
    }

    /** Si la parrilla ya esta publicada, avisa el cambio a Produccion y Programacion. */
    private void avisar(Usuario u, String fecha, String hora, String que) throws Exception {
        try {
            if (!Notificador.activo() || !negocio.parrillaPublicada(fecha)) {
                return;
            }
            enviarAviso(u, fecha, hora, que);
        } catch (Exception e) { // el cambio ya se hizo: un aviso que falla no lo convierte en error
            Registro.errorSistema("Aviso de cambio en la escaleta " + fecha, e);
        }
    }

    private void enviarAviso(Usuario u, String fecha, String hora, String que) throws Exception {
        String contenido = Html.parrafo(Html.esc(u.getNombre()) + " (" + Html.esc(u.getRol().getNombre()) + ") " + que + ".")
                + Html.info("La parrilla del <b>" + Html.esc(fecha.trim()) + "</b> ya está publicada: este es el orden vigente del noticiero.")
                + vista(fecha, hora);
        Notificador.enviar(avisos.emailsDeRoles(u.getEmail(), Rol.PRODUCCION, Rol.ADMINISTRADOR),
                "Cambió el orden del noticiero del " + fecha.trim(), contenido);
    }
}
