package canal11.presentacion;

import canal11.config.Registro;
import canal11.datos.DMaterial;
import canal11.negocio.NAviso;
import canal11.negocio.NMaterial;
import canal11.negocio.NParrilla;
import canal11.negocio.NValidacion;
import canal11.negocio.Usuario;

import java.util.ArrayList;
import java.util.List;

/**
 * CU5 - Capa de presentacion de la validacion: lee los parametros, llama a NValidacion y arma el HTML.
 * Sin SQL ni reglas de negocio.
 */
public class PValidacion {

    private final NValidacion negocio = new NValidacion();
    private final NMaterial materiales = new NMaterial();
    private final NParrilla parrillas = new NParrilla();
    private final NAviso avisos = new NAviso();
    private final DMaterial datosMaterial = new DMaterial();

    /** valMat["codigo","estado","observacion"] */
    public Respuesta validar(Usuario u, List<String> p) throws Exception {
        int id = negocio.validar(u, p);
        String[] f = materiales.mostrar(String.valueOf(id));
        boolean aprobado = "APROBADO".equals(f[8]);
        String aviso = aprobado
                ? Html.exito("El material <b>" + Html.esc(f[1]) + "</b> fue <b>APROBADO</b>. Ya puede usarse en la parrilla y en spots.")
                : Html.error("El material <b>" + Html.esc(f[1]) + "</b> fue <b>RECHAZADO</b>: " + Html.esc(f[9]));
        String siguiente = Html.subtitulo("Siguientes pasos")
                + Html.parrafo("Materiales pendientes: " + Html.codigo("lisMat[\"PENDIENTE\"]"))
                + (aprobado ? "" : Html.parrafo("Tras corregirlo, modifique el material " + Html.codigo("mosMat[\"" + f[1] + "\"]")
                + " y apruébelo con " + Html.codigo("valMat[\"" + f[1] + "\",\"APROBADO\",\"Corregido\"]")));
        try {
            avisarAlRegistrante(u, id, f, aprobado);
        } catch (Exception e) { // la validacion ya se guardo: un aviso que falla no la convierte en error
            Registro.errorSistema("Aviso de validacion del material " + f[1], e);
        }
        return new Respuesta(aprobado ? "Material aprobado" : "Material rechazado",
                aviso + Html.ficha(PMaterial.COLUMNAS, f) + siguiente);
    }

    /** Quien registro el material se entera del resultado sin tener que consultar (antes el rechazo solo lo veia Prensa). */
    private void avisarAlRegistrante(Usuario validador, int idMaterial, String[] f, boolean aprobado) throws Exception {
        if (!Notificador.activo()) {
            return;
        }
        String destino = avisos.emailDeUsuario(datosMaterial.registradoPor(idMaterial));
        if (destino == null || destino.equalsIgnoreCase(validador.getEmail())) {
            return;
        }
        String titulo = (aprobado ? "Material aprobado: " : "Material rechazado: ") + f[1];
        String cuerpo = Html.parrafo(Html.esc(validador.getNombre()) + " (" + Html.esc(validador.getRol().getNombre()) + ") "
                + (aprobado ? "<b>aprobó</b> su material <b>" + Html.esc(f[1]) + "</b>. Ya puede usarse en la parrilla y en spots."
                : "<b>rechazó</b> su material <b>" + Html.esc(f[1]) + "</b>."))
                + (aprobado ? "" : Html.error("<b>Motivo:</b> " + Html.esc(f[9]))
                + Html.parrafo("Corríjalo con " + Html.codigo("modMat[...]") + " y avise a Prensa para que lo apruebe de nuevo."))
                + Html.ficha(PMaterial.COLUMNAS, f);
        Notificador.enviar(List.of(destino), titulo, cuerpo);
    }

    /** estPar["fecha"] */
    public Respuesta estadoParrilla(Usuario u, List<String> p) throws Exception {
        String[] parrilla = parrillas.mostrar(p.get(0));
        String fecha = parrilla[1];
        List<String[]> bloques = negocio.estadoDeParrilla(p.get(0));

        int programas = 0;
        int listos = 0;
        List<String[]> tabla = new ArrayList<>();
        for (String[] b : bloques) {
            String estado = b[5];
            if (!NValidacion.NO_APLICA.equals(estado)) {
                programas++;
            }
            if (NValidacion.LISTO.equals(estado)) {
                listos++;
            }
            tabla.add(new String[]{
                    Html.esc(hora(b[0]) + " - " + hora(b[1])),
                    Html.esc(b[2]),
                    Html.esc(b[3]),
                    Html.esc(b[4]),
                    insignia(estado),
                    Html.esc(b[6]),
                    siguientePaso(fecha, b)});
        }

        String resumen;
        if (bloques.isEmpty()) {
            resumen = Html.info("La parrilla del <b>" + fecha + "</b> no tiene bloques todavía. Agréguelos con "
                    + Html.codigo("insBlo[\"" + fecha + "\",\"20:00\",\"21:00\",\"PROGRAMA\",\"programa\",\"CODIGO\"]"));
        } else if (programas == 0) {
            resumen = Html.info("La parrilla solo tiene pausas comerciales; no hay bloques de programa que validar.");
        } else if (listos == programas) {
            resumen = Html.exito("<b>" + listos + " de " + programas + "</b> bloques de programa listos: todo está aprobado para salir al aire.");
        } else {
            resumen = Html.error("<b>" + listos + " de " + programas + "</b> bloques de programa listos. "
                    + "Faltan " + (programas - listos) + " por resolver.");
        }
        String cabecera = Html.parrafo("Parrilla del <b>" + fecha + "</b> (" + Html.esc(parrilla[2]) + ")");
        return new Respuesta("Estado de emisión " + fecha, cabecera + resumen
                + Html.tablaHtml(new String[]{"Horario", "Tipo", "Programa", "Material", "Estado", "Observación", "Siguiente paso"}, tabla));
    }

    // ------------------------------------------------------------------ auxiliares

    private static String siguientePaso(String fecha, String[] b) {
        if (b[4].startsWith("ESCALETA")) { // las notas se resuelven una a una desde la escaleta
            return NValidacion.LISTO.equals(b[5]) ? "" : "Revise las notas: " + Html.codigo("lisLea[\"" + fecha + "\",\"" + hora(b[0]) + "\"]");
        }
        switch (b[5]) {
            case "PENDIENTE":
                return Html.codigo("valMat[\"" + b[4] + "\",\"APROBADO\",\"observación\"]");
            case "RECHAZADO":
                return Html.codigo("modMat[\"" + b[4] + "\", ...]") + " y luego " + Html.codigo("valMat[\"" + b[4] + "\",\"APROBADO\",\"Corregido\"]");
            case NValidacion.SIN_MATERIAL:
            case NValidacion.MATERIAL_DE_BAJA:
                return "Quite el bloque " + Html.codigo("eliBlo[\"" + fecha + "\",\"" + hora(b[0]) + "\"]") + " y agréguelo con un material aprobado";
            default:
                return "";
        }
    }

    /** Estado con color: verde LISTO, rojo RECHAZADO / sin material, naranja PENDIENTE, gris NO APLICA. */
    private static String insignia(String estado) {
        String color;
        switch (estado) {
            case NValidacion.LISTO:
                color = "#2e7d32";
                break;
            case NValidacion.NO_APLICA:
                color = "#6b7785";
                break;
            case "PENDIENTE":
                color = "#b26a00";
                break;
            default:
                color = "#c62828";
        }
        return "<b style='color:" + color + ";'>" + Html.esc(estado) + "</b>";
    }

    /** "20:00:00" -> "20:00"; "20:00:30" se deja igual. */
    private static String hora(String h) {
        if (h.equals("23:59:59")) {
            return "24:00"; // fin del dia
        }
        return h.endsWith(":00") && h.length() == 8 ? h.substring(0, 5) : h;
    }
}
