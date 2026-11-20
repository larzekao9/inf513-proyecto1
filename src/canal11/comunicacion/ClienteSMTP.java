package canal11.comunicacion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

/**
 * Cliente SMTP programado con sockets (RFC 5321), puerto 25.
 * Dialogo: HELO -> MAIL FROM -> RCPT TO -> DATA -> (mensaje) -> "." -> QUIT.
 */
public class ClienteSMTP {

    private static final int TIEMPO_ESPERA_MS = 20000;

    private final String host;
    private final int puerto;
    private final String remitente;

    public ClienteSMTP(String host, int puerto, String remitente) {
        this.host = host;
        this.puerto = puerto;
        this.remitente = remitente;
    }

    /** Envia un correo con cuerpo HTML. */
    public void enviar(String destino, String asunto, String html) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, puerto), TIEMPO_ESPERA_MS);
            socket.setSoTimeout(TIEMPO_ESPERA_MS);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            OutputStream out = socket.getOutputStream();

            esperar(in, 220);
            comando(out, in, "HELO " + host, 250);
            comando(out, in, "MAIL FROM:<" + remitente + ">", 250);
            comando(out, in, "RCPT TO:<" + destino + ">", 250, 251);
            comando(out, in, "DATA", 354);

            String dominio = remitente.substring(remitente.indexOf('@') + 1);
            // Cuerpo en Base64: evita problemas con tildes, lineas largas y puntos al inicio de linea
            String cuerpo = Base64.getMimeEncoder().encodeToString(html.getBytes(StandardCharsets.UTF_8));
            String mensaje = "From: Canal 11 - Programacion <" + remitente + ">\r\n"
                    + "To: " + destino + "\r\n"
                    + "Subject: " + MimeUtil.codificarCabecera(asunto) + "\r\n"
                    + "Date: " + ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME) + "\r\n"
                    + "Message-ID: <" + UUID.randomUUID() + "@" + dominio + ">\r\n"
                    + "MIME-Version: 1.0\r\n"
                    + "Content-Type: text/html; charset=UTF-8\r\n"
                    + "Content-Transfer-Encoding: base64\r\n"
                    + "\r\n"
                    + cuerpo + "\r\n"
                    + ".";
            comando(out, in, mensaje, 250);
            comando(out, in, "QUIT", 221);
        }
    }

    private void comando(OutputStream out, BufferedReader in, String cmd, int... esperados) throws IOException {
        out.write((cmd + "\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        esperar(in, esperados);
    }

    /** Lee la respuesta (incluidas las multilinea "250-...") y valida el codigo. */
    private void esperar(BufferedReader in, int... esperados) throws IOException {
        String linea;
        do {
            linea = in.readLine();
            if (linea == null) {
                throw new IOException("SMTP: el servidor cerro la conexion");
            }
        } while (linea.length() > 3 && linea.charAt(3) == '-');

        int codigo;
        try {
            codigo = Integer.parseInt(linea.substring(0, 3));
        } catch (RuntimeException e) {
            throw new IOException("SMTP: respuesta invalida: " + linea);
        }
        for (int e : esperados) {
            if (codigo == e) {
                return;
            }
        }
        throw new IOException("SMTP: " + linea);
    }
}
