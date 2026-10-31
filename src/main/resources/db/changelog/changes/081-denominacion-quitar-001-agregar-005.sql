--liquibase formatted sql

-- ============================================================================
-- CAJ-01d (PLAN-CORTE-CAJA, Q-10, Jorge 2026-10-30): quitar la moneda de 0.01 y
-- agregar la de 0.05.
--
-- 0.01 no se borra porque puede estar en desgloses de traspasos ya guardados: queda
-- inactiva y pasa al final del orden (15). 0.05 toma el lugar 14, despues de 0.10.
-- Los desgloses nuevos, la pantalla y el ticket solo usan denominaciones activas
-- (DenominacionRepository.findByActivaTrueOrderByOrdenAsc); un ticket viejo que
-- trae 0.01 la sigue mostrando, al final.
--
-- El rollback de 081-2 falla si 0.05 ya se uso en algun traspaso (FK desde
-- traspaso_caja_denominacion): a proposito, para no perder desgloses.
-- ============================================================================

--changeset emm-a:081-1
--comment: CAJ-01d - Q-10: la moneda de 0.01 queda inactiva y al final del orden.
UPDATE denominacion SET activa = 0, orden = 15 WHERE valor = 0.01;
--rollback UPDATE denominacion SET activa = 1, orden = 14 WHERE valor = 0.01;

--changeset emm-a:081-2
--comment: CAJ-01d - Q-10: moneda de 0.05, despues de 0.10.
INSERT INTO denominacion (valor, tipo, orden, activa) VALUES (0.05, 'MONEDA', 14, 1);
--rollback DELETE FROM denominacion WHERE valor = 0.05;
