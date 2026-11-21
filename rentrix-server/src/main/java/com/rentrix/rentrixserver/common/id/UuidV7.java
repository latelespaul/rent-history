package com.rentrix.rentrixserver.common.id;

import java.security.SecureRandom;
import java.util.UUID;

/** RFC 9562 version 7 UUIDs for {@code public_id} columns (D-12). */
public final class UuidV7 {
	
	private static final SecureRandom RANDOM = new SecureRandom();
	
	private UuidV7() {
	}
	
	public static UUID next() {
		return at(System.currentTimeMillis());
	}
	
	static UUID at(long epochMillis) {
		long randA = RANDOM.nextInt(1 << 12);
		long msb = (epochMillis << 16) | 0x7000L | randA;          // 48-bit time, version 7, 12 random bits
		long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L; // variant 10, 62 random bits
		return new UUID(msb, lsb);
	}
	
}
