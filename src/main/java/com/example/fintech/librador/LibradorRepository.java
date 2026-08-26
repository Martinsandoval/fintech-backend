package com.example.fintech.librador;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LibradorRepository extends JpaRepository<Librador, UUID> {

	Optional<Librador> findByCuit(String cuit);

	boolean existsByCuit(String cuit);
}
