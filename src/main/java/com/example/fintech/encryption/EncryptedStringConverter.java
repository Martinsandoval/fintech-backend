package com.example.fintech.encryption;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Aplicar explícitamente con @Convert(converter = EncryptedStringConverter.class)
 * en el campo — autoApply queda en false a propósito, no todo String de la
 * app debe cifrarse. Ver feature-specs/3-cifrado.md sección 2.
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

	private final FieldEncryptor fieldEncryptor;

	public EncryptedStringConverter(FieldEncryptor fieldEncryptor) {
		this.fieldEncryptor = fieldEncryptor;
	}

	@Override
	public String convertToDatabaseColumn(String attribute) {
		return fieldEncryptor.encrypt(attribute);
	}

	@Override
	public String convertToEntityAttribute(String dbData) {
		return fieldEncryptor.decrypt(dbData);
	}
}
