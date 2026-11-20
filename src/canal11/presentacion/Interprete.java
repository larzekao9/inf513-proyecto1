package canal11.presentacion;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta el asunto del correo con el patron:  comando["param1","param2",...]
 * Ejemplos validos: help | help["3"] | lisPro["*"] | insPar["2026-11-20"] | RE: mosUsu["7654321"]
 */
public final class Interprete {

    private static final Pattern PATRON = Pattern.compile("^([A-Za-z]{3,12})\\s*(?:\\[(.*)\\])?\\s*$", Pattern.DOTALL);

    private final String nombre;
    private final List<String> parametros;

    private Interprete(String nombre, List<String> parametros) {
        this.nombre = nombre;
        this.parametros = parametros;
    }

    public String getNombre() {
        return nombre;
    }

    public List<String> getParametros() {
        return parametros;
    }

    /** true si el asunto tiene forma de comando (sirve para ignorar spam sin responder). */
    public static boolean pareceComando(String asunto) {
        return asunto != null && PATRON.matcher(normalizar(asunto)).matches();
    }

    public static Interprete leer(String asunto) throws ComandoException {
        if (asunto == null || asunto.isBlank()) {
            throw new ComandoException("El asunto del correo esta vacio.");
        }
        Matcher m = PATRON.matcher(normalizar(asunto));
        if (!m.matches()) {
            throw new ComandoException("El asunto no tiene el formato de un comando. "
                    + "Use: comando[\"parametro1\",\"parametro2\",...]");
        }
        List<String> params = m.group(2) == null ? new ArrayList<>() : separarParametros(m.group(2));
        return new Interprete(m.group(1), params);
    }

    /**
     * Prefijos que agregan los clientes de correo al responder o reenviar, en espanol e ingles
     * (Gmail: "Re:", "Fwd:"; Outlook: "RE:", "RV:", "FW:"; otros: "Reenviar:", "TR:"). Pueden venir repetidos.
     */
    private static final Pattern PREFIJOS = Pattern.compile("^(\\s*(re|rv|fw|fwd|tr|reenviar|reenviado|aw|wg)\\s*:\\s*)+",
            Pattern.CASE_INSENSITIVE);

    /**
     * Deja el asunto listo para interpretar:
     *  - quita "RE:", "Fwd:", "RV:"... (si el usuario respondio o reenvio un correo con el comando);
     *  - cambia las comillas tipograficas de celulares y Word por comillas rectas.
     */
    static String normalizar(String asunto) {
        String s = asunto.trim()
                .replace('“', '"').replace('”', '"').replace('„', '"')
                .replace('‘', '\'').replace('’', '\'');
        return PREFIJOS.matcher(s).replaceFirst("").trim();
    }

    /**
     * Separa por comas respetando las comas que esten dentro de comillas. Las comillas son opcionales.
     * Dentro de un parametro entre comillas, una comilla doble literal se escribe \" o "" (como en CSV):
     * insPro["Dijo \"hola\", adios","x"]  ->  Dijo "hola", adios  y  x.
     * Cualquier otra barra invertida se conserva tal cual (rutas como D:\videos\nota.mp4 o \\servidor\carpeta).
     */
    private static List<String> separarParametros(String texto) throws ComandoException {
        List<String> lista = new ArrayList<>();
        if (texto.isBlank()) {
            return lista; // comando[]
        }
        StringBuilder actual = new StringBuilder();
        boolean enComillas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            char siguiente = i + 1 < texto.length() ? texto.charAt(i + 1) : '\0';
            if (enComillas && c == '\\' && siguiente == '"') {
                actual.append('"');
                i++;
            } else if (enComillas && c == '"' && siguiente == '"') {
                actual.append('"');
                i++;
            } else if (c == '"') {
                enComillas = !enComillas;
            } else if (c == ',' && !enComillas) {
                lista.add(actual.toString().trim());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        if (enComillas) {
            throw new ComandoException("Falta cerrar una comilla en los parametros.");
        }
        lista.add(actual.toString().trim());
        return lista;
    }
}
