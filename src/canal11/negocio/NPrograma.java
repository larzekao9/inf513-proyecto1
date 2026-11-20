package canal11.negocio;

import canal11.datos.DPrograma;

import java.sql.SQLException;
import java.util.List;

/**
 * CU2 - Catalogo de Programas. Reglas de negocio:
 *  - El nombre es unico (sin distinguir mayusculas). Un programa se referencia por su nombre (o ID).
 *  - Tipo: NOTICIERO, REVISTA, PELICULA, DOCUMENTAL, SERIE u OTRO. Duracion 1 a 600 minutos.
 *  - Descripcion, genero y clasificacion son opcionales (se envian "").
 *  - eliPro es baja logica y NO se permite si el programa tiene bloques en parrillas de hoy en adelante.
 *  - Cada alta, modificacion y baja se registra en la auditoria.
 */
public class NPrograma {

    public static final String[] TIPOS = {"NOTICIERO", "REVISTA", "PELICULA", "DOCUMENTAL", "SERIE", "OTRO"};

    private final DPrograma datos = new DPrograma();
    private final NAuditoria auditoria = new NAuditoria();

    /** filtro: "*" = todos; un tipo (ej. PELICULA) = por tipo; otro texto = busca por nombre, descripcion o genero. */
    public List<String[]> listar(String filtro) throws NegocioException, SQLException {
        Validador v = new Validador();
        String f = v.texto(filtro, "filtro", 60);
        v.verificar();
        if (f.equals("*")) {
            return datos.listar(null, null);
        }
        String mayus = f.toUpperCase();
        for (String t : TIPOS) {
            if (t.equals(mayus)) {
                return datos.listar(t, null);
            }
        }
        return datos.listar(null, f);
    }

    /** referencia: nombre o ID. Devuelve la fila completa. */
    public String[] mostrar(String referencia) throws NegocioException, SQLException {
        return datos.obtener(resolver(referencia));
    }

    /** insPro["nombre","descripcion","tipo","duracion_min","genero","clasificacion"]. Devuelve el id creado. */
    public int insertar(Usuario quien, List<String> p) throws NegocioException, SQLException {
        Validador v = new Validador();
        String nombre = v.texto(p.get(0), "nombre", 120);
        String descripcion = v.textoOpcional(p.get(1), "descripcion", 255);
        String tipo = v.opcion(p.get(2), "tipo", TIPOS);
        Integer duracion = v.entero(p.get(3), "duracion_min", 1, 600);
        String genero = v.textoOpcional(p.get(4), "genero", 60);
        String clasificacion = v.textoOpcional(p.get(5), "clasificacion", 10);
        v.verificar();

        validarNombreUnico(v, nombre, 0);
        v.verificar();

        int id = datos.insertar(nombre, descripcion, tipo, duracion, genero, clasificacion);
        auditoria.registrar(quien, NAuditoria.INSERTAR, "programa", id, nombre + " (" + tipo + ", " + duracion + " min)", "insPro");
        return id;
    }

    /** modPro["programa (nombre)","nombre","descripcion","tipo","duracion_min","genero","clasificacion"]. Devuelve el id. */
    public int modificar(Usuario quien, List<String> p) throws NegocioException, SQLException {
        int id = resolver(p.get(0));
        Validador v = new Validador();
        String nombre = v.texto(p.get(1), "nombre", 120);
        String descripcion = v.textoOpcional(p.get(2), "descripcion", 255);
        String tipo = v.opcion(p.get(3), "tipo", TIPOS);
        Integer duracion = v.entero(p.get(4), "duracion_min", 1, 600);
        String genero = v.textoOpcional(p.get(5), "genero", 60);
        String clasificacion = v.textoOpcional(p.get(6), "clasificacion", 10);
        v.verificar();

        validarNombreUnico(v, nombre, id);
        v.verificar();

        String[] actual = datos.obtener(id);
        datos.modificar(id, nombre, descripcion, tipo, duracion, genero, clasificacion);
        auditoria.registrar(quien, NAuditoria.MODIFICAR, "programa", id,
                "Antes: " + actual[1] + " (" + actual[3] + ", " + actual[4] + " min)"
                        + " | Ahora: " + nombre + " (" + tipo + ", " + duracion + " min)", "modPro");
        return id;
    }

    /** eliPro["programa (nombre)"]. Devuelve el id. */
    public int eliminar(Usuario quien, String referencia) throws NegocioException, SQLException {
        int id = resolver(referencia);
        String[] actual = datos.obtener(id);
        if (!datos.estaActivo(id)) {
            throw new NegocioException("El programa <b>" + Validador.esc(actual[1]) + "</b> ya está dado de baja.");
        }
        int bloques = datos.contarBloquesDesdeHoy(id);
        if (bloques > 0) {
            throw new NegocioException("No se puede dar de baja el programa <b>" + Validador.esc(actual[1]) + "</b>: tiene "
                    + bloques + " bloque(s) en parrillas de hoy en adelante. Quítelos primero de la parrilla.");
        }
        datos.darDeBaja(id);
        auditoria.registrar(quien, NAuditoria.ELIMINAR, "programa", id, "Baja de " + actual[1], "eliPro");
        return id;
    }

    /** Convierte la referencia (nombre o ID) en el id del programa. */
    private int resolver(String referencia) throws NegocioException, SQLException {
        Validador v = new Validador();
        String ref = v.texto(referencia, "programa", 120);
        v.verificar();
        Integer id = datos.buscarId(ref);
        if (id == null) {
            throw new NegocioException("No existe un programa con el nombre <b>" + Validador.esc(ref) + "</b>. "
                    + "Consulte los programas con lisPro[\"*\"].");
        }
        return id;
    }

    private void validarNombreUnico(Validador v, String nombre, int excluirId) throws SQLException {
        if (datos.existeNombre(nombre, excluirId)) {
            v.error("Ya existe otro programa con el nombre <b>" + Validador.esc(nombre) + "</b>.");
        }
    }
}
