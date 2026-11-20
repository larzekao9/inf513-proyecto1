package canal11.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Lee la configuracion desde config.properties (servidor del curso: correo y base de datos).
 * Si una clave no esta en el archivo se usa el valor por defecto definido aqui.
 */
public final class Config {

    private static final Properties PROPS = new Properties();

    static {
        // Servidor de correo del curso
        PROPS.setProperty("mail.usuario", "grupo12sa");
        PROPS.setProperty("mail.password", "");
        PROPS.setProperty("mail.direccion", "grupo12sa@tecnoweb.org.bo");
        PROPS.setProperty("pop3.host", "mail.tecnoweb.org.bo");
        PROPS.setProperty("pop3.puerto", "110");
        PROPS.setProperty("smtp.host", "mail.tecnoweb.org.bo");
        PROPS.setProperty("smtp.puerto", "25");
        // Base de datos del curso
        PROPS.setProperty("db.host", "db.tecnoweb.org.bo");
        PROPS.setProperty("db.puerto", "5432");
        PROPS.setProperty("db.nombre", "db_grupo12sa");
        PROPS.setProperty("db.usuario", "grupo12sa");
        PROPS.setProperty("db.password", "");
        PROPS.setProperty("db.esquema", "canal11");
        // Borrar los correos al leerlos (true). Con false se dejan en el buzon y se recuerdan los ya procesados
        PROPS.setProperty("pop3.borrar", "true");
        // Cada cuantos segundos se revisa el buzon
        PROPS.setProperty("app.intervalo", "10");

        // Plazo de revision de materiales PENDIENTES (minutos). 0 desactiva los avisos.
        PROPS.setProperty("revision.minutos_aviso", "1440");     // 24 h: recordatorio a Prensa
        PROPS.setProperty("revision.minutos_escalar", "2880");   // 48 h: escalamiento al administrador
        PROPS.setProperty("revision.intervalo", "60");           // cada cuantos segundos se revisa el plazo

        // Horario en que se envian los recordatorios de revision (las alertas de parrilla y emision van las 24 h)
        PROPS.setProperty("revision.hora_desde", "07:00");
        PROPS.setProperty("revision.hora_hasta", "21:00");
        // Materiales RECHAZADOS que nadie corrigio: aviso a quien los registro (minutos, 0 = desactivado)
        PROPS.setProperty("revision.correccion_minutos", "2880");
        // Parrilla en riesgo: aviso al Administrador (Programacion) X horas antes del dia (0 = desactivado) y urgente Y horas antes
        PROPS.setProperty("alerta.parrilla_horas", "24");
        PROPS.setProperty("alerta.parrilla_urgente_horas", "6");
        // Emision sin registrar: minutos despues de que termina el bloque (0 = desactivado) y hasta cuantas horas atras se mira
        PROPS.setProperty("alerta.emision_minutos", "30");
        PROPS.setProperty("alerta.emision_ventana_horas", "48");
        // Avisos por correo al publicar la parrilla, cambiarla, validar materiales, etc. (true/false)
        PROPS.setProperty("notificaciones.activas", "true");
        // Zona horaria del canal y, solo para pruebas o demostraciones, una fecha y hora fija (ej. 2031-01-01T10:00)
        PROPS.setProperty("app.zona", "America/La_Paz");
        PROPS.setProperty("app.ahora", "");

        // Por defecto config.properties (se puede indicar otro archivo con -Dcanal11.config)
        String archivo = System.getProperty("canal11.config", "config.properties");
        try (InputStreamReader in = new InputStreamReader(new FileInputStream(archivo), StandardCharsets.UTF_8)) {
            PROPS.load(in);
            System.out.println("[Config] Usando " + archivo + " (base " + PROPS.getProperty("db.host") + "/" + PROPS.getProperty("db.nombre") + ")");
        } catch (IOException e) {
            System.out.println("[Config] No se encontro " + archivo + "; se usan valores por defecto.");
        }
    }

    private Config() {
    }

    public static String get(String clave) {
        String v = PROPS.getProperty(clave);
        return v == null ? "" : v.trim();
    }

    /** Cambia un valor en memoria (lo usa --lote con la directiva @ahora para simular el paso del tiempo). */
    public static void fijar(String clave, String valor) {
        PROPS.setProperty(clave, valor);
    }

    public static int getInt(String clave) {
        return Integer.parseInt(get(clave));
    }
}
