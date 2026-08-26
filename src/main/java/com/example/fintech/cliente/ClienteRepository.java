package com.example.fintech.cliente;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClienteRepository extends JpaRepository<Cliente, UUID> {

	Optional<Cliente> findByCuit(String cuit);

	boolean existsByCuit(String cuit);
}
