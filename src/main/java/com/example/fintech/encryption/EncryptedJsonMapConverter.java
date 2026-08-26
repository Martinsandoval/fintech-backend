package com.example.fintech.encryption;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Igual que EncryptedStringConverter pero para columnas que del lado Java
 * son Map<String,Object> (ej. resultados_scoring.respuesta_raw): serializa
 * a JSON y cifra al guardar, descifra y parsea al leer. La columna deja de
 * ser jsonb (ver migración V2) porque el valor guardado ya no es JSON
 * válido, es el texto cifrado en base64.
 */
@Component
@Converter
public class EncryptedJsonMapConverter implements AttributeConverter<Map<String, Object>, String> {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private final FieldEncryptor fieldEncryptor;

	public EncryptedJsonMapConverter(FieldEncryptor fieldEncryptor) {
		this.fieldEncryptor = fieldEncryptor;
	}

	@Override
	public String convertToDatabaseColumn(Map<String, Object> attribute) {
		if (attribute == null) {
			return null;
		}
		try {
			return fieldEncryptor.encrypt(OBJECT_MAPPER.writeValueAsString(attribute));
		} catch (Exception e) {
			throw new IllegalStateException("no se pudo serializar el campo antes de cifrarlo", e);
		}
	}

	@Override
	public Map<String, Object> convertToEntityAttribute(String dbData) {
		if (dbData == null) {
			return null;
		}
		try {
			return OBJECT_MAPPER.readValue(fieldEncryptor.decrypt(dbData), MAP_TYPE);
		} catch (Exception e) {
			throw new IllegalStateException("no se pudo deserializar el campo después de descifrarlo", e);
		}
	}
}
