package com.ignis.prestamil.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Precio base del gramo de oro de 24 quilates vigente por sucursal.
 * Al actualizarse, dispara el recálculo de todas las tablas de hechura/kilataje.
 *
 * Incluye ademas el factor de ajuste por hechura (Fundir/Normal/Especial), un
 * multiplicador adicional configurable por sucursal que se aplica sobre el precio
 * de prestamo tanto en la pantalla "Configuracion del Oro" ({@code OroTablaPrestamoService})
 * como en el monto real ofrecido en contratos nuevos ({@code PlazoHechuraAlhaja.precioBase},
 * via {@code PlazoService.recalcularRegistros}). Reinstaurado en el quick task 260726-lin
 * tras haber sido eliminado en Phase 4.1 (D-17) por considerarse codigo muerto; el usuario
 * confirmo que SI aplica en la operacion real de COCAE.
 */
@Entity
@Table(name = "precio_oro")
@Getter
@Setter
public class PrecioOro {

    /** Factor neutro (sin ajuste): 100.0000%. */
    public static final BigDecimal FACTOR_NEUTRO = new BigDecimal("100.0000");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "sucursal_id", nullable = false, unique = true)
    private Integer sucursalId;

    @Column(name = "precio_gramo_24k", nullable = false, precision = 12, scale = 4)
    private BigDecimal precioGramo24k = BigDecimal.ZERO;

    @Column(name = "calcular_sobre", nullable = false, length = 20)
    private String calcularSobre = "PRESTAMO";

    @Column(name = "base_kilataje", nullable = false)
    private Integer baseKilataje = 24;

    @Column(name = "factor_fundir", nullable = false, precision = 7, scale = 4)
    private BigDecimal factorFundir = new BigDecimal("100.0000");

    @Column(name = "factor_normal", nullable = false, precision = 7, scale = 4)
    private BigDecimal factorNormal = new BigDecimal("100.0000");

    @Column(name = "factor_especial", nullable = false, precision = 7, scale = 4)
    private BigDecimal factorEspecial = new BigDecimal("100.0000");

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @Column(name = "actualizado_por", length = 80)
    private String actualizadoPor;

    /**
     * Devuelve el factor de ajuste configurado para una hechura, tolerante a nulos.
     * Helper estatico compartido por {@code OroTablaPrestamoService} y {@code PlazoService}
     * para que ambos motores de calculo apliquen exactamente el mismo factor.
     *
     * @param precio  precio del oro vigente de la sucursal; puede ser null
     * @param hechura clave de hechura ("F", "N" o "E")
     * @return el factor porcentual configurado, o 100.0000 (neutro) si no hay precio,
     *         el factor es null o la hechura es desconocida. Nunca lanza.
     */
    public static BigDecimal factorDeHechura(PrecioOro precio, String hechura) {
        if (precio == null || hechura == null) {
            return FACTOR_NEUTRO;
        }
        BigDecimal factor = switch (hechura) {
            case "F" -> precio.getFactorFundir();
            case "N" -> precio.getFactorNormal();
            case "E" -> precio.getFactorEspecial();
            default -> null;
        };
        return factor != null ? factor : FACTOR_NEUTRO;
    }
}
