package com.example.fintech.cheque;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChequeRepository extends JpaRepository<Cheque, UUID> {

	List<Cheque> findByClienteId(UUID clienteId);

	List<Cheque> findByEstado(EstadoCheque estado);
}
