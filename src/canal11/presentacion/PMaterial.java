package canal11.presentacion;

import canal11.negocio.NMaterial;
import canal11.negocio.Usuario;

import java.util.ArrayList;
import java.util.List;

/**
 * CU4 - Capa de presentacion de materiales: lee los parametros, llama a NMaterial y arma el HTML.
 * Sin SQL ni reglas de negocio. Cada respuesta trae los comandos siguientes ya rellenados.
 */
public class PMaterial {

    /** Columnas que devuelve DMaterial (mismo orden). */
    static final String[] COLUMNAS = {"ID", "Código", "Título", "Tipo", "Programa", "Duración (seg)", "Formato", "Ruta",
            "Estado", "Observación", "Activo", "Registrado por", "Fecha de registro", "Validado por", "Fecha de validación"};

    private final NMaterial negocio = new NMaterial();

    /** lisMat["*"] | lisMat["PENDIENTE"] | lisMat["SPOT"] | lisMat["texto"] */
    public Respuesta listar(Usuario u, List<String> p) throws Exception {
        List<String[]> filas = negocio.listar(p.get(0));
        List<String[]> tabla = new ArrayList<>();
        for (String[] f : filas) {
            tabla.add(new String[]{
                    Html.esc(f[1]),
                    Html.esc(f[2]),
                    Html.esc(f[3]),
                    Html.esc(f[5]),
                    Html.esc(f[8]),
                    Html.esc(f[10]),
                    Html.codigo("mosMat[\"" + f[1] + "\"]")});
        }
        String filtro = p.get(0).trim().equals("*") ? "Todos los materiales." : "Filtro: " + p.get(0).trim();
        return new Respuesta("Materiales", Html.parrafo(Html.esc(filtro))
                + Html.tablaHtml(new String[]{"Código", "Título", "Tipo", "Duración (seg)", "Estado", "Activo", "Ver detalle"}, tabla)
                + Html.total(filas.size()));
    }

    /** mosMat["codigo"] */
    public Respuesta mostrar(Usuario u, List<String> p) throws Exception {
        String[] f = negocio.mostrar(p.get(0));
        return new Respuesta("Material " + f[1], Html.ficha(COLUMNAS, f) + acciones(f));
    }

    /** insMat["codigo","titulo","tipo","programa","duracion_seg","formato","ruta"] */
    public Respuesta insertar(Usuario u, List<String> p) throws Exception {
        int id = negocio.insertar(u, p);
        String[] f = negocio.mostrar(String.valueOf(id));
        String duplicado = duplicados(id);
        return new Respuesta("Material registrado",
                Html.exito("El material <b>" + Html.esc(f[1]) + "</b> fue registrado en estado <b>PENDIENTE</b>. "
                        + "Un Encargado o Administrador debe validarlo antes de usarlo en la parrilla.")
                        + duplicado + Html.ficha(COLUMNAS, f) + acciones(f));
    }

    /** Advertencia (no bloquea) si ya hay otro material activo con el mismo titulo, tipo y duracion. */
    private String duplicados(int id) throws Exception {
        List<String> codigos = negocio.posiblesDuplicados(id);
        if (codigos.isEmpty()) {
            return "";
        }
        List<String> items = new ArrayList<>();
        for (String c : codigos) {
            items.add(Html.codigo("mosMat[\"" + c + "\"]"));
        }
        return Html.info("<b>Posible duplicado:</b> ya hay material(es) activo(s) con el mismo título, tipo y duración. "
                + "Si es el mismo video, dé de baja uno; si es otra versión, ignore este aviso." + Html.lista(items));
    }

    /** modMat["codigo","titulo","tipo","programa","duracion_seg","formato","ruta"] */
    public Respuesta modificar(Usuario u, List<String> p) throws Exception {
        String[] antes = negocio.mostrar(p.get(0));
        int id = negocio.modificar(u, p);
        String[] f = negocio.mostrar(String.valueOf(id));
        String aviso = "APROBADO".equals(antes[8]) && !"APROBADO".equals(f[8])
                ? Html.info("Cambió la ruta o la duración de un material aprobado: volvió a <b>PENDIENTE</b> y debe validarse de nuevo.")
                : "";
        return new Respuesta("Material modificado",
                Html.exito("Los datos del material fueron actualizados.") + aviso + duplicados(id) + Html.ficha(COLUMNAS, f) + acciones(f));
    }

    /** eliMat["codigo"] */
    public Respuesta eliminar(Usuario u, List<String> p) throws Exception {
        int id = negocio.eliminar(u, p.get(0));
        String[] f = negocio.mostrar(String.valueOf(id));
        return new Respuesta("Material dado de baja",
                Html.exito("El material <b>" + Html.esc(f[1]) + "</b> fue dado de baja.") + Html.ficha(COLUMNAS, f));
    }

    /** Comandos sugeridos ya rellenados con los datos del material. */
    private static String acciones(String[] f) {
        String mod = "modMat[\"" + f[1] + "\",\"" + f[2] + "\",\"" + f[3] + "\",\"" + f[4] + "\",\"" + f[5] + "\",\""
                + f[6] + "\",\"" + f[7] + "\"]";
        boolean activo = "Sí".equals(f[10]);
        return Html.subtitulo("Acciones (copie, cambie lo necesario y envíe)")
                + Html.parrafo("Modificar: " + Html.codigo(mod))
                + (activo ? Html.parrafo("Dar de baja: " + Html.codigo("eliMat[\"" + f[1] + "\"]")) : "");
    }
}
