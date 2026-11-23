package com.rentrix.rentrixserver.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Private documents at rest (D-131) and their signed links (D-30, D-95), without Spring. */
class DocumentStorageTest {
	
	private static final String KEY = "unit-test-document-key-that-is-long-enough-0123456789";
	private static final byte[] FILE = "%PDF-1.4 Aadhaar 2345 6789 0123 %%EOF".getBytes(StandardCharsets.US_ASCII);
	
	@TempDir
	Path dir;
	
	private static class MovableClock extends Clock {
		Instant now = Instant.parse("2026-11-23T10:00:00Z");
		
		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}
		
		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}
		
		@Override
		public Instant instant() {
			return now;
		}
	}
	
	private DocumentStorage storage(String key, Clock clock) {
		return new DocumentStorage(new LocalImageStorage(dir.toString()), key, clock);
	}
	
	private static void onRequest() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setServerName("api.example.test");
		request.setContextPath("/api/v1");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
	}
	
	@Test
	void storesEncryptedUnderARandomKeyAndReadsBack() throws Exception {
		DocumentStorage docs = storage(KEY, Clock.systemUTC());
		DocumentStorage.StoredDocument stored = docs.store("agreements", FILE, DocumentType.PDF);
		
		assertThat(stored.key()).matches("agreements/[0-9a-f-]{36}");
		assertThat(stored.sha256()).isEqualTo(DocumentStorage.sha256(FILE)).hasSize(64);
		byte[] onDisk = Files.readAllBytes(dir.resolve("private").resolve(stored.key()));
		assertThat(new String(onDisk, StandardCharsets.ISO_8859_1)).doesNotContain("2345 6789 0123").startsWith("RXD1");
		assertThat(docs.read(stored.key())).contains(FILE);
		// Another key cannot read it.
		assertThatThrownBy(() -> storage("another-document-key-that-is-long-enough-0123456789", Clock.systemUTC())
			.read(stored.key())).isInstanceOf(IllegalStateException.class);
		
		docs.delete(stored.key());
		assertThat(docs.read(stored.key())).isEmpty();
		assertThat(docs.exists(stored.key())).isFalse();
	}
	
	@Test
	void aFileCopiedUnderAnotherKeyDoesNotDecrypt() throws Exception {
		DocumentStorage docs = storage(KEY, Clock.systemUTC());
		DocumentStorage.StoredDocument a = docs.store("agreements", FILE, DocumentType.PDF);
		DocumentStorage.StoredDocument b = docs.store("agreements", "%PDF-other".getBytes(StandardCharsets.US_ASCII),
			DocumentType.PDF);
		Files.copy(dir.resolve("private").resolve(a.key()), dir.resolve("private").resolve(b.key()),
			java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		assertThatThrownBy(() -> docs.read(b.key())).isInstanceOf(IllegalStateException.class);
	}
	
	@Test
	void signedLinksLastFiveMinutesAndRefuseAnyChange() {
		onRequest();
		try {
			MovableClock clock = new MovableClock();
			DocumentStorage docs = storage(KEY, clock);
			String key = docs.store("agreements", FILE, DocumentType.PDF).key();
			DocumentStorage.SignedUrl url = docs.signedUrl(key, DocumentType.PDF);
			
			assertThat(url.url()).startsWith("http://api.example.test/api/v1/documents/").doesNotContain(key);
			assertThat(url.expiresAt()).isEqualTo(clock.now.plus(Duration.ofMinutes(5)));
			String token = url.url().substring(url.url().lastIndexOf('/') + 1);
			assertThat(docs.verify(token)).hasValueSatisfying(d -> {
				assertThat(d.key()).isEqualTo(key);
				assertThat(d.type()).isEqualTo(DocumentType.PDF);
			});
			
			String payload = token.substring(0, token.indexOf('.'));
			String signature = token.substring(token.indexOf('.') + 1);
			assertThat(docs.verify(payload + "." + signature.substring(1) + "A")).isEmpty();
			assertThat(docs.verify(payload + "x." + signature)).isEmpty();
			assertThat(docs.verify("nonsense")).isEmpty();
			assertThat(storage("another-document-key-that-is-long-enough-0123456789", clock).verify(token)).isEmpty();
			
			clock.now = clock.now.plus(Duration.ofMinutes(5)).plusSeconds(1);
			assertThat(docs.verify(token)).isEmpty();
		} finally {
			RequestContextHolder.resetRequestAttributes();
		}
	}
	
	@Test
	void withoutAKeyNothingIsStored() {
		DocumentStorage docs = storage("", Clock.systemUTC());
		assertThatThrownBy(() -> docs.store("agreements", FILE, DocumentType.PDF))
			.isInstanceOf(IllegalStateException.class).hasMessageContaining("DOCUMENT_ENCRYPTION_KEY");
		assertThat(docs.verify("a.b")).isEmpty();
	}
	
	@Test
	void keysThatCouldLeaveThePrivateFolderAreRefused() {
		LocalImageStorage local = new LocalImageStorage(dir.toString());
		assertThatThrownBy(() -> local.put(Visibility.PRIVATE, "../escape", FILE)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> local.put(Visibility.PRIVATE, "agreements/../../x", FILE))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> local.put(Visibility.PUBLIC, StorageKeys.random("agreements"), FILE))
			.isInstanceOf(UnsupportedOperationException.class);
	}
	
	@Test
	void sniffsTheFileTypeFromItsBytes() {
		assertThat(DocumentType.sniff("%PDF-1.7".getBytes(StandardCharsets.US_ASCII))).contains(DocumentType.PDF);
		assertThat(DocumentType.sniff(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00})).contains(DocumentType.JPEG);
		assertThat(DocumentType.sniff(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}))
			.contains(DocumentType.PNG);
		assertThat(DocumentType.sniff(new byte[] {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'}))
			.contains(DocumentType.HEIC);
		assertThat(DocumentType.sniff(new byte[] {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'})).isEmpty();
		assertThat(DocumentType.sniff("<html><script>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
		assertThat(DocumentType.sniff(new byte[0])).isEmpty();
	}
	
}
