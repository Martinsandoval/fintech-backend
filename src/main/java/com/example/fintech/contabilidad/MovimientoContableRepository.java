package com.example.fintech.contabilidad;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MovimientoContableRepository extends JpaRepository<MovimientoContable, UUID> {

	List<MovimientoContable> findByAsientoId(UUID asientoId);
}
