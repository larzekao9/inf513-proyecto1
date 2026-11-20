package canal11.presentacion;

import canal11.negocio.NUsuario;
import canal11.negocio.Usuario;

import java.util.ArrayList;
import java.util.List;

/**
 * CU1 - Capa de presentacion de usuarios: recibe los parametros del comando, llama a NUsuario
 * y arma el HTML de la respuesta. No tiene reglas de negocio ni SQL.
 *
 * Cada respuesta incluye los comandos siguientes YA RELLENADOS con los datos reales,
 * para que el usuario solo copie, cambie lo que necesite y envie (no hay que adivinar IDs).
 */
public class PUsuario {

    /** Columnas que devuelve DUsuario (mismo orden). */
    private static final String[] COLUMNAS = {"ID", "CI", "Nombre", "Apellido", "Email", "Teléfono", "Rol", "Activo", "Registrado"};

    private final NUsuario negocio = new NUsuario();

    /** lisUsu["*"] | lisUsu["ENCARGADO"] | lisUsu["texto"] */
    public Respuesta listar(Usuario u, List<String> p) throws Exception {
        List<String[]> filas = negocio.listar(p.get(0));
        List<String[]> tabla = new ArrayList<>();
        for (String[] f : filas) {
            tabla.add(new String[]{
                    Html.esc(f[1]),
                    Html.esc(f[2] + " " + f[3]),
                    Html.esc(f[4]),
                    Html.esc(f[6]),
                    Html.esc(f[7]),
                    Html.codigo("mosUsu[\"" + f[1] + "\"]")});
        }
        String filtro = p.get(0).trim().equals("*") ? "Todos los usuarios." : "Filtro: " + p.get(0).trim();
        return new Respuesta("Usuarios", Html.parrafo(Html.esc(filtro))
                + Html.tablaHtml(new String[]{"CI", "Nombre", "Email", "Rol", "Activo", "Ver detalle"}, tabla)
                + Html.total(filas.size()));
    }

    /** mosUsu["CI, email o ID"] */
    public Respuesta mostrar(Usuario u, List<String> p) throws Exception {
        String[] f = negocio.mostrar(p.get(0));
        return new Respuesta("Usuario " + f[2] + " " + f[3], Html.ficha(COLUMNAS, f) + acciones(f));
    }

    /** insUsu["ci","nombre","apellido","email","telefono","rol"] */
    public Respuesta insertar(Usuario u, List<String> p) throws Exception {
        int id = negocio.insertar(u, p);
        String[] f = negocio.mostrar(String.valueOf(id));
        return new Respuesta("Usuario registrado",
                Html.exito("El usuario fue registrado correctamente. Ya puede enviar comandos desde <b>" + Html.esc(f[4]) + "</b>.")
                        + Html.ficha(COLUMNAS, f) + acciones(f));
    }

    /** modUsu["CI, email o ID","ci","nombre","apellido","email","telefono","rol"] */
    public Respuesta modificar(Usuario u, List<String> p) throws Exception {
        int id = negocio.modificar(u, p);
        String[] f = negocio.mostrar(String.valueOf(id));
        return new Respuesta("Usuario modificado",
                Html.exito("Los datos del usuario fueron actualizados.") + Html.ficha(COLUMNAS, f));
    }

    /** eliUsu["CI, email o ID"] */
    public Respuesta eliminar(Usuario u, List<String> p) throws Exception {
        int id = negocio.eliminar(u, p.get(0));
        String[] f = negocio.mostrar(String.valueOf(id));
        return new Respuesta("Usuario dado de baja",
                Html.exito("El usuario <b>" + Html.esc(f[2] + " " + f[3]) + "</b> fue dado de baja. Ya no podrá enviar comandos.")
                        + Html.ficha(COLUMNAS, f) + acciones(f));
    }

    /** actUsu["CI, email o ID"] */
    public Respuesta reactivar(Usuario u, List<String> p) throws Exception {
        int id = negocio.reactivar(u, p.get(0));
        String[] f = negocio.mostrar(String.valueOf(id));
        return new Respuesta("Usuario reactivado",
                Html.exito("El usuario <b>" + Html.esc(f[2] + " " + f[3]) + "</b> fue reactivado. Ya puede volver a enviar comandos.")
                        + Html.ficha(COLUMNAS, f) + acciones(f));
    }

    /** Comandos sugeridos, ya rellenados con los datos del usuario (baja si esta activo, reactivar si no). */
    private static String acciones(String[] f) {
        String mod = "modUsu[\"" + f[1] + "\",\"" + f[1] + "\",\"" + f[2] + "\",\"" + f[3] + "\",\"" + f[4] + "\",\""
                + f[5] + "\",\"" + f[6] + "\"]";
        boolean activo = "Sí".equals(f[7]);
        return Html.subtitulo("Acciones (copie, cambie lo necesario y envíe)")
                + Html.parrafo("Modificar: " + Html.codigo(mod))
                + (activo ? Html.parrafo("Dar de baja: " + Html.codigo("eliUsu[\"" + f[1] + "\"]"))
                          : Html.parrafo("Reactivar: " + Html.codigo("actUsu[\"" + f[1] + "\"]")));
    }
}
