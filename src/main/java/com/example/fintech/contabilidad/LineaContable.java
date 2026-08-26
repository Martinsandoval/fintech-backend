package com.example.fintech.contabilidad;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Una línea de debe/haber para AsientoContableService.crearConLineas.
 * Exactamente uno de los dos montos debe ser mayor a cero.
 */
public record LineaContable(UUID cuentaContableId, BigDecimal debe, BigDecimal haber) {

	public static LineaContable debe(UUID cuentaContableId, BigDecimal monto) {
		return new LineaContable(cuentaContableId, monto, BigDecimal.ZERO);
	}

	public static LineaContable haber(UUID cuentaContableId, BigDecimal monto) {
		return new LineaContable(cuentaContableId, BigDecimal.ZERO, monto);
	}
}
