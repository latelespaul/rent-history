package com.rentrix.rentrixserver.storage;

import java.util.Arrays;
import java.util.Optional;

/**
 * The file types a private document may be (D-67, D-131): PDF, JPEG, PNG or HEIC (iPhone photos), read from the
 * file's first bytes, never from the client's content type or file name.
 */
public enum DocumentType {
	PDF("application/pdf", "pdf"),
	JPEG("image/jpeg", "jpg"),
	PNG("image/png", "png"),
	HEIC("image/heic", "heic");
	
	/** The largest file accepted (D-67): 10 MB. */
	public static final long MAX_BYTES = 10L * 1024 * 1024;
	
	private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
	private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
	private static final String[] HEIF_BRANDS = {"heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1"};
	
	private final String contentType;
	private final String extension;
	
	DocumentType(String contentType, String extension) {
		this.contentType = contentType;
		this.extension = extension;
	}
	
	public String contentType() {
		return contentType;
	}
	
	public String extension() {
		return extension;
	}
	
	public static Optional<DocumentType> ofContentType(String contentType) {
		return Arrays.stream(values()).filter(t -> t.contentType.equals(contentType)).findFirst();
	}
	
	/** The type the bytes show, or empty when they are none of the accepted types. */
	public static Optional<DocumentType> sniff(byte[] head) {
		if (startsWith(head, 0, PDF_MAGIC)) {
			return Optional.of(PDF);
		}
		if (head.length >= 3 && (head[0] & 0xff) == 0xff && (head[1] & 0xff) == 0xd8 && (head[2] & 0xff) == 0xff) {
			return Optional.of(JPEG);
		}
		if (startsWith(head, 0, PNG_MAGIC)) {
			return Optional.of(PNG);
		}
		// ISO base media file: a box size, then "ftyp" and the major brand.
		if (head.length >= 12 && startsWith(head, 4, new byte[] {'f', 't', 'y', 'p'})) {
			String brand = new String(head, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
			if (Arrays.asList(HEIF_BRANDS).contains(brand)) {
				return Optional.of(HEIC);
			}
		}
		return Optional.empty();
	}
	
	private static boolean startsWith(byte[] bytes, int offset, byte[] prefix) {
		if (bytes.length < offset + prefix.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if (bytes[offset + i] != prefix[i]) {
				return false;
			}
		}
		return true;
	}
	
}
