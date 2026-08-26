package com.example.fintech.auditoria;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Extiende Repository<T,ID> en vez de JpaRepository a propósito: sólo
 * declara los métodos de abajo, así que delete/deleteById no existen en
 * esta interfaz — no hay forma de llamarlos por error desde la app. Ver
 * feature-specs/4-auditoria.md sección 3.
 */
public interface AuditoriaAccionRepository extends Repository<AuditoriaAccion, UUID> {

	AuditoriaAccion save(AuditoriaAccion accion);

	Optional<AuditoriaAccion> findById(UUID id);

	List<AuditoriaAccion> findAllByOrderByFechaDesc();

	List<AuditoriaAccion> findByEntidadTipoAndEntidadIdOrderByFechaDesc(String entidadTipo, UUID entidadId);

	List<AuditoriaAccion> findByUsuarioIdOrderByFechaDesc(UUID usuarioId);
}
