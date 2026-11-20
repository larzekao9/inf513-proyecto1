package canal11.comunicacion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Cliente POP3 programado con sockets (RFC 1939), puerto 110.
 * Dialogo: USER -> PASS -> STAT -> TOP n 0 (solo cabeceras) -> DELE n -> QUIT.
 * Los DELE recien se aplican cuando se envia QUIT (al cerrar).
 */
public class ClientePOP3 implements AutoCloseable {

    private static final int TIEMPO_ESPERA_MS = 20000;

    private final Socket socket;
    private final BufferedReader entrada;
    private final OutputStream salida;

    public ClientePOP3(String host, int puerto) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, puerto), TIEMPO_ESPERA_MS);
        socket.setSoTimeout(TIEMPO_ESPERA_MS);
        // ISO-8859-1 conserva los bytes tal cual; luego MimeUtil decodifica las cabeceras
        entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
        salida = socket.getOutputStream();
        leerOk(); // saludo del servidor: +OK Dovecot ready
    }

    public void login(String usuario, String password) throws IOException {
        comando("USER " + usuario);
        comando("PASS " + password);
    }

    /** STAT -> "+OK cantidad tamanio". Devuelve la cantidad de mensajes. */
    public int cantidadMensajes() throws IOException {
        String r = comando("STAT");
        return Integer.parseInt(r.split("\\s+")[1]);
    }

    /**
     * Descarga solo las cabeceras del mensaje n (TOP n 0) y devuelve remitente y asunto.
     * El remitente se toma de la primera cabecera que traiga un email valido, en este orden:
     *   From  ->  Reply-To  ->  Return-Path  ->  Sender
     * Asi funciona con cualquier proveedor (Gmail, Outlook/Hotmail, Yahoo, UAGRM...) aunque alguno
     * no envie una de ellas o la envie en otro formato.
     */
    public Correo obtener(int n) throws IOException {
        comando("TOP " + n + " 0");
        List<String> cabeceras = unirCabecerasDobladas(leerMultilinea());
        String asunto = valor(cabeceras, "subject");
        String remitente = "";
        for (String nombre : new String[]{"from", "reply-to", "return-path", "sender"}) {
            remitente = MimeUtil.extraerEmail(valor(cabeceras, nombre));
            if (!remitente.isEmpty()) {
                break;
            }
        }
        return new Correo(n, remitente, asunto);
    }

    /** Valor decodificado de la primera cabecera con ese nombre (sin distinguir mayusculas), o "". */
    private static String valor(List<String> cabeceras, String nombre) {
        String prefijo = nombre + ":";
        for (String c : cabeceras) {
            if (c.regionMatches(true, 0, prefijo, 0, prefijo.length())) {
                return MimeUtil.decodificarCabecera(c.substring(prefijo.length()).trim()).trim();
            }
        }
        return "";
    }

    /** Identificador unico y estable del mensaje n (UIDL): sirve para reconocer un correo ya leido sin borrarlo. */
    public String identificador(int n) throws IOException {
        String[] partes = comando("UIDL " + n).trim().split("\\s+");
        return partes.length >= 3 ? partes[2] : String.valueOf(n);
    }

    /** Marca el mensaje n para borrar (se aplica al cerrar con QUIT). */
    public void eliminar(int n) throws IOException {
        comando("DELE " + n);
    }

    @Override
    public void close() {
        try {
            comando("QUIT");
        } catch (IOException ignorado) {
            // la conexion ya estaba cerrada
        }
        try {
            socket.close();
        } catch (IOException ignorado) {
            // nada que hacer
        }
    }

    private String comando(String cmd) throws IOException {
        salida.write((cmd + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        salida.flush();
        return leerOk();
    }

    private String leerOk() throws IOException {
        String r = entrada.readLine();
        if (r == null) {
            throw new IOException("POP3: conexion cerrada por el servidor");
        }
        if (r.startsWith("-ERR")) {
            throw new ErrorPOP3("POP3: " + r);
        }
        if (!r.startsWith("+OK")) {
            throw new IOException("POP3: " + r);
        }
        return r;
    }

    /** Respuesta "-ERR" del servidor (la conexion sigue viva), p. ej. un mensaje que otro cliente ya borro. */
    public static class ErrorPOP3 extends IOException {
        public ErrorPOP3(String mensaje) {
            super(mensaje);
        }
    }

    /** Lee una respuesta multilinea hasta la linea "." (quita el punto de relleno). */
    private List<String> leerMultilinea() throws IOException {
        List<String> lineas = new ArrayList<>();
        String l;
        while ((l = entrada.readLine()) != null) {
            if (l.equals(".")) {
                break;
            }
            lineas.add(l.startsWith("..") ? l.substring(1) : l);
        }
        return lineas;
    }

    /** Une las cabeceras partidas en varias lineas (las que continuan empiezan con espacio o tab). */
    private static List<String> unirCabecerasDobladas(List<String> lineas) {
        List<String> cabeceras = new ArrayList<>();
        for (String l : lineas) {
            if (l.isEmpty()) {
                break; // fin de cabeceras
            }
            if ((l.startsWith(" ") || l.startsWith("\t")) && !cabeceras.isEmpty()) {
                int i = cabeceras.size() - 1;
                cabeceras.set(i, cabeceras.get(i) + " " + l.trim());
            } else {
                cabeceras.add(l);
            }
        }
        return cabeceras;
    }
}
