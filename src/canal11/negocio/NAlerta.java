package canal11.negocio;

import canal11.config.Config;
import canal11.datos.DAlerta;
import canal11.datos.DParrilla;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Alertas automaticas. Reglas de negocio (los plazos estan en config.properties, 0 = desactivada):
 *  - PARRILLA EN RIESGO: la parrilla de un dia que, alerta.parrilla_horas antes de empezar el dia, no existe, sigue en BORRADOR,
 *    no tiene bloques o tiene bloques de programa con material PENDIENTE, RECHAZADO, de baja o sin material. Avisa a Programacion
 *    (y a Prensa si hay materiales pendientes de validar). A alerta.parrilla_urgente_horas antes vuelve a avisar, ahora tambien al administrador.
 *  - EMISION SIN REGISTRAR: un bloque de una parrilla PUBLICADA que termino hace mas de alerta.emision_minutos y nadie registro con insEmi.
 *    Avisa a Produccion. Solo mira los ultimos alerta.emision_ventana_horas para no avisar de datos viejos.
 *  - MATERIAL RECHAZADO SIN CORREGIR: avisa a quien lo registro pasados revision.correccion_minutos.
 *  Cada alerta se envia una sola vez (aviso_enviado); si el envio falla se reintenta en la siguiente vuelta.
 */
public class NAlerta {

    public static final String PARRILLA_1 = "PARRILLA_RIESGO_1";
    public static final String PARRILLA_2 = "PARRILLA_RIESGO_2";
    public static final String EMISION = "EMISION_SIN_REGISTRO";
    public static final String RECHAZO = "RECHAZO_SIN_CORREGIR";

    /** Resultado de evaluar una parrilla: que le falta y si hay materiales esperando validacion de Prensa. */
    public static final class Riesgo {
        public final LocalDate fecha;
        public final List<String> problemas = new ArrayList<>();
        public boolean conPendientes;

        Riesgo(LocalDate fecha) {
            this.fecha = fecha;
        }
    }

    private final DParrilla parrillas = new DParrilla();
    private final DAlerta datos = new DAlerta();
    private final NValidacion validacion = new NValidacion();

    public static int horasParrilla() {
        return Config.getInt("alerta.parrilla_horas");
    }

    public static int horasUrgente() {
        return Config.getInt("alerta.parrilla_urgente_horas");
    }

    public static int minutosEmision() {
        return Config.getInt("alerta.emision_minutos");
    }

    public static int minutosCorreccion() {
        return Config.getInt("revision.correccion_minutos");
    }

    /**
     * Nivel de aviso que toca para la parrilla de esa fecha segun la hora: 0 = aun no, 1 = aviso, 2 = urgente.
     * Despues de que termina el dia ya no se avisa.
     */
    public int nivelParrilla(LocalDate fecha) {
        LocalDateTime ahora = Reloj.ahora();
        LocalDateTime inicio = fecha.atStartOfDay();
        if (!ahora.isBefore(fecha.plusDays(1).atStartOfDay()) || horasParrilla() <= 0) {
            return 0;
        }
        if (horasUrgente() > 0 && !ahora.isBefore(inicio.minusHours(horasUrgente()))) {
            return 2;
        }
        return !ahora.isBefore(inicio.minusHours(horasParrilla())) ? 1 : 0;
    }

    /** Fechas que hay que revisar ahora: desde hoy hasta donde llega la anticipacion configurada. */
    public List<LocalDate> fechasARevisar() {
        List<LocalDate> fechas = new ArrayList<>();
        LocalDate hoy = Reloj.hoy();
        for (int i = 0; i <= horasParrilla() / 24 + 1; i++) {
            fechas.add(hoy.plusDays(i));
        }
        return fechas;
    }

    /** Evalua la parrilla de la fecha. Sin problemas = lista vacia. */
    public Riesgo evaluar(LocalDate fecha) throws SQLException, NegocioException {
        Riesgo r = new Riesgo(fecha);
        Integer id = parrillas.buscarId(fecha);
        if (id == null) {
            r.problemas.add("Todavía no existe la parrilla (créela con insPar[\"" + fecha + "\"]).");
            return r;
        }
        if (!"PUBLICADA".equals(parrillas.estado(id))) {
            r.problemas.add("Sigue en BORRADOR: falta publicarla con pubPar[\"" + fecha + "\"].");
        }
        List<String[]> bloques = validacion.estadoDeParrilla(fecha.toString());
        if (bloques.isEmpty()) {
            r.problemas.add("No tiene bloques.");
        }
        for (String[] b : bloques) {
            String estado = b[5];
            if (NValidacion.LISTO.equals(estado) || NValidacion.NO_APLICA.equals(estado)) {
                continue;
            }
            String horario = b[0].substring(0, 5) + "-" + b[1].substring(0, 5);
            r.problemas.add("Bloque " + horario + " (" + b[3] + "): " + (b[4].isEmpty() ? "" : b[4] + " ") + estado
                    + (b[6].isEmpty() ? "" : " - " + b[6]));
            r.conPendientes |= "PENDIENTE".equals(estado);
        }
        return r;
    }

    /** Bloques terminados hace mas de alerta.emision_minutos (y dentro de la ventana) sin emision registrada. */
    public List<String[]> emisionesSinRegistrar() throws SQLException {
        if (minutosEmision() <= 0) {
            return List.of();
        }
        LocalDateTime ahora = Reloj.ahora();
        Timestamp limite = Timestamp.valueOf(ahora.minusMinutes(minutosEmision()));
        Timestamp desde = Timestamp.valueOf(ahora.minusHours(Config.getInt("alerta.emision_ventana_horas")));
        return datos.bloquesSinEmision(desde, limite);
    }

    /** Materiales rechazados que nadie corrigio (columnas de DAlerta.rechazadosSinCorregir). */
    public List<String[]> rechazadosSinCorregir() throws SQLException {
        return minutosCorreccion() <= 0 ? List.of() : datos.rechazadosSinCorregir(minutosCorreccion());
    }
}
