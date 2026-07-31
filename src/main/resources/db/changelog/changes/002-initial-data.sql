--liquibase formatted sql

--changeset emmanuel:002-data comment:Initial seed data

INSERT INTO `tipoCatalogo` (`id`, `catalogo`, `esEditable`) VALUES
(1, 'Identificación oficial', NULL);

INSERT INTO `tipo_prenda` (`id`, `tipo`) VALUES
(1, 'ALHAJA'),
(3, 'VARIOS'),
(4, 'PLATAS'),
(5, 'AUTOS/MOTOS');

INSERT INTO `empresa` (`id`, `nombre`, `razonSocial`, `calle`, `no_exterior`, `no_interior`, `colonia`, `delegacion`, `cp`, `estado`, `pais`) VALUES
(1, 'Prestamil', 'ACTIVOS Y COMUNUCACACIONES PALMAS S.A. DE C.V', 'Calle de prueba 1', '12', '12', 'Colonia', 'Delegaciom', '03710', 'Ciudad de México', 'México'),
(2, 'Montefia', 'ACTIVOS Y COMUNUCACACIONES PALMAS S.A. DE C.V', 'Calle de prueba 3', '1', '1', 'Colonia de prueba', 'delegación', '03100', 'Ciudad de México', 'México'),
(3, 'Empeños Nacional de México', 'ACTIVOS Y COMUNUCACACIONES PALMAS S.A. DE C.V', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
(4, 'El Nacional', 'ACTIVOS Y COMUNUCACACIONES PALMAS S.A. DE C.V', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);

INSERT INTO `roles` (`id`, `rol`, `estatus`) VALUES
(1, 'Sistemas', 1),
(2, 'Administrador', 1),
(3, 'Cajero', 1),
(4, 'Valuador', 1),
(5, 'Gerente', 1),
(6, 'Cordinador', 1),
(7, 'Auditor', 1);

INSERT INTO `opciones` (`id`, `opcion`, `estatus`, `principalMenu`, `permiso`, `idPadre`, `icono`, `nombreIcono`) VALUES
(1, 'Usuarios', 1, 1, 0, NULL, 1, 'feather icon-user'),
(3, 'Catálogos', 1, 1, 0, NULL, 1, 'feather icon-grid'),
(4, 'Prendas', 1, 0, 0, 3, 1, NULL),
(5, 'Hardware', 1, 1, 0, NULL, 1, 'feather icon-printer'),
(6, 'Configuración', 1, 1, 0, NULL, 1, 'feather icon-settings'),
(7, 'Sucursal', 1, 0, 0, 6, 0, NULL),
(8, 'Parametros prestamo', 1, 0, 0, 6, 0, NULL),
(9, 'Parametros Generales', 1, 0, 0, 6, 0, NULL),
(10, 'Plazos y Periodos', 1, 0, 0, 6, 0, NULL),
(11, 'Empresas', 1, 0, 0, 3, 0, NULL),
(12, 'Turnos', 1, 1, 0, NULL, 0, 'feather icon-calendar'),
(13, 'Clientes', 1, 0, 0, 0, 0, 'feather icon-users'),
(14, 'Descuentos', 1, 0, 0, 3, 0, NULL);

INSERT INTO `configuraciones` (`id`, `configuracion`, `valorCadena`, `valorEntero`) VALUES
(1, 'diasCambioPassword', NULL, 90),
(2, 'roles_permitidos_apertura_turnos', '5', NULL);

INSERT INTO `parametros_sistema` (`id`, `descripcion`, `valor_cadena`, `valor_numerico`, `tipo_dato_interfaz`) VALUES
(1, 'Aviso Contrato', 'Para el desempeño de la(s) prenda(s)....', NULL, 'textarea'),
(2, 'Aviso Privacidad', 'La confidencialidad de sus datos', NULL, 'textarea'),
(3, '¿Tomar precio venta de la tabla?', NULL, 1.00, 'bool'),
(4, 'Factor incremento precio de venta alhajas', NULL, 3.00, 'number'),
(5, 'Factor incremento precio de venta varios', NULL, 3.00, 'number'),
(6, 'Descuento venta público alhajas', NULL, 30.00, 'number'),
(7, 'Descuento venta público varios', NULL, 30.00, 'number'),
(8, 'IVA', NULL, 16.00, 'number'),
(9, 'Prestamo máximo por Contrato', NULL, 0.00, 'number'),
(10, '¿Imprimir código de barras?', NULL, 1.00, 'bool'),
(11, '¿Imprime carta responsiva?', NULL, 1.00, 'bool'),
(12, 'Dias convenio apartados', NULL, 1.00, 'number'),
(13, 'Porcentaje mínimo apartados', NULL, 5.00, 'number'),
(14, 'Importe mínimo apartados', NULL, 10.00, 'number'),
(15, 'Porcentaje sanción apartados', NULL, 10.00, 'number');

INSERT INTO `catalogo` (`id`, `nombre`, `idTipoCatalogo`, `valorCadena`, `valorNumerico`, `valorFecha`, `esEditable`, `descrtipcion`) VALUES
(11, 'Credencial de elector', 1, 'Credencial de elector', NULL, NULL, 0, NULL),
(12, 'Pasaporte', 1, 'Pasaporte', NULL, NULL, 0, NULL),
(13, 'Licencia de manejo', 1, 'Licencia de manejo', NULL, NULL, 0, NULL),
(14, 'Cedula profesional', 1, 'Cedula profesional', NULL, NULL, 0, NULL),
(15, 'Cartilla S.M.N', 1, 'Cartilla S.M.N', NULL, NULL, 0, NULL);

INSERT INTO `cat_subtipo_prenda` (`id_atributo`, `id_tipo_prenda`, `nombre_atributo`) VALUES
(4, 1, 'Kilataje'),
(5, 1, 'Hechuras'),
(6, 3, 'Estandar'),
(7, 4, 'Tipo'),
(8, 5, 'Tipo'),
(9, 5, 'Marca'),
(10, 5, 'Modelo');

INSERT INTO `cat_valor_prenda` (`id_valor_atributo`, `id_atributo`, `clave`, `descripcion`, `kilataje`, `contiene_piedad`) VALUES
(2, 4, NULL, '6k', NULL, NULL),
(3, 4, NULL, '8k', NULL, NULL),
(4, 4, NULL, '10k', NULL, NULL),
(5, 4, NULL, '12k', NULL, NULL);

INSERT INTO `plazo` (`id`, `nombre`, `dias_por_periodo`, `numero_periodos`, `activo`, `creado_en`, `actualizado_en`) VALUES
(1, 'Semanal', 7, 12, 1, '2025-12-29 20:17:28', '2025-12-29 20:17:31'),
(2, 'Quincenal', 15, 12, 1, '2026-04-20 15:36:10', '2026-04-20 15:54:51'),
(3, 'Mensual', 30, 12, 1, '2026-04-20 15:36:53', '2026-04-20 15:55:06');

INSERT INTO `sucursal` (`id`, `numero_sucursal`, `nombre`, `idRazonSocial`, `calle`, `noExterior`, `noInterior`, `colonia`, `municipio`, `cp`, `estado`, `pais`, `lada`, `telefono`, `fechaApertura`, `rfc`, `fechaRegistroProfeco`, `fechaContratoProfeco`, `fecha_apertura`, `lunes`, `martes`, `miercoles`, `jueves`, `viernes`, `sabado`, `domingo`) VALUES
(1, 1, 'Sucursal 1', 1, 'calle de prueba 2', '12', '1', 'colonia de prueba', 'municipio', '03100', 'Morelos', 'México', 777, '123123123', '2025-04-01', 'hems890110aj0', '2025-03-19 00:00:00', '2025-03-19 00:00:00', '2025-11-25 14:48:27', 1, 1, 1, 1, 1, 1, 0);

INSERT INTO `prenda` (`id`, `idTipoPrenda`, `descripcion`, `con_piedra`, `clava_prenda`, `kilataje`) VALUES
(1, 1, '6k', NULL, NULL, 6),
(2, 1, '8k', NULL, NULL, 8),
(3, 1, '10k', NULL, NULL, 10),
(4, 1, '12k', NULL, NULL, 12),
(5, 1, '14k', NULL, NULL, 10),
(6, 1, '18k', NULL, NULL, 18),
(7, 1, '21k', NULL, NULL, 21),
(8, 1, '24k', NULL, NULL, 24),
(9, 1, 'Fundir', NULL, NULL, NULL),
(10, 1, 'Normal', NULL, NULL, NULL),
(11, 1, 'Especial', NULL, NULL, NULL),
(12, 3, 'Electrodomestico', NULL, NULL, NULL),
(13, 3, 'Celular', NULL, NULL, NULL),
(14, 3, 'Laptop', NULL, NULL, NULL),
(15, 4, '720', NULL, NULL, NULL),
(16, 4, '925', NULL, NULL, NULL),
(17, 5, 'Autos', NULL, NULL, NULL),
(18, 5, 'Motos', NULL, NULL, NULL);

INSERT INTO `plazo_prenda` (`plazo_id`, `tipo_prenda_id`) VALUES
(1, 1),
(2, 4),
(3, 3),
(3, 5);

INSERT INTO `plazo_hechura_alhaja` (`tabla_prestamo_id`, `kilataje`, `precio_base`, `porc_aumento`, `precio_prestamo`, `hechura`, `id_plazo`) VALUES
(4, 6, 118.84, 3.0000, 122.41, 'HF', 1),
(4, 8, 384.65, 3.0000, 396.19, 'HF', 1),
(4, 10, 488.26, 3.0000, 502.91, 'HF', 1),
(4, 12, 591.38, 3.0000, 609.12, 'HF', 1),
(4, 14, 695.74, 3.0000, 716.61, 'HF', 1),
(4, 18, 900.01, 3.0000, 927.01, 'HF', 1),
(4, 21, 1052.50, 3.0000, 1084.00, 'HF', 1),
(4, 24, 0.00, 0.0000, 0.00, 'HF', 1),
(5, 6, 118.84, 3.0000, 122.41, 'HN', 1),
(5, 8, 384.65, 3.0000, 396.19, 'HN', 1),
(5, 10, 488.26, 3.0000, 502.91, 'HN', 1),
(5, 12, 591.38, 3.0000, 609.12, 'HN', 1),
(5, 14, 695.74, 3.0000, 716.61, 'HN', 1),
(5, 18, 900.01, 3.0000, 927.01, 'HN', 1),
(5, 21, 1052.50, 3.0000, 1084.00, 'HN', 1),
(5, 24, 0.00, 0.0000, 0.00, 'HN', 1),
(6, 6, 118.84, 3.0000, 122.41, 'HE', 1),
(6, 8, 384.65, 3.0000, 396.19, 'HE', 1),
(6, 10, 488.26, 3.0000, 502.91, 'HE', 1),
(6, 12, 591.38, 3.0000, 609.12, 'HE', 1),
(6, 14, 695.74, 3.0000, 716.61, 'HE', 1),
(6, 18, 900.01, 3.0000, 927.01, 'HE', 1),
(6, 21, 1052.50, 3.0000, 1084.00, 'HE', 1),
(6, 24, 0.00, 0.0000, 0.00, 'HE', 1);

INSERT INTO `plazo_parametro` (`plazo_id`, `tipo_prenda_id`, `porc_interes`, `porc_almacen`, `porc_gastos_admin`, `porc_interes_total`, `cat`, `num_max_refrendos`, `porc_prestamo_s_avaluo`, `usa_avaluo_real`, `porc_prestamo_s_avaluo_real`, `cobrar_reposicion_contrato`, `reposicion_es_porcentaje`, `porc_reposicion`, `monto_reposicion`, `comision_por_venta_prenda`, `aplicar_sancion_por_periodo`, `dias_gracia_sin_interes`, `dias_antes_pase_venta`, `importe_min_prestamo`, `creado_en`, `actualizado_en`) VALUES
(1, 1, 1.1240, 0.6000, 0.0000, 1.7240, 0.0000, 0, 0.0000, 1, 0.0000, 1, 0, 0.0000, 0.00, 18.0000, 1, 2, 14, 50.00, '2025-12-30 02:46:37', '2025-12-30 02:46:37');

INSERT INTO `usuarios` (`id`, `nombreUsuario`, `password`, `nombre`, `apellidos`, `estatus`, `cambiarPassword`, `creado`, `ultimoLogin`, `idRol`, `fechaIni`, `fechaFin`, `vigencia`, `aplicaCambioPassword`, `fechaCambioPass`, `editable`, `session_token`, `ultima_actividad`, `inicio_sesion`, `updated_at`) VALUES
(1, 'sistemas', 'CfLvst1duF+e/+4gh87eJw==', 'Sistemas', 'Ignis', 1, 0, '2025-03-18 00:51:00', '2026-05-13 12:14:28', 5, NULL, NULL, 0, 0, '2025-11-23', 0, NULL, '2026-05-13 12:14:28', '2026-05-13 12:14:28', '2025-11-25 17:00:09.063008'),
(2, 'alexrampin', 'CfLvst1duF+e/+4gh87eJw==', 'Alejandro', 'Ramirez', 1, 0, '2025-03-24 00:12:55', '2026-04-19 03:54:41', 1, NULL, NULL, 0, 0, '2025-07-12', 0, NULL, '2026-04-19 03:54:41', '2026-04-19 03:54:41', '2025-11-25 22:59:33.376714'),
(3, 'emmanuel', 'CfLvst1duF+e/+4gh87eJw==', 'Emmanuel', 'Merida Toledo', 1, 0, '2025-03-24 00:17:43', '2026-02-02 19:07:49', 1, NULL, NULL, 0, 0, '2025-03-24', 0, NULL, '2026-02-02 19:07:49', '2026-02-02 19:07:49', '2025-11-23 23:25:51.715697'),
(4, 'Salvador', 'CfLvst1duF+e/+4gh87eJw==', 'Salvador', 'Hernandez', 1, 0, '2025-03-24 19:55:34', NULL, 1, '2025-07-12', '2025-07-13', 1, 1, '2025-07-12', 1, NULL, NULL, NULL, NULL),
(5, 'Test', 'CfLvst1duF+e/+4gh87eJw==', 'Test', 'Test', 0, 0, '2025-03-24 20:06:43', NULL, 2, '2025-01-03', '2025-05-08', 0, 0, '2025-04-18', 0, NULL, NULL, NULL, '2025-11-25 23:36:48.495258'),
(6, 'Test2', 'CfLvst1duF+e/+4gh87eJw==', 'Test 2', 'Test 2', 0, 0, '2025-04-18 22:33:59', NULL, 2, NULL, NULL, 0, 0, '2025-04-18', 0, NULL, NULL, NULL, '2025-11-23 23:20:18.140102'),
(7, 'cajero', 'CfLvst1duF+e/+4gh87eJw==', 'Cajero', 'Medrano', 1, 0, '2025-04-20 00:04:52', '2025-04-20 00:06:53', 3, NULL, NULL, 0, 0, '2025-04-20', 1, NULL, NULL, NULL, NULL),
(13, 'test1', 'BrKmNyw9in1nwHMcVnEQig==', 'test1', 'test1', 1, 0, '2025-11-25 17:09:30', '2025-11-25 17:10:43', 1, NULL, NULL, 0, 0, '2025-11-25', 0, NULL, '2025-11-25 17:10:43', '2025-11-25 17:10:43', '2025-11-25 17:10:25.883252'),
(18, 'test11', 'CfLvst1duF+e/+4gh87eJw==', 'test', 'test', 1, 0, '2025-11-25 22:55:08', NULL, 1, NULL, NULL, 0, 0, '2025-11-25', 0, NULL, NULL, NULL, NULL),
(19, 'hola', 'BrKmNyw9in1nwHMcVnEQig==', 'hola', 'hola', 0, 0, '2025-11-25 23:37:01', NULL, 4, NULL, NULL, 0, 0, '2025-11-25', 0, NULL, NULL, NULL, '2025-11-26 00:05:38.348523'),
(21, 'hola2', 'BrKmNyw9in1nwHMcVnEQig==', 'hola2', 'hola2', 0, 0, '2025-11-26 00:06:27', NULL, 2, NULL, NULL, 0, 0, '2025-11-26', 0, NULL, NULL, NULL, '2025-11-26 00:06:38.212827');

INSERT INTO `roles_opciones` (`idRol`, `idOpcion`) VALUES
(1, 1),
(1, 3),
(1, 4),
(1, 5),
(1, 6),
(1, 7),
(1, 8),
(1, 9),
(1, 10),
(1, 11),
(1, 12),
(1, 13),
(1, 14),
(2, 1),
(3, 1),
(5, 1),
(5, 3),
(5, 4),
(5, 5),
(5, 6),
(5, 7),
(5, 8),
(5, 9),
(5, 10),
(5, 11),
(5, 12),
(5, 13),
(5, 14);

INSERT INTO `turnos` (`id_turno`, `id_usuario`, `fecha_inicio`, `fecha_fin`, `activo`) VALUES
(1, 3, '2025-04-19 06:20:17', '2025-04-19 16:46:23', 0),
(2, 3, '2025-04-19 16:46:38', '2025-04-19 16:49:18', 0),
(3, 3, '2025-04-19 16:49:29', '2025-04-19 17:15:26', 0),
(4, 3, '2025-04-19 17:23:00', '2025-04-19 17:23:15', 0),
(5, 3, '2025-04-19 17:24:31', '2025-04-19 17:24:38', 0),
(6, 3, '2025-04-19 17:25:27', '2025-04-19 17:25:32', 0),
(7, 3, '2025-04-19 17:25:40', '2025-04-19 18:05:31', 0),
(8, 3, '2025-04-19 18:05:34', '2025-04-19 18:14:13', 0),
(9, 3, '2025-04-19 18:22:00', '2025-04-19 18:22:33', 0),
(10, 3, '2025-04-19 20:22:42', '2025-04-19 20:50:17', 0),
(11, 3, '2025-04-19 20:50:36', '2025-04-19 21:41:52', 0),
(12, 3, '2025-04-19 21:41:55', '2025-04-20 05:22:25', 0),
(13, 3, '2025-04-20 05:23:07', '2025-04-20 06:03:16', 0),
(14, 1, '2025-04-20 06:03:20', '2025-04-20 06:03:43', 0),
(15, 1, '2025-04-20 06:05:14', '2025-06-15 21:27:10', 0),
(16, 1, '2025-06-15 21:27:16', '2025-06-17 03:40:51', 0),
(17, 1, '2025-06-17 17:30:44', '2025-06-17 17:30:52', 0),
(18, 1, '2025-07-12 22:22:46', '2025-07-12 22:28:13', 0),
(19, 1, '2025-07-12 22:31:47', '2025-07-12 22:31:54', 0),
(20, 1, '2025-07-12 22:33:21', '2025-12-03 01:18:25', 0),
(21, 1, '2025-12-03 01:18:44', '2025-12-03 01:20:15', 0),
(22, 1, '2025-12-03 01:30:53', '2025-12-03 01:31:27', 0),
(23, 1, '2025-12-03 01:39:45', '2025-12-03 01:40:15', 0),
(24, 1, '2025-12-03 01:40:44', '2025-12-03 01:43:06', 0),
(25, 1, '2025-12-03 01:43:38', '2025-12-03 01:48:54', 0),
(26, 1, '2025-12-03 01:49:10', '2025-12-03 02:01:10', 0),
(27, 1, '2025-12-03 02:01:30', '2025-12-05 00:39:15', 0),
(28, 1, '2025-12-05 00:58:18', '2025-12-05 01:09:51', 0),
(29, 1, '2025-12-05 01:10:30', '2025-12-05 01:10:38', 0),
(30, 1, '2025-12-05 01:10:47', '2025-12-05 01:10:56', 0),
(31, 1, '2025-12-05 01:16:47', '2025-12-05 01:17:01', 0),
(32, 1, '2025-12-05 01:17:10', '2025-12-05 01:17:30', 0),
(33, 1, '2025-12-05 01:23:15', '2025-12-05 01:23:25', 0),
(34, 1, '2025-12-05 01:23:39', '2025-12-05 01:30:12', 0),
(35, 1, '2025-12-05 01:30:53', '2025-12-05 01:39:52', 0),
(36, 1, '2025-12-05 01:41:14', '2025-12-05 01:41:23', 0),
(37, 1, '2025-12-05 01:43:49', '2025-12-05 01:43:57', 0),
(38, 1, '2025-12-05 01:44:07', '2025-12-05 01:45:10', 0),
(39, 1, '2025-12-05 01:45:41', '2025-12-05 01:46:02', 0),
(40, 1, '2025-12-05 01:46:07', '2025-12-05 01:46:38', 0),
(41, 1, '2025-12-11 00:36:56', '2025-12-11 00:37:58', 0),
(42, 1, '2025-12-11 00:38:43', '2025-12-11 00:39:05', 0),
(43, 1, '2025-12-11 00:40:41', '2025-12-11 00:48:18', 0),
(44, 1, '2025-12-11 00:49:01', '2025-12-11 00:49:40', 0),
(45, 1, '2025-12-11 00:50:24', '2025-12-11 00:50:40', 0),
(46, 1, '2025-12-11 01:19:19', '2025-12-11 01:19:32', 0),
(47, 1, '2025-12-11 01:19:57', '2026-05-13 12:07:08', 0),
(48, 1, '2026-05-13 12:07:41', NULL, 1);
