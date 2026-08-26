package com.example.fintech.contabilidad;

import com.example.fintech.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AsientoContableService {

	private final AsientoContableRepository asientoContableRepository;
	private final MovimientoContableRepository movimientoContableRepository;
	private final PlanCuentaService planCuentaService;

	public AsientoContableService(AsientoContableRepository asientoContableRepository,
			MovimientoContableRepository movimientoContableRepository, PlanCuentaService planCuentaService) {
		this.asientoContableRepository = asientoContableRepository;
		this.movimientoContableRepository = movimientoContableRepository;
		this.planCuentaService = planCuentaService;
	}

	public List<AsientoContable> findAll() {
		return asientoContableRepository.findAll();
	}

	public AsientoContable findById(UUID id) {
		return asientoContableRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("AsientoContable", id));
	}

	public List<AsientoContable> findByOrigen(String origenTipo, UUID origenId) {
		return asientoContableRepository.findByOrigenTipoAndOrigenId(origenTipo, origenId);
	}

	@Transactional
	public AsientoContable create(AsientoContable asiento) {
		if (asientoContableRepository.existsByIdempotencyKey(asiento.getIdempotencyKey())) {
			throw new IllegalArgumentException(
					"ya existe un asiento contable con idempotency key " + asiento.getIdempotencyKey());
		}
		asiento.setId(null);
		return asientoContableRepository.save(asiento);
	}

	/**
	 * Para que un llamador pueda chequear "¿ya procesé esta operación?"
	 * antes de mutar su propio estado, no sólo antes de escribir el asiento.
	 */
	public boolean existePorIdempotencyKey(String idempotencyKey) {
		return asientoContableRepository.existsByIdempotencyKey(idempotencyKey);
	}

	public List<MovimientoContable> findLineas(UUID asientoId) {
		return movimientoContableRepository.findByAsientoId(asientoId);
	}

	/**
	 * Única forma de crear un asiento balanceado con sus líneas. Valida
	 * sum(debe) == sum(haber) y que cada línea sea debe xor haber antes de
	 * persistir nada; ver feature-specs/1-consistencia-datos.md sección 3.
	 * Idempotente por idempotencyKey igual que create().
	 */
	@Transactional
	public AsientoContable crearConLineas(String descripcion, String origenTipo, UUID origenId,
			String idempotencyKey, List<LineaContable> lineas) {
		Optional<AsientoContable> existente = asientoContableRepository.findByIdempotencyKey(idempotencyKey);
		if (existente.isPresent()) {
			return existente.get();
		}
		if (lineas.isEmpty()) {
			throw new IllegalArgumentException("un asiento contable requiere al menos una línea");
		}

		BigDecimal totalDebe = BigDecimal.ZERO;
		BigDecimal totalHaber = BigDecimal.ZERO;
		for (LineaContable linea : lineas) {
			boolean exclusivo = (linea.debe().compareTo(BigDecimal.ZERO) > 0)
					^ (linea.haber().compareTo(BigDecimal.ZERO) > 0);
			if (!exclusivo) {
				throw new IllegalArgumentException("cada línea del asiento debe tener debe>0 xor haber>0");
			}
			totalDebe = totalDebe.add(linea.debe());
			totalHaber = totalHaber.add(linea.haber());
		}
		if (totalDebe.compareTo(totalHaber) != 0) {
			throw new IllegalArgumentException(
					"el asiento no está balanceado: debe=" + totalDebe + " haber=" + totalHaber);
		}

		AsientoContable asiento = new AsientoContable();
		asiento.setDescripcion(descripcion);
		asiento.setOrigenTipo(origenTipo);
		asiento.setOrigenId(origenId);
		asiento.setIdempotencyKey(idempotencyKey);
		AsientoContable guardado = asientoContableRepository.save(asiento);

		for (LineaContable linea : lineas) {
			MovimientoContable movimiento = new MovimientoContable();
			movimiento.setAsiento(guardado);
			movimiento.setCuentaContable(planCuentaService.findById(linea.cuentaContableId()));
			movimiento.setDebe(linea.debe());
			movimiento.setHaber(linea.haber());
			movimientoContableRepository.save(movimiento);
		}
		return guardado;
	}

	@Transactional
	public void delete(UUID id) {
		if (!asientoContableRepository.existsById(id)) {
			throw new ResourceNotFoundException("AsientoContable", id);
		}
		asientoContableRepository.deleteById(id);
	}
}
