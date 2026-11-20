package canal11.negocio;

import canal11.datos.DBloque;
import canal11.datos.DEscaleta;
import canal11.datos.DMaterial;
import canal11.datos.DParrilla;
import canal11.datos.DPrograma;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Escaleta del noticiero (lisLea, insLea, modLea, eliLea): el orden de las notas ("lead 1 / video 1") dentro de un bloque
 * de un programa NOTICIERO o REVISTA. Es el orden de transmision que Prensa mandaba por WhatsApp; ahora queda en el sistema
 * y se puede cambiar hasta que el bloque empiece. Reglas de negocio:
 *  - La nota es un material activo y APROBADO, que no sea SPOT, del mismo programa del bloque (si tiene programa).
 *  - Una nota aparece una sola vez por bloque; el orden va de 1 a n sin huecos (al insertar o mover se recorren las demas).
 *  - La suma de las duraciones de las notas no puede superar la duracion del bloque.
 *  - Se puede cambiar mientras el bloque no haya empezado (tambien con la parrilla PUBLICADA: el cambio se avisa a Produccion).
 *  - Cada alta, cambio y baja queda en la auditoria. lisLea muestra la hora estimada de entrada de cada nota
 *    (inicio del bloque + duracion de las notas anteriores), para que Programacion y Produccion sepan a que hora entra cada una.
 */
public class NLead {

    private static final String[] PROGRAMAS_DE_PRENSA = {"NOTICIERO", "REVISTA"};

    private final DParrilla parrillas = new DParrilla();
    private final DBloque bloques = new DBloque();
    private final DEscaleta escaletas = new DEscaleta();
    private final DMaterial materiales = new DMaterial();
    private final DPrograma programas = new DPrograma();
    private final NAuditoria auditoria = new NAuditoria();

    /** Bloque ya resuelto: de que parrilla es y sus datos (columnas de DBloque). */
    private static final class Contexto {
        LocalDate fecha;
        int idParrilla;
        int idBloque;
        String[] bloque;
    }

    /** lisLea["fecha","hora_bloque"]. Columnas: 0 orden, 1 hora estimada de entrada, 2 codigo, 3 titulo, 4 duracion_seg, 5 estado, 6 registrado por. */
    public List<String[]> listar(String fecha, String hora) throws NegocioException, SQLException {
        Contexto c = contexto(fecha, hora);
        LocalTime entrada = LocalTime.parse(c.bloque[1]);
        List<String[]> filas = new ArrayList<>();
        for (String[] n : escaletas.listar(c.idBloque)) {
            String estado = "No".equals(n[5]) ? "DE BAJA" : n[4];
            filas.add(new String[]{n[0], entrada.toString().length() == 5 ? entrada + ":00" : entrada.toString(),
                    n[1], n[2], n[3], estado, n[6]});
            entrada = entrada.plusSeconds(Integer.parseInt(n[3]));
        }
        return filas;
    }

    /** Datos del bloque de la escaleta (columnas de DBloque): programa, horario, material... */
    public String[] bloque(String fecha, String hora) throws NegocioException, SQLException {
        return contexto(fecha, hora).bloque;
    }

    /** true si la parrilla de esa fecha ya esta PUBLICADA (los cambios se avisan a Produccion). */
    public boolean parrillaPublicada(String fecha) throws NegocioException, SQLException {
        Validador v = new Validador();
        LocalDate f = v.fecha(fecha, "fecha");
        v.verificar();
        Integer id = parrillas.buscarId(f);
        return id != null && "PUBLICADA".equals(parrillas.estado(id));
    }

    /** insLea["fecha","hora_bloque","orden","material (codigo)"]. Devuelve el orden en que quedo la nota. */
    public int insertar(Usuario quien, List<String> p) throws NegocioException, SQLException {
        Validador v = new Validador();
        v.fecha(p.get(0), "fecha");
        v.hora(p.get(1), "hora_bloque");
        Integer orden = v.entero(p.get(2), "orden", 1, 500);
        String codigo = v.texto(p.get(3), "material", 20);
        v.verificar();

        Contexto c = contexto(p.get(0), p.get(1));
        exigirNoIniciado(c);
        int cantidad = escaletas.listar(c.idBloque).size();
        if (orden > cantidad + 1) {
            v.error("El <b>orden</b> " + orden + " no es válido: la escaleta tiene " + cantidad + " nota(s), el siguiente orden es <b>"
                    + (cantidad + 1) + "</b>.");
        }
        Integer idMaterial = materiales.buscarId(codigo);
        String[] m = null;
        if (idMaterial == null) {
            v.error("No existe el material <b>" + Validador.esc(codigo) + "</b>. Consulte los materiales con lisMat[\"*\"].");
        } else {
            m = materiales.obtener(idMaterial);
            if (!materiales.estaActivo(idMaterial)) {
                v.error("El material <b>" + Validador.esc(m[1]) + "</b> está dado de baja.");
            } else if (!"APROBADO".equals(m[8])) {
                v.error("El material <b>" + Validador.esc(m[1]) + "</b> está <b>" + m[8] + "</b>: solo las notas APROBADAS entran a la escaleta.");
            }
            if ("SPOT".equals(m[3])) {
                v.error("El material <b>" + Validador.esc(m[1]) + "</b> es un SPOT: los spots van en una pausa comercial con insSpo.");
            }
            if (!m[4].isEmpty() && !m[4].equalsIgnoreCase(c.bloque[4])) {
                v.error("El material <b>" + Validador.esc(m[1]) + "</b> pertenece al programa <b>" + Validador.esc(m[4])
                        + "</b>, pero el bloque es de <b>" + Validador.esc(c.bloque[4]) + "</b>.");
            }
            if (escaletas.ordenDe(c.idBloque, idMaterial) != null) {
                v.error("La nota <b>" + Validador.esc(m[1]) + "</b> ya está en la escaleta de este bloque (use modLea para cambiar su orden).");
            }
        }
        v.verificar();

        long duracionBloque = java.time.Duration.between(LocalTime.parse(c.bloque[1]), LocalTime.parse(c.bloque[2])).getSeconds();
        long total = (long) escaletas.duracionTotal(c.idBloque) + Integer.parseInt(m[5]);
        if (total > duracionBloque) {
            throw new NegocioException("Con la nota <b>" + Validador.esc(m[1]) + "</b> la escaleta dura " + total + " s y el bloque solo "
                    + duracionBloque + " s (" + c.bloque[1] + "-" + c.bloque[2] + "): no cabe.");
        }

        int id = escaletas.insertar(c.idBloque, orden, idMaterial, quien.getId());
        auditoria.registrar(quien, NAuditoria.INSERTAR, "escaleta", id,
                c.fecha + " " + c.bloque[1] + " " + c.bloque[4] + ": nota " + m[1] + " en el orden " + orden, "insLea");
        return orden;
    }

    /** modLea["fecha","hora_bloque","material (codigo)","nuevo_orden"]. Devuelve el orden anterior. */
    public int mover(Usuario quien, List<String> p) throws NegocioException, SQLException {
        Validador v = new Validador();
        v.fecha(p.get(0), "fecha");
        v.hora(p.get(1), "hora_bloque");
        String codigo = v.texto(p.get(2), "material", 20);
        Integer nuevo = v.entero(p.get(3), "nuevo_orden", 1, 500);
        v.verificar();

        Contexto c = contexto(p.get(0), p.get(1));
        exigirNoIniciado(c);
        int idMaterial = materialDeLaEscaleta(c, codigo);
        int actual = escaletas.ordenDe(c.idBloque, idMaterial);
        int cantidad = escaletas.listar(c.idBloque).size();
        if (nuevo > cantidad) {
            throw new NegocioException("El <b>nuevo_orden</b> " + nuevo + " no es válido: la escaleta tiene " + cantidad + " nota(s).");
        }
        if (nuevo == actual) {
            throw new NegocioException("La nota <b>" + Validador.esc(codigo) + "</b> ya está en el orden " + actual + ".");
        }
        escaletas.mover(c.idBloque, idMaterial, actual, nuevo);
        auditoria.registrar(quien, NAuditoria.MODIFICAR, "escaleta", c.idBloque,
                c.fecha + " " + c.bloque[1] + " " + c.bloque[4] + ": nota " + codigo.toUpperCase() + " del orden " + actual + " al " + nuevo, "modLea");
        return actual;
    }

    /** eliLea["fecha","hora_bloque","material (codigo)"]. Devuelve el orden que tenia la nota. */
    public int eliminar(Usuario quien, List<String> p) throws NegocioException, SQLException {
        Validador v = new Validador();
        v.fecha(p.get(0), "fecha");
        v.hora(p.get(1), "hora_bloque");
        String codigo = v.texto(p.get(2), "material", 20);
        v.verificar();

        Contexto c = contexto(p.get(0), p.get(1));
        exigirNoIniciado(c);
        int idMaterial = materialDeLaEscaleta(c, codigo);
        int orden = escaletas.ordenDe(c.idBloque, idMaterial);
        escaletas.eliminar(c.idBloque, idMaterial, orden);
        auditoria.registrar(quien, NAuditoria.ELIMINAR, "escaleta", c.idBloque,
                c.fecha + " " + c.bloque[1] + " " + c.bloque[4] + ": se quita la nota " + codigo.toUpperCase() + " (orden " + orden + ")", "eliLea");
        return orden;
    }

    // ------------------------------------------------------------------ auxiliares

    /** Resuelve fecha + hora del bloque y exige que sea un bloque de noticiero o revista. */
    private Contexto contexto(String fechaTxt, String horaTxt) throws NegocioException, SQLException {
        Validador v = new Validador();
        LocalDate fecha = v.fecha(fechaTxt, "fecha");
        LocalTime hora = v.hora(horaTxt, "hora_bloque");
        v.verificar();
        Integer idParrilla = parrillas.buscarId(fecha);
        if (idParrilla == null) {
            throw new NegocioException("No existe una parrilla para el <b>" + fecha + "</b>. Consulte las parrillas con lisPar[\"*\"].");
        }
        Integer idBloque = bloques.buscarId(idParrilla, hora);
        if (idBloque == null) {
            throw new NegocioException("No hay un bloque que empiece a las <b>" + hora + "</b> en la parrilla del " + fecha
                    + ". Vea los bloques con mosPar[\"" + fecha + "\"].");
        }
        Contexto c = new Contexto();
        c.fecha = fecha;
        c.idParrilla = idParrilla;
        c.idBloque = idBloque;
        c.bloque = bloques.obtener(idBloque);
        boolean deNoticias = false;
        if ("PROGRAMA".equals(c.bloque[3]) && !c.bloque[4].isEmpty()) {
            Integer idPrograma = programas.buscarId(c.bloque[4]);
            String tipo = idPrograma == null ? "" : programas.obtener(idPrograma)[3];
            for (String t : PROGRAMAS_DE_PRENSA) {
                deNoticias |= t.equals(tipo);
            }
        }
        if (!deNoticias) {
            throw new NegocioException("El bloque de las <b>" + hora + "</b> del " + fecha + " no es de un noticiero ni de una revista: "
                    + "la escaleta solo se arma en bloques de programas tipo NOTICIERO o REVISTA.");
        }
        return c;
    }

    private void exigirNoIniciado(Contexto c) throws NegocioException {
        if (!LocalDateTime.of(c.fecha, LocalTime.parse(c.bloque[1])).isAfter(Reloj.ahora())) {
            throw new NegocioException("El bloque de las <b>" + c.bloque[1] + "</b> del " + c.fecha
                    + " ya empezó: la escaleta ya no se puede cambiar.");
        }
    }

    private int materialDeLaEscaleta(Contexto c, String codigo) throws NegocioException, SQLException {
        Integer idMaterial = materiales.buscarId(codigo);
        if (idMaterial == null || escaletas.ordenDe(c.idBloque, idMaterial) == null) {
            throw new NegocioException("La nota <b>" + Validador.esc(codigo) + "</b> no está en la escaleta del bloque de las "
                    + c.bloque[1] + ". Véala con lisLea[\"" + c.fecha + "\",\"" + c.bloque[1].substring(0, 5) + "\"].");
        }
        return idMaterial;
    }
}
