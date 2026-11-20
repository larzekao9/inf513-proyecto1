package canal11.comunicacion;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utilidades MIME (RFC 2047): asuntos con tildes y extraccion del email del remitente. */
public final class MimeUtil {

    private static final Pattern PALABRA_CODIFICADA = Pattern.compile("=\\?([^?]+)\\?([BbQq])\\?([^?]*)\\?=");
    /**
     * Email de CUALQUIER proveedor (Gmail, Outlook/Hotmail, Yahoo, UAGRM, dominios propios...).
     * Parte local: letras, numeros y los simbolos permitidos por el estandar (. _ % + - ' etc.), sin puntos al inicio/fin
     * ni ".." seguidos. Dominio: uno o mas subdominios (ficct.uagrm.edu.bo) y terminacion de 2+ letras.
     * IMPORTANTE: mantener igual que Validador.EMAIL.
     */
    public static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
            + "@(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\\.)+[A-Za-z]{2,}");
    private static final Pattern ENTRE_ANGULOS = Pattern.compile("<([^<>]+)>");

    private MimeUtil() {
    }

    /**
     * Decodifica una cabecera. La linea llega leida como ISO-8859-1 (bytes crudos),
     * por eso el texto sin codificar se reinterpreta como UTF-8.
     */
    public static String decodificarCabecera(String crudo) {
        String valor = crudo.replaceAll("\\?=\\s+=\\?", "?==?"); // espacios entre palabras codificadas se ignoran
        Matcher m = PALABRA_CODIFICADA.matcher(valor);
        StringBuilder sb = new StringBuilder();
        int ultimo = 0;
        while (m.find()) {
            sb.append(aUtf8(valor.substring(ultimo, m.start())));
            sb.append(decodificarPalabra(m.group(1), m.group(2), m.group(3)));
            ultimo = m.end();
        }
        sb.append(aUtf8(valor.substring(ultimo)));
        return sb.toString();
    }

    /** Codifica un texto (asunto) en UTF-8/Base64 para que las tildes lleguen bien. */
    public static String codificarCabecera(String texto) {
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(texto.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    /**
     * Extrae la direccion de una cabecera From / Reply-To / Return-Path, de cualquier proveedor:
     *   ana.rojas@gmail.com                        (solo la direccion)
     *   Ana Rojas <ana.rojas@gmail.com>            (Gmail, Outlook, Yahoo)
     *   "Rojas, Ana (ana@viejo.com)" <ana@gmail.com>  (el nombre visible trae otro email: se usa el de <>)
     *   <ana@ficct.uagrm.edu.bo>                   (Return-Path)
     * Devuelve la direccion en minusculas, o "" si no hay ninguna valida.
     */
    public static String extraerEmail(String cabecera) {
        if (cabecera == null) {
            return "";
        }
        // 1) Lo que esta entre < > (la ultima pareja: es la direccion real, el resto es el nombre visible)
        String encontrado = null;
        Matcher a = ENTRE_ANGULOS.matcher(cabecera);
        while (a.find()) {
            String dentro = a.group(1).trim();
            if (EMAIL.matcher(dentro).matches()) {
                encontrado = dentro;
            }
        }
        // 2) Si no hay < >, la ultima direccion valida que aparezca en el texto
        if (encontrado == null) {
            Matcher m = EMAIL.matcher(cabecera);
            while (m.find()) {
                encontrado = m.group();
            }
        }
        return encontrado == null ? "" : encontrado.toLowerCase();
    }

    private static String aUtf8(String latin1) {
        return new String(latin1.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }

    private static String decodificarPalabra(String charset, String tipo, String texto) {
        try {
            Charset cs = Charset.forName(charset);
            if (tipo.equalsIgnoreCase("B")) {
                return new String(Base64.getMimeDecoder().decode(texto), cs);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (int i = 0; i < texto.length(); i++) {
                char c = texto.charAt(i);
                if (c == '_') {
                    out.write(' ');
                } else if (c == '=' && i + 2 < texto.length()) {
                    out.write(Integer.parseInt(texto.substring(i + 1, i + 3), 16));
                    i += 2;
                } else {
                    out.write(c);
                }
            }
            return out.toString(cs);
        } catch (Exception e) {
            return texto;
        }
    }
}
