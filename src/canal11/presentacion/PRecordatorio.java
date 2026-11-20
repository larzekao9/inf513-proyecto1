package canal11.presentacion;

import canal11.comunicacion.ClienteSMTP;
import canal11.config.Registro;
import canal11.negocio.NRecordatorio;

import java.util.ArrayList;
import java.util.List;

/**
 * Avisos automaticos por falta de revision (no es un comando: lo dispara el hilo de recordatorios de App).
 * Arma el correo HTML con los materiales vencidos y lo envia por SMTP; solo si se envio, lo confirma en la BD.
 */
public class PRecordatorio {

    private final NRecordatorio negocio = new NRecordatorio();
    private final ClienteSMTP smtp;

    public PRecordatorio(ClienteSMTP smtp) {
        this.smtp = smtp;
    }

    /** Revisa los plazos y envia los avisos que correspondan. Devuelve cuantos materiales se avisaron. */
    public int revisar() {
        if (!negocio.habilitado() || !NRecordatorio.dentroDeHorario()) {
            return 0;
        }
        int avisados = 0;
        // Primero el escalamiento: un material muy atrasado no recibe antes el recordatorio.
        for (int nivel : new int[]{NRecordatorio.ESCALAMIENTO, NRecordatorio.RECORDATORIO}) {
            try {
                avisados += avisar(nivel);
            } catch (Exception e) {
                System.err.println("[Recordatorios] Error en el nivel " + nivel + ": " + e);
                Registro.errorSistema("Recordatorios - nivel " + nivel, e);
            }
        }
        return avisados;
    }

    private int avisar(int nivel) throws Exception {
        List<String[]> vencidos = negocio.vencidos(nivel);
        if (vencidos.isEmpty()) {
            return 0;
        }
        List<String> destinos = negocio.destinatarios(nivel);
        if (destinos.isEmpty()) {
            Registro.errorSistema("Recordatorios - no hay usuarios activos a quienes avisar (nivel " + nivel + ")");
            return 0;
        }
        boolean escalar = nivel == NRecordatorio.ESCALAMIENTO;
        String titulo = escalar ? "Escalamiento: materiales sin revisar" : "Recordatorio: materiales pendientes de revisión";

        List<String> enviados = EnvioAvisos.enviar(smtp, destinos, titulo, cuerpo(vencidos, escalar));
        if (enviados.isEmpty()) {
            return 0; // no se marca nada: se reintenta en la siguiente vuelta
        }
        negocio.confirmar(nivel, vencidos, enviados);
        Registro.acceso(escalar ? "ESCALAMIENTO" : "RECORDATORIO",
                vencidos.size() + " material(es) a=" + String.join(", ", enviados));
        System.out.println("[Recordatorios] " + (escalar ? "Escalamiento" : "Recordatorio") + ": " + vencidos.size()
                + " material(es) -> " + String.join(", ", enviados));
        return vencidos.size();
    }

    private static String cuerpo(List<String[]> vencidos, boolean escalar) {
        List<String[]> filas = new ArrayList<>();
        for (String[] m : vencidos) {
            filas.add(new String[]{Html.esc(m[1]), Html.esc(m[2]), Html.esc(m[3]), Html.esc(m[4]), Html.esc(m[5]),
                    Html.esc(duracion(Integer.parseInt(m[6]))), Html.codigo("mosMat[\"" + m[1] + "\"]")});
        }
        String intro = escalar
                ? "Estos materiales siguen <b>sin revisar</b> pese al recordatorio enviado a Prensa. Se avisa a la administración."
                : "Estos materiales llevan más tiempo del permitido <b>pendientes de revisión</b>. Prensa debe aprobarlos o rechazarlos.";
        return Html.parrafo(intro)
                + Html.tablaHtml(new String[]{"Código", "Título", "Tipo", "Registrado por", "Pendiente desde", "Tiempo", "Ver detalle"}, filas)
                + Html.total(vencidos.size())
                + Html.info("Para decidir: " + Html.codigo("valMat[\"código\",\"APROBADO\",\"\"]") + " o "
                + Html.codigo("valMat[\"código\",\"RECHAZADO\",\"motivo\"]") + ". Lista completa: "
                + Html.codigo("lisMat[\"PENDIENTE\"]"));
    }

    private static String duracion(int minutos) {
        return minutos >= 120 ? (minutos / 60) + " h " + (minutos % 60) + " min" : minutos + " min";
    }
}
