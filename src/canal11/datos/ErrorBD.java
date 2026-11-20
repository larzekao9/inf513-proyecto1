package canal11.datos;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Traduce los errores de PostgreSQL (SQLState) a mensajes claros en espanol.
 * Es la ultima red de seguridad: las clases N* deberian detectar estos casos antes,
 * pero si algo llega a la BD (ej. dos correos al mismo tiempo) el usuario igual recibe un mensaje entendible.
 */
public final class ErrorBD {

    private ErrorBD() {
    }

    public static String traducir(SQLException e) {
        String estado = e.getSQLState() == null ? "" : e.getSQLState();
        String msg = e.getMessage() == null ? "" : e.getMessage();
        switch (estado) {
            case "23505": // unique_violation
                if (msg.contains("ux_usuario_email_lower")) {
                    return "Ya existe un usuario con ese correo (sin importar mayúsculas o minúsculas).";
                }
                Matcher u = Pattern.compile("\\(([^)]+)\\)=\\(([^)]+)\\)").matcher(msg);
                return u.find() ? "Ya existe un registro con " + u.group(1) + " = '" + u.group(2) + "'."
                        : "Ya existe un registro con esos datos.";
            case "23503": // foreign_key_violation
                return msg.contains("still referenced") || msg.contains("todavía es referida")
                        ? "No se puede eliminar porque otros registros dependen de él."
                        : "Uno de los datos hace referencia a un registro que no existe.";
            case "23502": // not_null_violation
                Matcher n = Pattern.compile("column \"([^\"]+)\"").matcher(msg);
                return n.find() ? "El campo '" + n.group(1) + "' no puede estar vacío." : "Falta un campo obligatorio.";
            case "23514": // check_violation
                if (msg.contains("ck_bloque_tipo")) {
                    return "Un bloque PROGRAMA debe tener programa; una PAUSA_COMERCIAL no puede tener programa ni material.";
                }
                return "Uno de los valores no está permitido. Revise los valores válidos con help.";
            case "23P01": // exclusion_violation (ex_bloque_sin_solape)
                return "El horario se solapa con otro bloque de la parrilla.";
            case "P0001": // RAISE EXCEPTION de fn_validar_spot: el mensaje ya viene en espanol
                ServerErrorMessage srv = e instanceof PSQLException ? ((PSQLException) e).getServerErrorMessage() : null;
                return srv != null && srv.getMessage() != null ? srv.getMessage() : msg;
            case "22001":
                return "Uno de los textos es demasiado largo.";
            case "22P02": case "22007": case "22008":
                return "Uno de los datos tiene un formato incorrecto (números, fechas AAAA-MM-DD u horas HH:MM).";
            case "08001": case "08003": case "08006": case "08000":
                return "No hay conexión con la base de datos. Intente nuevamente en unos minutos.";
            default:
                return estado.startsWith("08") || msg.contains("conexión") || msg.contains("connection")
                        ? "No hay conexión con la base de datos. Intente nuevamente en unos minutos."
                        : "No se pudo completar la operación en la base de datos.";
        }
    }
}
