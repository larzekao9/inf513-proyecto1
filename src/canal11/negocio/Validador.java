package canal11.negocio;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Valida TODOS los parametros de un comando antes de tocar la base de datos y junta TODOS los errores,
 * para responder de una sola vez (el usuario no tiene que corregir de a uno).
 *
 * Uso en una clase N*:
 *   Validador v = new Validador();
 *   String ci     = v.ci(p.get(0));
 *   String nombre = v.nombre(p.get(1), "nombre");
 *   ...
 *   v.verificar();   // si hubo errores lanza NegocioException con la lista completa
 *
 * Todos los valores se limpian: se quitan espacios al inicio/fin y los espacios repetidos
 * ("  Ana    Maria " -> "Ana Maria"). Un parametro "" o con solo espacios cuenta como vacio.
 */
public class Validador {

    private static final Pattern LETRAS = Pattern.compile("^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ' ]+$");
    /** Email de cualquier proveedor. IMPORTANTE: mantener igual que MimeUtil.EMAIL. */
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
            + "@(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\\.)+[A-Za-z]{2,}$");
    private static final Pattern CI = Pattern.compile("^\\d{5,12}(-[0-9A-Za-z]{1,3})?$");
    private static final Pattern TELEFONO = Pattern.compile("^\\+?\\d{7,15}$");
    private static final Pattern CODIGO = Pattern.compile("^[A-Za-z0-9\\-_]{2,20}$");

    private final List<String> errores = new ArrayList<>();

    /** Escapa los valores que se repiten en los mensajes (los mensajes se muestran como HTML). */
    public static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Quita espacios de mas. null -> "". */
    public static String limpiar(String valor) {
        return valor == null ? "" : valor.trim().replaceAll("\\s+", " ");
    }

    /** Agrega un error propio (reglas que no son de formato). */
    public void error(String mensaje) {
        errores.add(mensaje);
    }

    public boolean hayErrores() {
        return !errores.isEmpty();
    }

    /** Lanza NegocioException con todos los errores acumulados (si hay). */
    public void verificar() throws NegocioException {
        if (!errores.isEmpty()) {
            throw new NegocioException(errores);
        }
    }

    // ---------------- texto ----------------

    /** Texto obligatorio. Devuelve el valor limpio, o null si es invalido. */
    public String texto(String valor, String campo, int maximo) {
        String v = limpiar(valor);
        if (v.isEmpty()) {
            errores.add("El campo <b>" + campo + "</b> es obligatorio y llegó vacío.");
            return null;
        }
        if (v.length() > maximo) {
            errores.add("El campo <b>" + campo + "</b> admite máximo " + maximo + " caracteres (tiene " + v.length() + ").");
            return null;
        }
        return v;
    }

    /** Texto que puede ir vacio (""). Devuelve null si esta vacio. */
    public String textoOpcional(String valor, String campo, int maximo) {
        String v = limpiar(valor);
        if (v.isEmpty()) {
            return null;
        }
        return texto(v, campo, maximo);
    }

    /** Nombre de persona: solo letras y espacios. */
    public String nombre(String valor, String campo) {
        String v = texto(valor, campo, 80);
        if (v != null && !LETRAS.matcher(v).matches()) {
            errores.add("El campo <b>" + campo + "</b> solo admite letras y espacios (recibido: '" + esc(v) + "').");
            return null;
        }
        return v;
    }

    /** Codigo corto: letras, numeros, guion (ej. NOT-2011). Se guarda en mayusculas. */
    public String codigo(String valor, String campo) {
        String v = texto(valor, campo, 20);
        if (v != null && !CODIGO.matcher(v).matches()) {
            errores.add("El campo <b>" + campo + "</b> solo admite letras, números y guiones, sin espacios (recibido: '" + esc(v) + "').");
            return null;
        }
        return v == null ? null : v.toUpperCase();
    }

    // ---------------- datos de personas ----------------

    public String ci(String valor) {
        String v = texto(valor, "ci", 20);
        if (v != null && !CI.matcher(v).matches()) {
            errores.add("El <b>ci</b> '" + esc(v) + "' no es válido: 5 a 12 dígitos y complemento opcional (ej. 7654321 o 7654321-1A).");
            return null;
        }
        return v == null ? null : v.toUpperCase();
    }

    public String email(String valor) {
        String v = texto(valor, "email", 120);
        if (v != null && !EMAIL.matcher(v).matches()) {
            errores.add("El <b>email</b> '" + esc(v) + "' no es válido (ej. nombre@gmail.com).");
            return null;
        }
        return v == null ? null : v.toLowerCase();
    }

    /** Telefono opcional ("" = sin telefono). */
    public String telefono(String valor) {
        String v = limpiar(valor).replace(" ", "");
        if (v.isEmpty()) {
            return null;
        }
        if (!TELEFONO.matcher(v).matches()) {
            errores.add("El <b>telefono</b> '" + esc(v) + "' no es válido: solo números, de 7 a 15 dígitos.");
            return null;
        }
        return v;
    }

    // ---------------- numeros, opciones, fechas ----------------

    /** Entero dentro de un rango. */
    public Integer entero(String valor, String campo, int minimo, int maximo) {
        String v = texto(valor, campo, 10);
        if (v == null) {
            return null;
        }
        if (!v.matches("-?\\d{1,9}")) {
            errores.add("El campo <b>" + campo + "</b> debe ser un número entero (recibido: '" + esc(v) + "').");
            return null;
        }
        int n = Integer.parseInt(v);
        if (n < minimo || n > maximo) {
            errores.add("El campo <b>" + campo + "</b> debe estar entre " + minimo + " y " + maximo + " (recibido: " + n + ").");
            return null;
        }
        return n;
    }

    /** Una de las opciones permitidas (no distingue mayusculas). Devuelve la opcion en mayusculas. */
    public String opcion(String valor, String campo, String... opciones) {
        String v = texto(valor, campo, 40);
        if (v == null) {
            return null;
        }
        String mayus = v.toUpperCase().replace(' ', '_');
        for (String o : opciones) {
            if (o.equals(mayus)) {
                return o;
            }
        }
        errores.add("El campo <b>" + campo + "</b> debe ser uno de: " + String.join(", ", opciones) + " (recibido: '" + esc(v) + "').");
        return null;
    }

    /** Fecha AAAA-MM-DD. */
    public LocalDate fecha(String valor, String campo) {
        String v = texto(valor, campo, 10);
        if (v == null) {
            return null;
        }
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            errores.add("El campo <b>" + campo + "</b> debe ser una fecha válida AAAA-MM-DD (recibido: '" + esc(v) + "').");
            return null;
        }
    }

    /** Hora HH:MM o HH:MM:SS. */
    public LocalTime hora(String valor, String campo) {
        String v = texto(valor, campo, 8);
        if (v == null) {
            return null;
        }
        try {
            return LocalTime.parse(v.length() == 4 ? "0" + v : v); // acepta 8:30
        } catch (DateTimeParseException e) {
            errores.add("El campo <b>" + campo + "</b> debe ser una hora válida HH:MM (recibido: '" + esc(v) + "').");
            return null;
        }
    }

    /** Ultimo segundo del dia: asi se guarda un bloque que termina a las 24:00. */
    public static final LocalTime FIN_DEL_DIA = LocalTime.of(23, 59, 59);

    /**
     * Hora de fin de un bloque: como hora(), pero acepta 24:00 (fin del dia), que se guarda como 23:59:59.
     * Un bloque que cruza la medianoche se parte en dos: hasta 24:00 en la parrilla de un dia y desde 00:00 en la del siguiente.
     */
    public LocalTime horaFin(String valor, String campo) {
        if (valor != null && valor.trim().matches("24:00(:00)?")) {
            return FIN_DEL_DIA;
        }
        return hora(valor, campo);
    }
}
