package com.example.fintech.cliente;

import com.example.fintech.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ClienteService {

	private final ClienteRepository clienteRepository;

	public ClienteService(ClienteRepository clienteRepository) {
		this.clienteRepository = clienteRepository;
	}

	public List<Cliente> findAll() {
		return clienteRepository.findAll();
	}

	public Cliente findById(UUID id) {
		return clienteRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Cliente", id));
	}

	@Transactional
	public Cliente create(Cliente cliente) {
		if (clienteRepository.existsByCuit(cliente.getCuit())) {
			throw new IllegalArgumentException("ya existe un cliente con CUIT " + cliente.getCuit());
		}
		cliente.setId(null);
		return clienteRepository.save(cliente);
	}

	@Transactional
	public Cliente update(UUID id, Cliente cambios) {
		Cliente existente = findById(id);
		existente.setRazonSocial(cambios.getRazonSocial());
		existente.setTipoPersona(cambios.getTipoPersona());
		existente.setEmail(cambios.getEmail());
		existente.setTelefono(cambios.getTelefono());
		existente.setDireccion(cambios.getDireccion());
		existente.setActivo(cambios.isActivo());
		return existente;
	}

	@Transactional
	public void delete(UUID id) {
		if (!clienteRepository.existsById(id)) {
			throw new ResourceNotFoundException("Cliente", id);
		}
		clienteRepository.deleteById(id);
	}
}
