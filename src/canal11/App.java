package canal11;

import canal11.comunicacion.ClientePOP3;
import canal11.comunicacion.ClienteSMTP;
import canal11.comunicacion.Correo;
import canal11.comunicacion.HiloCorreo;
import canal11.config.Config;
import canal11.config.Registro;
import canal11.datos.Instalador;
import canal11.presentacion.Despachador;
import canal11.presentacion.Interprete;
import canal11.presentacion.Notificador;
import canal11.presentacion.PAlertas;
import canal11.presentacion.PRecordatorio;
import canal11.presentacion.Respuesta;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Sistema de Gestion de Programacion y Contenidos - Canal 11 (via correo electronico).
 *
 * Trabaja con el servidor del curso (base db_grupo12sa y buzon grupo12sa@tecnoweb.org.bo), configurado en config.properties.
 *
 *   scripts\ejecutar.bat                               arranca el hilo que revisa el buzon cada N segundos y responde
 *   scripts\ejecutar.bat --instalar                    crea o actualiza las tablas en la base de datos
 *   scripts\ejecutar.bat --probar "help" correo        procesa un asunto sin usar el correo (OJO: los cambios quedan en la base)
 *   scripts\ejecutar.bat --lote pruebas\archivo.txt    procesa una lista de comandos (remitente | asunto); los cambios quedan en la base
 *   scripts\ejecutar.bat --buzon                       lista los correos del buzon sin borrarlos
 *   scripts\ejecutar.bat --enviar-prueba correo        envia la ayuda a un correo (prueba de SMTP)
 */
public class App {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Despachador despachador = new Despachador();
    private final ClienteSMTP smtp = new ClienteSMTP(
            Config.get("smtp.host"), Config.getInt("smtp.puerto"), Config.get("mail.direccion"));

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        String opcion = args.length > 0 ? args[0] : "";

        switch (opcion) {
            case "--instalar" -> Instalador.instalar();
            case "--probar" -> new App().probar(args);
            case "--lote" -> new App().probarLote(args);
            case "--buzon" -> new App().listarBuzon();
            case "--enviar-prueba" -> new App().enviarPrueba(args);
            case "" -> new App().escuchar();
            default -> System.out.println("Opcion desconocida: " + opcion);
        }
    }

    /**
     * Arranca el hilo que revisa el buzon (HiloCorreo) y le pasa este objeto como oyente:
     * cada vez que llegan correos, el hilo llama a alRecibirCorreos(...), que procesa y responde.
     */
    private void escuchar() throws InterruptedException {
        int intervalo = Config.getInt("app.intervalo");
        System.out.println("==============================================================");
        System.out.println("  CANAL 11 - Sistema de Programacion y Contenidos (via correo)");
        System.out.println("  Buzon:     " + Config.get("mail.direccion") + " (POP3 " + Config.get("pop3.host") + ":"
                + Config.get("pop3.puerto") + ")");
        System.out.println("  Respuesta: SMTP " + Config.get("smtp.host") + ":" + Config.get("smtp.puerto"));
        System.out.println("  Base:      " + Config.get("db.nombre") + " (esquema " + Config.get("db.esquema") + ")");
        System.out.println("  Revisando cada " + intervalo + " s. Ctrl+C para salir.");
        System.out.println("==============================================================");

        HiloCorreo hilo = new HiloCorreo(Config.get("pop3.host"), Config.getInt("pop3.puerto"),
                Config.get("mail.usuario"), Config.get("mail.password"), intervalo, this::alRecibirCorreos);
        Thread t = new Thread(hilo, "Hilo de verificacion de correo");
        t.start();
        Notificador.activar(smtp);
        iniciarRecordatorios();
        t.join(); // el programa sigue vivo mientras el hilo trabaja
    }

    /**
     * Hilo aparte (daemon) que cada revision.intervalo segundos revisa: el plazo de los materiales PENDIENTES
     * (recordatorio / escalamiento), las parrillas en riesgo, las emisiones sin registrar y los rechazos sin corregir.
     * Es independiente del buzon: si POP3 falla, los avisos siguen.
     */
    private void iniciarRecordatorios() {
        int cada = Math.max(5, Config.getInt("revision.intervalo"));
        System.out.println("  Avisos por correo: " + (Notificador.activo() ? "activos" : "desactivados")
                + " (parrilla publicada o cambiada, material validado, orden del noticiero).");
        System.out.println("  Recordatorio de revision: " + (Config.getInt("revision.minutos_aviso") > 0
                ? "aviso a los " + Config.getInt("revision.minutos_aviso") + " min, escalamiento a los "
                + Config.getInt("revision.minutos_escalar") + " min, entre " + Config.get("revision.hora_desde") + " y "
                + Config.get("revision.hora_hasta") : "desactivado") + ".");
        System.out.println("  Alertas: parrilla en riesgo " + Config.get("alerta.parrilla_horas") + " h antes, emision sin registrar a los "
                + Config.get("alerta.emision_minutos") + " min (se revisa todo cada " + cada + " s).");
        PRecordatorio recordatorio = new PRecordatorio(smtp);
        PAlertas alertas = new PAlertas(smtp);
        Thread hilo = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    recordatorio.revisar();
                    alertas.revisar();
                } catch (RuntimeException e) {
                    Registro.errorSistema("Avisos automaticos - error inesperado", e);
                }
                try {
                    Thread.sleep(cada * 1000L);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "Hilo de avisos automaticos");
        hilo.setDaemon(true);
        hilo.start();
    }

    /** Oyente del HiloCorreo: decide cuales responder, procesa cada comando y envia la respuesta. */
    private void alRecibirCorreos(List<Correo> correos) {
        log("Llegaron " + correos.size() + " correo(s)");
        int n = 0;
        for (Correo correo : correos) {
            n++;
            String pos = "[" + n + "/" + correos.size() + "] ";
            if (!debeResponderse(correo)) {
                log("  " + pos + "Ignorado (no es comando o es del sistema): " + correo.getRemitente() + " | " + correo.getAsunto());
                Registro.acceso("IGNORADO", "de=" + correo.getRemitente() + " | asunto=" + correo.getAsunto());
                continue;
            }
            Registro.acceso("CORREO", "recibido de=" + correo.getRemitente() + " | asunto=" + correo.getAsunto());
            log("  " + pos + "De: " + correo.getRemitente());
            log("        Asunto: " + correo.getAsunto());
            long inicio = System.currentTimeMillis();
            Respuesta r = despachador.procesar(correo.getAsunto(), correo.getRemitente());
            try {
                smtp.enviar(correo.getRemitente(), "Canal 11 | " + r.getTitulo(), r.getHtml());
                log("        Respondido: " + r.getTitulo() + " (" + (System.currentTimeMillis() - inicio) + " ms)");
                Registro.acceso("RESPUESTA", "enviada a=" + correo.getRemitente() + " | " + r.getTitulo());
            } catch (IOException e) {
                log("        NO se pudo enviar la respuesta: " + e.getMessage());
                Registro.errorSistema("SMTP - no se pudo enviar la respuesta a " + correo.getRemitente(), e);
            }
        }
    }

    /** No se responde a nosotros mismos, a rebotes del servidor ni a correos que no son comandos (spam). */
    private boolean debeResponderse(Correo c) {
        String de = c.getRemitente();
        return !de.isBlank()
                && !de.equalsIgnoreCase(Config.get("mail.direccion"))
                && !de.contains("mailer-daemon") && !de.contains("postmaster") && !de.contains("no-reply")
                && Interprete.pareceComando(c.getAsunto());
    }

    private ClientePOP3 abrirBuzon() throws IOException {
        ClientePOP3 pop = new ClientePOP3(Config.get("pop3.host"), Config.getInt("pop3.puerto"));
        try {
            pop.login(Config.get("mail.usuario"), Config.get("mail.password"));
            return pop;
        } catch (IOException e) {
            pop.close();
            throw e;
        }
    }

    /** --probar "asunto" [remitente]: muestra la respuesta y la guarda en salida/respuesta.html. */
    private void probar(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Uso: scripts\\ejecutar.bat --probar \"help\" correo@remitente.com");
            return;
        }
        String remitente = args.length > 2 ? args[2] : "";
        Respuesta r = despachador.procesar(args[1], remitente);
        Path archivo = Path.of("salida", "respuesta.html");
        Files.createDirectories(archivo.getParent());
        Files.writeString(archivo, r.getHtml(), StandardCharsets.UTF_8);
        System.out.println("Asunto de la respuesta: Canal 11 | " + r.getTitulo());
        System.out.println("Vista previa guardada en: " + archivo.toAbsolutePath());
    }

    /**
     * --lote pruebas/cu1.txt: ejecuta cada linea "remitente | asunto" del archivo (sin correo)
     * y guarda cada respuesta en salida/01.html, salida/02.html, ... Las lineas con # son comentarios.
     * La linea "@ahora AAAA-MM-DDTHH:MM" fija la hora del sistema desde ahi (para probar fechas y emisiones sin esperar).
     */
    private void probarLote(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Uso: scripts\\ejecutar.bat --lote pruebas\\cu1.txt");
            return;
        }
        Path carpeta = Path.of("salida");
        Files.createDirectories(carpeta);
        int n = 0;
        for (String linea : Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8)) {
            if (linea.trim().startsWith("@ahora")) { // @ahora 2031-05-10T18:00  fija la hora del sistema (vacio = hora real)
                Config.fijar("app.ahora", linea.trim().substring("@ahora".length()).trim());
                continue;
            }
            if (linea.isBlank() || linea.trim().startsWith("#") || !linea.contains("|")) {
                continue;
            }
            String remitente = linea.substring(0, linea.indexOf('|')).trim();
            String asunto = linea.substring(linea.indexOf('|') + 1).trim();
            Respuesta r = despachador.procesar(asunto, remitente);
            n++;
            String archivo = String.format("%02d.html", n);
            Files.writeString(carpeta.resolve(archivo), r.getHtml(), StandardCharsets.UTF_8);
            System.out.printf("%02d  %-12s %-60s -> %s%n", n, r.getTitulo().startsWith("Error") ? "[ERROR]" : "[OK]",
                    asunto.length() > 60 ? asunto.substring(0, 57) + "..." : asunto, r.getTitulo());
        }
        System.out.println("Respuestas guardadas en " + carpeta.toAbsolutePath() + " (abrir los .html en el navegador)");
    }

    /** --buzon: lista los correos sin borrar nada. */
    private void listarBuzon() throws IOException {
        try (ClientePOP3 pop = abrirBuzon()) {
            int total = pop.cantidadMensajes();
            System.out.println("Correos en el buzon: " + total);
            for (int i = 1; i <= total; i++) {
                Correo c = pop.obtener(i);
                System.out.println("  " + c + (debeResponderse(c) ? "   -> se respondera" : "   -> se ignorara"));
            }
        }
    }

    /** --enviar-prueba correo: envia la ayuda general (prueba del cliente SMTP). */
    private void enviarPrueba(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Uso: scripts\\ejecutar.bat --enviar-prueba correo@destino.com [remitente_registrado]");
            return;
        }
        String remitente = args.length > 2 ? args[2] : args[1];
        Respuesta r = despachador.procesar("help", remitente);
        smtp.enviar(args[1], "Canal 11 | " + r.getTitulo(), r.getHtml());
        System.out.println("Correo de prueba enviado a " + args[1]);
    }

    private static void log(String msg) {
        System.out.println("[" + LocalTime.now().format(HORA) + "] " + msg);
    }
}
