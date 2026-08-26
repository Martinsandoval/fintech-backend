package com.example.fintech.prestamo;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Genera el cronograma de cuotas a partir de monto/tasaAnual/plazoMeses/
 * sistemaAmortizacion. No persiste nada — PrestamoService.create guarda las
 * cuotas devueltas, en la misma transacción que ya banca el asiento de
 * originación. Ver feature-specs/6-motor-amortizacion.md.
 *
 * Las tres fórmulas simulan período a período (no sólo aplican la fórmula
 * de forma independiente en cada cuota) porque la última cuota necesita
 * saber el saldo pendiente *exacto* para poder cerrar el cronograma sin
 * que quede un resto de centavos por redondeo acumulado — ver sección 2
 * del spec.
 */
@Service
public class AmortizacionService {

	private static final int ESCALA_MONEDA = 2;
	private static final int ESCALA_CALCULO = 10;
	private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

	public List<CuotaPrestamo> generar(Prestamo prestamo) {
		BigDecimal monto = prestamo.getMonto();
		int n = prestamo.getPlazoMeses();
		BigDecimal tasaMensual = prestamo.getTasaAnual().divide(BigDecimal.valueOf(1200), ESCALA_CALCULO, REDONDEO);

		if (tasaMensual.compareTo(BigDecimal.ZERO) == 0 && prestamo.getSistemaAmortizacion() == SistemaAmortizacion.AMERICANO) {
			throw new IllegalArgumentException(
					"un préstamo AMERICANO con tasa 0 generaría cuotas intermedias de $0, no soportado");
		}

		return switch (prestamo.getSistemaAmortizacion()) {
			case FRANCES -> generarFrances(prestamo, monto, n, tasaMensual);
			case ALEMAN -> generarAleman(prestamo, monto, n, tasaMensual);
			case AMERICANO -> generarAmericano(prestamo, monto, n, tasaMensual);
		};
	}

	/** cuota = monto * (i * (1+i)^n) / ((1+i)^n - 1), fija salvo la última. */
	private List<CuotaPrestamo> generarFrances(Prestamo prestamo, BigDecimal monto, int n, BigDecimal i) {
		List<CuotaPrestamo> cuotas = new ArrayList<>(n);
		BigDecimal cuotaFija = cuotaFrancesa(monto, n, i);
		BigDecimal saldo = monto;

		for (int numero = 1; numero <= n; numero++) {
			BigDecimal interes = saldo.multiply(i).setScale(ESCALA_MONEDA, REDONDEO);
			BigDecimal montoCuota;
			if (numero < n) {
				montoCuota = cuotaFija;
				BigDecimal capital = montoCuota.subtract(interes);
				saldo = saldo.subtract(capital);
			} else {
				// última cuota: capital pendiente exacto + interés del último período,
				// absorbe el redondeo acumulado de las n-1 cuotas anteriores.
				montoCuota = saldo.add(interes);
			}
			cuotas.add(crearCuota(prestamo, numero, montoCuota));
		}
		return cuotas;
	}

	private BigDecimal cuotaFrancesa(BigDecimal monto, int n, BigDecimal i) {
		if (i.compareTo(BigDecimal.ZERO) == 0) {
			return monto.divide(BigDecimal.valueOf(n), ESCALA_MONEDA, REDONDEO);
		}
		BigDecimal factor = BigDecimal.ONE.add(i).pow(n);
		BigDecimal numerador = monto.multiply(i).multiply(factor);
		BigDecimal denominador = factor.subtract(BigDecimal.ONE);
		return numerador.divide(denominador, ESCALA_MONEDA, REDONDEO);
	}

	/** capital fijo = monto/n; interés variable sobre saldo, cuota total decrece. */
	private List<CuotaPrestamo> generarAleman(Prestamo prestamo, BigDecimal monto, int n, BigDecimal i) {
		List<CuotaPrestamo> cuotas = new ArrayList<>(n);
		BigDecimal capitalFijo = monto.divide(BigDecimal.valueOf(n), ESCALA_MONEDA, REDONDEO);
		BigDecimal saldo = monto;

		for (int numero = 1; numero <= n; numero++) {
			BigDecimal interes = saldo.multiply(i).setScale(ESCALA_MONEDA, REDONDEO);
			// última cuota: lo que quede de saldo, no el capital fijo — mismo motivo que en FRANCES.
			BigDecimal capital = numero < n ? capitalFijo : saldo;
			BigDecimal montoCuota = capital.add(interes);
			saldo = saldo.subtract(capital);
			cuotas.add(crearCuota(prestamo, numero, montoCuota));
		}
		return cuotas;
	}

	/** cuotas 1..n-1 sólo interés; la última, interés + capital completo. */
	private List<CuotaPrestamo> generarAmericano(Prestamo prestamo, BigDecimal monto, int n, BigDecimal i) {
		List<CuotaPrestamo> cuotas = new ArrayList<>(n);
		BigDecimal interesPeriodico = monto.multiply(i).setScale(ESCALA_MONEDA, REDONDEO);

		for (int numero = 1; numero <= n; numero++) {
			BigDecimal montoCuota = numero < n ? interesPeriodico : interesPeriodico.add(monto);
			cuotas.add(crearCuota(prestamo, numero, montoCuota));
		}
		return cuotas;
	}

	private CuotaPrestamo crearCuota(Prestamo prestamo, int numero, BigDecimal monto) {
		CuotaPrestamo cuota = new CuotaPrestamo();
		cuota.setPrestamo(prestamo);
		cuota.setNumero(numero);
		cuota.setMonto(monto);
		cuota.setFechaVencimiento(prestamo.getFechaOriginacion().plusMonths(numero));
		return cuota;
	}
}
