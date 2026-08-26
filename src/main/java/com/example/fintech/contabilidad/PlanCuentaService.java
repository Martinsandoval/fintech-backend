package com.example.fintech.contabilidad;

import com.example.fintech.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PlanCuentaService {

	private final PlanCuentaRepository planCuentaRepository;

	public PlanCuentaService(PlanCuentaRepository planCuentaRepository) {
		this.planCuentaRepository = planCuentaRepository;
	}

	public List<PlanCuenta> findAll() {
		return planCuentaRepository.findAll();
	}

	public PlanCuenta findById(UUID id) {
		return planCuentaRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("PlanCuenta", id));
	}

	/**
	 * Para uso interno de servicios que arman asientos por código de cuenta
	 * (ej. "1.1.02"). Un código faltante es un error de configuración del
	 * plan de cuentas, no un 404 de cliente.
	 */
	public PlanCuenta findByCodigo(String codigo) {
		return planCuentaRepository.findByCodigo(codigo)
				.orElseThrow(() -> new IllegalStateException("no existe la cuenta contable con código " + codigo));
	}

	@Transactional
	public PlanCuenta create(PlanCuenta planCuenta) {
		if (planCuentaRepository.existsByCodigo(planCuenta.getCodigo())) {
			throw new IllegalArgumentException("ya existe una cuenta contable con código " + planCuenta.getCodigo());
		}
		planCuenta.setId(null);
		return planCuentaRepository.save(planCuenta);
	}

	@Transactional
	public PlanCuenta update(UUID id, PlanCuenta cambios) {
		PlanCuenta existente = findById(id);
		existente.setNombre(cambios.getNombre());
		existente.setTipo(cambios.getTipo());
		existente.setActiva(cambios.isActiva());
		return existente;
	}

	@Transactional
	public void delete(UUID id) {
		if (!planCuentaRepository.existsById(id)) {
			throw new ResourceNotFoundException("PlanCuenta", id);
		}
		planCuentaRepository.deleteById(id);
	}
}
