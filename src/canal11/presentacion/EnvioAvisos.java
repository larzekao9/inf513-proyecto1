package canal11.presentacion;

import canal11.comunicacion.ClienteSMTP;
import canal11.config.Registro;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Envio de avisos desde los hilos automaticos: a diferencia de Notificador, aqui se necesita saber si el envio funciono. */
final class EnvioAvisos {

    private EnvioAvisos() {
    }

    /** Envia el aviso a cada destinatario y devuelve a quienes si les llego. */
    static List<String> enviar(ClienteSMTP smtp, List<String> destinos, String titulo, String contenido) {
        String html = Html.pagina(titulo, null, contenido);
        List<String> enviados = new ArrayList<>();
        for (String destino : destinos) {
            try {
                smtp.enviar(destino, "Canal 11 | " + titulo, html);
                enviados.add(destino);
            } catch (IOException e) {
                Registro.errorSistema("SMTP - no se pudo enviar el aviso '" + titulo + "' a " + destino, e);
            }
        }
        return enviados;
    }
}
