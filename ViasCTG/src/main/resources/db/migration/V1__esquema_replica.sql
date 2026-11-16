-- Réplica relacional de ViaCTG en PostgreSQL. MongoDB es la base principal; estas tablas son una copia normalizada que
-- llena ReplicaSqlService. Lo que en Mongo va embebido aquí es una tabla hija con llave foránea:
--   Barrio.calles[]            -> calles
--   Reporte.fotos[]            -> reporte_fotos
--   Reporte.historialEstados[] -> historial_estados
--   Reporte.categoria {id, nombre} -> reportes.categoria_id (el nombre vive solo en categorias)
-- Los id son los de MongoDB (ObjectId en texto o UUID), así cada fila se puede buscar en las dos bases.
-- No se replican contraseñas, tokens ni notificaciones.

CREATE TABLE usuarios (
    id               VARCHAR(64)  PRIMARY KEY,
    nombre           VARCHAR(200) NOT NULL,
    email            VARCHAR(320) NOT NULL UNIQUE,
    telefono         VARCHAR(40),
    rol              VARCHAR(20)  NOT NULL CHECK (rol IN ('CIUDADANO', 'MODERADOR', 'ADMIN')),
    activo           BOOLEAN      NOT NULL,
    email_verificado BOOLEAN      NOT NULL,
    fecha_registro   TIMESTAMPTZ
);

CREATE TABLE categorias (
    id          VARCHAR(64)  PRIMARY KEY,
    nombre      VARCHAR(100) NOT NULL,
    descripcion VARCHAR(500),
    activa      BOOLEAN      NOT NULL
);

CREATE TABLE barrios (
    id        VARCHAR(64)  PRIMARY KEY,
    nombre    VARCHAR(150) NOT NULL,
    localidad VARCHAR(200)
);

CREATE TABLE calles (
    id            VARCHAR(64)  PRIMARY KEY,
    barrio_id     VARCHAR(64)  NOT NULL REFERENCES barrios (id) ON DELETE CASCADE,
    nombre        VARCHAR(200) NOT NULL,
    codigo_postal VARCHAR(20)
);
CREATE INDEX calles_barrio ON calles (barrio_id);

CREATE TABLE reportes (
    id                  VARCHAR(64)      PRIMARY KEY,
    usuario_id          VARCHAR(64)      REFERENCES usuarios (id),
    categoria_id        VARCHAR(64)      REFERENCES categorias (id),
    descripcion         TEXT             NOT NULL,
    latitud             DOUBLE PRECISION NOT NULL,
    longitud            DOUBLE PRECISION NOT NULL,
    direccion_osm       VARCHAR(500),
    barrio_osm          VARCHAR(200),
    estado              VARCHAR(20)      NOT NULL
        CHECK (estado IN ('PENDIENTE', 'EN_REVISION', 'EN_PROCESO', 'RESUELTO', 'RECHAZADO')),
    prioridad           VARCHAR(10)      NOT NULL CHECK (prioridad IN ('BAJA', 'MEDIA', 'ALTA')),
    fecha_creacion      TIMESTAMPTZ      NOT NULL,
    fecha_actualizacion TIMESTAMPTZ
);
CREATE INDEX reportes_usuario ON reportes (usuario_id);
CREATE INDEX reportes_categoria ON reportes (categoria_id);
CREATE INDEX reportes_estado_fecha ON reportes (estado, fecha_creacion DESC);

CREATE TABLE reporte_fotos (
    reporte_id VARCHAR(64)  NOT NULL REFERENCES reportes (id) ON DELETE CASCADE,
    orden      SMALLINT     NOT NULL,
    ruta       VARCHAR(300) NOT NULL,
    PRIMARY KEY (reporte_id, orden)
);

CREATE TABLE historial_estados (
    id               VARCHAR(64) PRIMARY KEY,
    reporte_id       VARCHAR(64) NOT NULL REFERENCES reportes (id) ON DELETE CASCADE,
    orden            INTEGER     NOT NULL,
    -- NULL cuando el cambio lo hizo el sistema (cierre por la comunidad).
    usuario_admin_id VARCHAR(64) REFERENCES usuarios (id),
    estado_anterior  VARCHAR(20) NOT NULL,
    estado_nuevo     VARCHAR(20) NOT NULL,
    comentario       TEXT,
    fecha            TIMESTAMPTZ NOT NULL,
    UNIQUE (reporte_id, orden)
);
CREATE INDEX historial_admin ON historial_estados (usuario_admin_id);

CREATE TABLE comentarios (
    id         VARCHAR(64) PRIMARY KEY,
    reporte_id VARCHAR(64) NOT NULL REFERENCES reportes (id) ON DELETE CASCADE,
    usuario_id VARCHAR(64) NOT NULL REFERENCES usuarios (id),
    texto      TEXT        NOT NULL,
    fecha      TIMESTAMPTZ NOT NULL,
    editado    BOOLEAN     NOT NULL
);
CREATE INDEX comentarios_reporte ON comentarios (reporte_id, fecha);

CREATE TABLE confirmaciones (
    id         VARCHAR(64) PRIMARY KEY,
    reporte_id VARCHAR(64) NOT NULL REFERENCES reportes (id) ON DELETE CASCADE,
    usuario_id VARCHAR(64) NOT NULL REFERENCES usuarios (id),
    fecha      TIMESTAMPTZ NOT NULL,
    UNIQUE (reporte_id, usuario_id)
);

-- Votos "¿ya lo arreglaron?".
CREATE TABLE seguimientos (
    id         VARCHAR(64) PRIMARY KEY,
    reporte_id VARCHAR(64) NOT NULL REFERENCES reportes (id) ON DELETE CASCADE,
    usuario_id VARCHAR(64) NOT NULL REFERENCES usuarios (id),
    arreglado  BOOLEAN     NOT NULL,
    fecha      TIMESTAMPTZ NOT NULL,
    UNIQUE (reporte_id, usuario_id)
);

-- Registro de acciones administrativas. Sin llaves foráneas: el actor puede ser "sistema" y entidad_id apunta a
-- tablas distintas según la entidad.
CREATE TABLE auditoria (
    id           VARCHAR(64)  PRIMARY KEY,
    fecha        TIMESTAMPTZ  NOT NULL,
    actor_id     VARCHAR(64),
    actor_nombre VARCHAR(200),
    accion       VARCHAR(60)  NOT NULL,
    entidad      VARCHAR(30),
    entidad_id   VARCHAR(64),
    detalle      TEXT
);
CREATE INDEX auditoria_fecha ON auditoria (fecha DESC);
