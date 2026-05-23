--liquibase formatted sql

--changeset emm-a:009-1
--comment: Direcciones de clientes de prueba
INSERT INTO `direcciones` (`id`, `tipo_direccion`, `calle`, `numero_exterior`, `numero_interior`, `colonia`, `ciudad`, `estado`, `codigo_postal`, `referencias`, `es_verificada`, `fecha_registro`) VALUES
(1, 'Particular', 'Av. Insurgentes Sur', '1602',  '',   'Crédito Constructor',  'Ciudad de México', 'Ciudad de México', '03940', 'Torre WTC piso 12',       1, '2024-01-15'),
(2, 'Particular', 'Calle Hidalgo',        '45',   'B',  'Centro',               'Cuernavaca',       'Morelos',         '62000', 'Frente al jardín Juárez', 1, '2024-02-20'),
(3, 'Particular', 'Blvd. Cuauhnáhuac',   '210',   '',   'Lomas de la Selva',    'Cuernavaca',       'Morelos',         '62270', '',                        0, '2024-03-05'),
(4, 'Particular', 'Calle Morelos',       '88',    'A',  'Centro Histórico',     'Cuernavaca',       'Morelos',         '62000', 'Cerca del palacio municipal', 1, '2024-04-10'),
(5, 'Particular', 'Calle Matamoros',     '33',    '',   'Plan de Ayala',        'Cuernavaca',       'Morelos',         '62180', '',                        0, '2024-05-18'),
(6, 'Laboral',    'Av. Teopanzolco',     '500',   '',   'Vista Hermosa',        'Cuernavaca',       'Morelos',         '62290', 'Plaza Cuernavaca local 12', 1, '2024-06-01'),
(7, 'Particular', 'Calle Zapata',        '17',    '',   'Acapatzingo',          'Cuernavaca',       'Morelos',         '62440', '',                        1, '2024-07-22'),
(8, 'Particular', 'Privada Las Flores',  '9',     '2',  'Jardines de Cuernavaca', 'Cuernavaca',     'Morelos',         '62360', 'Segunda privada',         0, '2024-08-14');

--changeset emm-a:009-2
--comment: Clientes de prueba
INSERT INTO `clientes` (`id`, `nombre`, `apellido_paterno`, `apellido_materno`, `telefono`, `curp`, `rfc`, `activo`, `direccion_id`) VALUES
(1, 'Juan Carlos',    'García',     'Ortiz',    '7771234567', 'GAOJ850315HMSRCN05', 'GAOJ850315AB3', 1, 1),
(2, 'María Elena',    'Martínez',   'Ramos',    '7772345678', 'MARM920820MMSCML08', 'MARM920820GH5', 1, 2),
(3, 'Roberto',        'Hernández',  'López',    '7773456789', 'HELR780601HMSRPB03', 'HELR780601JK7', 1, 3),
(4, 'Ana Sofía',      'Torres',     'Gutiérrez','7774567890', 'TOGA950912MMSSRN06', 'TOGA950912MN2', 1, 4),
(5, 'Miguel Ángel',   'Ramírez',    'Cruz',     '7775678901', 'RACM881130HMSMRG09', 'RACM881130PQ4', 0, 5),
(6, 'Laura Patricia', 'Flores',     'Mendoza',  '7776789012', 'FOML910425MMSLRR07', 'FOML910425RS6', 1, 6),
(7, 'Carlos Alberto', 'Jiménez',    'Vargas',   '7777890123', 'JIVC001015HMSRMR01', 'JIVC001015TU8', 1, 7),
(8, 'Guadalupe',      'Sánchez',    'Pérez',    '7778901234', 'SAPG750308MMSRND04', 'SAPG750308VW9', 0, 8);
