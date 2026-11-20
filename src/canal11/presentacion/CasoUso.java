package canal11.presentacion;

/** Caso de uso aprobado por el docente (se muestra en el help). */
public class CasoUso {

    private final int numero;
    private final String nombre;
    private final String descripcion;

    public CasoUso(int numero, String nombre, String descripcion) {
        this.numero = numero;
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public int getNumero() {
        return numero;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
