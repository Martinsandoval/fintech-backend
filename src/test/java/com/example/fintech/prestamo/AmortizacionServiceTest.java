package com.example.fintech.prestamo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica, para las tres fórmulas, lo que realmente importa según
 * feature-specs/6-motor-amortizacion.md sección 2: que la suma del capital
 * amortizado a lo largo del cronograma cierre exactamente contra el monto
 * original, sin resto de redondeo — porque
 * AsientoContableService.crearConLineas rechaza cualquier asiento
 * desbalanceado, y un cronograma que no cierra eventualmente produce uno.
 */
class AmortizacionServiceTest {

	private final AmortizacionService amortizacionService = new AmortizacionService();

	@Test
	void frances_cuota_fija_salvo_la_ultima_y_cierra_exacto() {
		Prestamo prestamo = prestamo(new BigDecimal("10000.00"), new BigDecimal("36.00"), 12,
				SistemaAmortizacion.FRANCES);
		List<CuotaPrestamo> cuotas = amortizacionService.generar(prestamo);

		assertThat(cuotas).hasSize(12);
		BigDecimal primeraCuota = cuotas.get(0).getMonto();
		for (int i = 0; i < cuotas.size() - 1; i++) {
			assertThat(cuotas.get(i).getMonto()).isEqualByComparingTo(primeraCuota);
		}
		assertCapitalCierraExacto(prestamo, cuotas);
	}

	@Test
	void aleman_capital_fijo_y_cuota_decreciente() {
		Prestamo prestamo = prestamo(new BigDecimal("12000.00"), new BigDecimal("24.00"), 6,
				SistemaAmortizacion.ALEMAN);
		List<CuotaPrestamo> cuotas = amortizacionService.generar(prestamo);

		assertThat(cuotas).hasSize(6);
		for (int i = 0; i < cuotas.size() - 1; i++) {
			assertThat(cuotas.get(i).getMonto()).isGreaterThan(cuotas.get(i + 1).getMonto());
		}
		assertCapitalCierraExacto(prestamo, cuotas);
	}

	@Test
	void americano_solo_interes_salvo_la_ultima_que_lleva_todo_el_capital() {
		Prestamo prestamo = prestamo(new BigDecimal("5000.00"), new BigDecimal("18.00"), 4,
				SistemaAmortizacion.AMERICANO);
		List<CuotaPrestamo> cuotas = amortizacionService.generar(prestamo);

		assertThat(cuotas).hasSize(4);
		BigDecimal interesPeriodico = cuotas.get(0).getMonto();
		for (int i = 0; i < cuotas.size() - 1; i++) {
			assertThat(cuotas.get(i).getMonto()).isEqualByComparingTo(interesPeriodico);
		}
		assertThat(cuotas.get(3).getMonto()).isEqualByComparingTo(interesPeriodico.add(prestamo.getMonto()));
		assertCapitalCierraExacto(prestamo, cuotas);
	}

	@Test
	void americano_con_tasa_cero_no_esta_soportado() {
		Prestamo prestamo = prestamo(new BigDecimal("1000.00"), BigDecimal.ZERO, 3, SistemaAmortizacion.AMERICANO);

		assertThatThrownBy(() -> amortizacionService.generar(prestamo))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void frances_con_tasa_cero_reparte_el_capital_en_partes_iguales() {
		Prestamo prestamo = prestamo(new BigDecimal("9000.00"), BigDecimal.ZERO, 3, SistemaAmortizacion.FRANCES);
		List<CuotaPrestamo> cuotas = amortizacionService.generar(prestamo);

		assertThat(cuotas).extracting(CuotaPrestamo::getMonto)
				.containsExactly(new BigDecimal("3000.00"), new BigDecimal("3000.00"), new BigDecimal("3000.00"));
	}

	@Test
	void las_fechas_de_vencimiento_avanzan_un_mes_por_cuota() {
		Prestamo prestamo = prestamo(new BigDecimal("3000.00"), new BigDecimal("12.00"), 3,
				SistemaAmortizacion.FRANCES);
		prestamo.setFechaOriginacion(LocalDate.of(2026, 1, 15));
		List<CuotaPrestamo> cuotas = amortizacionService.generar(prestamo);

		assertThat(cuotas).extracting(CuotaPrestamo::getFechaVencimiento)
				.containsExactly(LocalDate.of(2026, 2, 15), LocalDate.of(2026, 3, 15), LocalDate.of(2026, 4, 15));
	}

	/**
	 * "Cierra exacto" = capital amortizado total (cuota total menos interés
	 * de cada período, sumado) igual al monto original, centavo a centavo.
	 * No alcanza con sumar los montos de las cuotas (eso incluye interés);
	 * hay que reconstruir el capital de cada una igual que lo hace el
	 * service, restando el interés sobre el saldo remanente.
	 */
	private void assertCapitalCierraExacto(Prestamo prestamo, List<CuotaPrestamo> cuotas) {
		BigDecimal tasaMensual = prestamo.getTasaAnual().divide(BigDecimal.valueOf(1200), 10, java.math.RoundingMode.HALF_UP);
		BigDecimal saldo = prestamo.getMonto();
		for (CuotaPrestamo cuota : cuotas) {
			BigDecimal interes = saldo.multiply(tasaMensual).setScale(2, java.math.RoundingMode.HALF_UP);
			BigDecimal capital = cuota.getMonto().subtract(interes);
			saldo = saldo.subtract(capital);
		}
		assertThat(saldo).as("el saldo pendiente después de la última cuota tiene que quedar exactamente en cero")
				.isEqualByComparingTo(BigDecimal.ZERO);
	}

	private Prestamo prestamo(BigDecimal monto, BigDecimal tasaAnual, int plazoMeses, SistemaAmortizacion sistema) {
		Prestamo prestamo = new Prestamo();
		prestamo.setMonto(monto);
		prestamo.setTasaAnual(tasaAnual);
		prestamo.setPlazoMeses(plazoMeses);
		prestamo.setSistemaAmortizacion(sistema);
		prestamo.setFechaOriginacion(LocalDate.of(2026, 1, 1));
		return prestamo;
	}
}
