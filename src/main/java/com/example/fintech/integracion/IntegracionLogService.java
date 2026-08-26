package com.example.fintech.integracion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class IntegracionLogService {

	private final IntegracionLogRepository integracionLogRepository;

	public IntegracionLogService(IntegracionLogRepository integracionLogRepository) {
		this.integracionLogRepository = integracionLogRepository;
	}

	public List<IntegracionLog> findByServicio(String servicio) {
		return integracionLogRepository.findByServicioOrderByFechaDesc(servicio);
	}

	@Transactional
	public IntegracionLog registrar(String servicio, String endpoint, Map<String, Object> request,
			Map<String, Object> response, Integer estadoHttp, boolean exitoso, long duracionMs) {
		IntegracionLog log = new IntegracionLog();
		log.setServicio(servicio);
		log.setEndpoint(endpoint);
		log.setRequest(request);
		log.setResponse(response);
		log.setEstadoHttp(estadoHttp);
		log.setExitoso(exitoso);
		log.setDuracionMs((int) duracionMs);
		return integracionLogRepository.save(log);
	}
}
