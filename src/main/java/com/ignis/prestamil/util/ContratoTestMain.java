package com.ignis.prestamil.util;

import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContratoTestMain {

	private static final String TEMPLATE_PATH = "jasper/contrato.jrxml";
	private static final Path OUTPUT_PDF = Paths.get("output", "contrato.pdf");

	public static void main(String[] args) {
		try {
			Files.createDirectories(OUTPUT_PDF.getParent());

			Map<String, Object> parameters = buildParameters();
			JasperPrint jasperPrint = compileAndFillReport(parameters);

			JasperExportManager.exportReportToPdfFile(jasperPrint, OUTPUT_PDF.toString());
			System.out.println("PDF generado en: " + OUTPUT_PDF.toAbsolutePath());
		} catch (IOException | JRException ex) {
			System.err.println("Error al generar el PDF de contrato: " + ex.getMessage());
			ex.printStackTrace();
		}
	}

	private static JasperPrint compileAndFillReport(Map<String, Object> parameters) throws JRException, IOException {
		try (InputStream jrxmlStream = ContratoTestMain.class.getClassLoader().getResourceAsStream(TEMPLATE_PATH)) {
			if (jrxmlStream == null) {
				throw new IOException("No se encontro el template JRXML en classpath: " + TEMPLATE_PATH);
			}

			JasperReport jasperReport = JasperCompileManager.compileReport(jrxmlStream);
			return JasperFillManager.fillReport(jasperReport, parameters, new JREmptyDataSource());
		}
	}

	private static Map<String, Object> buildParameters() {
		List<PrendaRow> prendas = new ArrayList<>();
		prendas.add(new PrendaRow("Anillo", "Oro 14k", "$8,000.00", "$4,800.00", "60%"));
		prendas.add(new PrendaRow("Laptop", "Dell XPS 13", "$18,000.00", "$10,800.00", "60%"));

		List<PagoRow> pagos = new ArrayList<>();
		pagos.add(new PagoRow("1", "$4,800.00", "$280.00", "$120.00", "$64.00", "$5,264.00", "$5,264.00", "Del 16/oct/2026 al 16/nov/2026", "NORMAL"));
		pagos.add(new PagoRow("2", "$4,800.00", "$280.00", "$120.00", "$64.00", "$5,264.00", "$5,264.00", "Del 16/oct/2026 al 16/nov/2026", "NORMAL"));
		pagos.add(new PagoRow("2 Dias de Gracia", "", "", "", "", "$233.28", "$1868.28", "Del 16/oct/2026 al 16/nov/2026", "GRACIA"));

		Map<String, Object> params = new HashMap<>();
		params.put("P_CLIENTE", "Juan Perez Martinez");
		params.put("P_COTITULAR", "Maria Lopez");
		params.put("P_BENEFICIARIO", "Ana Perez");
		params.put("P_CAT", "72.50%");
		params.put("P_TASA_ANUAL", "36.00%");
		params.put("P_MONTO_PRESTAMO", "$4,800.00");
		params.put("P_MONTO_TOTAL_PAGAR", "$5,264.00");
		params.put("P_NUMERO_CONTRATO", "PM-2026-0001");
		params.put("P_FECHA_FIRMA", "16/03/2026");
		params.put("P_MONTO_AVALUO", "$26,000.00");
		params.put("P_PORCENTAJE_PRESTAMO_AVALUO", "60%");
		params.put("P_FECHA_INICIO_COMERCIALIZACION", "17/04/2026");
		params.put("P_FECHA_LIMITE_FINIQUITO", "17/05/2026");
		params.put("P_DOMICILIO_SUCURSAL", "Av. Reforma 123, CDMX");
		params.put("P_TELEFONO_SUCURSAL", "55 1234 5678");
		params.put("P_PAGINA_INTERNET", "www.prestamil.com");
		params.put("P_NOMBRE_SUCURSAL", "Prestamil Centro");
		params.put("P_HORARIO_ATENCION", "Lun-Vie 09:00-18:00");
		params.put("P_IVA", "16%");
		params.put("P_CLIENTE_DIRECCION", "Calle Falsa 123, CDMX");
		params.put("P_CREDENCIAL_LECTOR", "INE 000011112222");
		params.put("P_GMS", "$120.00");
		params.put("P_RMO", "$0.00");
		params.put("P_REFRENDO", "$5,264.00");
		params.put("P_RESUMEN_INTERES", "$560.00");
		params.put("P_RESUMEN_CUSTODIA", "$240.00");
		params.put("P_RESUMEN_GTO_OPER", "$0.00");
		params.put("P_RESUMEN_MORATORIOS", "$0.00");
		params.put("P_PLAZO_PRESTAMO", "60 dias");
		params.put("P_AVALUO_TOTALES", "$26,000.00");
		List<PagoExtemporaneoRow> pagosExtemporaneos = new ArrayList<>();
		pagosExtemporaneos.add(new PagoExtemporaneoRow(
				"Pago extemporáneo 1",
				"$4,800.00", "$320.00", "$140.00", "$73.60",
				"$5,333.60", "17/11/2026", "$5,333.60"));
		pagosExtemporaneos.add(new PagoExtemporaneoRow(
				"Pago extemporáneo 2",
				"$4,800.00", "$360.00", "$160.00", "$83.20",
				"$5,403.20", "01/12/2026", "$5,403.20"));

		params.put("P_MOSTRAR_PAGO_EXTEMPORANEO", Boolean.TRUE);

		params.put("P_PRENDAS", new JRBeanCollectionDataSource(prendas));
		params.put("P_PAGOS", new JRBeanCollectionDataSource(pagos));
		params.put("P_PAGOS_EXTEMPORANEOS", new JRBeanCollectionDataSource(pagosExtemporaneos));

		return params;
	}
}
