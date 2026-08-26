package com.example.fintech.usuario;

import java.util.UUID;

/**
 * Lo que sale por HTTP en vez de la entidad Usuario — nunca lleva
 * passwordHash.
 */
public record UsuarioResponse(UUID id, String email, String nombre, String apellido, RolUsuario rol) {

	public static UsuarioResponse from(Usuario usuario) {
		return new UsuarioResponse(usuario.getId(), usuario.getEmail(), usuario.getNombre(), usuario.getApellido(),
				usuario.getRol());
	}
}
