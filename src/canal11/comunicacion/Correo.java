package canal11.comunicacion;

/** Correo recibido: solo interesa quien lo envia y el asunto (el comando). */
public class Correo {

    private final int numero;
    private final String remitente;
    private final String asunto;

    public Correo(int numero, String remitente, String asunto) {
        this.numero = numero;
        this.remitente = remitente;
        this.asunto = asunto;
    }

    /** Numero del mensaje dentro del buzon POP3 (para borrarlo). */
    public int getNumero() {
        return numero;
    }

    public String getRemitente() {
        return remitente;
    }

    public String getAsunto() {
        return asunto;
    }

    @Override
    public String toString() {
        return "#" + numero + " De: " + remitente + " | Asunto: " + asunto;
    }
}
