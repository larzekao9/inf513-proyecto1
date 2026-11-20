package canal11.datos;

import canal11.config.Config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Abre conexiones JDBC a PostgreSQL. Todas las tablas del sistema viven en el esquema
 * configurado (db.esquema = canal11), separadas de otras tablas que haya en la base del grupo.
 */
public final class Conexion {

    private Conexion() {
    }

    public static String url() {
        return "jdbc:postgresql://" + Config.get("db.host") + ":" + Config.get("db.puerto") + "/" + Config.get("db.nombre")
                + "?currentSchema=" + Config.get("db.esquema");
    }

    public static Connection abrir() throws SQLException {
        return DriverManager.getConnection(url(), Config.get("db.usuario"), Config.get("db.password"));
    }

    /** Convierte todas las filas de un ResultSet en arreglos de texto (listo para armar tablas HTML). */
    public static List<String[]> leerFilas(ResultSet rs) throws SQLException {
        int n = rs.getMetaData().getColumnCount();
        List<String[]> filas = new ArrayList<>();
        while (rs.next()) {
            String[] f = new String[n];
            for (int i = 0; i < n; i++) {
                f[i] = rs.getString(i + 1);
            }
            filas.add(f);
        }
        return filas;
    }
}
