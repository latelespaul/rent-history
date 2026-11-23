package com.rentrix.rentrixserver.storage;

import com.rentrix.rentrixserver.common.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /documents/{token}}: serves a private document through a signed URL from {@link DocumentStorage}
 * (D-30, D-95, D-131). No sign-in: the signed, 5-minute token is the permission, handed out only to the people who
 * may see the file. A bad or expired token is {@code 404}; a file deleted by retention is {@code 410 file_deleted}.
 * Never cached, never sniffed by the browser.
 */
@RestController
@RequiredArgsConstructor
public class DocumentDownloadController {
	
	private final DocumentStorage documents;
	
	@GetMapping("/documents/{token}")
	public ResponseEntity<byte[]> download(@PathVariable String token) {
		DocumentStorage.Download download = documents.verify(token)
			.orElseThrow(() -> ApiException.notFound("Document"));
		byte[] file = documents.read(download.key())
			.orElseThrow(() -> new ApiException(HttpStatus.GONE, "file_deleted",
				"This file was deleted after its check, as promised when it was uploaded."));
		DocumentType type = download.type();
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(type.contentType()))
			.cacheControl(CacheControl.noStore().cachePrivate())
			.header(HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.inline().filename("document." + type.extension()).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.header("Referrer-Policy", "no-referrer")
			.body(file);
	}
	
}
