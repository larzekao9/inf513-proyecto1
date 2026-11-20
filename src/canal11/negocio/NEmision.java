package canal11.negocio;

import canal11.datos.DBloque;
import canal11.datos.DEmision;
import canal11.datos.DParrilla;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * CU7 - Conciliar Emision Real. Reglas de negocio:
 *  - insEmi registra, una sola vez por bloque (fecha + hora de inicio), lo que realmente salio al aire.
 *    La parrilla debe estar PUBLICADA (solo se concilia lo que se publico).
 *  - Estado: EMITIDO, PARCIAL o NO_EMITIDO. Para EMITIDO y PARCIAL, inicio_real y fin_real son horas HH:MM:SS
 *    del mismo dia y fin_real > inicio_real. Para NO_EMITIDO se envian "" (si se envian, se ignoran).
 *  - Solo se registra lo que ya salio al aire: el bloque debe haber empezado (fecha y hora de inicio no futuras).
 *  - registrado_por = quien envia el comando.
 *  - Al registrar, los spots PROGRAMADOS del bloque se actualizan: EMITIDO -> EMITIDO; NO_EMITIDO -> NO_EMITIDO;
 *    PARCIAL -> EMITIDO los que caen entre inicio_real y fin_real y NO_EMITIDO los demas.
 *  - conPar compara por bloque lo programado con lo emitido: hora programada, hora real, desviacion del inicio
 *    en segundos (real - programado) y estado (EMITIDO / PARCIAL / NO_EMITIDO / SIN REGISTRO).
 *    Cumplimiento del dia = bloques EMITIDO / bloques programados (un bloque PARCIAL no cuenta como cumplido).
 *  - Cada registro de emision queda en la auditoria.
 */
public class NEmision {

    private static final String[] ESTADOS = {"EMITIDO", "PARCIAL", "NO_EMITIDO"};

    private final DEmision emisiones = new DEmision();
    private final DParrilla parrillas = new DParrilla();
    private final DBloque bloques = new DBloque();
    private final NAuditoria auditoria = new NAuditoria();

    /** insEmi["fecha","hora_inicio","inicio_real","fin_real","estado"]. Devuelve el id de la emision. */
    public int insertar(Usuario quien, List<String> p) throws NegocioException, SQLException {
        Validador v = new Validador();
        LocalDate fecha = v.fecha(p.get(0), "fecha");
        LocalTime horaInicio = v.hora(p.get(1), "hora_inicio");
        String estado = v.opcion(p.get(4), "estado", ESTADOS);
        LocalTime inicioReal = null;
        LocalTime finReal = null;
        if (!"NO_EMITIDO".equals(estado)) {
            inicioReal = v.hora(p.get(2), "inicio_real");
            finReal = v.hora(p.get(3), "fin_real");
            if (inicioReal != null && finReal != null && !finReal.isAfter(inicioReal)) {
                v.error("El <b>fin_real</b> (" + finReal + ") debe ser posterior al <b>inicio_real</b> (" + inicioReal
                        + "); ambas horas son del mismo día.");
            }
        }
        v.verificar();

        if (fecha.isAfter(Reloj.hoy()) || (fecha.equals(Reloj.hoy()) && horaInicio.isAfter(Reloj.ahora().toLocalTime()))) {
            throw new NegocioException("El bloque de las <b>" + horaInicio + "</b> del <b>" + fecha + "</b> todavía no empieza (ahora son las "
                    + Reloj.ahora().toLocalTime().withNano(0) + " del " + Reloj.hoy() + "): solo se registra lo que ya salió al aire.");
        }
        Integer idParrilla = parrillas.buscarId(fecha);
        if (idParrilla == null) {
            throw new NegocioException("No existe una parrilla para el <b>" + fecha + "</b>. Consulte las parrillas con lisPar[\"*\"].");
        }
        if (!"PUBLICADA".equals(parrillas.estado(idParrilla))) {
            throw new NegocioException("La parrilla del <b>" + fecha + "</b> sigue en BORRADOR: solo se registra la emisión de "
                    + "parrillas publicadas (pubPar[\"" + fecha + "\"]).");
        }
        Integer idBloque = bloques.buscarId(idParrilla, horaInicio);
        if (idBloque == null) {
            throw new NegocioException("No hay un bloque que empiece a las <b>" + horaInicio + "</b> en la parrilla del " + fecha
                    + ". Vea los bloques con mosPar[\"" + fecha + "\"].");
        }
        if (emisiones.existeParaBloque(idBloque)) {
            throw new NegocioException("El bloque de las <b>" + horaInicio + "</b> del " + fecha
                    + " ya tiene su emisión registrada. Revísela con conPar[\"" + fecha + "\"].");
        }

        // NO_EMITIDO: la tabla exige un inicio_real, se guarda la hora programada y fin_real queda vacio.
        Timestamp inicio = Timestamp.valueOf(LocalDateTime.of(fecha, inicioReal != null ? inicioReal : horaInicio));
        Timestamp fin = finReal == null ? null : Timestamp.valueOf(LocalDateTime.of(fecha, finReal));

        int id = emisiones.registrar(idBloque, inicio, fin, estado, quien.getId());
        auditoria.registrar(quien, NAuditoria.INSERTAR, "emision", id,
                fecha + " " + horaInicio + " " + estado + (inicioReal != null ? " real " + inicioReal + "-" + finReal : ""), "insEmi");
        return id;
    }

    /**
     * conPar["fecha"]. Un arreglo por bloque, ordenado por hora (ver DEmision.CONCILIACION para las columnas).
     */
    public List<String[]> conciliar(String fechaTxt) throws NegocioException, SQLException {
        Validador v = new Validador();
        LocalDate fecha = v.fecha(fechaTxt, "fecha");
        v.verificar();
        Integer idParrilla = parrillas.buscarId(fecha);
        if (idParrilla == null) {
            throw new NegocioException("No existe una parrilla para el <b>" + fecha + "</b>. Consulte las parrillas con lisPar[\"*\"].");
        }
        return emisiones.conciliar(idParrilla);
    }

    /** Cumplimiento (0 a 100, un decimal) = bloques EMITIDO / bloques programados. 0 si no hay bloques. */
    public static double cumplimiento(List<String[]> conciliacion) {
        if (conciliacion.isEmpty()) {
            return 0;
        }
        long emitidos = conciliacion.stream().filter(b -> "EMITIDO".equals(b[5])).count();
        return Math.round(emitidos * 1000.0 / conciliacion.size()) / 10.0;
    }
}
