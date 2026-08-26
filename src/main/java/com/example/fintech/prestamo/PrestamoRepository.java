package com.example.fintech.prestamo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PrestamoRepository extends JpaRepository<Prestamo, UUID> {

	List<Prestamo> findByClienteId(UUID clienteId);

	List<Prestamo> findByEstado(EstadoPrestamo estado);
}
