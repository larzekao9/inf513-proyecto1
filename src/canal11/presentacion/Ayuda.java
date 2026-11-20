package canal11.presentacion;

import canal11.negocio.Usuario;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Comando help. Flujo:
 *   help        -> tabla con los casos de uso a los que tiene acceso el rol del remitente
 *   help["3"]   -> tabla con los comandos del CU3: descripcion, parametros y ejemplo
 */
public class Ayuda {

    private final List<CasoUso> casosUso;
    private final Collection<Comando> comandos;

    public Ayuda(List<CasoUso> casosUso, Collection<Comando> comandos) {
        this.casosUso = casosUso;
        this.comandos = comandos;
    }

    public Respuesta responder(Usuario u, List<String> parametros) throws ComandoException {
        if (parametros.isEmpty()) {
            return general(u);
        }
        if (parametros.size() != 1 || !parametros.get(0).matches("\\d{1,2}")) {
            throw new ComandoException("Use " + Html.codigo("help") + " o " + Html.codigo("help[\"numero de CU\"]")
                    + ", por ejemplo " + Html.codigo("help[\"3\"]") + ".");
        }
        return detalle(u, Integer.parseInt(parametros.get(0)));
    }

    private Respuesta general(Usuario u) {
        List<String[]> filas = new ArrayList<>();
        for (CasoUso cu : casosUso) {
            List<Comando> permitidos = permitidos(u, cu.getNumero());
            if (permitidos.isEmpty()) {
                continue;
            }
            filas.add(new String[]{
                    "<b>CU" + cu.getNumero() + "</b>",
                    "<b>" + Html.esc(cu.getNombre()) + "</b><br><span style='color:#6b7785;font-size:12px;'>"
                            + Html.esc(cu.getDescripcion()) + "</span>",
                    String.valueOf(permitidos.size()),
                    Html.codigo("help[\"" + cu.getNumero() + "\"]")});
        }
        String html = Html.parrafo("Escriba el comando en el <b>asunto</b> del correo y envíelo a esta misma dirección. "
                + "Los parámetros van entre comillas dobles y separados por comas. Para una comilla dentro de un parámetro use \\\" o \"\".")
                + Html.parrafo("Formato: " + Html.codigo("comando[\"parametro1\",\"parametro2\",...]"))
                + Html.subtitulo("Casos de uso disponibles para su rol")
                + Html.tablaHtml(new String[]{"N°", "Caso de uso", "Comandos", "Ver comandos"}, filas)
                + Html.info("<b>Siguiente paso:</b> envíe " + Html.codigo("help[\"N\"]")
                + " con el número del caso de uso para ver sus comandos, parámetros y un ejemplo listo para copiar.");
        return new Respuesta("Ayuda - Casos de uso", html);
    }

    private Respuesta detalle(Usuario u, int numero) throws ComandoException {
        CasoUso cu = casosUso.stream().filter(c -> c.getNumero() == numero).findFirst()
                .orElseThrow(() -> new ComandoException("No existe el CU" + numero + ". Los casos de uso van del 1 al "
                        + casosUso.size() + ". Envíe " + Html.codigo("help") + " para verlos."));
        List<Comando> permitidos = permitidos(u, numero);
        if (permitidos.isEmpty()) {
            throw new ComandoException("Su rol (" + u.getRol().getNombre() + ") no tiene acceso al CU" + numero + ".");
        }
        List<String[]> filas = new ArrayList<>();
        for (Comando c : permitidos) {
            filas.add(new String[]{
                    Html.codigo(c.getNombre()),
                    Html.esc(c.getDescripcion())
                            + (c.implementado() ? "" : "<br><span style='color:#b26a00;font-size:11px;'>(en desarrollo)</span>"),
                    Html.esc(c.firma()),
                    Html.codigo(c.getEjemplo())});
        }
        String html = Html.parrafo(Html.esc(cu.getDescripcion()))
                + Html.tablaHtml(new String[]{"Comando", "Descripción", "Parámetros", "Ejemplo"}, filas)
                + Html.parrafo("Copie el ejemplo en el asunto, cambie los valores y envíelo. "
                + "Envíe " + Html.codigo("help") + " para volver a la lista de casos de uso.");
        return new Respuesta("Ayuda - CU" + numero + ": " + cu.getNombre(), html);
    }

    private List<Comando> permitidos(Usuario u, int cu) {
        List<Comando> lista = new ArrayList<>();
        for (Comando c : comandos) {
            if (c.getCu() == cu && c.permite(u.getRol())) {
                lista.add(c);
            }
        }
        return lista;
    }
}
