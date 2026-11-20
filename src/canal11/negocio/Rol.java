package canal11.negocio;

/**
 * Los 3 actores humanos del sistema, uno por cada area del canal:
 *   ADMINISTRADOR = Programacion y direccion (usuarios, programas, materiales, parrilla, pautas, spots, auditoria, reportes)
 *   ENCARGADO     = Prensa (aprueba o rechaza los materiales y arma la escaleta del noticiero)
 *   PRODUCCION    = Produccion (saca la senal al aire y registra la emision real)
 */
public enum Rol {
    ADMINISTRADOR("Administrador"),
    ENCARGADO("Encargado de Contenidos"),
    PRODUCCION("Producción");

    private final String nombre;

    Rol(String nombre) {
        this.nombre = nombre;
    }

    public String getNombre() {
        return nombre;
    }

    /** Convierte el texto de la BD o de un parametro ("encargado") en Rol; null si no existe. */
    public static Rol desde(String texto) {
        for (Rol r : values()) {
            if (r.name().equalsIgnoreCase(texto == null ? "" : texto.trim())) {
                return r;
            }
        }
        return null;
    }
}
