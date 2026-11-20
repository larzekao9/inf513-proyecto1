package canal11.negocio;

import canal11.config.Config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * "Ahora" del canal, en su zona horaria (app.zona). Todas las reglas que comparan contra hoy usan esta clase.
 * Para pruebas o demostraciones se puede fijar con app.ahora=AAAA-MM-DDTHH:MM en config.properties.
 */
public final class Reloj {

    private Reloj() {
    }

    public static LocalDateTime ahora() {
        String fijo = Config.get("app.ahora");
        if (!fijo.isEmpty()) {
            try {
                return LocalDateTime.parse(fijo);
            } catch (DateTimeParseException e) {
                System.err.println("[Reloj] app.ahora invalido ('" + fijo + "'): se usa la hora real.");
            }
        }
        return LocalDateTime.now(ZoneId.of(Config.get("app.zona")));
    }

    public static LocalDate hoy() {
        return ahora().toLocalDate();
    }
}
