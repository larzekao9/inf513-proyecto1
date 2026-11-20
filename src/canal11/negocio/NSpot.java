package canal11.negocio;

import canal11.datos.DBloque;
import canal11.datos.DMaterial;
import canal11.datos.DPauta;
import canal11.datos.DParrilla;
import canal11.datos.DSpot;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * CU6 - Insertar Spots en las pausas comerciales. Reglas de negocio de insSpo:
 *  - La pauta debe estar activa y la fecha dentro de su vigencia (fecha_inicio a fecha_fin).
 *  - El material debe ser de tipo SPOT, estar APROBADO y activo.
 *  - Debe existir la parrilla de esa fecha (BORRADOR o PUBLICADA) y un bloque que empiece a hora_pausa,
 *    y ese bloque debe ser PAUSA_COMERCIAL.
 *  - hora_spot debe caer dentro de la pausa y el spot debe terminar antes de que acabe la pausa.
 *  - No se repite la hora en el mismo bloque; la suma de duraciones de los spots no excede la pausa;
 *    no se supera max_pases de la pauta (los spots NO_EMITIDO no cuentan).
 * eliSpo: se identifica por fecha + hora del spot y solo se quita si sigue PROGRAMADO
 * (un spot EMITIDO o NO_EMITIDO conserva el historial).
 * Cada alta y baja se registra en la auditoria.
 */
public class NSpot {

    private final DSpot spots = new DSpot();
    private final DPauta pautas = new DPauta();
    private final DMaterial materiales = new DMaterial();
    private final DParrilla parrillas = new DParrilla();
    private final DBloque bloques = new DBloque();
    private final NAuditoria auditoria = new NAuditoria();

    /** insSpo["pauta (nombre)","material (codigo)","fecha","hora_pausa","hora_spot"]. Devuelve el id creado. */
    public int insertar(Usuario quien, List<String> p) throws NegocioException, SQLException {
        Validador v = new Validador();
        String nombrePauta = v.texto(p.get(0), "pauta", 150);
        String codigo = v.texto(p.get(1), "material", 20);
        LocalDate fecha = v.fecha(p.get(2), "fecha");
        LocalTime horaPausa = v.hora(p.get(3), "hora_pausa");
        LocalTime horaSpot = v.hora(p.get(4), "hora_spot");
        v.verificar();

        // --- pauta y material ---
        Integer idPauta = pautas.buscarId(nombrePauta);
        String[] pauta = null;
        if (idPauta == null) {
            v.error("No existe la pauta <b>" + Validador.esc(nombrePauta) + "</b>. Consulte las pautas con lisPau[\"*\"].");
        } else {
            pauta = pautas.obtener(idPauta);
            if ("No".equals(pauta[6])) {
                v.error("La pauta <b>" + Validador.esc(pauta[1]) + "</b> está dada de baja.");
            }
            LocalDate desde = LocalDate.parse(pauta[3]);
            LocalDate hasta = LocalDate.parse(pauta[4]);
            if (fecha.isBefore(desde) || fecha.isAfter(hasta)) {
                v.error("La fecha <b>" + fecha + "</b> está fuera de la vigencia de la pauta (" + desde + " a " + hasta + ").");
            }
        }
        Integer idMaterial = materiales.buscarId(codigo);
        String[] material = null;
        if (idMaterial == null) {
            v.error("No existe el material <b>" + Validador.esc(codigo) + "</b>. Consulte los spots con lisMat[\"SPOT\"].");
        } else {
            material = materiales.obtener(idMaterial);
            if (!"SPOT".equals(material[3])) {
                v.error("El material <b>" + Validador.esc(material[1]) + "</b> es de tipo " + material[3] + ": solo se insertan materiales de tipo SPOT.");
            }
            if ("No".equals(material[10])) {
                v.error("El material <b>" + Validador.esc(material[1]) + "</b> está dado de baja.");
            } else if (!"APROBADO".equals(material[8])) {
                v.error("El material <b>" + Validador.esc(material[1]) + "</b> está <b>" + material[8] + "</b>: debe estar APROBADO (valMat).");
            }
        }
        v.verificar();

        // --- parrilla y pausa comercial ---
        Integer idParrilla = parrillas.buscarId(fecha);
        if (idParrilla == null) {
            throw new NegocioException("No existe una parrilla para el <b>" + fecha + "</b>. Créela con insPar[\"" + fecha + "\"].");
        }
        Integer idBloque = bloques.buscarId(idParrilla, horaPausa);
        if (idBloque == null) {
            throw new NegocioException("No hay un bloque que empiece a las <b>" + horaPausa + "</b> en la parrilla del " + fecha
                    + ". Vea los bloques con mosPar[\"" + fecha + "\"].");
        }
        String[] bloque = bloques.obtener(idBloque);
        if (!"PAUSA_COMERCIAL".equals(bloque[3])) {
            throw new NegocioException("El bloque de las " + horaPausa + " es de tipo <b>" + bloque[3]
                    + "</b>: los spots solo se insertan en una PAUSA_COMERCIAL.");
        }

        // --- reglas de la pausa ---
        LocalTime inicioPausa = LocalTime.parse(bloque[1]);
        LocalTime finPausa = LocalTime.parse(bloque[2]);
        int duracion = Integer.parseInt(material[5]);
        if (horaSpot.isBefore(inicioPausa) || !horaSpot.isBefore(finPausa)) {
            v.error("La <b>hora_spot</b> (" + horaSpot + ") debe estar dentro de la pausa " + inicioPausa + "-" + finPausa + ".");
        } else if (horaSpot.plusSeconds(duracion).isAfter(finPausa) || horaSpot.plusSeconds(duracion).isBefore(horaSpot)) {
            v.error("El spot dura " + duracion + " s y a las " + horaSpot + " terminaría después del fin de la pausa (" + finPausa + ").");
        }
        if (spots.existeHoraEnBloque(idBloque, horaSpot)) {
            v.error("Ya hay un spot a las <b>" + horaSpot + "</b> en esa pausa.");
        }
        long capacidad = Duration.between(inicioPausa, finPausa).getSeconds();
        int ocupados = spots.segundosOcupados(idBloque);
        if (ocupados + duracion > capacidad) {
            v.error("La pausa dura " + capacidad + " s y ya tiene " + ocupados + " s de spots: no cabe un spot de " + duracion + " s.");
        }
        int maxPases = Integer.parseInt(pauta[5]);
        int usados = spots.contarPases(idPauta);
        if (usados >= maxPases) {
            v.error("La pauta <b>" + Validador.esc(pauta[1]) + "</b> ya usó sus " + maxPases + " pases máximos.");
        }
        v.verificar();

        int id = spots.insertar(idPauta, idMaterial, idBloque, horaSpot);
        auditoria.registrar(quien, NAuditoria.INSERTAR, "spot", id,
                pauta[1] + ": " + material[1] + " el " + fecha + " a las " + horaSpot + " (pausa " + inicioPausa + "-" + finPausa + ")",
                "insSpo");
        return id;
    }

    /** eliSpo["fecha","hora_spot"]. Devuelve la fila del spot quitado (ver DSpot para las columnas). */
    public String[] eliminar(Usuario quien, List<String> p) throws NegocioException, SQLException {
        Validador v = new Validador();
        LocalDate fecha = v.fecha(p.get(0), "fecha");
        LocalTime hora = v.hora(p.get(1), "hora_spot");
        v.verificar();

        Integer id = spots.buscarId(fecha, hora);
        if (id == null) {
            throw new NegocioException("No hay un spot programado el <b>" + fecha + "</b> a las <b>" + hora
                    + "</b>. Vea los spots de una pauta con mosPau[\"nombre de la pauta\"].");
        }
        String[] spot = spots.obtener(id);
        if (!"PROGRAMADO".equals(spot[7])) {
            throw new NegocioException("El spot de las " + hora + " está <b>" + spot[7]
                    + "</b>: solo se pueden quitar spots PROGRAMADOS (los demás conservan el historial).");
        }
        spots.eliminar(id);
        auditoria.registrar(quien, NAuditoria.ELIMINAR, "spot", id,
                spot[3] + ": " + spot[4] + " el " + fecha + " a las " + spot[2], "eliSpo");
        return spot;
    }
}
