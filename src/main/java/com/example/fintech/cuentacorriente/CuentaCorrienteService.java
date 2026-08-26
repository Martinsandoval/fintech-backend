package com.example.fintech.cuentacorriente;

import com.example.fintech.cliente.Cliente;
import com.example.fintech.cliente.ClienteService;
import com.example.fintech.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * No expone una operación para modificar el saldo directamente: el saldo es
 * la fuente de verdad y solo debe cambiar a través del registro de
 * movimientos_cta_cte (fuera de este scope), nunca por un PUT arbitrario.
 */
@Service
@Transactional(readOnly = true)
public class CuentaCorrienteService {

	private final CuentaCorrienteRepository cuentaCorrienteRepository;
	private final ClienteService clienteService;

	public CuentaCorrienteService(CuentaCorrienteRepository cuentaCorrienteRepository,
			ClienteService clienteService) {
		this.cuentaCorrienteRepository = cuentaCorrienteRepository;
		this.clienteService = clienteService;
	}

	public List<CuentaCorriente> findAll() {
		return cuentaCorrienteRepository.findAll();
	}

	public CuentaCorriente findById(UUID id) {
		return cuentaCorrienteRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("CuentaCorriente", id));
	}

	public List<CuentaCorriente> findByCliente(UUID clienteId) {
		return cuentaCorrienteRepository.findByClienteId(clienteId);
	}

	@Transactional
	public CuentaCorriente create(CuentaCorriente cuenta) {
		if (cuenta.getCliente() == null || cuenta.getCliente().getId() == null) {
			throw new IllegalArgumentException("la cuenta corriente debe referenciar un cliente existente");
		}
		String moneda = cuenta.getMoneda() == null ? "ARS" : cuenta.getMoneda();
		if (cuentaCorrienteRepository.existsByClienteIdAndMoneda(cuenta.getCliente().getId(), moneda)) {
			throw new IllegalArgumentException("el cliente ya tiene una cuenta corriente en " + moneda);
		}

		Cliente cliente = clienteService.findById(cuenta.getCliente().getId());
		cuenta.setId(null);
		cuenta.setCliente(cliente);
		cuenta.setMoneda(moneda);
		cuenta.setSaldo(java.math.BigDecimal.ZERO);
		return cuentaCorrienteRepository.save(cuenta);
	}

	@Transactional
	public void delete(UUID id) {
		if (!cuentaCorrienteRepository.existsById(id)) {
			throw new ResourceNotFoundException("CuentaCorriente", id);
		}
		cuentaCorrienteRepository.deleteById(id);
	}
}
