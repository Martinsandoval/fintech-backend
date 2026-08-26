package com.example.fintech.encryption;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldEncryptorTest {

	private final FieldEncryptor fieldEncryptor =
			new FieldEncryptor(Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()));

	@Test
	void cifra_y_descifra_a_la_misma_cadena() {
		String plano = "20304050617";
		String cifrado = fieldEncryptor.encrypt(plano);

		assertThat(cifrado).isNotEqualTo(plano);
		assertThat(fieldEncryptor.decrypt(cifrado)).isEqualTo(plano);
	}

	@Test
	void es_determinístico_mismo_texto_plano_mismo_cifrado() {
		String cifrado1 = fieldEncryptor.encrypt("20304050617");
		String cifrado2 = fieldEncryptor.encrypt("20304050617");

		assertThat(cifrado1).isEqualTo(cifrado2);
	}

	@Test
	void textos_planos_distintos_dan_cifrados_distintos() {
		String cifrado1 = fieldEncryptor.encrypt("20304050617");
		String cifrado2 = fieldEncryptor.encrypt("20304050618");

		assertThat(cifrado1).isNotEqualTo(cifrado2);
	}

	@Test
	void null_pasa_igual_sin_romper() {
		assertThat(fieldEncryptor.encrypt(null)).isNull();
		assertThat(fieldEncryptor.decrypt(null)).isNull();
	}

	@Test
	void un_valor_manipulado_falla_al_descifrar_en_vez_de_devolver_basura() {
		String cifrado = fieldEncryptor.encrypt("20304050617");
		byte[] bytes = Base64.getDecoder().decode(cifrado);
		bytes[bytes.length - 1] ^= 0x01;
		String manipulado = Base64.getEncoder().encodeToString(bytes);

		assertThatThrownBy(() -> fieldEncryptor.decrypt(manipulado))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void clave_corta_falla_al_construir_en_vez_de_arrancar_débil() {
		String claveCorta = Base64.getEncoder().encodeToString("muycorta".getBytes());

		assertThatThrownBy(() -> new FieldEncryptor(claveCorta))
				.isInstanceOf(IllegalStateException.class);
	}
}
