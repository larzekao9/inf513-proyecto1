package canal11.presentacion;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Plantilla HTML de los correos de respuesta.
 * Todos los estilos van dentro de cada etiqueta (style="...") porque Gmail descarta los bloques <style>.
 */
public final class Html {

    private static final String AZUL = "#1f3a5f";
    private static final String AZUL_CLARO = "#e8eef5";
    private static final String BORDE = "#d5dbe3";
    private static final String TEXTO = "#1f2933";
    private static final String GRIS = "#6b7785";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private Html() {
    }

    public static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Pagina completa: encabezado del canal, titulo de la operacion, contenido y pie. */
    public static String pagina(String titulo, String usuario, String contenido) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'></head>"
                + "<body style='margin:0;padding:0;background:#eef1f5;'>"
                + "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' style='background:#eef1f5;padding:24px 0;'>"
                + "<tr><td align='center'>"
                + "<table role='presentation' width='760' cellpadding='0' cellspacing='0' style='max-width:760px;width:100%;"
                + "background:#ffffff;border:1px solid " + BORDE + ";border-radius:8px;font-family:Arial,Helvetica,sans-serif;color:" + TEXTO + ";'>"
                // Encabezado
                + "<tr><td style='background:" + AZUL + ";padding:18px 28px;border-radius:8px 8px 0 0;'>"
                + "<div style='color:#ffffff;font-size:20px;font-weight:bold;letter-spacing:1px;'>CANAL 11</div>"
                + "<div style='color:#c9d6e6;font-size:12px;margin-top:2px;'>Sistema de Gesti&oacute;n de Programaci&oacute;n y Contenidos</div>"
                + "</td></tr>"
                // Titulo de la operacion
                + "<tr><td style='padding:20px 28px 4px 28px;'>"
                + "<div style='font-size:18px;font-weight:bold;color:" + AZUL + ";'>" + esc(titulo) + "</div>"
                + (usuario == null ? "" : "<div style='font-size:12px;color:" + GRIS + ";margin-top:4px;'>" + esc(usuario)
                + " &middot; " + LocalDateTime.now().format(FECHA) + "</div>")
                + "</td></tr>"
                // Contenido
                + "<tr><td style='padding:12px 28px 24px 28px;font-size:14px;line-height:1.5;'>" + contenido + "</td></tr>"
                // Pie
                + "<tr><td style='background:#f6f8fa;border-top:1px solid " + BORDE + ";padding:12px 28px;border-radius:0 0 8px 8px;"
                + "font-size:11px;color:" + GRIS + ";text-align:center;'>"
                + "Env&iacute;e <b>help</b> en el asunto para ver los comandos &middot; Respuesta autom&aacute;tica, no responda este correo"
                + "</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    /** Tabla con datos de la BD: el contenido de cada celda se escapa. */
    public static String tabla(String[] columnas, List<String[]> filas) {
        List<String[]> escapadas = new java.util.ArrayList<>();
        for (String[] f : filas) {
            String[] e = new String[f.length];
            for (int i = 0; i < f.length; i++) {
                e[i] = esc(f[i]);
            }
            escapadas.add(e);
        }
        return tablaHtml(columnas, escapadas);
    }

    /** Tabla cuyas celdas ya vienen como HTML (por ejemplo con codigo() dentro). */
    public static String tablaHtml(String[] columnas, List<String[]> filas) {
        if (filas.isEmpty()) {
            return parrafo("<i>No se encontraron registros.</i>");
        }
        StringBuilder sb = new StringBuilder("<table cellpadding='0' cellspacing='0' width='100%' style='border-collapse:collapse;"
                + "font-size:13px;border:1px solid " + BORDE + ";margin:8px 0;'>");
        sb.append("<tr>");
        for (String c : columnas) {
            sb.append("<th align='left' style='background:").append(AZUL).append(";color:#ffffff;padding:8px 10px;"
                    + "border:1px solid ").append(AZUL).append(";font-weight:bold;'>").append(esc(c)).append("</th>");
        }
        sb.append("</tr>");
        for (int i = 0; i < filas.size(); i++) {
            String fondo = i % 2 == 0 ? "#ffffff" : "#f5f7fa";
            sb.append("<tr>");
            for (String v : filas.get(i)) {
                sb.append("<td valign='top' style='background:").append(fondo).append(";padding:7px 10px;border:1px solid ")
                        .append(BORDE).append(";'>").append(v == null ? "" : v).append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    /** Tabla con el total de registros debajo. */
    public static String tablaConTotal(String[] columnas, List<String[]> filas) {
        return tabla(columnas, filas) + total(filas.size());
    }

    /** "Total: N registro(s)" (vacio si no hay registros). */
    public static String total(int n) {
        return n == 0 ? "" : "<div style='font-size:12px;color:" + GRIS + ";'>Total: " + n + " registro(s)</div>";
    }

    /** Lista con viñetas (mensajes ya en HTML). */
    public static String lista(List<String> items) {
        StringBuilder sb = new StringBuilder("<ul style='margin:6px 0 0 0;padding-left:20px;'>");
        for (String i : items) {
            sb.append("<li style='margin:3px 0;'>").append(i).append("</li>");
        }
        return sb.append("</ul>").toString();
    }

    /** Ficha de un solo registro: tabla vertical "Campo | Valor" (para mos*, ins*, mod*). */
    public static String ficha(String[] campos, String[] valores) {
        StringBuilder sb = new StringBuilder("<table cellpadding='0' cellspacing='0' width='100%' style='border-collapse:collapse;"
                + "font-size:13px;border:1px solid " + BORDE + ";margin:8px 0;'>");
        for (int i = 0; i < campos.length && i < valores.length; i++) {
            sb.append("<tr><td width='30%' style='background:").append(AZUL_CLARO).append(";color:").append(AZUL)
                    .append(";font-weight:bold;padding:7px 10px;border:1px solid ").append(BORDE).append(";'>")
                    .append(esc(campos[i])).append("</td><td style='padding:7px 10px;border:1px solid ").append(BORDE)
                    .append(";'>").append(esc(valores[i])).append("</td></tr>");
        }
        return sb.append("</table>").toString();
    }

    public static String parrafo(String html) {
        return "<p style='margin:8px 0;'>" + html + "</p>";
    }

    public static String subtitulo(String texto) {
        return "<div style='font-size:15px;font-weight:bold;color:" + AZUL + ";margin:18px 0 6px 0;'>" + esc(texto) + "</div>";
    }

    /** Texto en fuente monoespaciada (para comandos y ejemplos). */
    public static String codigo(String texto) {
        return "<span style='font-family:Consolas,Courier New,monospace;font-size:12px;background:" + AZUL_CLARO
                + ";color:" + AZUL + ";padding:2px 6px;border-radius:3px;white-space:nowrap;'>" + esc(texto) + "</span>";
    }

    public static String exito(String html) {
        return aviso(html, "#e8f5e9", "#2e7d32");
    }

    public static String error(String html) {
        return aviso(html, "#fdecea", "#c62828");
    }

    public static String info(String html) {
        return aviso(html, AZUL_CLARO, AZUL);
    }

    private static String aviso(String html, String fondo, String color) {
        return "<div style='background:" + fondo + ";border-left:4px solid " + color + ";padding:10px 14px;margin:8px 0;"
                + "color:" + TEXTO + ";'>" + html + "</div>";
    }
}
