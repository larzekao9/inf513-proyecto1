package canal11.negocio;

import canal11.datos.DUsuario;

import java.sql.SQLException;
import java.util.List;

/**
 * CU1 - Gestion de Usuarios. Reglas de negocio:
 *  - Solo el Administrador gestiona usuarios (el permiso lo controla el Despachador).
 *  - Un usuario se referencia por su CI, su email o su ID (no hace falta saber el ID).
 *  - CI y email son unicos; el email debe ser valido (es desde donde el usuario envia los comandos).
 *  - Nombre y apellido solo letras. Rol: ADMINISTRADOR (Programacion), ENCARGADO (Prensa) o PRODUCCION.
 *  - eliUsu es baja logica (activo = false): el usuario puede tener historial en otras tablas.
 *  - actUsu reactiva a un usuario dado de baja (por si se dio de baja por error).
 *  - No se puede dar de baja a uno mismo, y el sistema nunca se queda sin un Administrador activo.
 *  - Cada alta, modificacion y baja se registra en la auditoria.
 */
public class NUsuario {

    private static final String[] ROLES = {"ADMINISTRADOR", "ENCARGADO", "PRODUCCION"};

    private final DUsuario datos = new DUsuario();
    private final NAuditoria auditoria = new NAuditoria();

    /** Identifica al remitente del correo. Lanza NegocioException si no esta registrado o esta inactivo. */
    public Usuario identificar(String email) throws NegocioException, SQLException {
        String[] fila = datos.buscarPorEmail(email == null ? "" : email.trim());
        if (fila == null) {
            throw new NegocioException("El correo <b>" + Validador.esc(email) + "</b> no está registrado en el sistema. "
                    + "Solicite su registro al Administrador.");
        }
        if (!Boolean.parseBoolean(fila[3])) {
            throw new NegocioException("Su usuario está dado de baja. Comuníquese con el Administrador.");
        }
        Rol rol = Rol.desde(fila[2]);
        if (rol == null) { // rol que ya no existe (por ejemplo PROGRAMADOR, que pasó a ser ADMINISTRADOR)
            throw new NegocioException("Su rol (" + Validador.esc(fila[2]) + ") ya no existe en el sistema. "
                    + "Pida al Administrador que le asigne uno de: ADMINISTRADOR, ENCARGADO o PRODUCCION.");
        }
        return new Usuario(Integer.parseInt(fila[0]), fila[1], email, rol);
    }

    /** filtro: "*" = todos; un rol (ej. ENCARGADO) = por rol; otro texto = busca por nombre, apellido o email. */
    public List<String[]> listar(String filtro) throws NegocioException, SQLException {
        Validador v = new Validador();
        String f = v.texto(filtro, "filtro", 60);
        v.verificar();
        if (f.equals("*")) {
            return datos.listar(null, null);
        }
        Rol rol = Rol.desde(f);
        return rol != null ? datos.listar(rol.name(), null) : datos.listar(null, f);
    }

    /** referencia: CI, email o ID. Devuelve la fila completa. */
    public String[] mostrar(String referencia) throws NegocioException, SQLException {
        return datos.obtener(resolver(referencia));
    }

    /** insUsu["ci","nombre","apellido","email","telefono","rol"]. Devuelve el id creado. */
    public int insertar(Usuario quien, List<String> p) throws NegocioException, SQLException {
        Validador v = new Validador();
        String ci = v.ci(p.get(0));
        String nombre = v.nombre(p.get(1), "nombre");
        String apellido = v.nombre(p.get(2), "apellido");
        String email = v.email(p.get(3));
        String telefono = v.telefono(p.get(4));
        String rol = v.opcion(p.get(5), "rol", ROLES);
        v.verificar(); // primero el formato de todos los campos

        validarUnicos(v, ci, email, 0);
        v.verificar(); // luego que no esten repetidos

        int id = datos.insertar(ci, nombre, apellido, email, telefono, rol);
        auditoria.registrar(quien, NAuditoria.INSERTAR, "usuario", id,
                nombre + " " + apellido + " <" + email + "> rol " + rol, "insUsu");
        return id;
    }

    /** modUsu["usuario (CI, email o ID)","ci","nombre","apellido","email","telefono","rol"]. Devuelve el id. */
    public int modificar(Usuario quien, List<String> p) throws NegocioException, SQLException {
        int id = resolver(p.get(0));
        Validador v = new Validador();
        String ci = v.ci(p.get(1));
        String nombre = v.nombre(p.get(2), "nombre");
        String apellido = v.nombre(p.get(3), "apellido");
        String email = v.email(p.get(4));
        String telefono = v.telefono(p.get(5));
        String rol = v.opcion(p.get(6), "rol", ROLES);
        v.verificar();

        String[] actual = datos.obtener(id);
        String[] estado = datos.rolYEstado(id);
        boolean eraAdminActivo = estado[0].equals("ADMINISTRADOR") && Boolean.parseBoolean(estado[1]);
        if (eraAdminActivo && !rol.equals("ADMINISTRADOR") && datos.contarAdministradoresActivos() <= 1) {
            v.error("No se puede cambiar el rol del único Administrador activo; primero registre otro Administrador.");
        }
        validarUnicos(v, ci, email, id);
        v.verificar();

        datos.modificar(id, ci, nombre, apellido, email, telefono, rol);
        auditoria.registrar(quien, NAuditoria.MODIFICAR, "usuario", id,
                "Antes: " + actual[2] + " " + actual[3] + " <" + actual[4] + "> rol " + actual[6]
                        + " | Ahora: " + nombre + " " + apellido + " <" + email + "> rol " + rol, "modUsu");
        return id;
    }

    /** eliUsu["usuario (CI, email o ID)"]. Devuelve el id. */
    public int eliminar(Usuario quien, String referencia) throws NegocioException, SQLException {
        int id = resolver(referencia);
        String[] actual = datos.obtener(id);
        String[] estado = datos.rolYEstado(id);
        if (!Boolean.parseBoolean(estado[1])) {
            throw new NegocioException("El usuario <b>" + Validador.esc(actual[2] + " " + actual[3]) + "</b> ya está dado de baja.");
        }
        if (id == quien.getId()) {
            throw new NegocioException("No puede darse de baja a sí mismo.");
        }
        if (estado[0].equals("ADMINISTRADOR") && datos.contarAdministradoresActivos() <= 1) {
            throw new NegocioException("No se puede dar de baja al único Administrador activo.");
        }
        datos.darDeBaja(id);
        auditoria.registrar(quien, NAuditoria.ELIMINAR, "usuario", id,
                "Baja de " + actual[2] + " " + actual[3] + " <" + actual[4] + ">", "eliUsu");
        return id;
    }

    /** actUsu["usuario (CI, email o ID)"]. Devuelve el id. */
    public int reactivar(Usuario quien, String referencia) throws NegocioException, SQLException {
        int id = resolver(referencia);
        String[] actual = datos.obtener(id);
        String[] estado = datos.rolYEstado(id);
        if (Boolean.parseBoolean(estado[1])) {
            throw new NegocioException("El usuario <b>" + Validador.esc(actual[2] + " " + actual[3]) + "</b> ya está activo.");
        }
        datos.reactivar(id);
        auditoria.registrar(quien, NAuditoria.REACTIVAR, "usuario", id,
                "Reactivacion de " + actual[2] + " " + actual[3] + " <" + actual[4] + ">", "actUsu");
        return id;
    }

    /** Convierte la referencia (CI, email o ID) en el id del usuario. */
    private int resolver(String referencia) throws NegocioException, SQLException {
        Validador v = new Validador();
        String ref = v.texto(referencia, "usuario", 120);
        v.verificar();
        Integer id = datos.buscarId(ref);
        if (id == null) {
            throw new NegocioException("No existe un usuario con CI, email o ID <b>" + Validador.esc(ref) + "</b>. "
                    + "Consulte los usuarios con lisUsu[\"*\"].");
        }
        return id;
    }

    private void validarUnicos(Validador v, String ci, String email, int excluirId) throws SQLException {
        if (datos.existe("ci", ci, excluirId)) {
            v.error("Ya existe otro usuario con el CI <b>" + Validador.esc(ci) + "</b>.");
        }
        if (datos.existe("email", email, excluirId)) {
            v.error("Ya existe otro usuario con el email <b>" + Validador.esc(email) + "</b>.");
        }
    }
}
