package canal11.comunicacion;

import canal11.config.Config;
import canal11.config.Registro;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hilo que revisa el buzon cada N segundos (ciclo "forever" del servidor).
 * En cada vuelta: conecta por POP3, descarga las cabeceras de todos los correos, los marca para borrar,
 * cierra con QUIT (ahi se borran) y avisa al OyenteCorreo con la lista. El oyente decide que responder.
 *
 * Si falla la conexion (servidor caido, sin internet) solo se informa y se reintenta en la siguiente vuelta:
 * el hilo nunca se detiene por un error.
 */
public class HiloCorreo implements Runnable {

    private final String host;
    private final int puerto;
    private final String usuario;
    private final String password;
    private final int intervaloSegundos;
    private final OyenteCorreo oyente;
    private volatile boolean activo = true;
    /** pop3.borrar=false (solo pruebas): los correos se leen sin borrarlos y se recuerdan los ya procesados. */
    private final boolean borrar = !"false".equalsIgnoreCase(Config.get("pop3.borrar"));
    private final Set<String> yaLeidos = new HashSet<>();
    private boolean primeraVuelta = true;

    public HiloCorreo(String host, int puerto, String usuario, String password, int intervaloSegundos, OyenteCorreo oyente) {
        this.host = host;
        this.puerto = puerto;
        this.usuario = usuario;
        this.password = password;
        this.intervaloSegundos = intervaloSegundos;
        this.oyente = oyente;
    }

    @Override
    public void run() {
        while (activo) {
            try {
                List<Correo> correos = descargar();
                if (!correos.isEmpty()) {
                    oyente.alRecibirCorreos(correos);
                }
            } catch (IOException e) {
                System.err.println("[HiloCorreo] No se pudo revisar el buzon (" + host + ":" + puerto + "): " + e.getMessage());
                Registro.errorSistema("POP3 - no se pudo revisar el buzon (" + host + ":" + puerto + ")", e);
            } catch (RuntimeException e) {
                System.err.println("[HiloCorreo] Error inesperado: " + e);
                Registro.errorSistema("HiloCorreo - error inesperado", e);
            }
            try {
                Thread.sleep(intervaloSegundos * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                activo = false;
            }
        }
    }

    /** Detiene el hilo al terminar la vuelta actual. */
    public void detener() {
        activo = false;
    }

    /** Descarga todos los correos del buzon y los borra (el QUIT al cerrar aplica los DELE), salvo con pop3.borrar=false. */
    private List<Correo> descargar() throws IOException {
        List<Correo> correos = new ArrayList<>();
        try (ClientePOP3 pop = new ClientePOP3(host, puerto)) {
            pop.login(usuario, password);
            int total = pop.cantidadMensajes();
            for (int i = 1; i <= total; i++) {
                try {
                    if (!borrar) {
                        String uid = pop.identificador(i);
                        // Sin borrar, lo que ya estaba al arrancar se da por leido: no se vuelven a ejecutar comandos viejos.
                        if (!yaLeidos.add(uid) || primeraVuelta) {
                            continue;
                        }
                        correos.add(pop.obtener(i));
                        continue;
                    }
                    correos.add(pop.obtener(i));
                    pop.eliminar(i);
                } catch (ClientePOP3.ErrorPOP3 e) {
                    // Buzon compartido: otro cliente ya borro este mensaje ("expunged"). Se omite y se sigue.
                    System.err.println("[HiloCorreo] Mensaje " + i + " omitido: " + e.getMessage());
                }
            }
            primeraVuelta = false;
        }
        return correos;
    }
}
