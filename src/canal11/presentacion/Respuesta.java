package canal11.presentacion;

/** Resultado de procesar un correo: asunto y cuerpo HTML de la respuesta. */
public class Respuesta {

    private final String titulo;
    private final String html;

    public Respuesta(String titulo, String html) {
        this.titulo = titulo;
        this.html = html;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getHtml() {
        return html;
    }
}
