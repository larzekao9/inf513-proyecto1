package canal11.negocio;

/** Usuario que envio el correo (identificado por su email). */
public class Usuario {

    private final int id;
    private final String nombre;
    private final String email;
    private final Rol rol;

    public Usuario(int id, String nombre, String email, Rol rol) {
        this.id = id;
        this.nombre = nombre;
        this.email = email;
        this.rol = rol;
    }

    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getEmail() {
        return email;
    }

    public Rol getRol() {
        return rol;
    }
}
