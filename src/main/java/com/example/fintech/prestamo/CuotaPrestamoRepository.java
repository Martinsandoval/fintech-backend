package com.example.fintech.prestamo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CuotaPrestamoRepository extends JpaRepository<CuotaPrestamo, UUID> {

	List<CuotaPrestamo> findByPrestamoIdOrderByNumero(UUID prestamoId);
}
