package com.rentrix.rentrixserver.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Private documents (rental agreements, D-131; later ownership papers, D-152): stored encrypted through
 * {@link ImageStorage} under random keys and handed out only as short-lived HMAC-signed URLs (5 minutes, D-30,
 * D-95) to {@code GET /documents/{token}}. Who may get a URL is decided by the caller (uploader, the other party of
 * the tenancy, admins); the URL itself is the permission, so it is never logged, and it names neither the user nor
 * the document's record.
 */
@Slf4j
@Service
public class DocumentStorage {
	
	public static final Duration SIGNED_URL_TTL = Duration.ofMinutes(5);
	
	private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder B64_DECODE = Base64.getUrlDecoder();
	
	private final ImageStorage storage;
	private final DocumentCipher cipher;
	private final Clock clock;
	
	public DocumentStorage(ImageStorage storage, @Value("${app.documents.encryption-key:}") String encryptionKey,
		Clock clock) {
		this.storage = storage;
		this.cipher = encryptionKey == null || encryptionKey.isBlank() ? null : new DocumentCipher(encryptionKey);
		this.clock = clock;
	}
	
	/** What {@link #store} kept: the random key, the sniffed type, the plain file's size and SHA-256. */
	public record StoredDocument(String key, DocumentType type, long sizeBytes, String sha256) {
	}
	
	/** A signed download link and when it stops working. */
	public record SignedUrl(String url, Instant expiresAt) {
	}
	
	/** A download the signature allows: the key and the type to serve it as. */
	public record Download(String key, DocumentType type) {
	}
	
	/** Encrypts and stores {@code plain} under a new random key in {@code prefix}. */
	public StoredDocument store(String prefix, byte[] plain, DocumentType type) {
		String key = StorageKeys.random(prefix);
		try {
			storage.put(Visibility.PRIVATE, key, cipher().encrypt(key, plain));
		} catch (IOException e) {
			throw new UncheckedIOException("Could not store the document", e);
		}
		return new StoredDocument(key, type, plain.length, sha256(plain));
	}
	
	/** The decrypted file, or empty when it is gone (deleted by retention). */
	public Optional<byte[]> read(String key) {
		try (InputStream in = storage.open(Visibility.PRIVATE, key)) {
			return Optional.of(cipher().decrypt(key, in.readAllBytes()));
		} catch (NoSuchFileException gone) {
			return Optional.empty();
		} catch (IOException e) {
			throw new UncheckedIOException("Could not read the document", e);
		}
	}
	
	public boolean exists(String key) {
		return key != null && storage.exists(Visibility.PRIVATE, key);
	}
	
	public void delete(String key) {
		if (key == null) {
			return;
		}
		try {
			storage.delete(Visibility.PRIVATE, key);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not delete the document", e);
		}
	}
	
	/** A {@link #SIGNED_URL_TTL} link to the decrypted file, absolute on the API host of the current request. */
	public SignedUrl signedUrl(String key, DocumentType type) {
		Instant expires = clock.instant().plus(SIGNED_URL_TTL);
		byte[] payload = (StorageKeys.require(key) + "\n" + type.name() + "\n" + expires.getEpochSecond())
			.getBytes(StandardCharsets.UTF_8);
		String token = B64.encodeToString(payload) + "." + B64.encodeToString(cipher().sign(payload));
		String url = ServletUriComponentsBuilder.fromCurrentContextPath().path("/documents/{token}")
			.buildAndExpand(token).toUriString();
		return new SignedUrl(url, expires);
	}
	
	/**
	 * Checks a token from {@link #signedUrl}: a valid signature (compared in constant time) that has not expired.
	 * Empty for anything else, without saying which check failed.
	 */
	public Optional<Download> verify(String token) {
		if (token == null || cipher == null) {
			return Optional.empty();
		}
		int dot = token.indexOf('.');
		if (dot <= 0 || dot != token.lastIndexOf('.')) {
			return Optional.empty();
		}
		try {
			byte[] payload = B64_DECODE.decode(token.substring(0, dot));
			byte[] signature = B64_DECODE.decode(token.substring(dot + 1));
			if (!MessageDigest.isEqual(cipher.sign(payload), signature)) {
				return Optional.empty();
			}
			String[] parts = new String(payload, StandardCharsets.UTF_8).split("\n", -1);
			if (parts.length != 3 || !StorageKeys.valid(parts[0])) {
				return Optional.empty();
			}
			if (Instant.ofEpochSecond(Long.parseLong(parts[2])).isBefore(clock.instant())) {
				return Optional.empty();
			}
			return Optional.of(new Download(parts[0], DocumentType.valueOf(parts[1])));
		} catch (IllegalArgumentException malformed) {
			return Optional.empty();
		}
	}
	
	public static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}
	
	private DocumentCipher cipher() {
		if (cipher == null) {
			// The startup guard refuses to start outside dev and test without a key; this is the last line.
			throw new IllegalStateException("DOCUMENT_ENCRYPTION_KEY is not set");
		}
		return cipher;
	}
	
}
