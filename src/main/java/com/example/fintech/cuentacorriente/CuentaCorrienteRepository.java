package com.example.fintech.cuentacorriente;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CuentaCorrienteRepository extends JpaRepository<CuentaCorriente, UUID> {

	List<CuentaCorriente> findByClienteId(UUID clienteId);

	Optional<CuentaCorriente> findByClienteIdAndMoneda(UUID clienteId, String moneda);

	boolean existsByClienteIdAndMoneda(UUID clienteId, String moneda);
}
