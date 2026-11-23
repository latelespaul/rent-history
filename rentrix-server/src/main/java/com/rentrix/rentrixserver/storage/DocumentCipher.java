package com.rentrix.rentrixserver.storage;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AES-256-GCM for private documents at rest (D-131): a random 96-bit IV per file, the storage key as associated data
 * (a file copied under another key does not decrypt), and the stored form {@code "RXD1" | IV | ciphertext+tag}. The
 * AES key and the URL-signing key are derived from {@code DOCUMENT_ENCRYPTION_KEY} with HMAC-SHA256 and two fixed
 * labels, so one env secret serves both and neither derived key is ever the raw secret. Nothing here logs a key.
 */
final class DocumentCipher {
	
	private static final byte[] MAGIC = {'R', 'X', 'D', '1'};
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;
	private static final SecureRandom RANDOM = new SecureRandom();
	
	private final SecretKeySpec aesKey;
	private final byte[] signingKey;
	
	DocumentCipher(String secret) {
		byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
		this.aesKey = new SecretKeySpec(derive(raw, "rentrix-document-encryption-v1"), "AES");
		this.signingKey = derive(raw, "rentrix-document-url-v1");
	}
	
	byte[] encrypt(String storageKey, byte[] plain) {
		byte[] iv = new byte[IV_BYTES];
		RANDOM.nextBytes(iv);
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
			cipher.updateAAD(storageKey.getBytes(StandardCharsets.UTF_8));
			byte[] sealed = cipher.doFinal(plain);
			return ByteBuffer.allocate(MAGIC.length + IV_BYTES + sealed.length).put(MAGIC).put(iv).put(sealed).array();
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Document encryption failed", e);
		}
	}
	
	byte[] decrypt(String storageKey, byte[] stored) {
		if (stored.length < MAGIC.length + IV_BYTES || !Arrays.equals(stored, 0, MAGIC.length, MAGIC, 0, MAGIC.length)) {
			throw new IllegalStateException("Not an encrypted document");
		}
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, aesKey,
				new GCMParameterSpec(TAG_BITS, stored, MAGIC.length, IV_BYTES));
			cipher.updateAAD(storageKey.getBytes(StandardCharsets.UTF_8));
			int offset = MAGIC.length + IV_BYTES;
			return cipher.doFinal(stored, offset, stored.length - offset);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Document decryption failed", e);
		}
	}
	
	byte[] sign(byte[] payload) {
		return hmac(signingKey, payload);
	}
	
	private static byte[] derive(byte[] secret, String label) {
		return hmac(secret, label.getBytes(StandardCharsets.UTF_8));
	}
	
	private static byte[] hmac(byte[] key, byte[] data) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key, "HmacSHA256"));
			return mac.doFinal(data);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("HMAC-SHA256 unavailable", e);
		}
	}
	
}
