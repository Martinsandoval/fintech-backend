package com.example.fintech.librador;

import com.example.fintech.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class LibradorService {

	private final LibradorRepository libradorRepository;

	public LibradorService(LibradorRepository libradorRepository) {
		this.libradorRepository = libradorRepository;
	}

	public List<Librador> findAll() {
		return libradorRepository.findAll();
	}

	public Librador findById(UUID id) {
		return libradorRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Librador", id));
	}

	@Transactional
	public Librador create(Librador librador) {
		if (libradorRepository.existsByCuit(librador.getCuit())) {
			throw new IllegalArgumentException("ya existe un librador con CUIT " + librador.getCuit());
		}
		librador.setId(null);
		return libradorRepository.save(librador);
	}

	@Transactional
	public Librador update(UUID id, Librador cambios) {
		Librador existente = findById(id);
		existente.setRazonSocial(cambios.getRazonSocial());
		return existente;
	}

	@Transactional
	public void delete(UUID id) {
		if (!libradorRepository.existsById(id)) {
			throw new ResourceNotFoundException("Librador", id);
		}
		libradorRepository.deleteById(id);
	}
}
