package com.example.fintech.usuario;

public record AuthResponse(String accessToken, String tokenType, long expiresInMs, UsuarioResponse usuario) {
}
