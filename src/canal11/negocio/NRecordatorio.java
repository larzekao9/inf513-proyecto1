package canal11.negocio;

import canal11.config.Config;
import canal11.datos.DMaterial;
import canal11.datos.DUsuario;

import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Plazo de revision de materiales. Reglas de negocio:
 *  - Un material PENDIENTE y activo cuenta el tiempo desde pendiente_desde (su registro, o el ultimo modMat que lo devolvio a PENDIENTE).
 *  - Pasado revision.minutos_aviso se envia un RECORDATORIO (nivel 1) a los usuarios ENCARGADO (Prensa).
 *  - Pasado revision.minutos_escalar se ESCALA (nivel 2) a los ADMINISTRADOR. Despues de eso no se avisa mas.
 *  - El sistema nunca aprueba ni rechaza por tiempo: solo avisa. La decision sigue siendo de Prensa (valMat).
 *  - Cada aviso queda en la auditoria como accion del sistema.
 *  - revision.minutos_aviso = 0 desactiva los avisos.
 *  - Los avisos solo se envian dentro del horario revision.hora_desde - revision.hora_hasta (hora del canal): no hay correos de madrugada;
 *    si el plazo vence fuera de horario, el aviso sale al abrir el horario.
 */
public class NRecordatorio {

    public static final int RECORDATORIO = 1;
    public static final int ESCALAMIENTO = 2;

    private final DMaterial materiales = new DMaterial();
    private final DUsuario usuarios = new DUsuario();
    private final NAuditoria auditoria = new NAuditoria();

    public boolean habilitado() {
        return minutosAviso() > 0;
    }

    /** true si ahora (hora del canal) esta dentro del horario en que se envian los recordatorios. */
    public static boolean dentroDeHorario() {
        try {
            LocalTime desde = LocalTime.parse(Config.get("revision.hora_desde"));
            LocalTime hasta = LocalTime.parse(Config.get("revision.hora_hasta"));
            LocalTime ahora = Reloj.ahora().toLocalTime();
            return !ahora.isBefore(desde) && ahora.isBefore(hasta);
        } catch (DateTimeParseException e) {
            return true; // horario mal escrito: mejor avisar que quedarse callado
        }
    }

    public static int minutosAviso() {
        return Config.getInt("revision.minutos_aviso");
    }

    public static int minutosEscalar() {
        // el escalamiento nunca es antes que el recordatorio
        return Math.max(Config.getInt("revision.minutos_escalar"), minutosAviso() + 1);
    }

    /** Materiales que ya deben recibir el aviso del nivel (columnas de DMaterial.pendientesDeAviso). */
    public List<String[]> vencidos(int nivel) throws SQLException {
        return materiales.pendientesDeAviso(nivel, minutosAviso(), minutosEscalar());
    }

    /** Correos a los que va el aviso: Prensa en el recordatorio, el administrador en el escalamiento. */
    public List<String> destinatarios(int nivel) throws SQLException {
        return usuarios.emailsActivosPorRol(nivel == ESCALAMIENTO ? Rol.ADMINISTRADOR.name() : Rol.ENCARGADO.name());
    }

    /** Deja constancia de que el aviso se envio: marca el material y lo registra en la auditoria. */
    public void confirmar(int nivel, List<String[]> enviados, List<String> destinatarios) throws SQLException {
        for (String[] m : enviados) {
            int id = Integer.parseInt(m[0]);
            if (materiales.marcarAviso(id, nivel) > 0) {
                auditoria.registrarSistema(nivel == ESCALAMIENTO ? NAuditoria.ESCALAR : NAuditoria.RECORDAR, "material", id,
                        m[1] + " sin revisar hace " + m[6] + " min; aviso a " + String.join(", ", destinatarios),
                        nivel == ESCALAMIENTO ? "escalamiento" : "recordatorio");
            }
        }
    }
}
