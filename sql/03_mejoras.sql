-- =====================================================================
-- Mejoras de la base de datos: las reglas de negocio que ya valida la capa N*
-- (NParrilla, NSpot, NUsuario) se refuerzan tambien en la propia BD.
-- Se puede ejecutar varias veces (DROP ... IF EXISTS antes de cada ADD CONSTRAINT).
-- Antes de aplicarlo, el Instalador revisa que los datos existentes no violen
-- estas restricciones y, si las violan, lo informa en vez de fallar.
-- =====================================================================

-- 1) Un bloque PROGRAMA lleva programa; una PAUSA_COMERCIAL no lleva programa ni material.
ALTER TABLE bloque DROP CONSTRAINT IF EXISTS ck_bloque_tipo;
ALTER TABLE bloque ADD CONSTRAINT ck_bloque_tipo CHECK (
    (tipo = 'PROGRAMA' AND id_programa IS NOT NULL)
    OR (tipo = 'PAUSA_COMERCIAL' AND id_programa IS NULL AND id_material IS NULL)
);

-- 2) Dos bloques de la misma parrilla no pueden solaparse: [hora_inicio, hora_fin).
--    Usa tsrange sobre una fecha fija para no crear un tipo timerange (no es repetible).
--    Si el servidor no permite CREATE EXTENSION (permisos), solo avisa y el instalador sigue:
--    el solape queda validado unicamente por NParrilla.
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS btree_gist;
    ALTER TABLE bloque DROP CONSTRAINT IF EXISTS ex_bloque_sin_solape;
    ALTER TABLE bloque ADD CONSTRAINT ex_bloque_sin_solape EXCLUDE USING gist (
        id_parrilla WITH =,
        tsrange(DATE '2000-01-01' + hora_inicio, DATE '2000-01-01' + hora_fin, '[)') WITH &&
    );
EXCEPTION WHEN OTHERS THEN
    RAISE WARNING 'AVISO: no se pudo crear ex_bloque_sin_solape (%). El solape de bloques solo se valida en la aplicacion.', SQLERRM;
END
$$;

-- 3) Un spot solo va en una pausa comercial, dentro de su horario, con material SPOT aprobado.
--    Se dispara solo al cambiar id_bloque, id_material o hora: el UPDATE de estado que hace
--    DEmision.actualizarSpots NO vuelve a validar el material (pudo ser rechazado despues).
CREATE OR REPLACE FUNCTION fn_validar_spot() RETURNS trigger AS $$
DECLARE
    b bloque%ROWTYPE;
    m material%ROWTYPE;
BEGIN
    SELECT * INTO b FROM bloque WHERE id_bloque = NEW.id_bloque;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'El bloque del spot no existe.';
    END IF;
    IF b.tipo <> 'PAUSA_COMERCIAL' THEN
        RAISE EXCEPTION 'Un spot solo puede ubicarse en una pausa comercial.';
    END IF;
    IF NEW.hora < b.hora_inicio OR NEW.hora >= b.hora_fin THEN
        RAISE EXCEPTION 'La hora del spot (%) debe estar dentro de la pausa comercial (% a %).',
            NEW.hora, b.hora_inicio, b.hora_fin;
    END IF;
    SELECT * INTO m FROM material WHERE id_material = NEW.id_material;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'El material del spot no existe.';
    END IF;
    IF m.tipo <> 'SPOT' THEN
        RAISE EXCEPTION 'El material % no es de tipo SPOT.', m.codigo;
    END IF;
    IF m.estado <> 'APROBADO' THEN
        RAISE EXCEPTION 'El material % no esta APROBADO (estado: %).', m.codigo, m.estado;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_validar_spot ON spot;
CREATE TRIGGER trg_validar_spot
    BEFORE INSERT OR UPDATE OF id_bloque, id_material, hora ON spot
    FOR EACH ROW EXECUTE FUNCTION fn_validar_spot();

-- 4) El correo es unico sin importar mayusculas (Ana@x.com = ana@x.com).
CREATE UNIQUE INDEX IF NOT EXISTS ux_usuario_email_lower ON usuario (lower(email));

-- 5) Indices sobre llaves foraneas que se consultan al listar, reportar o dar de baja.
CREATE INDEX IF NOT EXISTS idx_bloque_programa    ON bloque (id_programa);
CREATE INDEX IF NOT EXISTS idx_bloque_material    ON bloque (id_material);
CREATE INDEX IF NOT EXISTS idx_material_programa  ON material (id_programa);
CREATE INDEX IF NOT EXISTS idx_material_registrado ON material (registrado_por);
CREATE INDEX IF NOT EXISTS idx_material_validado  ON material (validado_por);
CREATE INDEX IF NOT EXISTS idx_spot_pauta         ON spot (id_pauta);
CREATE INDEX IF NOT EXISTS idx_spot_material      ON spot (id_material);

-- 6) NO se aplica ON DELETE SET NULL a auditoria.id_usuario: los usuarios solo se dan de baja
--    logica y SET NULL borraria en silencio quien hizo cada cambio. La FK queda como esta.

-- 8) Descripcion de la nota/material (el levantamiento dice que Prensa entrega un texto de descripcion).
ALTER TABLE material ADD COLUMN IF NOT EXISTS descripcion VARCHAR(500);

-- 9) Reglas de la validacion de materiales que antes solo vivian en la aplicacion:
--    rechazar exige una observacion y todo material no PENDIENTE tiene quien lo valido.
ALTER TABLE material DROP CONSTRAINT IF EXISTS ck_material_rechazo;
ALTER TABLE material ADD CONSTRAINT ck_material_rechazo CHECK (
    estado <> 'RECHAZADO' OR (observacion IS NOT NULL AND btrim(observacion) <> '')
);
ALTER TABLE material DROP CONSTRAINT IF EXISTS ck_material_validado;
ALTER TABLE material ADD CONSTRAINT ck_material_validado CHECK (
    estado = 'PENDIENTE' OR validado_por IS NOT NULL
);

-- 10) Una emision con fin debe terminar despues de empezar (NO_EMITIDO no tiene fin_real).
ALTER TABLE emision DROP CONSTRAINT IF EXISTS ck_emision_fin;
ALTER TABLE emision ADD CONSTRAINT ck_emision_fin CHECK (fin_real IS NULL OR fin_real > inicio_real);

-- 11) Publicar una parrilla: debe tener bloques y cada bloque PROGRAMA un material activo y APROBADO
--     (la misma regla que NParrilla.publicar). Solo se revisa en el cambio a PUBLICADA: rechazar
--     un material despues no invalida una parrilla ya publicada.
CREATE OR REPLACE FUNCTION fn_validar_publicacion() RETURNS trigger AS $$
DECLARE
    malo RECORD;
BEGIN
    IF NEW.estado = 'PUBLICADA' AND OLD.estado IS DISTINCT FROM 'PUBLICADA' THEN
        IF NOT EXISTS (SELECT 1 FROM bloque WHERE id_parrilla = NEW.id_parrilla) THEN
            RAISE EXCEPTION 'No se puede publicar una parrilla sin bloques.';
        END IF;
        -- Bloque PROGRAMA sin escaleta: necesita su propio material activo y APROBADO.
        SELECT b.hora_inicio, m.codigo, m.estado, m.activo INTO malo
        FROM bloque b LEFT JOIN material m ON m.id_material = b.id_material
        WHERE b.id_parrilla = NEW.id_parrilla AND b.tipo = 'PROGRAMA'
          AND NOT EXISTS (SELECT 1 FROM escaleta e WHERE e.id_bloque = b.id_bloque)
          AND (m.id_material IS NULL OR m.estado <> 'APROBADO' OR NOT m.activo)
        ORDER BY b.hora_inicio LIMIT 1;
        IF FOUND THEN
            RAISE EXCEPTION 'El bloque de las % no tiene un material activo y APROBADO (material: %, estado: %).',
                malo.hora_inicio, coalesce(malo.codigo, 'sin asignar'), coalesce(malo.estado, '-');
        END IF;
        -- Bloque con escaleta: todas sus notas deben estar activas y APROBADAS.
        SELECT b.hora_inicio, m.codigo, m.estado, m.activo INTO malo
        FROM escaleta e
        JOIN bloque b ON b.id_bloque = e.id_bloque
        JOIN material m ON m.id_material = e.id_material
        WHERE b.id_parrilla = NEW.id_parrilla AND (m.estado <> 'APROBADO' OR NOT m.activo)
        ORDER BY b.hora_inicio, e.orden LIMIT 1;
        IF FOUND THEN
            RAISE EXCEPTION 'La escaleta del bloque de las % usa el material % (estado: %), que no esta activo y APROBADO.',
                malo.hora_inicio, malo.codigo, malo.estado;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_validar_publicacion ON parrilla;
CREATE TRIGGER trg_validar_publicacion
    BEFORE UPDATE OF estado ON parrilla
    FOR EACH ROW EXECUTE FUNCTION fn_validar_publicacion();

-- 12) Spot: la fecha de la parrilla debe estar dentro de la vigencia de la pauta y no superar
--     max_pases (cuenta los spots de la pauta que no estan NO_EMITIDO). Se agrega como segundo
--     trigger para no tocar fn_validar_spot.
CREATE OR REPLACE FUNCTION fn_validar_spot_pauta() RETURNS trigger AS $$
DECLARE
    pa pauta%ROWTYPE;
    f  DATE;
    usados INTEGER;
BEGIN
    SELECT * INTO pa FROM pauta WHERE id_pauta = NEW.id_pauta;
    SELECT p.fecha INTO f FROM bloque b JOIN parrilla p ON p.id_parrilla = b.id_parrilla WHERE b.id_bloque = NEW.id_bloque;
    IF f < pa.fecha_inicio OR f > pa.fecha_fin THEN
        RAISE EXCEPTION 'La fecha % esta fuera de la vigencia de la pauta % (% a %).', f, pa.nombre, pa.fecha_inicio, pa.fecha_fin;
    END IF;
    SELECT count(*) INTO usados FROM spot
    WHERE id_pauta = NEW.id_pauta AND estado <> 'NO_EMITIDO'
      AND id_spot IS DISTINCT FROM NEW.id_spot;
    IF usados >= pa.max_pases THEN
        RAISE EXCEPTION 'La pauta % ya alcanzo su maximo de % pases.', pa.nombre, pa.max_pases;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_validar_spot_pauta ON spot;
CREATE TRIGGER trg_validar_spot_pauta
    BEFORE INSERT OR UPDATE OF id_pauta, id_bloque ON spot
    FOR EACH ROW EXECUTE FUNCTION fn_validar_spot_pauta();

-- 13) Auditoria de solo insercion: nadie (ni el dueno de la app) modifica ni borra lo registrado.
--     No impide que un administrador de la BD elimine el trigger; para eso hay que quitar
--     UPDATE/DELETE al rol de la aplicacion.
CREATE OR REPLACE FUNCTION fn_auditoria_inmutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'La auditoria es de solo insercion: no se permite % sobre auditoria.', TG_OP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_auditoria_inmutable ON auditoria;
CREATE TRIGGER trg_auditoria_inmutable
    BEFORE UPDATE OR DELETE ON auditoria
    FOR EACH ROW EXECUTE FUNCTION fn_auditoria_inmutable();

DROP TRIGGER IF EXISTS trg_auditoria_sin_truncate ON auditoria;
CREATE TRIGGER trg_auditoria_sin_truncate
    BEFORE TRUNCATE ON auditoria
    FOR EACH STATEMENT EXECUTE FUNCTION fn_auditoria_inmutable();

-- 7) Los 3 roles del sistema: ADMINISTRADOR (Programacion), ENCARGADO (Prensa) y PRODUCCION.
--    El rol PROGRAMADOR se elimino: sus permisos los tiene el ADMINISTRADOR, asi que los usuarios
--    que lo tenian pasan a ADMINISTRADOR (mismo trabajo). Luego se reemplaza el CHECK de usuario.rol.
UPDATE usuario SET rol = 'ADMINISTRADOR' WHERE rol = 'PROGRAMADOR';
ALTER TABLE usuario DROP CONSTRAINT IF EXISTS usuario_rol_check;
ALTER TABLE usuario ADD CONSTRAINT usuario_rol_check
    CHECK (rol IN ('ADMINISTRADOR', 'ENCARGADO', 'PRODUCCION'));

-- 14) Plazo de revision de materiales: si Prensa no valida (PENDIENTE) a tiempo, el sistema avisa por correo
--     (avisos = 1, a los ENCARGADO) y luego escala (avisos = 2, a los ADMINISTRADOR). Los plazos se
--     configuran en config.properties. pendiente_desde se reinicia cuando modMat devuelve el material a PENDIENTE.
ALTER TABLE material ADD COLUMN IF NOT EXISTS pendiente_desde TIMESTAMP NOT NULL DEFAULT now();
ALTER TABLE material ADD COLUMN IF NOT EXISTS ultimo_aviso TIMESTAMP;
ALTER TABLE material ADD COLUMN IF NOT EXISTS avisos INTEGER NOT NULL DEFAULT 0;
ALTER TABLE material DROP CONSTRAINT IF EXISTS ck_material_avisos;
ALTER TABLE material ADD CONSTRAINT ck_material_avisos CHECK (avisos BETWEEN 0 AND 2);

CREATE INDEX IF NOT EXISTS idx_material_pendiente ON material (pendiente_desde) WHERE estado = 'PENDIENTE';

-- 15) Columnas sin uso: la fecha de registro de una nota de escaleta y de una emision no se consulta en
--     ningun comando (la emision ya guarda inicio_real/fin_real y la auditoria guarda cuando se registro).
--     Para bases creadas antes de quitarlas de 01_tablas.sql.
ALTER TABLE escaleta DROP COLUMN IF EXISTS fecha_registro;
ALTER TABLE emision DROP COLUMN IF EXISTS fecha_registro;
