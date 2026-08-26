package com.example.fintech.contabilidad;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlanCuentaRepository extends JpaRepository<PlanCuenta, UUID> {

	Optional<PlanCuenta> findByCodigo(String codigo);

	boolean existsByCodigo(String codigo);
}
