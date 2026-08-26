package com.example.fintech.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface TokenRevocadoRepository extends JpaRepository<TokenRevocado, UUID> {

	long deleteByExpiraEnBefore(OffsetDateTime limite);
}
