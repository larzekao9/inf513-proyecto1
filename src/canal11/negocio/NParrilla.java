package canal11.negocio;

import canal11.datos.DBloque;
import canal11.datos.DEmision;
import canal11.datos.DEscaleta;
import canal11.datos.DMaterial;
import canal11.datos.DParrilla;
import canal11.datos.DPrograma;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;


public class NParrilla {

    public static final String[] TIPOS_BLOQUE = {"PROGRAMA", "PAUSA_COMERCIAL"};

    private final DParrilla parrillas = new DParrilla();
    private final DBloque bloques = new DBloque();
    private final DPrograma programas = new DPrograma();
    private final DMaterial materiales = new DMaterial();
    private final DEscaleta escaletas = new DEscaleta();
    private final DEmision emisiones = new DEmision();
    private final NAuditoria auditoria = new NAuditoria();

    /** filtro: "*" = todas; AAAA-MM = las de ese mes. */
    public List<String[]> listar(String filtro) throws NegocioException, SQLException {
        Validador v = new Validador();
        String f = v.texto(filtro, "filtro", 10);
        v.verificar();
        if (f.equals("*")) {
            return parrillas.listar(null);
        }
        if (!f.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new NegocioException("El filtro debe ser <b>*</b> (todas) o un mes con formato AAAA-MM, por ejemplo 2026-11 "
                    + "(recibido: '" + Validador.esc(f) + "').");
        }
        return parrillas.listar(f);
    }

    /** Fila de la parrilla de esa fecha (ver DParrilla para las columnas). */
    public String[] mostrar(String fecha) throws NegocioException, SQLException {
        return parrillas.obtener(resolver(fecha(fecha)));
    }

    /** Bloques de la parrilla de esa fecha, ordenados por hora (ver DBloque para las columnas). */
    public List<String[]> bloques(String fecha) throws NegocioException, SQLException {
        return bloques.listarPorParrilla(resolver(fecha(fecha)));
    }

    /** insPar["fecha"]. */
    public void insertar(Usuario quien, String fechaTxt) throws NegocioException, SQLException {
        LocalDate fecha = fecha(fechaTxt);
        if (fecha.isBefore(Reloj.hoy())) {
            throw new NegocioException("No se puede crear la parrilla del <b>" + fecha + "</b>: es una fecha pasada (hoy es "
                    + Reloj.hoy() + ").");
        }
        if (parrillas.buscarId(fecha) != null) {
            throw new NegocioException("Ya existe una parrilla para el <b>" + fecha + "</b>. Consúltela con mosPar[\"" + fecha + "\"].");
        }
        int id = parrillas.insertar(fecha, quien.getId());
        auditoria.registrar(quien, NAuditoria.INSERTAR, "parrilla", id, "Parrilla del " + fecha + " (BORRADOR)", "insPar");
    }

    /** eliPar["fecha"]. */
    public void eliminar(Usuario quien, String fechaTxt) throws NegocioException, SQLException {
        LocalDate fecha = fecha(fechaTxt);
        int id = resolver(fecha);
        exigirBorrador(id, fecha, "eliminarla");
        int spots = bloques.contarSpotsDeParrilla(id);
        if (spots > 0) {
            throw new NegocioException("No se puede eliminar la parrilla del <b>" + fecha + "</b>: tiene " + spots
                    + " spot(s) programado(s). Quítelos primero con eliSpo.");
        }
        int cantidad = bloques.listarPorParrilla(id).size();
        parrillas.eliminar(id);
        auditoria.registrar(quien, NAuditoria.ELIMINAR, "parrilla", id,
                "Parrilla del " + fecha + " con " + cantidad + " bloque(s)", "eliPar");
    }

    /** insBlo["fecha","hora_inicio","hora_fin","tipo","programa (nombre)","material (codigo)"]. */
    public void insertarBloque(Usuario quien, List<String> p) throws NegocioException, SQLException {
        Validador v = new Validador();
        LocalDate fecha = v.fecha(p.get(0), "fecha");
        LocalTime inicio = v.hora(p.get(1), "hora_inicio");
        LocalTime fin = v.horaFin(p.get(2), "hora_fin");
        String tipo = v.opcion(p.get(3), "tipo", TIPOS_BLOQUE);
        String programa = v.textoOpcional(p.get(4), "programa", 120);
        String material = v.textoOpcional(p.get(5), "material", 20);
        if (inicio != null && fin != null && !fin.isAfter(inicio)) {
            v.error("La <b>hora_fin</b> (" + fin + ") debe ser posterior a la <b>hora_inicio</b> (" + inicio + ").");
        }
        if ("PROGRAMA".equals(tipo) && programa == null) {
            v.error("Un bloque de tipo <b>PROGRAMA</b> necesita el nombre del programa.");
        }
        if ("PAUSA_COMERCIAL".equals(tipo) && (programa != null || material != null)) {
            v.error("Una <b>PAUSA_COMERCIAL</b> no lleva programa ni material (envíe \"\" en ambos); "
                    + "sus spots se insertan después con insSpo.");
        }
        v.verificar();

        int idParrilla = resolver(fecha);
        exigirBorrador(idParrilla, fecha, "agregarle bloques");
        String solape = bloques.buscarSolape(idParrilla, inicio, fin);
        if (solape != null) {
            v.error("El horario " + inicio + "-" + fin + " se solapa con el bloque existente " + recortar(solape) + ".");
        }
        Integer idPrograma = null;
        if (programa != null) {
            idPrograma = programas.buscarId(programa);
            if (idPrograma == null) {
                v.error("No existe el programa <b>" + Validador.esc(programa) + "</b>. Consulte los programas con lisPro[\"*\"].");
            } else if (!programas.estaActivo(idPrograma)) {
                v.error("El programa <b>" + Validador.esc(programa) + "</b> está dado de baja.");
            }
        }
        Integer idMaterial = null;
        if (material != null) {
            idMaterial = materiales.buscarId(material);
            if (idMaterial == null) {
                v.error("No existe el material <b>" + Validador.esc(material) + "</b>. Consulte los materiales con lisMat[\"*\"].");
            } else if (!materiales.estaActivo(idMaterial)) {
                v.error("El material <b>" + Validador.esc(material) + "</b> está dado de baja.");
            } else {
                validarMaterialDelBloque(v, materiales.obtener(idMaterial), programa, inicio, fin);
            }
        }
        v.verificar();

        int id = bloques.insertar(idParrilla, idPrograma, idMaterial, inicio, fin, tipo);
        auditoria.registrar(quien, NAuditoria.INSERTAR, "bloque", id,
                fecha + " " + inicio + "-" + fin + " " + tipo + (programa != null ? " " + programa : ""), "insBlo");
    }

    /**
     * El material de un bloque PROGRAMA debe corresponder al bloque: no puede ser un SPOT (esos van en una pausa con insSpo),
     * si tiene programa debe ser el del bloque, y no puede durar mas que el bloque.
     * (Un material mas corto que el bloque si se permite: el hueco lo ve el equipo en estPar/conPar.)
     */
    private void validarMaterialDelBloque(Validador v, String[] m, String programa, LocalTime inicio, LocalTime fin) {
        String codigo = Validador.esc(m[1]);
        if ("SPOT".equals(m[3])) {
            v.error("El material <b>" + codigo + "</b> es un SPOT: los spots se ubican en una PAUSA_COMERCIAL con insSpo, no en un bloque PROGRAMA.");
        }
        if (programa != null && !m[4].isEmpty() && !m[4].equalsIgnoreCase(programa.trim())) {
            v.error("El material <b>" + codigo + "</b> pertenece al programa <b>" + Validador.esc(m[4])
                    + "</b>, pero el bloque es de <b>" + Validador.esc(programa) + "</b>.");
        }
        long duracionBloque = Duration.between(inicio, fin).getSeconds();
        int duracionMaterial = Integer.parseInt(m[5]);
        if (duracionMaterial > duracionBloque) {
            v.error("El material <b>" + codigo + "</b> dura " + duracionMaterial + " s y el bloque solo " + duracionBloque
                    + " s (" + inicio + "-" + fin + "): no cabe.");
        }
    }

    /** eliBlo["fecha","hora_inicio"]. */
    public void eliminarBloque(Usuario quien, List<String> p) throws NegocioException, SQLException {
        Validador v = new Validador();
        LocalDate fecha = v.fecha(p.get(0), "fecha");
        LocalTime inicio = v.hora(p.get(1), "hora_inicio");
        v.verificar();

        int idParrilla = resolver(fecha);
        exigirBorrador(idParrilla, fecha, "quitarle bloques");
        Integer idBloque = bloques.buscarId(idParrilla, inicio);
        if (idBloque == null) {
            throw new NegocioException("No hay un bloque que empiece a las <b>" + inicio + "</b> en la parrilla del " + fecha
                    + ". Vea los bloques con mosPar[\"" + fecha + "\"].");
        }
        int spots = bloques.contarSpotsDeBloque(idBloque);
        if (spots > 0) {
            throw new NegocioException("No se puede quitar el bloque de las " + inicio + ": tiene " + spots
                    + " spot(s) programado(s). Quítelos primero con eliSpo.");
        }
        String[] b = bloques.obtener(idBloque);
        bloques.eliminar(idBloque);
        auditoria.registrar(quien, NAuditoria.ELIMINAR, "bloque", idBloque,
                fecha + " " + b[1] + "-" + b[2] + " " + b[3] + (b[4].isEmpty() ? "" : " " + b[4]), "eliBlo");
    }

    /**
     * pubPar["fecha"]. Revisa y publica la parrilla. Devuelve los AVISOS (no impiden publicar);
     * si hay ERRORES lanza NegocioException con todos juntos y no publica.
     */
    public List<String> publicar(Usuario quien, String fechaTxt) throws NegocioException, SQLException {
        LocalDate fecha = fecha(fechaTxt);
        if (fecha.isBefore(Reloj.hoy())) {
            throw new NegocioException("No se puede publicar la parrilla del <b>" + fecha + "</b>: es una fecha pasada (hoy es "
                    + Reloj.hoy() + ").");
        }
        int id = resolver(fecha);
        exigirBorrador(id, fecha, "publicarla de nuevo");
        List<String[]> lista = bloques.listarPorParrilla(id);

        Validador v = new Validador();
        List<String> avisos = new ArrayList<>();
        if (lista.isEmpty()) {
            v.error("La parrilla del <b>" + fecha + "</b> no tiene bloques. Agregue bloques con insBlo antes de publicar.");
        }
        LocalTime cursor = LocalTime.MIDNIGHT;
        long sinProgramar = 0;
        for (String[] b : lista) {
            LocalTime inicio = LocalTime.parse(b[1]);
            LocalTime fin = LocalTime.parse(b[2]);
            String horario = recortar(b[1]) + "-" + recortar(b[2]);
            if ("PROGRAMA".equals(b[3])) {
                int notas = Integer.parseInt(b[9]);
                if (notas > 0) { // bloque con escaleta: cada nota debe estar activa y APROBADA
                    List<String> noListas = escaletas.notasNoListas(Integer.parseInt(b[0]));
                    if (!noListas.isEmpty()) {
                        v.error("La escaleta del bloque " + horario + " tiene notas que no están APROBADAS: "
                                + Validador.esc(String.join(", ", noListas)) + ".");
                    }
                } else if (b[5].isEmpty()) {
                    v.error("El bloque " + horario + " (" + Validador.esc(b[4]) + ") no tiene material asignado.");
                } else if ("No".equals(b[7])) {
                    v.error("El bloque " + horario + " usa el material <b>" + Validador.esc(b[5]) + "</b>, que está dado de baja.");
                } else if (!"APROBADO".equals(b[6])) {
                    v.error("El bloque " + horario + " usa el material <b>" + Validador.esc(b[5]) + "</b>, que está <b>" + b[6]
                            + "</b> (debe estar APROBADO).");
                }
            }
            if (inicio.isAfter(cursor)) {
                avisos.add("Hueco sin programación de " + etiqueta(cursor) + " a " + etiqueta(inicio) + ".");
                sinProgramar += Duration.between(cursor, inicio).getSeconds();
            }
            if (fin.isAfter(cursor)) {
                cursor = fin;
            }
        }
        if (cursor.isBefore(Validador.FIN_DEL_DIA)) { // canal 24/7: tambien cuenta lo que falta hasta el final del dia
            avisos.add("Hueco sin programación de " + etiqueta(cursor) + " a 24:00.");
            sinProgramar += Duration.between(cursor, Validador.FIN_DEL_DIA).getSeconds() + 1;
        }
        if (sinProgramar > 0) {
            long cubierto = 86400 - sinProgramar;
            avisos.add(0, "Cobertura del día: " + (cubierto * 100 / 86400) + " % programado; faltan "
                    + (sinProgramar / 3600) + " h " + (sinProgramar % 3600 / 60) + " min sin programar (el canal emite 24 h).");
        }
        v.verificar();

        parrillas.publicar(id);
        auditoria.registrar(quien, NAuditoria.PUBLICAR, "parrilla", id,
                "Parrilla del " + fecha + " publicada con " + lista.size() + " bloque(s) y " + avisos.size() + " aviso(s)", "pubPar");
        return avisos;
    }

    /**
     * camBlo["fecha","hora_inicio","material (codigo)"]. Cambia el material de un bloque PROGRAMA, aunque la parrilla ya este
     * PUBLICADA, mientras el bloque no haya empezado. Devuelve {anterior, nuevo, estado de la parrilla, programa, hora inicio, hora fin}.
     */
    public String[] cambiarMaterialBloque(Usuario quien, List<String> p) throws NegocioException, SQLException {
        Validador v = new Validador();
        LocalDate fecha = v.fecha(p.get(0), "fecha");
        LocalTime inicio = v.hora(p.get(1), "hora_inicio");
        String codigo = v.texto(p.get(2), "material", 20);
        v.verificar();

        int idParrilla = resolver(fecha);
        Integer idBloque = bloques.buscarId(idParrilla, inicio);
        if (idBloque == null) {
            throw new NegocioException("No hay un bloque que empiece a las <b>" + inicio + "</b> en la parrilla del " + fecha
                    + ". Vea los bloques con mosPar[\"" + fecha + "\"].");
        }
        String[] b = bloques.obtener(idBloque);
        if (!"PROGRAMA".equals(b[3])) {
            throw new NegocioException("El bloque de las <b>" + inicio + "</b> es una pausa comercial: no lleva material. "
                    + "Sus spots se cambian con eliSpo e insSpo.");
        }
        if (Integer.parseInt(b[9]) > 0) {
            throw new NegocioException("El bloque de las <b>" + inicio + "</b> tiene una escaleta de " + b[9] + " nota(s): "
                    + "el orden y las notas se cambian con insLea, modLea y eliLea.");
        }
        LocalTime horaInicio = LocalTime.parse(b[1]);
        LocalTime horaFin = LocalTime.parse(b[2]);
        if (!LocalDateTime.of(fecha, horaInicio).isAfter(Reloj.ahora())) {
            throw new NegocioException("El bloque de las <b>" + inicio + "</b> del " + fecha
                    + " ya empezó: no se puede cambiar su material. Registre lo ocurrido con insEmi.");
        }
        if (emisiones.existeParaBloque(idBloque)) {
            throw new NegocioException("El bloque de las <b>" + inicio + "</b> ya tiene su emisión registrada: no se puede cambiar.");
        }

        Integer idMaterial = materiales.buscarId(codigo);
        if (idMaterial == null) {
            throw new NegocioException("No existe el material <b>" + Validador.esc(codigo) + "</b>. Consulte los materiales con lisMat[\"*\"].");
        }
        String[] m = materiales.obtener(idMaterial);
        if (!materiales.estaActivo(idMaterial)) {
            v.error("El material <b>" + Validador.esc(m[1]) + "</b> está dado de baja.");
        } else if (!"APROBADO".equals(m[8])) {
            v.error("El material <b>" + Validador.esc(m[1]) + "</b> está <b>" + m[8] + "</b>: solo se puede usar uno APROBADO.");
        }
        if (m[1].equalsIgnoreCase(b[5])) {
            v.error("El bloque de las " + inicio + " ya usa el material <b>" + Validador.esc(m[1]) + "</b>.");
        }
        validarMaterialDelBloque(v, m, b[4], horaInicio, horaFin);
        v.verificar();

        bloques.cambiarMaterial(idBloque, idMaterial);
        String estadoParrilla = parrillas.estado(idParrilla);
        String anterior = b[5].isEmpty() ? "(sin material)" : b[5];
        auditoria.registrar(quien, NAuditoria.MODIFICAR, "bloque", idBloque,
                fecha + " " + b[1] + " " + b[4] + ": material " + anterior + " -> " + m[1] + " (parrilla " + estadoParrilla + ")", "camBlo");
        return new String[]{anterior, m[1], estadoParrilla, b[4], b[1], b[2]};
    }

    // ------------------------------------------------------------------ auxiliares

    /** 23:59:59 se muestra como 24:00 (fin del dia). */
    private static String etiqueta(LocalTime hora) {
        return hora.equals(Validador.FIN_DEL_DIA) ? "24:00" : recortar(hora.toString().length() == 5 ? hora + ":00" : hora.toString());
    }

    private LocalDate fecha(String texto) throws NegocioException {
        Validador v = new Validador();
        LocalDate fecha = v.fecha(texto, "fecha");
        v.verificar();
        return fecha;
    }

    private int resolver(LocalDate fecha) throws NegocioException, SQLException {
        Integer id = parrillas.buscarId(fecha);
        if (id == null) {
            throw new NegocioException("No existe una parrilla para el <b>" + fecha + "</b>. "
                    + "Créela con insPar[\"" + fecha + "\"] o consulte las existentes con lisPar[\"*\"].");
        }
        return id;
    }

    private void exigirBorrador(int idParrilla, LocalDate fecha, String accion) throws NegocioException, SQLException {
        if (!"BORRADOR".equals(parrillas.estado(idParrilla))) {
            throw new NegocioException("La parrilla del <b>" + fecha + "</b> ya está <b>PUBLICADA</b>: no se puede " + accion + ".");
        }
    }

    /** "20:00:00-21:00:00" -> "20:00-21:00"; "20:00:00" -> "20:00". */
    private static String recortar(String horario) {
        return horario.replace(":00-", "-").replaceAll(":00$", "");
    }
}
