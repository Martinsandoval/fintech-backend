package com.example.fintech.encryption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;

/**
 * Cifrado determinístico a nivel de campo: AES-256-GCM con el nonce
 * derivado por HMAC-SHA256 del texto plano en vez de aleatorio. Mismo texto
 * plano siempre produce el mismo texto cifrado — necesario para que
 * columnas UNIQUE y búsquedas por igualdad (findByCuit) sigan funcionando
 * sobre el valor cifrado. Ver feature-specs/3-cifrado.md sección 2.
 */
@Component
public class FieldEncryptor {

	private static final String AES = "AES";
	private static final String AES_GCM = "AES/GCM/NoPadding";
	private static final String HMAC_SHA256 = "HmacSHA256";
	private static final int GCM_TAG_LENGTH_BITS = 128;
	private static final int NONCE_LENGTH_BYTES = 12;
	private static final int KEY_LENGTH_BYTES = 32;

	private final SecretKeySpec encryptionKey;
	private final SecretKeySpec nonceDerivationKey;

	public FieldEncryptor(@Value("${app.encryption.field-key}") String masterKeyBase64) {
		byte[] masterKey = Base64.getDecoder().decode(masterKeyBase64);
		if (masterKey.length < KEY_LENGTH_BYTES) {
			throw new IllegalStateException(
					"app.encryption.field-key debe decodificar a al menos 32 bytes (256 bits); tiene "
							+ masterKey.length);
		}
		this.encryptionKey = new SecretKeySpec(deriveSubkey(masterKey, "field-encryption-enc-key"), AES);
		this.nonceDerivationKey = new SecretKeySpec(deriveSubkey(masterKey, "field-encryption-nonce-key"), AES);
	}

	public String encrypt(String plaintext) {
		if (plaintext == null) {
			return null;
		}
		try {
			byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
			byte[] nonce = deriveNonce(plaintextBytes);

			Cipher cipher = Cipher.getInstance(AES_GCM);
			cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
			byte[] ciphertext = cipher.doFinal(plaintextBytes);

			byte[] payload = new byte[nonce.length + ciphertext.length];
			System.arraycopy(nonce, 0, payload, 0, nonce.length);
			System.arraycopy(ciphertext, 0, payload, nonce.length, ciphertext.length);
			return Base64.getEncoder().encodeToString(payload);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("no se pudo cifrar el campo", e);
		}
	}

	public String decrypt(String encoded) {
		if (encoded == null) {
			return null;
		}
		try {
			byte[] payload = Base64.getDecoder().decode(encoded);
			byte[] nonce = Arrays.copyOfRange(payload, 0, NONCE_LENGTH_BYTES);
			byte[] ciphertext = Arrays.copyOfRange(payload, NONCE_LENGTH_BYTES, payload.length);

			Cipher cipher = Cipher.getInstance(AES_GCM);
			cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
			byte[] plaintextBytes = cipher.doFinal(ciphertext);
			return new String(plaintextBytes, StandardCharsets.UTF_8);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(
					"no se pudo descifrar el campo (clave incorrecta o valor corrupto/manipulado)", e);
		}
	}

	private byte[] deriveNonce(byte[] plaintextBytes) throws GeneralSecurityException {
		Mac mac = Mac.getInstance(HMAC_SHA256);
		mac.init(nonceDerivationKey);
		byte[] full = mac.doFinal(plaintextBytes);
		return Arrays.copyOf(full, NONCE_LENGTH_BYTES);
	}

	private static byte[] deriveSubkey(byte[] masterKey, String label) {
		try {
			Mac mac = Mac.getInstance(HMAC_SHA256);
			mac.init(new SecretKeySpec(masterKey, HMAC_SHA256));
			byte[] full = mac.doFinal(label.getBytes(StandardCharsets.UTF_8));
			return Arrays.copyOf(full, KEY_LENGTH_BYTES);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("no se pudo derivar la subclave de cifrado", e);
		}
	}
}
