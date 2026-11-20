package canal11.presentacion;

import canal11.negocio.NPrograma;
import canal11.negocio.Usuario;

import java.util.ArrayList;
import java.util.List;

/**
 * CU2 - Capa de presentacion de programas: lee los parametros, llama a NPrograma y arma el HTML.
 * Sin SQL ni reglas de negocio. Cada respuesta trae los comandos siguientes ya rellenados.
 */
public class PPrograma {

    /** Columnas que devuelve DPrograma (mismo orden). */
    private static final String[] COLUMNAS = {"ID", "Nombre", "Descripción", "Tipo", "Duración (min)", "Género", "Clasificación", "Activo"};

    private final NPrograma negocio = new NPrograma();

    /** lisPro["*"] | lisPro["PELICULA"] | lisPro["texto"] */
    public Respuesta listar(Usuario u, List<String> p) throws Exception {
        List<String[]> filas = negocio.listar(p.get(0));
        List<String[]> tabla = new ArrayList<>();
        for (String[] f : filas) {
            tabla.add(new String[]{
                    Html.esc(f[1]),
                    Html.esc(f[3]),
                    Html.esc(f[4]),
                    Html.esc(f[5]),
                    Html.esc(f[7]),
                    Html.codigo("mosPro[\"" + f[1] + "\"]")});
        }
        String filtro = p.get(0).trim().equals("*") ? "Todos los programas." : "Filtro: " + p.get(0).trim();
        return new Respuesta("Programas", Html.parrafo(Html.esc(filtro))
                + Html.tablaHtml(new String[]{"Nombre", "Tipo", "Duración (min)", "Género", "Activo", "Ver detalle"}, tabla)
                + Html.total(filas.size()));
    }

    /** mosPro["nombre"] */
    public Respuesta mostrar(Usuario u, List<String> p) throws Exception {
        String[] f = negocio.mostrar(p.get(0));
        return new Respuesta("Programa " + f[1], Html.ficha(COLUMNAS, f) + acciones(f));
    }

    /** insPro["nombre","descripcion","tipo","duracion_min","genero","clasificacion"] */
    public Respuesta insertar(Usuario u, List<String> p) throws Exception {
        int id = negocio.insertar(u, p);
        String[] f = negocio.mostrar(String.valueOf(id));
        return new Respuesta("Programa registrado",
                Html.exito("El programa <b>" + Html.esc(f[1]) + "</b> fue registrado en el catálogo.")
                        + Html.ficha(COLUMNAS, f) + acciones(f));
    }

    /** modPro["nombre actual","nombre","descripcion","tipo","duracion_min","genero","clasificacion"] */
    public Respuesta modificar(Usuario u, List<String> p) throws Exception {
        int id = negocio.modificar(u, p);
        String[] f = negocio.mostrar(String.valueOf(id));
        return new Respuesta("Programa modificado",
                Html.exito("Los datos del programa fueron actualizados.") + Html.ficha(COLUMNAS, f) + acciones(f));
    }

    /** eliPro["nombre"] */
    public Respuesta eliminar(Usuario u, List<String> p) throws Exception {
        int id = negocio.eliminar(u, p.get(0));
        String[] f = negocio.mostrar(String.valueOf(id));
        return new Respuesta("Programa dado de baja",
                Html.exito("El programa <b>" + Html.esc(f[1]) + "</b> fue dado de baja del catálogo.") + Html.ficha(COLUMNAS, f));
    }

    /** Comandos sugeridos ya rellenados con los datos del programa. */
    private static String acciones(String[] f) {
        String mod = "modPro[\"" + f[1] + "\",\"" + f[1] + "\",\"" + f[2] + "\",\"" + f[3] + "\",\"" + f[4] + "\",\""
                + f[5] + "\",\"" + f[6] + "\"]";
        boolean activo = "Sí".equals(f[7]);
        return Html.subtitulo("Acciones (copie, cambie lo necesario y envíe)")
                + Html.parrafo("Modificar: " + Html.codigo(mod))
                + (activo ? Html.parrafo("Dar de baja: " + Html.codigo("eliPro[\"" + f[1] + "\"]")) : "");
    }
}
