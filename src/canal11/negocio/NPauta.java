package canal11.negocio;

import canal11.datos.DPauta;
import canal11.datos.DSpot;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * CU6 - Pauta Publicitaria. Reglas de negocio:
 *  - Alcance: solo TRAFICO (ubicar spots). Clientes, contratos y facturacion son del subsistema 13 (Comercial),
 *    por eso el anunciante es solo un texto.
 *  - Una pauta se referencia por su nombre (unico, sin distinguir mayusculas) o por su ID.
 *  - fecha_fin >= fecha_inicio; max_pases (cantidad maxima de spots) de 1 a 10000.
 *  - eliPau es baja logica y NO se permite si la pauta tiene spots PROGRAMADOS (primero se quitan con eliSpo).
 *  - Cada alta y baja se registra en la auditoria.
 */
public class NPauta {

    private final DPauta datos = new DPauta();
    private final DSpot spots = new DSpot();
    private final NAuditoria auditoria = new NAuditoria();

    /** filtro: "*" = todas; otro texto = busca por anunciante o nombre de la pauta. */
    public List<String[]> listar(String filtro) throws NegocioException, SQLException {
        Validador v = new Validador();
        String f = v.texto(filtro, "filtro", 150);
        v.verificar();
        return datos.listar(f.equals("*") ? null : f);
    }

    /** referencia: nombre o ID. Devuelve la fila completa (ver DPauta para las columnas). */
    public String[] mostrar(String referencia) throws NegocioException, SQLException {
        return datos.obtener(resolver(referencia));
    }

    /** Spots de la pauta ordenados por fecha y hora (ver DSpot para las columnas). */
    public List<String[]> spots(String referencia) throws NegocioException, SQLException {
        return spots.listarPorPauta(resolver(referencia));
    }

    /** insPau["nombre","anunciante","fecha_inicio","fecha_fin","max_pases"]. Devuelve el id creado. */
    public int insertar(Usuario quien, List<String> p) throws NegocioException, SQLException {
        Validador v = new Validador();
        String nombre = v.texto(p.get(0), "nombre", 150);
        String anunciante = v.texto(p.get(1), "anunciante", 150);
        LocalDate inicio = v.fecha(p.get(2), "fecha_inicio");
        LocalDate fin = v.fecha(p.get(3), "fecha_fin");
        Integer maxPases = v.entero(p.get(4), "max_pases", 1, 10000);
        if (inicio != null && fin != null && fin.isBefore(inicio)) {
            v.error("La <b>fecha_fin</b> (" + fin + ") no puede ser anterior a la <b>fecha_inicio</b> (" + inicio + ").");
        }
        v.verificar();

        if (datos.existeNombre(nombre)) {
            v.error("Ya existe una pauta con el nombre <b>" + Validador.esc(nombre) + "</b>.");
        }
        v.verificar();

        int id = datos.insertar(nombre, anunciante, inicio, fin, maxPases);
        auditoria.registrar(quien, NAuditoria.INSERTAR, "pauta", id,
                nombre + " (" + anunciante + ", " + inicio + " a " + fin + ", max " + maxPases + " pases)", "insPau");
        return id;
    }

    /** eliPau["pauta (nombre)"]. Devuelve el id. */
    public int eliminar(Usuario quien, String referencia) throws NegocioException, SQLException {
        int id = resolver(referencia);
        String[] actual = datos.obtener(id);
        if ("No".equals(actual[6])) {
            throw new NegocioException("La pauta <b>" + Validador.esc(actual[1]) + "</b> ya está dada de baja.");
        }
        int programados = spots.contarProgramados(id);
        if (programados > 0) {
            throw new NegocioException("No se puede dar de baja la pauta <b>" + Validador.esc(actual[1]) + "</b>: tiene " + programados
                    + " spot(s) programado(s). Quítelos primero con eliSpo.");
        }
        datos.darDeBaja(id);
        auditoria.registrar(quien, NAuditoria.ELIMINAR, "pauta", id, "Baja de " + actual[1] + " (" + actual[2] + ")", "eliPau");
        return id;
    }

    /** Convierte la referencia (nombre o ID) en el id de la pauta. */
    private int resolver(String referencia) throws NegocioException, SQLException {
        Validador v = new Validador();
        String ref = v.texto(referencia, "pauta", 150);
        v.verificar();
        Integer id = datos.buscarId(ref);
        if (id == null) {
            throw new NegocioException("No existe una pauta con el nombre <b>" + Validador.esc(ref) + "</b>. "
                    + "Consulte las pautas con lisPau[\"*\"].");
        }
        return id;
    }
}
