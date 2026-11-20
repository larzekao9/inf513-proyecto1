package canal11.presentacion;

import canal11.negocio.Rol;

import java.util.EnumSet;
import java.util.Set;

/** Definicion de un comando: a que CU pertenece, que parametros pide, quien puede usarlo y que hace. */
public class Comando {

    private final int cu;
    private final String nombre;
    private final String descripcion;
    private final String[] parametros;
    private final String ejemplo;
    private final Set<Rol> roles;
    private final Accion accion; // null = todavia no implementado

    public Comando(int cu, String nombre, String descripcion, String[] parametros, String ejemplo,
                   Set<Rol> roles, Accion accion) {
        this.cu = cu;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.parametros = parametros;
        this.ejemplo = ejemplo;
        this.roles = EnumSet.copyOf(roles);
        this.accion = accion;
    }

    public int getCu() {
        return cu;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String[] getParametros() {
        return parametros;
    }

    public String getEjemplo() {
        return ejemplo;
    }

    public Accion getAccion() {
        return accion;
    }

    public boolean permite(Rol rol) {
        return roles.contains(rol);
    }

    public boolean implementado() {
        return accion != null;
    }

    /** "id_programa, titulo, tipo" o "(ninguno)". */
    public String firma() {
        return parametros.length == 0 ? "(ninguno)" : String.join(", ", parametros);
    }
}
