package canal11.presentacion;

import canal11.datos.DAuditoria;
import canal11.negocio.NAuditoria;
import canal11.negocio.Usuario;

import java.util.ArrayList;
import java.util.List;

/**
 * CU7 - Capa de presentacion del historial de cambios (lisAud). Sin SQL ni reglas de negocio.
 */
public class PAuditoria {

    private final NAuditoria negocio = new NAuditoria();

    /** lisAud["*"] | lisAud["usuario"] | lisAud["2026-11-20"] */
    public Respuesta listar(Usuario u, List<String> p) throws Exception {
        List<String[]> filas = negocio.listar(p.get(0));
        List<String[]> tabla = new ArrayList<>();
        for (String[] f : filas) {
            tabla.add(new String[]{f[1], f[2], f[3], f[4] + (f[5].isEmpty() ? "" : " #" + f[5]), f[6], f[7]});
        }
        String filtro = p.get(0).trim().equals("*") ? "Todo el historial" : "Filtro: " + p.get(0).trim();
        String aviso = filas.size() >= DAuditoria.MAXIMO
                ? Html.info("Se muestran solo los " + DAuditoria.MAXIMO + " registros más recientes; use un filtro (entidad o fecha) para acotar.")
                : "";
        return new Respuesta("Historial de cambios", Html.parrafo(Html.esc(filtro) + ", del más reciente al más antiguo.") + aviso
                + Html.tabla(new String[]{"Fecha", "Usuario", "Acción", "Entidad", "Detalle", "Comando"}, tabla)
                + Html.total(filas.size())
                + Html.parrafo("<span style='font-size:12px;'>Filtros: " + Html.codigo("lisAud[\"usuario\"]") + " "
                + Html.codigo("lisAud[\"material\"]") + " " + Html.codigo("lisAud[\"" + java.time.LocalDate.now() + "\"]") + "</span>"));
    }
}
