package com.example.fintech.auditoria;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuditoriaAccionService {

	private final AuditoriaAccionRepository auditoriaAccionRepository;

	public AuditoriaAccionService(AuditoriaAccionRepository auditoriaAccionRepository) {
		this.auditoriaAccionRepository = auditoriaAccionRepository;
	}

	@Transactional
	public void registrar(UUID usuarioId, String usuarioEmail, String accion, String entidadTipo, UUID entidadId,
			String estadoAnterior, String estadoNuevo, String ipOrigen) {
		registrar(usuarioId, usuarioEmail, accion, entidadTipo, entidadId, estadoAnterior, estadoNuevo, null,
				ipOrigen);
	}

	@Transactional
	public void registrar(UUID usuarioId, String usuarioEmail, String accion, String entidadTipo, UUID entidadId,
			String estadoAnterior, String estadoNuevo, Map<String, Object> detalle, String ipOrigen) {
		auditoriaAccionRepository.save(new AuditoriaAccion(usuarioId, usuarioEmail, accion, entidadTipo, entidadId,
				estadoAnterior, estadoNuevo, detalle, ipOrigen));
	}

	public List<AuditoriaAccion> findAll() {
		return auditoriaAccionRepository.findAllByOrderByFechaDesc();
	}

	public List<AuditoriaAccion> findByEntidad(String entidadTipo, UUID entidadId) {
		return auditoriaAccionRepository.findByEntidadTipoAndEntidadIdOrderByFechaDesc(entidadTipo, entidadId);
	}

	public List<AuditoriaAccion> findByUsuario(UUID usuarioId) {
		return auditoriaAccionRepository.findByUsuarioIdOrderByFechaDesc(usuarioId);
	}
}
