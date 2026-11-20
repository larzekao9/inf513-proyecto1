package canal11.negocio;

import canal11.datos.DEscaleta;
import canal11.datos.DMaterial;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * CU5 - Validar Estado de Emision. Reglas de negocio:
 *  - valMat: el estado es APROBADO o RECHAZADO. Al rechazar la observacion es obligatoria.
 *  - Solo se valida un material activo que este PENDIENTE (o RECHAZADO, para aprobarlo tras corregirlo).
 *    Un material APROBADO no se vuelve a validar: si cambia su ruta o duracion (modMat) vuelve a PENDIENTE.
 *  - Quien registro el material no puede validarlo: lo valida otra persona (el jefe de Prensa), asi la aprobacion es un control real.
 *  - Se guardan validado_por (quien envia el comando) y fecha_validacion.
 *  - estPar: por cada bloque de la parrilla del dia indica si esta LISTO (material APROBADO), PENDIENTE,
 *    RECHAZADO, SIN MATERIAL o con MATERIAL DE BAJA. Las pausas comerciales no llevan material propio
 *    (NO APLICA) y no cuentan en el resumen.
 *  - Cada validacion se registra en la auditoria.
 */
public class NValidacion {

    public static final String LISTO = "LISTO";
    public static final String SIN_MATERIAL = "SIN MATERIAL";
    public static final String MATERIAL_DE_BAJA = "MATERIAL DE BAJA";
    public static final String NO_APLICA = "NO APLICA";

    private static final String[] RESULTADOS = {"APROBADO", "RECHAZADO"};

    private final DMaterial materiales = new DMaterial();
    private final DEscaleta escaletas = new DEscaleta();
    private final NParrilla parrillas = new NParrilla();
    private final NAuditoria auditoria = new NAuditoria();

    /** valMat["material (codigo)","estado","observacion"]. Devuelve el id del material. */
    public int validar(Usuario quien, List<String> p) throws NegocioException, SQLException {
        Validador v = new Validador();
        String codigo = v.texto(p.get(0), "material", 20);
        String estado = v.opcion(p.get(1), "estado", RESULTADOS);
        String observacion = v.textoOpcional(p.get(2), "observacion", 255);
        if ("RECHAZADO".equals(estado) && observacion == null) {
            v.error("Al <b>RECHAZAR</b> un material la <b>observacion</b> es obligatoria: indique el motivo.");
        }
        v.verificar();

        Integer id = materiales.buscarId(codigo);
        if (id == null) {
            throw new NegocioException("No existe un material con el código <b>" + Validador.esc(codigo) + "</b>. "
                    + "Consulte los materiales con lisMat[\"PENDIENTE\"].");
        }
        String[] actual = materiales.obtener(id);
        if (!materiales.estaActivo(id)) {
            throw new NegocioException("El material <b>" + Validador.esc(actual[1]) + "</b> está dado de baja y no se puede validar.");
        }
        if (materiales.registradoPor(id) == quien.getId()) {
            throw new NegocioException("No puede validar el material <b>" + Validador.esc(actual[1]) + "</b> porque usted mismo lo registró: "
                    + "debe aprobarlo o rechazarlo otra persona (el jefe de Prensa).");
        }
        String anterior = actual[8];
        if ("APROBADO".equals(anterior)) {
            throw new NegocioException("El material <b>" + Validador.esc(actual[1]) + "</b> ya está <b>APROBADO</b>. "
                    + "Solo vuelve a validarse si cambia su ruta o duración con modMat.");
        }
        if ("RECHAZADO".equals(anterior) && "RECHAZADO".equals(estado)) {
            throw new NegocioException("El material <b>" + Validador.esc(actual[1]) + "</b> ya está <b>RECHAZADO</b>. "
                    + "Cuando se corrija (modMat) puede aprobarlo con valMat.");
        }

        materiales.validar(id, estado, observacion, quien.getId());
        auditoria.registrar(quien, NAuditoria.VALIDAR, "material", id,
                actual[1] + ": " + anterior + " -> " + estado + (observacion != null ? " (" + observacion + ")" : ""), "valMat");
        return id;
    }

    /**
     * estPar["fecha"]. Un arreglo por bloque, ordenado por hora:
     * 0 hora inicio, 1 hora fin, 2 tipo, 3 programa, 4 codigo del material, 5 estado de emision
     * (LISTO, PENDIENTE, RECHAZADO, SIN MATERIAL, MATERIAL DE BAJA o NO APLICA), 6 observacion del material.
     */
    public List<String[]> estadoDeParrilla(String fecha) throws NegocioException, SQLException {
        List<String[]> resultado = new ArrayList<>();
        for (String[] b : parrillas.bloques(fecha)) {
            String estado;
            String codigo = b[5];
            String observacion = b[8];
            if (!"PROGRAMA".equals(b[3])) {
                estado = NO_APLICA;
            } else if (Integer.parseInt(b[9]) > 0) { // bloque con escaleta: manda el estado de sus notas
                List<String[]> notas = escaletas.listar(Integer.parseInt(b[0]));
                codigo = "ESCALETA (" + notas.size() + " nota" + (notas.size() == 1 ? "" : "s") + ")";
                estado = LISTO;
                List<String> pendientes = new ArrayList<>();
                for (String[] n : notas) {
                    String e = "No".equals(n[5]) ? MATERIAL_DE_BAJA : ("APROBADO".equals(n[4]) ? LISTO : n[4]);
                    if (!LISTO.equals(e)) {
                        pendientes.add(n[1] + " (" + e + ")");
                        if (LISTO.equals(estado) || "PENDIENTE".equals(estado)) {
                            estado = e;
                        }
                    }
                }
                observacion = String.join(", ", pendientes);
            } else if (b[5].isEmpty()) {
                estado = SIN_MATERIAL;
            } else if ("No".equals(b[7])) {
                estado = MATERIAL_DE_BAJA;
            } else {
                estado = "APROBADO".equals(b[6]) ? LISTO : b[6];
            }
            resultado.add(new String[]{b[1], b[2], b[3], b[4], codigo, estado, observacion});
        }
        return resultado;
    }
}
