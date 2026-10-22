package com.ignis.prestamil.util;

import com.ignis.prestamil.model.PartidaContrato;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** D-09: contrato y ticket muestran "CLAVE - Descripción general" y abajo la descripción específica. */
class FormatoDocumentoTest {

    @Test
    void descripcionPartida_claveGeneralYEspecifica_enDosRenglones() {
        PartidaContrato p = partida("ESC14", "ESCLAVA 14K", "Esclava con mariposa, broche roto");

        assertThat(FormatoDocumento.descripcionPartida(p))
                .isEqualTo("ESC14 - ESCLAVA 14K\nEsclava con mariposa, broche roto");
    }

    @Test
    void descripcionPartida_historicaSinSnapshot_muestraClaveYEspecifica() {
        PartidaContrato p = partida("Esclava", null, "Esclava rota");

        assertThat(FormatoDocumento.descripcionPartida(p)).isEqualTo("Esclava\nEsclava rota");
    }

    @Test
    void descripcionPartida_sinCatalogo_soloEspecifica() {
        PartidaContrato p = partida(null, null, "Pulsera de plata tejida");

        assertThat(FormatoDocumento.descripcionPartida(p)).isEqualTo("Pulsera de plata tejida");
    }

    private static PartidaContrato partida(String clave, String general, String especifica) {
        PartidaContrato p = new PartidaContrato();
        p.setClavePrenda(clave);
        p.setDescripcionGeneral(general);
        p.setDescripcion(especifica);
        return p;
    }
}
