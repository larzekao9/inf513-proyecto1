package canal11.config;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Registro (log) en archivos locales, pedido por el enunciado para la auditoria tecnica:
 *   logs/access.log  cada correo recibido, cada comando ejecutado (con su resultado y tiempo) y cada respuesta enviada.
 *   logs/error.log   cada error: de sintaxis o negocio (ERROR_USUARIO) y los de sistema, con la traza (ERROR_SISTEMA).
 * Una linea por evento: "2026-11-20 10:15:30 | TIPO | detalle". Los saltos de linea se reemplazan por espacios.
 * Si no se puede escribir el archivo, el programa NO se detiene: solo avisa por consola.
 */
public final class Registro {

    private static final Path CARPETA = Path.of("logs");
    private static final Path ACCESO = CARPETA.resolve("access.log");
    private static final Path ERRORES = CARPETA.resolve("error.log");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Registro() {
    }

    /** access.log: un evento normal (CORREO, COMANDO, RESPUESTA...). */
    public static void acceso(String tipo, String detalle) {
        escribir(ACCESO, tipo, detalle);
    }

    /** access.log: un comando procesado. resultado = OK o ERROR. */
    public static void comando(String remitente, String asunto, String resultado, long milisegundos) {
        acceso("COMANDO", "de=" + remitente + " | asunto=" + asunto + " | resultado=" + resultado + " | " + milisegundos + " ms");
    }

    /** error.log: un error de uso (sintaxis, permisos, reglas de negocio). El sistema funciona bien. */
    public static void errorUsuario(String remitente, String asunto, String motivo) {
        escribir(ERRORES, "ERROR_USUARIO", "de=" + remitente + " | asunto=" + asunto + " | " + limpiarHtml(motivo));
    }

    /** error.log: un fallo del sistema (base de datos, correo, bug). Incluye la traza completa. */
    public static void errorSistema(String contexto, Throwable e) {
        StringWriter traza = new StringWriter();
        e.printStackTrace(new PrintWriter(traza));
        escribir(ERRORES, "ERROR_SISTEMA", contexto + " | " + e + " | " + traza);
    }

    /** error.log: un fallo del sistema sin excepcion asociada. */
    public static void errorSistema(String contexto) {
        escribir(ERRORES, "ERROR_SISTEMA", contexto);
    }

    private static synchronized void escribir(Path archivo, String tipo, String detalle) {
        String linea = LocalDateTime.now().format(FECHA) + " | " + tipo + " | " + plano(detalle) + System.lineSeparator();
        try {
            Files.createDirectories(CARPETA);
            Files.writeString(archivo, linea, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[Registro] No se pudo escribir " + archivo + ": " + e.getMessage());
        }
    }

    /** Una sola linea: sin saltos de linea (la traza queda separada por " ¶ "). */
    private static String plano(String s) {
        return s == null ? "" : s.replace("\r", "").replace("\n", " ¶ ").replace("\t", " ").trim();
    }

    /** Los mensajes de negocio traen etiquetas HTML (<b>, <code>); en el log se guardan como texto. */
    private static String limpiarHtml(String s) {
        return s == null ? "" : s.replaceAll("<[^>]*>", "").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }
}
