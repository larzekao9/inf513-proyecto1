package canal11.presentacion;

import canal11.comunicacion.ClienteSMTP;
import canal11.config.Config;
import canal11.config.Registro;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Avisos inmediatos por correo (parrilla publicada o cambiada, material validado, orden del noticiero...).
 * Es el "push" que reemplaza al WhatsApp: el sistema avisa solo, sin esperar a que alguien consulte.
 *
 * Solo envia cuando el servidor esta corriendo (App.escuchar() lo activa). En --probar y --lote no manda nada,
 * para que las pruebas no escriban a personas reales. El envio es en segundo plano: no demora la respuesta del comando.
 */
public final class Notificador {

    private static volatile ClienteSMTP smtp;
    private static final ExecutorService COLA = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Hilo de notificaciones");
        t.setDaemon(true);
        return t;
    });

    private Notificador() {
    }

    /** Activa los avisos (solo si notificaciones.activas=true). */
    public static void activar(ClienteSMTP cliente) {
        if (!"false".equalsIgnoreCase(Config.get("notificaciones.activas"))) {
            smtp = cliente;
        }
    }

    public static boolean activo() {
        return smtp != null;
    }

    /** Envia el aviso a cada destinatario (sin repetir). Con lista vacia o sin activar, no hace nada. */
    public static void enviar(Collection<String> destinos, String titulo, String contenido) {
        ClienteSMTP cliente = smtp;
        if (cliente == null || destinos == null || destinos.isEmpty()) {
            return;
        }
        Set<String> unicos = new LinkedHashSet<>(destinos);
        String html = Html.pagina(titulo, null, contenido);
        COLA.submit(() -> {
            for (String destino : unicos) {
                try {
                    cliente.enviar(destino, "Canal 11 | " + titulo, html);
                    Registro.acceso("AVISO", "enviado a=" + destino + " | " + titulo);
                } catch (IOException e) {
                    Registro.errorSistema("SMTP - no se pudo enviar el aviso '" + titulo + "' a " + destino, e);
                }
            }
        });
    }
}
