-- =====================================================================
-- Datos iniciales. Se puede ejecutar varias veces (no duplica).
-- El administrador debe ser un correo REAL: es desde donde se envian los comandos.
-- =====================================================================

INSERT INTO usuario (ci, nombre, apellido, email, telefono, rol)
VALUES ('0000001', 'Luis Angel', 'Arze Kao', 'larzekaobnc@gmail.com', NULL, 'ADMINISTRADOR')
ON CONFLICT (email) DO NOTHING;
