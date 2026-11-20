package canal11.presentacion;

import canal11.config.Registro;
import canal11.datos.ErrorBD;
import canal11.negocio.NUsuario;
import canal11.negocio.NegocioException;
import canal11.negocio.Rol;
import canal11.negocio.Usuario;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Catalogo de comandos del sistema (agrupados por caso de uso aprobado) y punto de entrada
 * de la capa de presentacion: recibe asunto + remitente y devuelve la respuesta HTML.
 *
 * Nomenclatura: 3 letras de la accion + 3 de la entidad.
 *   lis = listar, mos = mostrar, ins = insertar, mod = modificar, eli = eliminar (baja logica)
 *
 * Sin IDs: los registros se referencian por su clave natural.
 *   usuario -> CI o email          programa -> nombre          material -> codigo (ej. NOT-2011)
 *   parrilla -> fecha AAAA-MM-DD   bloque -> fecha + hora      pauta -> nombre
 *   (el ID tambien se acepta como alternativa)
 */
public class Despachador {

    // 3 actores: ADMINISTRADOR (Programación y dirección), ENCARGADO (Prensa) y PRODUCCION.
    private static final Set<Rol> TODOS = EnumSet.allOf(Rol.class);
    // Programación (parrilla, programas, pautas, spots) la hace el Administrador
    private static final Set<Rol> ADM = EnumSet.of(Rol.ADMINISTRADOR);
    // Materiales: los registra Programación (Administrador) o Prensa; la aprobación (valMat) es de Prensa o del
    // Administrador, pero quien registra un material nunca se lo aprueba a sí mismo.
    // El orden del noticiero (escaleta) también es de Prensa.
    private static final Set<Rol> ADM_ENC = EnumSet.of(Rol.ADMINISTRADOR, Rol.ENCARGADO);
    // Producción saca la señal al aire: además de consultar, registra la emisión real (insEmi)
    private static final Set<Rol> ADM_PROD = EnumSet.of(Rol.ADMINISTRADOR, Rol.PRODUCCION);

    private final List<CasoUso> casosUso = new ArrayList<>();
    private final Map<String, Comando> comandos = new LinkedHashMap<>();
    private final NUsuario usuarios = new NUsuario();
    private final Ayuda ayuda;

    public Despachador() {
        registrarCasosDeUso();
        ayuda = new Ayuda(casosUso, comandos.values());
    }

    /**
     * Para implementar un comando pendiente: crear su clase P* (presentacion) y cambiar el ultimo
     * argumento null por el metodo, por ejemplo usuario::listar. Ver docs/GUIA_DESARROLLO.md.
     */
    private void registrarCasosDeUso() {
        PUsuario usuario = new PUsuario();
        PPrograma programa = new PPrograma();
        PMaterial material = new PMaterial();
        PParrilla parrilla = new PParrilla();
        PLead lead = new PLead();
        PValidacion validacion = new PValidacion();
        PPauta pauta = new PPauta();
        PEmision emision = new PEmision();
        PAuditoria auditoria = new PAuditoria();
        PReporte reporte = new PReporte();

        // ------------------------------------------------------------------ CU1 (implementado)
        cu(1, "Gestión de Usuarios", "Registrar y administrar a los usuarios del sistema y su rol.");
        cmd(1, "lisUsu", "Lista los usuarios. Filtro: \"*\" = todos, un rol, o un texto para buscar por nombre o email",
                p("filtro"), "lisUsu[\"*\"]", ADM, usuario::listar);
        cmd(1, "mosUsu", "Muestra un usuario y los comandos para modificarlo o darlo de baja",
                p("usuario (CI o email)"), "mosUsu[\"7654321\"]", ADM, usuario::mostrar);
        cmd(1, "insUsu", "Registra un usuario. Rol: ADMINISTRADOR, ENCARGADO o PRODUCCION. Teléfono puede ir vacío \"\"",
                p("ci", "nombre", "apellido", "email", "telefono", "rol"),
                "insUsu[\"7654321\",\"Ana\",\"Rojas\",\"ana.rojas@gmail.com\",\"71234567\",\"ENCARGADO\"]", ADM, usuario::insertar);
        cmd(1, "modUsu", "Modifica un usuario: primero el usuario a modificar, luego todos sus datos nuevos",
                p("usuario (CI o email)", "ci", "nombre", "apellido", "email", "telefono", "rol"),
                "modUsu[\"7654321\",\"7654321\",\"Ana\",\"Rojas\",\"ana.rojas@gmail.com\",\"71234567\",\"ENCARGADO\"]", ADM, usuario::modificar);
        cmd(1, "eliUsu", "Da de baja a un usuario (queda inactivo y ya no puede enviar comandos)",
                p("usuario (CI o email)"), "eliUsu[\"7654321\"]", ADM, usuario::eliminar);
        cmd(1, "actUsu", "Reactiva a un usuario dado de baja (por ejemplo, si se dio de baja por error)",
                p("usuario (CI o email)"), "actUsu[\"7654321\"]", ADM, usuario::reactivar);

        // ------------------------------------------------------------------ CU2
        cu(2, "Gestión de Catálogo de Programas", "Mantener el catálogo de programas que se emiten en el canal.");
        cmd(2, "lisPro", "Lista los programas. Filtro: \"*\" = todos, o un tipo (NOTICIERO, PELICULA...)",
                p("filtro"), "lisPro[\"*\"]", TODOS, programa::listar);
        cmd(2, "mosPro", "Muestra un programa", p("programa (nombre)"), "mosPro[\"Noticiero Central\"]", TODOS, programa::mostrar);
        cmd(2, "insPro", "Registra un programa. Tipo: NOTICIERO, REVISTA, PELICULA, DOCUMENTAL, SERIE u OTRO",
                p("nombre", "descripcion", "tipo", "duracion_min", "genero", "clasificacion"),
                "insPro[\"Noticiero Central\",\"Edicion de la noche\",\"NOTICIERO\",\"60\",\"Informativo\",\"ATP\"]", ADM, programa::insertar);
        cmd(2, "modPro", "Modifica un programa: primero el programa, luego todos sus datos nuevos",
                p("programa (nombre)", "nombre", "descripcion", "tipo", "duracion_min", "genero", "clasificacion"),
                "modPro[\"Noticiero Central\",\"Noticiero Central\",\"Edicion de la noche\",\"NOTICIERO\",\"90\",\"Informativo\",\"ATP\"]",
                ADM, programa::modificar);
        cmd(2, "eliPro", "Da de baja un programa del catálogo", p("programa (nombre)"), "eliPro[\"Noticiero Central\"]", ADM, programa::eliminar);

        // ------------------------------------------------------------------ CU3
        cu(3, "Gestión de Confeccionar y Controlar la Parrilla de Programación",
                "Armar la parrilla de cada día por bloques horarios, revisarla y publicarla.");
        cmd(3, "lisPar", "Lista las parrillas. Filtro: \"*\" = todas, o un mes AAAA-MM", p("filtro"), "lisPar[\"*\"]", TODOS, parrilla::listar);
        cmd(3, "mosPar", "Muestra la parrilla de un día con todos sus bloques en orden", p("fecha"), "mosPar[\"2026-11-20\"]", TODOS, parrilla::mostrar);
        cmd(3, "insPar", "Crea la parrilla (en borrador) de un día", p("fecha"), "insPar[\"2026-11-20\"]", ADM, parrilla::insertar);
        cmd(3, "eliPar", "Elimina la parrilla de un día (solo si sigue en borrador)", p("fecha"), "eliPar[\"2026-11-20\"]", ADM, parrilla::eliminar);
        cmd(3, "insBlo", "Agrega un bloque a la parrilla. Tipo: PROGRAMA o PAUSA_COMERCIAL. Material (código) puede ir vacío \"\"",
                p("fecha", "hora_inicio", "hora_fin", "tipo", "programa (nombre)", "material (código)"),
                "insBlo[\"2026-11-20\",\"20:00\",\"21:00\",\"PROGRAMA\",\"Noticiero Central\",\"NOT-2011\"]", ADM, parrilla::insertarBloque);
        cmd(3, "eliBlo", "Quita un bloque de la parrilla", p("fecha", "hora_inicio"), "eliBlo[\"2026-11-20\",\"20:00\"]", ADM, parrilla::eliminarBloque);
        cmd(3, "camBlo", "Cambia el material de un bloque de programa, aun con la parrilla publicada, mientras el bloque no haya empezado. "
                        + "Avisa a Producción y Prensa. El material debe estar APROBADO",
                p("fecha", "hora_inicio", "material (código)"), "camBlo[\"2026-11-20\",\"20:00\",\"NOT-2012\"]", ADM, parrilla::cambiarMaterial);
        cmd(3, "lisLea", "Muestra la escaleta (orden de las notas) de un bloque de noticiero o revista, con la hora estimada de cada nota",
                p("fecha", "hora_bloque"), "lisLea[\"2026-11-20\",\"20:00\"]", TODOS, lead::listar);
        cmd(3, "insLea", "Agrega una nota (material APROBADO) a la escaleta del bloque, en la posición indicada",
                p("fecha", "hora_bloque", "orden", "material (código)"), "insLea[\"2026-11-20\",\"20:00\",\"1\",\"NOT-2011\"]",
                ADM_ENC, lead::insertar);
        cmd(3, "modLea", "Cambia el orden de una nota de la escaleta",
                p("fecha", "hora_bloque", "material (código)", "nuevo_orden"), "modLea[\"2026-11-20\",\"20:00\",\"NOT-2011\",\"3\"]",
                ADM_ENC, lead::mover);
        cmd(3, "eliLea", "Quita una nota de la escaleta",
                p("fecha", "hora_bloque", "material (código)"), "eliLea[\"2026-11-20\",\"20:00\",\"NOT-2011\"]", ADM_ENC, lead::eliminar);
        cmd(3, "pubPar", "Revisa la parrilla (solapes, huecos, materiales aprobados) y la publica",
                p("fecha"), "pubPar[\"2026-11-20\"]", ADM, parrilla::publicar);

        // ------------------------------------------------------------------ CU4
        cu(4, "Gestión de Materiales de Video", "Registrar los videos (programas, spots, promos) con un código único y evitar duplicados.");
        cmd(4, "lisMat", "Lista los materiales. Filtro: \"*\" = todos, un estado (PENDIENTE...), VENCIDO (pendientes con aviso por demora) o un tipo (SPOT...)",
                p("filtro"), "lisMat[\"PENDIENTE\"]", TODOS, material::listar);
        cmd(4, "mosMat", "Muestra un material", p("material (código)"), "mosMat[\"NOT-2011\"]", TODOS, material::mostrar);
        cmd(4, "insMat", "Registra un material. Tipo: PROGRAMA, SPOT o PROMO. Programa puede ir vacío \"\" (spots)",
                p("codigo", "titulo", "tipo", "programa (nombre)", "duracion_seg", "formato", "ruta"),
                "insMat[\"NOT-2011\",\"Noticiero 20 de noviembre\",\"PROGRAMA\",\"Noticiero Central\",\"3600\",\"MP4\",\"/prensa/not_2011.mp4\"]",
                ADM_ENC, material::insertar);
        cmd(4, "modMat", "Modifica un material: primero el código, luego todos sus datos nuevos",
                p("material (código)", "titulo", "tipo", "programa (nombre)", "duracion_seg", "formato", "ruta"),
                "modMat[\"NOT-2011\",\"Noticiero 20 de noviembre\",\"PROGRAMA\",\"Noticiero Central\",\"3580\",\"MP4\",\"/prensa/not_2011.mp4\"]",
                ADM_ENC, material::modificar);
        cmd(4, "eliMat", "Da de baja un material", p("material (código)"), "eliMat[\"NOT-2011\"]", ADM_ENC, material::eliminar);

        // ------------------------------------------------------------------ CU5
        cu(5, "Gestión de Validar Estado de Emisión", "Aprobar o rechazar materiales y ver si cada bloque está listo para salir al aire.");
        cmd(5, "valMat", "Aprueba o rechaza un material. Estado: APROBADO o RECHAZADO (al rechazar, la observación es obligatoria)",
                p("material (código)", "estado", "observacion"), "valMat[\"NOT-2011\",\"APROBADO\",\"Audio y video correctos\"]",
                ADM_ENC, validacion::validar);
        cmd(5, "estPar", "Estado de emisión de cada bloque de la parrilla de un día", p("fecha"), "estPar[\"2026-11-20\"]", TODOS, validacion::estadoParrilla);

        // ------------------------------------------------------------------ CU6
        cu(6, "Gestión de Pauta Publicitaria e Insertar Spots Comerciales",
                "Registrar las pautas y ubicar sus spots en las pausas comerciales de la parrilla.");
        cmd(6, "lisPau", "Lista las pautas. Filtro: \"*\" = todas, o el nombre del anunciante", p("filtro"), "lisPau[\"*\"]", TODOS, pauta::listar);
        cmd(6, "mosPau", "Muestra una pauta con sus spots programados", p("pauta (nombre)"), "mosPau[\"Navidad Banco Sol\"]", TODOS, pauta::mostrar);
        cmd(6, "insPau", "Registra una pauta publicitaria",
                p("nombre", "anunciante", "fecha_inicio", "fecha_fin", "max_pases"),
                "insPau[\"Navidad Banco Sol\",\"Banco Sol\",\"2026-12-01\",\"2026-12-31\",\"40\"]", ADM, pauta::insertar);
        cmd(6, "eliPau", "Da de baja una pauta", p("pauta (nombre)"), "eliPau[\"Navidad Banco Sol\"]", ADM, pauta::eliminar);
        cmd(6, "insSpo", "Inserta un spot en la pausa comercial que empieza a hora_pausa",
                p("pauta (nombre)", "material (código)", "fecha", "hora_pausa", "hora_spot"),
                "insSpo[\"Navidad Banco Sol\",\"SPOT-BSOL-01\",\"2026-12-05\",\"20:30\",\"20:30:00\"]", ADM, pauta::insertarSpot);
        cmd(6, "eliSpo", "Quita el spot programado en esa fecha y hora", p("fecha", "hora_spot"),
                "eliSpo[\"2026-12-05\",\"20:30:00\"]", ADM, pauta::eliminarSpot);

        // ------------------------------------------------------------------ CU7
        cu(7, "Gestión de Conciliar Emisión Real, Registrar Auditoría",
                "Registrar lo que realmente salió al aire, compararlo con lo programado y consultar el historial de cambios.");
        cmd(7, "insEmi", "Registra la emisión real del bloque que empieza a hora_inicio. Estado: EMITIDO, PARCIAL o NO_EMITIDO",
                p("fecha", "hora_inicio", "inicio_real", "fin_real", "estado"),
                "insEmi[\"2026-11-20\",\"20:00\",\"20:00:05\",\"21:00:00\",\"EMITIDO\"]", ADM_PROD, emision::insertar);
        cmd(7, "conPar", "Conciliación de un día: lo programado vs. lo emitido", p("fecha"), "conPar[\"2026-11-20\"]", TODOS, emision::conciliar);
        cmd(7, "lisAud", "Historial de cambios. Filtro: \"*\" = todo, una entidad (usuario, parrilla...) o una fecha",
                p("filtro"), "lisAud[\"*\"]", ADM, auditoria::listar);

        // ------------------------------------------------------------------ CU8
        cu(8, "Reportes y Estadísticas", "Reportes de programación, contenidos y pauta filtrados por rango de fechas.");
        cmd(8, "repPro", "Horas programadas por tipo de programa", p("desde", "hasta"), "repPro[\"2026-11-01\",\"2026-11-30\"]", ADM, reporte::horasPorPrograma);
        cmd(8, "repMat", "Materiales por estado y tipo", p("desde", "hasta"), "repMat[\"2026-11-01\",\"2026-11-30\"]", ADM_ENC, reporte::materiales);
        cmd(8, "repPau", "Spots programados y emitidos por anunciante", p("desde", "hasta"), "repPau[\"2026-11-01\",\"2026-11-30\"]", ADM, reporte::spotsPorAnunciante);
        cmd(8, "repCum", "Porcentaje de cumplimiento: programado vs. emitido", p("desde", "hasta"), "repCum[\"2026-11-01\",\"2026-11-30\"]", ADM, reporte::cumplimiento);
    }

    /** Procesa un correo. Nunca lanza excepciones: siempre devuelve una respuesta para el remitente. */
    public Respuesta procesar(String asunto, String remitente) {
        long inicio = System.currentTimeMillis();
        Respuesta r = ejecutar(asunto, remitente);
        Registro.comando(remitente, asunto, r.getTitulo().startsWith("Error") ? "ERROR" : "OK", System.currentTimeMillis() - inicio);
        return r;
    }

    /** Interpreta, valida permisos y ejecuta el comando. Los errores se registran en error.log y se devuelven como respuesta. */
    private Respuesta ejecutar(String asunto, String remitente) {
        Usuario usuario = null;
        try {
            Interprete cmd = Interprete.leer(asunto);
            usuario = usuarios.identificar(remitente);
            String nombre = cmd.getNombre();

            if (nombre.equalsIgnoreCase("help")) {
                return envolver(ayuda.responder(usuario, cmd.getParametros()), usuario);
            }
            Comando def = comandos.get(nombre.toLowerCase());
            if (def == null) {
                throw new ComandoException(comandoInexistente(nombre, cmd.getParametros(), usuario.getRol()));
            }
            if (!def.permite(usuario.getRol())) {
                throw new ComandoException("Su rol (" + usuario.getRol().getNombre() + ") no tiene permiso para usar "
                        + Html.codigo(def.getNombre()) + ".");
            }
            if (cmd.getParametros().size() != def.getParametros().length) {
                throw new ComandoException(cantidadIncorrecta(def, cmd.getParametros()));
            }
            if (!def.implementado()) {
                return envolver(new Respuesta("En desarrollo", Html.info("El comando " + Html.codigo(def.getNombre())
                        + " está bien escrito, pero todavía se está implementando (CU" + def.getCu() + ").")), usuario);
            }
            return envolver(def.getAccion().ejecutar(usuario, cmd.getParametros()), usuario);
        } catch (ComandoException e) {
            Registro.errorUsuario(remitente, asunto, e.getMessage());
            return error(asunto, List.of(e.getMessage()), usuario);
        } catch (NegocioException e) {
            Registro.errorUsuario(remitente, asunto, String.join(" ; ", e.getErrores()));
            return error(asunto, e.getErrores(), usuario);
        } catch (SQLException e) {
            System.err.println("[BD] " + e.getSQLState() + " " + e.getMessage());
            Registro.errorSistema("BD (SQLState " + e.getSQLState() + ") | de=" + remitente + " | asunto=" + asunto, e);
            return error(asunto, List.of(Html.esc(ErrorBD.traducir(e))), usuario);
        } catch (Exception e) {
            System.err.println("[Error] " + e);
            Registro.errorSistema("Inesperado | de=" + remitente + " | asunto=" + asunto, e);
            return error(asunto, List.of("Ocurrió un error inesperado al procesar el comando."), usuario);
        }
    }

    /**
     * Mensaje para un comando que no existe. Si hay uno parecido (ej. "lisUsr" -> "lisUsu"),
     * lo sugiere ya armado con los mismos parametros que envio el usuario.
     */
    private String comandoInexistente(String nombre, List<String> parametros, Rol rol) {
        Comando parecido = null;
        int mejor = Integer.MAX_VALUE;
        for (Comando c : comandos.values()) {
            int d = distancia(nombre.toLowerCase(), c.getNombre().toLowerCase());
            if (d < mejor && c.permite(rol)) {
                mejor = d;
                parecido = c;
            }
        }
        String msg = "El comando <b>" + Html.esc(nombre) + "</b> no existe.";
        if (parecido != null && mejor <= 2) {
            StringBuilder sugerido = new StringBuilder(parecido.getNombre()).append('[');
            for (int i = 0; i < parametros.size(); i++) {
                sugerido.append(i > 0 ? "," : "").append('"').append(parametros.get(i)).append('"');
            }
            sugerido.append(']');
            return msg + " ¿Quiso decir " + Html.codigo(parecido.getNombre()) + "?<br>Pruebe con: "
                    + Html.codigo(sugerido.toString()) + "<br>Uso: " + Html.codigo(parecido.getEjemplo());
        }
        return msg + " Revise que esté bien escrito (3 letras de la acción + 3 de la entidad, ej. lisUsu).";
    }

    /** Distancia de Levenshtein: cuantas letras hay que cambiar, agregar o quitar para pasar de a a b. */
    private static int distancia(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] act = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            act[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                act[j] = Math.min(Math.min(act[j - 1] + 1, prev[j] + 1), prev[j - 1] + costo);
            }
            int[] t = prev;
            prev = act;
            act = t;
        }
        return prev[b.length()];
    }

    /** Explica que parametros esperaba el comando y cuales llegaron, uno por uno. */
    private static String cantidadIncorrecta(Comando def, List<String> recibidos) {
        String[] esperados = def.getParametros();
        StringBuilder sb = new StringBuilder("El comando " + Html.codigo(def.getNombre()) + " necesita <b>" + esperados.length
                + "</b> parámetro(s) y se recibieron <b>" + recibidos.size() + "</b>.");
        List<String[]> filas = new ArrayList<>();
        int n = Math.max(esperados.length, recibidos.size());
        for (int i = 0; i < n; i++) {
            String esperado = i < esperados.length ? Html.esc(esperados[i]) : "<i>(sobra)</i>";
            String recibido = i < recibidos.size() ? "\"" + Html.esc(recibidos.get(i)) + "\"" : "<i style='color:#c62828;'>(falta)</i>";
            filas.add(new String[]{String.valueOf(i + 1), esperado, recibido});
        }
        return sb + Html.tablaHtml(new String[]{"#", "Parámetro esperado", "Valor recibido"}, filas)
                + "Ejemplo: " + Html.codigo(def.getEjemplo());
    }

    /** Coloca el contenido dentro de la plantilla del canal. */
    private Respuesta envolver(Respuesta r, Usuario u) {
        String quien = u == null ? null : u.getNombre() + " (" + u.getRol().getNombre() + ")";
        return new Respuesta(r.getTitulo(), Html.pagina(r.getTitulo(), quien, r.getHtml()));
    }

    private Respuesta error(String asunto, List<String> errores, Usuario u) {
        String titulo = errores.size() == 1 ? "No se pudo procesar el comando"
                : "Se encontraron " + errores.size() + " errores en el comando";
        String detalle = errores.size() == 1 ? errores.get(0) : Html.lista(errores);
        String html = Html.error("<b>" + titulo + ":</b> " + Html.codigo(asunto == null ? "" : asunto.trim())
                + "<div style='margin-top:6px;'>" + detalle + "</div>")
                + Html.parrafo("Corrija y vuelva a enviar. Envíe " + Html.codigo("help") + " para ver los comandos y sus ejemplos.");
        return envolver(new Respuesta("Error", html), u);
    }

    // ---- utilidades para registrar el catalogo ----

    private void cu(int numero, String nombre, String descripcion) {
        casosUso.add(new CasoUso(numero, nombre, descripcion));
    }

    private void cmd(int cu, String nombre, String descripcion, String[] parametros, String ejemplo,
                     Set<Rol> roles, Accion accion) {
        comandos.put(nombre.toLowerCase(), new Comando(cu, nombre, descripcion, parametros, ejemplo, roles, accion));
    }

    private static String[] p(String... nombres) {
        return nombres;
    }
}
