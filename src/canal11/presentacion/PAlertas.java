package canal11.presentacion;

import canal11.comunicacion.ClienteSMTP;
import canal11.config.Registro;
import canal11.negocio.NAlerta;
import canal11.negocio.NAviso;
import canal11.negocio.NRecordatorio;
import canal11.negocio.Rol;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Alertas automaticas (no son comandos: las dispara el hilo de avisos de App): parrilla en riesgo, emision sin registrar
 * y material rechazado sin corregir. Arma el correo, lo envia y solo si se envio lo marca como avisado (ver NAlerta).
 */
public class PAlertas {

    private final NAlerta negocio = new NAlerta();
    private final NAviso avisos = new NAviso();
    private final ClienteSMTP smtp;

    public PAlertas(ClienteSMTP smtp) {
        this.smtp = smtp;
    }

    /** Revisa las tres alertas. Un error en una no impide las otras. */
    public void revisar() {
        try {
            parrillasEnRiesgo();
        } catch (Exception e) {
            Registro.errorSistema("Alertas - parrilla en riesgo", e);
        }
        try {
            emisionesSinRegistrar();
        } catch (Exception e) {
            Registro.errorSistema("Alertas - emision sin registrar", e);
        }
        try {
            if (NRecordatorio.dentroDeHorario()) {
                rechazadosSinCorregir();
            }
        } catch (Exception e) {
            Registro.errorSistema("Alertas - material rechazado sin corregir", e);
        }
    }

    // ------------------------------------------------------------------ parrilla en riesgo

    private void parrillasEnRiesgo() throws Exception {
        for (LocalDate fecha : negocio.fechasARevisar()) {
            int nivel = negocio.nivelParrilla(fecha);
            if (nivel == 0) {
                continue;
            }
            String tipo = nivel == 2 ? NAlerta.PARRILLA_2 : NAlerta.PARRILLA_1;
            if (avisos.yaEnviado(tipo, fecha.toString())) {
                continue;
            }
            NAlerta.Riesgo riesgo = negocio.evaluar(fecha);
            if (riesgo.problemas.isEmpty()) {
                continue;
            }
            // La parrilla es de Programación, que la lleva el Administrador (en ambos niveles; el 2 es el urgente)
            List<Rol> roles = new ArrayList<>(List.of(Rol.ADMINISTRADOR));
            if (riesgo.conPendientes) {
                roles.add(Rol.ENCARGADO); // Prensa tiene materiales por validar
            }
            List<String> destinos = avisos.emailsDeRoles(null, roles.toArray(new Rol[0]));
            String titulo = (nivel == 2 ? "URGENTE: parrilla del " : "Parrilla en riesgo: ") + fecha;
            String cuerpo = Html.parrafo("La parrilla del <b>" + fecha + "</b> " + (nivel == 2
                    ? "<b>sigue sin estar lista</b> y el día está por empezar." : "todavía no está lista para salir al aire.")
                    + " Pendientes:") + Html.lista(escapar(riesgo.problemas))
                    + Html.parrafo("Revise el detalle con " + Html.codigo("estPar[\"" + fecha + "\"]") + " o " + Html.codigo("mosPar[\"" + fecha + "\"]") + ".");
            List<String> enviados = EnvioAvisos.enviar(smtp, destinos, titulo, cuerpo);
            if (!enviados.isEmpty()) {
                avisos.marcarEnviado(tipo, fecha.toString());
                avisarEnConsola(titulo, enviados);
            }
        }
    }

    // ------------------------------------------------------------------ emision sin registrar

    private void emisionesSinRegistrar() throws Exception {
        List<String[]> pendientes = new ArrayList<>();
        for (String[] b : negocio.emisionesSinRegistrar()) {
            if (!avisos.yaEnviado(NAlerta.EMISION, b[0])) {
                pendientes.add(b);
            }
        }
        if (pendientes.isEmpty()) {
            return;
        }
        List<String[]> filas = new ArrayList<>();
        for (String[] b : pendientes) {
            filas.add(new String[]{Html.esc(b[1]), Html.esc(b[2] + " - " + b[3]), Html.esc(b[4]), Html.esc(b[5]),
                    Html.codigo("insEmi[\"" + b[1] + "\",\"" + b[2] + "\",\"" + b[2] + ":00\",\"" + b[3] + ":00\",\"EMITIDO\"]")});
        }
        String cuerpo = Html.parrafo("Estos bloques ya terminaron y <b>no tienen la emisión registrada</b>. Registre lo que realmente salió al aire "
                + "(ajuste las horas reales y el estado):")
                + Html.tablaHtml(new String[]{"Fecha", "Horario", "Tipo", "Programa", "Registrar"}, filas) + Html.total(pendientes.size());
        String titulo = "Emisión sin registrar (" + pendientes.size() + " bloque" + (pendientes.size() == 1 ? "" : "s") + ")";
        List<String> enviados = EnvioAvisos.enviar(smtp, avisos.emailsDeRoles(null, Rol.PRODUCCION), titulo, cuerpo);
        if (!enviados.isEmpty()) {
            for (String[] b : pendientes) {
                avisos.marcarEnviado(NAlerta.EMISION, b[0]);
            }
            avisarEnConsola(titulo, enviados);
        }
    }

    // ------------------------------------------------------------------ rechazados sin corregir

    private void rechazadosSinCorregir() throws Exception {
        Map<String, List<String[]>> porDestino = new LinkedHashMap<>();
        for (String[] m : negocio.rechazadosSinCorregir()) {
            if (avisos.yaEnviado(NAlerta.RECHAZO, m[0] + ":" + m[5])) {
                continue;
            }
            String destino = avisos.emailDeUsuario(Integer.parseInt(m[6]));
            if (destino != null) {
                porDestino.computeIfAbsent(destino, k -> new ArrayList<>()).add(m);
            }
        }
        for (Map.Entry<String, List<String[]>> e : porDestino.entrySet()) {
            List<String[]> filas = new ArrayList<>();
            for (String[] m : e.getValue()) {
                filas.add(new String[]{Html.esc(m[1]), Html.esc(m[2]), Html.esc(m[4]), Html.esc(m[3])});
            }
            String cuerpo = Html.parrafo("Estos materiales que usted registró fueron <b>rechazados</b> y siguen sin corregirse:")
                    + Html.tablaHtml(new String[]{"Código", "Título", "Rechazado", "Motivo"}, filas)
                    + Html.parrafo("Corríjalos con " + Html.codigo("modMat[...]") + " y pida a Prensa que los apruebe con " + Html.codigo("valMat[...]") + ".");
            String titulo = "Materiales rechazados sin corregir (" + e.getValue().size() + ")";
            if (!EnvioAvisos.enviar(smtp, List.of(e.getKey()), titulo, cuerpo).isEmpty()) {
                for (String[] m : e.getValue()) {
                    avisos.marcarEnviado(NAlerta.RECHAZO, m[0] + ":" + m[5]);
                }
                avisarEnConsola(titulo, List.of(e.getKey()));
            }
        }
    }

    // ------------------------------------------------------------------ apoyo

    private static void avisarEnConsola(String titulo, List<String> destinos) {
        Registro.acceso("ALERTA", titulo + " a=" + String.join(", ", destinos));
        System.out.println("[Alertas] " + titulo + " -> " + String.join(", ", destinos));
    }

    private static List<String> escapar(List<String> textos) {
        List<String> r = new ArrayList<>();
        for (String t : textos) {
            r.add(Html.esc(t));
        }
        return r;
    }
}
