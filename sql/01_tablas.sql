-- =====================================================================
-- Canal 11 - Sistema de Gestion de Programacion y Contenidos
-- Tablas (esquema canal11). Se ejecuta con: scripts\ejecutar.bat --instalar
-- Una tabla por entidad de los casos de uso aprobados (CU1..CU8).
--
-- Cada tabla tiene una CLAVE NATURAL unica para que en los comandos no haya que
-- adivinar IDs:  usuario -> ci / email   programa -> nombre   material -> codigo
--                parrilla -> fecha       bloque -> fecha + hora_inicio   pauta -> nombre
-- =====================================================================

-- CU1: usuarios del sistema (3 actores: Administrador = Programacion, Encargado de Contenidos = Prensa, Produccion)
CREATE TABLE IF NOT EXISTS usuario (
    id_usuario      SERIAL PRIMARY KEY,
    ci              VARCHAR(20)  NOT NULL UNIQUE,
    nombre          VARCHAR(80)  NOT NULL,
    apellido        VARCHAR(80)  NOT NULL,
    email           VARCHAR(120) NOT NULL UNIQUE,
    telefono        VARCHAR(20),
    rol             VARCHAR(20)  NOT NULL CHECK (rol IN ('ADMINISTRADOR', 'ENCARGADO', 'PRODUCCION')),
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_registro  TIMESTAMP    NOT NULL DEFAULT now()
);

-- CU2: catalogo de programas
CREATE TABLE IF NOT EXISTS programa (
    id_programa     SERIAL PRIMARY KEY,
    nombre          VARCHAR(120) NOT NULL UNIQUE,
    descripcion     VARCHAR(255),
    tipo            VARCHAR(20)  NOT NULL CHECK (tipo IN ('NOTICIERO', 'REVISTA', 'PELICULA', 'DOCUMENTAL', 'SERIE', 'OTRO')),
    duracion_min    INTEGER      NOT NULL CHECK (duracion_min > 0),
    genero          VARCHAR(60),
    clasificacion   VARCHAR(10),
    activo          BOOLEAN      NOT NULL DEFAULT TRUE
);

-- CU4 y CU5: materiales de video y su validacion
CREATE TABLE IF NOT EXISTS material (
    id_material       SERIAL PRIMARY KEY,
    codigo            VARCHAR(20)  NOT NULL UNIQUE,   -- ej. NOT-2011, SPOT-BSOL-01
    id_programa       INTEGER      REFERENCES programa (id_programa),
    titulo            VARCHAR(160) NOT NULL,
    descripcion       VARCHAR(500),
    tipo              VARCHAR(20)  NOT NULL CHECK (tipo IN ('PROGRAMA', 'SPOT', 'PROMO')),
    duracion_seg      INTEGER      NOT NULL CHECK (duracion_seg > 0),
    formato           VARCHAR(10)  NOT NULL,
    ruta              VARCHAR(255) NOT NULL UNIQUE,
    estado            VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO')),
    observacion       VARCHAR(255),
    registrado_por    INTEGER      NOT NULL REFERENCES usuario (id_usuario),
    fecha_registro    TIMESTAMP    NOT NULL DEFAULT now(),
    validado_por      INTEGER      REFERENCES usuario (id_usuario),
    fecha_validacion  TIMESTAMP,
    activo            BOOLEAN      NOT NULL DEFAULT TRUE
);

-- CU3: parrilla diaria
CREATE TABLE IF NOT EXISTS parrilla (
    id_parrilla        SERIAL PRIMARY KEY,
    fecha              DATE        NOT NULL UNIQUE,
    estado             VARCHAR(20) NOT NULL DEFAULT 'BORRADOR' CHECK (estado IN ('BORRADOR', 'PUBLICADA')),
    creado_por         INTEGER     NOT NULL REFERENCES usuario (id_usuario),
    fecha_creacion     TIMESTAMP   NOT NULL DEFAULT now(),
    fecha_publicacion  TIMESTAMP
);

-- CU3: bloques horarios de la parrilla
CREATE TABLE IF NOT EXISTS bloque (
    id_bloque     SERIAL PRIMARY KEY,
    id_parrilla   INTEGER     NOT NULL REFERENCES parrilla (id_parrilla) ON DELETE CASCADE,
    id_programa   INTEGER     REFERENCES programa (id_programa),
    id_material   INTEGER     REFERENCES material (id_material),
    hora_inicio   TIME        NOT NULL,
    hora_fin      TIME        NOT NULL,
    tipo          VARCHAR(20) NOT NULL CHECK (tipo IN ('PROGRAMA', 'PAUSA_COMERCIAL')),
    CHECK (hora_fin > hora_inicio),
    UNIQUE (id_parrilla, hora_inicio)
);

-- CU6: pautas publicitarias
CREATE TABLE IF NOT EXISTS pauta (
    id_pauta      SERIAL PRIMARY KEY,
    anunciante    VARCHAR(150) NOT NULL,
    nombre        VARCHAR(150) NOT NULL UNIQUE,
    fecha_inicio  DATE         NOT NULL,
    fecha_fin     DATE         NOT NULL,
    max_pases     INTEGER      NOT NULL CHECK (max_pases > 0),
    activo        BOOLEAN      NOT NULL DEFAULT TRUE,
    CHECK (fecha_fin >= fecha_inicio)
);

-- CU6: spots insertados en las pausas comerciales
CREATE TABLE IF NOT EXISTS spot (
    id_spot      SERIAL PRIMARY KEY,
    id_pauta     INTEGER     NOT NULL REFERENCES pauta (id_pauta),
    id_material  INTEGER     NOT NULL REFERENCES material (id_material),
    id_bloque    INTEGER     NOT NULL REFERENCES bloque (id_bloque) ON DELETE CASCADE,
    hora         TIME        NOT NULL,
    estado       VARCHAR(20) NOT NULL DEFAULT 'PROGRAMADO' CHECK (estado IN ('PROGRAMADO', 'EMITIDO', 'NO_EMITIDO')),
    UNIQUE (id_bloque, hora)   -- un spot se identifica por fecha de parrilla + hora
);

-- CU7: emision real de cada bloque
CREATE TABLE IF NOT EXISTS emision (
    id_emision      SERIAL PRIMARY KEY,
    id_bloque       INTEGER     NOT NULL UNIQUE REFERENCES bloque (id_bloque) ON DELETE CASCADE,
    inicio_real     TIMESTAMP   NOT NULL,
    fin_real        TIMESTAMP,
    estado          VARCHAR(20) NOT NULL CHECK (estado IN ('EMITIDO', 'PARCIAL', 'NO_EMITIDO')),
    registrado_por  INTEGER     NOT NULL REFERENCES usuario (id_usuario)
);

-- CU7: auditoria (quien cambio que y cuando). Solo se inserta, nunca se modifica.
CREATE TABLE IF NOT EXISTS auditoria (
    id_auditoria  SERIAL PRIMARY KEY,
    id_usuario    INTEGER      REFERENCES usuario (id_usuario),
    accion        VARCHAR(20)  NOT NULL,
    entidad       VARCHAR(30)  NOT NULL,
    id_entidad    INTEGER,
    detalle       VARCHAR(500),
    comando       VARCHAR(500),
    fecha         TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_auditoria_entidad ON auditoria (entidad, id_entidad);

-- Escaleta (orden de las notas dentro de un bloque de noticiero o revista). Es el "orden de transmision" que
-- Prensa mandaba por WhatsApp (lead 1 / video 1, lead 2 / video 2...). El UNIQUE es DEFERRABLE para poder
-- reordenar con un solo UPDATE.
CREATE TABLE IF NOT EXISTS escaleta (
    id_escaleta    SERIAL PRIMARY KEY,
    id_bloque      INTEGER   NOT NULL REFERENCES bloque (id_bloque) ON DELETE CASCADE,
    orden          INTEGER   NOT NULL CHECK (orden > 0),
    id_material    INTEGER   NOT NULL REFERENCES material (id_material),
    registrado_por INTEGER   NOT NULL REFERENCES usuario (id_usuario),
    CONSTRAINT uq_escaleta_orden UNIQUE (id_bloque, orden) DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT uq_escaleta_material UNIQUE (id_bloque, id_material)
);

CREATE INDEX IF NOT EXISTS idx_escaleta_material ON escaleta (id_material);

-- Avisos automaticos ya enviados (recordatorios, alertas): evita repetir el mismo aviso en cada vuelta del hilo.
CREATE TABLE IF NOT EXISTS aviso_enviado (
    tipo   VARCHAR(30) NOT NULL,
    clave  VARCHAR(80) NOT NULL,
    fecha  TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (tipo, clave)
);
