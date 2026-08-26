package com.example.fintech.common;

import java.util.UUID;

public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String entity, UUID id) {
		super(entity + " no encontrado: " + id);
	}
}
