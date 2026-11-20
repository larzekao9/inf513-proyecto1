package canal11.negocio;

import canal11.datos.DMaterial;
import canal11.datos.DPrograma;

import java.sql.SQLException;
import java.util.List;

/**
 * CU4 - Materiales de Video. Reglas de negocio:
 *  - El codigo es unico y se guarda en mayusculas. Un material se referencia por su codigo (o ID).
 *  - Tipo: PROGRAMA, SPOT o PROMO. Si es PROGRAMA, el programa (activo) es obligatorio; si no, puede ir "".
 *  - Formato: MP4, MOV o MXF. Duracion 1 a 86400 segundos.
 *  - La ruta es unica: si ya existe, se informa el codigo del material que la usa (evita archivos duplicados).
 *  - Estado inicial PENDIENTE; registrado_por = quien envia el comando.
 *  - modMat: si cambia la ruta o la duracion de un material APROBADO, vuelve a PENDIENTE (hay que validarlo de nuevo).
 *    Si el material esta en una parrilla PUBLICADA no se permite cambiar ruta, duracion ni tipo (el titulo, programa y formato si se pueden cambiar).
 *  - eliMat es baja logica y NO se permite si esta en una parrilla PUBLICADA o en un spot programado.
 *  - Cada alta, modificacion y baja se registra en la auditoria.
 */
public class NMaterial {

    public static final String[] TIPOS = {"PROGRAMA", "SPOT", "PROMO"};
    public static final String[] FORMATOS = {"MP4", "MOV", "MXF"};
    private static final String[] ESTADOS = {"PENDIENTE", "APROBADO", "RECHAZADO"};

    private final DMaterial datos = new DMaterial();
    private final DPrograma programas = new DPrograma();
    private final NAuditoria auditoria = new NAuditoria();

    /** filtro: "*" = todos; un estado (PENDIENTE...) o un tipo (SPOT...); otro texto = busca por codigo, titulo o programa. */
    public List<String[]> listar(String filtro) throws NegocioException, SQLException {
        Validador v = new Validador();
        String f = v.texto(filtro, "filtro", 60);
        v.verificar();
        if (f.equals("*")) {
            return datos.listar(null, null, null);
        }
        String mayus = f.toUpperCase();
        if (mayus.equals("VENCIDO")) { // pendientes que ya recibieron aviso por falta de revision
            return datos.listarVencidos();
        }
        for (String e : ESTADOS) {
            if (e.equals(mayus)) {
                return datos.listar(e, null, null);
            }
        }
        for (String t : TIPOS) {
            if (t.equals(mayus)) {
                return datos.listar(null, t, null);
            }
        }
        return datos.listar(null, null, f);
    }

    /** referencia: codigo o ID. Devuelve la fila completa. */
    public String[] mostrar(String referencia) throws NegocioException, SQLException {
        return datos.obtener(resolver(referencia));
    }

    /** insMat["codigo","titulo","tipo","programa (nombre)","duracion_seg","formato","ruta"]. Devuelve el id creado. */
    public int insertar(Usuario quien, List<String> p) throws NegocioException, SQLException {
        Validador v = new Validador();
        String codigo = v.codigo(p.get(0), "codigo");
        String titulo = v.texto(p.get(1), "titulo", 160);
        String tipo = v.opcion(p.get(2), "tipo", TIPOS);
        String programa = v.textoOpcional(p.get(3), "programa", 120);
        Integer duracion = v.entero(p.get(4), "duracion_seg", 1, 86400);
        String formato = v.opcion(p.get(5), "formato", FORMATOS);
        String ruta = v.texto(p.get(6), "ruta", 255);
        if ("PROGRAMA".equals(tipo) && programa == null) {
            v.error("Un material de tipo <b>PROGRAMA</b> necesita el nombre del programa al que pertenece.");
        }
        v.verificar();

        if (datos.existeCodigo(codigo, 0)) {
            v.error("Ya existe un material con el código <b>" + Validador.esc(codigo) + "</b>.");
        }
        validarRuta(v, ruta, 0);
        validarExtension(v, ruta, formato);
        Integer idPrograma = resolverPrograma(v, programa);
        v.verificar();

        int id = datos.insertar(codigo, idPrograma, titulo, tipo, duracion, formato, ruta, quien.getId());
        auditoria.registrar(quien, NAuditoria.INSERTAR, "material", id,
                codigo + " " + titulo + " (" + tipo + ", " + duracion + " s, " + formato + ")", "insMat");
        return id;
    }

    /** modMat["material (codigo)","titulo","tipo","programa (nombre)","duracion_seg","formato","ruta"]. Devuelve el id. */
    public int modificar(Usuario quien, List<String> p) throws NegocioException, SQLException {
        int id = resolver(p.get(0));
        Validador v = new Validador();
        String titulo = v.texto(p.get(1), "titulo", 160);
        String tipo = v.opcion(p.get(2), "tipo", TIPOS);
        String programa = v.textoOpcional(p.get(3), "programa", 120);
        Integer duracion = v.entero(p.get(4), "duracion_seg", 1, 86400);
        String formato = v.opcion(p.get(5), "formato", FORMATOS);
        String ruta = v.texto(p.get(6), "ruta", 255);
        if ("PROGRAMA".equals(tipo) && programa == null) {
            v.error("Un material de tipo <b>PROGRAMA</b> necesita el nombre del programa al que pertenece.");
        }
        v.verificar();

        validarRuta(v, ruta, id);
        validarExtension(v, ruta, formato);
        Integer idPrograma = resolverPrograma(v, programa);
        v.verificar();

        String[] actual = datos.obtener(id);
        boolean cambioContenido = !actual[7].equalsIgnoreCase(ruta) || !actual[5].equals(String.valueOf(duracion));
        boolean volverAPendiente = "APROBADO".equals(actual[8]) && cambioContenido;
        int enPublicadas = datos.contarEnParrillasPublicadas(id);
        if (enPublicadas > 0 && (cambioContenido || !actual[3].equals(tipo))) {
            v.error("El material <b>" + Validador.esc(actual[1]) + "</b> está en " + enPublicadas + " bloque(s) de parrillas PUBLICADAS: "
                    + "no se puede cambiar su ruta, duración ni tipo (volvería a PENDIENTE y la parrilla publicada quedaría inconsistente). "
                    + "Registre un material nuevo y cámbielo en la parrilla.");
            v.verificar();
        }

        datos.modificar(id, idPrograma, titulo, tipo, duracion, formato, ruta, volverAPendiente);
        auditoria.registrar(quien, NAuditoria.MODIFICAR, "material", id,
                "Antes: " + actual[2] + " (" + actual[3] + ", " + actual[5] + " s, " + actual[7] + ")"
                        + " | Ahora: " + titulo + " (" + tipo + ", " + duracion + " s, " + ruta + ")"
                        + (volverAPendiente ? " | Vuelve a PENDIENTE" : ""), "modMat");
        return id;
    }

    /** eliMat["material (codigo)"]. Devuelve el id. */
    public int eliminar(Usuario quien, String referencia) throws NegocioException, SQLException {
        int id = resolver(referencia);
        String[] actual = datos.obtener(id);
        if (!datos.estaActivo(id)) {
            throw new NegocioException("El material <b>" + Validador.esc(actual[1]) + "</b> ya está dado de baja.");
        }
        Validador v = new Validador();
        int enParrillas = datos.contarEnParrillasPublicadas(id);
        if (enParrillas > 0) {
            v.error("El material <b>" + Validador.esc(actual[1]) + "</b> está en " + enParrillas + " bloque(s) de parrillas PUBLICADAS.");
        }
        int enSpots = datos.contarSpotsProgramados(id);
        if (enSpots > 0) {
            v.error("El material <b>" + Validador.esc(actual[1]) + "</b> tiene " + enSpots + " spot(s) programado(s).");
        }
        v.verificar();

        datos.darDeBaja(id);
        auditoria.registrar(quien, NAuditoria.ELIMINAR, "material", id, "Baja de " + actual[1] + " " + actual[2], "eliMat");
        return id;
    }

    /** Convierte la referencia (codigo o ID) en el id del material. */
    private int resolver(String referencia) throws NegocioException, SQLException {
        Validador v = new Validador();
        String ref = v.texto(referencia, "material", 120);
        v.verificar();
        Integer id = datos.buscarId(ref);
        if (id == null) {
            throw new NegocioException("No existe un material con el código <b>" + Validador.esc(ref) + "</b>. "
                    + "Consulte los materiales con lisMat[\"*\"].");
        }
        return id;
    }

    /** El archivo debe corresponder al formato declarado: la ruta termina en .mp4, .mov o .mxf segun el formato. */
    private void validarExtension(Validador v, String ruta, String formato) {
        if (ruta != null && formato != null && !ruta.toLowerCase().endsWith("." + formato.toLowerCase())) {
            v.error("La ruta <b>" + Validador.esc(ruta) + "</b> debe terminar en <b>." + formato.toLowerCase()
                    + "</b>: el archivo y el formato (" + formato + ") deben coincidir.");
        }
    }

    /**
     * Posibles duplicados de un material: otros materiales activos con el mismo titulo, tipo y duracion.
     * No bloquea (puede ser una version distinta): solo advierte para que se revise con mosMat.
     */
    public List<String> posiblesDuplicados(int id) throws SQLException {
        String[] m = datos.obtener(id);
        return datos.buscarSimilares(m[2], m[3], Integer.parseInt(m[5]), id);
    }

    /** La ruta no puede estar usada por otro material: se informa cual la usa. */
    private void validarRuta(Validador v, String ruta, int excluirId) throws SQLException {
        String codigo = datos.codigoConRuta(ruta, excluirId);
        if (codigo != null) {
            v.error("La ruta <b>" + Validador.esc(ruta) + "</b> ya está registrada en el material <b>" + Validador.esc(codigo)
                    + "</b> (archivo duplicado). Use ese material con mosMat[\"" + Validador.esc(codigo) + "\"].");
        }
    }

    /** Devuelve el id del programa (debe existir y estar activo) o null si no se indico programa. */
    private Integer resolverPrograma(Validador v, String nombre) throws SQLException {
        if (nombre == null) {
            return null;
        }
        Integer id = programas.buscarId(nombre);
        if (id == null) {
            v.error("No existe el programa <b>" + Validador.esc(nombre) + "</b>. Consulte los programas con lisPro[\"*\"].");
            return null;
        }
        if (!programas.estaActivo(id)) {
            v.error("El programa <b>" + Validador.esc(nombre) + "</b> está dado de baja.");
            return null;
        }
        return id;
    }
}
