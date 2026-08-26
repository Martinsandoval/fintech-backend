package com.example.fintech.cheque;

import com.example.fintech.cliente.Cliente;
import com.example.fintech.cliente.ClienteService;
import com.example.fintech.common.ResourceNotFoundException;
import com.example.fintech.librador.Librador;
import com.example.fintech.librador.LibradorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ChequeService {

	private final ChequeRepository chequeRepository;
	private final ClienteService clienteService;
	private final LibradorService libradorService;

	public ChequeService(ChequeRepository chequeRepository, ClienteService clienteService,
			LibradorService libradorService) {
		this.chequeRepository = chequeRepository;
		this.clienteService = clienteService;
		this.libradorService = libradorService;
	}

	public List<Cheque> findAll() {
		return chequeRepository.findAll();
	}

	public Cheque findById(UUID id) {
		return chequeRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Cheque", id));
	}

	public List<Cheque> findByCliente(UUID clienteId) {
		return chequeRepository.findByClienteId(clienteId);
	}

	@Transactional
	public Cheque create(Cheque cheque) {
		if (cheque.getCliente() == null || cheque.getCliente().getId() == null) {
			throw new IllegalArgumentException("el cheque debe referenciar un cliente existente");
		}
		if (cheque.getLibrador() == null || cheque.getLibrador().getId() == null) {
			throw new IllegalArgumentException("el cheque debe referenciar un librador existente");
		}
		if (cheque.getFechaVencimiento().isBefore(cheque.getFechaEmision())) {
			throw new IllegalArgumentException("la fecha de vencimiento no puede ser anterior a la de emisión");
		}

		Cliente cliente = clienteService.findById(cheque.getCliente().getId());
		Librador librador = libradorService.findById(cheque.getLibrador().getId());

		cheque.setId(null);
		cheque.setCliente(cliente);
		cheque.setLibrador(librador);
		cheque.setEstado(EstadoCheque.EN_CARTERA);
		return chequeRepository.save(cheque);
	}

	@Transactional
	public Cheque actualizarEstado(UUID id, EstadoCheque nuevoEstado) {
		Cheque existente = findById(id);
		existente.setEstado(nuevoEstado);
		return existente;
	}

	@Transactional
	public void delete(UUID id) {
		if (!chequeRepository.existsById(id)) {
			throw new ResourceNotFoundException("Cheque", id);
		}
		chequeRepository.deleteById(id);
	}
}
