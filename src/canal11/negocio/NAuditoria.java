package canal11.negocio;

import canal11.datos.DAuditoria;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * CU7 - Registro de auditoria (RF-29: quien modifico que y cuando).
 * Todas las clases N* deben llamar a registrar() despues de insertar, modificar o dar de baja algo.
 * Si la auditoria falla, NO se cancela la operacion del usuario: solo se informa en consola.
 */
public class NAuditoria {

    public static final String INSERTAR = "INSERTAR";
    public static final String MODIFICAR = "MODIFICAR";
    public static final String ELIMINAR = "ELIMINAR";
    public static final String REACTIVAR = "REACTIVAR";
    public static final String PUBLICAR = "PUBLICAR";
    public static final String VALIDAR = "VALIDAR";
    public static final String RECORDAR = "RECORDAR";
    public static final String ESCALAR = "ESCALAR";

    private final DAuditoria datos = new DAuditoria();

    private static final String[] ENTIDADES = {"usuario", "programa", "material", "parrilla", "bloque", "pauta", "spot", "emision", "escaleta"};

    /**
     * lisAud: historial de cambios (maximo DAuditoria.MAXIMO, mas recientes primero).
     * filtro: "*" = todo; una entidad (usuario, programa, material, parrilla, bloque, pauta, spot, emision, escaleta) o una fecha AAAA-MM-DD.
     */
    public List<String[]> listar(String filtro) throws NegocioException, SQLException {
        Validador v = new Validador();
        String f = v.texto(filtro, "filtro", 30);
        v.verificar();
        if (f.equals("*")) {
            return datos.listar(null, null);
        }
        if (f.matches("\\d{4}-\\d{2}-\\d{2}")) {
            LocalDate fecha = v.fecha(f, "filtro");
            v.verificar();
            return datos.listar(null, fecha);
        }
        for (String e : ENTIDADES) {
            if (e.equalsIgnoreCase(f)) {
                return datos.listar(e, null);
            }
        }
        throw new NegocioException("El filtro debe ser <b>*</b> (todo), una entidad (" + String.join(", ", ENTIDADES)
                + ") o una fecha AAAA-MM-DD (recibido: '" + Validador.esc(f) + "').");
    }

    /**
     * @param usuario   quien hizo el cambio (el remitente del correo)
     * @param accion    INSERTAR, MODIFICAR, ELIMINAR (o PUBLICAR, VALIDAR... en otros CU)
     * @param entidad   nombre de la tabla afectada (usuario, programa, parrilla...)
     * @param idEntidad id del registro afectado
     * @param detalle   texto breve con lo que cambio
     * @param comando   comando que lo origino, ej. insUsu
     */
    public void registrar(Usuario usuario, String accion, String entidad, Integer idEntidad, String detalle, String comando) {
        try {
            datos.insertar(usuario.getId(), accion, entidad, idEntidad, detalle, comando);
        } catch (Exception e) {
            System.err.println("[Auditoria] No se pudo registrar " + accion + " " + entidad + " " + idEntidad + ": " + e.getMessage());
        }
    }

    /** Accion automatica del sistema (sin usuario): en lisAud aparece como "(sistema)". */
    public void registrarSistema(String accion, String entidad, Integer idEntidad, String detalle, String comando) {
        try {
            datos.insertar(null, accion, entidad, idEntidad, detalle, comando);
        } catch (Exception e) {
            System.err.println("[Auditoria] No se pudo registrar " + accion + " " + entidad + " " + idEntidad + ": " + e.getMessage());
        }
    }
}
