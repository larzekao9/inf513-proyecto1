package canal11.datos;

import canal11.config.Config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Crea el esquema y ejecuta los scripts de la carpeta sql/ (tablas y datos iniciales).
 * Uso: scripts\ejecutar.bat --instalar
 */
public final class Instalador {

    private static final String[] SCRIPTS = {"sql/01_tablas.sql", "sql/02_datos.sql", "sql/03_mejoras.sql"};

    private Instalador() {
    }

    public static void instalar() throws IOException, SQLException {
        String esquema = Config.get("db.esquema");
        if (!esquema.matches("[a-z_][a-z0-9_]*")) {
            throw new IllegalArgumentException("Nombre de esquema invalido: " + esquema);
        }
        String urlBase = "jdbc:postgresql://" + Config.get("db.host") + ":" + Config.get("db.puerto") + "/" + Config.get("db.nombre");
        try (Connection c = DriverManager.getConnection(urlBase, Config.get("db.usuario"), Config.get("db.password"));
             Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + esquema);
            st.execute("SET search_path TO " + esquema + ", public");
            for (String script : SCRIPTS) {
                if (script.endsWith("03_mejoras.sql")) {
                    try {
                        verificarDatos(st);
                    } catch (IllegalStateException e) { // datos que violan las restricciones: mensaje claro, sin traza
                        System.err.println(e.getMessage());
                        System.exit(1);
                    }
                }
                int n = 0;
                for (String sentencia : leerSentencias(Path.of(script))) {
                    st.execute(sentencia);
                    mostrarAvisos(st);
                    n++;
                }
                System.out.println("  " + script + ": " + n + " sentencia(s) ejecutadas");
            }
        }
        System.out.println("Esquema '" + esquema + "' listo en " + Config.get("db.nombre") + ".");
    }

    /** Muestra los avisos (RAISE WARNING) que dejo la ultima sentencia, por ejemplo si no se pudo crear una extension. */
    private static void mostrarAvisos(Statement st) throws SQLException {
        for (SQLWarning w = st.getWarnings(); w != null; w = w.getNextWarning()) {
            if (w.getMessage() != null && w.getMessage().startsWith("AVISO")) { // los demas son "ya existe, se omite"
                System.out.println("  " + w.getMessage());
            }
        }
        st.clearWarnings();
    }

    /**
     * Antes de agregar las restricciones de 03_mejoras.sql revisa que los datos que ya existen no las violen.
     * Si las violan, informa que filas son y detiene la instalacion (en vez de un error critico de PostgreSQL).
     * Los spots invalidos solo se informan: el trigger no revisa filas anteriores.
     */
    private static void verificarDatos(Statement st) throws SQLException {
        String[][] bloqueantes = {
            {"Bloques de una misma parrilla que se solapan (se debe corregir con eliBlo/insBlo)",
             "SELECT p.fecha || ' ' || a.hora_inicio || '-' || a.hora_fin || ' con ' || b.hora_inicio || '-' || b.hora_fin "
             + "FROM bloque a JOIN bloque b ON a.id_parrilla = b.id_parrilla AND a.id_bloque < b.id_bloque "
             + "AND a.hora_inicio < b.hora_fin AND b.hora_inicio < a.hora_fin "
             + "JOIN parrilla p ON p.id_parrilla = a.id_parrilla"},
            {"Bloques PROGRAMA sin programa, o PAUSA_COMERCIAL con programa o material",
             "SELECT p.fecha || ' ' || b.hora_inicio || ' (' || b.tipo || ')' FROM bloque b JOIN parrilla p ON p.id_parrilla = b.id_parrilla "
             + "WHERE NOT ((b.tipo = 'PROGRAMA' AND b.id_programa IS NOT NULL) "
             + "OR (b.tipo = 'PAUSA_COMERCIAL' AND b.id_programa IS NULL AND b.id_material IS NULL))"},
            {"Usuarios con el mismo correo escrito con distintas mayusculas",
             "SELECT lower(email) FROM usuario GROUP BY lower(email) HAVING count(*) > 1"},
            {"Materiales RECHAZADOS sin observacion, o validados (no PENDIENTE) sin responsable",
             "SELECT codigo || ' (' || estado || ')' FROM material "
             + "WHERE (estado = 'RECHAZADO' AND (observacion IS NULL OR btrim(observacion) = '')) "
             + "OR (estado <> 'PENDIENTE' AND validado_por IS NULL)"},
            {"Emisiones cuyo fin_real no es posterior a inicio_real",
             "SELECT 'emision ' || id_emision FROM emision WHERE fin_real IS NOT NULL AND fin_real <= inicio_real"},
        };
        List<String> problemas = new ArrayList<>();
        for (String[] regla : bloqueantes) {
            List<String> filas = consultar(st, regla[1]);
            if (!filas.isEmpty()) {
                problemas.add(regla[0] + ":\n    " + String.join("\n    ", filas));
            }
        }
        List<String> spots = consultar(st,
                "SELECT p.fecha || ' ' || s.hora || ' (' || m.codigo || ')' FROM spot s "
                + "JOIN bloque b ON b.id_bloque = s.id_bloque JOIN parrilla p ON p.id_parrilla = b.id_parrilla "
                + "JOIN material m ON m.id_material = s.id_material "
                + "WHERE b.tipo <> 'PAUSA_COMERCIAL' OR s.hora < b.hora_inicio OR s.hora >= b.hora_fin "
                + "OR m.tipo <> 'SPOT' OR m.estado <> 'APROBADO'");
        if (!spots.isEmpty()) {
            System.out.println("  AVISO: spots existentes que no cumplen las reglas del trigger (no se modifican):\n    "
                    + String.join("\n    ", spots));
        }
        if (!problemas.isEmpty()) {
            throw new IllegalStateException("No se pueden aplicar las mejoras de sql/03_mejoras.sql porque hay datos que violan las restricciones.\n"
                    + "  " + String.join("\n  ", problemas) + "\n  Corrija esos datos y vuelva a ejecutar --instalar.");
        }
    }

    private static List<String> consultar(Statement st, String sql) throws SQLException {
        List<String> filas = new ArrayList<>();
        try (ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                filas.add(rs.getString(1));
            }
        }
        return filas;
    }

    /**
     * Separa el script en sentencias por ';' al final de linea, ignorando comentarios "--".
     * Respeta los bloques $$ ... $$ (cuerpo de funciones y DO): mientras haya un $$ abierto no corta.
     */
    private static List<String> leerSentencias(Path archivo) throws IOException {
        List<String> sentencias = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean dentroDolar = false;
        for (String linea : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
            String l = linea.strip();
            if (!dentroDolar && (l.isEmpty() || l.startsWith("--"))) {
                continue;
            }
            actual.append(linea).append('\n');
            // cada "$$" abre o cierra un bloque
            for (int i = linea.indexOf("$$"); i >= 0; i = linea.indexOf("$$", i + 2)) {
                dentroDolar = !dentroDolar;
            }
            if (!dentroDolar && l.endsWith(";")) {
                sentencias.add(actual.toString().strip());
                actual.setLength(0);
            }
        }
        if (!actual.toString().isBlank()) {
            sentencias.add(actual.toString().strip());
        }
        return sentencias;
    }
}
