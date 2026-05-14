--liquibase formatted sql

--changeset emmanuel:001-schema comment:Initial schema - all tables
CREATE TABLE IF NOT EXISTS `tipoCatalogo` (
  `id` int(11) NOT NULL,
  `catalogo` varchar(50) DEFAULT NULL,
  `esEditable` smallint(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `tipo_prenda` (
  `id` int(11) NOT NULL,
  `tipo` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `tipoPrenda_pk` (`id`,`tipo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `empresa` (
  `id` int(11) NOT NULL,
  `nombre` varchar(50) DEFAULT NULL,
  `razonSocial` varchar(250) DEFAULT NULL,
  `calle` varchar(250) DEFAULT NULL,
  `no_exterior` varchar(10) DEFAULT NULL,
  `no_interior` varchar(10) DEFAULT NULL,
  `colonia` varchar(250) DEFAULT NULL,
  `delegacion` varchar(100) DEFAULT NULL,
  `cp` varchar(10) DEFAULT NULL,
  `estado` varchar(50) DEFAULT NULL,
  `pais` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `roles` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `rol` varchar(100) NOT NULL,
  `estatus` tinyint(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `roles_unique` (`rol`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `opciones` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `opcion` varchar(100) NOT NULL,
  `estatus` tinyint(1) NOT NULL,
  `principalMenu` tinyint(1) NOT NULL DEFAULT 1,
  `permiso` tinyint(1) NOT NULL DEFAULT 0,
  `idPadre` int(11) DEFAULT NULL,
  `icono` tinyint(1) NOT NULL,
  `nombreIcono` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `options_unique` (`opcion`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `configuraciones` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `configuracion` varchar(100) NOT NULL,
  `valorCadena` varchar(100) DEFAULT NULL,
  `valorEntero` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `parametros_sistema` (
  `id` int(11) NOT NULL,
  `descripcion` varchar(250) DEFAULT NULL,
  `valor_cadena` varchar(1000) DEFAULT NULL,
  `valor_numerico` decimal(10,2) DEFAULT NULL,
  `tipo_dato_interfaz` enum('bool','textarea','text','number') DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `bitacora` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `nombreUsuario` varchar(30) NOT NULL,
  `fecha` datetime NOT NULL,
  `valorViejo` varchar(5000) DEFAULT NULL,
  `valorNuevo` varchar(5000) NOT NULL,
  `tipoMov` varchar(100) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `parametroEmpeño` (
  `id` int(11) NOT NULL,
  `avisoPrivacidad` varchar(500) DEFAULT NULL,
  `avisoContrato` varchar(500) DEFAULT NULL,
  `tomarPrecioTabla` smallint(6) DEFAULT NULL,
  `factorIncrementoVentaAlhaja` decimal(10,2) DEFAULT NULL,
  `factorIncrementoVentavarios` decimal(10,2) DEFAULT NULL,
  `descuentoVentaAlhaja` decimal(10,2) DEFAULT NULL,
  `descuentoVentaVarios` decimal(10,2) DEFAULT NULL,
  `iva` decimal(10,2) DEFAULT NULL,
  `prestamoMaximoCotrato` decimal(10,2) DEFAULT NULL,
  `debeImprimirCB` smallint(6) DEFAULT NULL,
  `diasConvenioApartado` int(11) DEFAULT NULL,
  `porcentajeSancionApartado` decimal(10,2) DEFAULT NULL,
  `descuentoPrendasApartadas` decimal(10,2) DEFAULT NULL,
  `importeMinimoApartado` decimal(10,2) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `catalogo` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(100) DEFAULT NULL,
  `idTipoCatalogo` int(11) DEFAULT NULL,
  `valorCadena` varchar(100) DEFAULT NULL,
  `valorNumerico` decimal(10,2) DEFAULT NULL,
  `valorFecha` datetime DEFAULT NULL,
  `esEditable` smallint(6) DEFAULT NULL,
  `descrtipcion` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `catalogo_pk` (`nombre`),
  KEY `catalogo_tipo_catalogo_id_fk` (`idTipoCatalogo`),
  CONSTRAINT `catalogo_tipo_catalogo_id_fk` FOREIGN KEY (`idTipoCatalogo`) REFERENCES `tipoCatalogo` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `cat_subtipo_prenda` (
  `id_atributo` int(11) NOT NULL AUTO_INCREMENT,
  `id_tipo_prenda` int(11) NOT NULL,
  `nombre_atributo` varchar(50) NOT NULL,
  PRIMARY KEY (`id_atributo`),
  KEY `cat_subtipo_prenda_ibfk_1` (`id_tipo_prenda`),
  CONSTRAINT `cat_subtipo_prenda_ibfk_1` FOREIGN KEY (`id_tipo_prenda`) REFERENCES `tipo_prenda` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `cat_valor_prenda` (
  `id_valor_atributo` int(11) NOT NULL AUTO_INCREMENT,
  `id_atributo` int(11) NOT NULL,
  `clave` int(11) DEFAULT NULL,
  `descripcion` varchar(100) NOT NULL,
  `kilataje` int(11) DEFAULT NULL,
  `contiene_piedad` smallint(6) DEFAULT NULL,
  PRIMARY KEY (`id_valor_atributo`),
  KEY `id_atributo` (`id_atributo`),
  CONSTRAINT `cat_valor_prenda_ibfk_1` FOREIGN KEY (`id_atributo`) REFERENCES `cat_subtipo_prenda` (`id_atributo`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `sucursal` (
  `id` int(11) NOT NULL,
  `numero_sucursal` int(11) DEFAULT NULL,
  `nombre` varchar(50) DEFAULT NULL,
  `idRazonSocial` int(11) DEFAULT NULL,
  `calle` varchar(250) DEFAULT NULL,
  `noExterior` varchar(25) DEFAULT NULL,
  `noInterior` varchar(25) DEFAULT NULL,
  `colonia` varchar(250) DEFAULT NULL,
  `municipio` varchar(250) DEFAULT NULL,
  `cp` varchar(5) DEFAULT NULL,
  `estado` varchar(50) DEFAULT NULL,
  `pais` varchar(20) DEFAULT NULL,
  `lada` int(11) DEFAULT NULL,
  `telefono` varchar(50) DEFAULT NULL,
  `fechaApertura` date DEFAULT NULL,
  `rfc` varchar(20) DEFAULT NULL,
  `fechaRegistroProfeco` datetime DEFAULT NULL,
  `fechaContratoProfeco` datetime DEFAULT NULL,
  `fecha_apertura` datetime DEFAULT NULL,
  `lunes` smallint(6) DEFAULT NULL,
  `martes` smallint(6) DEFAULT NULL,
  `miercoles` smallint(6) DEFAULT NULL,
  `jueves` smallint(6) DEFAULT NULL,
  `viernes` smallint(6) DEFAULT NULL,
  `sabado` smallint(6) DEFAULT NULL,
  `domingo` smallint(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `sucursal_empresa_id_fk` (`idRazonSocial`),
  CONSTRAINT `sucursal_empresa_id_fk` FOREIGN KEY (`idRazonSocial`) REFERENCES `empresa` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `plazo` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(80) NOT NULL,
  `dias_por_periodo` int(11) NOT NULL,
  `numero_periodos` int(11) NOT NULL,
  `activo` tinyint(4) NOT NULL DEFAULT 1,
  `creado_en` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `actualizado_en` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `plazo_prenda` (
  `plazo_id` int(11) NOT NULL,
  `tipo_prenda_id` int(11) NOT NULL,
  PRIMARY KEY (`plazo_id`,`tipo_prenda_id`),
  KEY `fk_pc_tipo_prenda` (`tipo_prenda_id`),
  CONSTRAINT `fk_pc_plazo` FOREIGN KEY (`plazo_id`) REFERENCES `plazo` (`id`),
  CONSTRAINT `fk_pc_tipo_prenda` FOREIGN KEY (`tipo_prenda_id`) REFERENCES `tipo_prenda` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `plazo_parametro` (
  `plazo_id` int(11) NOT NULL,
  `tipo_prenda_id` int(11) NOT NULL,
  `porc_interes` decimal(9,4) NOT NULL DEFAULT 0.0000,
  `porc_almacen` decimal(9,4) NOT NULL DEFAULT 0.0000,
  `porc_gastos_admin` decimal(9,4) NOT NULL DEFAULT 0.0000,
  `porc_interes_total` decimal(9,4) NOT NULL DEFAULT 0.0000,
  `cat` decimal(9,4) NOT NULL DEFAULT 0.0000,
  `num_max_refrendos` int(11) NOT NULL DEFAULT 0,
  `porc_prestamo_s_avaluo` decimal(9,4) NOT NULL DEFAULT 0.0000,
  `usa_avaluo_real` tinyint(4) NOT NULL DEFAULT 0,
  `porc_prestamo_s_avaluo_real` decimal(9,4) NOT NULL DEFAULT 0.0000,
  `cobrar_reposicion_contrato` tinyint(4) NOT NULL DEFAULT 0,
  `reposicion_es_porcentaje` tinyint(4) NOT NULL DEFAULT 0,
  `porc_reposicion` decimal(9,4) NOT NULL DEFAULT 0.0000,
  `monto_reposicion` decimal(18,2) NOT NULL DEFAULT 0.00,
  `comision_por_venta_prenda` decimal(9,4) NOT NULL DEFAULT 0.0000,
  `aplicar_sancion_por_periodo` tinyint(4) NOT NULL DEFAULT 0,
  `dias_gracia_sin_interes` int(11) NOT NULL DEFAULT 0,
  `dias_antes_pase_venta` int(11) NOT NULL DEFAULT 0,
  `importe_min_prestamo` decimal(18,2) NOT NULL DEFAULT 0.00,
  `creado_en` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `actualizado_en` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`plazo_id`,`tipo_prenda_id`),
  CONSTRAINT `fk_pp_plazo_categoria` FOREIGN KEY (`plazo_id`, `tipo_prenda_id`) REFERENCES `plazo_prenda` (`plazo_id`, `tipo_prenda_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `plazo_hechura_alhaja` (
  `tabla_prestamo_id` int(11) NOT NULL,
  `kilataje` int(11) NOT NULL,
  `precio_base` decimal(18,2) NOT NULL DEFAULT 0.00,
  `porc_aumento` decimal(9,4) NOT NULL DEFAULT 0.0000,
  `precio_prestamo` decimal(18,2) NOT NULL DEFAULT 0.00,
  `hechura` varchar(2) DEFAULT NULL,
  `id_plazo` int(11) DEFAULT NULL,
  KEY `plazo_hechura_alhaja_plazo_id_fk` (`id_plazo`),
  CONSTRAINT `plazo_hechura_alhaja_plazo_id_fk` FOREIGN KEY (`id_plazo`) REFERENCES `plazo` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `prenda` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `idTipoPrenda` int(11) DEFAULT NULL,
  `descripcion` varchar(100) NOT NULL,
  `con_piedra` smallint(6) DEFAULT NULL,
  `clava_prenda` varchar(10) DEFAULT NULL,
  `kilataje` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `prenda_tipoPrenda_id_fk` (`idTipoPrenda`),
  CONSTRAINT `prenda_tipoPrenda_id_fk` FOREIGN KEY (`idTipoPrenda`) REFERENCES `tipo_prenda` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `usuarios` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `nombreUsuario` varchar(30) NOT NULL,
  `password` varchar(100) NOT NULL,
  `nombre` varchar(100) NOT NULL,
  `apellidos` varchar(100) NOT NULL,
  `estatus` tinyint(1) NOT NULL,
  `cambiarPassword` tinyint(1) NOT NULL,
  `creado` datetime NOT NULL,
  `ultimoLogin` datetime DEFAULT NULL,
  `idRol` int(11) NOT NULL,
  `fechaIni` date DEFAULT NULL,
  `fechaFin` date DEFAULT NULL,
  `vigencia` tinyint(1) NOT NULL DEFAULT 0,
  `aplicaCambioPassword` tinyint(1) NOT NULL DEFAULT 1,
  `fechaCambioPass` date NOT NULL,
  `editable` tinyint(1) NOT NULL DEFAULT 1,
  `session_token` varchar(100) DEFAULT NULL,
  `ultima_actividad` datetime DEFAULT NULL,
  `inicio_sesion` datetime DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `usuarios_unique` (`nombreUsuario`),
  KEY `users_rols_FK` (`idRol`),
  CONSTRAINT `users_rols_FK` FOREIGN KEY (`idRol`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `turnos` (
  `id_turno` int(11) NOT NULL AUTO_INCREMENT,
  `id_usuario` int(11) NOT NULL,
  `fecha_inicio` datetime NOT NULL,
  `fecha_fin` datetime DEFAULT NULL,
  `activo` tinyint(1) DEFAULT 1,
  PRIMARY KEY (`id_turno`),
  KEY `id_usuario` (`id_usuario`),
  CONSTRAINT `turnos_ibfk_1` FOREIGN KEY (`id_usuario`) REFERENCES `usuarios` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=49 DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `direcciones` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `tipo_direccion` enum('Fiscal','Particular','Laboral') NOT NULL,
  `calle` varchar(200) NOT NULL,
  `numero_exterior` varchar(10) NOT NULL,
  `numero_interior` varchar(10) DEFAULT NULL,
  `colonia` varchar(100) NOT NULL,
  `ciudad` varchar(100) NOT NULL,
  `estado` varchar(50) NOT NULL,
  `codigo_postal` varchar(5) NOT NULL,
  `referencias` text,
  `es_verificada` tinyint(1) DEFAULT 0,
  `fecha_registro` date NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_direcciones_verificadas` (`es_verificada`,`tipo_direccion`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `clientes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(100) NOT NULL,
  `apellido_paterno` varchar(150) NOT NULL,
  `apellido_materno` varchar(150) NOT NULL,
  `telefono` varchar(15) NOT NULL,
  `curp` varchar(18) DEFAULT NULL,
  `rfc` varchar(13) DEFAULT NULL,
  `activo` tinyint(1) DEFAULT 1,
  `direccion_id` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `telefono` (`telefono`),
  KEY `direccion_id` (`direccion_id`),
  CONSTRAINT `clientes_ibfk_1` FOREIGN KEY (`direccion_id`) REFERENCES `direcciones` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `roles_opciones` (
  `idRol` int(11) NOT NULL,
  `idOpcion` int(11) NOT NULL,
  PRIMARY KEY (`idRol`,`idOpcion`),
  KEY `rols_options_options_FK` (`idOpcion`),
  CONSTRAINT `rols_options_options_FK` FOREIGN KEY (`idOpcion`) REFERENCES `opciones` (`id`),
  CONSTRAINT `rols_options_rols_FK` FOREIGN KEY (`idRol`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `SPRING_SESSION` (
  `PRIMARY_ID` char(36) NOT NULL,
  `SESSION_ID` char(36) NOT NULL,
  `CREATION_TIME` bigint(20) NOT NULL,
  `LAST_ACCESS_TIME` bigint(20) NOT NULL,
  `MAX_INACTIVE_INTERVAL` int(11) NOT NULL,
  `EXPIRY_TIME` bigint(20) NOT NULL,
  `PRINCIPAL_NAME` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`PRIMARY_ID`),
  UNIQUE KEY `SPRING_SESSION_IX1` (`SESSION_ID`),
  KEY `SPRING_SESSION_IX2` (`EXPIRY_TIME`),
  KEY `SPRING_SESSION_IX3` (`PRINCIPAL_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `SPRING_SESSION_ATTRIBUTES` (
  `SESSION_PRIMARY_ID` char(36) NOT NULL,
  `ATTRIBUTE_NAME` varchar(150) NOT NULL,
  `ATTRIBUTE_BYTES` blob NOT NULL,
  PRIMARY KEY (`SESSION_PRIMARY_ID`,`ATTRIBUTE_NAME`),
  CONSTRAINT `SPRING_SESSION_ATTRIBUTES_FK` FOREIGN KEY (`SESSION_PRIMARY_ID`) REFERENCES `SPRING_SESSION` (`PRIMARY_ID`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

--changeset emmanuel:001-trigger splitStatements:false
CREATE TRIGGER `set_fecha_direccion` BEFORE INSERT ON `direcciones` FOR EACH ROW
BEGIN
    SET NEW.fecha_registro = CURDATE();
END;
